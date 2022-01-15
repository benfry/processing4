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
import processing.app.ui.Editor;
import processing.app.ui.Theme;
import processing.app.ui.Toolkit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;


public class ThemeSelector extends JFrame implements Tool {
  static final String[] themeOrder = {
    "kyanite", "calcite", "olivine", "beryl",
    "galena", "jasper", "malachite", "pyrite",
    "gabbro", "fluorite", "orpiment", "feldspar",
    "antimony", "serandite", "bauxite", "garnet"
  };
  static final int COUNT = themeOrder.length;
  String[] themeContents;

  File sketchbookFile;
  int currentIndex;

  ColorfulPanel selector;

  Base base;


  public String getMenuTitle() {
    return Language.text("Theme Selector...");
  }


  public void init(Base base) {
    this.base = base;

    themeContents = new String[COUNT];
    for (int i = 0; i < COUNT; i++) {
      try {
        File file = Base.getLibFile("themes/" + themeOrder[i] + ".txt");
        themeContents[i] = Util.loadFile(file);

      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    Container pane = getContentPane();
    pane.setLayout(new BorderLayout());
    pane.add(selector = new ColorfulPanel(), BorderLayout.CENTER);

    Toolkit.registerWindowCloseKeys(getRootPane(), e -> setVisible(false));
    setTitle(getMenuTitle());
    pack();
    setLocationRelativeTo(null);
  }


  public void run() {
    // location for theme.txt in the sketchbook folder
    // (doing this in run() in case the sketchbook location has changed)
    sketchbookFile = new File(Base.getSketchbookFolder(), "theme.txt");

    // figure out if the current theme in sketchbook is a known one
    currentIndex = getCurrentIndex();

    setVisible(true);
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


  private void setCurrentIndex(int index) {
    //System.out.println("index is " + index);
    currentIndex = index;
    try {
      if (sketchbookFile.exists() && getCurrentIndex() == -1) {
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
      Util.saveFile(themeContents[index], sketchbookFile);
      Theme.load();

      for (Editor editor : base.getEditors()) {
        editor.updateTheme();
        //editor.repaint();
      }

    } catch (IOException e) {
      base.getActiveEditor().statusError(e);
    }
  }


  private int getCurrentIndex() {
    try {
      if (sketchbookFile.exists()) {
        String currentContents = Util.loadFile(sketchbookFile);
        for (int i = 0; i < COUNT; i++) {
          if (themeContents[i].equals(currentContents)) {
            return i;
          }
        }
        return -1;
      }
      return 0;  // the default theme is index 0

    } catch (Exception e) {
      e.printStackTrace();
      return -1;  // could not identify the theme
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
      image = Toolkit.getLibImage("themes/4x4.png");
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

      int col = currentIndex % 4;
      int row = currentIndex / 4;
      g2.drawRect(BETWEEN + EACH*col - OUTSET,
        BETWEEN + EACH*row - OUTSET,
        DIM + OUTSET*2,
        DIM + OUTSET*2);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(SIZE, SIZE);
    }
  }
}