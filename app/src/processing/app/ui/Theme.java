/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2021 The Processing Foundation

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

import processing.app.Base;
import processing.app.Messages;
import processing.app.Platform;
import processing.app.Settings;
import processing.app.syntax.SyntaxStyle;
import processing.core.PApplet;
import processing.core.PConstants;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;


public class Theme {
  static Settings theme;

  static public void init() {
    load();
  }

  static public void load() {
    try {
      File inputFile = Platform.getContentFile("lib/theme.txt");
      if (inputFile == null) {
        throw new RuntimeException("Missing required file (theme.txt), you may need to reinstall.");
      }
      // First load the default theme data for the whole PDE.
      theme = new Settings(inputFile);

      /*
      // The mode-specific theme.txt file should only contain additions,
      // and in extremely rare cases, it might override entries from the
      // main theme. Do not override for style changes unless they are
      // objectively necessary for your Mode.
      File modeTheme = new File(folder, "theme/theme.txt");
      if (modeTheme.exists()) {
        // Override the built-in settings with what the theme provides
        theme.load(modeTheme);
      }
      */

      // https://github.com/processing/processing/issues/5445
      File sketchbookTheme = getSketchbookFile();
//        new File(Base.getSketchbookFolder(), "theme.txt");
      if (sketchbookTheme.exists()) {
        theme.load(sketchbookTheme);
      }

      // other things that have to be set explicitly for the defaults
      theme.setColor("run.window.bgcolor", SystemColor.control);

    } catch (IOException e) {
      Messages.showError("Problem loading theme.txt",
        "Could not load theme.txt, please re-install Processing", e);
    }
  }

  static public void save() {
    theme.save(getSketchbookFile());
  }


  static public File getSketchbookFile() {
    return new File(Base.getSketchbookFolder(), "theme.txt");
  }


  static public boolean getBoolean(String attribute) {
    return theme.getBoolean(attribute);
  }


  static public int getInteger(String attribute) {
    return theme.getInteger(attribute);
  }


  static public Color getColor(String attribute) {
    return theme.getColor(attribute);
  }


  static public Font getFont(String attribute) {
    return theme.getFont(attribute);
  }


  static public SyntaxStyle getStyle(String attribute) {
    //String str = Preferences.get("editor.token." + attribute + ".style");
    String str = theme.get("editor.token." + attribute + ".style");
    if (str == null) {
      throw new IllegalArgumentException("No style found for " + attribute);
    }
    return SyntaxStyle.fromString(str);
  }


  static public Image makeGradient(String attribute, int wide, int high) {
    int top = getColor(attribute + ".gradient.top").getRGB();
    int bot = getColor(attribute + ".gradient.bottom").getRGB();

    BufferedImage outgoing =
      new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
    int[] row = new int[wide];
    WritableRaster wr = outgoing.getRaster();
    for (int i = 0; i < high; i++) {
      int rgb = PApplet.lerpColor(top, bot, i / (float)(high-1), PConstants.RGB);
      Arrays.fill(row, rgb);
      wr.setDataElements(0, i, wide, 1, row);
    }
    return outgoing;
  }
}