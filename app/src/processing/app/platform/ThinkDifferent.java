/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-2014 The Processing Foundation
  Copyright (c) 2007-2012 Ben Fry and Casey Reas

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

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import processing.app.Base;
import processing.app.Language;
import processing.app.ui.About;
import processing.app.ui.Toolkit;


/**
 * Deal with issues related to thinking differently. This handles the basic
 * Mac OS X menu commands (and apple events) for open, about, prefs, etc.
 *
 * As of 0140, this code need not be built on platforms other than OS X,
 * because of the new platform structure which isolates through reflection.
 *
 * Rewritten for 0232 to remove deprecation issues, per the message
 * <a href="http://lists.apple.com/archives/java-dev/2012/Jan/msg00101.html">here</a>.
 * (We're able to do this now because we're dropping older Java versions.)
 */
public class ThinkDifferent {

  static private ThinkDifferent adapter;


  static protected void init(final Base base) {
    final Desktop desktop = Desktop.getDesktop();

    if (adapter == null) {
      adapter = new ThinkDifferent();
    }

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

    // Set the menu bar to be used when nothing else is open.
    JMenuBar defaultMenuBar = new JMenuBar();
    JMenu fileMenu = buildFileMenu(base);
    defaultMenuBar.add(fileMenu);
    // This is kind of a gross way to do this, but the alternatives? Hrm.
    Base.defaultFileMenu = fileMenu;
    desktop.setDefaultMenuBar(defaultMenuBar);
  }


  /**
   * Gimpy file menu to be used on OS X when no sketches are open.
   */
  static protected JMenu buildFileMenu(final Base base) {
    JMenuItem item;
    JMenu fileMenu = new JMenu(Language.text("menu.file"));

    item = Toolkit.newJMenuItem(Language.text("menu.file.new"), 'N');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          base.handleNew();
        }
      });
    fileMenu.add(item);

    item = Toolkit.newJMenuItem(Language.text("menu.file.open"), 'O');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          base.handleOpenPrompt();
        }
      });
    fileMenu.add(item);

    item = Toolkit.newJMenuItemShift(Language.text("menu.file.sketchbook"), 'K');
    item.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        base.getNextMode().showSketchbookFrame();
      }
    });
    fileMenu.add(item);

    item = Toolkit.newJMenuItemShift(Language.text("menu.file.examples"), 'O');
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        base.thinkDifferentExamples();
      }
    });
    fileMenu.add(item);

    return fileMenu;
  }
}
