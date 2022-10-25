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
    if ("lab".equals(Preferences.get("theme.gradient.method"))) {
      return makeGradientLab(attribute, wide, high);
    } else {  // otherwise go with the default
      return makeGradientRGB(attribute, wide, high);
    }
  }


  static private Image makeGradientRGB(String attribute, int wide, int high) {
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


  static private Image makeGradientLab(String attribute, int wide, int high) {
    double[] top = xyzToLab(rgbToXyz(getColor(attribute + ".gradient.top")));
    double[] bot = xyzToLab(rgbToXyz(getColor(attribute + ".gradient.bottom")));

    double diffL = bot[0] - top[0];
    double diffA = bot[1] - top[1];
    double diffB = bot[2] - top[2];

    BufferedImage outgoing =
      new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
    int[] row = new int[wide];
    WritableRaster wr = outgoing.getRaster();
    for (int i = 0; i < high; i++) {
      double amt = i / (high - 1.0);
      double el = top[0] + amt * diffL;
      double ay = top[1] + amt * diffA;
      double be = top[2] + amt * diffB;
      int rgb = argb(xzyToRgb(labToXyz(el, ay, be)));
      Arrays.fill(row, rgb);
      wr.setDataElements(0, i, wide, 1, row);
    }
    return outgoing;
  }


  // https://web.archive.org/web/20060213080500/http://www.easyrgb.com/math.php?MATH=M2#text2

  // Observer= 2°, Illuminant= D65;
  static final double REF_X = 95.047;
  static final double REF_Y = 100.0;
  static final double REF_Z = 108.883;


  static private double[] rgbToXyz(Color color) {
    double var_R = color.getRed() / 255.0;
    double var_G = color.getGreen() / 255.0;
    double var_B = color.getBlue() / 255.0;

    var_R = 100 * ((var_R > 0.04045) ?
      Math.pow((var_R + 0.055) / 1.055, 2.4) : var_R / 12.92);
    var_G = 100 * ((var_G > 0.04045) ?
      Math.pow((var_G + 0.055) / 1.055, 2.4) : var_G / 12.92);
    var_B = 100 * ((var_B > 0.04045) ?
      Math.pow((var_B + 0.055) / 1.055, 2.4) : var_B / 12.92);

    // Observer = 2°, Illuminant = D65
    return new double[] {
      var_R * 0.4124 + var_G * 0.3576 + var_B * 0.1805,
      var_R * 0.2126 + var_G * 0.7152 + var_B * 0.0722,
      var_R * 0.0193 + var_G * 0.1192 + var_B * 0.9505
    };
  }


  static private double[] xyzToLab(double[] xyz) {
    double var_X = xyz[0] / REF_X;  //  Observer= 2°, Illuminant= D65
    double var_Y = xyz[1] / REF_Y;
    double var_Z = xyz[2] / REF_Z;

    var_X = (var_X > 0.008856) ?
      Math.pow(var_X, 1/3.0) : (7.787*var_X + 16/116.0);

    var_Y = (var_Y > 0.008856) ?
      Math.pow(var_Y, 1/3.0) : (7.787*var_Y + 16/116.0);

    var_Z = (var_Z > 0.008856) ?
      Math.pow(var_Z, 1/3.0) : (7.787*var_Z + 16/116.0);

    return new double[] {
      (116 * var_Y) - 16,
      500 * (var_X - var_Y),
      200 * (var_Y - var_Z)
    };
  }

//  static private double[] labToXyz(double[] lab) {
//    double var_Y = (lab[0] + 16) / 116.0;
//    double var_X = lab[1] / 500 + var_Y;
//    double var_Z = var_Y - lab[2] / 200.0;
  static private double[] labToXyz(double el, double ay, double be) {
    double var_Y = (el + 16) / 116;
    double var_X = ay / 500 + var_Y;
    double var_Z = var_Y - be / 200;

//    if ( var_Y^3 > 0.008856 ) var_Y = var_Y^3
//    else                      var_Y = ( var_Y - 16 / 116 ) / 7.787
//    if ( var_X^3 > 0.008856 ) var_X = var_X^3
//    else                      var_X = ( var_X - 16 / 116 ) / 7.787
//    if ( var_Z^3 > 0.008856 ) var_Z = var_Z^3
//    else                      var_Z = ( var_Z - 16 / 116 ) / 7.787

    final double amt = Math.pow(0.008856, 1/3.0);
    var_Y = (var_Y > amt) ?
      Math.pow(var_Y, 3) : (var_Y - 16/116.0) / 7.787;
    var_X = (var_X > amt) ?
      Math.pow(var_X, 3) : (var_X - 16/116.0) / 7.787;
    var_Z = (var_Z > amt) ?
      Math.pow(var_Z, 3) : (var_Z - 16/116.0) / 7.787;

//    X = ref_X * var_X     //ref_X =  95.047  Observer= 2°, Illuminant= D65
//    Y = ref_Y * var_Y     //ref_Y = 100.000
//    Z = ref_Z * var_Z     //ref_Z = 108.883
    return new double[] {
      REF_X * var_X,
      REF_Y * var_Y,
      REF_Z * var_Z
    };
  }


  static private double[] xzyToRgb(double[] xyz) {
    double var_X = xyz[0] / 100;  // Where X = 0 ÷  95.047
    double var_Y = xyz[1] / 100;  // Where Y = 0 ÷ 100.000
    double var_Z = xyz[2] / 100;  // Where Z = 0 ÷ 108.883

    double var_R = var_X *  3.2406 + var_Y * -1.5372 + var_Z * -0.4986;
    double var_G = var_X * -0.9689 + var_Y *  1.8758 + var_Z *  0.0415;
    double var_B = var_X *  0.0557 + var_Y * -0.2040 + var_Z *  1.0570;

//    if ( var_R > 0.0031308 ) var_R = 1.055 * ( var_R ^ ( 1 / 2.4 ) ) - 0.055
//    else                     var_R = 12.92 * var_R
//    if ( var_G > 0.0031308 ) var_G = 1.055 * ( var_G ^ ( 1 / 2.4 ) ) - 0.055
//    else                     var_G = 12.92 * var_G
//    if ( var_B > 0.0031308 ) var_B = 1.055 * ( var_B ^ ( 1 / 2.4 ) ) - 0.055
//    else                     var_B = 12.92 * var_B

    var_R = (var_R > 0.0031308) ?
      1.055 * Math.pow(var_R, 1/2.4) - 0.055 : 12.92 * var_R;
    var_G = (var_G > 0.0031308) ?
      1.055 * Math.pow(var_G, 1/2.4) - 0.055 : 12.92 * var_G;
    var_B = (var_B > 0.0031308) ?
      1.055 * Math.pow(var_B, 1/2.4) - 0.055 : 12.92 * var_B;

    return new double[] {
      var_R * 255,
      var_G * 255,
      var_B * 255
    };
  }


  static private int bounded(double amount) {
    return Math.max(0, Math.min((int) Math.round(amount), 255));
  }


  static private int argb(double[] rgb) {
    return 0xff000000 |
      bounded(rgb[0]) << 16 | bounded(rgb[1]) << 8 | bounded(rgb[2]);
  }
}