/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org

Copyright (c) 2012-23 The Processing Foundation
Copyright (c) 2004-12 Ben Fry and Casey Reas
Copyright (c) 2001-04 Massachusetts Institute of Technology

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License version 2
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation,
Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.mode.java;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;

import processing.app.*;
import processing.app.exec.ProcessHelper;
import processing.app.platform.MacPlatform;
import processing.app.ui.ExportPrompt;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.data.StringList;
import processing.data.XML;
import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.PreprocessorResult;


public class JavaBuild {
  static public final String PACKAGE_REGEX =
    "(?:^|\\s|;)package\\s+(\\S+)\\;";

  static public final String MIN_JAVA_VERSION = "17";

  protected Sketch sketch;
  protected Mode mode;

  // what happens in the build, stays in the build.
  // (which is to say that everything below this line, stays within this class)

  protected File srcFolder;
  protected File binFolder;
  private boolean foundMain = false;
  private String classPath;
  protected String sketchClassName;

  /**
   * This will include the code folder, any library folders, etc. that might
   * contain native libraries that need to be picked up with java.library.path.
   * This is *not* the "Processing" libraries path, this is the Java libraries
   * path, as in java.library.path=BlahBlah, which identifies search paths for
   * DLLs or JNILIBs. (It's Java's LD_LIBRARY_PATH, for you UNIX fans.)
   * This is set by the preprocessor as it figures out where everything is.
   */
  private String javaLibraryPath;

  /** List of library folders, as figured out during preprocessing. */
  private List<Library> importedLibraries;


  public JavaBuild(Sketch sketch) {
    this.sketch = sketch;
    this.mode = sketch.getMode();
  }


  /**
   * Run the build inside a temporary build folder. Used for run/present.
   * @return null if compilation failed, main class name if not
   * @throws SketchException details of where the build choked
   */
  public String build(boolean sizeWarning) throws SketchException {
    return build(sketch.makeTempFolder(), sketch.makeTempFolder(), sizeWarning);
  }


  /**
   * Preprocess and compile all the code for this sketch.
   *
   * In an advanced program, the returned class name could be different,
   * which is why the className is set based on the return value.
   * A compilation error will burp up a RunnerException.
   *
   * @return null if compilation failed, main class name if not
   */
  public String build(File srcFolder, File binFolder, boolean sizeWarning) throws SketchException {
    this.srcFolder = srcFolder;
    this.binFolder = binFolder;

    // run the preprocessor
    PreprocessorResult result = preprocess(srcFolder, sizeWarning);
    String classNameFound = result.getClassName();

    // compile the program. errors will happen as a RunnerException
    // that will bubble up to whomever called build().
    if (Compiler.compile(this)) {
      sketchClassName = classNameFound;
      return classNameFound;
    }
    return null;
  }


  public String getSketchClassName() {
    return sketchClassName;
  }


  /**
   * Build all the code for this sketch.
   *
   * In an advanced program, the returned class name could be different,
   * which is why the className is set based on the return value.
   * A compilation error will burp up a RunnerException.
   *
   * Setting purty to 'true' will cause exception line numbers to be incorrect.
   * Unless you know the code compiles, you should first run the preprocessor
   * with purty set to false to make sure there are no errors, then once
   * successful, re-export with purty set to true.
   *
   * @param srcFolder Location to copy all the .java files
   * @return null if compilation failed, main class name if not
   */
  public PreprocessorResult preprocess(File srcFolder, boolean sizeWarning) throws SketchException {
    PdePreprocessor preprocessor = PdePreprocessor.builderFor(sketch.getMainName()).build();
    return preprocess(srcFolder, null, preprocessor, sizeWarning);
  }


  /**
   * @param srcFolder location where the .java source files will be placed
   * @param packageName null, or the package name that should be used as default
   * @param preprocessor the preprocessor object ready to do the work
   * @return PreprocessorResult object containing the preprocessing results
   * @throws SketchException details of where the preprocessing failed
   */
  public PreprocessorResult preprocess(File srcFolder,
                                       String packageName,
                                       PdePreprocessor preprocessor,
                                       boolean sizeWarning) throws SketchException {
    // make sure the user isn't playing "hide the sketch folder"
    sketch.ensureExistence();

//    System.out.println("srcFolder is " + srcFolder);
    classPath = binFolder.getAbsolutePath();

    // figure out the contents of the code folder to see if there
    // are files that need to be added to the imports
    StringList codeFolderPackages = null;
    if (sketch.hasCodeFolder()) {
      File codeFolder = sketch.getCodeFolder();
      javaLibraryPath = codeFolder.getAbsolutePath();

      // get a list of .jar files in the "code" folder
      // (class files in subfolders should also be picked up)
      String codeFolderClassPath =
        Util.contentsToClassPath(codeFolder);
      // append the jar files in the code folder to the class path
      classPath += File.pathSeparator + codeFolderClassPath;
      // get list of packages found in those jars
      codeFolderPackages =
        Util.packageListFromClassPath(codeFolderClassPath);

    } else {
      javaLibraryPath = "";
    }

    // 1. concatenate all .pde files to the 'main' pde
    //    store line number for starting point of each code bit

    StringBuilder bigCode = new StringBuilder();
    int bigCount = 0;
    List<Integer> linesPerTab = new ArrayList<>();
    for (SketchCode sc : sketch.getCode()) {
      if (sc.isExtension("pde")) {
        sc.setPreprocOffset(bigCount);
        bigCode.append(sc.getProgram());
        bigCode.append('\n');
        linesPerTab.add(bigCount);
        bigCount += sc.getLineCount();
      }
    }
    linesPerTab.add(bigCount);

//    // initSketchSize() sets the internal sketchWidth/Height/Renderer vars
//    // in the preprocessor. Those are used in preproc.write() so that they
//    // can be turned into sketchXxxx() methods.
//    // This also returns the size info as an array so that we can figure out
//    // if this fella is OpenGL, and if so, to add the import. It's messy and
//    // gross and someday we'll just always include OpenGL.
//    String[] sizeInfo =

/* next line commented out for ANTLR 4 - PdePreprocessor now does this when
 * walking the tree
 */
//    preprocessor.initSketchSize(sketch.getMainProgram(), sizeWarning);

//      //PdePreprocessor.parseSketchSize(sketch.getMainProgram(), false);
//    if (sizeInfo != null) {
//      String sketchRenderer = sizeInfo[3];
//      if (sketchRenderer != null) {
//        if (sketchRenderer.equals("P2D") ||
//            sketchRenderer.equals("P3D") ||
//            sketchRenderer.equals("OPENGL")) {
//          bigCode.insert(0, "import processing.opengl.*; ");
//        }
//      }
//    }

    PreprocessorResult result;
    try {
      File outputFolder = (packageName == null) ?
          srcFolder : new File(srcFolder, packageName.replace('.', '/'));
      outputFolder.mkdirs();
//      Base.openFolder(outputFolder);
      final File java = new File(outputFolder, sketch.getMainName() + ".java");
      try (PrintWriter stream = PApplet.createWriter(java)) {
        result = preprocessor.write(stream, bigCode.toString(), codeFolderPackages);
      }
    } catch (SketchException pe) {
      // RunnerExceptions are caught here and re-thrown, so that they don't
      // get lost in the more general "Exception" handler below.
      throw pe;

    } catch (Exception ex) {
      // TODO better method for handling this?
      System.err.println("Uncaught exception type:" + ex.getClass());
      ex.printStackTrace();
      throw new SketchException(ex.toString());
    }

    if (result.getPreprocessIssues().size() > 0) {
      Problem problem = ProblemFactory.build(
          result.getPreprocessIssues().get(0),
          linesPerTab
      );

      throw new SketchException(
          problem.getMessage(),
          problem.getTabIndex(),
          problem.getLineNumber() - 1,
          0
      );
    }

    // grab the imports from the code just preprocessed

    importedLibraries = new ArrayList<>();
    Library core = mode.getCoreLibrary();
    if (core != null) {
      importedLibraries.add(core);
      classPath += core.getClassPath();
      javaLibraryPath += File.pathSeparator + core.getNativePath();
    }

    for (ImportStatement item : result.getImportStatements()) {
      String entry = item.getPackageName();
      Library library = mode.getLibrary(entry);

      if (library != null) {
        if (!importedLibraries.contains(library)) {
          importedLibraries.add(library);
          // don't add the JavaFX libraries to the classpath
          // https://github.com/processing/processing4/issues/212
          // Disabling this after all, because we need our javafx.jar (PGraphicsJavaFX)
          // and the JavaFX .jars are safely tucked into a "modules" subfolder
          //if (!library.getName().equals("JavaFX")) {
          classPath += library.getClassPath();
          //}
          javaLibraryPath += File.pathSeparator + library.getNativePath();
        }
      } else {
        boolean found = false;
        // If someone insists on unnecessarily repeating the code folder
        // import, don't show an error for it.
        if (codeFolderPackages != null) {
          for (String pkg : codeFolderPackages) {
            if (pkg.equals(entry)) {
              found = true;
              break;
            }
          }
        }
        if (ignorableImport(entry + '.')) {
          found = true;
        }
        if (!found) {
          System.err.println("No library found for " + entry);
        }
      }
    }

    // Turning this off after 4.0 alpha 5, to see if everything still works.
    // Including this classpath is really problematic (many possible conflicts,
    // these are classes that won't be available on export, etc etc...)
    // Also avoids accidentally hiding other potential classpath bugs.
    /*
    // Finally, add the regular Java CLASSPATH. This contains everything
    // imported by the PDE itself (core.jar, pde.jar, quaqua.jar) which may
    // in fact be more of a problem.
    String javaClassPath = System.getProperty("java.class.path");
    // Remove quotes if any.. A messy (and frequent) Windows problem
    if (javaClassPath.startsWith("\"") && javaClassPath.endsWith("\"")) {
      javaClassPath = javaClassPath.substring(1, javaClassPath.length() - 1);
    }
    classPath += File.pathSeparator + javaClassPath;
    */

    // But make sure that there isn't anything in there that's missing,
    // otherwise ECJ will complain and die. For instance, Java 1.7 (or maybe
    // it's appbundler?) adds Java/Classes to the path, which kills us.
    //String[] classPieces = PApplet.split(classPath, File.pathSeparator);
    // Nah, nevermind... we'll just create the @!#$! folder until they fix it.

    // 3. then loop over the code[] and save each .java file

    for (SketchCode sc : sketch.getCode()) {
      if (sc.isExtension("java")) {
        // In most cases, no pre-processing services necessary for Java files.
        // Just write the the contents of 'program' to a .java file
        // into the build directory. However, if a default package is being
        // used (as in Android), and no package is specified in the source,
        // then we need to move this code to the same package as the sketch.
        // Otherwise, the class may not be found, or at a minimum, the default
        // access across the packages will mean that things behave incorrectly.
        // For instance, desktop code that uses a .java file with no packages,
        // will be fine with the default access, but since Android's PApplet
        // requires a package, code from that (default) package (such as the
        // PApplet itself) won't have access to methods/variables from the
        // package-less .java file (unless they're all marked public).
        String filename = sc.getFileName();
        try {
          String javaCode = sc.getProgram();
          String[] packageMatch = PApplet.match(javaCode, PACKAGE_REGEX);
          // if no package, and a default package is being used
          // (i.e. on Android) we'll have to add one

          if (packageMatch == null && packageName == null) {
            sc.copyTo(new File(srcFolder, filename));

          } else {
            if (packageMatch == null) {
              // use the default package name, since mixing with package-less code will break
              packageMatch = new String[]{"", packageName};
              // add the package name to the source before writing it
              javaCode = "package " + packageName + ";" + javaCode;
            }
            File packageFolder = new File(srcFolder, packageMatch[1].replace('.', File.separatorChar));
            packageFolder.mkdirs();
            Util.saveFile(javaCode, new File(packageFolder, filename));
          }
        } catch (IOException e) {
          e.printStackTrace();
          String msg = "Problem moving " + filename + " to the build folder";
          throw new SketchException(msg);
        }

      } else if (sc.isExtension("pde")) {
        // The compiler and runner will need this to have a proper offset
        sc.addPreprocOffset(result.getHeaderOffset());
      }
    }
    foundMain = preprocessor.hasMain();
    return result;
  }


  /**
   * Returns true if this package isn't part of a library (it's a system import
   * or something like that). Don't bother complaining about java.* or javax.*
   * because it's probably in boot.class.path. But we're not checking against
   * that path since it's enormous. Unfortunately we do still have to check
   * for libraries that begin with a prefix like javax, since that includes
   * the OpenGL library, even though we're just returning true here, hrm...
   */
  @SuppressWarnings("RedundantIfStatement")
  protected boolean ignorableImport(String pkg) {
    if (pkg.startsWith("java.")) return true;
    if (pkg.startsWith("javax.")) return true;
    if (pkg.startsWith("javafx.")) return true;

    if (pkg.startsWith("processing.core.")) return true;
    if (pkg.startsWith("processing.data.")) return true;
    if (pkg.startsWith("processing.event.")) return true;
    if (pkg.startsWith("processing.opengl.")) return true;

    if (pkg.startsWith("org.omg")) return true;
    if (pkg.startsWith("org.w3c.dom")) return true;
    if (pkg.startsWith("org.xml.sax")) return true;

    return false;
  }


  protected int findErrorFile(int errorLine) {
    for (int i = sketch.getCodeCount() - 1; i > 0; i--) {
      SketchCode sc = sketch.getCode(i);
      if (sc.isExtension("pde") && (sc.getPreprocOffset() <= errorLine)) {
        // keep looping until the errorLine is past the offset
        return i;
      }
    }
    return 0;  // i give up
  }


  /**
   * Path to the folder that will contain processed .java source files. Not
   * the location for .pde files, since that can be obtained from the sketch.
   */
  public File getSrcFolder() {
    return srcFolder;
  }


  public File getBinFolder() {
    return binFolder;
  }


  /**
   * Absolute path to the sketch folder. Used to set the working directry of
   * the sketch when running, i.e. so that saveFrame() goes to the right
   * location when running from the PDE, instead of the same folder as the
   * Processing.exe or the root of the user's home dir.
   */
  public String getSketchPath() {
    return sketch.getFolder().getAbsolutePath();
  }


  /** Class path determined during build. */
  public String getClassPath() {
    return classPath;
  }


//  /** Returns the dummy "module" path so that JavaFX doesn't complain. */
//  public String getModulePath() {
//    // Just set this to the main core/library directory to pick up JavaFX
//    //return mode.getCoreLibrary().getLibraryPath();
//    File folder = new File(mode.getFolder(), "libraries/javafx/library");
//    return folder.getAbsolutePath();
//  }


  /** Return the java.library.path for this sketch (for all the native DLLs etc). */
  public String getJavaLibraryPath() {
    return javaLibraryPath;
  }


  /**
   * Whether the preprocessor found a main() method. If main() is found, then
   * it will be used to launch the sketch instead of PApplet.main().
   */
  public boolean getFoundMain() {
    return foundMain;
  }


  /**
   * Get the list of imported libraries. Used by external tools like Android mode.
   * @return list of library folders connected to this sketch.
   */
  public List<Library> getImportedLibraries() {
    return importedLibraries;
  }


  /**
   * Map an error from a set of processed .java files back to its location
   * in the actual sketch.
   * @param message The error message.
   * @param dotJavaFilename The .java file where the exception was found.
   * @param dotJavaLine Line number of the .java file for the exception (0-indexed!)
   * @return A RunnerException to be sent to the editor, or null if it wasn't
   *         possible to place the exception to the sketch code.
   */
  public SketchException placeException(String message,
                                        String dotJavaFilename,
                                        int dotJavaLine) {
    int codeIndex = 0; //-1;
    int codeLine = -1;

    //System.out.println(message + " placing " + dotJavaFilename + " " + dotJavaLine);
    //System.out.println("code count is " + getCodeCount());

    // first check to see if it's a .java file
    for (int i = 0; i < sketch.getCodeCount(); i++) {
      SketchCode code = sketch.getCode(i);
      if (code.isExtension("java")) {
        if (dotJavaFilename.equals(code.getFileName())) {
          codeIndex = i;
          codeLine = dotJavaLine;
          return new SketchException(message, codeIndex, codeLine);
        }
      }
    }

    // If not the preprocessed file at this point, then need to get out
    if (!dotJavaFilename.equals(sketch.getMainName() + ".java")) {
      return null;
    }

    // if it's not a .java file, codeIndex will still be 0
    // this section searches through the list of .pde files
    codeIndex = 0;
    for (int i = 0; i < sketch.getCodeCount(); i++) {
      SketchCode code = sketch.getCode(i);

      if (code.isExtension("pde")) {
        //System.out.println("preproc offset is " + code.getPreprocOffset());
        //System.out.println("looking for line " + dotJavaLine);
        if (code.getPreprocOffset() <= dotJavaLine) {
          codeIndex = i;
//          System.out.println("i'm thinkin file " + i);
          codeLine = dotJavaLine - code.getPreprocOffset();
        }
      }
    }
    // could not find a proper line number, so deal with this differently.
    // but if it was in fact the .java file we're looking for, though,
    // send the error message through.
    // this is necessary because 'import' statements will be at a line
    // that has a lower number than the preproc offset, for instance.
//    if (codeLine == -1 && !dotJavaFilename.equals(name + ".java")) {
//      return null;
//    }
//    return new SketchException(message, codeIndex, codeLine);
    return new SketchException(message, codeIndex, codeLine, -1, false);  // changed for 0194 for compile errors, but...
  }


  /**
   * Export to application via GUI.
   */
  protected boolean exportApplication() throws IOException, SketchException {
    // Do the build once, so that we know what libraries are in use (and what
    // the situation is with their native libs), and also for efficiency of
    // not redoing the compilation for each platform. In particular, though,
    // importedLibraries won't be set until the preprocessing has finished,
    // so we have to do that before the stuff below.
    String foundName = build(true);

    // (already reported) error during export, exit this function
    if (foundName == null) return false;

    // if name != exportSketchName, then that's weirdness
    // BUG unfortunately, that can also be a bug in the preproc :(
    if (!sketch.getMainName().equals(foundName)) {
      Messages.showWarning("Error during export",
                           "Main tab is named " + sketch.getMainName() + " but the\n" +
                           "sketch name in the code was " + foundName, null);
      return false;
    }

    /*
    for (StringDict.Entry entry : Platform.getSupportedVariants().entries()) {
      String variant = entry.key;
      String name = entry.value;
    }
    */

    final String hostVariant = Platform.getVariant();
    for (String variant : Preferences.get(ExportPrompt.EXPORT_VARIANTS).split(",")) {
      // Can only embed Java on the native platform
      boolean embedJava = variant.equals(hostVariant) &&
        Preferences.getBoolean("export.application.embed_java");

      File folder = new File(sketch.getFolder(), variant);
      if (!exportApplication(folder, variant, embedJava)) {
        return false;
      }
    }

//    File folder = null;
//    for (String platformName : PConstants.platformNames) {
////      int platform = Platform.getIndex(platformName);
//
//      // Can only embed Java on the native platform
//      boolean embedJava = (platform == PApplet.platform) &&
//        Preferences.getBoolean("export.application.embed_java");
//
//      if (Preferences.getBoolean(JavaEditor.EXPORT_PREFIX + platformName)) {
//        final int bits = Platform.getNativeBits();
//        final String arch = Platform.getNativeArch();
//
//        if (Library.hasMultipleArch(platform, importedLibraries)) {
//          // removing 32-bit export for 4.0 alpha 3
//          /*
//          // Don't try to export 32-bit on macOS, because it doesn't exist.
//          if (platform != PConstants.MACOS) {
//            // export the 32-bit version
//            folder = new File(sketch.getFolder(), "application." + platformName + "32");
//            if (!exportApplication(folder, platform, "32", embedJava && (bits == 32) && ("x86".equals(arch) || "i386".equals(arch)))) {
//              return false;
//            }
//          }
//          */
//          // export the 64-bit version
//          //folder = new File(sketch.getFolder(), "application." + platformName + "64");
//          // No longer including the 64 suffix in 4.0a3 because it's all 64-bit
//          folder = new File(sketch.getFolder(), "application." + platformName);
//          if (!exportApplication(folder, platform, "64", embedJava && (bits == 64) && "amd64".equals(arch))) {
//            return false;
//          }
//          /*
//          if (platform == PConstants.LINUX) {
//            // export the arm versions as well
//            folder = new File(sketch.getFolder(), "application.linux-armv6hf");
//            if (!exportApplication(folder, platform, "armv6hf", embedJava && (bits == 32) && "arm".equals(arch))) {
//              return false;
//            }
//            folder = new File(sketch.getFolder(), "application.linux-arm64");
//            if (!exportApplication(folder, platform, "arm64", embedJava && (bits == 64) && "aarch64".equals(arch))) {
//              return false;
//            }
//          }
//          */
//        } else { // just make a single one for this platform
//          folder = new File(sketch.getFolder(), "application." + platformName);
//          if (!exportApplication(folder, platform, "", embedJava)) {
//            return false;
//          }
//        }
//      }
//    }

    return true;  // all good
  }


  /**
   * Export to application without GUI. Also called by the Commander.
   */
  protected boolean exportApplication(File destFolder,
                                      String exportVariant,
                                      boolean embedJava) throws IOException, SketchException {
    for (Library library : importedLibraries) {
      if (library.isUnsupported(exportVariant)) {
        Messages.showWarning("Quibbles 'n Bits",
                             "The application will not be exported for\n" +
                             Platform.getSupportedVariants().get(exportVariant) +
                             " because " + library.getName() + "\n" +
                             "does not support " + exportVariant + ".", null);
        return true;  // don't cancel all exports for this, just move along
      }
    }


    /// getting started

    int exportPlatform = Platform.getIndex(exportVariant);
    mode.prepareExportFolder(destFolder);


    /// figure out where the jar files will be placed

    File jarFolder = new File(destFolder, "lib");


    /// where all the skeleton info lives

    /// on macosx, need to copy .app skeleton since that's
    /// also where the jar files will be placed
    File dotAppFolder = null;
    String jvmRuntime = "";
    String jdkPath = null;
    if (exportPlatform == PConstants.MACOS) {
      dotAppFolder = new File(destFolder, sketch.getName() + ".app");

      File contentsOrig = new File(Platform.getJavaHome(), "../../../..");

      if (embedJava) {
        File jdkFolder = new File(Platform.getJavaHome(), "../..");
        String jdkFolderName = jdkFolder.getCanonicalFile().getName();
        jvmRuntime = "<key>JVMRuntime</key>\n    <string>" + jdkFolderName + "</string>";
        jdkPath = new File(dotAppFolder, "Contents/PlugIns/" + jdkFolderName).getAbsolutePath();
      }

      File contentsFolder = new File(dotAppFolder, "Contents");
      contentsFolder.mkdirs();

      // Info.plist will be written later

      // set the jar folder to a different location than windows/linux
      //jarFolder = new File(dotAppFolder, "Contents/Resources/Java");
      jarFolder = new File(contentsFolder, "Java");

      File macosFolder = new File(contentsFolder, "MacOS");
      macosFolder.mkdirs();
      // This is an unsigned copy of the app binary (see build/build.xml)
      File stubFile =
        mode.getContentFile("application/stub-" + exportVariant);
      File execFile =
        new File(contentsFolder, "MacOS/" + sketch.getMainName());
      Util.copyFile(stubFile, execFile);
      if (!execFile.setExecutable(true)) {
        throw new IOException("Could not make " + execFile + " executable.");
      }

      File pkgInfo = new File(contentsFolder, "PkgInfo");
      PrintWriter writer = PApplet.createWriter(pkgInfo);
      writer.println("APPL????");
      writer.flush();
      writer.close();

      // Use faster(?) native copy here (also to do sym links)
      if (embedJava) {
        Util.copyDirNative(new File(contentsOrig, "PlugIns"),
                           new File(contentsFolder, "PlugIns"));
      }

      File resourcesFolder = new File(contentsFolder, "Resources");
      Util.copyDir(new File(contentsOrig, "Resources/en.lproj"),
                   new File(resourcesFolder, "en.lproj"));
      Util.copyFile(mode.getContentFile("application/application.icns"),
                    new File(resourcesFolder, "application.icns"));

    } else if (exportPlatform == PConstants.LINUX) {
      if (embedJava) {
        Util.copyDirNative(Platform.getJavaHome(), new File(destFolder, "java"));
      }

    } else if (exportPlatform == PConstants.WINDOWS) {
      if (embedJava) {
        Util.copyDir(Platform.getJavaHome(), new File(destFolder, "java"));
      }
    }


    /// make the jar folder (all platforms)

    if (!jarFolder.exists()) jarFolder.mkdirs();


    /// start copying all jar files

    StringList jarList = new StringList();


    /// create the main .jar file

    FileOutputStream zipOutputFile =
      new FileOutputStream(new File(jarFolder, sketch.getMainName() + ".jar"));
    ZipOutputStream zos = new ZipOutputStream(zipOutputFile);

    // add the manifest file so that the .jar can be double-clickable
    // (not likely to work anymore these days [fry 200925])
    addManifest(zos);

    addClasses(zos, binFolder);

    // add the data folder to the main jar file
//    addDataFolder(zos);
    // For 2.0a2, make the data folder a separate directory,
    // rather than packaging potentially large files into the JAR.
    // On macOS, we have to hide the folder inside the .app package,
    // while Linux and Windows will have a 'data' folder next to 'lib'.
    if (sketch.hasDataFolder()) {
      if (exportPlatform == PConstants.MACOS) {
        Util.copyDir(sketch.getDataFolder(), new File(jarFolder, "data"));
      } else {
        Util.copyDir(sketch.getDataFolder(), new File(destFolder, "data"));
      }
    }

    // add the contents of the code folder to the jar
    if (sketch.hasCodeFolder()) {
      String includes = Util.contentsToClassPath(sketch.getCodeFolder());
      // Use tokens to get rid of extra blanks, which causes huge exports
      String[] codeList = PApplet.splitTokens(includes, File.pathSeparator);
      for (int i = 0; i < codeList.length; i++) {
        if (codeList[i].toLowerCase().endsWith(".jar") ||
            codeList[i].toLowerCase().endsWith(".zip")) {
          File exportFile = new File(codeList[i]);
          String exportFilename = exportFile.getName();
          Util.copyFile(exportFile, new File(jarFolder, exportFilename));
          jarList.append(exportFilename);
        }
      }
    }

    zos.flush();
    zos.close();

    jarList.append(sketch.getMainName() + ".jar");


    /// add contents of 'library' folders to the export
    for (Library library : importedLibraries) {
      // add each item from the library folder / export list to the output
      for (File exportFile : library.getApplicationExports(exportVariant)) {
        String exportName = exportFile.getName();
        if (!exportFile.exists()) {
          System.err.println(exportFile.getName() +
                             " is mentioned in export.txt," +
                             " but it does not exist.");

        } else if (exportFile.isDirectory()) {
          Util.copyDir(exportFile, new File(jarFolder, exportName));

        } else if (exportName.toLowerCase().endsWith(".zip") ||
                   exportName.toLowerCase().endsWith(".jar")) {
          Util.copyFile(exportFile, new File(jarFolder, exportName));
          jarList.append(exportName);

        } else {
          // Starting with 2.0a2 put extra export files (DLLs, plugins folder,
          // anything else for libraries) inside lib or Contents/Resources/Java
          Util.copyFile(exportFile, new File(jarFolder, exportName));
        }
      }
    }

    /// create platform-specific CLASSPATH based on included jars

    String exportClassPath = null;
    if (exportPlatform == PConstants.MACOS) {
      exportClassPath = "$JAVAROOT/" + jarList.join(":$JAVAROOT/");
    } else if (exportPlatform == PConstants.WINDOWS) {
      exportClassPath = jarList.join(",");
    } else if (exportPlatform == PConstants.LINUX) {
      // why is $APPDIR at the front of this list?
      exportClassPath = "$APPDIR" +
        ":$APPDIR/lib/" + jarList.join(":$APPDIR/lib/");
    }


    /// figure out run options for the VM

    StringList runOptions = new StringList();

    // Set memory options, except for ARM where we're more memory-constrained
    // compared to the machine being used to build/export the sketch
    // https://github.com/processing/processing/pull/4406
    if (Preferences.getBoolean("run.options.memory") &&
        !exportVariant.contains("arm")) {
      runOptions.append("-Xms" + Preferences.get("run.options.memory.initial") + "m");
      runOptions.append("-Xmx" + Preferences.get("run.options.memory.maximum") + "m");
    }
    // https://github.com/processing/processing/issues/2239
    runOptions.append("-Djna.nosys=true");

    // https://github.com/processing/processing/issues/2559
    if (exportPlatform == PConstants.WINDOWS) {
      runOptions.append("-Djava.library.path=\"%EXEDIR%\\lib\"");
    }

    Library javafx = findJavaFX();
    if (javafx != null) {
      String modulePath = exportPlatform == PConstants.MACOS ?
        "$APP_ROOT/Contents/Java/modules" : "lib/modules";
      for (String opt : getArgsJavaFX(modulePath)) {
        runOptions.append(opt);
      }
    }


    /// macOS: write out Info.plist (template for classpath, etc)

    if (exportPlatform == PConstants.MACOS) {
      StringBuilder runOptionsXML = new StringBuilder();
      for (String opt : runOptions) {
        runOptionsXML.append("      <string>");
        runOptionsXML.append(opt);
        runOptionsXML.append("</string>");
        runOptionsXML.append('\n');
      }

      // read the template, first checking whether the sketch has its own copy
      String PLIST_TEMPLATE = "Info.plist.tmpl";
      File plistTemplate = new File(sketch.getFolder(), PLIST_TEMPLATE);
      if (!plistTemplate.exists()) {
        plistTemplate = mode.getContentFile("application/" + PLIST_TEMPLATE);
      }

      // substitute variables from the template with their values
      StringBuilder sb = new StringBuilder(Util.loadFile(plistTemplate));
      replaceTags(sb, "jvm_runtime", jvmRuntime);
      replaceTags(sb, "jvm_version", MIN_JAVA_VERSION);
      replaceTags(sb, "jvm_options_list", runOptionsXML.toString());
      replaceTags(sb, "sketch", sketch.getMainName());
      replaceTags(sb, "lsuipresentationmode",
        Preferences.getBoolean("export.application.present") ? "4" : "0");
      replaceTags(sb, "lsarchitecturepriority",
        exportVariant.substring("macos-".length()));

      // write the plist file
      File plistFile = new File(dotAppFolder, "Contents/Info.plist");
      PrintWriter pw = PApplet.createWriter(plistFile);
      pw.print(sb);
      pw.flush();
      pw.close();

      // attempt to code sign if the Xcode tools appear to be installed
      String appPath = dotAppFolder.getAbsolutePath();
      if (Platform.isMacOS()) {
        if (MacPlatform.isXcodeInstalled()) {
//        if (embedJava) {
//          ProcessHelper.ffs("codesign", "--force", "--sign", "--deep", "-", jdkPath);
//        }
          ProcessHelper.ffs("codesign", "--force", "--deep", "--sign", "-", appPath);
        } else {
          System.err.println("Xcode not installed, install it and manually sign this app:");
          System.err.println("codesign --force --deep --sign - " + appPath);
        }
      }

    } else if (exportPlatform == PConstants.WINDOWS) {
      File buildFile = new File(destFolder, "launch4j-build.xml");
      System.out.println(buildFile);
      File configFile = new File(destFolder, "launch4j-config.xml");

      XML project = new XML("project");
      XML target = project.addChild("target");
      target.setString("name", "windows");

      XML taskdef = target.addChild("taskdef");
      taskdef.setString("name", "launch4j");
      taskdef.setString("classname", "net.sf.launch4j.ant.Launch4jTask");
      String launchPath = mode.getContentFile("application/launch4j").getAbsolutePath();
      taskdef.setString("classpath", launchPath + "/launch4j.jar:" + launchPath + "/lib/xstream.jar");

      XML launch4j = target.addChild("launch4j");
      // not all launch4j options are available when embedded inside the ant
      // build file (i.e. the icon param doesn't work), so use a config file
      //<launch4j configFile="windows/work/config.xml" />
      launch4j.setString("configFile", configFile.getAbsolutePath());

      XML config = new XML("launch4jConfig");
      config.addChild("headerType").setContent("gui");
      //config.addChild("headerType").setContent("console");
      config.addChild("dontWrapJar").setContent("true");
      config.addChild("downloadUrl").setContent(ExportPrompt.JAVA_DOWNLOAD_URL);

      File exeFile = new File(destFolder, sketch.getName() + ".exe");
      config.addChild("outfile").setContent(exeFile.getAbsolutePath());

      File iconFile = mode.getContentFile("application/application.ico");
      config.addChild("icon").setContent(iconFile.getAbsolutePath());

      XML clazzPath = config.addChild("classPath");
      clazzPath.addChild("mainClass").setContent(sketch.getMainName());
      for (String jarName : jarList) {
        clazzPath.addChild("cp").setContent("lib/" + jarName);
      }
      XML jre = config.addChild("jre");
      if (embedJava) {
        // also falling back to PATH if the "java" folder is later removed
        jre.addChild("path").setContent("java;%PATH%");
      } else {
        // needed to make OpenJDK work properly
        // https://sourceforge.net/p/launch4j/bugs/197/
        // https://github.com/processing/processing4/issues/667
        jre.addChild("path").setContent("%PATH%");
      }
      jre.addChild("minVersion").setContent(MIN_JAVA_VERSION);
      for (String opt : runOptions) {
        jre.addChild("opt").setContent(opt);
      }

      config.save(configFile);
      project.save(buildFile);
      if (!buildWindowsLauncher(buildFile, "windows")) {
        // don't delete the build file, might be useful for debugging
        return false;
      }
      configFile.delete();
      buildFile.delete();

    } else {
      File shellScript = new File(destFolder, sketch.getName());
      PrintWriter pw = PApplet.createWriter(shellScript);

      // Do the newlines explicitly so that Windows CRLF
      // isn't used when exporting for Unix.
      pw.print("#!/bin/sh\n\n");
      pw.print("APPDIR=$(readlink -f \"$0\")\n");   // allow symlinks
      pw.print("APPDIR=$(dirname \"$APPDIR\")\n");  // more POSIX compliant

      // another fix for bug #234, LD_LIBRARY_PATH ignored on some platforms
      //ps.print("LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$APPDIR\n");

      if (embedJava) {
        // https://github.com/processing/processing/issues/2349
        pw.print("$APPDIR/java/bin/");
      }
      String runOptionsStr = runOptions.join(" ");
      pw.print("java " + runOptionsStr +
               " -Djava.library.path=\"$APPDIR:$APPDIR/lib\"" +
               // these aren't included here since they're added elsewhere
               // (and only included when Java is embedded)
               //" -Djava.ext.dirs=\"$APPDIR/java/lib/ext\"" +
               //" -Djna.nosys=true" +
               " -cp \"" + exportClassPath + "\"" +
               " " + sketch.getMainName() + " \"$@\"\n");

      pw.flush();
      pw.close();

      String shellPath = shellScript.getAbsolutePath();
      // will work on osx or *nix, but just dies on windows, oh well..
      if (!Platform.isWindows()) {
        Runtime.getRuntime().exec(new String[] { "chmod", "+x", shellPath });
      }
    }


    /// copy the source files to the target
    /// (we like to encourage people to share their code)

    File sourceFolder = new File(destFolder, "source");
    sourceFolder.mkdirs();

    for (SketchCode code : sketch.getCode()) {
      try {
        code.copyTo(new File(sourceFolder, code.getFileName()));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    // move the .java file from the preproc there too
    String preprocFilename = sketch.getMainName() + ".java";
    File preprocFile = new File(srcFolder, preprocFilename);
    if (preprocFile.exists()) {
      Util.copyFile(preprocFile, new File(sourceFolder, preprocFilename));
    } else {
      System.err.println("Could not copy source file: " + preprocFile.getAbsolutePath());
    }


    /// goodbye
    return true;
  }


  private void replaceTags(StringBuilder sb, String replacing, String replacement) {
    int index = 0;
    String tag = "@@" + replacing + "@@";
    while ((index = sb.indexOf(tag)) != -1) {
      sb.replace(index, index + tag.length(), replacement);
    }
  }


  // This is a workaround until a more complete solution is found.
  public Library findJavaFX() {
    for (Library library : getImportedLibraries()) {
      if (library.getName().equals("JavaFX")) {
        return library;
      }
    }
    return null;
  }


  static public String[] getArgsJavaFX(String modulePath) {
    return new String[] {
      "--module-path", modulePath,

      // Full list of modules, let's not commit to all of these unless
      // a compelling argument is made or a reason presents itself.
      //"javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.swing,javafx.web"
      "--add-modules", "javafx.base,javafx.graphics,javafx.swing",

      // TODO Presumably, this is only because com.sun.* classes are being used?
      // https://github.com/processing/processing4/issues/208
      "--add-exports", "javafx.graphics/com.sun.javafx.geom=ALL-UNNAMED",
      "--add-exports", "javafx.graphics/com.sun.glass.ui=ALL-UNNAMED"
    };
  }


  /**
   * Run the launch4j build.xml file through ant to create the exe.
   * Most of this code was lifted from Android mode.
   */
  protected boolean buildWindowsLauncher(File buildFile, String target) {
    Project p = new Project();
    String path = buildFile.getAbsolutePath().replace('\\', '/');
    p.setUserProperty("ant.file", path);

    // deals with a problem where javac error messages weren't coming through
    p.setUserProperty("build.compiler", "extJavac");

    // too chatty
    /*
    // try to spew something useful to the console
    final DefaultLogger consoleLogger = new DefaultLogger();
    consoleLogger.setErrorPrintStream(System.err);
    consoleLogger.setOutputPrintStream(System.out);
    // WARN, INFO, VERBOSE, DEBUG
    consoleLogger.setMessageOutputLevel(Project.MSG_ERR);
    p.addBuildListener(consoleLogger);
    */

    DefaultLogger errorLogger = new DefaultLogger();
    ByteArrayOutputStream errb = new ByteArrayOutputStream();
    PrintStream errp = new PrintStream(errb);
    errorLogger.setErrorPrintStream(errp);
    ByteArrayOutputStream outb = new ByteArrayOutputStream();
    PrintStream outp = new PrintStream(outb);
    errorLogger.setOutputPrintStream(outp);
    errorLogger.setMessageOutputLevel(Project.MSG_INFO);
    p.addBuildListener(errorLogger);

    try {
      p.fireBuildStarted();
      p.init();
      final ProjectHelper helper = ProjectHelper.getProjectHelper();
      p.addReference("ant.projectHelper", helper);
      helper.parse(p, buildFile);
      p.executeTarget(target);
      return true;

    } catch (final BuildException e) {
      // Send a "build finished" event to the build listeners for this project.
      p.fireBuildFinished(e);

      System.out.println(outb);
      System.err.println(errb);
    }
    return false;
  }


  protected void addManifest(ZipOutputStream zos) throws IOException {
    ZipEntry entry = new ZipEntry("META-INF/MANIFEST.MF");
    zos.putNextEntry(entry);

    String contents =
      "Manifest-Version: 1.0\n" +
      "Created-By: Processing " + Base.getVersionName() + "\n" +
      "Main-Class: " + sketch.getMainName() + "\n";  // TODO not package friendly
    zos.write(contents.getBytes());
    zos.closeEntry();
  }


  protected void addClasses(ZipOutputStream zos, File dir) throws IOException {
    String path = dir.getAbsolutePath();
    if (!path.endsWith("/") && !path.endsWith("\\")) {
      path += '/';
    }
//    System.out.println("path is " + path);
    addClasses(zos, dir, path);
  }


  protected void addClasses(ZipOutputStream zos, File dir, String rootPath) throws IOException {
    File[] files = dir.listFiles((dir1, name) -> (name.charAt(0) != '.'));
    if (files != null) {
      for (File sub : files) {
        String relativePath = sub.getAbsolutePath().substring(rootPath.length());

        if (sub.isDirectory()) {
          addClasses(zos, sub, rootPath);

        } else if (sub.getName().endsWith(".class")) {
//        System.out.println("  adding item " + relativePath);
          ZipEntry entry = new ZipEntry(relativePath);
          zos.putNextEntry(entry);
          //zos.write(Base.loadBytesRaw(sub));
          PApplet.saveStream(zos, new FileInputStream(sub));
          zos.closeEntry();
        }
      }
    }
  }


  protected void addDataFolder(ZipOutputStream zos) throws IOException {
    if (sketch.hasDataFolder()) {
      String[] dataFiles = Util.listFiles(sketch.getDataFolder(), false);
      int offset = sketch.getFolder().getAbsolutePath().length() + 1;
      for (String path : dataFiles) {
        if (Platform.isWindows()) {
          path = path.replace('\\', '/');
        }
        //File dataFile = new File(dataFiles[i]);
        File dataFile = new File(path);
        if (!dataFile.isDirectory()) {
          // don't export hidden files
          // skipping dot prefix removes all: . .. .DS_Store
          if (dataFile.getName().charAt(0) != '.') {
            ZipEntry entry = new ZipEntry(path.substring(offset));
            zos.putNextEntry(entry);
            //zos.write(Base.loadBytesRaw(dataFile));
            PApplet.saveStream(zos, new FileInputStream(dataFile));
            zos.closeEntry();
          }
        }
      }
    }
  }


  /**
   * Slurps up .class files from a colon (or semicolon on windows)
   * separated list of paths and adds them to a ZipOutputStream.
   */
  protected void packClassPathIntoZipFile(String path,
                                          ZipOutputStream zos,
                                          HashMap<String,Object> zipFileContents)
    throws IOException {
    String[] pieces = PApplet.split(path, File.pathSeparatorChar);

    for (String piece : pieces) {
      if (piece.length() == 0) continue;

      // is it a jar file or directory?
      if (piece.toLowerCase().endsWith(".jar") ||
              piece.toLowerCase().endsWith(".zip")) {
        try {
          ZipFile file = new ZipFile(piece);
          Enumeration<?> entries = file.entries();
          while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            if (!entry.isDirectory()) {
              String entryName = entry.getName();
              // ignore contents of the META-INF folders
              if (entryName.indexOf("META-INF") == 0) continue;

              // don't allow duplicate entries
              if (zipFileContents.get(entryName) != null) continue;
              zipFileContents.put(entryName, new Object());

              ZipEntry entree = new ZipEntry(entryName);

              zos.putNextEntry(entree);
              byte[] buffer = new byte[(int) entry.getSize()];
              InputStream is = file.getInputStream(entry);

              int offset = 0;
              int remaining = buffer.length;
              while (remaining > 0) {
                int count = is.read(buffer, offset, remaining);
                offset += count;
                remaining -= count;
              }

              zos.write(buffer);
              zos.flush();
              zos.closeEntry();
            }
          }
          file.close();

        } catch (IOException e) {
          System.err.println("Error in file " + piece);
          e.printStackTrace();
        }
      } else {  // not a .jar or .zip, prolly a directory
        File dir = new File(piece);
        // but must be a dir, since it's one of several paths
        // just need to check if it exists
        if (dir.exists()) {
          packClassPathIntoZipFileRecursive(dir, null, zos);
        }
      }
    }
  }


  /**
   * Continue the process of magical exporting. This function
   * can be called recursively to walk through folders looking
   * for more goodies that will be added to the ZipOutputStream.
   */
  static protected void packClassPathIntoZipFileRecursive(File dir,
                                                          String sofar,
                                                          ZipOutputStream zos)
    throws IOException {
    String[] files = dir.list();
    if (files != null) {
      for (String filename : files) {
        // ignore . .. and .DS_Store
        if (filename.charAt(0) == '.') continue;

        File sub = new File(dir, filename);
        String nowfar = (sofar == null) ?
                filename : (sofar + "/" + filename);

        if (sub.isDirectory()) {
          packClassPathIntoZipFileRecursive(sub, nowfar, zos);

        } else {
          // don't add .jar and .zip files, since they only work
          // inside the root, and they're unpacked
          if (!filename.toLowerCase().endsWith(".jar") &&
                  !filename.toLowerCase().endsWith(".zip") &&
                  filename.charAt(0) != '.') {
            ZipEntry entry = new ZipEntry(nowfar);
            zos.putNextEntry(entry);
            //zos.write(Base.loadBytesRaw(sub));
            PApplet.saveStream(zos, new FileInputStream(sub));
            zos.closeEntry();
          }
        }
      }
    } else {
      System.err.println("Could not read from " + dir);
    }
  }
}
