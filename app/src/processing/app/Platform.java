/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-23 The Processing Foundation
  Copyright (c) 2008-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.jna.platform.FileUtils;

import processing.app.platform.DefaultPlatform;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.data.StringDict;


public class Platform {
  static DefaultPlatform inst;

  /*
  static Map<Integer, String> platformNames = new HashMap<>();
  static {
    platformNames.put(PConstants.WINDOWS, "windows"); //$NON-NLS-1$
    platformNames.put(PConstants.MACOS, "macos"); //$NON-NLS-1$
    platformNames.put(PConstants.LINUX, "linux"); //$NON-NLS-1$
  }
  */

  // TODO only used in one place, probably overkill for this to be a map
  static Map<String, Integer> platformIndices = new HashMap<>();
  static {
    platformIndices.put("windows", PConstants.WINDOWS); //$NON-NLS-1$
    platformIndices.put("macos", PConstants.MACOS); //$NON-NLS-1$
    platformIndices.put("linux", PConstants.LINUX); //$NON-NLS-1$
  }

  /** How many bits this machine is */
  static int nativeBits;
  static {
    nativeBits = 32;  // perhaps start with 32
    String bits = System.getProperty("sun.arch.data.model"); //$NON-NLS-1$
    if (bits != null) {
      if (bits.equals("64")) { //$NON-NLS-1$
        nativeBits = 64;
      }
    } else {
      // if some other strange vm, maybe try this instead
      if (System.getProperty("java.vm.name").contains("64")) { //$NON-NLS-1$ //$NON-NLS-2$
        nativeBits = 64;
      }
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static public boolean isAvailable() {
    return inst != null;
  }


  static public void init() {
    try {
      // Start with DefaultPlatform, but try to upgrade to a known platform
      final String packageName = DefaultPlatform.class.getPackageName();
      Class<?> platformClass =
        Class.forName(packageName + ".DefaultPlatform");

      if (Platform.isMacOS()) {
        platformClass = Class.forName(packageName + ".MacPlatform");
      } else if (Platform.isWindows()) {
        platformClass = Class.forName(packageName + ".WindowsPlatform");
      } else if (Platform.isLinux()) {
        platformClass = Class.forName(packageName + ".LinuxPlatform");
      }
      inst = (DefaultPlatform) platformClass.getDeclaredConstructor().newInstance();

    } catch (Exception e) {
      Messages.showError("Problem Setting the Platform",
                         "An unknown error occurred while trying to load\n" +
                         "platform-specific code for your machine.", e);
    }
  }


  static public void initBase(Base base) throws Exception {
    inst.initBase(base);
  }


  static public void setLookAndFeel() throws Exception {
    inst.setLookAndFeel();
  }


  static public void setInterfaceZoom() throws Exception {
    inst.setInterfaceZoom();
  }


  static public float getSystemZoom() {
    return inst == null ? 1 : inst.getSystemZoom();
  }


  static public File getSettingsFolder() throws Exception {
    return inst.getSettingsFolder();
  }


  static public File getDefaultSketchbookFolder() throws Exception {
    return inst.getDefaultSketchbookFolder();
  }


  static public void saveLanguage(String languageCode) {
    inst.saveLanguage(languageCode);
  }


  /**
   * Implements the cross-platform headache of opening URLs.
   * <p>
   * Since 2.0a8, this requires the parameter to be an actual URL,
   * meaning that you can't send it a file:// path without a prefix.
   * It also just calls into Platform, which now uses java.awt.Desktop
   * (where possible). The URL must also be properly URL-encoded.
   */
  static public void openURL(String url) {
    try {
      inst.openURL(url);

    } catch (Exception e) {
      Messages.showWarning("Problem Opening URL",
                           "Could not open the URL\n" + url, e);
    }
  }


  /**
   * Used to determine whether to disable the "Show Sketch Folder" option.
   * @return true If a means of opening a folder is known to be available.
   */
  static public boolean openFolderAvailable() {
    return inst.openFolderAvailable();
  }


  /**
   * Implements the other cross-platform headache of opening
   * a folder in the machine's native file browser.
   */
  static public void openFolder(File file) {
    try {
      inst.openFolder(file);

    } catch (Exception e) {
      Messages.showWarning("Problem Opening Folder",
                           "Could not open the folder\n" + file.getAbsolutePath(), e);
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


//  /**
//   * Return whether sketches will run as 32- or 64-bits based
//   * on the JVM that's in use.
//   */
//  static public int getNativeBits() {
//    return nativeBits;
//  }


  /**
   * Return the value of the os.arch property
   */
  static public String getNativeArch() {
    // This will return "arm" for 32-bit ARM on Linux,
    // and "aarch64" for 64-bit ARM on Linux (rpi) and Apple Silicon
    // (the latter only when using a native 64-bit ARM VM on macOS,
    // which as of 4.0 alpha 5 is not being used b/c of missing libs).
    return System.getProperty("os.arch");
  }


  static public String getVariant() {
    return getName() + "-" + getNativeArch();
  }


  static StringDict supportedVariants = new StringDict(new String[][] {
    { "macos-x86_64", "macOS (Intel 64-bit)" },
    { "macos-aarch64", "macOS (Apple Silicon)" },
    { "windows-amd64", "Windows (Intel 64-bit)" },
    { "linux-amd64", "Linux (Intel 64-bit)" },
    { "linux-arm", "Linux (Raspberry Pi 32-bit)" },
    { "linux-aarch64", "Linux (Raspberry Pi 64-bit)" }
  });

  /**
   * List of variants that are supported by this release of the PDE.
   */
  static public StringDict getSupportedVariants() {
    return supportedVariants;
  }

//  /*
//   * Return a string that identifies the variant of a platform
//   * e.g. "32" or "64" on Intel
//   */
  /*
  static public String getVariant() {
    return getVariant(PApplet.platform, getNativeArch(), getNativeBits());
  }


  static public String getVariant(int platform, String arch, int bits) {
    if (platform == PConstants.LINUX &&
        bits == 32 && "arm".equals(Platform.getNativeArch())) {
      return "armv6hf";  // assume armv6hf
    } else if (platform == PConstants.LINUX &&
        bits == 64 && "aarch64".equals(Platform.getNativeArch())) {
      return "arm64";
    }

    return Integer.toString(bits);  // 32 or 64
  }
  */


  /**
   * Returns one of macos, windows, linux, or other.
   * Changed in 4.0b4 to return macos instead of macosx.
   * Only used inside processing.app.java.
   */
  static public String getName() {
    return PConstants.platformNames[PApplet.platform];
  }


  static public String getPrettyName() {
    return supportedVariants.get(getVariant());
  }


//  /**
//   * Map a platform constant to its name.
//   * @param which PConstants.WINDOWS, PConstants.MACOSX, PConstants.LINUX
//   * @return one of "windows", "macosx", or "linux"
//   */
//  static public String getName(int which) {
//    return platformNames.get(which);
//  }


  static public int getIndex(String platformName) {
    // if this has os.arch at the end, remove it
    int index = platformName.indexOf('-');
    if (index != -1) {
      platformName = platformName.substring(0, index);
    }
    return platformIndices.getOrDefault(platformName, -1);
  }


  // These were changed to no longer rely on PApplet and PConstants because
  // of conflicts that could happen with older versions of core.jar, where
  // the MACOSX constant would instead read as the LINUX constant.


  /**
   * returns true if Processing is running on a Mac OS X machine.
   */
  static public boolean isMacOS() {
    return System.getProperty("os.name").contains("Mac"); //$NON-NLS-1$ //$NON-NLS-2$
  }


  /**
   * returns true if running on windows.
   */
  static public boolean isWindows() {
    return System.getProperty("os.name").contains("Windows"); //$NON-NLS-1$ //$NON-NLS-2$
  }


  /**
   * true if running on linux.
   */
  static public boolean isLinux() {
    return System.getProperty("os.name").contains("Linux"); //$NON-NLS-1$ //$NON-NLS-2$
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static protected File processingRoot;

  /**
   * Get reference to a file adjacent to the executable on Windows and Linux,
   * or inside Contents/Resources/Java on Mac OS X. This will return the local
   * JRE location, *whether or not it is the active JRE*.
   */
  static public File getContentFile(String name) {
    if (processingRoot == null) {
      // Get the path to the .jar file that contains Base.class
      URL pathURL =
          Base.class.getProtectionDomain().getCodeSource().getLocation();
      // Decode URL
      String decodedPath;
      try {
        decodedPath = pathURL.toURI().getSchemeSpecificPart();
      } catch (URISyntaxException e) {
        Messages.showError("Missing File",
          "Could not access a required file:\n" +
            "<b>" + name + "</b>\n" +
            "You may need to reinstall Processing.", e);
        return null;
      }

      if (decodedPath.contains("/app/bin")) {  // This means we're in Eclipse
        final File build = new File(decodedPath, "../../build").getAbsoluteFile();
        if (Platform.isMacOS()) {
          processingRoot = new File(build, "macos/work/Processing.app/Contents/Java");
        } else if (Platform.isWindows()) {
          processingRoot =  new File(build, "windows/work");
        } else if (Platform.isLinux()) {
          processingRoot =  new File(build, "linux/work");
        }
      } else {
        // The .jar file will be in the lib folder
        File jarFolder = new File(decodedPath).getParentFile();
        if (jarFolder.getName().equals("lib")) {
          // The main Processing installation directory.
          // This works for Windows, Linux, and Apple's Java 6 on OS X.
          processingRoot = jarFolder.getParentFile();
        } else if (Platform.isMacOS()) {
          // This works for Java 8 on OS X. We don't have things inside a 'lib'
          // folder on OS X. Adding it caused more problems than it was worth.
          processingRoot = jarFolder;
        }
        if (processingRoot == null || !processingRoot.exists()) {
          // Try working directory instead (user.dir, different from user.home)
          System.err.println("Could not find lib folder via " +
            jarFolder.getAbsolutePath() +
            ", switching to user.dir");
          processingRoot = new File(""); // resolves to "user.dir"
        }
      }
    }
    return new File(processingRoot, name);
  }


  static public File getJavaHome() {
    if (Platform.isMacOS()) {
      //return "Contents/PlugIns/jdk1.7.0_40.jdk/Contents/Home/jre/bin/java";
      File[] plugins = getContentFile("../PlugIns").listFiles((dir, name) -> dir.isDirectory() &&
        name.contains("jdk") && !name.startsWith("."));
      return new File(plugins[0], "Contents/Home");
    }
    // On all other platforms, it's the 'java' folder adjacent to Processing
    return getContentFile("java");
  }


  /** Get the path to the embedded Java executable. */
  static public String getJavaPath() {
    String javaPath = "bin/java" + (Platform.isWindows() ? ".exe" : "");
    File javaFile = new File(getJavaHome(), javaPath);
    try {
      return javaFile.getCanonicalPath();
    } catch (IOException e) {
      return javaFile.getAbsolutePath();
    }
  }


  static protected File getProcessingApp() {
    File appFile;
    if (Platform.isMacOS()) {
      // walks up from Processing.app/Contents/Java to Processing.app
      // (or whatever the user has renamed it to)
      appFile = getContentFile("../..");
    } else if (Platform.isWindows()) {
      appFile = getContentFile("processing.exe");
    } else {
      appFile = getContentFile("processing");
    }
    try {
      return appFile.getCanonicalFile();

    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }


  // Not great, shows the crusty Duke icon in the dock.
  // Better to just re-launch the .exe instead.
  // Hacked up from <a href="https://lewisleo.blogspot.com/2012/08/programmatically-restart-java.html">this code</a>.
  static private void restartJavaApplication() {
    //    System.out.println("java path: " + javaPath);
//    String java = System.getProperty("java.home") + "/bin/java";
    // Tested and working with JDK 17 [fry 230122]
//    System.out.println("sun java command: " + System.getProperty("sun.java.command"));
//    System.out.println("class path: " + System.getProperty("java.class.path"));
    List<String> cmd = new ArrayList<>();

    // Add the path to the current java binary
    cmd.add(getJavaPath());

    // Get all the VM arguments that are currently in use
    List<String> vmArguments =
      ManagementFactory.getRuntimeMXBean().getInputArguments();

    // Add all the arguments we're using now, except for -agentlib
    for (String arg : vmArguments) {
      if (!arg.contains("-agentlib")) {
        cmd.add(arg);
      }
    }

    // Does not work for .jar files, should this be used in a more general way
    cmd.add("-cp");
    cmd.add(System.getProperty("java.class.path"));

    // Finally, add the class that was used to launch the app
    // (in our case, this is the Processing splash screen)
    String javaCommand = System.getProperty("sun.java.command");
    String[] splitCommand = PApplet.split(javaCommand, ' ');
//    if (splitCommand.length > 1) {
//      try {
//        Util.saveFile(javaCommand, PApplet.desktopFile("arrrrrghs.txt"));
//      } catch (IOException e) {
//        throw new RuntimeException(e);
//      }
//    }
    cmd.add(splitCommand[0]);  // should be the main class name

    ProcessBuilder builder = new ProcessBuilder(cmd);

    /*
    StringBuffer vmArgsOneLine = new StringBuffer();
    for (String arg : vmArguments) {
      // if it's the agent argument : we ignore it otherwise the
      // address of the old application and the new one will be in conflict
      if (!arg.contains("-agentlib")) {
        vmArgsOneLine.append(arg);
        vmArgsOneLine.append(" ");
      }
    }
    // init the command to execute, add the vm args
    final StringBuffer cmd = new StringBuffer("\"" + java + "\" " + vmArgsOneLine);
    // program main and program arguments (be careful a sun property. might not be supported by all JVM)
    String[] mainCommand = System.getProperty("sun.java.command").split(" ");
    // program main is a jar
    if (mainCommand[0].endsWith(".jar")) {
      // if it's a jar, add -jar mainJar
      cmd.append("-jar " + new File(mainCommand[0]).getPath());
    } else {
      // else it's a .class, add the classpath and mainClass
      cmd.append("-cp \"" + System.getProperty("java.class.path") + "\" " + mainCommand[0]);
    }
    // finally add program arguments
    for (int i = 1; i < mainCommand.length; i++) {
      cmd.append(" ");
      cmd.append(mainCommand[i]);
    }
    */
    // execute the command in a shutdown hook, to be sure that all the
    // resources have been disposed before restarting the application
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
//        System.out.println(new StringList(cmd).join(" "));
//        Runtime.getRuntime().exec(cmd.toArray(new String[0]));
        builder.start();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }));
    System.exit(0);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Delete a file or directory in a platform-specific manner. Removes a File
   * object (a file or directory) from the system by placing it in the Trash
   * or Recycle Bin (if available) or simply deleting it (if not).
   * Also tries to find a suitable Trash location on Linux.
   * <p>
   * When the file/folder is on another file system, it may simply be removed
   * immediately, without additional warning. So only use this if you want to,
   * you know, "delete" the subject in question.
   *
   * @param file the victim (a directory or individual file)
   * @return true if all ends well
   * @throws IOException what went wrong
   */
  static public boolean deleteFile(File file) throws IOException {
    try {
      FileUtils fu = FileUtils.getInstance();
      if (fu.hasTrash()) {
        fu.moveToTrash(file);
        return true;
      }
    } catch (Throwable t) {
      // On macOS getting NoClassDefFoundError inside JNA on Big Sur.
      // (Can't find com.sun.jna.platform.mac.MacFileUtils$FileManager)
      // Just adding a catch-all here so that it does the fall-through below.
      System.err.println(t.getMessage());
    }

    if (file.isDirectory()) {
      Util.removeDir(file);
      return true;

    } else {
      return file.delete();
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static public void setenv(String variable, String value) {
    inst.setenv(variable, value);
  }


  static public String getenv(String variable) {
    return inst.getenv(variable);
  }


  static public int unsetenv(String variable) {
    return inst.unsetenv(variable);
  }
}
