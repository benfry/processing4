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
import processing.app.Preferences;
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
  static final String DEFAULT_PATH = "Minerals/kyanite.txt";
  static Settings theme;

  static public void init() {
    try {
      File inputFile = getDefaultFile();
      if (!inputFile.exists()) {
        System.err.println("Missing required file (theme.txt), please reinstall Processing.");
      }
      // First load the default theme data, in case new parameters were added
      // that may not be covered with a custom version found in the sketchbook.
      theme = new Settings(inputFile);

      // other things that have to be set explicitly for the defaults
      theme.setColor("run.window.bgcolor", SystemColor.control);

      if (Preferences.get("theme") == null) {
        // This is not being set in defaults.txt so that we have a way
        // to reset the theme after the major changes in 4.0 beta 9.
        // This does a one-time archival of the theme.txt file in the
        // sketchbook folder, because most people have not customized
        // their theme, but they probably made a selection.
        // If they customized the theme, they can bring it back by
        // renaming the file from theme.001 to theme.txt.
        // If they were using a built-in theme, they will need to
        // re-select it using the Theme Selector.
        Preferences.set("theme", DEFAULT_PATH);

        if (getSketchbookFile().exists()) {
          archiveCurrent();
        }
      }

      // load sketchbook theme or the one specified in preferences
      reload();

    } catch (IOException e) {
      Messages.showError("Problem loading theme.txt",
        "Could not load theme.txt, please re-install Processing", e);
    }
  }


  /**
   * Pull in the version from the user's sketchbook folder,
   * or if none exists, use the setting from preferences.
   */
  static public void reload() {
    if (!loadSketchbookFile()) {
      String prefTheme = Preferences.get("theme");
      try {
        File prefFile = new File(getThemeFolder(), prefTheme);
        if (prefFile.exists()) {
          theme.load(prefFile);
        }
      } catch (IOException e) {
        Messages.showWarning("Theme Reload Problem",
          "Error while reloading the theme. Please report.", e);
      }
    }
  }


  /**
   * Load theme.txt from the user's sketchbook folder.
   * The caller is expected to make sure the file exists.
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  static public boolean loadSketchbookFile() {
    File sketchbookTheme = getSketchbookFile();
    if (sketchbookTheme.exists()) {
      theme.load(sketchbookTheme);
      return true;
    }
    return false;
  }


  static public void save() {
    theme.save(getSketchbookFile());
  }


  static public File getThemeFolder() throws IOException {
    return Base.getLibFile("theme");
  }


  /**
   * Returns lib/theme/theme.txt in the Processing installation.
   */
  static public File getDefaultFile() throws IOException {
    return new File(getThemeFolder(), "theme.txt");
  }


  static public File getSketchbookFile() {
    return new File(Base.getSketchbookFolder(), "theme.txt");
  }


  /**
   * If the user has a custom theme they've modified, rename it to theme.001,
   * theme.002, etc. as a backup to avoid overwriting anything they've created.
   */
  static public boolean archiveCurrent() {
    File backupFile = nextArchiveFile();
    return getSketchbookFile().renameTo(backupFile);
  }


  static private File nextArchiveFile() {
    int index = 0;
    File backupFile;
    do {
      index++;
      backupFile = new File(Base.getSketchbookFolder(), String.format("theme.%03d", index));
    } while (backupFile.exists());
    return backupFile;
  }


  static public void print() {
    theme.print();
  }


  static public String get(String attribute) {
    return theme.get(attribute);
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