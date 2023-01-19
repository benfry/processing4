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

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import processing.app.Base;
import processing.app.Language;
import processing.app.ui.Theme;
import processing.app.ui.Toolkit;


/**
 * This class is the main Contribution Manager Dialog.
 * It contains all the contributions tab and the update tab.
 */
public class ManagerFrame {
  static final String ANY_CATEGORY = Language.text("contrib.all");

  static final int AUTHOR_WIDTH = Toolkit.zoom(240);
  static final int STATUS_WIDTH = Toolkit.zoom(66);
  static final int VERSION_WIDTH = Toolkit.zoom(66);

  static final String title = "Contribution Manager";

  Base base;
  JFrame frame;
  ManagerTabs tabs;

  ContributionTab librariesTab;
  ContributionTab modesTab;
  ContributionTab toolsTab;
  ContributionTab examplesTab;
  UpdateContributionTab updatesTab;

  ContributionTab[] tabList;


  public ManagerFrame(Base base) {
    this.base = base;

    // TODO Optimize these inits... unfortunately it needs to run on the EDT,
    //      and Swing is a piece of s*t, so it's gonna be slow with lots of contribs.
    //      In particular, load everything and then fire the update events.
    //      Also, don't pull all the colors over and over again.
//    long t1 = System.currentTimeMillis();
    librariesTab = new ContributionTab(this, ContributionType.LIBRARY);
//    long t2 = System.currentTimeMillis();
    modesTab = new ContributionTab(this, ContributionType.MODE);
//    long t3 = System.currentTimeMillis();
    toolsTab = new ContributionTab(this, ContributionType.TOOL);
//    long t4 = System.currentTimeMillis();
    examplesTab = new ContributionTab(this, ContributionType.EXAMPLES);
//    long t5 = System.currentTimeMillis();
    updatesTab = new UpdateContributionTab(this);
//    long t6 = System.currentTimeMillis();

    tabList = new ContributionTab[] {
      librariesTab, modesTab, toolsTab, examplesTab, updatesTab
    };

//    System.out.println("ManagerFrame.<init> " + (t2-t1) + " " + (t3-t2) + " " + (t4-t3) + " " + (t5-t4) + " " + (t6-t5));
  }


  public void showFrame(ContributionType contributionType) {
    ContributionTab showTab = getTab(contributionType);
    if (frame == null) {
      // Build the Contribution Manager UI on first use.
      makeFrame();

      // Update the list of contribs with what's installed locally.
      ContributionListing.updateInstalled(base.getInstalledContribs());

      // Set the list of categories on first use. If a new category is added
      // from an already-installed contrib, or in the downloaded contribs list,
      // it won't be included. Yech! But practically speaking… [fry 230114]
      getTab(ContributionType.LIBRARY).updateCategoryChooser();

      // TODO If it's the updates tab, need to reset the list. This is papering
      //      over a concurrency bug with adding/removing contribs during the
      //      initial load/startup, but probably always relevant. [fry 230115]
//      if (showTab.contribType == null) {
      for (ContributionTab tab : tabList) {
        //tab.listPanel.model.fireTableDataChanged();
      }
    }
    tabs.setPanel(showTab);
    frame.setVisible(true);
    // Avoid the search box taking focus and hiding the 'search' text
    tabs.requestFocusInWindow();
  }


  private void makeFrame() {
    frame = new JFrame(title);
    frame.setMinimumSize(Toolkit.zoom(750, 500));
    tabs = new ManagerTabs();

    //rebuildTabLayouts(false, true);
//    for (ContributionTab tab : tabList) {
//      tab.rebuildLayout();
//    }

    tabs.addPanel(librariesTab, "Libraries");
    tabs.addPanel(modesTab, "Modes");
    tabs.addPanel(toolsTab, "Tools");
    tabs.addPanel(examplesTab, "Examples");
    tabs.addPanel(updatesTab, "Updates");

    frame.setResizable(true);

    frame.getContentPane().add(tabs);
    updateTheme();

    frame.validate();
    frame.repaint();

    Toolkit.setIcon(frame);
    registerDisposeListeners();

    frame.pack();
    frame.setLocationRelativeTo(null);
  }


  protected void updateTheme() {
    // don't update if the Frame doesn't actually exist yet
    // https://github.com/processing/processing4/issues/476
    if (frame != null) {
      Color bgColor = Theme.getColor("manager.tab.background");
      frame.getContentPane().setBackground(bgColor);

      tabs.updateTheme();

      for (ContributionTab tab : tabList) {
        tab.updateTheme();
      }
    }
  }


  /**
   * Close the window after an OK or Cancel.
   */
  protected void disposeFrame() {
    frame.dispose();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  private void registerDisposeListeners() {
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        disposeFrame();
      }
    });
    // handle window closing commands for ctrl/cmd-W or hitting ESC.
    Toolkit.registerWindowCloseKeys(frame.getRootPane(), actionEvent -> disposeFrame());

    frame.getContentPane().addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
      //System.out.println(e);
      KeyStroke wc = Toolkit.WINDOW_CLOSE_KEYSTROKE;
      if ((e.getKeyCode() == KeyEvent.VK_ESCAPE)
        || (KeyStroke.getKeyStrokeForEvent(e).equals(wc))) {
        disposeFrame();
      }
      }
    });
  }


  /*
  // TODO move this to ContributionTab (this is handled weirdly, period) [fry]
  //void downloadAndUpdateContributionListing(Base base) {
  void downloadAndUpdateContributionListing() {
    //activeTab is required now but should be removed
    //as there is only one instance of contribListing, and it should be present in this class
//    final ContributionTab activeTab = getActiveTab();
    ContributionTab activeTab = (ContributionTab) tabs.getPanel();

//        activeTab.updateContributionListing();
    ContributionListing.updateInstalled(base);
    activeTab.updateCategoryChooser();

    //rebuildTabLayouts(false, false);
    //activeTab.rebuildLayout(false, false);
    activeTab.rebuildLayout();
  }
  */


  /*
  protected void rebuildTabLayouts(boolean error, boolean loading) {
    for (ContributionTab tab : tabList) {
      tab.rebuildLayout(error, loading);
    }
  }
  */


  protected ContributionTab getTab(ContributionType contributionType) {
    if (contributionType == ContributionType.LIBRARY) {
      return librariesTab;
    } else if (contributionType == ContributionType.MODE) {
      return modesTab;
    } else if (contributionType == ContributionType.TOOL) {
      return toolsTab;
    } else if (contributionType == ContributionType.EXAMPLES) {
      return examplesTab;
    }
    return updatesTab;
  }


//  ContributionTab getActiveTab() {
//    return (ContributionTab) tabs.getPanel();
//  }
}
