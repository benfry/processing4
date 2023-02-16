/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-2022 The Processing Foundation
  Copyright (c) 2008-2012 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License
  version 2, as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app.platform;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;

import javax.swing.JMenu;
import javax.swing.JMenuBar;

import processing.app.Base;
import processing.app.Messages;
import processing.app.ui.About;
import processing.core.PApplet;
import processing.data.StringList;


/**
 * Platform handler for macOS.
 */
public class MacPlatform extends DefaultPlatform {

  public void saveLanguage(String language) {
    String[] cmdarray = new String[]{
      "defaults", "write",
      System.getProperty("user.home") + "/Library/Preferences/org.processing.app",
      "AppleLanguages", "-array", language
    };
    try {
      Runtime.getRuntime().exec(cmdarray);
    } catch (IOException e) {
      Messages.log("Error saving platform language: " + e.getMessage());
    }
  }


  public void initBase(Base base) {
    super.initBase(base);

    final Desktop desktop = Desktop.getDesktop();

    System.setProperty("apple.laf.useScreenMenuBar", "true");

    // Set the menu bar to be used when nothing else is open.
    JMenuBar defaultMenuBar = new JMenuBar();
    JMenu fileMenu = base.initDefaultFileMenu();
    defaultMenuBar.add(fileMenu);
    desktop.setDefaultMenuBar(defaultMenuBar);

    desktop.setAboutHandler((event) -> new About(null));

    desktop.setPreferencesHandler((event) -> base.handlePrefs());

    desktop.setOpenFileHandler((event) -> {
      for (File file : event.getFiles()) {
        base.handleOpen(file.getAbsolutePath());
      }
    });

    desktop.setPrintFileHandler((event) -> {
      // TODO not yet implemented
    });

    desktop.setQuitHandler((event, quitResponse) -> {
      if (base.handleQuit()) {
        quitResponse.performQuit();
      } else {
        quitResponse.cancelQuit();
      }
    });

    desktop.setOpenURIHandler((event) -> {
      // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URI.html
//      URI uri = event.getURI();
      base.handleScheme(event.getURI().toString());
//      String location = uri.toString().substring(6);
//      if (location.length() > 0) {
//        base.handleLocation(location);
//      }
    });
  }


  public File getSettingsFolder() throws Exception {
    File override = Base.getSettingsOverride();
    if (override != null) {
      return override;
    }
    return new File(getLibraryFolder(), "Processing");
  }


  public File getDefaultSketchbookFolder() throws Exception {
    return new File(getDocumentsFolder(), "Processing");
  }


  public void openURL(String url) throws Exception {
    try {
      Desktop.getDesktop().browse(new URI(url));
    } catch (IOException e) {
      // Deal with a situation where the browser hangs on macOS
      // https://github.com/fathominfo/processing-p5js-mode/issues/4
      if (e.getMessage().contains("Error code: -600")) {
        throw new RuntimeException("Could not open the sketch, please restart your browser or computer");
      } else {
        throw e;
      }
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  // TODO I suspect this won't work much longer, since access to the user's
  //      home directory seems verboten on more recent macOS versions [fry 191008]
  //      However, anecdotally it seems that just using the name works,
  //      and the localization is handled transparently. [fry 220116]
  //      https://github.com/processing/processing4/issues/9
  protected String getLibraryFolder() throws FileNotFoundException {
    File folder = new File(System.getProperty("user.home"), "Library");
    if (!folder.exists()) {
      throw new FileNotFoundException("Folder missing: " + folder);
    }
    return folder.getAbsolutePath();
  }


  // TODO See above, and https://github.com/processing/processing4/issues/9
  protected String getDocumentsFolder() throws FileNotFoundException {
    File folder = new File(System.getProperty("user.home"), "Documents");
    if (!folder.exists()) {
      throw new FileNotFoundException("Folder missing: " + folder);
    }
    return folder.getAbsolutePath();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static private Boolean xcodeInstalled;

  static public boolean isXcodeInstalled() {
    if (xcodeInstalled == null) {
      // Note that xcode-select is *not* an xcrun tool: it's part of the OS.
      // pkgutil --file-info /usr/bin/xcode-select
      // https://stackoverflow.com/a/32752859/18247494
      StringList stdout = new StringList();
      StringList stderr = new StringList();
      int result = PApplet.exec(stdout, stderr, "/usr/bin/xcode-select", "-p");

      // Returns 0 if installed, 2 if not (-1 if exception)
      // http://stackoverflow.com/questions/15371925
      xcodeInstalled = (result == 0);
    }
    return xcodeInstalled;
  }


  static public void resetXcodeInstalled() {
    xcodeInstalled = null;  // give them another chance
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /*
  // Some of these are supposedly constants in com.apple.eio.FileManager,
  // however they don't seem to link properly from Eclipse.

  static final int kDocumentsFolderType =
    ('d' << 24) | ('o' << 16) | ('c' << 8) | 's';
  //static final int kPreferencesFolderType =
  //  ('p' << 24) | ('r' << 16) | ('e' << 8) | 'f';
  static final int kDomainLibraryFolderType =
    ('d' << 24) | ('l' << 16) | ('i' << 8) | 'b';
  static final short kUserDomain = -32763;


  // apple java extensions documentation
  // http://developer.apple.com/documentation/Java/Reference/1.5.0
  //   /appledoc/api/com/apple/eio/FileManager.html

  // carbon folder constants
  // http://developer.apple.com/documentation/Carbon/Reference
  //   /Folder_Manager/folder_manager_ref/constant_6.html#/
  //   /apple_ref/doc/uid/TP30000238/C006889

  // additional information found int the local file:
  // /System/Library/Frameworks/CoreServices.framework
  //   /Versions/Current/Frameworks/CarbonCore.framework/Headers/


  protected String getLibraryFolder() throws FileNotFoundException {
    return FileManager.findFolder(kUserDomain, kDomainLibraryFolderType);
  }


  protected String getDocumentsFolder() throws FileNotFoundException {
    return FileManager.findFolder(kUserDomain, kDocumentsFolderType);
  }
  */
}
