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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.JProgressBar;

import processing.app.*;
import processing.app.laf.PdeProgressBarUI;


/**
 * An unfortunate mix of state information about the installation
 * status of a Contribution, *as well as* the methods to handle
 * installation and update of that Contribution.
 */
class StatusDetail {
  private final Base base;
  private final StatusPanel statusPanel;

  private Contribution contrib;
  private JProgressBar progressBar;

  boolean updateInProgress;
  boolean installInProgress;
  boolean removeInProgress;


  StatusDetail(Base base, StatusPanel statusPanel) {
    this.base = base;
    this.statusPanel = statusPanel;

//    initProgressBar();
//    updateTheme();
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


  protected void setProgressBar(JProgressBar progressBar) {
    this.progressBar = progressBar;
  }


  private void installContribution(AvailableContribution info) {
    if (info.link == null) {
      statusPanel.setErrorMessage(Language.interpolate("contrib.unsupported_operating_system", info.getType()));
    } else {
      installContribution(info, info.link);
    }
  }


  private void finishInstall(boolean error) {
    statusPanel.resetProgressBar();

    if (error) {
      statusPanel.setErrorMessage(Language.text("contrib.download_error"));
    }
    installInProgress = false;
    if (updateInProgress) {
      updateInProgress = false;
    }
    // change the status icon from downloading to installed
    statusPanel.contributionTab.repaint();
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
      // TODO This is really, really gross [fry 221104]
      if (progressBar == null) {
        // This was removed in 4.x and brought back for 4.0.2 because
        // it broke the "Update All" option in the Contributions Manager.
        // https://github.com/processing/processing4/issues/567
        progressBar = new JProgressBar();
      } else {
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
      }

      ContribProgress progress = new ContribProgress(progressBar) {
        @Override
        public void finishedAction() {
          statusPanel.resetProgressBar();
          AvailableContribution ad =
            contribListing.getAvailableContribution(contrib);
          // install the new version of the Mode (or Tool)
          installContribution(ad, ad.link);
        }

        @Override
        public void cancelAction() {
          statusPanel.resetProgressBar();
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
          statusPanel.resetProgressBar();
          removeInProgress = false;
        }

        @Override
        public void cancelAction() {
          statusPanel.resetProgressBar();
          removeInProgress = false;
        }
      };
      getLocalContrib().removeContribution(base, progress, statusPanel, false);
    }
  }


  protected void updateTheme() {
    if (progressBar != null) {
      if (progressBar.getUI() instanceof PdeProgressBarUI) {
        ((PdeProgressBarUI) progressBar.getUI()).updateTheme();
      } else {
        progressBar.setUI(new PdeProgressBarUI("manager.progress"));
      }
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
