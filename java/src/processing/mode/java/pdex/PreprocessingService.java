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

package processing.mode.java.pdex;

import java.io.File;
import java.io.StringWriter;
import java.util.*;
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

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import processing.app.*;
import processing.data.IntList;
import processing.data.StringList;
import processing.mode.java.JavaEditor;
import processing.mode.java.JavaMode;
import processing.mode.java.pdex.TextTransform.OffsetMapper;
import processing.mode.java.pdex.util.ProblemFactory;
import processing.mode.java.pdex.util.RuntimePathBuilder;
import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.PreprocessorResult;
import processing.mode.java.preproc.code.SyntaxUtil;


/**
 * Service which preprocesses code to check for and report on issues.
 *
 * <p>
 * Service running in a background thread which checks for grammatical issues via ANTLR and performs
 * code analysis via the JDT to check for other issues and related development services. These are
 * reported as {Problem} instances via a callback registered by an {Editor}.
 * </p>
 */
public class PreprocessingService {

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
  private CompletableFuture<PreprocessedSketch> preprocessingTask = new CompletableFuture<>();

  private CompletableFuture<?> lastCallback =
      new CompletableFuture<Object>() {{
        complete(null); // initialization block
      }};

  private volatile boolean isEnabled = true;

  /**
   * Create a new preprocessing service to support an editor.
   *
   * @param editor The editor that will be supported by this service and to which issues should be
   *    reported.
   */
  public PreprocessingService(JavaEditor editor) {
    this.editor = editor;
    isEnabled = !editor.hasJavaTabs();

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
    PreprocessedSketch prevResult = null;
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
          } catch (TimeoutException e) { }
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
    if (!isEnabled) return;
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
   * Indicate to this service that the sketch libarries have changed.
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
    Messages.log("PPS: snotified code folder changed");
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
  private CompletableFuture<?> registerCallback(Consumer<PreprocessedSketch> callback) {
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
  public void whenDone(Consumer<PreprocessedSketch> callback) {
    if (!isEnabled) return;
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
   *
   * @param callback
   */
  public void whenDoneBlocking(Consumer<PreprocessedSketch> callback) {
    if (!isEnabled) return;
    try {
      registerCallback(callback).get(BLOCKING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      // Don't care
    }
  }



  /// LISTENERS ----------------------------------------------------------------


  private Set<Consumer<PreprocessedSketch>> listeners = new CopyOnWriteArraySet<>();

  /**
   * Register a consumer that will receive all {PreprocessedSketch}es produced from this service.
   *
   * @param listener The listener to receive all future {PreprocessedSketch}es.
   */
  public void registerListener(Consumer<PreprocessedSketch> listener) {
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
  public void unregisterListener(Consumer<PreprocessedSketch> listener) {
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
  private void fireListeners(PreprocessedSketch ps) {
    for (Consumer<PreprocessedSketch> listener : listeners) {
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
  private PreprocessedSketch preprocessSketch(PreprocessedSketch prevResult) {

    boolean firstCheck = prevResult == null;

    PreprocessedSketch.Builder result = new PreprocessedSketch.Builder();

    List<ImportStatement> codeFolderImports = result.codeFolderImports;
    List<ImportStatement> programImports = result.programImports;

    JavaMode javaMode = (JavaMode) editor.getMode();
    Sketch sketch = result.sketch = editor.getSketch();
    String className = sketch.getName();

    StringBuilder workBuffer = new StringBuilder();

    // Combine code into one buffer
    int numLines = 1;
    IntList tabStartsList = new IntList();
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
        numLines += SyntaxUtil.getCount(newPieceBuilt, "\n");
        workBuffer.append(newPieceBuilt);
      }
    }
    result.tabStartOffsets = tabStartsList.array();

    String pdeStage = result.pdeCode = workBuffer.toString();

    boolean reloadCodeFolder = firstCheck || codeFolderChanged.getAndSet(false);
    boolean reloadLibraries = firstCheck || librariesChanged.getAndSet(false);

    // Core and default imports
    PdePreprocessor preProcessor = editor.createPreprocessor(editor.getSketch().getName());
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

    SourceUtils.scrubCommentsAndStrings(workBuffer);

    result.scrubbedPdeCode = workBuffer.toString();

    PreprocessorResult preprocessorResult;
    try {
      preprocessorResult = preProcessor.write(
          new StringWriter(),
          result.scrubbedPdeCode,
          codeFolderImports.stream()
              .map(ImportStatement::getFullClassName)
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
      return result.build();
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
    //System.out.println(new String(parsableStage.toCharArray()));
    CompilationUnit parsableCU = JdtCompilerUtil.makeAST(
        parser,
        parsableStage.toCharArray(),
        JdtCompilerUtil.COMPILER_OPTIONS
    );

    // Prepare advanced transforms which operate on AST
    TextTransform toCompilable = new TextTransform(parsableStage);
    toCompilable.addAll(SourceUtils.preprocessAST(parsableCU));

    // Transform code to compilable state
    String compilableStage = toCompilable.apply();
    OffsetMapper compilableMapper = toCompilable.getMapper();
    char[] compilableStageChars = compilableStage.toCharArray();

    // Create compilable AST to get syntax problems
    // System.out.println(new String(compilableStageChars));
    // System.out.println("-----");
    CompilationUnit compilableCU =
        JdtCompilerUtil.makeAST(parser, compilableStageChars, JdtCompilerUtil.COMPILER_OPTIONS);

    // Get syntax problems from compilable AST
    result.hasSyntaxErrors |= Arrays.stream(compilableCU.getProblems())
        .anyMatch(IProblem::isError);

    // Generate bindings after getting problems - avoids
    // 'missing type' errors when there are syntax problems
    CompilationUnit bindingsCU = JdtCompilerUtil.makeASTWithBindings(
          parser,
          compilableStageChars,
          JdtCompilerUtil.COMPILER_OPTIONS,
          className,
          result.classPathArray
    );

    // Get compilation problems
    List<IProblem> bindingsProblems = Arrays.asList(bindingsCU.getProblems());
    result.hasCompilationErrors = bindingsProblems.stream()
        .anyMatch(IProblem::isError);

    // Update builder
    result.offsetMapper = parsableMapper.thenMapping(compilableMapper);
    result.javaCode = compilableStage;
    result.compilationUnit = bindingsCU;

    // Build it
    return result.build();
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



  /// CLASSPATHS ---------------------------------------------------------------

  private RuntimePathBuilder runtimePathBuilder = new RuntimePathBuilder();

  /// --------------------------------------------------------------------------


  /**
   * Emit events and update internal state (isEnabled) if java tabs added or modified.
   *
   * @param hasJavaTabs True if java tabs are in the sketch and false otherwise.
   */
  public void handleHasJavaTabsChange(boolean hasJavaTabs) {
    isEnabled = !hasJavaTabs;
    if (isEnabled) {
      notifySketchChanged();
    } else {
      preprocessingTask.cancel(false);
    }
  }

}
