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

import java.awt.Component;
import java.awt.Dimension;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.JProgressBar;

import processing.app.*;
import processing.app.ui.Toolkit;


/**
 * Vestigial class that was formerly a detail panel, but since 3.x
 * has only been used to track install/remove state information.
 */
class StatusPanelDetail {
  //private final ListPanel listPanel;
  Base base;
  StatusPanel statusPanel;

  static private final int BUTTON_WIDTH = Toolkit.zoom(100);

  private Contribution contrib;

  private JProgressBar progressBar;

  boolean updateInProgress;
  boolean installInProgress;
  boolean removeInProgress;


  //StatusPanelDetail(ContributionTab contributionTab) {
  StatusPanelDetail(Base base, StatusPanel statusPanel) {
//    System.out.println("DetailPanel.<init>");
//    new Exception().printStackTrace(System.out);

//    listPanel = contributionListPanel;
    this.base = base;
    this.statusPanel = statusPanel;
  }


  protected Contribution getContrib() {
    return contrib;
  }


  private LocalContribution getLocalContrib() {
    return (LocalContribution) contrib;
  }


  protected void setContrib(Contribution contrib) {
    this.contrib = contrib;
  }


  protected JProgressBar getProgressBar() {
    if (progressBar == null) {
      initProgressBar();
    }
    return progressBar;
  }


  protected void initProgressBar() {
    progressBar = new JProgressBar();

    progressBar.setInheritsPopupMenu(true);
    progressBar.setStringPainted(true);
    progressBar.setFont(ManagerFrame.NORMAL_PLAIN);
    progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
    progressBar.setOpaque(false);

    resetProgressBar();

    final int high = progressBar.getPreferredSize().height;
    Dimension dim = new Dimension(BUTTON_WIDTH, high);
    progressBar.setPreferredSize(dim);
    progressBar.setMaximumSize(dim);
    progressBar.setMinimumSize(dim);
  }


  private void resetProgressBar() {
    // TODO is this overkill for a reset? is this really only being used
    //      when we mean to call setVisible(false)? [fry 220311]
    progressBar.setString(Language.text("contrib.progress.starting"));
    progressBar.setIndeterminate(false);
    progressBar.setValue(0);
    progressBar.setVisible(false);
  }


  private void installContribution(AvailableContribution info) {
    if (info.link == null) {
      setErrorMessage(Language.interpolate("contrib.unsupported_operating_system", info.getType()));
    } else {
      installContribution(info, info.link);
    }
  }


  private void finishInstall(boolean error) {
    resetProgressBar();

    if (error) {
      setErrorMessage(Language.text("contrib.download_error"));
    }
    installInProgress = false;
    if (updateInProgress) {
      updateInProgress = false;
    }
  }


  private void installContribution(AvailableContribution ad, String url) {
    try {
      URL downloadUrl = new URL(url);
      progressBar.setVisible(true);

      ContribProgress downloadProgress = new ContribProgress(progressBar) {
        public void finishedAction() { }

        public void cancelAction() {
          finishInstall(false);
        }
      };

      ContribProgress installProgress = new ContribProgress(progressBar) {
        public void finishedAction() {
          finishInstall(isException());

          // if it was a Mode, restore any sketches
          restoreSketches();
        }

        public void cancelAction() {
          finishedAction();
        }
      };

      ContributionManager.downloadAndInstall(getBase(), downloadUrl, ad,
                                             downloadProgress, installProgress,
                                             getStatusPanel());

    } catch (MalformedURLException e) {
      Messages.showWarning(Language.text("contrib.errors.install_failed"),
                           Language.text("contrib.errors.malformed_url"), e);
    }
  }


  protected void install() {
    clearStatusMessage();
    installInProgress = true;
    if (contrib instanceof AvailableContribution) {
      installContribution((AvailableContribution) contrib);
      ContributionListing.getInstance().replaceContribution(contrib, contrib);
    }
  }


  // TODO Update works by first calling a remove, and then ContribProgress,
  //      of all things, calls install() in its finishedAction() method.
  //      FFS this is gross. [fry 220311]
  public void update() {
    clearStatusMessage();
    updateInProgress = true;

    ContributionListing contribListing = ContributionListing.getInstance();

    // TODO not really a 'restart' anymore, just requires care [fry 220312]
    if (contrib.getType().requiresRestart()) {
      // For the special "Updates" tab in the manager, there are no progress
      // bars, so if that's what we're doing, this will create a dummy bar.
      // TODO Not a good workaround [fry 220312]
      if (progressBar == null) {
        initProgressBar();
      }
      progressBar.setVisible(true);
      progressBar.setIndeterminate(true);

      ContribProgress progress = new ContribProgress(progressBar) {
        @Override
        public void finishedAction() {
          resetProgressBar();
          AvailableContribution ad =
            contribListing.getAvailableContribution(contrib);
          // install the new version of the Mode (or Tool)
          installContribution(ad, ad.link);
          // if it was a Mode, restore any sketches
          //if (contrib.getType() == ContributionType.MODE) {
          //restoreSketches();
          //}
        }

        @Override
        public void cancelAction() {
          resetProgressBar();
          clearStatusMessage();
          updateInProgress = false;
          if (contrib.isDeletionFlagged()) {
            getLocalContrib().setUpdateFlag();
            getLocalContrib().setDeletionFlag(false);
            contribListing.replaceContribution(contrib, contrib);
          }
        }
      };
      getLocalContrib().removeContribution(getBase(), progress, getStatusPanel(), true);

    } else {
      AvailableContribution ad =
        contribListing.getAvailableContribution(contrib);
      installContribution(ad, ad.link);
    }
  }


  public void remove() {
    clearStatusMessage();
    if (contrib.isInstalled() && contrib instanceof LocalContribution) {
      removeInProgress = true;
      progressBar.setVisible(true);
      progressBar.setIndeterminate(true);

      ContribProgress progress = new ContribProgress(progressBar) {
        @Override
        public void finishedAction() {
          resetProgressBar();
          removeInProgress = false;
        }

        @Override
        public void cancelAction() {
          resetProgressBar();
          removeInProgress = false;
        }
      };
      getLocalContrib().removeContribution(getBase(), progress, getStatusPanel(), false);
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static AbstractQueue<String> restoreQueue = new ConcurrentLinkedQueue<>();


  static protected void storeSketchPath(String path) {
    restoreQueue.add(path);
  }


  protected void restoreSketches() {
    while (!restoreQueue.isEmpty()) {
      String path = restoreQueue.remove();
      getBase().handleOpen(path);
    }
  }


  /*
  static final String SAVED_COUNT = "mode.update.sketch.count";


  static protected void storeSketches(StringList sketchPathList) {
    Preferences.setInteger(SAVED_COUNT, sketchPathList.size());
    int index = 0;
    for (String path : sketchPathList) {
      Preferences.set("mode.update.sketch." + index, path);
      index++;
    }
  }


  protected void restoreSketches() {
    if (Preferences.get(SAVED_COUNT) != null) {
      int count = Preferences.getInteger(SAVED_COUNT);
      for (int i = 0; i < count; i++) {
        String key =  "mode.update.sketch." + i;
        String path = Preferences.get(key);
        getBase().handleOpen(path);  // re-open this sketch
        Preferences.unset(key);
      }
    }
  }
  */


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  // Can't be called from the constructor because the path isn't set all the
  // way down. However, Base does not change over time. More importantly,
  // though, is that the functions being called in Base are somewhat suspect
  // since they're contribution-related, and should perhaps live closer.
  private Base getBase() {
    //return listPanel.contributionTab.base;  // TODO this is gross [fry]
    return base;
  }


  private StatusPanel getStatusPanel() {
    //return listPanel.contributionTab.statusPanel;  // TODO this is also gross
    return statusPanel;
  }


  private void clearStatusMessage() {
    getStatusPanel().clearMessage();
  }


  private void setErrorMessage(String message) {
    getStatusPanel().setErrorMessage(message);
  }
}
