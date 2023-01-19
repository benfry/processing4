/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - https://processing.org

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


  protected float getProgressAmount() {
    if (progressBar.isIndeterminate()) {
      return -1;
    } else {
      return (float) progressBar.getValue() / progressBar.getMaximum();
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


  static class StatusAnimator {
    Thread thread;

    StatusAnimator(ListPanel listPanel) {
      thread = new Thread(() -> {
        while (Thread.currentThread() == thread) {
          // Should be ok to call this from any Thread (EDT or otherwise)
          // https://www.oracle.com/java/technologies/painting.html#mgr
          listPanel.repaint();
//          System.out.println("calling repaint() " + System.currentTimeMillis());
//          listPanel.table.repaint();
//          listPanel.contributionTab.repaint();
          // TODO Ideally this should be only calling update on the relevant
          //      cell with model.fireTableCellUpdated(), but that requires
          //      more state housekeeping that's already broken. [fry 230115]
          try {
            Thread.sleep(100);
          } catch (InterruptedException ignored) { }
        }
      });
      thread.start();
    }

    void stop() {
      thread = null;
    }
  }


  private void installContribution(AvailableContribution ad, ListPanel listPanel) {
    try {
      URL downloadUrl = new URL(ad.link);
      progressBar.setVisible(true);

      final StatusAnimator spinner = new StatusAnimator(listPanel);

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

          // stop animating the installation
          spinner.stop();
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


  protected void installContrib(ListPanel listPanel) {
    statusPanel.clearMessage();
    installInProgress = true;
    if (contrib instanceof AvailableContribution info) {
      if (info.link == null) {
        statusPanel.setErrorMessage(Language.interpolate("contrib.missing_link", info.getType()));
      } else {
        installContribution(info, listPanel);
        // NOTE As of 4.1.1 this was being called even if the error message
        //      above was getting called. Probably harmless, especially since
        //      the error may never happen, but still… weird. [fry 230114]
        // TODO More importantly, why is this being called? Seems like this
        //      should be doing an actual replacement (i.e. what happens with
        //      the previous contrib?) And because it's usually (always?) not
        //      actually removing anything, shouldn't this be add? [fry 230114]
        ContributionListing listing = ContributionListing.getInstance();
        listing.replaceContribution(contrib, contrib);
        listing.updateTableModels();
      }
    }
  }


  // TODO Update works by first calling a remove, and then ContribProgress,
  //      of all things, calls install() in its finishedAction() method.
  //      FFS this is gross. [fry 220311]
  protected void updateContrib(ListPanel listPanel) {
    statusPanel.clearMessage();
    updateInProgress = true;

    ContributionListing contribListing = ContributionListing.getInstance();

    // TODO not really a 'restart' anymore, just requires care [fry 220312]
    if (contrib.getType().requiresRestart()) {
      progressBar.setVisible(true);
      progressBar.setIndeterminate(true);

      ContribProgress progress = new ContribProgress(progressBar) {
        @Override
        public void finishedAction() {
          statusPanel.resetProgressBar();
          AvailableContribution available =
            contribListing.findAvailableContribution(contrib);
          // install the new version of the Mode (or Tool)
          installContribution(available, listPanel);
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
            contribListing.updateTableModels();
          }
        }
      };
      getLocalContrib().removeContribution(base, progress, statusPanel, true);

    } else {
      AvailableContribution available =
        contribListing.findAvailableContribution(contrib);
      installContribution(available, listPanel);
    }
  }


  protected void removeContrib() {
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
