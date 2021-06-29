/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-2013 The Processing Foundation
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

import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import processing.app.Base;
import processing.app.Messages;
import processing.app.Preferences;
import processing.app.ui.About;
import processing.app.ui.Toolkit;


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

    desktop.setAboutHandler((event) -> {
      new About(null);
    });

    desktop.setPreferencesHandler((event) -> {
      base.handlePrefs();
    });

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
  }


  @Override
  public void setLookAndFeel() throws Exception {
    super.setLookAndFeel();

    String laf = UIManager.getLookAndFeel().getClass().getName();
    if ("com.apple.laf.AquaLookAndFeel".equals(laf)) {
      //setUIFont(new FontUIResource(".AppleSystemUIFont", Font.PLAIN, 12));
      // oh my god, the kerning, the tracking, my eyes...
      //setUIFont(new FontUIResource(".SFNS-Regular", Font.PLAIN, 13));
      //setUIFont(new FontUIResource(Toolkit.getSansFont(14, Font.PLAIN)));
      //setUIFont(new FontUIResource("Roboto-Regular", Font.PLAIN, 13));

    } else if ("org.violetlib.aqua.AquaLookAndFeel".equals(laf)) {
      Icon collapse = new VAquaTreeIcon(true);
      Icon open = new VAquaTreeIcon(false);
      Icon leaf = new VAquaEmptyIcon();
      UIManager.put("Tree.closedIcon", leaf);
      UIManager.put("Tree.openIcon", leaf);
      UIManager.put("Tree.collapsedIcon", open);
      UIManager.put("Tree.expandedIcon", collapse);
      UIManager.put("Tree.leafIcon", leaf);
    }
  }


  // Rewritten from https://stackoverflow.com/a/7434935
  static private void setUIFont(FontUIResource f) {
    for (Object key : UIManager.getLookAndFeelDefaults().keySet()) {
      Object value = UIManager.get(key);
      if (value instanceof FontUIResource) {
        UIManager.put(key, f);
      }
    }
  }


  public File getSettingsFolder() throws Exception {
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
  // home directory seems verboten on more recent macOS versions [fry 191008]
  protected String getLibraryFolder() throws FileNotFoundException {
    return System.getProperty("user.home") + "/Library";
  }


  // see notes on getLibraryFolder()
  protected String getDocumentsFolder() throws FileNotFoundException {
    return System.getProperty("user.home") + "/Documents";
  }


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


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  // VAQUA WORKAROUNDS FROM SAM


  /**
   * Spacer icon for macOS when using Vaqua.

   * Due to potential rendering issues, this small spacer is used
   * to ensure that rendering is stable while using Vaqua with non-standard
   * Swing components. Without this, some sizing calculations non-standard
   * components may fail or become unreliable.
   */
  class VAquaEmptyIcon implements Icon {
    private final int SIZE = 1;

    @Override
    public int getIconWidth() {
      return SIZE;
    }

    @Override
    public int getIconHeight() {
      return SIZE;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) { }
  }


  /**
   * Replacement tree icon for macOS when using Vaqua.
   *
   * Due to potential rendering issues with the regular tree icon set,
   * this replacement tree icon for macOS ensures stable rendering when using
   * Vaqua with non-standard swing components. Without this, some sizing
   * calculations within non-standard components may fail or become unreliable.
   */
  private class VAquaTreeIcon implements Icon {
    private final int SIZE = 12;
    private final boolean isOpen;

    /**
     * Create a new tree icon.
     *
     * @param newIsOpen Flag indicating if the icon should be in the open or closed state at
     *    construction. True if open false otherwise.
     */
    public VAquaTreeIcon(boolean newIsOpen) {
      isOpen = newIsOpen;
    }

    @Override
    public int getIconWidth() {
      return SIZE;
    }

    @Override
    public int getIconHeight() {
      return SIZE;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.setColor(Color.GRAY);

      g.drawLine(x + SIZE / 2 - 3, y + SIZE / 2, x + SIZE / 2 + 3, y + SIZE / 2);

      if (!isOpen) {
        g.drawLine(x + SIZE / 2, y + SIZE / 2 - 3, x + SIZE / 2, y + SIZE / 2 + 3);
      }
    }
  }
}
