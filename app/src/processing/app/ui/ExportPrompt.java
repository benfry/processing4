/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org

Copyright (c) 2012-23 The Processing Foundation
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

package processing.app.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import processing.app.Language;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.platform.MacPlatform;

import processing.core.PApplet;
import processing.data.StringDict;
import processing.data.StringList;


public class ExportPrompt {
  static final String MACOS_EXPORT_WIKI =
    "https://github.com/processing/processing4/wiki/Exporting-Applications#macos";
  static public final String EXPORT_VARIANTS = "export.application.variants";

  static public final String JAVA_DOWNLOAD_URL = "https://adoptium.net/";

  final JButton exportButton = new JButton(Language.text("prompt.export"));
  final JButton cancelButton = new JButton(Language.text("prompt.cancel"));

  List<JCheckBox> variantButtons;

  final Editor editor;
  final Runnable callback;

//  static ExportPrompt inst;


  public ExportPrompt(Editor editor, Runnable callback) {
    this.editor = editor;
    this.callback = callback;

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


  protected boolean anyExportButton() {
    for (JCheckBox button : variantButtons) {
      if (button.isSelected()) {
        return true;
      }
    }
    return false;
  }


  public void trigger() {
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
      "<html><div width=\"" + divWidth + "\">";
    final String warning2a =
      "This option will make the " + platformName + " application " +
      "larger, but it will be far more likely to work. " +
      "Users on other platforms will need to ";
    final String warning2b =
      "Users will need to ";
    final String warning3 =
      "<a href=\"" + JAVA_DOWNLOAD_URL + "\">" +
      "install OpenJDK " + PApplet.javaPlatform + "</a>.";

    // both are needed because they change as the user hits the checkbox
    final String embedWarning = warning1 + warning2a + warning3;
    final String nopeWarning = warning1 + warning2b + warning3;

    final JLabel warningLabel = new JLabel(embed ? embedWarning : nopeWarning);
    warningLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent event) {
        Platform.openURL(JAVA_DOWNLOAD_URL);
      }
    });
    warningLabel.setBorder(new EmptyBorder(3, 13, 3, 13));
//    warningLabel.putClientProperty("FlatLaf.styleClass", "medium");

    final JCheckBox embedJavaButton =
      new JCheckBox(Language.interpolate("export.include_java", Platform.getPrettyName()));
      //new JCheckBox(Language.interpolate("export.include_java", platformName));
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

      String thePain =
        "Applications on macOS must be “signed” and “notarized,”" +
        "or they will be reported as damaged or unsafe. ";

      //if (false && new File("/usr/bin/codesign_allocate").exists()) {
      if (MacPlatform.isXcodeInstalled()) {
        thePain += "<br/>" +
          "This application will be “self-signed” which means that " +
          "macOS may complain that it comes from an unidentified developer. " +
          "If the application will not run, try right-clicking the app and " +
          "selecting Open from the pop-up menu. " +
          "More details at the <a href=\"\">Exporting Applications</a> wiki page.";
      } else {
        thePain +=
          "To create a working macOS application, " +
          "<a href=\"\">click here</a> to install " +
          "the Command Line Tools from Apple.";
      }

      // Are you f-king serious, Java API developers?
      // (Unless it's an HTML component, even with line wrap turned on,
      // getPreferredSize() will return the size for just a single line.)
//      JLabel area = new JLabel("<html><div width=\"" + divWidth + "\"><font size=\"2\">" + thePain + "</div></html>");
      JLabel area = new JLabel("<html><div width=\"" + divWidth + "\">" + thePain + "</div></html>");
//      area.putClientProperty("FlatLaf.styleClass", "medium");

      area.setBorder(new EmptyBorder(3, 13, 3, 13));
      // Using area.setPreferredSize() here doesn't help,
      // but setting the div width in CSS above worked.
      signPanel.add(area);
      signPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

      area.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent event) {
          if (MacPlatform.isXcodeInstalled()) {
            Platform.openURL(MACOS_EXPORT_WIKI);

          } else {
            // Launch the process asynchronously
            PApplet.exec("xcode-select", "--install");
            // Reset the installed state so that the message will change.
            MacPlatform.resetXcodeInstalled();
            // Close the window so that we can rebuild it with different text
            // once they've finished installing the Command Line Tools.
            dialog.setVisible(false);
          }
        }
      });

      panel.add(signPanel);
    }

    //

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
    dialog.setResizable(false);

    // Center the window in the middle of the editor
    Rectangle bounds = editor.getBounds();
    dialog.setLocation(bounds.x + (bounds.width - dialog.getSize().width) / 2,
      bounds.y + (bounds.height - dialog.getSize().height) / 2);
    dialog.setVisible(true);

    Object value = optionPane.getValue();
    if (value.equals(exportButton)) {
      //return ((JavaMode) editor.getMode()).handleExportApplication(editor.getSketch());
      callback.run();
    } else if (value.equals(cancelButton) || value.equals(-1)) {
      // closed window by hitting Cancel or ESC
      editor.statusNotice(Language.text("export.notice.exporting.cancel"));
    }
//    return false;
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
}