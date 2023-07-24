/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org
Copyright (c) 2012-23 The Processing Foundation

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License version 2
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation, Inc.
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/

package processing.mode.java;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.swing.text.BadLocationException;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;

import processing.app.Messages;
import processing.app.Sketch;
import processing.app.SketchCode;
import processing.app.SketchException;
import processing.app.Util;
import processing.mode.java.TextTransform.OffsetMapper;
import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.PreprocessorResult;
import processing.data.IntList;
import processing.data.StringList;


/**
 * Service which preprocesses code to check for and report on issues.
 * <p>
 * Service running in a background thread which checks for grammatical issues
 * via ANTLR and performs code analysis via the JDT to check for other issues
 * and related development services. These are reported as {Problem} instances
 * via a callback registered by an {Editor}.
 */
public class PreprocService {
  private final static int TIMEOUT_MILLIS = 100;
  private final static int BLOCKING_TIMEOUT_SECONDS = 3000;

  protected final JavaMode javaMode;
  protected final Sketch sketch;

  protected final ASTParser parser = ASTParser.newParser(AST.JLS11);

  private final Thread preprocessingThread;
  private final BlockingQueue<Boolean> requestQueue = new ArrayBlockingQueue<>(1);

  private final Object requestLock = new Object();

  private final AtomicBoolean codeFolderChanged = new AtomicBoolean(true);
  private final AtomicBoolean librariesChanged = new AtomicBoolean(true);

  private volatile boolean running;
  private CompletableFuture<PreprocSketch> preprocessingTask = new CompletableFuture<>();

  private CompletableFuture<?> lastCallback =
    new CompletableFuture<>() {{
      complete(null); // initialization block
    }};

  /**
   * Create a new preprocessing service to support the language server.
   */
  public PreprocService(JavaMode javaMode, Sketch sketch) {
    this.javaMode = javaMode;
    this.sketch = sketch;

    // Register listeners for first run
    whenDone(this::fireListeners);

    preprocessingThread = new Thread(this::mainLoop, "ECS");
    preprocessingThread.start();
  }

  /**
   * The "main loop" for the background thread that checks for code issues.
   */
  private void mainLoop() {
    running = true;
    PreprocSketch prevResult = null;
    CompletableFuture<?> runningCallbacks = null;
    Messages.log("PPS: Hi!");
    while (running) {
      try {
        try {
          requestQueue.take(); // blocking until requested
        } catch (InterruptedException e) {
          running = false;
          break;
        }

        Messages.log("PPS: Starting");

        prevResult = preprocessSketch(prevResult);

        // Wait until callbacks finish before firing new wave
        // If new request arrives while waiting, break out and start preprocessing
        while (requestQueue.isEmpty() && runningCallbacks != null) {
          try {
            runningCallbacks.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            runningCallbacks = null;
          } catch (TimeoutException ignored) { }
        }

        synchronized (requestLock) {
          if (requestQueue.isEmpty()) {
            runningCallbacks = lastCallback;
            Messages.log("PPS: Done");
            preprocessingTask.complete(prevResult);
          }
        }
      } catch (Exception e) {
        Messages.err("problem in preprocessor service loop", e);
      }
    }
    Messages.log("PPS: Bye!");
  }

  /**
   * End and clean up the background preprocessing thread.
   */
  public void dispose() {
    cancel();
    running = false;
    preprocessingThread.interrupt();
  }

  /**
   * Cancel any pending code checks.
   */
  public void cancel() {
    requestQueue.clear();
  }

  /**
   * Indicate to this service that the sketch code has changed.
   */
  public void notifySketchChanged() {
    synchronized (requestLock) {
      if (preprocessingTask.isDone()) {
        preprocessingTask = new CompletableFuture<>();
        // Register callback which executes all listeners
        whenDone(this::fireListeners);
      }
      requestQueue.offer(Boolean.TRUE);
    }
  }

  /**
   * Indicate to this service that the sketch libraries have changed.
   */
  public void notifyLibrariesChanged() {
    Messages.log("PPS: notified libraries changed");
    librariesChanged.set(true);
    notifySketchChanged();
  }

  /**
   * Indicate to this service that the folder housing sketch code has changed.
   */
  public void notifyCodeFolderChanged() {
    Messages.log("PPS: notified code folder changed");
    codeFolderChanged.set(true);
    notifySketchChanged();
  }

  /**
   * Register a callback to be fired when preprocessing is complete.
   *
   * @param callback The consumer to inform when preprocessing is complete which will provide a
   *    {PreprocessedSketch} that has any {Problem} instances that were resultant.
   * @return A future that will be fulfilled when preprocessing is complete.
   */
  private CompletableFuture<?> registerCallback(Consumer<PreprocSketch> callback) {
    synchronized (requestLock) {
      lastCallback = preprocessingTask
          // Run callback after both preprocessing task and previous callback
          .thenAcceptBothAsync(lastCallback, (ps, a) -> callback.accept(ps))
          // Make sure exception in callback won't cancel whole callback chain
          .handleAsync((res, e) -> {
            if (e != null) Messages.err("PPS: exception in callback", e);
            return res;
          });
      return lastCallback;
    }
  }

  /**
   * Register a callback to be fired when preprocessing is complete if the service is still running.
   *
   * <p>
   * Register a callback to be fired when preprocessing is complete if the service is still running,
   * turning this into a no-op if it is no longer running. Note that this callback will only be
   * executed once and it is distinct from registering a listener below which will receive all
   * future updates.
   * </p>
   *
   * @param callback The consumer to inform when preprocessing is complete which will provide a
   *    {PreprocessedSketch} that has any {Problem} instances that were resultant.
   */
  public void whenDone(Consumer<PreprocSketch> callback) {
    registerCallback(callback);
  }

  /**
   * Wait for preprocessing to complete.
   *
   * <p>
   * Register a callback to be fired when preprocessing is complete if the service is still running,
   * turning this into a no-op if it is no longer running. However, wait up to
   * BLOCKING_TIMEOUT_SECONDS in a blocking manner until preprocessing is complete.
   * Note that this callback will only be executed once and it is distinct from registering a
   * listener below which will receive all future updates.
   * </p>
   */
  public void whenDoneBlocking(Consumer<PreprocSketch> callback) {
    try {
      registerCallback(callback).get(BLOCKING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      // Don't care
    }
  }



  /// LISTENERS ----------------------------------------------------------------


  private final Set<Consumer<PreprocSketch>> listeners = new CopyOnWriteArraySet<>();

  /**
   * Register a consumer that will receive all {PreprocessedSketch}es
   * produced from this service.
   * @param listener The listener to receive all future {PreprocessedSketch}es.
   */
  public void registerListener(Consumer<PreprocSketch> listener) {
    if (listener != null) listeners.add(listener);
  }

  /**
   * Remove a consumer previously registered.
   *
   * <p>
   * Remove a consumer previously registered that was receiving {PreprocessedSketch}es produced from
   * this service.
   * </p>
   *
   * @param listener The listener to remove.
   */
  public void unregisterListener(Consumer<PreprocSketch> listener) {
    listeners.remove(listener);
  }

  /**
   * Inform consumers waiting for {PreprocessedSketch}es.
   *
   * <p>
   * Inform all consumers registered for receiving ongoing {PreprocessedSketch}es produced from
   * this service.
   * </p>
   *
   * @param ps The sketch to be sent out to consumers.
   */
  private void fireListeners(PreprocSketch ps) {
    for (Consumer<PreprocSketch> listener : listeners) {
      try {
        listener.accept(ps);
      } catch (Exception e) {
        Messages.err("error when firing preprocessing listener", e);
      }
    }
  }


  /// --------------------------------------------------------------------------


  /**
   * Transform and attempt compilation of a sketch.
   *
   * <p>
   * Transform a sketch via ANTLR first to detect and explain grammatical issues before executing a
   * build via the JDT to detect other non-grammatical compilation issues and to support developer
   * services in the editor.
   * </p>
   *
   * @param prevResult The last produced preprocessed sketch or null if never preprocessed
   *    beforehand.
   * @return The newly generated preprocessed sketch.
   */
  private PreprocSketch preprocessSketch(PreprocSketch prevResult) {
    boolean firstCheck = (prevResult == null);

    PreprocSketch.Builder result = new PreprocSketch.Builder();

    List<ImportStatement> codeFolderImports = result.codeFolderImports;
    List<ImportStatement> programImports = result.programImports;

    result.sketch = this.sketch;
    String className = sketch.getMainName();

    StringBuilder workBuffer = new StringBuilder();

    // Combine code into one buffer
    int numLines = 1;
    IntList tabStartsList = new IntList();
    List<JavaSketchCode> javaFiles = new ArrayList<>();
    List<Integer> tabLineStarts = new ArrayList<>();
    for (SketchCode sc : sketch.getCode()) {
      if (sc.isExtension("pde")) {
        tabStartsList.append(workBuffer.length());
        tabLineStarts.add(numLines);

        String newPieceBuilt = getSketchTabContents(sc) + '\n';
        numLines += SourceUtil.getCount(newPieceBuilt, "\n");
        workBuffer.append(newPieceBuilt);

      } else if (sc.isExtension("java")) {
        tabStartsList.append(workBuffer.length());
        tabLineStarts.add(numLines);

        javaFiles.add(new JavaSketchCode(sc, tabStartsList.size()-1));

        String newPieceBuilt = String.format(
            "\n// Skip java file %s.\n",
            sc.getFileName()
        );

        numLines += SourceUtil.getCount(newPieceBuilt, "\n");
        workBuffer.append(newPieceBuilt);
      }
    }
    result.tabStartOffsets = tabStartsList.toArray();

    String pdeStage = result.pdeCode = workBuffer.toString();

    boolean reloadCodeFolder = firstCheck || codeFolderChanged.getAndSet(false);
    boolean reloadLibraries = firstCheck || librariesChanged.getAndSet(false);

    // Core and default imports
    PdePreprocessor preProcessor =
      PdePreprocessor.builderFor(this.sketch.getMainName()).build();
    if (coreAndDefaultImports == null) {
      coreAndDefaultImports = buildCoreAndDefaultImports(preProcessor);
    }
    result.coreAndDefaultImports.addAll(coreAndDefaultImports);

    // Prepare code folder imports
    if (reloadCodeFolder) {
      codeFolderImports.addAll(buildCodeFolderImports(sketch));
    } else {
      codeFolderImports.addAll(prevResult.codeFolderImports);
    }

    // TODO: convert unicode escapes to chars
    // TODO: This appears no longer to be needed.
    SourceUtil.scrubCommentsAndStrings(workBuffer);

    result.scrubbedPdeCode = workBuffer.toString();

    PreprocessorResult preprocessorResult;
    try {
      preprocessorResult = preProcessor.write(
          new StringWriter(),
          result.scrubbedPdeCode,
          codeFolderImports.stream()
              .map(ImportStatement::getFullMemberName)
              .collect(Collectors.toList())
      );
    } catch (SketchException e) {
      throw new RuntimeException("Unexpected sketch exception in preprocessing: " + e);
    }

    final int endNumLines = numLines;

    if (preprocessorResult.getPreprocessIssues().size() > 0) {
      preprocessorResult.getPreprocessIssues().stream()
        .map((x) -> ProblemFactory.build(x, tabLineStarts))
        .forEach(result.otherProblems::add);

      result.hasSyntaxErrors = true;
    }

    // Save off the imports
    programImports.addAll(preprocessorResult.getImportStatements());
    result.programImports.addAll(preprocessorResult.getImportStatements());

    // Prepare transforms to convert pde code into parsable code
    TextTransform toParsable = new TextTransform(pdeStage);
    toParsable.addAll(preprocessorResult.getEdits());
    { // Refresh sketch classloader and classpath if imports changed
      if (reloadLibraries) {
        runtimePathBuilder.markLibrariesChanged();
      }

      boolean rebuildLibraryClassPath = reloadLibraries ||
          checkIfImportsChanged(programImports, prevResult.programImports);

      if (rebuildLibraryClassPath) {
        runtimePathBuilder.markLibraryImportsChanged();
      }

      boolean rebuildClassPath = reloadCodeFolder || rebuildLibraryClassPath ||
          prevResult.classLoader == null || prevResult.classPath == null ||
          prevResult.classPathArray == null || prevResult.searchClassPathArray == null;

      if (reloadCodeFolder) {
        runtimePathBuilder.markCodeFolderChanged();
      }

      if (rebuildClassPath) {
        runtimePathBuilder.prepareClassPath(result, javaMode);
      } else {
        result.classLoader = prevResult.classLoader;
        result.classPath = prevResult.classPath;
        result.searchClassPathArray = prevResult.searchClassPathArray;
        result.classPathArray = prevResult.classPathArray;
      }
    }

    // Transform code to parsable state
    String parsableStage = toParsable.apply();
    OffsetMapper parsableMapper = toParsable.getMapper();

    // Create intermediate AST for advanced preprocessing
    // Wait on .java tabs due to speed since they don't go through preproc.
    CompileResults parsableCompile = compileInMemory(
        parsableStage,
        className,
        result.classPathArray,
        false
    );
    CompilationUnit parsableCU = parsableCompile.getCompilationUnit();

    // Prepare advanced transforms which operate on AST
    TextTransform toCompilable = new TextTransform(parsableStage);
    toCompilable.addAll(SourceUtil.preprocessAST(parsableCU));

    // Transform code to compilable state
    // Again, wait on .java tabs due to speed since they don't go through
    // the preprocessor.
    String compilableStage = toCompilable.apply();
    OffsetMapper compilableMapper = toCompilable.getMapper();

    // Create compilable AST to get syntax problems
    CompileResults compilableCompile = compileInMemory(
        compilableStage,
        className,
        result.classPathArray,
        false
    );
    CompilationUnit compilableCU = compilableCompile.getCompilationUnit();

    // Get syntax problems from compilable AST
    result.hasSyntaxErrors |=
      Arrays.stream(compilableCU.getProblems()).anyMatch(IProblem::isError);

    // Generate bindings after getting problems - avoids
    // 'missing type' errors when there are syntax problems.
    // Introduce .java tabs here for type resolution.
    CompileResults bindingsCompile;
    if (javaFiles.size() == 0) {
      bindingsCompile = compileInMemory(
          compilableStage,
          className,
          result.classPathArray,
          true
      );
    } else {
      bindingsCompile = compileFromDisk(
          compilableStage,
          className,
          result.classPathArray,
          javaFiles
      );
    }

    // Get compilation problems
    List<IProblem> bindingsProblems = bindingsCompile.getProblems();
    result.hasCompilationErrors =
      bindingsProblems.stream().anyMatch(IProblem::isError);

    // Update builder
    result.offsetMapper = parsableMapper.thenMapping(compilableMapper);
    result.javaCode = compilableStage;
    result.compilationUnit = bindingsCompile.getCompilationUnit();
    result.javaFileMapping = bindingsCompile.getJavaFileMapping();
    result.iproblems = bindingsCompile.getProblems();

    // Build it
    return result.build();
  }

  /**
   * Get the updated (and possibly unsaved) code from a sketch tab.
   *
   * @param sketchCode The tab from which to content program contents.
   * @return Updated program contents.
   */
  private String getSketchTabContents(SketchCode sketchCode) {
    String code = null;
    if (sketchCode.getDocument() != null) {
      try {
        code = sketchCode.getDocumentText();
      } catch (BadLocationException e) {
        e.printStackTrace();
      }
    }

    if (code == null) {
      code = sketchCode.getProgram();
    }

    return code;
  }

  /// COMPILATION -----------------------------------------------------------

  /**
   * Perform compilation on a transformed Processing sketch.
   *
   * <p>
   * Perform compilation with optional binding on a transformed Processing
   * sketch that is fully described in a single in a single in memory string.
   * </p>
   *
   * @param sketchSource Full processing sketch source code.
   * @param className The name of the class enclosing the sketch.
   * @param classPathArray List of classpath entries.
   * @param resolveBindings Flag indicating if compilation should happen with
   *    binding resolution.
   * @return The results of compilation with binding.
   */
  private CompileResults compileInMemory(String sketchSource, String className,
      String[] classPathArray, boolean resolveBindings) {

    // Prepare parser
    parser.setSource(sketchSource.toCharArray());
    setupParser(resolveBindings, className, classPathArray);

    CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
    List<IProblem> problems = Arrays.asList(compilationUnit.getProblems());
    return new CompileResults(compilationUnit, problems, new HashMap<>());
  }

  /**
   * Perform compilation on a sketch with ".java" tabs.
   *
   * <p>
   * Perform compilation with optional binding on a transformed Processing sketch
   * containing Java files beyond the generated sketch after preprocessing.
   * </p>
   *
   * @param sketchSource Full processing sketch source code without the
   *    ".java" tabs.
   * @param className The name of the class enclosing the sketch.
   * @param classPathArray List of classpath entries.
   * @param javaFiles Information about the java files.
   * @return The results of compilation with binding.
   */
  private CompileResults compileFromDisk(String sketchSource,
                                         String className, String[] classPathArray,
                                         List<JavaSketchCode> javaFiles) {

    ProcessingASTRequester astRequester;
    List<Path> temporaryFilesList = new ArrayList<>();
    Map<String, Integer> javaFileMapping = new HashMap<>();

    // Prepare parser
    setupParser(true, className, classPathArray);

    // Write compilable processing file
    Path mainTemporaryFile = createTemporaryFile(sketchSource);
    final String mainSource = mainTemporaryFile.toString();
    temporaryFilesList.add(mainTemporaryFile);

    // Write temporary java files as tab may be unsaved
    for (JavaSketchCode javaFile : javaFiles) {
      String tabContents = getSketchTabContents(javaFile.getSketchCode());
      Path newPath = createTemporaryFile(tabContents);
      javaFileMapping.put(newPath.toString(), javaFile.getTabIndex());
      temporaryFilesList.add(newPath);
    }

    // Convert paths
    int numFiles = temporaryFilesList.size();
    String[] temporaryFilesArray = new String[numFiles];
    for (int i = 0; i < numFiles; i++) {
      temporaryFilesArray[i] = temporaryFilesList.get(i).toString();
    }

    // Compile
    astRequester = new ProcessingASTRequester(mainSource);
    parser.createASTs(
        temporaryFilesArray,
        null,
        new String[] {},
        astRequester,
        null
    );

    // Delete
    deleteTemporaryFiles(temporaryFilesList);

    // Return
    return new CompileResults(
      astRequester.getMainCompilationUnit(),
      astRequester.getProblems(),
      javaFileMapping
    );
  }

  /**
   * Set up the parser compiler options and optionally bindings information.
   *
   * @param resolveBindings Flag indicating if bindings should be resolved /
   *    checked for things like missing types.
   * @param className The name of the class enclosing the sketch.
   * @param classPathArray List of classpath entries.
   */
  private void setupParser(boolean resolveBindings, String className,
      String[] classPathArray) {

    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setCompilerOptions(COMPILER_OPTIONS);
    parser.setStatementsRecovery(true);

    if (resolveBindings) {
      parser.setUnitName(className);
      parser.setEnvironment(classPathArray, null, null, false);
      parser.setResolveBindings(true);
    }
  }

  /**
   * Create a temporary file for compilation with compileFromDisk.
   *
   * <p>
   * Create a temporary file for compilation with compileFromDisk where it may
   * be necessary to provide multiple files (like in the case of .java tabs) to
   * JDT.
   * </p>
   *
   * @param content The content to write to the temporary file.
   * @return Path to the newly created temporary file.
   */
  private Path createTemporaryFile(String content) {
    try {
      Path tempFile = Files.createTempFile(null, null);
      Files.writeString(tempFile, content);
      return tempFile;
    } catch (IOException e) {
      throw new RuntimeException("Cannot write to temporary folder.");
    }
  }

  /**
   * Delete temporary files used for compileFromDisk.
   *
   * @param paths The temporary files to remove.
   */
  private void deleteTemporaryFiles(List<Path> paths) {
    try {
      for (Path path : paths) {
        Files.delete(path);
      }
    } catch (IOException e) {
      throw new RuntimeException("Cannot delete from temporary folder.");
    }
  }

  /**
   * JDT AST requester for compileFromDisk.
   *
   * <p>
   * Abstract syntax tree requester for the JDT useful for compileFromDisk where
   * there may be ".java" tabs in addition to the single combined ".pde" source.
   * This requester will collect problems from all files but hold onto the AST
   * for the sketch's combined PDE code (not ".java" tabs).
   * </p>
   */
  static private class ProcessingASTRequester extends FileASTRequestor {
    private final String mainSource;
    private final List<IProblem> problems;
    private CompilationUnit mainCompilationUnit;

    /**
     * Create a new requester filtering for a target file.
     *
     * @param newMainSource The file (likely temporary file) that contains the
     *    PDE main sketch code.
     */
    public ProcessingASTRequester(String newMainSource) {
      mainSource = newMainSource;
      problems = new ArrayList<>();
    }

    /**
     * Callback for a new AST.
     *
     * @param source The file from which the AST was built.
     * @param ast The newly built AST as a compilation unit.
     */
    public void acceptAST(String source, CompilationUnit ast) {
      if (source.equals(mainSource)) {
        mainCompilationUnit = ast;
      }
      Collections.addAll(problems, ast.getProblems());
    }

    /**
     * Get problems found from all source files.
     *
     * @return Problems found across all files.
     */
    public List<IProblem> getProblems() {
      return problems;
    }

    /**
     * Get the CompilationUnit (AST) associated with the combined PDE source.
     *
     * @return Compilation unit associated with the combined PDE source
     *    (excludes ".java" tabs).
     */
    public CompilationUnit getMainCompilationUnit() {
      return mainCompilationUnit;
    }
  }

  /**
   * Data structure holding the results of compilation.
   */
  static private class CompileResults {
    private final CompilationUnit compilationUnit;
    private final List<IProblem> problems;
    private final Map<String, Integer> javaFileMapping;

    /**
     * Create a new record of compilation.
     *
     * @param newCompilationUnit The main compilation unit associated with the
     *    combined PDE sources (excludes ".java" tabs).
     * @param newProblems The problems found across all tabs (PDS or .java).
     * @param newJavaFileMapping Mapping from java temporary file name to tab
     *    number for ".java" tabs.
     */
    public CompileResults(CompilationUnit newCompilationUnit,
        List<IProblem> newProblems, Map<String, Integer> newJavaFileMapping) {

      compilationUnit = newCompilationUnit;
      problems = newProblems;
      javaFileMapping = newJavaFileMapping;
    }

    /**
     * Get the main compilation unit.
     *
     * @return The main compilation unit for the combined PDE sources. This is
     *    typically the entire sketch except if there are ".java" tabs which
     *    are excluded from this compilation unit as they may be in different
     *    packages.
     */
    public CompilationUnit getCompilationUnit() {
      return compilationUnit;
    }

    /**
     * Get problems found in compilation.
     *
     * @return Problems found across all files (PDE or ".java" tabs).
     */
    public List<IProblem> getProblems() {
      return problems;
    }

    /**
     * Get mapping from ".java" temporary file to tab index.
     *
     * @return To deal with unsaved ".java" file changes, this mapping indicates
     *    into what temporary file a ".java" tab ended up to the tab index for
     *    that file.
     */
    public Map<String, Integer> getJavaFileMapping() {
      return javaFileMapping;
    }
  }

  /**
   * SketchCode (tab of sketch) which is a ".java" tab.
   */
  static private class JavaSketchCode {
    private final SketchCode sketchCode;
    private final int tabIndex;

    /**
     * Create a new record of a ".java" tab inside a sketch.
     *
     * @param newSketchCode The SketchCode to be decorated.
     * @param newTabIndex The index of the tab.
     */
    public JavaSketchCode(SketchCode newSketchCode, int newTabIndex) {
      sketchCode = newSketchCode;
      tabIndex = newTabIndex;
    }

    /**
     * Get the sketch tab.
     *
     * @return SketchCode The tab and its contents.
     */
    public SketchCode getSketchCode() {
      return sketchCode;
    }

    /**
     * Get the index of the tab.
     *
     * @return The index of this ".java" tab within the sketch.
     */
    public int getTabIndex() {
      return tabIndex;
    }
  }

  /// IMPORTS -----------------------------------------------------------------

  private List<ImportStatement> coreAndDefaultImports;

  /**
   * Determine which imports need to be available for core processing services.
   *
   * @param preprocessor The preprocessor to operate on.
   * @return The import statements that need to be present.
   */
  private static List<ImportStatement> buildCoreAndDefaultImports(PdePreprocessor preprocessor) {
    List<ImportStatement> result = new ArrayList<>();

    for (String imp : preprocessor.getCoreImports()) {
      result.add(ImportStatement.parse(imp));
    }
    for (String imp : preprocessor.getDefaultImports()) {
      result.add(ImportStatement.parse(imp));
    }

    return result;
  }

  /**
   * Create import statements for items in the code folder itself.
   *
   * @param sketch The sketch for which the import statements should be created.
   * @return The new import statements.
   */
  private static List<ImportStatement> buildCodeFolderImports(Sketch sketch) {
    if (sketch.hasCodeFolder()) {
      File codeFolder = sketch.getCodeFolder();
      String codeFolderClassPath = Util.contentsToClassPath(codeFolder);
      StringList codeFolderPackages = Util.packageListFromClassPath(codeFolderClassPath);
      return StreamSupport.stream(codeFolderPackages.spliterator(), false)
          .map(ImportStatement::wholePackage)
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  /**
   * Determine if imports have changed.
   *
   * @param prevImports The last iteration imports.
   * @param imports The current iterations imports.
   * @return True if the list of imports changed and false otherwise.
   *    This includes change in order.
   */
  private static boolean checkIfImportsChanged(List<ImportStatement> prevImports,
                                                 List<ImportStatement> imports) {
    if (imports.size() != prevImports.size()) {
      return true;
    } else {
      int count = imports.size();
      for (int i = 0; i < count; i++) {
        if (!imports.get(i).isSameAs(prevImports.get(i))) {
          return true;
        }
      }
    }
    return false;
  }



  /// CLASS PATHS --------------------------------------------------------------

  private final RuntimePathBuilder runtimePathBuilder = new RuntimePathBuilder();

  /// --------------------------------------------------------------------------


  static private final Map<String, String> COMPILER_OPTIONS;
  static {
    Map<String, String> compilerOptions = new HashMap<>();

    // TODO: VERSION_17 if using new language features. Requires new JDT.
    compilerOptions.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_11);
    compilerOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_11);
    compilerOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_11);

    // See http://help.eclipse.org/mars/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Fguide%2Fjdt_api_options.htm&anchor=compiler

    final String[] generate = {
        JavaCore.COMPILER_LINE_NUMBER_ATTR,
        JavaCore.COMPILER_SOURCE_FILE_ATTR
    };

    final String[] ignore = {
        JavaCore.COMPILER_PB_UNUSED_IMPORT,
        JavaCore.COMPILER_PB_MISSING_SERIAL_VERSION,
        JavaCore.COMPILER_PB_RAW_TYPE_REFERENCE,
        JavaCore.COMPILER_PB_REDUNDANT_TYPE_ARGUMENTS,
        JavaCore.COMPILER_PB_UNCHECKED_TYPE_OPERATION
    };

    final String[] warn = {
        JavaCore.COMPILER_PB_NO_EFFECT_ASSIGNMENT,
        JavaCore.COMPILER_PB_NULL_REFERENCE,
        JavaCore.COMPILER_PB_POTENTIAL_NULL_REFERENCE,
        JavaCore.COMPILER_PB_REDUNDANT_NULL_CHECK,
        JavaCore.COMPILER_PB_POSSIBLE_ACCIDENTAL_BOOLEAN_ASSIGNMENT,
        JavaCore.COMPILER_PB_UNUSED_LABEL,
        JavaCore.COMPILER_PB_UNUSED_LOCAL,
        JavaCore.COMPILER_PB_UNUSED_OBJECT_ALLOCATION,
        JavaCore.COMPILER_PB_UNUSED_PARAMETER,
        JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER
    };

    for (String s : generate) compilerOptions.put(s, JavaCore.GENERATE);
    for (String s : ignore)   compilerOptions.put(s, JavaCore.IGNORE);
    for (String s : warn)     compilerOptions.put(s, JavaCore.WARNING);

    COMPILER_OPTIONS = Collections.unmodifiableMap(compilerOptions);
  }
}
