package processing.app;

import processing.core.PApplet;
import processing.data.JSONArray;
import processing.data.JSONObject;
import processing.data.StringList;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class SketchName {
  static boolean breakTime = false;

  static Map<String, WordList> wordLists;


  /**
   * Return a new File object for the next sketch directory.
   * The name of this directory will also be used as the sketch name.
   * (i.e. in Java Mode, dirname.pde will be created as the main tab)
   * If returning null, it's up to *this* class to report errors to the user.
   * @param parentDir Parent directory where the sketch folder will live
   * @return File object for safe new path, or null if there were problems
   */
  static File nextFolder(File parentDir) {
    //return classicFolder(parentDir);
    return wordsFolder(parentDir);
  }


  /**
   * Use a generic name like sketch_031008a, the date plus a char.
   */
  static File classicFolder(File parentDir) {
    File newbieDir;
    String newbieName;

    int index = 0;
    String prefix = Preferences.get("editor.untitled.prefix");
    String format = Preferences.get("editor.untitled.suffix");
    String suffix;
    if (format == null) {
      // If no format is specified, uses this ancient format
      Calendar cal = Calendar.getInstance();
      int day = cal.get(Calendar.DAY_OF_MONTH);  // 1..31
      int month = cal.get(Calendar.MONTH);  // 0..11
      final String[] months = {
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec"
      };
      suffix = months[month] + PApplet.nf(day, 2);
    } else {
      SimpleDateFormat formatter = new SimpleDateFormat(format);
      suffix = formatter.format(new Date());
    }
    do {
      if (index == 26) {
        // In 0159, avoid running past z by sending people outdoors.
        if (!breakTime) {
          Messages.showWarning("Time for a Break",
                  "You've reached the limit for auto naming of new sketches\n" +
                          "for the day. How about going for a walk instead?", null);
          breakTime = true;
        } else {
          Messages.showWarning("Sunshine",
                  "No really, time for some fresh air for you.", null);
        }
        return null;
      }
      newbieName = prefix + suffix + ((char) ('a' + index));
      // Also sanitize the name since it might do strange things on
      // non-English systems that don't use this sort of date format.
      // https://github.com/processing/processing/issues/322
      newbieName = Sketch.sanitizeName(newbieName);
      newbieDir = new File(parentDir, newbieName);
      index++;
      // Make sure it's not in the temp folder *and* it's not in the sketchbook
    } while (newbieDir.exists() || new File(Base.getSketchbookFolder(), newbieName).exists());

    return newbieDir;
  }


  static class WordList {
    String name;
    String notes;
    StringList prefixes;
    StringList suffixes;

    WordList(JSONObject source) {
      name = source.getString("name");
      notes = source.getString("notes");
      prefixes = source.getStringList("prefixes");
      suffixes = source.getStringList("suffixes");
    }

    String getPair() {
      return (prefixes.random() + " " + suffixes.random()).replace(' ', '_');
    }
  }


  static File wordsFolder(File parentDir) {
    if (wordLists == null) {
      wordLists = new HashMap<>();
      try {
        File namingFile = Base.getLibFile("naming.json");
        JSONArray array = PApplet.loadJSONArray(namingFile);
        for (int i = 0; i < array.size(); i++) {
          JSONObject obj = array.getJSONObject(i);
          WordList wl = new WordList(obj);
          wordLists.put(wl.name, wl);
        }
      } catch (IOException e) {
        Messages.showWarning("Naming Error", "Could not load word lists from naming.json", e);
      }
    }
    final String setName = "Cooking";
    WordList wl = wordLists.get(setName);
    File outgoing;
    do {
      outgoing = new File(parentDir, wl.getPair());
    } while (outgoing.exists());
    return outgoing;
  }
}