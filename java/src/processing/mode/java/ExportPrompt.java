/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org

Copyright (c) 2012-22 The Processing Foundation
Copyright (c) 2004-12 Ben Fry and Casey Reas
Copyright (c) 2001-04 Massachusetts Institute of Technology

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

package processing.mode.java;

import processing.app.Language;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.SketchException;
import processing.app.ui.ColorChooser;
import processing.core.PApplet;
import processing.data.StringDict;
import processing.data.StringList;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class ExportPrompt {
  // Can't be .windows because that'll be stripped off as a per-platform pref
  static final String EXPORT_VARIANTS = "export.application.variants";
  /*
  static final String EXPORT_PREFIX = "export.application.platform_";
  static final String EXPORT_MACOSX = EXPORT_PREFIX + "macosx";
  static final String EXPORT_WINDOWS = EXPORT_PREFIX + "windows";
  static final String EXPORT_LINUX = EXPORT_PREFIX + "linux";
  */

  final JButton exportButton = new JButton(Language.text("prompt.export"));
  final JButton cancelButton = new JButton(Language.text("prompt.cancel"));

  /*
  final JCheckBox windowsButton = new JCheckBox("Windows");
  final JCheckBox macosButton = new JCheckBox("Mac OS X");
  final JCheckBox linuxButton = new JCheckBox("Linux");
  */
  List<JCheckBox> variantButtons;

  final JavaEditor editor;

  static ExportPrompt inst;


  private ExportPrompt(JavaEditor editor) {
    this.editor = editor;

    String pref = Preferences.get(EXPORT_VARIANTS);
    if (pref == null) {
      pref = Platform.getVariant();  // just add myself
      Preferences.set(EXPORT_VARIANTS, pref);
    }
    StringList selectedVariants = new StringList(pref.split(","));

    variantButtons = new ArrayList<>();
    for (StringDict.Entry entry : Platform.getSupportedVariants().entries()) {
      String variant = entry.key;
      JCheckBox button = new JCheckBox(entry.value);  // variant name
      if (variant.startsWith("macos") && !Platform.isMacOS()) {
        button.setEnabled(false);
      } else {
        button.setSelected(selectedVariants.hasValue(variant));
      }
      button.setActionCommand(variant);
      button.addActionListener(e -> updateVariants());
      variantButtons.add(button);
      /*
      final String variant = entry.key;
      button.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          e.getActionCommand();
          toggleVariant(variant);
        }
      });
      */
    }
  }


  protected void updateVariants() {
    StringList list = new StringList();
    for (JCheckBox button : variantButtons) {
      if (button.isSelected()) {
        list.append(button.getActionCommand());
      }
    }
    Preferences.set(EXPORT_VARIANTS, list.join(","));
    exportButton.setEnabled(anyExportButton());
  }


  /*
  protected void toggleVariant(String variant) {
    String pref = Preferences.get(EXPORT_VARIANTS);
    StringList list = new StringList(pref.split(","));
    if (list.hasValue(variant)) {
      list.removeValue(variant);
    } else {
      list.append(variant);
    }
    pref = list.join(",");
    Preferences.set(EXPORT_VARIANTS, pref);
  }


  protected void updateExportButton() {
    exportButton.setEnabled(anyExportButton());
  }
  */


  protected boolean anyExportButton() {
    for (JCheckBox button : variantButtons) {
      if (button.isSelected()) {
        return true;
      }
    }
    return false;
  }


  static protected boolean trigger(JavaEditor editor) throws IOException, SketchException {
    if (inst == null) {
      inst = new ExportPrompt(editor);
    }
    return inst.trigger();
  }


  protected boolean trigger() throws IOException, SketchException {
    final JDialog dialog = new JDialog(editor, Language.text("export"), true);

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(Box.createVerticalStrut(6));

    JLabel label = new JLabel(Language.text("export.description"), SwingConstants.CENTER);
    label.setBorder(new EmptyBorder(0, 8, 0, 0));
    panel.add(label);
    panel.add(Box.createVerticalStrut(12));

    JPanel platformPanel = new JPanel();
    int half = (variantButtons.size() + 1) / 2;

    Box leftPlatforms = Box.createVerticalBox();
    for (int i = 0; i < half; i++) {
      leftPlatforms.add(variantButtons.get(i));
    }

    Box rightPlatforms = Box.createVerticalBox();
    for (int i = half; i < variantButtons.size(); i++) {
      rightPlatforms.add(variantButtons.get(i));
    }

    platformPanel.add(leftPlatforms);
    platformPanel.add(rightPlatforms);

    platformPanel.setBorder(new TitledBorder(Language.text("export.platforms")));
    platformPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.add(platformPanel);

    int divWidth = platformPanel.getPreferredSize().width;

//    Dimension labelDim = new Dimension(divWidth, label.getPreferredSize().height);
//    label.setPreferredSize(labelDim);
//    label.setMinimumSize(labelDim);
//    label.setMaximumSize(labelDim);

    final JCheckBox showStopButton = new JCheckBox(Language.text("export.options.show_stop_button"));
    showStopButton.setSelected(Preferences.getBoolean("export.application.stop"));
    showStopButton.addItemListener(e -> Preferences.setBoolean("export.application.stop", showStopButton.isSelected()));
    showStopButton.setEnabled(Preferences.getBoolean("export.application.present"));
    showStopButton.setBorder(new EmptyBorder(3, 13, 6, 13));

    final JCheckBox presentButton = new JCheckBox(Language.text("export.options.present"));
    presentButton.setSelected(Preferences.getBoolean("export.application.present"));
    presentButton.addItemListener(e -> {
      boolean sal = presentButton.isSelected();
      Preferences.setBoolean("export.application.present", sal);
      showStopButton.setEnabled(sal);
    });
    presentButton.setBorder(new EmptyBorder(3, 13, 3, 13));

    JPanel presentPanel = new JPanel();
    presentPanel.setLayout(new BoxLayout(presentPanel, BoxLayout.Y_AXIS));
    Box fullScreenBox = Box.createHorizontalBox();
    fullScreenBox.add(presentButton);

    fullScreenBox.add(new ColorPreference("run.present.bgcolor"));
    fullScreenBox.add(Box.createHorizontalStrut(10));
    fullScreenBox.add(Box.createHorizontalGlue());

    presentPanel.add(fullScreenBox);

    Box showStopBox = Box.createHorizontalBox();
    showStopBox.add(showStopButton);
    showStopBox.add(new ColorPreference("run.present.stop.color"));
    showStopBox.add(Box.createHorizontalStrut(10));
    showStopBox.add(Box.createHorizontalGlue());
    presentPanel.add(showStopBox);

    presentPanel.setBorder(new TitledBorder(Language.text("export.full_screen")));
    presentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.add(presentPanel);

    //

    JPanel embedPanel = new JPanel();
    embedPanel.setLayout(new BoxLayout(embedPanel, BoxLayout.Y_AXIS));

    String platformName = null;
    if (Platform.isMacOS()) {
      platformName = "macOS";
    } else if (Platform.isWindows()) {
      platformName = "Windows";
    } else if (Platform.isLinux()) {
      platformName = "Linux";
    }

    final boolean embed =
      Preferences.getBoolean("export.application.embed_java");
    final String warning1 =
      "<html><div width=\"" + divWidth + "\">"; //<font size=\"2\">";
    final String warning2a =
      "Embedding Java will make the " + platformName + " application " +
      "larger, but it will be far more likely to work. " +
      "Users on other platforms will need to ";
    final String warning2b =
      "Users will need to ";
    final String warning3 =
      "<a href=\"" + JavaBuild.JAVA_DOWNLOAD_URL + "\">" +
      "install OpenJDK " + PApplet.javaPlatform + "</a>.";

    // both are needed because they change as the user hits the checkbox
    final String embedWarning = warning1 + warning2a + warning3;
    final String nopeWarning = warning1 + warning2b + warning3;

    final JLabel warningLabel = new JLabel(embed ? embedWarning : nopeWarning);
    warningLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent event) {
        Platform.openURL(JavaBuild.JAVA_DOWNLOAD_URL);
      }
    });
    warningLabel.setBorder(new EmptyBorder(3, 13, 3, 13));
    warningLabel.putClientProperty("FlatLaf.styleClass", "medium");

    final JCheckBox embedJavaButton =
      new JCheckBox(Language.interpolate("export.include_java", platformName));
    embedJavaButton.setSelected(embed);
    embedJavaButton.addItemListener(e -> {
      boolean selected = embedJavaButton.isSelected();
      Preferences.setBoolean("export.application.embed_java", selected);
      if (selected) {
        warningLabel.setText(embedWarning);
      } else {
        warningLabel.setText(nopeWarning);
      }
      dialog.pack();
    });
    embedJavaButton.setBorder(new EmptyBorder(3, 13, 3, 13));

    embedPanel.add(embedJavaButton);
    embedPanel.add(warningLabel);
    embedPanel.setBorder(new TitledBorder(Language.text("export.embed_java")));
    panel.add(embedPanel);

    //

    if (Platform.isMacOS()) {
      JPanel signPanel = new JPanel();
      signPanel.setLayout(new BoxLayout(signPanel, BoxLayout.Y_AXIS));
      signPanel.setBorder(new TitledBorder(Language.text("export.code_signing")));

      // gatekeeper: http://support.apple.com/kb/ht5290
      // for developers: https://developer.apple.com/developer-id/
      final String APPLE_URL = "https://developer.apple.com/developer-id/";
      String thePain =
        //"<html><body><font size=2>" +
        "In recent versions of macOS, Apple has introduced the \u201CGatekeeper\u201D system, " +
        "which makes it more difficult to run applications like those exported from Processing. ";

      if (new File("/usr/bin/codesign_allocate").exists()) {
        thePain +=
          "This application will be \u201Cself-signed\u201D which means that Finder may report that the " +
          "application is from an \u201Cunidentified developer\u201D. If the application will not " +
          "run, try right-clicking the app and selecting Open from the pop-up menu. Or you can visit " +
          "System Preferences \u2192 Security & Privacy and select Allow apps downloaded from: anywhere. ";
      } else {
        thePain +=
          "Gatekeeper requires applications to be \u201Csigned\u201D, or they will be reported as damaged. " +
          "To prevent this message, install Xcode (and the Command Line Tools) from the App Store. ";
      }
      thePain +=
        "To avoid the messages entirely, manually code sign your app. " +
        "For more information: <a href=\"\">" + APPLE_URL + "</a>";

      // xattr -d com.apple.quarantine thesketch.app

      //signPanel.add(new JLabel(thePain));
      //JEditorPane area = new JEditorPane("text/html", thePain);
      //JTextPane area = new JEditorPane("text/html", thePain);

//      JTextArea area = new JTextArea(thePain);
//      area.setBackground(null);
//      area.setFont(new Font("Dialog", Font.PLAIN, 10));
//      area.setLineWrap(true);
//      area.setWrapStyleWord(true);
      // Are you f-king serious, Java API developers?
      // (Unless it's an HTML component, even with line wrap turned on,
      // getPreferredSize() will return the size for just a single line.)
//      JLabel area = new JLabel("<html><div width=\"" + divWidth + "\"><font size=\"2\">" + thePain + "</div></html>");
      JLabel area = new JLabel("<html><div width=\"" + divWidth + "\">" + thePain + "</div></html>");
      area.putClientProperty("FlatLaf.styleClass", "medium");

      area.setBorder(new EmptyBorder(3, 13, 3, 13));
//      area.setPreferredSize(new Dimension(embedPanel.getPreferredSize().width, 100));
//      area.setPreferredSize(new Dimension(300, 200));
      signPanel.add(area);
//      signPanel.add(Box.createHorizontalGlue());
      signPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

      area.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent event) {
        Platform.openURL("https://developer.apple.com/developer-id/");
        }
      });

      panel.add(signPanel);
    }

    //

    //String[] options = { Language.text("prompt.export"), Language.text("prompt.cancel") };
    final JButton[] options = { exportButton, cancelButton };

    final JOptionPane optionPane = new JOptionPane(panel,
      JOptionPane.PLAIN_MESSAGE,
      JOptionPane.YES_NO_OPTION,
      null,
      options,
      exportButton); //options[0]);

    dialog.setContentPane(optionPane);

    exportButton.addActionListener(e -> optionPane.setValue(exportButton));
    cancelButton.addActionListener(e -> optionPane.setValue(cancelButton));

    optionPane.addPropertyChangeListener(e -> {
      String prop = e.getPropertyName();

      if (dialog.isVisible() &&
        (e.getSource() == optionPane) &&
        (prop.equals(JOptionPane.VALUE_PROPERTY))) {
        // If you were going to check something before
        // closing the window, you'd do it here.
        dialog.setVisible(false);
      }
    });
    dialog.pack();
//    System.out.println("after pack: " + panel.getPreferredSize());
//    dialog.setSize(optionPane.getPreferredSize());
    dialog.setResizable(false);

    // Center the window in the middle of the editor
    Rectangle bounds = editor.getBounds();
    dialog.setLocation(bounds.x + (bounds.width - dialog.getSize().width) / 2,
      bounds.y + (bounds.height - dialog.getSize().height) / 2);
    dialog.setVisible(true);

    Object value = optionPane.getValue();
    if (value.equals(exportButton)) {
      return ((JavaMode) editor.getMode()).handleExportApplication(editor.getSketch());
    } else if (value.equals(cancelButton) || value.equals(-1)) {
      // closed window by hitting Cancel or ESC
      editor.statusNotice(Language.text("export.notice.exporting.cancel"));
    }
    return false;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class ColorPreference extends JPanel implements ActionListener {
    ColorChooser chooser;
    String prefName;

    public ColorPreference(String pref) {
      prefName = pref;

      //setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
      setPreferredSize(new Dimension(30, 20));
      setMaximumSize(new Dimension(30, 20));

      addMouseListener(new MouseAdapter() {
        public void mouseReleased(MouseEvent e) {
          Color color = Preferences.getColor(prefName);
          chooser = new ColorChooser(editor, true, color, Language.text("color_chooser.select"), ColorPreference.this);
          chooser.show();
        }
      });
    }

    public void paintComponent(Graphics g) {
      g.setColor(Preferences.getColor(prefName));
      Dimension size = getSize();
      g.fillRect(0, 0, size.width, size.height);
    }

    public void actionPerformed(ActionEvent e) {
      Color color = chooser.getColor();
      Preferences.setColor(prefName, color);
      //presentColorPanel.repaint();
      repaint();
      chooser.hide();
    }
  }


//  protected void selectColor(String prefName) {
//    Color color = Preferences.getColor(prefName);
//    final ColorChooser chooser = new ColorChooser(JavaEditor.this, true, color,
//                                            "Select", new ActionListener() {
//
//      @Override
//      public void actionPerformed(ActionEvent e) {
//        Preferences.setColor(prefName, c.getColor());
//      }
//    });
//  }
}