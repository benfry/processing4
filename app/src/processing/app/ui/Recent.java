/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-22 The Processing Foundation
  Copyright (c) 2011-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import processing.app.Base;
import processing.app.Language;
import processing.app.Library;
import processing.app.Messages;
import processing.app.Mode;
import processing.app.Preferences;
import processing.core.PApplet;


// TODO this isn't pretty... probably better to do an internal instance fancy thing

// dealing with renaming
//   before sketch save/rename, remove it from the recent list
//   after sketch save/rename add it to the list
//   (this is the more straightforward model, otherwise has lots of weird edge cases)

public class Recent {
  static final String FILENAME = "recent.txt";
  static final String VERSION = "2";

  static Base base;
  static File file;
  static List<Record> records;
  /** actual menu used in the primary menu bar */
  static JMenu mainMenu;
  /** copy of the menu to use in the toolbar */
  static JMenu toolbarMenu;


  static public void init(Base b) {
    base = b;
    file = Base.getSettingsFile(FILENAME);
    mainMenu = new JMenu(Language.text("menu.file.recent"));
    toolbarMenu = new JMenu(Language.text("menu.file.open"));

    try {
      load();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  static protected void load() throws IOException {
    records = new ArrayList<>();
    if (file.exists()) {
      BufferedReader reader = PApplet.createReader(file);
      String version = reader.readLine();
      if (version != null && version.equals(VERSION)) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (new File(line).exists()) {  // don't add ghost entries
            records.add(new Record(line));
          } else {
            Messages.log("Ghost file found in recent: " + line);
          }
        }
      }
      reader.close();
    }
    updateMenu(mainMenu);
    updateMenu(toolbarMenu);
  }


  static protected void save() {
    // Need to check whether file exists and may already be writable.
    // Otherwise, setWritable() will actually return false.
    if (file.exists() && !file.canWrite()) {
      if (!file.setWritable(true, false)) {
        System.err.println("Warning: could not set " + file + " to writable");
      }
    }
    PrintWriter writer = PApplet.createWriter(file);
    writer.println(VERSION);
    for (Record record : records) {
      writer.println(record.path); // + "\t" + record.getState());
    }
    writer.flush();
    writer.close();
    updateMenu(mainMenu);
    updateMenu(toolbarMenu);
  }


  static public JMenu getMenu() {
    return mainMenu;
  }


  static public JMenu getToolbarMenu() {
    return toolbarMenu;
  }


  static private void updateMenu(JMenu menu) {
    menu.removeAll();
    String sketchbookPath = Base.getSketchbookFolder().getAbsolutePath();
    for (Record rec : records) {
      updateMenuRecord(menu, rec, sketchbookPath);
    }
  }


  static private void updateMenuRecord(JMenu menu, final Record rec,
                                       String sketchbookPath) {
    try {
      String recPath = new File(rec.getPath()).getParent();
      String purtyPath = null;

      if (recPath.startsWith(sketchbookPath)) {
        purtyPath = "sketchbook \u2192 " +
          recPath.substring(sketchbookPath.length() + 1);
      } else {
        List<Mode> modes = base.getModeList();
        for (Mode mode : modes) {
          File examplesFolder = mode.getExamplesFolder();
          String examplesPath = examplesFolder.getAbsolutePath();
          if (recPath.startsWith(examplesPath)) {
            String modePrefix = mode.getTitle() + " ";
            if (mode.getTitle().equals("Standard")) {
              modePrefix = "";  // "Standard examples" is dorky
            }
            purtyPath = modePrefix + "examples \u2192 " +
              recPath.substring(examplesPath.length() + 1);
            break;
          }

          if (mode.coreLibraries != null) {
            for (Library lib : mode.coreLibraries) {
              examplesFolder = lib.getExamplesFolder();
              examplesPath = examplesFolder.getAbsolutePath();
              if (recPath.startsWith(examplesPath)) {
                purtyPath = lib.getName() + " examples \u2192 " +
                  recPath.substring(examplesPath.length() + 1);
                break;
              }
            }
          }

          if (mode.contribLibraries != null) {
            for (Library lib : mode.contribLibraries) {
              examplesFolder = lib.getExamplesFolder();
              examplesPath = examplesFolder.getAbsolutePath();
              if (recPath.startsWith(examplesPath)) {
                purtyPath = lib.getName() + " examples \u2192 " +
                  recPath.substring(examplesPath.length() + 1);
                break;
              }
            }
          }
        }
      }
      if (purtyPath == null) {
        String homePath = System.getProperty("user.home");
        if (recPath.startsWith(homePath)) {
          // Not localized, but this is gravy. It'll work on OS X & EN Windows
          String desktopPath = homePath + File.separator + "Desktop";
          if (recPath.startsWith(desktopPath)) {
            purtyPath = "Desktop \u2192 " + recPath.substring(desktopPath.length() + 1);
          } else {
            //purtyPath = "\u2302 \u2192 " + recPath.substring(homePath.length() + 1);
            //purtyPath = "Home \u2192 " + recPath.substring(homePath.length() + 1);
            String userName = new File(homePath).getName();
            //purtyPath = "\u2302 " + userName + " \u2192 " + recPath.substring(homePath.length() + 1);
            purtyPath = userName + " \u2192 " + recPath.substring(homePath.length() + 1);
          }
        } else {
          purtyPath = recPath;
        }
      }

      JMenuItem item = new JMenuItem(purtyPath);
      item.addActionListener(e -> {
        // Base will call handle() (below) which will cause this entry to
        // be removed from the list and re-added to the end. If already
        // opened, Base will bring the window forward, and also call handle()
        // so that it's re-queued to the newest slot in the Recent menu.
        base.handleOpen(rec.path);
      });
      menu.insert(item, 0);

    } catch (Exception e) {
      // Strange things can happen... report them for the geeky and move on:
      // https://github.com/processing/processing/issues/2463
      e.printStackTrace();
    }
  }


  synchronized static public void remove(Editor editor) {
    int index = findRecord(editor.getSketch().getMainPath());
    if (index != -1) {
      records.remove(index);
    }
  }


  /**
   * Called by Base when a new sketch is opened, to add the sketch to the last
   * entry on the Recent queue. If the sketch is already in the list, it is
   * first removed so it doesn't show up multiple times.
   */
  synchronized static public void append(Editor editor) {
    if (!editor.getSketch().isUntitled()) {
      // If this sketch is already in the menu, remove it
      remove(editor);

      // If the list is full, remove the first entry
      if (records.size() == Preferences.getInteger("recent.count")) {
        records.remove(0);  // remove the first entry
      }

      records.add(new Record(editor));
      save();
    }
  }


  synchronized static public void rename(Editor editor, String oldPath) {
    if (records.size() == Preferences.getInteger("recent.count")) {
      records.remove(0);  // remove the first entry
    }
    int index = findRecord(oldPath);
    //check if record exists
    if (index != -1) {
      records.remove(index);
    }
    records.add(new Record(editor));
    save();
  }


  static int findRecord(String path) {
    for (int i = 0; i < records.size(); i++) {
      if (path.equals(records.get(i).path)) {
        return i;
      }
    }
    return -1;
  }


  static class Record {
    String path;  // if not loaded, this is non-null

    Record(String path) {
      this.path = path;
    }

    Record(Editor editor) {
      this(editor.getSketch().getMainPath());
    }

    /*
    String getName() {
      // Get the filename of the .pde (or .js or .py...)
      String name = path.substring(path.lastIndexOf(File.separatorChar) + 1);
      // Return the name with the extension removed
      return name.substring(0, name.indexOf('.'));
    }
    */

    String getPath() {
      return path;
    }
  }
}
