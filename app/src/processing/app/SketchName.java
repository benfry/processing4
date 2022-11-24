package processing.app;

import processing.core.PApplet;
import processing.data.JSONArray;
import processing.data.JSONObject;
import processing.data.StringList;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class SketchName {
  static final String FILENAME = "naming.json";
  static final public String CLASSIC = "Classic (sketch_220809a)";
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
    String approach = Preferences.get("sketch.name.approach");
    if ((approach != null) && !CLASSIC.equals(approach)) {
      WordList wl = getWordLists().get(approach);
      if (wl != null) {  // just in case the naming scheme no longer available
        File folder = wl.wordsFolder(parentDir);
        if (folder == null) {
          Messages.showWarning("Out of Options", """
            All possible naming combinations have been used.
            Use “Preferences” to choose a different naming system,
            or restart Processing.""");
        }
        return folder;  // null or otherwise
      }
    }
    // classic was selected, or fallback due to an error
    return classicFolder(parentDir);
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
                  "No really, time for some fresh air for you.\n" +
                  "(At a minimum, you'll need to restart Processing.)", null);
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
    StringList exhaustive;

    WordList(JSONObject source) {
      name = source.getString("name");
      notes = source.getString("notes");
      prefixes = source.getStringList("prefixes");
      suffixes = source.getStringList("suffixes");
    }

    String getPair() {
      return (prefixes.choice() + " " + suffixes.choice()).replace(' ', '_');
    }

//    int getComboCount() {
//      return prefixes.size() * suffixes.size();
//    }

    /**
     * For name sets that are especially small, it may be possible
     * to run out of name pairs. This first builds a (shuffled) list
     * of all remaining name possibilities and then returns entries
     * from that list until it has been exhausted.
     */
    File wordsFolderExhaust(File parentDir) {
      // if it doesn't exist already, create a list of all (unused) possibilities
      if (exhaustive == null) {
        exhaustive = new StringList();
        for (String prefix : prefixes) {
          for (String suffix : suffixes) {
            String pair = (prefix + " " + suffix).replace(' ', '_');
            String name = Sketch.sanitizeName(pair);
            File folder = new File(parentDir, name);
            if (!folder.exists()) {
              exhaustive.append(name);
            }
          }
        }
        exhaustive.shuffle();
      }
      // keep trying until we find something or the list is empty
      while (true) {
        if (exhaustive.size() == 0) {
          return null;
        }
        String name = exhaustive.removeChoice();
        if (name == null) {
          return null;  // no more choices available
        }
        File outgoing = new File(parentDir, name);
        if (!outgoing.exists()) {
          return outgoing;
        }
      }
    }

    File wordsFolder(File parentDir) {
      if (exhaustive != null) {
        return wordsFolderExhaust(parentDir);
      }
      // Still may be other possibilities after this, but if we hit
      // this many attempts, we've pretty much exhausted the list.
      final int maxAttempts = prefixes.size() * suffixes.size();
      int attempts = 0;
      File outgoing;
      do {
        // Clean up the name in case a user-supplied word list breaks the rules
        String name = Sketch.sanitizeName(getPair());
        outgoing = new File(parentDir, name);
        attempts++;
        if (attempts == maxAttempts) {
          //return null;  // avoid infinite loop
          return wordsFolderExhaust(parentDir);
        }
      } while (outgoing.exists());
      return outgoing;
    }
  }


  static private void load(File namingFile) {
    JSONArray array = PApplet.loadJSONArray(namingFile);
    for (int i = 0; i < array.size(); i++) {
      JSONObject obj = array.getJSONObject(i);
      WordList wl = new WordList(obj);
      wordLists.put(wl.name, wl);
    }
  }

  static Map<String, WordList> getWordLists() {
    if (wordLists == null) {
      wordLists = new HashMap<>();
      try {
        File namingFile = Base.getLibFile(FILENAME);
        load(namingFile);
      } catch (Exception e) {
        Messages.showWarning("Naming Error",
          "Could not load word lists from " + FILENAME, e);
      }
      File sketchbookFile = new File(Base.getSketchbookFolder(), FILENAME);
      if (sketchbookFile.exists()) {
        try {
          load(sketchbookFile);
        } catch (Exception e) {
          Messages.showWarning("Naming Error",
            "Error while reading " + FILENAME + " from sketchbook folder", e);
        }
      }
    }
    return wordLists;
  }


  static public String[] getOptions() {
    StringList outgoing = new StringList();
    outgoing.append(CLASSIC);
    for (String approach : getWordLists().keySet()) {
      outgoing.append(approach);
    }
    return outgoing.toArray();
  }
}