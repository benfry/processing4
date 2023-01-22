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
import java.net.*;
import java.util.*;

import processing.app.Base;
import processing.app.Language;
import processing.app.Messages;
import processing.app.Platform;
import processing.app.Util;
import processing.app.ui.Editor;
import processing.core.PApplet;
import processing.data.StringDict;


public class ContributionManager {
  static ManagerFrame managerFrame;
  static ContributionListing contribListing;


  /**
   * Blocks until the file is downloaded or an error occurs.
   *
   * @param source the URL of the file to download
   * @param post Binary blob of POST data if there is data to be sent.
   *             Must already be URL-encoded and will be Gzipped for upload.
   *             If null, the connection will use GET instead of POST.
   * @param dest The location on the local system to write the file.
   *             Its parent directory must already exist.
   * @param progress null if progress is irrelevant, such as when downloading
   *                 files for installation during startup, when the
   *                 ProgressMonitor is useless because the UI is unavailable.
   *
   * @return true if the file was successfully downloaded, false otherwise.
   */
  static boolean download(URL source, byte[] post,
                          File dest, ContribProgress progress) {
    boolean success = false;
    try {
      HttpURLConnection conn = (HttpURLConnection) source.openConnection();
      // Will not handle a protocol change (see below)
      HttpURLConnection.setFollowRedirects(true);
      conn.setConnectTimeout(15 * 1000);
      conn.setReadTimeout(60 * 1000);

      if (post == null) {
        conn.setRequestMethod("GET");
        conn.connect();

      } else {
        post = Util.gzipEncode(post);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Content-Encoding", "gzip");
        conn.setRequestProperty("Content-Length", String.valueOf(post.length));
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.getOutputStream().write(post);
      }

      if (progress != null) {
        // TODO this is often -1, may need to set progress to indeterminate
        int fileSize = conn.getContentLength();
//      System.out.println("file size is " + fileSize);
        progress.startTask(Language.text("contrib.progress.downloading"), fileSize);
      }

      int response = conn.getResponseCode();
      // Default won't follow HTTP -> HTTPS redirects for security reasons
      // http://stackoverflow.com/a/1884427
      if (response >= 300 && response < 400) {
        // Handle SSL redirects from HTTP sources
        // https://github.com/processing/processing/issues/5554
        String newLocation = conn.getHeaderField("Location");
        return download(new URL(newLocation), post, dest, progress);

      } else {
        InputStream in = conn.getInputStream();
        FileOutputStream out = new FileOutputStream(dest);

        byte[] b = new byte[8192];
        int amount;
        if (progress != null) {
          int total = 0;
          while (progress.notCanceled() && (amount = in.read(b)) != -1) {
            out.write(b, 0, amount);
            total += amount;
            progress.setProgress(total);
          }
        } else {
          while ((amount = in.read(b)) != -1) {
            out.write(b, 0, amount);
          }
        }
        out.flush();
        out.close();
        success = true;
      }
    } catch (IOException ioe) {
      if (progress != null) {
        progress.setException(ioe);
        progress.cancel();
      }
    }
    if (progress != null) {
      progress.finished();
    }
    return success;
  }


  /**
   * Non-blocking call to download and install a contribution in a new thread.
   */
  static void downloadAndInstall(final Base base,
                                 final URL url,
                                 final AvailableContribution available,
                                 final ContribProgress downloadProgress,
                                 final ContribProgress installProgress,
                                 final StatusPanel status) {
    // TODO: replace with SwingWorker [jv]
    new Thread(() -> {
      String filename = url.getFile();
      filename = filename.substring(filename.lastIndexOf('/') + 1);
      try {
        File contribZip = File.createTempFile("download", filename);
        contribZip.setWritable(true);  // necessary?

        try {
          download(url, null, contribZip, downloadProgress);

          if (downloadProgress.notCanceled() && !downloadProgress.isException()) {
            installProgress.startTask(Language.text("contrib.progress.installing"));
            final LocalContribution installed =
              available.install(base, contribZip, status);

            if (installed != null) {
              try {
                // TODO: run this in SwingWorker done() [jv]
                EventQueue.invokeAndWait(() -> {
                  contribListing.replaceContribution(available, installed);
                  contribListing.updateTableModels();
                  base.refreshContribs(installed.getType());
                  base.tallyUpdatesAvailable();
                });
              } catch (InterruptedException e) {
                e.printStackTrace();
              } catch (InvocationTargetException e) {
                throw (Exception) e.getCause();
              }
            }
            installProgress.finished();

          } else {
            Exception exception = downloadProgress.getException();
            if (exception instanceof SocketTimeoutException) {
              status.setErrorMessage(Language
                .interpolate("contrib.errors.contrib_download.timeout",
                             available.getName()));
            } else if (exception != null) {
              status.setErrorMessage(Language
                .interpolate("contrib.errors.download_and_install",
                             available.getName()));
              exception.printStackTrace();
            }
          }
          contribZip.delete();

        } catch (Exception e) {
          String msg = null;
          if (e instanceof RuntimeException) {
            Throwable cause = e.getCause();
            if (cause instanceof NoClassDefFoundError ||
                cause instanceof NoSuchMethodError) {
              msg = "This item is not compatible with this version of Processing";
            } else if (cause instanceof UnsupportedClassVersionError) {
              msg = "This needs to be recompiled for Java " + PApplet.javaPlatform;
            }
          }

          if (msg == null) {
            msg = Language.interpolate("contrib.errors.download_and_install", available.getName());
            // Something unexpected, so print the trace for bug tracking
            e.printStackTrace();
          }
          status.setErrorMessage(msg);
          downloadProgress.cancel();
          installProgress.cancel();
        }
      } catch (IOException e) {
        status.setErrorMessage(Language.text("contrib.errors.temporary_directory"));
        downloadProgress.cancel();
        installProgress.cancel();
      }
    }, "Contribution Installer").start();
  }


  /**
   * Non-blocking call to download and install a contribution in a new thread.
   * Used when information about the progress of the download and install
   * procedure is not of importance, such as if a contribution has to be
   * installed at startup time.
   *
   * @param url Direct link to the contribution.
   * @param available The AvailableContribution to be downloaded and installed.
   */
  static void downloadAndInstallOnStartup(final Base base, final URL url,
                                          final AvailableContribution available) {
    // TODO: replace with SwingWorker [jv]
    new Thread(() -> {
      String filename = url.getFile();
      filename = filename.substring(filename.lastIndexOf('/') + 1);
      try {
        File contribZip = File.createTempFile("download", filename);
        try {
          download(url, null, contribZip, null);
          final LocalContribution installed =
            available.install(base, contribZip, null);

          if (installed != null) {
            try {
              // TODO: run this in SwingWorker done() [jv]
              EventQueue.invokeAndWait(() -> {
                contribListing.replaceContribution(available, installed);
                contribListing.updateTableModels();
                base.refreshContribs(installed.getType());
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
          if (contribZip.exists() && !contribZip.delete()) {
            System.err.println("Could not delete " + contribZip);
          }
          handleUpdateFailedMarkers(available);

        } catch (Exception e) {
          String arg = "contrib.startup.errors.download_install";
          System.err.println(Language.interpolate(arg, available.getName()));
        }
      } catch (IOException e) {
        String arg = "contrib.startup.errors.temp_dir";
        System.err.println(Language.interpolate(arg, available.getName()));
      }
    }, "Contribution Installer").start();
  }


  /**
   * After install, this function checks whether everything went properly.
   * If not, it adds a marker file so that the next time Processing is started,
   * installPreviouslyFailed() can install the contribution.
   * @param c the contribution just installed
   */
  static private void handleUpdateFailedMarkers(final AvailableContribution c) {
    File typeFolder = c.getType().getSketchbookFolder();
    if (typeFolder != null) {
      File[] folderList = typeFolder.listFiles();
      if (folderList != null) {
        for (File contribDir : folderList) {
          if (contribDir.isDirectory()) {
            File propsFile = new File(contribDir, c.getType() + ".properties");
            if (propsFile.exists()) {
              StringDict props = Util.readSettings(propsFile, false);
              if (props != null) {
                if (c.getName().equals(props.get("name"))) {
                  return;
                }
              }
            }
          }
        }
      }
    }

    try {
      new File(typeFolder, c.getName()).createNewFile();
    } catch (IOException e) {
      String arg = "contrib.startup.errors.new_marker";
      System.err.println(Language.interpolate(arg, c.getName()));
    }
  }


  /**
   * Blocking call to download and install a set of libraries. Used when a list
   * of libraries have to be installed while forcing the user to not modify
   * anything and providing feedback via the console status area, such as when
   * the user tries to run a sketch that imports uninstalled libraries.
   *
   * @param list The list of AvailableContributions to be downloaded and installed.
   */
  static public void downloadAndInstallOnImport(final Base base,
                                                final List<AvailableContribution> list) {
    // Disable the Editor to avoid the user from modifying anything,
    // because this function is only called during pre-processing.
    Editor editor = base.getActiveEditor();
    editor.getTextArea().setEditable(false);

    List<String> installedLibList = new ArrayList<>();

    // boolean variable to check if previous lib was installed successfully,
    // to give the user an idea about progress being made.
    boolean prevDone = false;

    for (final AvailableContribution available : list) {
      if (available.getType() != ContributionType.LIBRARY) {
        continue;
      }
      try {
        URL url = new URL(available.link);
        String filename = url.getFile();
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        try {

          File contribZip = File.createTempFile("download", filename);
          contribZip.setWritable(true);

          try {
            // Use the console to let the user know what's happening
            // The slightly complex if-else is required to let the user know when
            // one install is completed and the next download has begun without
            // interfering with other status messages that may arise in the meanwhile
            String statusMsg = editor.getStatusMessage();
            if (prevDone) {
              String status = statusMsg + " "
                + Language.interpolate("contrib.import.progress.download", available.name);
              editor.statusNotice(status);
            } else {
              String arg = "contrib.import.progress.download";
              String status = Language.interpolate(arg, available.name);
              editor.statusNotice(status);
            }

            prevDone = false;

            download(url, null, contribZip, null);

            String arg = "contrib.import.progress.install";
            editor.statusNotice(Language.interpolate(arg,available.name));
            final LocalContribution installed =
              available.install(base, contribZip, null);

            if (installed != null) {
              try {
                EventQueue.invokeAndWait(() -> {
                  contribListing.replaceContribution(available, installed);
                  contribListing.updateTableModels();
                  base.refreshContribs(installed.getType());
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

            contribZip.delete();

            installedLibList.add(available.name);
            prevDone = true;

            arg = "contrib.import.progress.done";
            editor.statusNotice(Language.interpolate(arg,available.name));

          } catch (Exception e) {
            String arg = "contrib.startup.errors.download_install";
            System.err.println(Language.interpolate(arg, available.getName()));
          }
        } catch (IOException e) {
          String arg = "contrib.startup.errors.temp_dir";
          System.err.println(Language.interpolate(arg,available.getName()));
        }
      } catch (MalformedURLException e1) {
        System.err.println(Language.interpolate("contrib.import.errors.link",
                                                available.getName()));
      }
    }
    editor.getTextArea().setEditable(true);
    editor.statusEmpty();
    System.out.println(Language.text("contrib.import.progress.final_list"));
    for (String l : installedLibList) {
      System.out.println("  * " + l);
    }
  }


  /**
   * Returns a file in the parent folder that does not exist yet. If
   * parent/fileName already exists, this will look for parent/fileName(2)
   * then parent/fileName(3) and so forth.
   *
   * @return a file that does not exist yet
   */
  static public File getUniqueName(File parentFolder, String fileName) {
    File backupFolderForLib;
    int i = 1;
    do {
      String folderName = fileName;
      if (i >= 2) {
        folderName += "(" + i + ")";
      }
      i++;

      backupFolderForLib = new File(parentFolder, folderName);
    } while (backupFolderForLib.exists());

    return backupFolderForLib;
  }


  /**
   * Returns the name of a file without its path or extension.
   *
   * For example,
   *   "/path/to/helpfullib.zip" returns "helpfullib"
   *   "helpfullib-0.1.1.plb" returns "helpfullib-0.1.1"
   */
  static public String getFileName(File libFile) {
    String path = libFile.getPath();
    int lastSeparator = path.lastIndexOf(File.separatorChar);

    String fileName;
    if (lastSeparator != -1) {
      fileName = path.substring(lastSeparator + 1);
    } else {
      fileName = path;
    }

    int lastDot = fileName.lastIndexOf('.');
    if (lastDot != -1) {
      return fileName.substring(0, lastDot);
    }

    return fileName;
  }


  /**
   * Called by Base to clean up entries previously marked for deletion
   * and remove any "requires restart" flags.
   * Also updates all entries previously marked for update.
   */
  static private void cleanup(final Base base) throws Exception {
    deleteTemp(Base.getSketchbookModesFolder());
    deleteTemp(Base.getSketchbookToolsFolder());

    deleteFlagged(Base.getSketchbookLibrariesFolder());
    deleteFlagged(Base.getSketchbookModesFolder());
    deleteFlagged(Base.getSketchbookToolsFolder());

    installPreviouslyFailed(base, Base.getSketchbookModesFolder());

    updateFlagged(base, Base.getSketchbookModesFolder());
    updateFlagged(base, Base.getSketchbookToolsFolder());

    /*
    clearRestartFlags(Base.getSketchbookModesFolder());
    clearRestartFlags(Base.getSketchbookToolsFolder());
    */
  }


  /**
   * Deletes the icky tmp folders that were left over from installs and updates
   * in the previous run of Processing. Needed to be called only on the tools
   * and modes sketchbook folders.
   */
  static private void deleteTemp(File root) {
    String pattern = root.getName().substring(0, 4) + "\\d*" + "tmp";
    File[] possible = root.listFiles();
    if (possible != null) {
      for (File f : possible) {
        if (f.getName().matches(pattern)) {
          //Util.removeDir(f);
          try {
            Platform.deleteFile(f);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }


  /**
   * Deletes all the modes/tools/libs that are flagged for removal.
   */
  static private void deleteFlagged(File root) {
    File[] markedForDeletion = root.listFiles(folder ->
      (folder.isDirectory() && LocalContribution.isDeletionFlagged(folder))
    );
    if (markedForDeletion != null) {
      for (File folder : markedForDeletion) {
        //Util.removeDir(folder);
        try {
          Platform.deleteFile(folder);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }


  /**
   * Installs all the modes/tools whose installation failed during an
   * auto-update the previous time Processing was started up.
   */
  static private void installPreviouslyFailed(Base base, File root) throws Exception {
    File[] installList = root.listFiles(File::isFile);

    // https://github.com/processing/processing/issues/5823
    if (installList != null) {
      boolean found = false;
      for (File file : installList) {
        for (AvailableContribution available : contribListing.availableContribs) {
          if (file.getName().equals(available.getName())) {
            file.delete();
            installOnStartUp(base, available);
            EventQueue.invokeAndWait(() -> contribListing.replaceContribution(available, available));
            found = true;
          }
        }
      }
      if (found) {
        contribListing.updateTableModels();
      }
    } else {
      System.err.println("Could not read " + root);
    }
  }


  /**
   * Updates all the flagged Mode and Tool folders.
   */
  static private void updateFlagged(Base base, File root) throws Exception {
    // https://github.com/processing/processing/issues/6034
    if (!root.exists()) return;  // folder doesn't exist, nothing to update

    if (!root.canRead() || !root.canWrite()) {
      // Sometimes macOS users disallow access to the Documents folder,
      // then wonder why there's a problem accessing the Documents folder.
      // https://github.com/processing/processing4/issues/581
      // TODO would like this to be in a more central location, but this is
      //      where it's triggered most consistently, so it's here for now.
      if (Platform.isMacOS()) {
        // we're on the EDT here, so it's safe to show the error
        Messages.showError("Cannot access sketchbook",
          """
            There is a problem with the “permissions” for the sketchbook folder.
            Processing needs access to the Documents folder to save your work.
            Usually this happens after you click “Don't Allow” when macOS asks
            for access to your Documents folder. To fix:
            
            1. Quit Processing
            2. Open Applications → Utilities → Terminal
            3. Type “tccutil reset All org.processing.four” and press return
            4. Restart Processing, and when prompted for access, click “OK”
            
            If that's not the problem, the forum is a good place to get help:
            https://discourse.processing.org
            """, null);
      } else {
        throw new Exception("Please fix read/write permissions for " + root);
      }
    }

    File[] markedForUpdate = root.listFiles(folder ->
      (folder.isDirectory() && LocalContribution.isUpdateFlagged(folder)));

    List<String> updateContribsNames = new ArrayList<>();
    List<AvailableContribution> updateContribsList = new LinkedList<>();

    // TODO This is bad code... This root.getName() stuff to get the folder
    //      type, plus "libraries.properties" (not the correct file name).
    //      Not sure the function here so I'm not fixing it at the moment,
    //      but this whole function could use some cleaning. [fry 180105]

    // TODO getName() will (should?) never have a slash, wtf [fry 220312]
    String contribType = root.getName().substring(root.getName().lastIndexOf('/') + 1);
    String propFileName = null;

    if (contribType.equalsIgnoreCase("tools"))
      propFileName = "tool.properties";
    else if (contribType.equalsIgnoreCase("modes"))
      propFileName = "mode.properties";

    if (markedForUpdate != null) {
      for (File folder : markedForUpdate) {
        StringDict props = Util.readSettings(new File(folder, propFileName), false);
        if (props != null) {
          String name = props.get("name", null);
          if (name != null) {  // should not happen, but...
            updateContribsNames.add(name);
          }
          try {
            Platform.deleteFile(folder);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }

    for (AvailableContribution contrib : contribListing.availableContribs) {
      if (updateContribsNames.contains(contrib.getName())) {
        updateContribsList.add(contrib);
      }
    }

    for (AvailableContribution contrib : updateContribsList) {
      installOnStartUp(base, contrib);
      contribListing.replaceContribution(contrib, contrib);
    }
    if (!updateContribsList.isEmpty()) {
      contribListing.updateTableModels();
    }
  }


  static private void installOnStartUp(final Base base, final AvailableContribution availableContrib) {
    if (availableContrib.link == null) {
      Messages.showWarning(Language.interpolate("contrib.errors.update_on_restart_failed", availableContrib.getName()),
                           Language.text("contrib.missing_link"));
    } else {
      try {
        URL downloadUrl = new URL(availableContrib.link);
        ContributionManager.downloadAndInstallOnStartup(base, downloadUrl, availableContrib);

      } catch (MalformedURLException e) {
        Messages.showWarning(Language.interpolate("contrib.errors.update_on_restart_failed", availableContrib.getName()),
                             Language.text("contrib.errors.malformed_url"), e);
      }
    }
  }


  /*
  static private void clearRestartFlags(File root) {
    File[] folderList = root.listFiles(File::isDirectory);
    if (folderList != null) {
      for (File folder : folderList) {
        LocalContribution.clearRestartFlag(folder);
      }
    }
  }
  */


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static public void init(Base base) throws Exception {
//    long t1 = System.currentTimeMillis();
    // Moved here to make sure it runs on EDT [jv 170121]
    contribListing = ContributionListing.getInstance();
//    long t2 = System.currentTimeMillis();
    managerFrame = new ManagerFrame(base);
//    long t3 = System.currentTimeMillis();
    cleanup(base);
//    long t4 = System.currentTimeMillis();
//    System.out.println("ContributionManager.init() " + (t2-t1) + " " + (t3-t2) + " " + (t4-t3));
  }


  /*
  static public void downloadAvailable() {
    //ContributionListing cl = ContributionListing.getInstance();
    contribListing.downloadAvailableList(base, new ContribProgress(null));
  }
  */


  static public void updateTheme() {
    if (managerFrame != null) {
      managerFrame.updateTheme();
    }
  }


  /**
   * Show the Library installer window.
   */
  static public void openLibraries() {
    managerFrame.showFrame(ContributionType.LIBRARY);
  }


  /**
   * Show the Mode installer window.
   */
  static public void openModes() {
    managerFrame.showFrame(ContributionType.MODE);
  }


  /**
   * Show the Tool installer window.
   */
  static public void openTools() {
    managerFrame.showFrame(ContributionType.TOOL);
  }


  /**
   * Show the Examples installer window.
   */
  static public void openExamples() {
    managerFrame.showFrame(ContributionType.EXAMPLES);
  }


  /**
   * Open the updates panel.
   */
  static public void openUpdates() {
    managerFrame.showFrame(null);
  }
}
