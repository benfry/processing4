/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - https://processing.org

  Copyright (c) 2013-22 The Processing Foundation
  Copyright (c) 2011-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along
  with this program; if not, write to the Free Software Foundation, Inc.
  59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package processing.app.contrib;

import java.awt.EventQueue;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

import javax.swing.JOptionPane;

import processing.app.*;
import processing.app.ui.Editor;
import processing.core.PApplet;
import processing.data.StringDict;
import processing.data.StringList;


/**
 * A contribution that has been downloaded to the disk,
 * and may or may not be installed.
 */
public abstract class LocalContribution extends Contribution {
  static public final String DELETION_FLAG = "marked_for_deletion";
  static public final String UPDATE_FLAGGED = "marked_for_update";
//  static public final String RESTART_FLAG = "requires_restart";

  protected String id;  // 1 (unique id for this library)
  protected File folder;
  protected StringDict properties;
  protected ClassLoader loader;


  public LocalContribution(File folder) {
    this.folder = folder;

    // required for contributed modes, but not for built-in core modes
    File propertiesFile = new File(folder, getTypeName() + ".properties");
    if (propertiesFile.exists()) {
      properties = Util.readSettings(propertiesFile, false);

      if (properties != null) {
        name = properties.get("name");
        id = properties.get("id");
        categories = parseCategories(properties);

        // Only used by Libraries and Modes
        imports = parseImports(properties, IMPORTS_PROPERTY);
        exports = parseImports(properties, EXPORTS_PROPERTY);

        if (name == null) {
          name = folder.getName();
        }
        // changed 'authorList' to 'authors' in 3.0a11
        authors = properties.get(AUTHORS_PROPERTY);
        url = properties.get("url");
        sentence = properties.get("sentence");
        paragraph = properties.get("paragraph");

        try {
          version = Integer.parseInt(properties.get("version"));
        } catch (NumberFormatException e) {
          System.err.println("The version number for the “" + name + "” library is not a number.");
          System.err.println("Please contact the library author to fix it according to the guidelines.");
        }

        setPrettyVersion(properties.get("prettyVersion"));

        try {
          lastUpdated = Long.parseLong(properties.get("lastUpdated"));
        } catch (NumberFormatException e) {
          lastUpdated = 0;
        }

        String minRev = properties.get("minRevision");
        if (minRev != null) {
          minRevision = PApplet.parseInt(minRev, 0);
        }

        String maxRev = properties.get("maxRevision");
        if (maxRev != null) {
          maxRevision = PApplet.parseInt(maxRev, 0);
        }
      } else {
        Messages.log("Could not read " + propertiesFile.getAbsolutePath());
      }
    } else {
      Messages.log("No properties file at " + propertiesFile.getAbsolutePath());
    }
    if (name == null) {  // fall-through case
      // We'll need this to be set at a minimum.
      name = folder.getName();
      categories = new StringList(UNKNOWN_CATEGORY);
    }
  }


  public String initLoader(String className) throws Exception {
    File modeDirectory = new File(folder, getTypeName());
    if (modeDirectory.exists()) {
      Messages.log("checking mode folder regarding " + className);
      // If no class name specified, search the main <modename>.jar for the
      // full name package and mode name.
      if (className == null) {
        String shortName = folder.getName();
        File mainJar = new File(modeDirectory, shortName + ".jar");
        if (mainJar.exists()) {
          className = findClassInZipFile(shortName, mainJar);
        } else {
          throw new IgnorableException(mainJar.getAbsolutePath() + " does not exist.");
        }

        if (className == null) {
          throw new IgnorableException("Could not find " + shortName +
                                       " class inside " + mainJar.getAbsolutePath());
        }
      }

      // Add .jar and .zip files from the "mode" folder into the classpath
      File[] archives = Util.listJarFiles(modeDirectory);
      if (archives != null && archives.length > 0) {
        URL[] urlList = new URL[archives.length];
        for (int j = 0; j < urlList.length; j++) {
          Messages.log("Found archive " + archives[j] + " for " + getName());
          urlList[j] = archives[j].toURI().toURL();
        }
//        loader = new URLClassLoader(urlList, Thread.currentThread().getContextClassLoader());
        loader = new URLClassLoader(urlList);
        Messages.log("loading above JARs with loader " + loader);
//        System.out.println("listing classes for loader " + loader);
//        listClasses(loader);
      }
    }

    // If no archives were found, just use the regular ClassLoader
    if (loader == null) {
      loader = Thread.currentThread().getContextClassLoader();
    }
    return className;
  }


  LocalContribution copyAndLoad(Base base,
                                StatusPanel status) {
    // NOTE: null status => function is called on startup
    // when Editor objects, et al. aren't ready

    String contribFolderName = getFolder().getName();

    File contribTypeFolder = getType().getSketchbookFolder();
    File contribFolder = new File(contribTypeFolder, contribFolderName);

    // when status is null, that means we're starting up the PDE
    if (status != null) {
      Editor editor = base.getActiveEditor();

      List<LocalContribution> oldContribs =
        getType().listContributions(base, editor);

      // In case an update marker exists, and the user wants to install, delete the update marker
      if (contribFolder.exists() && !contribFolder.isDirectory()) {
        contribFolder.delete();
        contribFolder = new File(contribTypeFolder, contribFolderName);
      }

      for (LocalContribution oldContrib : oldContribs) {
        if ((oldContrib.getFolder().exists() && oldContrib.getFolder().equals(contribFolder)) ||
            (oldContrib.getId() != null && oldContrib.getId().equals(getId()))) {

          if (oldContrib.getType().requiresRestart()) {
            if (!oldContrib.backup(false, status)) {
              return null;
            }
          } else {
            int result;
            boolean doBackup = Preferences.getBoolean("contribution.backup.on_install");
            if ((doBackup && !oldContrib.backup(true, status)) ||
                  (!doBackup && !oldContrib.getFolder().delete())) {
              return null;
            }
          }
        }
      }

      // At this point it should be safe to replace this fella
      if (contribFolder.exists()) {
        //Util.removeDir(contribFolder);
        try {
          Platform.deleteFile(contribFolder);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

    } else {
      // This if should ideally never happen, since this function
      // is to be called only when restarting on update
      if (contribFolder.exists() && contribFolder.isDirectory()) {
        //Util.removeDir(contribFolder);
        try {
          Platform.deleteFile(contribFolder);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      else if (contribFolder.exists()) {
        contribFolder.delete();
        contribFolder = new File(contribTypeFolder, contribFolderName);
      }
    }

    File oldFolder = getFolder();
    try {
      Util.copyDir(oldFolder, contribFolder);
    } catch (IOException e) {
      status.setErrorMessage("Could not copy " + getTypeName() +
                             " \"" + getName() + "\" to the sketchbook.");
      e.printStackTrace();
      return null;
    }

    return getType().load(base, contribFolder);
  }


  /**
   * Moves the given contribution to a backup folder.
   * @param deleteOriginal
   *          true if the file should be moved to the directory, false if it
   *          should instead be copied, leaving the original in place
   */
  boolean backup(boolean deleteOriginal, StatusPanel status) {
    File backupFolder = getType().createBackupFolder(status);

    boolean success = false;
    if (backupFolder != null) {
      String libFolderName = getFolder().getName();
      String prefix = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
      final String backupName = prefix + " " + libFolderName;
      File backupSubFolder =
        ContributionManager.getUniqueName(backupFolder, backupName);

      if (deleteOriginal) {
        success = getFolder().renameTo(backupSubFolder);
      } else {
        try {
          Util.copyDir(getFolder(), backupSubFolder);
          success = true;
        } catch (IOException ignored) { }
      }
      if (!success) {
        status.setErrorMessage("Could not move contribution to backup folder.");
      }
    }
    return success;
  }


  /**
   * Non-blocking call to remove a contribution in a new thread.
   */
  protected void removeContribution(Base base,
                                    ContribProgress pm,
                                    StatusPanel status,
                                    boolean updating) {
    // TODO: replace with SwingWorker [jv]
    new Thread(() -> {
      pm.startTask("Removing");

      if (getType() == ContributionType.MODE) {
        if (!removeMode(base, updating)) {
          pm.cancel();
          return;
        }

      } else if (getType() == ContributionType.TOOL) {
        // menu will be rebuilt below with the refreshContribs() call
        base.clearToolMenus();
        ((ToolContribution) this).clearClassLoader();
      }

      boolean success;
      boolean doBackup = Preferences.getBoolean("contribution.backup.on_remove");
      if (doBackup) {
        success = backup(true, status);
      } else {
        try {
          success = Platform.deleteFile(getFolder());
        } catch (IOException e) {
          e.printStackTrace();
          success = false;
        }
      }

      if (success) {
        try {
          // TODO: run this in SwingWorker done() [jv]
          EventQueue.invokeAndWait(() -> {
            ContributionListing cl = ContributionListing.getInstance();

            Contribution advertisedVersion =
              cl.findAvailableContribution(LocalContribution.this);

            if (advertisedVersion == null) {
              cl.removeContribution(LocalContribution.this);
            } else {
              cl.replaceContribution(LocalContribution.this, advertisedVersion);
            }
            cl.updateTableModels();
            base.refreshContribs(LocalContribution.this.getType());
            base.tallyUpdatesAvailable();
          });
        } catch (InterruptedException e) {
          e.printStackTrace();
        } catch (InvocationTargetException e) {
          Throwable cause = e.getCause();
          if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
          } else {
            cause.printStackTrace();
          }
        }

      } else {
        // There was a failure backing up the folder
        if (!doBackup || backup(false, status)) {
          if (setDeletionFlag(true)) {
            try {
              // TODO: run this in SwingWorker done() [jv]
              EventQueue.invokeAndWait(() -> {
                ContributionListing cl = ContributionListing.getInstance();
                cl.replaceContribution(LocalContribution.this, LocalContribution.this);
                cl.updateTableModels();
                base.refreshContribs(LocalContribution.this.getType());
                base.tallyUpdatesAvailable();
              });
            } catch (InterruptedException e) {
              e.printStackTrace();
            } catch (InvocationTargetException e) {
              Throwable cause = e.getCause();
              if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
              } else {
                cause.printStackTrace();
              }
            }
          }
        } else {
          status.setErrorMessage("Could not delete the contribution's files");
        }
      }
      if (success) {
        pm.finished();
      } else {
        pm.cancel();
      }
    }, "Contribution Uninstaller").start();
  }


  private boolean removeMode(Base base, boolean updating) {
    List<Editor> editors = new ArrayList<>();  // might be nice to be in order
    ModeContribution m = (ModeContribution) this;
    for (Editor editor : base.getEditors()) {
      if (editor.getMode().equals(m.getMode())) {
        Sketch sketch = editor.getSketch();
        if (sketch.isModified()) {
          editor.toFront();
          Messages.showMessage("Save Sketch",
            "Please first save “" + sketch.getName() + "”.");
          return false;
        } else {
          // Keep track of open Editor windows using this Mode
          //sketchMainList.add(sketch.getMainPath());
          //sketches.add(sketch);
          editors.add(editor);
        }
      }
    }
    // Close any open Editor windows that were using this Mode,
    // and if updating, build up a list of paths for the sketches
    // so that we can dispose of the Editor objects.
    //StringList sketchPathList = new StringList();
    for (Editor editor : editors) {
      //sketchPathList.append(editor.getSketch().getMainPath());
      StatusDetail.storeSketchPath(editor.getSketch().getMainPath());
      base.handleClose(editor, true);
    }
    editors.clear();
    m.clearClassLoader(base);
    //StatusPanelDetail.storeSketches(sketchPathList);

      /*
        pm.cancel();
        Messages.showMessage("Mode Manager",
                             "Please save your Sketch and change the Mode of all Editor\n" +
                             "windows that have " + name + " as the active Mode.");
        return;
      */

    if (!updating) {
      // Notify the Base in case this is the current Mode
      base.modeRemoved(m.getMode());
      // If that was the last Editor window, and we deleted its Mode,
      // open a fresh window using the default Mode.
      if (base.getEditors().size() == 0) {
        base.handleNew();
      }
    }
    return true;
  }


  public File getFolder() {
    return folder;
  }


  public boolean isInstalled() {
    return folder != null;
  }


  public String getId() {
    return id;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  boolean setDeletionFlag(boolean flag) {
    return setFlag(DELETION_FLAG, flag);
  }


  boolean isDeletionFlagged() {
    return isDeletionFlagged(getFolder());
  }


  static boolean isDeletionFlagged(File folder) {
    return isFlagged(folder, DELETION_FLAG);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  boolean setUpdateFlag() {
    return setFlag(UPDATE_FLAGGED, true);
  }


  boolean isUpdateFlagged() {
    return isUpdateFlagged(getFolder());
  }


  static boolean isUpdateFlagged(File folder) {
    return isFlagged(folder, UPDATE_FLAGGED);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /*
  boolean setRestartFlag() {
    //System.out.println("setting restart flag for " + folder);
    return setFlag(RESTART_FLAG, true);
  }


  @Override
  boolean isRestartFlagged() {
    //System.out.println("checking for restart inside LocalContribution for " + getName());
    return isFlagged(getFolder(), RESTART_FLAG);
  }


  static void clearRestartFlag(File folder) {
    File restartFlag = new File(folder, RESTART_FLAG);
    if (restartFlag.exists()) {
      restartFlag.delete();
    }
  }
  */


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  private boolean setFlag(String flagFilename, boolean flag) {
    if (flag) {
      // Only returns false if the file already exists, so we can
      // ignore the return value.
      try {
        new File(getFolder(), flagFilename).createNewFile();
        return true;
      } catch (IOException e) {
        return false;
      }
    } else {
      return new File(getFolder(), flagFilename).delete();
    }
  }


  static private boolean isFlagged(File folder, String flagFilename) {
    return new File(folder, flagFilename).exists();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   *
   * @param base name of the class, with or without the package
   * @return name of class (with full package name) or null if not found
   */
  static protected String findClassInZipFile(String base, File file) {
    // Class file to search for
    String classFileName = "/" + base + ".class";

    try {
      ZipFile zipFile = new ZipFile(file);
      Enumeration<?> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = (ZipEntry) entries.nextElement();

        if (!entry.isDirectory()) {
          String name = entry.getName();
//          System.out.println("entry: " + name);

          if (name.endsWith(classFileName)) {
            //int slash = name.lastIndexOf('/');
            //String packageName = (slash == -1) ? "" : name.substring(0, slash);
            // Remove .class and convert slashes to periods.
            zipFile.close();
            return name.substring(0, name.length() - 6).replace('/', '.');
          }
        }
      }
      zipFile.close();
    } catch (IOException e) {
      //System.err.println("Ignoring " + filename + " (" + e.getMessage() + ")");
      e.printStackTrace();
    }
    return null;
  }
}
