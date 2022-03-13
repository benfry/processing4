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
  private final Base base;
  private final StatusPanel statusPanel;

  static private final int BUTTON_WIDTH = Toolkit.zoom(100);

  private Contribution contrib;

  private JProgressBar progressBar;

  boolean updateInProgress;
  boolean installInProgress;
  boolean removeInProgress;


  StatusPanelDetail(Base base, StatusPanel statusPanel) {
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
      statusPanel.setErrorMessage(Language.interpolate("contrib.unsupported_operating_system", info.getType()));
    } else {
      installContribution(info, info.link);
    }
  }


  private void finishInstall(boolean error) {
    resetProgressBar();

    if (error) {
      statusPanel.setErrorMessage(Language.text("contrib.download_error"));
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
          //if (contrib.getType() == ContributionType.MODE) {  // no need
          restoreSketches();
        }

        public void cancelAction() {
          finishedAction();
        }
      };

      ContributionManager.downloadAndInstall(base, downloadUrl, ad,
                                             downloadProgress, installProgress,
                                             statusPanel);

    } catch (MalformedURLException e) {
      Messages.showWarning(Language.text("contrib.errors.install_failed"),
                           Language.text("contrib.errors.malformed_url"), e);
    }
  }


  protected void install() {
    //clearStatusMessage();
    statusPanel.clearMessage();
    installInProgress = true;
    if (contrib instanceof AvailableContribution) {
      installContribution((AvailableContribution) contrib);
      ContributionListing.getInstance().replaceContribution(contrib, contrib);
    }
  }


  // TODO Update works by first calling a remove, and then ContribProgress,
  //      of all things, calls install() in its finishedAction() method.
  //      FFS this is gross. [fry 220311]
  protected void update() {
    //clearStatusMessage();
    statusPanel.clearMessage();
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
        }

        @Override
        public void cancelAction() {
          resetProgressBar();
          //clearStatusMessage();
          statusPanel.clearMessage();
          updateInProgress = false;
          if (contrib.isDeletionFlagged()) {
            getLocalContrib().setUpdateFlag();
            getLocalContrib().setDeletionFlag(false);
            contribListing.replaceContribution(contrib, contrib);
          }
        }
      };
      getLocalContrib().removeContribution(base, progress, statusPanel, true);

    } else {
      AvailableContribution ad =
        contribListing.getAvailableContribution(contrib);
      installContribution(ad, ad.link);
    }
  }


  protected void remove() {
    //clearStatusMessage();
    statusPanel.clearMessage();
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
      getLocalContrib().removeContribution(base, progress, statusPanel, false);
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
      base.handleOpen(path);
    }
  }
}
