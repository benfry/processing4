/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2004-11 Ben Fry and Casey Reas
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

package processing.app;

import java.awt.*;
import java.io.*;
import java.util.*;

import processing.app.ui.Toolkit;
import processing.core.*;


/**
 * Storage class for theme settings. This was separated from the Preferences
 * class for 1.0 so that the coloring wouldn't conflict with previous releases
 * and to make way for future ability to customize.
 */
public class Settings {
  /**
   * Copy of the defaults in case the user mangles a preference.
   * It's necessary to keep a copy of the defaults around, because the user may
   * have replaced a setting on their own. In the past, we used to load the
   * defaults, then replace those with what was in the user's preferences file.
   * Problem is, if something like a font entry in the user's file no longer
   * parses properly, we need to be able to get back to a clean version of that
   * setting so that we can recover.
   */
  Map<String, String> defaults;

  /** Table of attributes/values. */
  Map<String, String> table = new HashMap<>();

  /** Associated file for this settings data. */
  File file;


  public Settings(File file) throws IOException {
    this.file = file;

    if (file.exists()) {
      load();
    }

    // clone the hash table
    defaults = new HashMap<>(table);
  }


  public void load() {
    load(file);
  }


  public void load(File additions) {
    String[] lines = PApplet.loadStrings(additions);
    if (lines != null) {
      for (String line : lines) {
        if ((line.length() == 0) ||
          (line.charAt(0) == '#')) continue;

        // this won't properly handle = signs being in the text
        int equals = line.indexOf('=');
        if (equals != -1) {
          String key = line.substring(0, equals).trim();
          String value = line.substring(equals + 1).trim();
          table.put(key, value);
        }
      }
    } else {
      Messages.err(additions + " could not be read");
    }

    // check for platform-specific properties in the defaults
    String platformExt = "." + Platform.getName();
    int platformExtLength = platformExt.length();
    HashMap<String,String> tmpMap = new HashMap<>();
    for (String key : table.keySet()) {
      if (key.endsWith(platformExt)) {
        // this is a key specific to a particular platform
        String actualKey = key.substring(0, key.length() - platformExtLength);
        String value = get(key);
        //if the key is specific to this platform and is not the last property then attempting to append it to the map will throw a ConcurrentModificationException
        //to avoid this we append it to a temporary map that can then be safely merged after the loop is complete
        tmpMap.put(actualKey, value);
      }
    }
    table.putAll(tmpMap);
  }


  public void save() {
    save(file);  // save back to the original file
  }


  public void save(File outputFile) {
    PrintWriter writer = PApplet.createWriter(outputFile);

    String[] keyList = table.keySet().toArray(new String[0]);
    // Sorting is really helpful for debugging, diffing, and finding keys
    keyList = PApplet.sort(keyList);
    for (String key : keyList) {
      writer.println(key + "=" + table.get(key));
    }

    writer.flush();
    writer.close();
  }


  public String get(String attribute) {
    return table.get(attribute);
  }


  public String getDefault(String attribute) {
    return defaults.get(attribute);
  }


  public void set(String attribute, String value) {
    table.put(attribute, value);
  }


  public boolean getBoolean(String attribute) {
    String value = get(attribute);
    if (value == null) {
      System.err.println("Boolean not found: " + attribute);
      return false;
    }
    return Boolean.parseBoolean(value);
  }


  public void setBoolean(String attribute, boolean value) {
    set(attribute, value ? "true" : "false");
  }


  public int getInteger(String attribute) {
    String value = get(attribute);
    if (value == null) {
      System.err.println("Integer not found: " + attribute);
      return 0;
    }
    return Integer.parseInt(value);
  }


  @SuppressWarnings("unused")
  public void setInteger(String key, int value) {
    set(key, String.valueOf(value));
  }


  /**
   * Parse a color from a Settings file. Values are hexadecimal in either
   * #RRGGBB (for opaque) or 0xAARRGGBB format (to include alpha).
   */
  public Color getColor(String attribute) {
    Color outgoing = null;
    String s = get(attribute);
    if (s != null) {
      try {
        if (s.startsWith("#")) {
          // parse a 6-digit hex color
          outgoing = new Color(Integer.parseInt(s.substring(1), 16));
        } else if (s.startsWith("0x")) {
          int v = Integer.parseInt(s.substring(2), 16);
          outgoing = new Color(v, true);
        }
      } catch (Exception ignored) { }
    }
    if (outgoing == null) {
      System.err.println("Could not parse color " + s + " for " + attribute);
    }
    return outgoing;
  }


  public void setColor(String attr, Color what) {
    set(attr, "#" + PApplet.hex(what.getRGB() & 0xffffff, 6));
  }


  // identical version found in Preferences.java
  public Font getFont(String attr) {
    try {
      boolean replace = false;
      String value = get(attr);
      if (value == null) {
        // use the default font instead
        value = getDefault(attr);
        replace = true;
      }

      String[] pieces = PApplet.split(value, ',');

      if (pieces.length != 3) {
        value = getDefault(attr);
        pieces = PApplet.split(value, ',');
        replace = true;
      }

      String name = pieces[0];
      int style = Font.PLAIN;  // equals zero
      if (pieces[1].contains("bold")) { //$NON-NLS-1$
        style |= Font.BOLD;
      }
      if (pieces[1].contains("italic")) { //$NON-NLS-1$
        style |= Font.ITALIC;
      }
      int size = PApplet.parseInt(pieces[2], 12);
      size = Toolkit.zoom(size);

      // replace bad font with the default from lib/preferences.txt
      if (replace) {
        set(attr, value);
      }

      if (!name.startsWith("processing.")) {
        return new Font(name, style, size);

      } else {
        if (pieces[0].equals("processing.sans")) {
          return Toolkit.getSansFont(size, style);
        } else if (pieces[0].equals("processing.mono")) {
          return Toolkit.getMonoFont(size, style);
        }
      }

    } catch (Exception e) {
      // Adding try/catch block because this may be where
      // a lot of startup crashes are happening.
      Messages.log("Error with font " + get(attr) + " for attribute " + attr);
    }
    return new Font("Dialog", Font.PLAIN, 12);
  }


  public String remove(String key) {
    return table.remove(key);
  }


  /**
   * The day of reckoning: save() a file if it has entries, or delete
   * if the file exists but the table no longer has any entries.
   */
  public void reckon() {
    if (table.isEmpty()) {
      if (file.exists() && !file.delete()) {
        System.err.println("Could not remove empty " + file);
      }
    } else {
      save();
    }
  }


  public boolean isEmpty() {
    return table.isEmpty();
  }


  public void print() {
    String[] keys = table.keySet().toArray(new String[0]);
    Arrays.sort(keys);
    for (String key : keys) {
      System.out.println(key + " = " + table.get(key));
    }
  }


  public Map<String, String> getMap() {
    return table;
  }
}