/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org
Copyright (c) 2012-19 The Processing Foundation

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
import java.nio.charset.StandardCharsets;
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
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;

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

  protected final JavaEditor editor;

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

  private volatile boolean enabled = true;

  /**
   * Create a new preprocessing service to support an editor.
   * @param editor The editor supported by this service and receives issues.
   */
  public PreprocService(JavaEditor editor) {
    this.editor = editor;

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
        Messages.loge("problem in preprocessor service loop", e);
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
    if (enabled) {
      synchronized (requestLock) {
        if (preprocessingTask.isDone()) {
          preprocessingTask = new CompletableFuture<>();
          // Register callback which executes all listeners
          whenDone(this::fireListeners);
        }
        requestQueue.offer(Boolean.TRUE);
      }
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
            if (e != null) Messages.loge("PPS: exception in callback", e);
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
    if (!enabled) return;
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
    if (!enabled) return;
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
        Messages.loge("error when firing preprocessing listener", e);
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

    JavaMode javaMode = (JavaMode) editor.getMode();
    Sketch sketch = result.sketch = editor.getSketch();
    String className = sketch.getName();

    StringBuilder workBuffer = new StringBuilder();

    // Combine code into one buffer
    int numLines = 1;
    IntList tabStartsList = new IntList();
    List<SketchCode> javaFiles = new ArrayList<>();
    List<Integer> tabLineStarts = new ArrayList<>();
    for (SketchCode sc : sketch.getCode()) {
      if (sc.isExtension("pde")) {
        tabStartsList.append(workBuffer.length());
        tabLineStarts.add(numLines);

        StringBuilder newPiece = new StringBuilder();
        if (sc.getDocument() != null) {
          try {
            newPiece.append(sc.getDocumentText());
          } catch (BadLocationException e) {
            e.printStackTrace();
          }
        } else {
          newPiece.append(sc.getProgram());
        }
        newPiece.append('\n');

        String newPieceBuilt = newPiece.toString();
        numLines += SourceUtil.getCount(newPieceBuilt, "\n");
        workBuffer.append(newPieceBuilt);
      } else if (sc.isExtension("java")) {
        javaFiles.add(sc);
      }
    }
    result.tabStartOffsets = tabStartsList.array();

    String pdeStage = result.pdeCode = workBuffer.toString();

    boolean reloadCodeFolder = firstCheck || codeFolderChanged.getAndSet(false);
    boolean reloadLibraries = firstCheck || librariesChanged.getAndSet(false);

    // Core and default imports
    PdePreprocessor preProcessor =
      editor.createPreprocessor(editor.getSketch().getName());
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

    if (preprocessorResult.getPreprocessIssues().size() > 0) {
      final int endNumLines = numLines;

      preprocessorResult.getPreprocessIssues().stream()
          .map((x) -> ProblemFactory.build(x, tabLineStarts, endNumLines, editor))
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
    parser.setSource(parsableStage.toCharArray());
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setCompilerOptions(COMPILER_OPTIONS);
    parser.setStatementsRecovery(true);
    CompilationUnit parsableCU = (CompilationUnit) parser.createAST(null);

    // Prepare advanced transforms which operate on AST
    TextTransform toCompilable = new TextTransform(parsableStage);
    toCompilable.addAll(SourceUtil.preprocessAST(parsableCU));

    // Transform code to compilable state
    String compilableStage = toCompilable.apply();
    OffsetMapper compilableMapper = toCompilable.getMapper();
    char[] compilableStageChars = compilableStage.toCharArray();

    // Create compilable AST to get syntax problems
    parser.setSource(compilableStageChars);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setCompilerOptions(COMPILER_OPTIONS);
    parser.setStatementsRecovery(true);
    CompilationUnit compilableCU = (CompilationUnit) parser.createAST(null);

    // Get syntax problems from compilable AST
    result.hasSyntaxErrors |=
      Arrays.stream(compilableCU.getProblems()).anyMatch(IProblem::isError);

    // Generate bindings after getting problems - avoids
    // 'missing type' errors when there are syntax problems
    CompilationUnit bindingsCU;
    if (javaFiles.size() == 0) {
      bindingsCU = compileInMemory(
          compilableStage,
          className,
          result.classPathArray
      );
    } else {
      bindingsCU = compileFromDisk(
          compilableStage,
          className,
          result.classPathArray,
          javaFiles
      );
    }

    // Get compilation problems
    List<IProblem> bindingsProblems = Arrays.asList(bindingsCU.getProblems());
    result.hasCompilationErrors =
      bindingsProblems.stream().anyMatch(IProblem::isError);

    // Update builder
    result.offsetMapper = parsableMapper.thenMapping(compilableMapper);
    result.javaCode = compilableStage;
    result.compilationUnit = bindingsCU;

    // Build it
    return result.build();
  }

  /// FINAL COMPILATION -------------------------------------------------------

  private CompilationUnit compileInMemory(String compilableStage,
      String className, String[] classPathArray) {

    parser.setSource(compilableStage.toCharArray());
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setCompilerOptions(COMPILER_OPTIONS);
    parser.setStatementsRecovery(true);
    parser.setUnitName(className);
    parser.setEnvironment(classPathArray, null, null, false);
    parser.setResolveBindings(true);
    return (CompilationUnit) parser.createAST(null);
  }

  private CompilationUnit compileFromDisk(String compilableStage,
      String className, String[] classPathArray, List<SketchCode> javaFiles) {

    List<Path> temporaryFilesList = new ArrayList<>();

    // Prepare preprocessor
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setCompilerOptions(COMPILER_OPTIONS);
    parser.setStatementsRecovery(true);
    parser.setUnitName(className);
    parser.setEnvironment(classPathArray, null, null, false);
    parser.setResolveBindings(true);

    // Write compilable processing file
    Path mainTemporaryFile = createTemporaryFile(compilableStage);
    final String mainSource = mainTemporaryFile.toString();
    temporaryFilesList.add(mainTemporaryFile);

    // Write temporary java files
    for (SketchCode javaFile : javaFiles) {
      Path newPath = createTemporaryFile(javaFile.getProgram());
      temporaryFilesList.add(newPath);
    }

    // Convert paths
    int numFiles = temporaryFilesList.size();
    String[] temporaryFilesArray = new String[numFiles];
    for (int i = 0; i < numFiles; i++) {
      temporaryFilesArray[i] = temporaryFilesList.get(i).toString();
    }

    // Compile
    MutableCompiledUnit compiledHolder = new MutableCompiledUnit();;
    parser.createASTs(
        temporaryFilesArray,
        null,
        new String[] {},
        new FileASTRequestor() {
          public void acceptAST(String source, CompilationUnit ast) {
            if (source.equals(mainSource)) {
              compiledHolder.set(ast);
            }
          }
        },
        null
    );

    // Delete
    deleteFiles(temporaryFilesList);

    // Return
    return compiledHolder.get();
  }

  private Path createTemporaryFile(String content) {
    try {
      Path tempFile = Files.createTempFile(null, null);
      Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
      return tempFile;
    } catch (IOException e) {
      throw new RuntimeException("Cannot write to temporary folder.");
    }
  }

  private void deleteFiles(List<Path> paths) {
    try {
      for (Path path : paths) {
        Files.delete(path);
      }
    } catch (IOException e) {
      throw new RuntimeException("Cannot delete from temporary folder.");
    }
  }

  private class MutableCompiledUnit {
    private CompilationUnit compilationUnit;

    public void set(CompilationUnit newUnit) {
      compilationUnit = newUnit;
    }

    public CompilationUnit get() {
      return compilationUnit;
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
