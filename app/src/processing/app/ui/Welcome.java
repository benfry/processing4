/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2015-19 The Processing Foundation

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

package processing.app.ui;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;

import processing.app.Base;
import processing.app.Platform;
import processing.app.Preferences;


public class Welcome {
  Base base;
  WebFrame view;


  public Welcome(Base base) throws IOException {
    this.base = base;

    JComponent panel = Box.createHorizontalBox();
    //panel.setBackground(new Color(245, 245, 245));
    panel.setBackground(Color.WHITE);
    panel.setOpaque(true);
    panel.setBorder(new EmptyBorder(15, 20, 15, 20));

    JCheckBox checkbox = new JCheckBox("Show this message on startup");
    // handles the Help menu invocation, and also the pref not existing
    checkbox.setSelected("true".equals(Preferences.get("welcome.four.show")));
    checkbox.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        Preferences.setBoolean("welcome.four.show", true);
      } else if (e.getStateChange() == ItemEvent.DESELECTED) {
        Preferences.setBoolean("welcome.four.show", false);
      }
    });
    panel.add(checkbox);

    panel.add(Box.createHorizontalGlue());

    JButton button = new JButton("Get Started");
    button.setFont(Toolkit.getSansFont(14, Font.PLAIN));
    button.addActionListener(e -> view.handleClose());
    panel.add(button);

    File indexFile = getIndexFile();
    if (indexFile == null) return;  // giving up; error already printed

    view = new WebFrame(getIndexFile(), 420, panel) {
      /*
      @Override
      public void handleSubmit(StringDict dict) {
        String sketchbookAction = dict.get("sketchbook", null);
        if ("create_new".equals(sketchbookAction)) {
          File folder = new File(Preferences.getSketchbookPath()).getParentFile();
          PApplet.selectFolder(Language.text("preferences.sketchbook_location.popup"),
                               "sketchbookCallback", folder,
                               this, this);
        }

//        // If un-checked, the key won't be in the dict, so null will be passed
//        boolean keepShowing = "on".equals(dict.get("show_each_time", null));
//        Preferences.setBoolean("welcome.show", keepShowing);
//        Preferences.save();
        handleClose();
      }
      */

      @Override
      public void handleLink(String link) {
        // The link will already have the full URL prefix
        /*
        if (link.endsWith("#sketchbook")) {
          File folder = new File(Preferences.getSketchbookPath()).getParentFile();
          ShimAWT.selectFolder(Language.text("preferences.sketchbook_location.popup"),
                               "sketchbookCallback", folder, this);
         */

        if (link.endsWith("#examples")) {
          base.getDefaultMode().showExamplesFrame();

        } else if (link.endsWith("#mouse")) {
          openExample("Basics/Input/Mouse2D/Mouse2D.pde");

        } else if (link.endsWith("#arctan")) {
          openExample("Basics/Math/Arctangent/Arctangent.pde");

        } else if (link.endsWith("#flocking")) {
          openExample("Topics/Simulate/Flocking/Flocking.pde");

        } else if (link.endsWith("#rotating")) {
          openExample("Demos/Graphics/RotatingArcs/RotatingArcs.pde");

        } else {
          super.handleLink(link);
        }
      }

      private void openExample(String examplePath) {
        File examplesFolder =
          Platform.getContentFile("modes/java/examples");
        File pdeFile = new File(examplesFolder, examplePath);
        base.handleOpen(pdeFile.getAbsolutePath());
      }

      @Override
      public void handleClose() {
        Preferences.setBoolean("welcome.four.seen", true);
        Preferences.save();
        super.handleClose();
      }
    };
    view.setVisible(true);
  }


//  /** Callback for the folder selector. */
//  public void sketchbookCallback(File folder) {
//    if (folder != null) {
//      if (base != null) {
//        base.setSketchbookFolder(folder);
//      }
//    }
//  }


  static private File getIndexFile() {
    String filename = "welcome/index.html";

    // version when running from IntelliJ for editing
    File htmlFile = new File("build/shared/lib/" + filename);
    if (htmlFile.exists()) {
      return htmlFile;
    }

    /* needs to be tested/updated for 4.0
    // processing/build/macosx/work/Processing.app/Contents/Java
    // version for Scott to use for OS X debugging
    htmlFile = Platform.getContentFile("../../../../../shared/lib/" + filename);
    if (htmlFile.exists()) {
      return htmlFile;
    }
    */

    try {
      return Base.getLibFile(filename);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }


  static public void main(String[] args) {
    Platform.init();
    try {
      Platform.setLookAndFeel();  // set font for checkbox and button
    } catch (Exception e) {
      e.printStackTrace();
    }
    Preferences.init();

    EventQueue.invokeLater(() -> {
      try {
        new Welcome(null);
      } catch (IOException e) {
        e.printStackTrace();
      }
    });
  }
}
