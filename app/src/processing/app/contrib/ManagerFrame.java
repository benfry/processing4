/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2013-15 The Processing Foundation
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

import processing.app.*;
import processing.app.ui.Editor;
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

  static final String title = "Contribution Manager";

  Base base;
  JFrame frame;
  ManagerTabs tabs;

  ContributionTab librariesTab;
  ContributionTab modesTab;
  ContributionTab toolsTab;
  ContributionTab examplesTab;
  UpdateContributionTab updatesTab;

  static Font SMALL_PLAIN;
  static Font SMALL_BOLD;
  static Font NORMAL_PLAIN;
  static Font NORMAL_BOLD;


  public ManagerFrame(Base base) {
    this.base = base;

    final int smallSize = Toolkit.zoom(12);
    final int normalSize = Toolkit.zoom(14);
    SMALL_PLAIN = Toolkit.getSansFont(smallSize, Font.PLAIN);
    SMALL_BOLD = Toolkit.getSansFont(smallSize, Font.BOLD);
    NORMAL_PLAIN = Toolkit.getSansFont(normalSize, Font.PLAIN);
    NORMAL_BOLD = Toolkit.getSansFont(normalSize, Font.BOLD);

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

//    System.out.println("ManagerFrame.<init> " + (t2-t1) + " " + (t3-t2) + " " + (t4-t3) + " " + (t5-t4) + " " + (t6-t5));
  }


  public void showFrame(ContributionType contributionType) {
    ContributionTab showTab = getTab(contributionType);
    if (frame == null) {
      makeFrame();
      // done before as downloadAndUpdateContributionListing()
      // requires the current selected tab
      tabs.setPanel(showTab);
      downloadAndUpdateContributionListing(base);
    } else {
      tabs.setPanel(showTab);
    }
    frame.setVisible(true);
    // Avoid the search box taking focus and hiding the 'search' text
    tabs.requestFocusInWindow();
  }


  private void makeFrame() {
    frame = new JFrame(title);
    frame.setMinimumSize(Toolkit.zoom(750, 500));
    tabs = new ManagerTabs();

    makeAndShowTab(false, true);

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
    Color bgColor = Theme.getColor("manager.tab.background");
    frame.getContentPane().setBackground(bgColor);

    tabs.updateTheme();
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


  // TODO move this to ContributionTab (this is handled weirdly, period) [fry]
  void downloadAndUpdateContributionListing(Base base) {
    //activeTab is required now but should be removed
    //as there is only one instance of contribListing and it should be present in this class
    final ContributionTab activeTab = getActiveTab();

    ContribProgressMonitor progress =
      new ContribProgressBar(activeTab.progressBar) {

      @Override
      public void startTask(String name, int maxValue) {
        super.startTask(name, maxValue);
        progressBar.setVisible(true);
        progressBar.setString(null);
      }

      @Override
      public void setProgress(int value) {
        super.setProgress(value);
//        int percent = 100 * value / this.max;
        progressBar.setValue(value);
      }

      @Override
      public void finishedAction() {
        progressBar.setVisible(false);
        activeTab.updateContributionListing();
        activeTab.updateCategoryChooser();

        if (error) {
          exception.printStackTrace();
          makeAndShowTab(true, false);
        } else {
          makeAndShowTab(false, false);
        }
      }
    };
    activeTab.contribListing.downloadAvailableList(base, progress);
  }


  void makeAndShowTab(boolean error, boolean loading) {
    Editor editor = base.getActiveEditor();
    librariesTab.showFrame(editor, error, loading);
    modesTab.showFrame(editor, error, loading);
    toolsTab.showFrame(editor, error, loading);
    examplesTab.showFrame(editor, error, loading);
    updatesTab.showFrame(editor, error, loading);
  }


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


  ContributionTab getActiveTab() {
    return (ContributionTab) tabs.getPanel();
  }
}
