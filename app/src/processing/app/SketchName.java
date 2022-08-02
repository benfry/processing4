package processing.app;

import processing.core.PApplet;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


public class SketchName {
  static final String[] months = {
    "jan", "feb", "mar", "apr", "may", "jun",
    "jul", "aug", "sep", "oct", "nov", "dec"
  };

  static boolean breakTime = false;


  /**
   * Return a new File object for the next sketch directory.
   * The name of this directory will also be used as the sketch name.
   * (i.e. in Java Mode, dirname.pde will be created as the main tab)
   * If returning null, it's up to *this* class to report errors to the user.
   * @param parentDir Parent directory where the sketch folder will live
   * @return File object for safe new path, or null if there were problems
   */
  static File nextFolder(File parentDir) {
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
      Calendar cal = Calendar.getInstance();
      int day = cal.get(Calendar.DAY_OF_MONTH);  // 1..31
      int month = cal.get(Calendar.MONTH);  // 0..11
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
}