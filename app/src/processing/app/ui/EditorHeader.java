/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-19 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

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

package processing.app.ui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.util.Arrays;

import javax.swing.*;

import processing.app.*;


/**
 * Sketch tabs at the top of the editor window.
 */
public class EditorHeader extends JComponent {
  // height of this tab bar
  static final int HIGH = Toolkit.zoom(31);

  static final int ARROW_TAB_WIDTH = Toolkit.zoom(18);
  static final int ARROW_TOP = Toolkit.zoom(12);
  static final int ARROW_BOTTOM = Toolkit.zoom(18);
  static final int ARROW_WIDTH = Toolkit.zoom(6);

  static final int CURVE_RADIUS = Toolkit.zoom(6);

  static final int TAB_TOP = 0;
  // amount of extra space between individual tabs
  static final int TAB_BETWEEN = Toolkit.zoom(2);
  // space between tab and editor
  static final int TAB_BELOW = TAB_BETWEEN;
  // bottom position as determined by TAB_BELOW gap
  static final int TAB_BOTTOM = HIGH - TAB_BELOW;
  // amount of margin on the left/right for the text on the tab
  static final int TEXT_MARGIN = Toolkit.zoom(13);
  // width of the tab when no text visible
  // (total tab width will be this plus TEXT_MARGIN*2)
  static final int NO_TEXT_WIDTH = Toolkit.zoom(16);

  Color[] textColor = new Color[2];
  Color[] tabColor = new Color[2];
  Color modifiedColor;
  Color arrowColor;

  Editor editor;

  Tab[] tabs = new Tab[0];
  Tab[] visitOrder;

  Font font;
  int fontAscent;

  JMenu menu;
  JPopupMenu popup;

  int menuLeft;
  int menuRight;

  static final int UNSELECTED = 0;
  static final int SELECTED = 1;

  String lastNoticeName;

  Image gradient;


  public EditorHeader(Editor eddie) {
    this.editor = eddie;

    updateTheme();

    addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();

        if ((x > menuLeft) && (x < menuRight)) {
          popup.show(EditorHeader.this, x, y);
        } else {
          Sketch sketch = editor.getSketch();
          for (Tab tab : tabs) {
            if (tab.contains(x)) {
              sketch.setCurrentCode(tab.index);
              repaint();
            }
          }
        }
      }

      public void mouseExited(MouseEvent e) {
        // only clear if it's been set
        if (lastNoticeName != null) {
          // only clear if it's the same as what we set it to
          editor.clearNotice(lastNoticeName);
          lastNoticeName = null;
        }
      }
    });

    addMouseMotionListener(new MouseMotionAdapter() {
        public void mouseMoved(MouseEvent e) {
      int x = e.getX();
      for (Tab tab : tabs) {
        if (tab.contains(x) && !tab.textVisible) {
          lastNoticeName = editor.getSketch().getCode(tab.index).getPrettyName();
          editor.statusNotice(lastNoticeName);
        }
      }
      }
    });
  }


  public void updateTheme() {
    textColor[SELECTED] = Theme.getColor("header.text.selected.color");
    textColor[UNSELECTED] = Theme.getColor("header.text.unselected.color");
    font = Theme.getFont("header.text.font");

    tabColor[SELECTED] = Theme.getColor("header.tab.selected.color");
    tabColor[UNSELECTED] = Theme.getColor("header.tab.unselected.color");

    arrowColor = Theme.getColor("header.tab.arrow.color");
    //modifiedColor = mode.getColor("editor.selection.color");
    modifiedColor = Theme.getColor("header.tab.modified.color");

    gradient = Theme.makeGradient("header", 400, HIGH);
  }


  public void paintComponent(Graphics g) {
    if (g == null) return;
    Sketch sketch = editor.getSketch();
    if (sketch == null) return;  // is this even possible?

    g.setFont(font);  // need to set this each time through
    if (fontAscent == 0) {
      fontAscent = (int) Toolkit.getAscent(g);
    }

    Graphics2D g2 = Toolkit.prepareGraphics(g, false);

    /*
    Graphics2D g2 = (Graphics2D) g;

    if (!Toolkit.isRetina()) {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                          RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                          RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
//      g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
//                          RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
    */

    g.drawImage(gradient, 0, 0, getWidth(), getHeight(), this);

    if (tabs.length != sketch.getCodeCount()) {
      tabs = new Tab[sketch.getCodeCount()];
      for (int i = 0; i < tabs.length; i++) {
        tabs[i] = new Tab(i);
      }
      visitOrder = new Tab[sketch.getCodeCount() - 1];
    }

    int leftover = TAB_BETWEEN + ARROW_TAB_WIDTH;
    int tabMax = getWidth() - leftover;

    // reset all tab positions
    for (Tab tab : tabs) {
      SketchCode code = sketch.getCode(tab.index);
      tab.textVisible = true;
      tab.lastVisited = code.lastVisited();

      tab.text = code.getFileName();
      // hide extensions for .pde files
      if (editor.getMode().hideExtension(code.getExtension())) {
        tab.text = code.getPrettyName();
        if (Preferences.getBoolean("sketch.name.replace_underscore")) {
          tab.text = tab.text.replace('_', ' ');
        }
      }

      tab.textWidth = (int)
        font.getStringBounds(tab.text, g2.getFontRenderContext()).getWidth();
    }
    // try to make everything fit
    if (!placeTabs(Editor.LEFT_GUTTER, tabMax, null)) {
      // always show the tab with the sketch's name
      int index = 0;
      // stock the array backwards so the rightmost tabs are closed by default
      for (int i = tabs.length - 1; i > 0; --i) {
        visitOrder[index++] = tabs[i];
      }
      Arrays.sort(visitOrder);  // sort on when visited

      // Keep shrinking the tabs one-by-one until things fit properly
      for (Tab tab : visitOrder) {
        tabs[tab.index].textVisible = false;
        if (placeTabs(Editor.LEFT_GUTTER, tabMax, null)) {
          break;
        }
      }
    }

    // now actually draw the tabs
    if (!placeTabs(Editor.LEFT_GUTTER, tabMax - ARROW_TAB_WIDTH, g2)){
      // draw the dropdown menu target at the right of the window
      menuRight = tabMax;
      menuLeft = menuRight - ARROW_TAB_WIDTH;
    } else {
      // draw the dropdown menu target next to the tabs
      menuLeft = tabs[tabs.length - 1].right + TAB_BETWEEN;
      menuRight = menuLeft + ARROW_TAB_WIDTH;
    }

    /*
    // draw the two pixel line that extends left/right below the tabs
    g.setColor(tabColor[SELECTED]);
    // can't be done with lines, b/c retina leaves tiny hairlines
    g.fillRect(Editor.LEFT_GUTTER, TAB_BOTTOM,
               editor.getTextArea().getWidth() - Editor.LEFT_GUTTER,
               Toolkit.zoom(2));
     */

    // draw the tab for the menu
    g.setColor(tabColor[UNSELECTED]);
    drawTab(g, menuLeft, menuRight, false, true, false);

    // draw the arrow on the menu tab
    g.setColor(arrowColor);
    GeneralPath trianglePath = new GeneralPath();
    float x1 = menuLeft + (ARROW_TAB_WIDTH - ARROW_WIDTH) / 2f;
    float x2 = menuLeft + (ARROW_TAB_WIDTH + ARROW_WIDTH) / 2f;
    trianglePath.moveTo(x1, ARROW_TOP);
    trianglePath.lineTo(x2, ARROW_TOP);
    trianglePath.lineTo((x1 + x2) / 2, ARROW_BOTTOM);
    trianglePath.closePath();
    g2.fill(trianglePath);
  }


  private boolean placeTabs(int left, int right, Graphics2D g) {
    Sketch sketch = editor.getSketch();
    int x = left;

    for (int i = 0; i < sketch.getCodeCount(); i++) {
      SketchCode code = sketch.getCode(i);
      Tab tab = tabs[i];

      int state = (code == sketch.getCurrentCode()) ? SELECTED : UNSELECTED;
      tab.left = x;
      x += TEXT_MARGIN;

      int drawWidth = tab.textVisible ? tab.textWidth : NO_TEXT_WIDTH;
      x += drawWidth + TEXT_MARGIN;
      tab.right = x;

      if (g != null && tab.right < right) {
        g.setColor(tabColor[state]);
        drawTab(g, tab.left, tab.right, i == 0, false, state == SELECTED);

        if (tab.textVisible) {
          int textLeft = tab.left + ((tab.right - tab.left) - tab.textWidth) / 2;
          g.setColor(textColor[state]);
          int tabHeight = TAB_BOTTOM - TAB_TOP;
          int baseline = TAB_TOP + (tabHeight + fontAscent) / 2 + 1;
          g.drawString(tab.text, textLeft, baseline);
        }

        if (code.isModified()) {
          g.setColor(modifiedColor);
          int barTop = TAB_TOP;
          int barWidth = Toolkit.zoom(1);
          int barHeight = (TAB_BOTTOM - barTop) + ((state == SELECTED) ? TAB_BELOW : 0);
          int barLeft = tab.right - barWidth;
          g.fillRect(barLeft, barTop,
                  barWidth,
                  barHeight);
        }
      }
      x += TAB_BETWEEN;
    }
    return x <= right;
  }


  private void drawTab(Graphics g, int left, int right,
                       boolean leftNotch, boolean rightNotch,
                       boolean selected) {
    Graphics2D g2 = (Graphics2D) g;
    final int bottom = TAB_BOTTOM + (selected ? TAB_BELOW : 0);
    g2.fill(Toolkit.createRoundRect(left, TAB_TOP,
                                    right, bottom,
                                    leftNotch ? CURVE_RADIUS : 0,
                                    rightNotch ? CURVE_RADIUS : 0,
                                    0, 0));
  }


  /**
   * Called when a new sketch is opened.
   */
  public void rebuild() {
    //System.out.println("rebuilding editor header");
    rebuildMenu();
    repaint();
  }


  public void rebuildMenu() {
    //System.out.println("rebuilding");
    if (menu != null) {
      menu.removeAll();

    } else {
      menu = new JMenu();
      popup = menu.getPopupMenu();
      add(popup);
      popup.setLightWeightPopupEnabled(true);

      /*
      popup.addPopupMenuListener(new PopupMenuListener() {
          public void popupMenuCanceled(PopupMenuEvent e) {
            // on redraw, the isVisible() will get checked.
            // actually, a repaint may be fired anyway, so this
            // may be redundant.
            repaint();
          }
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) { }
        });
      */
    }
    JMenuItem item;
    final JRootPane rootPane = editor.getRootPane();
    InputMap inputMap =
      rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    ActionMap actionMap = rootPane.getActionMap();

    Action action;
    String mapKey;
    KeyStroke keyStroke;

    item = Toolkit.newJMenuItemShift(Language.text("editor.header.new_tab"), KeyEvent.VK_N);
    action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        editor.getSketch().handleNewCode();
      }
    };
    mapKey = "editor.header.new_tab";
    keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.SHORTCUT_SHIFT_KEY_MASK);
    inputMap.put(keyStroke, mapKey);
    actionMap.put(mapKey, action);
    item.addActionListener(action);
    menu.add(item);

    item = new JMenuItem(Language.text("editor.header.rename"));
    action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        editor.getSketch().handleRenameCode();
      }
    };
    item.addActionListener(action);
    menu.add(item);

    item = Toolkit.newJMenuItemShift(Language.text("editor.header.delete"), KeyEvent.VK_D);
    action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Sketch sketch = editor.getSketch();
        if (!Platform.isMacOS() &&  // ok on OS X
            editor.base.getEditors().size() == 1 &&  // mmm! accessor
            sketch.getCurrentCodeIndex() == 0) {
            Messages.showWarning(Language.text("editor.header.delete.warning.title"),
                                 Language.text("editor.header.delete.warning.text"));
        } else {
          editor.getSketch().handleDeleteCode();
        }
      }
    };
    mapKey = "editor.header.delete";
    keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_D, Toolkit.SHORTCUT_SHIFT_KEY_MASK);
    inputMap.put(keyStroke, mapKey);
    actionMap.put(mapKey, action);
    item.addActionListener(action);
    menu.add(item);

    menu.addSeparator();

    //  KeyEvent.VK_LEFT and VK_RIGHT will make Windows beep

    mapKey = "editor.header.previous_tab";
    item = Toolkit.newJMenuItemExt(mapKey);
    action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        editor.getSketch().handlePrevCode();
      }
    };
    keyStroke = item.getAccelerator();
    inputMap.put(keyStroke, mapKey);
    actionMap.put(mapKey, action);
    item.addActionListener(action);
    menu.add(item);

    mapKey = "editor.header.next_tab";
    item = Toolkit.newJMenuItemExt(mapKey);
    action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        editor.getSketch().handleNextCode();
      }
    };
    keyStroke = item.getAccelerator();
    inputMap.put(keyStroke, mapKey);
    actionMap.put(mapKey, action);
    item.addActionListener(action);
    menu.add(item);

    Sketch sketch = editor.getSketch();
    if (sketch != null) {
      menu.addSeparator();

      ActionListener jumpListener =
        e -> editor.getSketch().setCurrentCode(e.getActionCommand());
      for (SketchCode code : sketch.getCode()) {
        item = new JMenuItem(code.getPrettyName());
        item.addActionListener(jumpListener);
        menu.add(item);
      }
    }

    Toolkit.setMenuMnemonics(menu);
  }


  /*
  public void deselectMenu() {
    repaint();
  }
  */


  public Dimension getPreferredSize() {
    return new Dimension(300, HIGH);
  }


  public Dimension getMinimumSize() {
    return getPreferredSize();
  }


  public Dimension getMaximumSize() {
    return new Dimension(super.getMaximumSize().width, HIGH);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static class Tab implements Comparable<Tab> {
    int index;
    int left;
    int right;
    String text;
    int textWidth;
    boolean textVisible;
    long lastVisited;

    Tab(int index) {
      this.index = index;
    }

    boolean contains(int x) {
      return x >= left && x <= right;
    }

    // sort by the last time visited
    public int compareTo(Tab other) {
      // do this here to deal with situation where both are 0
      if (lastVisited == other.lastVisited) {
        return 0;
      }
      if (lastVisited == 0) {
        return -1;
      }
      if (other.lastVisited == 0) {
        return 1;
      }
      return (int) (lastVisited - other.lastVisited);
    }
  }
}
