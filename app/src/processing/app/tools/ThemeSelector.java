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
    return Language.text("Theme Selector");
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
    checkCurrent();

    setVisible(true);
  }


  void setCurrent(int index) {
    //System.out.println("index is " + index);
    currentIndex = index;
    try {
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


  private void checkCurrent() {
    //
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class ColorfulPanel extends JPanel {
    static final int SCALE = 4;
    static final int EACH = 320 / SCALE;
    static final int BETWEEN = 100 / SCALE;
    static final int SIZE = EACH*4 + BETWEEN*5;

    Image image;

    ColorfulPanel() {
      image = Toolkit.getLibImage("themes/4x4.png");
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          //super.mousePressed(e);

          int col = constrain((e.getX() - BETWEEN) / (EACH + BETWEEN));
          int row = constrain((e.getY() - BETWEEN) / (EACH + BETWEEN));
          int index = row*4 + col;

          setCurrent(index);
        }
      });
    }

    private int constrain(int value) {
      return Math.max(0, Math.min(value, 3));
    }

    @Override
    public void paintComponent(Graphics g) {
      g.drawImage(image, 0, 0, SIZE, SIZE,null);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(SIZE, SIZE);
    }
  }
}