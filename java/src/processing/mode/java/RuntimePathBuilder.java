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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.classpath.ClassPathFactory;

import processing.app.Library;
import processing.app.Messages;
import processing.app.Sketch;
import processing.app.SketchException;
import processing.app.Util;


/**
 * Builder which generates runtime paths using a series of caches.
 *
 * <p>
 * The runtime path is dependent on the java runtime, libraries, code folder contents and is used
 * both to determine classpath in sketch execution and to determine import suggestions within the
 * editor. This builder determines those paths using a mixture of inputs (modules, jars, etc)
 * and manages when those paths need to be recalculated (like with addition of a new library).
 * Note that this is a wrapper around com.google.classpath.ClassPathFactory which is where the
 * classpath is actually determined.
 * </p>
 */
public class RuntimePathBuilder {

  /*
   * ==============================================================
   * === List of modules and jars part of standard distribution ===
   * ==============================================================
   *
   * List of modules and to be included as part of a "standard" distribution which, at this point,
   * contains the standard library and JFX.
   */

  /**
   * The modules comprising the Java standard modules.
   */
  @SuppressWarnings("SpellCheckingInspection")
  protected static final String[] STANDARD_MODULES = {
      "java.base.jmod",
      "java.compiler.jmod",
      "java.datatransfer.jmod",
      "java.desktop.jmod",
      "java.instrument.jmod",
      "java.logging.jmod",
      "java.management.jmod",
      "java.management.rmi.jmod",
      "java.naming.jmod",
      "java.net.http.jmod",
      "java.prefs.jmod",
      "java.rmi.jmod",
      "java.scripting.jmod",
      "java.se.jmod",
      "java.security.jgss.jmod",
      "java.security.sasl.jmod",
      "java.smartcardio.jmod",
      "java.sql.jmod",
      "java.sql.rowset.jmod",
      "java.transaction.xa.jmod",
      "java.xml.crypto.jmod",
      "java.xml.jmod",
      "jdk.accessibility.jmod",
      "jdk.aot.jmod",
      "jdk.attach.jmod",
      "jdk.charsets.jmod",
      "jdk.compiler.jmod",
      "jdk.crypto.cryptoki.jmod",
      "jdk.crypto.ec.jmod",
      "jdk.dynalink.jmod",
      "jdk.editpad.jmod",
      "jdk.hotspot.agent.jmod",
      "jdk.httpserver.jmod",
      "jdk.internal.ed.jmod",
      "jdk.internal.jvmstat.jmod",
      "jdk.internal.le.jmod",
      "jdk.internal.opt.jmod",
      "jdk.internal.vm.ci.jmod",
      "jdk.internal.vm.compiler.jmod",
      "jdk.internal.vm.compiler.management.jmod",
      "jdk.jartool.jmod",
      "jdk.javadoc.jmod",
      "jdk.jcmd.jmod",
      "jdk.jconsole.jmod",
      "jdk.jdeps.jmod",
      "jdk.jdi.jmod",
      "jdk.jdwp.agent.jmod",
      "jdk.jfr.jmod",
      "jdk.jlink.jmod",
      "jdk.jshell.jmod",
      "jdk.jsobject.jmod",
      "jdk.jstatd.jmod",
      "jdk.localedata.jmod",
      "jdk.management.agent.jmod",
      "jdk.management.jfr.jmod",
      "jdk.management.jmod",
      "jdk.naming.dns.jmod",
      "jdk.naming.rmi.jmod",
      "jdk.net.jmod",
      "jdk.pack.jmod",
      "jdk.rmic.jmod",
      "jdk.scripting.nashorn.jmod",
      "jdk.scripting.nashorn.shell.jmod",
      "jdk.sctp.jmod",
      "jdk.security.auth.jmod",
      "jdk.security.jgss.jmod",
      "jdk.unsupported.desktop.jmod",
      "jdk.unsupported.jmod",
      "jdk.xml.dom.jmod",
      "jdk.zipfs.jmod"
  };

  /**
   * The jars required for OpenJFX.
   */
  protected static final String[] JAVA_FX_JARS = {
      "javafx-swt.jar",
      "javafx.base.jar",
      "javafx.controls.jar",
      "javafx.fxml.jar",
      "javafx.graphics.jar",
      "javafx.media.jar",
      "javafx.swing.jar",
      "javafx.web.jar"
  };

  /*
   * ======================
   * === Path factories ===
   * ======================
   *
   * There are multiple types of paths that are used in different contexts (sketch class path and
   * import recommendations) and that are required to be re-calculated due to different events.
   *
   * The following collections determine which types of paths apply in each and are assigned in the
   * constructor. Note that often factories are included in more than one of these collections
   * and are cached independently as their values are invalidated at different events.
   */

  // Path caches that are invalidated by one or more events within processing.
  private final List<CachedRuntimePathFactory> libraryDependentCaches;
  private final List<CachedRuntimePathFactory> libraryImportsDependentCaches;
  private final List<CachedRuntimePathFactory> codeFolderDependentCaches;

  // Path factories involved in determining sketch class path
  private final List<CachedRuntimePathFactory> sketchClassPathStrategies;

  // Path factories involved in determining sketch search path (import recommendations)
  private final List<CachedRuntimePathFactory> searchClassPathStrategies;

  // Inner class path factory
  private final ClassPathFactory classPathFactory;

  /*
   * ======================
   * === Public Methods ===
   * ======================
   */

  /**
   * Create a new runtime path builder with empty caches.
   */
  public RuntimePathBuilder() {

    // Create the inner classpath factory.
    classPathFactory  = new ClassPathFactory();

    // Build caches
    CachedRuntimePathFactory javaRuntimePathFactory = new CachedRuntimePathFactory(
        this::buildJavaRuntimePath
    );
    CachedRuntimePathFactory javaFxRuntimePathFactory = new CachedRuntimePathFactory(
        this::buildJavaFxRuntimePath
    );
    CachedRuntimePathFactory modeSketchPathFactory = new CachedRuntimePathFactory(
        this::buildModeSketchPath
    );
    CachedRuntimePathFactory modeSearchPathFactory = new CachedRuntimePathFactory(
        this::buildModeSearchPath
    );
    CachedRuntimePathFactory librarySketchPathFactory = new CachedRuntimePathFactory(
        this::buildLibrarySketchPath
    );
    CachedRuntimePathFactory librarySearchPathFactory = new CachedRuntimePathFactory(
        this::buildLibrarySearchPath
    );
    CachedRuntimePathFactory coreLibraryPathFactory = new CachedRuntimePathFactory(
        this::buildCoreLibraryPath
    );
    CachedRuntimePathFactory codeFolderPathFactory = new CachedRuntimePathFactory(
        this::buildCodeFolderPath
    );

    // Create collections for strategies
    sketchClassPathStrategies = new ArrayList<>();
    searchClassPathStrategies = new ArrayList<>();
    libraryDependentCaches = new ArrayList<>();
    libraryImportsDependentCaches = new ArrayList<>();
    codeFolderDependentCaches = new ArrayList<>();

    // Strategies required for the sketch class path at sketch execution
    sketchClassPathStrategies.add(javaRuntimePathFactory);
    sketchClassPathStrategies.add(javaFxRuntimePathFactory);
    sketchClassPathStrategies.add(modeSketchPathFactory);
    sketchClassPathStrategies.add(librarySketchPathFactory);
    sketchClassPathStrategies.add(coreLibraryPathFactory);
    sketchClassPathStrategies.add(codeFolderPathFactory);

    // Strategies required for import suggestions
    searchClassPathStrategies.add(javaRuntimePathFactory);
    searchClassPathStrategies.add(javaFxRuntimePathFactory);
    searchClassPathStrategies.add(modeSearchPathFactory);
    searchClassPathStrategies.add(librarySearchPathFactory);
    searchClassPathStrategies.add(coreLibraryPathFactory);
    searchClassPathStrategies.add(codeFolderPathFactory);

    // Assign strategies to collections for cache invalidation on library events.
    libraryDependentCaches.add(coreLibraryPathFactory);
    libraryImportsDependentCaches.add(librarySketchPathFactory);
    libraryImportsDependentCaches.add(librarySearchPathFactory);

    // Assign strategies to collections for cache invalidation on code folder changes.
    codeFolderDependentCaches.add(codeFolderPathFactory);
  }

  /**
   * Invalidate all the runtime path caches associated with sketch libraries.
   */
  public void markLibrariesChanged() {
    invalidateAll(libraryDependentCaches);
  }

  /**
   * Invalidate all the runtime path caches associated with sketch library imports.
   */
  public void markLibraryImportsChanged() {
    invalidateAll(libraryImportsDependentCaches);
  }

  /**
   * Invalidate all the runtime path caches associated with the code folder having changed.
   */
  public void markCodeFolderChanged() {
    invalidateAll(codeFolderDependentCaches);
  }

  /**
   * Generate a classpath and inject it into a {PreprocessedSketch.Builder}.
   *
   * @param result The {PreprocessedSketch.Builder} into which the classpath should be inserted.
   * @param mode The {JavaMode} for which the classpath should be generated.
   */
  public void prepareClassPath(PreprocSketch.Builder result, JavaMode mode) {
    List<ImportStatement> programImports = result.programImports;
    Sketch sketch = result.sketch;

    prepareSketchClassPath(result, mode, programImports, sketch);
    prepareSearchClassPath(result, mode, programImports, sketch);
  }

  /**
   * Invalidate all of the caches in a provided collection.
   *
   * @param caches The caches to invalidate so that, when their value is requested again, the value
   *    is generated again.
   */
  static private void invalidateAll(List<CachedRuntimePathFactory> caches) {
    for (CachedRuntimePathFactory cache : caches) {
      cache.invalidateCache();
    }
  }

  /**
   * Prepare the classpath required for the sketch's execution.
   *
   * @param result The PreprocessedSketch builder into which the classpath and class loader should
   *    be injected.
   * @param mode The JavaMode for which a sketch classpath should be generated.
   * @param programImports The imports listed by the sketch (user imports).
   * @param sketch The sketch for which the classpath is being generated.
   */
  private void prepareSketchClassPath(PreprocSketch.Builder result, JavaMode mode,
        List<ImportStatement> programImports, Sketch sketch) {

    Stream<String> sketchClassPath = sketchClassPathStrategies.stream()
        .flatMap((x) -> x.buildClasspath(mode, programImports, sketch).stream());

    String[] classPathArray = sketchClassPath.toArray(String[]::new);
    URL[] urlArray = Arrays.stream(classPathArray)
        .map(path -> {
          try {
            return Paths.get(path).toUri().toURL();
          } catch (MalformedURLException e) {
            Messages.err("malformed URL when preparing sketch classloader", e);
            return null;
          }
        })
        .filter(Objects::nonNull)
        .toArray(URL[]::new);

    result.classLoader = new URLClassLoader(urlArray, null);
    result.classPath = classPathFactory.createFromPaths(classPathArray);
    result.classPathArray = classPathArray;
  }

  /**
   * Prepare the classpath for searching in case of import suggestions.
   *
   * @param result The PreprocessedSketch builder into which the search classpath should be
   *    injected.
   * @param mode The JavaMode for which a sketch classpath should be generated.
   * @param programImports The imports listed by the sketch (user imports).
   * @param sketch The sketch for which the classpath is being generated.
   */
  private void prepareSearchClassPath(PreprocSketch.Builder result, JavaMode mode,
        List<ImportStatement> programImports, Sketch sketch) {

    Stream<String> searchClassPath = searchClassPathStrategies.stream()
        .flatMap((x) -> x.buildClasspath(mode, programImports, sketch).stream());

    result.searchClassPathArray = searchClassPath.toArray(String[]::new);
  }

  /*
   * ===============================================================
   * ====== Methods for determining different kinds of paths. ======
   * ===============================================================
   *
   * Methods which help determine different paths for different types of classpath entries. Note
   * that these are protected so that they can be tested.
   */

  /**
   * Enumerate the modules as part of the java runtime.
   *
   * @param mode The mode engaged by the user like JavaMode.
   * @param imports The program imports imposed by the user within their sketch.
   * @param sketch The sketch provided by the user.
   * @return List of classpath and/or module path entries.
   */
  protected List<String> buildJavaRuntimePath(JavaMode mode, List<ImportStatement> imports,
        Sketch sketch) {

    return Arrays.stream(STANDARD_MODULES)
        .map(this::buildForModule)
        .collect(Collectors.toList());
  }

  /**
   * Enumerate the modules as part of the java runtime.
   *
   * @param mode The mode engaged by the user like JavaMode.
   * @param imports The program imports imposed by the user within their sketch.
   * @param sketch The sketch provided by the user.
   * @return List of classpath and/or module path entries.
   */
  protected List<String> buildJavaFxRuntimePath(JavaMode mode, List<ImportStatement> imports,
        Sketch sketch) {

    return Arrays.stream(JAVA_FX_JARS)
        .map(this::findFullyQualifiedJarName)
        .collect(Collectors.toList());
  }

  /**
   * Enumerate paths for resources like jars within the sketch code folder.
   *
   * @param mode The mode engaged by the user like JavaMode.
   * @param imports The program imports imposed by the user within their sketch.
   * @param sketch The sketch provided by the user.
   * @return List of classpath and/or module path entries.
   */
  protected List<String> buildCodeFolderPath(JavaMode mode, List<ImportStatement> imports,
        Sketch sketch) {

    StringBuilder classPath = new StringBuilder();

    // Code folder
    if (sketch.hasCodeFolder()) {
      File codeFolder = sketch.getCodeFolder();
      String codeFolderClassPath = Util.contentsToClassPath(codeFolder);
      classPath.append(codeFolderClassPath);
    }

    return sanitizeClassPath(classPath.toString());
  }

  /**
   * Determine paths for libraries part of the processing mode (like {JavaMode}).
   *
   * @param mode The mode engaged by the user like JavaMode.
   * @param imports The program imports imposed by the user within their sketch.
   * @param sketch The sketch provided by the user.
   * @return List of classpath and/or module path entries.
   */
  protected List<String> buildCoreLibraryPath(JavaMode mode, List<ImportStatement> imports,
        Sketch sketch) {

    StringBuilder classPath = new StringBuilder();

    for (Library lib : mode.coreLibraries) {
      classPath.append(File.pathSeparator).append(lib.getClassPath());
    }

    return sanitizeClassPath(classPath.toString());
  }

  /**
   * Generate classpath entries for third party libraries that are required for running the sketch.
   *
   * @param mode The mode engaged by the user like JavaMode.
   * @param imports The program imports imposed by the user within their sketch.
   * @param sketch The sketch provided by the user.
   * @return List of classpath and/or module path entries.
   */
  protected List<String> buildLibrarySketchPath(JavaMode mode,
                                                List<ImportStatement> imports,
                                                Sketch sketch) {

    StringJoiner classPathBuilder = new StringJoiner(File.pathSeparator);

    imports.stream()
        .map(ImportStatement::getPackageName)
        .filter(pkg -> !isIgnorableForSketchPath(pkg))
        .map(pkg -> {
          try {
            return mode.getLibrary(pkg);
          } catch (SketchException e) {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .map(Library::getClassPath)
        .forEach(classPathBuilder::add);

    return sanitizeClassPath(classPathBuilder.toString());
  }

  /**
   * Generate classpath entries for third party libraries that are used when determining import
   * recommendations.
   *
   * @param mode The mode engaged by the user like JavaMode.
   * @param imports The program imports imposed by the user within their sketch.
   * @param sketch The sketch provided by the user.
   * @return List of classpath and/or module path entries.
   */
  protected List<String> buildLibrarySearchPath(JavaMode mode, List<ImportStatement> imports,
        Sketch sketch) {

    StringJoiner classPathBuilder = new StringJoiner(File.pathSeparator);

    for (Library lib : mode.contribLibraries) {
      classPathBuilder.add(lib.getClassPath());
    }

    return sanitizeClassPath(classPathBuilder.toString());
  }

  /**
   * Generate classpath entries for the processing mode (like {JavaMode}) used when making import
   * recommendations.
   *
   * @param mode The mode engaged by the user like JavaMode.
   * @param imports The program imports imposed by the user within their sketch.
   * @param sketch The sketch provided by the user.
   * @return List of classpath and/or module path entries.
   */
  protected List<String> buildModeSearchPath(JavaMode mode, List<ImportStatement> imports,
        Sketch sketch) {

    String searchClassPath = mode.getSearchPath();

    if (searchClassPath != null) {
      return sanitizeClassPath(searchClassPath);
    } else {
      return new ArrayList<>();
    }
  }

  /**
   * Generate classpath entries required by the processing mode like {JavaMode}.
   *
   * @param mode The mode engaged by the user like JavaMode.
   * @param imports The program imports imposed by the user within their sketch.
   * @param sketch The sketch provided by the user.
   * @return List of classpath and/or module path entries.
   */
  protected List<String> buildModeSketchPath(JavaMode mode, List<ImportStatement> imports,
        Sketch sketch) {

    Library coreLibrary = mode.getCoreLibrary();
    String coreClassPath = coreLibrary != null ?
        coreLibrary.getClassPath() : mode.getSearchPath();
    if (coreClassPath != null) {
      return sanitizeClassPath(coreClassPath);
    } else {
      return new ArrayList<>();
    }
  }

  /*
   * ===============================================================
   * === Helper functions for the path generation methods above. ===
   * ===============================================================
   *
   * Note that these are made protected so that they can be tested.
   */

  /**
   * Remove invalid entries in a classpath string.
   *
   * @param classPathString The classpath to clean.
   * @return The cleaned classpath entries without invalid entries.
   */
  protected List<String> sanitizeClassPath(String classPathString) {
    // Make sure class path does not contain empty string (home dir)
    return Arrays.stream(classPathString.split(File.pathSeparator))
        .filter(p -> p != null && !p.trim().isEmpty())
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * Determine if a package is ignorable because it is standard.
   * This is different from being ignorable in imports recommendations.
   *
   * @param packageName The name of the package to evaluate.
   * @return True if the package is part of standard Java (like java.lang.*). False otherwise.
   */
  protected boolean isIgnorableForSketchPath(String packageName) {
    return (packageName.startsWith("java.") || packageName.startsWith("javax."));
  }

  /**
   * Find a fully qualified jar name.
   *
   * @param jarName The jar name like "javafx.base.jar" for which a
   *                fully qualified entry should be created.
   * @return The fully qualified classpath entry like ".../Processing.app/Contents/PlugIns/
   *    adoptopenjdk-11.0.1.jdk/Contents/Home/lib/javafx.base.jar"
   */
  protected String findFullyQualifiedJarName(String jarName) {
    StringJoiner joiner = new StringJoiner(File.separator);
    joiner.add(System.getProperty("java.home"));
    joiner.add("lib");
    joiner.add(jarName);

    return joiner.toString();
  }

  /**
   * Build a classpath entry for a module.
   *
   * @param moduleName The name of the module like "java.base.jmod".
   * @return The fully qualified classpath entry like ".../Processing.app/Contents/PlugIns/
   *    adoptopenjdk-11.0.1.jdk/Contents/Home/jmods/java.base.jmod"
   */
  protected String buildForModule(String moduleName) {
    StringJoiner jmodPathJoiner = new StringJoiner(File.separator);
    jmodPathJoiner.add(System.getProperty("java.home"));
    jmodPathJoiner.add("jmods");
    jmodPathJoiner.add(moduleName);
    return jmodPathJoiner.toString();
  }

  /*
   * ============================================
   * === Interface definitions and utilities. ===
   * ============================================
   *
   * Note that these are protected so that they can be tested. The interface below defines a
   * strategy for determining path elements. An optional caching object which allows for path
   * invalidation is also defined below.
   */

  /**
   * Strategy which generates part of the classpath and/or module path.
   *
   * <p>
   * Strategy for factories each of which generate part of the classpath and/or module path required
   * by a sketch through user supplied requirements, mode (as in JavaMode) requirements, or
   * transitive requirements imposed by third party libraries.
   * </p>
   */
  protected interface RuntimePathFactoryStrategy {

    /**
     * Create classpath and/or module path entries.
     *
     * @param mode The mode engaged by the user like JavaMode.
     * @param programImports The program imports imposed by the user within their sketch.
     * @param sketch The sketch provided by the user.
     * @return List of classpath and/or module path entries.
     */
    List<String> buildClasspath(JavaMode mode, List<ImportStatement> programImports, Sketch sketch);

  }

  /**
   * Runtime path factory which caches the results of another runtime path factory.
   *
   * <p>
   * Runtime path factory which decorates another {RuntimePathFactoryStrategy} that caches the
   * results of another runtime path factory. This is a lazy cached getter so the value will not be
   * resolved until it is requested.
   * </p>
   */
  protected static class CachedRuntimePathFactory implements RuntimePathFactoryStrategy {

    final private AtomicReference<List<String>> cachedResult;
    final private RuntimePathFactoryStrategy innerStrategy;

    /**
     * Create a new cache around {RuntimePathFactoryStrategy}.
     *
     * @param newInnerStrategy The strategy to cache.
     */
    public CachedRuntimePathFactory(RuntimePathFactoryStrategy newInnerStrategy) {
      cachedResult = new AtomicReference<>(null);
      innerStrategy = newInnerStrategy;
    }

    /**
     * Invalidate the cached path so that, when requested next time, it will be rebuilt from
     * scratch.
     */
    public void invalidateCache() {
      cachedResult.set(null);
    }

    /**
     * Return the cached classpath or, if not cached, build a classpath using the inner strategy.
     *
     * <p>
     * Return the cached classpath or, if not cached, build a classpath using the inner strategy.
     * Note that this getter will not check to see if mode, imports, or sketch have changed. If a
     * cached value is available, it will be returned without examining the identity of the
     * parameters.
     * </p>
     *
     * @param mode The {JavaMode} for which the classpath should be built.
     * @param imports The sketch (user) imports.
     * @param sketch The sketch for which a classpath is to be returned.
     * @return Newly generated classpath.
     */
    @Override
    public List<String> buildClasspath(JavaMode mode, List<ImportStatement> imports,
          Sketch sketch) {

      return cachedResult.updateAndGet((cachedValue) ->
          cachedValue == null ? innerStrategy.buildClasspath(mode, imports, sketch) : cachedValue
      );
    }

  }

}
