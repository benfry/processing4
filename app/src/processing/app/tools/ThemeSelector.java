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
import processing.app.ui.Theme;
import processing.app.ui.Toolkit;
import processing.core.PApplet;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static processing.app.ui.Toolkit.addRow;


public class ThemeSelector extends JFrame implements Tool {
  static final String HOWTO_URL = "https://github.com/processing/processing4/wiki/Themes";
  static final String ORDER_FILENAME = "order.txt";

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
      File themeFolder = Base.getLibFile("themes");
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
    //pane.setLayout(new BorderLayout());

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
    //pane.add(setSelector, BorderLayout.NORTH);
    addRow(axis, setSelector);

    //pane.add(selector = new ColorfulPanel(), BorderLayout.CENTER);
    axis.add(selector = new ColorfulPanel());  // flush with sides

    axis.setBorder(new EmptyBorder(13, 13, 13, 13));
    pane.add(axis);

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

    addRow(axis, reloadTheme = new JLabel());
    reloadTheme.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        if (!sketchbookFile.exists()) {
          // When first called, just create the theme.txt file
          Theme.save();
          updateTheme();  // changes the JLabel for this fella
        } else {
          reloadTheme();
        }
      }

      public void mouseEntered(MouseEvent e) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      public void mouseExited(MouseEvent e) {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    });

    Toolkit.registerWindowCloseKeys(getRootPane(), e -> setVisible(false));
    setTitle(getMenuTitle());
    pack();
    setLocationRelativeTo(null);
  }


  public void run() {
    // location for theme.txt in the sketchbook folder
    // (doing this in run() in case the sketchbook location has changed)
    sketchbookFile = new File(Base.getSketchbookFolder(), "theme.txt");

    updateTheme();

    // figure out if the current theme in sketchbook is a known one
    //currentIndex = getCurrentIndex();
    updateCurrentIndex();

    setVisible(true);
  }


  private void updateTheme() {
    getContentPane().setBackground(Theme.getColor("theme_selector.window.color"));

    if (setSelector.getUI() instanceof PdeComboBoxUI) {
      ((PdeComboBoxUI) setSelector.getUI()).updateTheme();
    } else {
      setSelector.setUI(new PdeComboBoxUI("theme_selector.combo_box"));
    }

    String textColor = Theme.get("theme_selector.text.color");
    String linkColor = Theme.get("theme_selector.link.color");

    howtoLabel.setText("<html><a href=\"\" color=\"" + linkColor +
      "\">Read</a> <font color=\"" + textColor + "\">about how to create your own themes.");
    if (Theme.getSketchbookFile().exists()) {
      reloadTheme.setText("<html><a href=\"\" color=\"" + linkColor +
        "\">Reload</a> <font color=\"" + textColor + "\">theme.txt to update the current theme.");
    } else {
      reloadTheme.setText("<html><a href=\"\" color=\"" + linkColor +
        "\">Save</a> <font color=\"" + textColor + "\">theme.txt to sketchbook for editing.");
    }
  }


  private File nextBackupFile() {
    int index = 0;
    File backupFile;
    do {
      index++;
      backupFile = new File(Base.getSketchbookFolder(), String.format("theme.%03d", index));
    } while (backupFile.exists());
    return backupFile;
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
    return sets.get(0).themes.get(0);
  }


  /**
   * @return true if sketchbook/theme.txt does not match a built-in theme.
   */
  private boolean userModifiedTheme() {
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
    try {
      if (userModifiedTheme()) {
        // If the user has a custom theme they've modified,
        // rename it to theme.001, theme.002, etc. as a backup
        // to avoid overwriting anything they've created.
        File backupFile = nextBackupFile();
        boolean success = sketchbookFile.renameTo(backupFile);
        if (!success) {
          Messages.showWarning("Could not back up theme",
            "Could not save a backup of theme.txt in your sketchbook folder.\n" +
            "Rename it manually and try setting the theme again.");
          return;
        }
      }

      // Save the file and reload the theme.
      Util.saveFile(currentSet.get(index), sketchbookFile);
      reloadTheme();

    } catch (IOException e) {
      base.getActiveEditor().statusError(e);
    }
  }


  private void reloadTheme() {
    Theme.load();
    base.updateTheme();
    updateTheme();
  }


  private void updateCurrentIndex() {
    String currentTheme = getCurrentTheme();
    currentIndex = currentSet.getIndex(currentTheme);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class ThemeSet {
    private String name;
    private List<String> themes;
    private Map<String, Integer> indices;

    ThemeSet(File dir) {
      name = dir.getName();
      themes = new ArrayList<>();
      indices = new HashMap<>();

      File orderFile = new File(dir, ORDER_FILENAME);
      String[] lines = PApplet.loadStrings(orderFile);
      if (lines != null) {
        for (String name : lines) {
          File file = new File(dir, name + ".txt");
          String theme = Util.loadFile(file);
          indices.put(theme, indices.size());
//          hashes.add(theme.hashCode());
          themes.add(theme);
        }
      }
    }

    String get(int index) {
      return themes.get(index);
    }

    /**
     * Return the index for a given theme in this set,
     * or -1 if not part of this set.
     */
    int getIndex(String theme) {
      return indices.getOrDefault(theme, -1);
      /*
      for (int i = 0; i < themeContents.size(); i++) {
        if (theme.equals(themeContents.get(i))) {
          return i;
        }
      }
      return -1;
      */
    }
  }



  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class ColorfulPanel extends JPanel {
    static final int SCALE = 4;
    static final int DIM = 320 / SCALE;
    static final int BETWEEN = 100 / SCALE;
    static final int EACH = DIM + BETWEEN;
    static final int SIZE = DIM*4 + BETWEEN*5;

    static final int OUTSET = 5;
    static final int OUTLINE = 3;

    Image image;

    ColorfulPanel() {
      image = Toolkit.getLibImage("themes/Minerals/4x4.png");
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          //super.mousePressed(e);

          int col = constrain((e.getX() - BETWEEN) / EACH);
          int row = constrain((e.getY() - BETWEEN) / EACH);
          int index = row*4 + col;

          setCurrentIndex(index);
          repaint();
        }
      });
    }

    private int constrain(int value) {
      return Math.max(0, Math.min(value, 3));
    }

    @Override
    public void paintComponent(Graphics g) {
      g.drawImage(image, 0, 0, SIZE, SIZE,null);

      Graphics2D g2 = (Graphics2D) g;
      g2.setStroke(new BasicStroke(OUTLINE));
      g2.setColor(Color.GRAY);

      if (currentIndex != -1) {
        int col = currentIndex % 4;
        int row = currentIndex / 4;
        g2.drawRect(BETWEEN + EACH * col - OUTSET,
          BETWEEN + EACH * row - OUTSET,
          DIM + OUTSET * 2,
          DIM + OUTSET * 2);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(SIZE, SIZE);
    }
  }
}