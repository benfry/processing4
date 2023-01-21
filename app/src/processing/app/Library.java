package processing.app;

import java.awt.EventQueue;
import java.io.*;
import java.util.*;
import java.util.zip.ZipFile;

import processing.app.contrib.*;
import processing.core.*;
import processing.data.StringDict;
import processing.data.StringList;


public class Library extends LocalContribution {
//  static final String[] platformNames = PConstants.platformNames;

  static StringDict newToOld = new StringDict(new String[][] {
    { "macos-x86_64", "macosx" },
    { "windows-amd64", "windows64" },
    { "linux-amd64", "linux64" },
    { "linux-arm", "linux-armv6hf" },
    { "linux-aarch64", "linux-arm64" }
  });

  protected File libraryFolder;   // shortname/library
  protected File examplesFolder;  // shortname/examples
  protected File referenceFile;   // shortname/reference/index.html

  /**
   * Subfolder for grouping libraries in a menu. Basic subfolder support
   * is provided so that some organization can be done in the import menu.
   * (This is the replacement for the "library compilation" type.)
   */
  protected String group;

  /** Packages provided by this library. */
  StringList packageList;

  /** Per-platform exports for this library. */
  Map<String, String[]> exportList;

  /** List of default exports */
  String[] baseList;

  /** Android exports (single platform for now, may not exist). */
  String[] androidExportList;

//  /** True if there are separate 32/64 bit for the specified platform index. */
//  boolean[] multipleArch = new boolean[platformNames.length];
//  Map<String, Boolean> multipleArch = new HashMap<>();

  /**
   * For runtime, the native library path for this platform. e.g. on Windows 64,
   * this might be the windows64 subfolder with the library.
   */
  String nativeLibraryPath;

//  /** True if */
//  boolean variants;

  static public final String propertiesFileName = "library.properties";

  /**
   * Filter to pull out just files and none of the platform-specific
   * directories, and to skip export.txt.
   *
   * As of 2.0a2, other directories are included, because we need
   * things like the 'plugins' subfolder w/ video.
   *
   * As of 4.0b4, only checking whether macos, windows, linux, or
   * android are the prefix of the folder name, so that we can avoid
   * explicitly listing all possible architectures, and so that
   * macos-blah as well as and macosx will be handled properly.
   */
  static FilenameFilter libraryFolderFilter = (dir, name) -> {
    // skip .DS_Store files, .svn folders, etc
    if (name.charAt(0) == '.') return false;
    // ha, the sftp library still has one [fry 220121]
    if (name.equals("CVS")) return false;
    if (name.equals("export.txt")) return false;

    File file = new File(dir, name);
    if (file.isDirectory()) {
      //noinspection RedundantIfStatement
      if (name.startsWith("macos") ||
          name.startsWith("windows") ||
          name.startsWith("linux")) {
          //name.startsWith("android")) {  // no libraries use this
        return false;
      }
    }
    return true;
  };

  static FilenameFilter jarFilter = (dir, name) -> {
    if (name.charAt(0) == '.') return false;  // skip ._blah.jar crap on OS X
    if (new File(dir, name).isDirectory()) return false;
    String lc = name.toLowerCase();
    return lc.endsWith(".jar") || lc.endsWith(".zip");
  };


  static public Library load(File folder) {
    try {
      return new Library(folder);
//    } catch (IgnorableException ig) {
//      Base.log(ig.getMessage());
    } catch (Error err) {
      // Handles UnsupportedClassVersionError and others
      err.printStackTrace();
    }
    return null;
  }


  public Library(File folder) {
    this(folder, null);
  }


  private Library(File folder, String groupName) {
    super(folder);
    this.group = groupName;

    libraryFolder = new File(folder, "library");
    examplesFolder = new File(folder, "examples");
    referenceFile = new File(folder, "reference/index.html");

    handle();
  }


  /**
   * Handles all the Java-specific parsing for library handling.
   */
  protected void handle() {
    handleNative();
    handleExports();
  }


  /**
   * Identify nativeLibraryFolder location for the current platform.
   */
  private void handleNative() {
    String variant = Platform.getVariant();

    // use the root of the library folder as the default
    File nativeLibraryFolder = libraryFolder;

    /*
    String hostPlatform = Platform.getName();
    // see if there's a 'windows', 'macosx', or 'linux' folder
    File hostLibrary = new File(libraryFolder, hostPlatform);
    if (hostLibrary.exists()) {
      nativeLibraryFolder = hostLibrary;
    }
    */

    // see if there's a {platform}-{arch} folder
    File hostLibrary = new File(libraryFolder, variant);
    if (hostLibrary.exists()) {
      nativeLibraryFolder = hostLibrary;

    } else {
      // if not found, try the old-style naming
      String oldName = newToOld.get(variant);
      if (oldName != null) {
        hostLibrary = new File(libraryFolder, oldName);
        if (hostLibrary.exists()) {
          nativeLibraryFolder = hostLibrary;
        }
      }
    }

    // save that folder for later use
    nativeLibraryPath = nativeLibraryFolder.getAbsolutePath();
  }


  private void handleExports() {
    /*
    File exportSettings = new File(libraryFolder, "export.txt");
    StringDict exportTable = exportSettings.exists() ?
      Util.readSettings(exportSettings) : new StringDict();
    */

    exportList = new HashMap<>();

    // get the list of files just in the library root
    baseList = libraryFolder.list(libraryFolderFilter);

    for (String variant : Platform.getSupportedVariants().keys()) {
      File variantFolder = new File(libraryFolder, variant);
      if (!variantFolder.exists()) {
        // check to see if old naming is in use
        String oldName = newToOld.get(variant, null);
        if (oldName != null) {
          variantFolder = new File(libraryFolder, variant);
          if (variantFolder.exists()) {
            Messages.log("Please update " + getName() + " for Processing 4. " +
              variantFolder + " is the older naming scheme.");
          }
        }
      }
      if (variantFolder.exists()) {
        String[] entries = listPlatformEntries(libraryFolder, variant, baseList);
        if (entries != null) {
          exportList.put(variant, entries);
        }
      }
    }

    /*
    // not actually used in any libraries
    String androidExportStr = exportTable.get("android");
    if (androidExportStr != null) {
      androidExportList = PApplet.splitTokens(androidExportStr, ", ");
    } else {
      androidExportList = baseList;
    }
    */

    /*
    // for each individual platform that this library supports, figure out what's around
    for (int i = 1; i < platformNames.length; i++) {
      String platformName = platformNames[i];
      String platformName32 = platformName + "32";
      String platformName64 = platformName + "64";
      String platformNameArmv6hf = platformName + "-armv6hf";
      String platformNameArm64 = platformName + "-arm64";

      // First check for things like 'application.macosx=' or 'application.windows32' in the export.txt file.
      // These will override anything in the platform-specific subfolders.
      String platformAll = exportTable.get("application." + platformName);
      String[] platformList = platformAll == null ? null : PApplet.splitTokens(platformAll, ", ");
      String platform32 = exportTable.get("application." + platformName + "32");
      String[] platformList32 = platform32 == null ? null : PApplet.splitTokens(platform32, ", ");
      String platform64 = exportTable.get("application." + platformName + "64");
      String[] platformList64 = platform64 == null ? null : PApplet.splitTokens(platform64, ", ");
      String platformArmv6hf = exportTable.get("application." + platformName + "-armv6hf");
      String[] platformListArmv6hf = platformArmv6hf == null ? null : PApplet.splitTokens(platformArmv6hf, ", ");
      String platformArm64 = exportTable.get("application." + platformName + "-arm64");
      String[] platformListArm64 = platformArm64 == null ? null : PApplet.splitTokens(platformArm64, ", ");

      // If nothing specified in the export.txt entries, look for the platform-specific folders.
      if (platformAll == null) {
        platformList = listPlatformEntries(libraryFolder, platformName, baseList);
      }
      if (platform32 == null) {
        platformList32 = listPlatformEntries(libraryFolder, platformName32, baseList);
      }
      if (platform64 == null) {
        platformList64 = listPlatformEntries(libraryFolder, platformName64, baseList);
      }
      if (platformListArmv6hf == null) {
        platformListArmv6hf = listPlatformEntries(libraryFolder, platformNameArmv6hf, baseList);
      }
      if (platformListArm64 == null) {
        platformListArm64 = listPlatformEntries(libraryFolder, platformNameArm64, baseList);
      }

      if (platformList32 != null || platformList64 != null || platformListArmv6hf != null || platformListArm64 != null) {
        //multipleArch[i] = true;
        multipleArch.put(platformName, true);
      }

      // if there aren't any relevant imports specified or in their own folders,
      // then use the baseList (root of the library folder) as the default.
      if (platformList == null && platformList32 == null && platformList64 == null && platformListArmv6hf == null && platformListArm64 == null) {
        exportList.put(platformName, baseList);

      } else {
        // once we've figured out which side our bread is buttered on, save it.
        // (also concatenate the list of files in the root folder as well
        if (platformList != null) {
          exportList.put(platformName, platformList);
        }
        if (platformList32 != null) {
          exportList.put(platformName32, platformList32);
        }
        if (platformList64 != null) {
          exportList.put(platformName64, platformList64);
        }
        if (platformListArmv6hf != null) {
          exportList.put(platformNameArmv6hf, platformListArmv6hf);
        }
        if (platformListArm64 != null) {
          exportList.put(platformNameArm64, platformListArm64);
        }
      }
    }
    */

//    for (String p : exportList.keySet()) {
//      System.out.println(p + " -> ");
//      PApplet.println(exportList.get(p));
//    }

    // get the path for all .jar files in this code folder
    packageList = Util.packageListFromClassPath(getClassPath());
  }


  /**
   * List who's inside a windows64, macosx, linux32, etc folder.
   */
  static String[] listPlatformEntries(File libraryFolder, String folderName, String[] baseList) {
    File folder = new File(libraryFolder, folderName);
    if (folder.exists()) {
      String[] entries = folder.list((dir, name) -> name.charAt(0) != '.');
      if (entries != null) {
        String[] outgoing = new String[entries.length + baseList.length];
        for (int i = 0; i < entries.length; i++) {
          outgoing[i] = folderName + "/" + entries[i];
        }
        // Copy the base libraries in there as well
        System.arraycopy(baseList, 0, outgoing, entries.length, baseList.length);
        return outgoing;
      }
    }
    return null;
  }


  //static protected HashMap<String, Object> packageWarningMap = new HashMap<>();

  /**
   * Add the packages provided by this library to the master list that maps
   * imports to specific libraries.
   * @param importToLibraryTable mapping from package names to Library objects
   */
//  public void addPackageList(HashMap<String,Library> importToLibraryTable) {
  public void addPackageList(Map<String, List<Library>> importToLibraryTable) {
//    PApplet.println(packages);
    for (String pkg : packageList) {
//          pw.println(pkg + "\t" + libraryFolder.getAbsolutePath());
//      PApplet.println(pkg + "\t" + getName());
//      Library library = importToLibraryTable.get(pkg);
      List<Library> libraries = importToLibraryTable.get(pkg);
      if (libraries == null) {
        libraries = new ArrayList<>();
        importToLibraryTable.put(pkg, libraries);
      } else {
        if (Base.DEBUG) {
          System.err.println("The library found in");
          System.err.println(getPath());
          System.err.println("conflicts with");
          for (Library library : libraries) {
            System.err.println(library.getPath());
          }
          System.err.println("which already define(s) the package " + pkg);
          System.err.println("If you have a line in your sketch that reads");
          System.err.println("import " + pkg + ".*;");
          System.err.println("Then you'll need to first remove one of those libraries.");
          System.err.println();
        }
      }
      libraries.add(this);
    }
  }


  public boolean hasExamples() {
    return examplesFolder.exists();
  }


  public File getExamplesFolder() {
    return examplesFolder;
  }


  public String getGroup() {
    return group;
  }


  public String getPath() {
    return folder.getAbsolutePath();
  }


  public String getLibraryPath() {
    return libraryFolder.getAbsolutePath();
  }


  public String getJarPath() {
    //return new File(folder, "library/" + name + ".jar").getAbsolutePath();
    return new File(libraryFolder, folder.getName() + ".jar").getAbsolutePath();
  }


  // the returned value begins with File.pathSeparatorChar
  // so that it can be appended to other paths safely
  public String getClassPath() {
    StringBuilder cp = new StringBuilder();

    String[] jarHeads = libraryFolder.list(jarFilter);
    if (jarHeads != null) {
      for (String jar : jarHeads) {
        cp.append(File.pathSeparatorChar);
        cp.append(new File(libraryFolder, jar).getAbsolutePath());
      }
    }
    File nativeLibraryFolder = new File(nativeLibraryPath);
    if (!libraryFolder.equals(nativeLibraryFolder)) {
      jarHeads = new File(nativeLibraryPath).list(jarFilter);
      if (jarHeads != null) {
        for (String jar : jarHeads) {
          cp.append(File.pathSeparatorChar);
          cp.append(new File(nativeLibraryPath, jar).getAbsolutePath());
        }
      }
    }
    return cp.toString();
  }


  public String getNativePath() {
    return nativeLibraryPath;
  }


  protected File[] wrapFiles(String[] list) {
    File[] outgoing = new File[list.length];
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = new File(libraryFolder, list[i]);
    }
    return outgoing;
  }


  public File[] getApplicationExports(String variant) {
    String[] list = getApplicationExportList(variant);
    return wrapFiles(list);
  }


  /*
  public File[] getApplicationExports(int platform, String variant) {
    String[] list = getApplicationExportList(platform, variant);
    return wrapFiles(list);
  }


//   * Returns the necessary exports for the specified platform.
//   * If no 32 or 64-bit version of the exports exists, it returns the version
//   * that doesn't specify bit depth.
  public String[] getApplicationExportList(int platform, String variant) {
    String platformName = PConstants.platformNames[platform];
    if (variant.equals("32")) {
      String[] pieces = exportList.get(platformName + "32");
      if (pieces != null) return pieces;
    } else if (variant.equals("64")) {
      String[] pieces = exportList.get(platformName + "64");
      if (pieces != null) return pieces;
    } else if (variant.equals("armv6hf")) {
      String[] pieces = exportList.get(platformName + "-armv6hf");
      if (pieces != null) return pieces;
    } else if (variant.equals("arm64")) {
      String[] pieces = exportList.get(platformName + "-arm64");
      if (pieces != null) return pieces;
    }
    return exportList.get(platformName);
  }
  */

  public String[] getApplicationExportList(String variant) {
    if (exportList.isEmpty()) {
      return baseList;
    }
    return exportList.get(variant);
  }


  @SuppressWarnings("unused")
  public File[] getAndroidExports() {
    return wrapFiles(androidExportList);
  }


  /*
  public boolean hasMultipleArch(int platform) {
    //return multipleArch[platform];
    return multipleArch.getOrDefault(platform, false);
  }
  */


  /*
  public boolean supportsArch(int platform, String variant) {
    // If this is a universal library, or has no natives, then we're good.
    if (multipleArch[platform] == false) {
      return true;
    }
    return getApplicationExportList(platform, variant) != null;
  }

  public boolean isUnsupported(int platform, String variant) {
    // If this is a universal library, or has no natives, then we're good.
    if (!multipleArch.containsKey(platformNames[platform])) {
      return false;
    }
    return getApplicationExportList(platform, variant) == null;
  }
  */


  public boolean isUnsupported(String variant) {
    if (exportList.isEmpty()) {
      // if no per-platform exports, then nothing to worry about
      return false;
    }
    return getApplicationExportList(variant) == null;
  }


  /*
  static public boolean hasMultipleArch(int platform, List<Library> libraries) {
    return libraries.stream().anyMatch(library -> library.hasMultipleArch(platform));
  }
  */


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static protected FilenameFilter junkFolderFilter = (dir, name) -> {
    // skip .DS_Store files, .svn and .git folders, etc
    if (name.charAt(0) == '.') return false;
    if (name.equals("CVS")) return false;  // old skool
    return new File(dir, name).isDirectory();
  };


  static public String findCollision(File folder) {
    File[] jars = PApplet.listFiles(folder, "recursive", "extension=jar");
    if (jars != null) {
      for (File file : jars) {
        try {
          ZipFile zf = new ZipFile(file);
          if (zf.getEntry("processing/core/PApplet.class") != null) {
            return "processing.core";
          }
          if (zf.getEntry("processing/app/Base.class") != null) {
            return "processing.app";
          }
        } catch (IOException e) {
          // ignored
        }
      }
    }
    return null;
  }


  static public List<File> discover(File folder) {
    List<File> libraries = new ArrayList<>();
    String[] folderNames = folder.list(junkFolderFilter);

    // if a bad folder or unreadable, folderNames might be null
    if (folderNames != null) {
      // alphabetize list, since it's not always alpha order
      // replaced hella slow bubble sort with this feller for 0093
      Arrays.sort(folderNames, String.CASE_INSENSITIVE_ORDER);

      // TODO a little odd because ContributionType.LIBRARY.isCandidate()
      //      handles some, but not all, of this; and the rules of selection
      //      should probably be consolidated in a sensible way [fry 200116]
      for (String potentialName : folderNames) {
        File baseFolder = new File(folder, potentialName);
        File libraryFolder = new File(baseFolder, "library");
        File libraryJar = new File(libraryFolder, potentialName + ".jar");
        // If a .jar file of the same prefix as the folder exists
        // inside the 'library' subfolder of the sketch
        if (libraryJar.exists()) {
          String sanityCheck = Sketch.sanitizeName(potentialName);
          if (!sanityCheck.equals(potentialName)) {
            final String mess =
              "The library \"" + potentialName + "\" cannot be used.\n" +
              "Library names must contain only basic letters and numbers.\n" +
              "(ASCII only and no spaces, and it cannot start with a number)";
            Messages.showMessage("Ignoring bad library name", mess);

          } else {
            String pkg = findCollision(libraryFolder);
            if (pkg != null) {
              final String mess =
                "The library \"" + potentialName + "\" cannot be used\n" +
                "because it contains the " + pkg + " libraries.\n" +
                "Please contact the library author for an update.";
              Messages.showMessage("Ignoring bad library", mess);

              // Move the folder out of the way
              File badFolder = new File(baseFolder.getParentFile(), "disabled");
              boolean success = true;
              if (!badFolder.exists()) {
                success = badFolder.mkdirs();
              }
              if (success) {
                File hideFolder = new File(badFolder, baseFolder.getName());
                success = baseFolder.renameTo(hideFolder);
                if (success) {
                  System.out.println("Moved " + baseFolder + " to " + hideFolder);
                } else {
                  System.err.println("Could not move " + baseFolder + " to " + hideFolder);
                }
              }

            } else {
              libraries.add(baseFolder);
            }
          }
        }
      }
    }
    return libraries;
  }


  static Set<String> duplicateLibraries = new HashSet<>();

  static public List<Library> list(File folder) {
    List<Library> libraries = new ArrayList<>();
    List<File> librariesFolders = new ArrayList<>(discover(folder));

    Map<String, File> seen = new HashMap<>();
    for (File baseFolder : librariesFolders) {
      Library lib = new Library(baseFolder);
      String name = lib.getName();
      File foundEarlier = seen.get(name);
      if (foundEarlier != null) {
        // Warn the user about this duplication (later, on the EDT)
        if (!duplicateLibraries.contains(name)) {
          Messages.showWarningTiered("Duplicate Library Found",
            "There are multiple libraries named “" + name + "”",
            "Please remove either “" + foundEarlier.getName() +
              " or “" + lib.getName() + "”\n" +
              "from " + folder.getPath() + "\n" +
              "and restart Processing.", null);
        }
        duplicateLibraries.add(name);

      } else {
        seen.put(name, lib.getFolder());
        libraries.add(lib);
      }
    }
    return libraries;
  }


  public ContributionType getType() {
    return ContributionType.LIBRARY;
  }


  /**
   * Returns the object stored in the referenceFile field, which contains an
   * instance of the file object representing the index file of the reference
   *
   * @return referenceFile
   */
  public File getReferenceIndexFile() {
    return referenceFile;
  }


  /**
   * Tests whether the reference's index file indicated by referenceFile exists.
   *
   * @return true if and only if the file denoted by referenceFile exists; false
   *         otherwise.
   */
  public boolean hasReference() {
    return referenceFile.exists();
  }
}
