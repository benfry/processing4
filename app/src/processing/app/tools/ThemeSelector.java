/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  ThemeSelector - easy selection of alternate color systems
  Part of the Processing project - https://processing.org

  Copyright (c) 2022 The Processing Foundation

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

package processing.app.tools;

import processing.app.*;
import processing.app.laf.PdeComboBoxUI;
import processing.app.ui.Editor;
import processing.app.ui.Theme;
import processing.app.ui.Toolkit;
import processing.core.PApplet;
import processing.data.StringDict;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ThemeSelector extends JFrame implements Tool {
  static final String HOWTO_URL =
    "https://github.com/processing/processing4/wiki/Themes";
  static final String ORDER_FILENAME = "order.txt";

  String miniSvgXml;

  List<ThemeSet> sets;

  String defaultTheme;

  File sketchbookFile;
  ThemeSet currentSet;
  int currentIndex;

  JComboBox<String> setSelector;
  ColorfulPanel selector;

  JLabel howtoLabel;
  JLabel reloadTheme;

  Base base;


  public String getMenuTitle() {
    return "Theme Selector";
  }


  public void init(Base base) {
    this.base = base;

    try {
      File themeFolder = Theme.getThemeFolder();
      File[] setFolders = themeFolder.listFiles(file -> {
        if (file.isDirectory()) {
          File orderFile = new File(file, ORDER_FILENAME);
          return orderFile.exists();
        }
        return false;
      });
      if (setFolders == null) {
        Messages.showWarning("Could not load themes",
          "The themes directory could not be read.\n" +
            "Please reinstall Processing.");
        return;
      }

      File miniFile = new File(themeFolder, "mini.svg");
      miniSvgXml = Util.loadFile(miniFile);

      sets = new ArrayList<>();
      for (File folder : setFolders) {
        sets.add(new ThemeSet(folder));
      }
      currentSet = sets.get(0);
      defaultTheme = getDefaultTheme();

    } catch (IOException e) {
      e.printStackTrace();
    }

    Container pane = getContentPane();

    Box axis = Box.createVerticalBox();

    String[] setNames = new String[sets.size()];
    for (int i = 0; i < sets.size(); i++) {
      setNames[i] = sets.get(i).name;
    }
    setSelector = new JComboBox<>(setNames);
    setSelector.addItemListener(e -> {
      currentSet = sets.get(setSelector.getSelectedIndex());
      updateCurrentIndex();
      repaint();
    });
    addRow(axis, setSelector);

    axis.add(Box.createVerticalStrut(13));

    axis.add(selector = new ColorfulPanel());  // flush with sides

    axis.add(Box.createVerticalStrut(13));

    addRow(axis, howtoLabel = new JLabel());
    howtoLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        Platform.openURL(HOWTO_URL);
      }

      public void mouseEntered(MouseEvent e) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        //clickable.setForeground(Theme.getColor("laf.accent.color"));
      }

      // Set the text back to black when the mouse is outside
      public void mouseExited(MouseEvent e) {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        // steal the color from a component that doesn't change
        // (so that it works after updateTheme() has been called)
        //clickable.setForeground(sketchbookLocationLabel.getForeground());
      }
    });

    axis.add(Box.createVerticalStrut(6));

    addRow(axis, reloadTheme = new JLabel());
    reloadTheme.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        if (!sketchbookFile.exists()) {
          // When first called, just create the theme.txt file
          Theme.save();
          updateTheme();  // changes the JLabel for this fella

        } else {
          reloadTheme();

          // May be too subtle, but popping up a dialog box is too much.
          // Feels like something needed because theme.txt edits may be subtle.
          Editor activeEditor = base.getActiveEditor();
          if (activeEditor != null) {
            activeEditor.statusNotice("Finished updating theme.");
          }
        }
      }

      public void mouseEntered(MouseEvent e) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      public void mouseExited(MouseEvent e) {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    });

    axis.setBorder(new EmptyBorder(20, 20, 20, 20));
    pane.add(axis);

    Toolkit.registerWindowCloseKeys(getRootPane(), e -> setVisible(false));
    setTitle(getMenuTitle());
    setResizable(false);
    updateTheme();  // important before pack()
    pack();
    setLocationRelativeTo(null);
  }

  static final int ROW_H_GAP = 7;
  static final int ROW_V_GAP = 0;

  static private void addRow(Container axis, Component... components) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, ROW_H_GAP, ROW_V_GAP));
    row.setOpaque(false);
    for (Component comp : components) {
      row.add(comp);
    }
    axis.add(row);
  }


  public void run() {
    // location for theme.txt in the sketchbook folder
    // (doing this in run() in case the sketchbook location has changed)
    sketchbookFile = Theme.getSketchbookFile();

    updateTheme();

    // figure out if the current theme in sketchbook is a known one
    //currentIndex = getCurrentIndex();
    updateCurrentIndex();

//    invalidate();
//    pack();
    setVisible(true);
  }


  private void updateTheme() {
    getContentPane().setBackground(Theme.getColor("theme_selector.window.color"));

    if (setSelector.getUI() instanceof PdeComboBoxUI) {
      ((PdeComboBoxUI) setSelector.getUI()).updateTheme();
    } else {
      setSelector.setUI(new PdeComboBoxUI("theme_selector.combo_box"));
    }

//    String textColor = Theme.get("theme_selector.text.color");
//    String linkColor = Theme.get("theme_selector.link.color");

    String labelStyle =
      "body { " +
//    "  margin: 0; " +
//    "  padding: 0;" +
//    "  font-family: " + detailFont.getName() + ", sans-serif;" +
//    "  font-size: " + detailFont.getSize() + "px;" +
//    "  font-size: 18px; " +
        "  color: " + Theme.get("theme_selector.text.color") + ";" +
        "} " +
        "a { " +
        "  color: " + Theme.get("theme_selector.link.color") + ";" +
        "  text-decoration: none;" +
        "}";

    String prefix = "<html><head><style type='text/css'>" + labelStyle + "</style></head><body>";
//    System.out.println(prefix);

//    howtoLabel.setText("<html><a href=\"\" color=\"" + linkColor +
//      "\">Read</a> <font color=\"" + textColor + "\">about how to create your own themes.");
//    howtoLabel.setText(prefix + "&rarr; <a>Read</a> about how to create your own themes");
//    howtoLabel.setText("&rarr; <a href=\"\">Read</a> about how to create your own themes", labelStyle);
    howtoLabel.setText(prefix + "&rarr; <a href=\"\">Read</a> about how to create your own themes");

    if (Theme.getSketchbookFile().exists()) {
//      reloadTheme.setText("<html>&rarr; <a href=\"\" color=\"" + linkColor +
//        "\">Reload</a> <font color=\"" + textColor + "\">theme.txt to update the current theme.");
//      reloadTheme.setText(prefix + "&rarr; <a>Reload</a> theme.txt to update the current theme");
//      reloadTheme.setText("&rarr; <a href=\"\">Reload</a> theme.txt to update the current theme", labelStyle);
      reloadTheme.setText(prefix + "&rarr; <a href=\"\">Reload</a> theme.txt to update the current theme");

    } else {
//      reloadTheme.setText("<html><a href=\"\" color=\"" + linkColor +
//        "\">Save</a> <font color=\"" + textColor + "\">theme.txt to sketchbook for editing.");
//      reloadTheme.setText(prefix + "&rarr; <a>Save</a> theme.txt to sketchbook for editing.");
      reloadTheme.setText(prefix + "&rarr; <a href=\"\">Save</a> theme.txt to sketchbook for editing");
    }
    //((HTMLDocument) howtoLabel.getDocument()).getStyleSheet().addRule(detailStyle);
  }


  private String getCurrentTheme() {
    if (sketchbookFile.exists()) {
      return Util.loadFile(sketchbookFile);
    }
    return defaultTheme;
  }


  private String getDefaultTheme() {
    // should be entry 0 of set 0, but let's not make that assumption
    try {
      return Util.loadFile(Base.getLibFile("theme.txt"));
    } catch (IOException e) {
      e.printStackTrace();
    }
    // do this as a fallback
    return sets.get(0).themes[0];
  }


  /**
   * @return true if sketchbook/theme.txt does not match a built-in theme.
   */
  private boolean userModifiedTheme() {
    if (!sketchbookFile.exists()) {
      return false;
    }
    String currentTheme = getCurrentTheme();
    for (ThemeSet set : sets) {
      if (set.getIndex(currentTheme) != -1) {
        return false;  // this is a built-in theme
      }
    }
    return true;
  }


  private void setCurrentIndex(int index) {
    currentIndex = index;

    // If there is a theme.txt file in the sketchbook folder,
    // archive it and move out of the way.
    if (userModifiedTheme()) {
      boolean success = Theme.archiveCurrent();
      if (!success) {
        Messages.showWarning("Could not back up theme",
          "Could not save a backup of theme.txt in your sketchbook folder.\n" +
          "Rename it manually and try setting the theme again.");
        return;
      }
    }

    // required to update the color of the dropdown menu
    setSelector.repaint();

    // No longer saving a new theme.txt when making a selection, just setting a
    // preference so that subsequent Processing updates load new theme changes.
    //Util.saveFile(currentSet.get(index), sketchbookFile);
    Preferences.set("theme", currentSet.getPath(index));
    // On some machines, the theme wasn't getting saved; try an explicit save
    // https://github.com/processing/processing4/issues/565
    Preferences.save();
    reloadTheme();
  }


  /**
   * Called when user clicks the 'reload' button, or when the theme
   * is changed by clicking on a built-in selection.
   */
  private void reloadTheme() {
    Theme.reload();
    base.updateTheme();
    updateTheme();
  }


  private void updateCurrentIndex() {
    String currentTheme = getCurrentTheme();
    currentIndex = currentSet.getIndex(currentTheme);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class ThemeSet {
    final String name;
    private int count;
    private String[] paths;
    private String[] themes;
    private Image[] images;
    private Map<String, Integer> indices;

    ThemeSet(File dir) {
      name = dir.getName();

      File orderFile = new File(dir, ORDER_FILENAME);
      String[] lines = PApplet.loadStrings(orderFile);
      if (lines != null) {
        count = Math.min(16, lines.length);
        if (count < lines.length) {
          System.err.println("Only using the first 16 themes inside " + orderFile);
        }
        paths = new String[count];
        themes = new String[count];
        images = new Image[count];
        indices = new HashMap<>(count);

        // don't load more than 16 entries
        for (int i = 0; i < count; i++) {
          String filename = lines[i] + ".txt";
          File file = new File(dir, filename);
          String theme = Util.loadFile(file);
          indices.put(theme, i);
          paths[i] = name + "/" + filename;
          themes[i] = theme;
          images[i] = renderImage(file.getName(), theme);
        }
      }
    }

    /**
     * Render the Mini SVG using the theme colors.
     * @param filename only used for debug messages
     * @param theme all the lines of the theme file, joined
     * @return mini image of the PDE with theme colors applied
     */
    private Image renderImage(String filename, String theme) {
      // parse the txt file to get entries for swapping
      StringDict entries =
        Util.readSettings(filename, PApplet.split(theme, '\n'), true);
      //entries.print();

      StringDict replacements = new StringDict(new String[][] {
        { "#000000", entries.get("console.color") },
        { "#111111", entries.get("editor.gutter.highlight.color") },
        { "#222222", entries.get("footer.gradient.top") },
        { "#444444", entries.get("mode.background.color") },
        { "#555555", entries.get("toolbar.button.enabled.glyph") },
        { "#666666", entries.get("editor.gradient.top") },
        { "#777777", entries.get("editor.gradient.bottom") },
        { "#888888", entries.get("toolbar.button.selected.field") },
        { "#CCCCCC", entries.get("toolbar.button.enabled.field") },
        { "#DDDDDD", entries.get("editor.line.highlight.color") },
        { "#EEEEEE", entries.get("toolbar.button.selected.glyph") },
        { "#FFFFFF", entries.get("editor.bgcolor") }
      });
      //replacements.print();
      //System.out.println();

      return Toolkit.svgToImageMult(miniSvgXml, ColorfulPanel.DIM, ColorfulPanel.DIM, replacements);
    }

    String getPath(int index) {
      return paths[index];
    }

//    String getTheme(int index) {
//      return themes[index];
//    }

    /**
     * Return the index for a given theme in this set,
     * or -1 if not part of this set.
     */
    int getIndex(String theme) {
      return indices.getOrDefault(theme, -1);
    }
  }



  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class ColorfulPanel extends JPanel {
    static final int OUTSET = 5;
    static final int OUTLINE = 3;

    static final int DIM = 80;
    static final int BETWEEN = 25;
    static final int EACH = DIM + BETWEEN;
    static final int MARGIN = OUTSET + (OUTLINE + 1) / 2;
    static final int SIZE = MARGIN*2 + DIM*4 + BETWEEN*3;

    ColorfulPanel() {
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          handleMouse(e);
        }
      });

      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
          handleMouse(e);
        }
      });

      setOpaque(false);
    }

    private void handleMouse(MouseEvent e) {
      int col = constrain((e.getX() - MARGIN) / EACH);
      int colEx = constrain((e.getX() - MARGIN) % EACH);
      int row = constrain((e.getY() - MARGIN) / EACH);
      int rowEx = constrain((e.getY() - MARGIN) % EACH);

      if (colEx < DIM && rowEx < DIM) {
        int index = row * 4 + col;
        if (index < currentSet.count && index != currentIndex) {
          setCurrentIndex(index);
          repaint();
        }
      }
    }

    private int constrain(int value) {
      return Math.max(0, Math.min(value, 3));
    }

    @Override
    public void paintComponent(Graphics g) {
      for (int i = 0; i < currentSet.count; i++) {
        int col = i % 4;
        int row = i / 4;
        int x = MARGIN + col*EACH;
        int y = MARGIN + row*EACH;
        g.drawImage(currentSet.images[i], x, y, DIM, DIM, null);
      }

      Graphics2D g2 = (Graphics2D) g;
      g2.setStroke(new BasicStroke(OUTLINE));
      g2.setColor(Color.GRAY);

      if (currentIndex != -1) {
        int col = currentIndex % 4;
        int row = currentIndex / 4;
        g2.drawRect(MARGIN + EACH * col - OUTSET,
          MARGIN + EACH * row - OUTSET,
          DIM + OUTSET * 2,
          DIM + OUTSET * 2);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(SIZE, SIZE);
    }

    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }
  }
}