/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013-16 The Processing Foundation
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
import java.util.concurrent.locks.ReentrantLock;

import processing.app.Base;
import processing.app.Library;
import processing.app.Util;
import processing.core.PApplet;
import processing.data.StringDict;


public class ContributionListing {
  static volatile ContributionListing singleInstance;

  /**
   * Stable URL that will redirect to wherever the file is hosted.
   * Changed to use https in 4.0 beta 8 (returns same data).
   */
  static final String LISTING_URL = "https://download.processing.org/contribs";
  static final String LOCAL_FILENAME = "contribs.txt";

  /** Location of the listing file on disk, will be read and written. */
  File listingFile;

  Set<ListPanel> listPanels;
  final List<AvailableContribution> advertisedContributions;
  Map<String, Contribution> librariesByImportHeader;
  Set<Contribution> allContributions;
  boolean listDownloaded;
//  boolean listDownloadFailed;
  ReentrantLock downloadingListingLock;


  private ContributionListing() {
    listPanels = new HashSet<>();
    advertisedContributions = new ArrayList<>();
    librariesByImportHeader = new HashMap<>();
    allContributions = new LinkedHashSet<>();
    downloadingListingLock = new ReentrantLock();

    listingFile = Base.getSettingsFile(LOCAL_FILENAME);
    if (listingFile.exists()) {
      // On the EDT already, but do this later on the EDT so that the
      // constructor can finish more efficiently for getInstance().
      EventQueue.invokeLater(() -> setAdvertisedList(listingFile));
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


  private void setAdvertisedList(File file) {
    listingFile = file;

    advertisedContributions.clear();
    advertisedContributions.addAll(parseContribList(listingFile));
    for (Contribution contribution : advertisedContributions) {
      addContribution(contribution);
    }
  }


  /**
   * Adds the installed libraries to the listing of libraries, replacing
   * any pre-existing libraries by the same name as one in the list.
   */
  protected void updateInstalledList(List<Contribution> installed) {
    for (Contribution contribution : installed) {
      Contribution existingContribution = getContribution(contribution);
      if (existingContribution != null) {
        if (existingContribution != contribution) {
          // don't replace contrib with itself
          replaceContribution(existingContribution, contribution);
        }
      } else {
        addContribution(contribution);
      }
    }
  }


  protected void replaceContribution(Contribution oldLib, Contribution newLib) {
    if (oldLib != null && newLib != null) {
      if (oldLib.getImports() != null) {
        for (String importName : oldLib.getImports()) {
          if (getLibrariesByImportHeader().containsKey(importName)) {
            getLibrariesByImportHeader().put(importName, newLib);
          }
        }
      }
      allContributions.remove(oldLib);
      allContributions.add(newLib);

      for (ListPanel listener : listPanels) {
        listener.contributionChanged(oldLib, newLib);
      }
    }
  }


  private void addContribution(Contribution contribution) {
    if (contribution.getImports() != null) {
      for (String importName : contribution.getImports()) {
        getLibrariesByImportHeader().put(importName, contribution);
      }
    }
    allContributions.add(contribution);

    for (ListPanel listener : listPanels) {
      listener.contributionAdded(contribution);
    }
  }


  protected void removeContribution(Contribution contribution) {
    if (contribution.getImports() != null) {
      for (String importName : contribution.getImports()) {
        getLibrariesByImportHeader().remove(importName);
      }
    }
    allContributions.remove(contribution);

    for (ListPanel listener : listPanels) {
      listener.contributionRemoved(contribution);
    }
  }


  private Contribution getContribution(Contribution contribution) {
    for (Contribution c : allContributions) {
      if (c.getName().equals(contribution.getName()) &&
          c.getType() == contribution.getType()) {
        return c;
      }
    }
    return null;
  }


  protected AvailableContribution getAvailableContribution(Contribution info) {
    synchronized (advertisedContributions) {
      for (AvailableContribution advertised : advertisedContributions) {
        if (advertised.getType() == info.getType() &&
            advertised.getName().equals(info.getName())) {
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
      downloadingListingLock.lock();

      try {
        URL url = new URL(LISTING_URL);
        File tempContribFile = Base.getSettingsFile("contribs.tmp");
        if (tempContribFile.exists() && !tempContribFile.canWrite()) {
          if (!tempContribFile.setWritable(true, false)) {
            System.err.println("Could not set " + tempContribFile + " writable");
          }
        }
        ContributionManager.download(url, base.getInstalledContribsInfo(),
                                     tempContribFile, progress);
        if (!progress.isCanceled() && !progress.isException()) {
          if (listingFile.exists()) {
            listingFile.delete();  // may silently fail, but below may still work
          }
          if (tempContribFile.renameTo(listingFile)) {
            listDownloaded = true;
//            listDownloadFailed = false;
            try {
              // TODO: run this in SwingWorker done() [jv]
              EventQueue.invokeAndWait(() -> {
                setAdvertisedList(listingFile);
                base.setUpdatesAvailable(countUpdates(base));
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
        downloadingListingLock.unlock();
      }
    }, "Contribution List Downloader").start();
  }


  protected boolean hasUpdates(Contribution contrib) {
    if (!contrib.isInstalled()) {
      return false;
    }
    Contribution advertised = getAvailableContribution(contrib);
    if (advertised == null) {
      return false;
    }
    return (advertised.getVersion() > contrib.getVersion() &&
            advertised.isCompatible(Base.getRevision()));
  }


  protected String getLatestPrettyVersion(Contribution contrib) {
    Contribution newestContrib = getAvailableContribution(contrib);
    if (newestContrib == null) {
      return null;
    }
    return newestContrib.getPrettyVersion();
  }


  protected boolean isDownloaded() {
    return listDownloaded;
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
   * TODO This needs to be called when the listing loads, and also
   *      the contribs list has been updated (for whatever reason).
   *      In addition, the caller (presumably Base) should update all
   *      Editor windows with the correct number of items available.
   * @return The number of contributions that have available updates.
   */
  public int countUpdates(Base base) {
    int count = 0;
    for (ModeContribution mc : base.getModeContribs()) {
      if (hasUpdates(mc)) {
        count++;
      }
    }
    if (base.getActiveEditor() != null) {
      for (Library lib : base.getActiveEditor().getMode().contribLibraries) {
        if (hasUpdates(lib)) {
          count++;
        }
      }
      for (Library lib : base.getActiveEditor().getMode().coreLibraries) {
        if (hasUpdates(lib)) {
          count++;
        }
      }
    }
    for (ToolContribution tc : base.getToolContribs()) {
      if (hasUpdates(tc)) {
        count++;
      }
    }
    for (ExamplesContribution ec : base.getContribExamples()) {
      if (hasUpdates(ec)) {
        count++;
      }
    }
    return count;
  }


  /** Used by JavaEditor to auto-import */
  public Map<String, Contribution> getLibrariesByImportHeader() {
    return librariesByImportHeader;
  }
}
