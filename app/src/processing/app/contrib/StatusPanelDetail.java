/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

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

import javax.swing.JProgressBar;

import processing.app.*;
import processing.app.ui.Toolkit;


/**
 * Vestigial class that was formerly a detail panel, but since 3.x
 * has only been used to track install/remove state information.
 */
class StatusPanelDetail {
  private final ListPanel listPanel;
  private final ContributionListing contribListing = ContributionListing.getInstance();

  static private final int BUTTON_WIDTH = Toolkit.zoom(100);

  private Contribution contrib;

  private JProgressBar progressBar;

  boolean updateInProgress;
  boolean installInProgress;
  boolean removeInProgress;


  StatusPanelDetail(ListPanel contributionListPanel) {
//    System.out.println("DetailPanel.<init>");
//    new Exception().printStackTrace(System.out);
    listPanel = contributionListPanel;
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
      progressBar = new JProgressBar();
      progressBar.setInheritsPopupMenu(true);
      progressBar.setStringPainted(true);
      progressBar.setFont(ManagerFrame.NORMAL_PLAIN);
      progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
      progressBar.setOpaque(false);

      resetProgressBar();

      Dimension dim =
        new Dimension(BUTTON_WIDTH,
          progressBar.getPreferredSize().height);
      progressBar.setPreferredSize(dim);
      progressBar.setMaximumSize(dim);
      progressBar.setMinimumSize(dim);
    }
    return progressBar;
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

      ContribProgressBar downloadProgress = new ContribProgressBar(progressBar) {
        public void finishedAction() { }

        public void cancelAction() {
          finishInstall(false);
        }
      };

      ContribProgressBar installProgress = new ContribProgressBar(progressBar) {
        public void finishedAction() {
          finishInstall(isError());
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


  private void resetProgressBar() {
    progressBar.setString(Language.text("contrib.progress.starting"));
    progressBar.setIndeterminate(false);
    progressBar.setValue(0);
    progressBar.setVisible(false);
  }


//  private boolean isSelected() {
//    return listPanel.getSelectedPanel() == this;
//  }


  protected void install() {
    clearStatusMessage();
    installInProgress = true;
    if (contrib instanceof AvailableContribution) {
      installContribution((AvailableContribution) contrib);
      contribListing.replaceContribution(contrib, contrib);
    }
  }


  public void update() {
    clearStatusMessage();
    updateInProgress = true;
    if (contrib.getType().requiresRestart()) {
      progressBar.setVisible(true);
      progressBar.setIndeterminate(true);

      ContribProgressBar progress = new UpdateProgressBar(progressBar);
      getLocalContrib().removeContribution(getBase(), progress, getStatusPanel());
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

      ContribProgressBar monitor = new RemoveProgressBar(progressBar);
      getLocalContrib().removeContribution(getBase(), monitor, getStatusPanel());
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class UpdateProgressBar extends ContribProgressBar {
    public UpdateProgressBar(JProgressBar progressBar) {
      super(progressBar);
    }

    public void finishedAction() {
      resetProgressBar();
      AvailableContribution ad =
        contribListing.getAvailableContribution(contrib);
      String url = ad.link;
      installContribution(ad, url);
    }

    @Override
    public void cancelAction() {
      resetProgressBar();
      clearStatusMessage();
      updateInProgress = false;
      if (contrib.isDeletionFlagged()) {
        getLocalContrib().setUpdateFlag(true);
        getLocalContrib().setDeletionFlag(false);
        contribListing.replaceContribution(contrib, contrib);
      }
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class RemoveProgressBar extends ContribProgressBar {
    public RemoveProgressBar(JProgressBar progressBar) {
      super(progressBar);
    }

    private void preAction() {
      resetProgressBar();
      removeInProgress = false;
    }

    public void finishedAction() {
      preAction();
    }

    public void cancelAction() {
      preAction();
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  // Can't be called from the constructor because the path isn't set all the
  // way down. However, Base does not change over time. More importantly,
  // though, is that the functions being called in Base are somewhat suspect
  // since they're contribution-related, and should perhaps live closer.
  private Base getBase() {
    return listPanel.contributionTab.editor.getBase();
  }


  private StatusPanel getStatusPanel() {
    return listPanel.contributionTab.statusPanel;  // TODO this is gross [fry]
  }


  private void clearStatusMessage() {
    getStatusPanel().clearMessage();
  }


  private void setErrorMessage(String message) {
    getStatusPanel().setErrorMessage(message);
  }
}
