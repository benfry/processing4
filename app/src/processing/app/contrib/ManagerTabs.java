/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - https://processing.org

  Copyright (c) 2015-22 The Processing Foundation

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package processing.app.contrib;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import processing.app.ui.Theme;
import processing.app.ui.Toolkit;


/**
 * Tab button bar for Libraries, Modes, Tools, and Updates, across the
 * top of the Contribution Manager. Tab content is in ContributionTab.
 * Most of the sizing dimensions are identical to EditorHeader.
 */
public class ManagerTabs extends Box {
  // height of this tab bar
  static final int HIGH = Toolkit.zoom(31);

  // amount of space around the entire window
  static final int BORDER = Toolkit.zoom(8);

  static final int CURVE_RADIUS = Toolkit.zoom(6);

  // amount of extra space between individual tabs
  static final int TAB_BETWEEN = Toolkit.zoom(2);
  // amount of margin on the left/right for the text on the tab
  static final int MARGIN = Toolkit.zoom(13);

  static final int UNSELECTED = 0;
  static final int SELECTED = 1;

  Color[] textColor = new Color[2];
  Color[] tabColor = new Color[2];

  List<Tab> tabList = new ArrayList<>();

  Font font;
  int fontAscent;

  Image gradient;

  JPanel cardPanel;
  CardLayout cardLayout;
  Controller controller;

  Component currentPanel;


  public ManagerTabs() {
    super(BoxLayout.Y_AXIS);

    updateTheme();

    setBorder(new EmptyBorder(BORDER, BORDER, BORDER, BORDER));

    controller = new Controller();
    add(controller);

    cardLayout = new CardLayout();
    cardPanel = new JPanel(cardLayout);
    add(cardPanel);
  }


  protected void updateTheme() {
    textColor[SELECTED] = Theme.getColor("manager.tab.text.selected.color");
    textColor[UNSELECTED] = Theme.getColor("manager.tab.text.unselected.color");
    font = Theme.getFont("manager.tab.text.font");

    tabColor[SELECTED] = Theme.getColor("manager.tab.selected.color");
    tabColor[UNSELECTED] = Theme.getColor("manager.tab.unselected.color");

    gradient = Theme.makeGradient("manager.tab", Toolkit.zoom(400), HIGH);

    repaint();
  }


  /**
   * Add a panel with a name.
   * @param comp Component that will be shown when this tab is selected
   * @param name Title to appear on the tab itself
   */
  public void addPanel(Component comp, String name) {
    if (tabList.isEmpty()) {
      currentPanel = comp;
    }
    tabList.add(new Tab(comp, name));
    cardPanel.add(name, comp);
  }


  public void setPanel(Component comp) {
    for (Tab tab : tabList) {
      if (tab.comp == comp) {
        currentPanel = comp;
        cardLayout.show(cardPanel, tab.name);
        repaint();
      }
    }
  }


  public Component getPanel() {
    return currentPanel;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class Controller extends JComponent {

    Controller() {
      addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          int x = e.getX();
          for (Tab tab : tabList) {
            if (tab.contains(x)) {
              //cardLayout.show(cardPanel, tab.name);
              setPanel(tab.comp);
              repaint();
            }
          }
        }
      });
    }


    public void paintComponent(Graphics g) {
      if (g == null) return;

      g.setFont(font);  // need to set this each time through
      if (fontAscent == 0) {
        fontAscent = (int) Toolkit.getAscent(g);
      }

      Graphics2D g2 = Toolkit.prepareGraphics(g);

      g.drawImage(gradient, 0, 0, getWidth(), getHeight(), this);

      // reset all tab positions
      for (Tab tab : tabList) {
        tab.textWidth = (int)
          font.getStringBounds(tab.name, g2.getFontRenderContext()).getWidth();
      }
      placeTabs();
      // now actually draw the tabs
      drawTabs(g2);
    }


    private void placeTabs() {
      int x = 0;
      for (Tab tab : tabList) {
        tab.left = x;
        x += MARGIN;
        x += tab.textWidth + MARGIN;
        tab.right = x;
        x += TAB_BETWEEN;
      }
      // Align the final tab (the "updates") to the right-hand side
      Tab lastTab = tabList.get(tabList.size() - 1);
      int offset = getWidth() - lastTab.right;
      lastTab.left += offset;
      lastTab.right += offset;
    }


    private void drawTabs(Graphics2D g) {
      for (Tab tab: tabList) {
        tab.draw(g);
      }
    }


    public Dimension getPreferredSize() {
      return new Dimension(300, HIGH);
    }


    public Dimension getMinimumSize() {
      return getPreferredSize();
    }


    public Dimension getMaximumSize() {
      return new Dimension(super.getMaximumSize().width, HIGH);
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class Tab {
    String name;
    Component comp;
    boolean notification;

    int left;
    int right;
    int textWidth;

    Tab(Component comp, String name) {
      this.comp = comp;
      this.name = name;
    }

    boolean contains(int x) {
      return x >= left && x <= right;
    }

    boolean isCurrent() {
      return comp.isVisible();
    }

    boolean hasLeftNotch() {
      return (tabList.get(0) == this ||
              tabList.get(tabList.size() - 1) == this);
    }

    boolean hasRightNotch() {
      return (tabList.get(tabList.size() - 1) == this ||
              tabList.get(tabList.size() - 2) == this);

    }

    int getTextLeft() {
      return left + ((right - left) - textWidth) / 2;
    }

    void draw(Graphics g) {
      int state = isCurrent() ? SELECTED : UNSELECTED;
      g.setColor(tabColor[state]);

      Graphics2D g2 = (Graphics2D) g;
      g2.fill(Toolkit.createRoundRect(left, 0,
                                      right, HIGH,
                                      hasLeftNotch() ? CURVE_RADIUS : 0,
                                      hasRightNotch() ? CURVE_RADIUS : 0,
                                      0, 0));

      int textLeft = getTextLeft();
      if (notification && state == UNSELECTED) {
        g.setColor(textColor[SELECTED]);
      } else {
        g.setColor(textColor[state]);
      }
      int baseline = (HIGH + fontAscent) / 2;
      g.drawString(name, textLeft, baseline);
    }
  }
}
