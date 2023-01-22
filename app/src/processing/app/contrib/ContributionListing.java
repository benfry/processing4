/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013-23 The Processing Foundation
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
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import processing.app.Base;
import processing.app.UpdateCheck;
import processing.app.Util;
import processing.core.PApplet;
import processing.data.StringDict;
import processing.data.StringList;


public class ContributionListing {
  static volatile ContributionListing singleInstance;

  /**
   * Stable URL that will redirect to wherever the file is hosted.
   * Changed to use https in 4.0 beta 8 (returns same data).
   */
  static final String LISTING_URL = "https://download.processing.org/contribs";
  static final String LOCAL_FILENAME = "contribs.txt";

  /** Location of the listing file on disk, will be read and written. */
  private File listingFile;
  private boolean listDownloaded;
//  boolean listDownloadFailed;
  final private ReentrantLock downloadingLock = new ReentrantLock();

  final Set<AvailableContribution> availableContribs;
  final private Map<String, Contribution> libraryExports;
  final private Set<Contribution> allContribs;

  Set<ListPanel> listPanels;


  private ContributionListing() {
    listPanels = new HashSet<>();
    availableContribs = new HashSet<>();
    libraryExports = new HashMap<>();
    allContribs = ConcurrentHashMap.newKeySet();

    listingFile = Base.getSettingsFile(LOCAL_FILENAME);
    if (listingFile.exists()) {
      // On the EDT already, but do this later on the EDT so that the
      // constructor can finish more efficiently inside getInstance().
      EventQueue.invokeLater(() -> loadAvailableList(listingFile));
    }
  }


  static public ContributionListing getInstance() {
    if (singleInstance == null) {
      synchronized (ContributionListing.class) {
        if (singleInstance == null) {
          singleInstance = new ContributionListing();
        }
      }
    }
    return singleInstance;
  }


  static protected Set<Contribution> getAllContribs() {
    return getInstance().allContribs;
  }


  /**
   * Update the list of contribs with entries for what is installed.
   * If it matches an entry from contribs.txt, replace that entry.
   * If not, add it to the list as a new contrib.
   */
  static protected void updateInstalled(Set<Contribution> installedContribs) {
    ContributionListing listing = getInstance();

    for (Contribution installed : installedContribs) {
      Contribution listed = listing.findContribution(installed);
      if (listed != null) {
        if (listed != installed) {
          // don't replace contrib with itself
          listing.replaceContribution(listed, installed);
        }
      } else {
        listing.addContribution(installed);
      }
    }
    listing.updateTableModels();
  }


  private Contribution findContribution(Contribution contribution) {
    for (Contribution c : allContribs) {
      if (c.getName().equals(contribution.getName()) &&
        c.getType() == contribution.getType()) {
        return c;
      }
    }
    return null;
  }


  // This could just be a remove followed by an add, but contributionChanged()
  // is a little weird, so that should be cleaned up first [fry 230114]
  protected void replaceContribution(Contribution oldContrib, Contribution newContrib) {
//    removeContribution(oldContrib);
//    addContribution(newContrib);

    if (oldContrib != null && newContrib != null) {
      /*
      if (oldContrib.getImports() != null) {
        for (String importName : oldContrib.getImports()) {
//          System.out.println("replaceContribution() removing import " + importName);
          importToLibrary.remove(importName);
        }
      }
      if (newContrib.getImports() != null) {
        for (String importName : newContrib.getImports()) {
//          System.out.println("replaceContribution() putting import " + importName);
          importToLibrary.put(importName, newContrib);
        }
      }
      */
      allContribs.remove(oldContrib);
      allContribs.add(newContrib);

      for (ListPanel listener : listPanels) {
        listener.contributionChanged(oldContrib, newContrib);
      }
    }
  }


  private void addContribution(Contribution contribution) {
      /*
    if (contribution.getImports() != null) {
      for (String importName : contribution.getImports()) {
//        System.out.println("addContribution() putting import " + importName);
        importToLibrary.put(importName, contribution);
      }
    }
      */
    allContribs.add(contribution);

    for (ListPanel listener : listPanels) {
      listener.contributionAdded(contribution);
    }
  }


  protected void removeContribution(Contribution contribution) {
    /*
    if (contribution.getImports() != null) {
      for (String importName : contribution.getImports()) {
//        System.out.println("removeContribution() removing import " + importName);
        importToLibrary.remove(importName);
      }
    }
    */
    allContribs.remove(contribution);

    for (ListPanel listener : listPanels) {
      listener.contributionRemoved(contribution);
    }
  }


  protected void updateTableModels() {
    for (ListPanel listener : listPanels) {
      listener.updateModel();
    }
  }


  /**
   * Given a contribution that's already installed, find it in the list
   * of available contributions to see if there is an update available.
   */
  protected AvailableContribution findAvailableContribution(Contribution contrib) {
    synchronized (availableContribs) {
      for (AvailableContribution advertised : availableContribs) {
        if (advertised.getType() == contrib.getType() &&
            advertised.getName().equals(contrib.getName())) {
          return advertised;
        }
      }
    }
    return null;
  }


  // formerly addListener(), but the ListPanel was the only Listener
  protected void addListPanel(ListPanel listener) {
    listPanels.add(listener);
  }


  /**
   * Starts a new thread to download the advertised list of contributions.
   * Only one instance will run at a time.
   */
  public void downloadAvailableList(final Base base,
                                    final ContribProgress progress) {
    // TODO: replace with SwingWorker [jv]
    new Thread(() -> {
      downloadingLock.lock();

      try {
        URL url = new URL(LISTING_URL);
        File tempContribFile = Base.getSettingsFile("contribs.tmp");
        if (tempContribFile.exists() && !tempContribFile.canWrite()) {
          if (!tempContribFile.setWritable(true, false)) {
            System.err.println("Could not set " + tempContribFile + " writable");
          }
        }
        ContributionManager.download(url, makeContribsBlob(base),
                                     tempContribFile, progress);
        if (progress.notCanceled() && !progress.isException()) {
          if (listingFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            listingFile.delete();  // may silently fail, but below may still work
          }
          if (tempContribFile.renameTo(listingFile)) {
            listDownloaded = true;
//            listDownloadFailed = false;
            try {
              // TODO: run this in SwingWorker done() [jv]
              EventQueue.invokeAndWait(() -> {
                loadAvailableList(listingFile);
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
//          } else {
//            listDownloadFailed = true;
          }
        }

      } catch (MalformedURLException e) {
        progress.setException(e);
        progress.finished();
      } finally {
        downloadingLock.unlock();
      }
    }, "Contribution List Downloader").start();
  }


  // Thread: EDT
  private void loadAvailableList(File file) {
    listingFile = file;

    availableContribs.clear();
    availableContribs.addAll(parseContribList(listingFile));

    // Only updated whenever the available list is loaded.
    // No need to do this after/during the installation process.
    libraryExports.clear();

    for (Contribution available : availableContribs) {
      addContribution(available);
      if (available.getType() == ContributionType.LIBRARY) {
        // Only needs to be called for libraries, the list of exports
        // maps packages to available libraries for auto-installation.
        StringList exports = available.getExports();
        if (exports != null) {
          for (String export : exports) {
            libraryExports.put(export, available);
          }
        }
      }
    }
    updateTableModels();
  }


  /**
   * Bundles information about what contribs are installed, so that they can
   * be reported at the <a href="https://download.processing.org/stats/">stats</a> link.
   * (Eventually this may also be used to show relative popularity of contribs.)
   * Read more about it <a href="<a href="https://github.com/processing/processing4/wiki/FAQ#checking-for-updates">here</a>.">in the FAQ</a>.
   */
  private byte[] makeContribsBlob(Base base) {
    Set<Contribution> contribs = base.getInstalledContribs();
    StringList entries = new StringList();
    for (Contribution c : contribs) {
      String entry = c.getTypeName() + "=" +
        PApplet.urlEncode(String.format("name=%s\nurl=%s\nrevision=%d\nversion=%s",
          c.getName(), c.getUrl(),
          c.getVersion(), c.getBenignVersion()));
      entries.append(entry);
    }
    String joined =
      "id=" + UpdateCheck.getUpdateID() + "&" + entries.join("&");
    return joined.getBytes();
  }


  public boolean hasUpdates(Contribution contrib) {
    if (contrib.isInstalled()) {
      Contribution available = findAvailableContribution(contrib);
      return available != null &&
        available.getVersion() > contrib.getVersion() &&
        available.isCompatible();
    }
    return false;
  }


  /**
   * Get the human-readable version number from the available list.
   */
  protected String getLatestPrettyVersion(Contribution contrib) {
    Contribution newestContrib = findAvailableContribution(contrib);
    if (newestContrib != null) {
      return newestContrib.getPrettyVersion();
    }
    return null;
  }


  static private List<AvailableContribution> parseContribList(File file) {
    List<AvailableContribution> outgoing = new ArrayList<>();

    if (file != null && file.exists()) {
      String[] lines = PApplet.loadStrings(file);
      if (lines != null) {
        int start = 0;
        while (start < lines.length) {
          String type = lines[start];
          ContributionType contribType = ContributionType.fromName(type);
          if (contribType == null) {
            System.err.println("Error in contribution listing file on line " + (start + 1));
            // Scan forward for the next blank line
            int end = ++start;
            while (end < lines.length && !lines[end].trim().isEmpty()) {
              end++;
            }
            start = end + 1;

          } else {
            // Scan forward for the next blank line
            int end = ++start;
            while (end < lines.length && !lines[end].trim().isEmpty()) {
              end++;
            }

            String[] contribLines = PApplet.subset(lines, start, end - start);
            StringDict contribParams = Util.readSettings(file.getName(), contribLines, false);
            outgoing.add(new AvailableContribution(contribType, contribParams));
            start = end + 1;
          }
        }
      }
    }
    return outgoing;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Used by JavaEditor to auto-import. Not known to be used by other Modes.
   */
  public Map<String, Contribution> getLibraryExports() {
    return libraryExports;
  }
}
