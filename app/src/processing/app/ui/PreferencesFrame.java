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

package processing.app.ui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

import javax.swing.*;
import javax.swing.border.*;

import com.formdev.flatlaf.FlatClientProperties;

import processing.app.Base;
import processing.app.Language;
import processing.app.Messages;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.SketchName;
import processing.awt.ShimAWT;

import processing.core.PApplet;
import processing.data.StringList;


/**
 * Creates the window for modifying preferences.
 */
public class PreferencesFrame {
  JFrame frame;

  static final int ROW_H_GAP = 5;
  static final int ROW_V_GAP = 3;

  static final Integer[] FONT_SIZES = { 10, 12, 14, 18, 24, 36, 48 };

  JTextField sketchbookLocationField;
  JComboBox<String> namingSelectionBox;

  JTextField presentColor;
  //JCheckBox editorAntialiasBox;
//  JCheckBox deletePreviousBox;
  JCheckBox memoryOverrideBox;
  JTextField memoryField;
  JCheckBox checkUpdatesBox;
  JComboBox<Integer> fontSizeField;
  JComboBox<Integer> consoleFontSizeField;
  JCheckBox inputMethodBox;
//  JLabel inputRestartLabel;
  JCheckBox autoAssociateBox;

  ColorChooser selector;

  JCheckBox errorCheckerBox;
  JCheckBox warningsCheckerBox;
  JCheckBox codeCompletionBox;
  JCheckBox importSuggestionsBox;

  JComboBox<String> zoomSelectionBox;
  JCheckBox zoomAutoBox;
//  JLabel zoomRestartLabel;

  JCheckBox hidpiDisableBox;
//  JLabel hidpiRestartLabel;
  JCheckBox syncSketchNameBox;

  JComboBox<String> displaySelectionBox;
  JComboBox<String> languageSelectionBox;
  Map<String, String> languageToCode = new HashMap<>();

  int displayCount;
  int defaultDisplayNum;

  String[] monoFontFamilies;
  JComboBox<String> fontSelectionBox;

  Map<String, Boolean> restartMap = new HashMap<>();
  JLabel restartLabel;

  JButton okButton;

  /** Base object so that updates can be applied to the list of editors. */
  Base base;


  public PreferencesFrame(Base base) {
    this.base = base;

    frame = new JFrame(Language.text("preferences"));
    Container pain = frame.getContentPane();

    //layout = new GroupLayout(pain);
    //layout.setAutoCreateGaps(true);
    //layout.setAutoCreateContainerGaps(true);
    //pain.setLayout(layout);

    JLabel sketchbookLocationLabel;
//    JLabel languageRestartLabel;
    JButton browseButton; //, button2;


    // Sketchbook folder:
    // [...............................]  [ Browse ]

    sketchbookLocationLabel = new JLabel(Language.text("preferences.sketchbook_location"));

    sketchbookLocationField = new JTextField(25);
    /*
    sketchbookLocationField.putClientProperty(
      FlatClientProperties.TEXT_FIELD_TRAILING_ICON,
      UIManager.getIcon("Tree.closedIcon")
    );
    sketchbookLocationField.setEditable(false);
    */

    //browseButton = new JButton(Language.getPrompt("browse"));
    browseButton = new JButton(UIManager.getIcon("Tree.closedIcon"));
    browseButton.addActionListener(e -> {
      File defaultLocation = new File(sketchbookLocationField.getText());
      ShimAWT.selectFolder(Language.text("preferences.sketchbook_location.popup"),
                           "sketchbookCallback", defaultLocation,
                           PreferencesFrame.this);
    });

    sketchbookLocationField.putClientProperty(
      FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT,
      browseButton
    );


    // Sketch Naming: [ Classic (sketch_220822a) ]

    JLabel namingLabel = new JLabel(Language.text("preferences.sketch_naming"));
    namingSelectionBox = new JComboBox<>(SketchName.getOptions());


    // Language: [ English ] (requires restart of Processing)

    JLabel languageLabel = new JLabel(Language.text("preferences.language"));
    languageSelectionBox = new JComboBox<>();

    Map<String, String> languages = Language.getLanguages();
    String[] languageSelection = new String[languages.size()];
    StringList codeList = new StringList(languages.keySet());

    // Build a map from the display names of the languages to their codes
    for (String code : codeList) {
      languageToCode.put(languages.get(code), code);
    }

    // Set the current language as the first/default choice
    String currentCode = Language.getLanguage();
    languageSelection[0] = languages.get(currentCode);

    // Remove that language from the list of other possible choices
    codeList.removeValue(currentCode);
    // Sort the language list based on its code; this avoids showing preference
    // to any language, and keeps related variants together (zh_CN and zh_TW).
    codeList.sort();

    // Start counting from 1 to fill out the rest of the list
    int i = 1;
    for (String code : codeList) {
      languageSelection[i++] = languages.get(code);
    }
    languageSelectionBox.setModel(new DefaultComboBoxModel<>(languageSelection));
    languageSelectionBox.addItemListener(e -> updateRestart("language", languageSelectionBox.getSelectedIndex() != 0));
    languageSelectionBox.setRenderer(new LanguageRenderer());


    // Editor and console font [ Source Code Pro ]

    JLabel fontLabel = new JLabel(Language.text("preferences.editor_and_console_font"));
    final String fontTip = "<html>" + Language.text("preferences.editor_and_console_font.tip");
    fontLabel.setToolTipText(fontTip);
    // get a wide name in there before getPreferredSize() is called
    fontSelectionBox = new JComboBox<>(new String[] { Toolkit.getMonoFontName() });
    fontSelectionBox.setToolTipText(fontTip);
    fontSelectionBox.setEnabled(false);  // don't enable until fonts are loaded


    // Editor font size [ 12 ]  Console font size [ 10 ]

    JLabel fontSizeLabel = new JLabel(Language.text("preferences.editor_font_size"));
    fontSizeField = new JComboBox<>(FONT_SIZES);
    fontSizeField.setSelectedItem(Preferences.getInteger("editor.font.size"));

    JLabel consoleFontSizeLabel = new JLabel(Language.text("preferences.console_font_size"));
    consoleFontSizeField = new JComboBox<>(FONT_SIZES);
    consoleFontSizeField.setSelectedItem(Preferences.getInteger("console.font.size"));

    // Sizing is screwed up on macOS, bug has been open since 2017
    // https://github.com/processing/processing4/issues/232
    // https://bugs.openjdk.java.net/browse/JDK-8179076
    fontSizeField.setEditable(true);
    consoleFontSizeField.setEditable(true);


    // Interface scale: [ 100% ] (requires restart of Processing)

//    zoomRestartLabel = new JLabel(Language.text("preferences.requires_restart"));

    JLabel zoomLabel = new JLabel(Language.text("preferences.interface_scale"));

    zoomAutoBox = new JCheckBox(Language.text("preferences.interface_scale.auto"));
    zoomAutoBox.addChangeListener(e -> {
      zoomSelectionBox.setEnabled(!zoomAutoBox.isSelected());
      updateZoomRestartRequired();
    });

    zoomSelectionBox = new JComboBox<>();
    zoomSelectionBox.setModel(new DefaultComboBoxModel<>(Toolkit.zoomOptions.toArray()));
    zoomSelectionBox.addActionListener(e -> updateZoomRestartRequired());


    // [ ] Disable HiDPI Scaling (requires restart)

    hidpiDisableBox = new JCheckBox("Disable HiDPI Scaling");
//    hidpiDisableBox.setVisible(false);  // only for Windows
//    hidpiRestartLabel = new JLabel(Language.text("preferences.requires_restart"));
//    hidpiRestartLabel.setVisible(false);
//    hidpiDisableBox.addChangeListener(e -> hidpiRestartLabel.setVisible(hidpiDisableBox.isSelected() != Splash.getDisableHiDPI()));
    hidpiDisableBox.addChangeListener(e -> updateRestart("hidpi", hidpiDisableBox.isSelected() != Splash.getDisableHiDPI()));


    // [ ] Keep sketch name and main tab name in sync
    syncSketchNameBox =
      new JCheckBox("Keep sketch name and main tab in sync");
    syncSketchNameBox.setToolTipText("<html>" +
      "This removes the requirement for the sketch name to be<br>" +
      "the same as the main tab, which makes it easier to use<br>" +
      "Processing sketches with version control systems like Git.<br>" +
      "This is experimental: save often and report any issues!");
    //syncSketchNameBox.setVerticalTextPosition(SwingConstants.TOP);


    // Colors

    JLabel backgroundColorLabel = new JLabel(Language.text("preferences.background_color"));

    final String colorTip = "<html>" + Language.text("preferences.background_color.tip");
    backgroundColorLabel.setToolTipText(colorTip);

    presentColor = new JTextField("      ");
    presentColor.setOpaque(true);
    presentColor.setEnabled(true);
    presentColor.setEditable(false);
    Border cb = new CompoundBorder(BorderFactory.createMatteBorder(1, 1, 0, 0, new Color(195, 195, 195)),
                                   BorderFactory.createMatteBorder(0, 0, 1, 1, new Color(54, 54, 54)));
    presentColor.setBorder(cb);
    presentColor.setBackground(Preferences.getColor("run.present.bgcolor"));

    /*
    presentColorHex = new JTextField(6);
    presentColorHex.setText(Preferences.get("run.present.bgcolor").substring(1));
    presentColorHex.getDocument().addDocumentListener(new DocumentListener() {

      @Override
      public void removeUpdate(DocumentEvent e) {
        final String colorValue = presentColorHex.getText().toUpperCase();
        if (colorValue.length() == 7 && (colorValue.startsWith("#")))
          EventQueue.invokeLater(() -> presentColorHex.setText(colorValue.substring(1)));
        if (colorValue.length() == 6 &&
            colorValue.matches("[0123456789ABCDEF]*")) {
          presentColor.setBackground(new Color(PApplet.unhex(colorValue)));
          if (!colorValue.equals(presentColorHex.getText()))
            EventQueue.invokeLater(() -> presentColorHex.setText(colorValue));
        }
      }

      @Override
      public void insertUpdate(DocumentEvent e) {
        final String colorValue = presentColorHex.getText().toUpperCase();
        if (colorValue.length() == 7 && (colorValue.startsWith("#")))
          EventQueue.invokeLater(() -> presentColorHex.setText(colorValue.substring(1)));
        if (colorValue.length() == 6
            && colorValue.matches("[0123456789ABCDEF]*")) {
          presentColor.setBackground(new Color(PApplet.unhex(colorValue)));
          if (!colorValue.equals(presentColorHex.getText()))
            EventQueue.invokeLater(() -> presentColorHex.setText(colorValue));
        }
      }

      @Override public void changedUpdate(DocumentEvent e) {}
    });
    */

    selector = new ColorChooser(frame, false,
                                Preferences.getColor("run.present.bgcolor"),
                                Language.text("prompt.ok"), e -> {
      String colorValue = selector.getHexColor().substring(1);
      presentColor.setBackground(new Color(PApplet.unhex(colorValue)));
      selector.hide();
    });

    presentColor.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        selector.show();
      }
    });

    //JLabel hashLabel = new JLabel("#");


    // [ ] Use smooth text in editor window

    //editorAntialiasBox = new JCheckBox(Language.text("preferences.use_smooth_text"));


    // [ ] Enable complex text input (for Japanese et al, requires restart)

    inputMethodBox =
      new JCheckBox(Language.text("preferences.enable_complex_text"));
    inputMethodBox.setToolTipText("<html>" + Language.text("preferences.enable_complex_text.tip"));

    /*
    JLabel inputMethodExample =
      new JLabel("(" + Language.text("preferences.enable_complex_text_input_example") + ")");
    inputMethodExample.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        Platform.openURL("https://en.wikipedia.org/wiki/CJK_characters");
      }

      public void mouseEntered(MouseEvent e) {
        inputMethodExample.setForeground(Theme.getColor("laf.accent.color"));
      }

      // Set the text back to black when the mouse is outside
      public void mouseExited(MouseEvent e) {
        inputMethodExample.setForeground(sketchbookLocationLabel.getForeground());
      }
    });
//    inputRestartLabel = new JLabel(Language.text("preferences.requires_restart"));
//    inputRestartLabel.setVisible(false);
    */

//    inputMethodBox.addChangeListener(e -> inputRestartLabel.setVisible(inputMethodBox.isSelected() != Preferences.getBoolean("editor.input_method_support")));
    inputMethodBox.addChangeListener(e -> updateRestart("input_method", inputMethodBox.isSelected() != Preferences.getBoolean("editor.input_method_support")));


    // [ ] Continuously check for errors - PDE X

    errorCheckerBox =
      new JCheckBox(Language.text("preferences.continuously_check"));
    errorCheckerBox.addItemListener(e -> warningsCheckerBox.setEnabled(errorCheckerBox.isSelected()));


    // [ ] Show Warnings - PDE X

    warningsCheckerBox =
      new JCheckBox(Language.text("preferences.show_warnings"));


    // [ ] Enable Code Completion - PDE X

    codeCompletionBox =
      new JCheckBox(Language.text("preferences.code_completion") +
                    " Ctrl-" + Language.text("preferences.cmd_space"));


    // [ ] Show import suggestions - PDE X

    importSuggestionsBox =
      new JCheckBox(Language.text("preferences.suggest_imports"));


    // [ ] Increase maximum available memory to [______] MB

    memoryOverrideBox = new JCheckBox(Language.text("preferences.increase_max_memory"));
    memoryField = new JTextField(4);
    memoryOverrideBox.addChangeListener(e -> memoryField.setEnabled(memoryOverrideBox.isSelected()));
    JLabel mbLabel = new JLabel("MB");


    // [ ] Delete previous application folder on export

//    deletePreviousBox =
//      new JCheckBox(Language.text("preferences.delete_previous_folder_on_export"));


    // [ ] Check for updates on startup

    checkUpdatesBox =
      new JCheckBox(Language.text("preferences.check_for_updates_on_startup"));


    // Run sketches on display [  1 ]

    JLabel displayLabel = new JLabel(Language.text("preferences.run_sketches_on_display"));
    final String tip = "<html>" + Language.text("preferences.run_sketches_on_display.tip");
    displayLabel.setToolTipText(tip);
    displaySelectionBox = new JComboBox<>();
    updateDisplayList();  // needs to happen here for getPreferredSize()


    // [ ] Automatically associate .pde files with Processing

    autoAssociateBox =
      new JCheckBox(Language.text("preferences.automatically_associate_pde_files"));
//    autoAssociateBox.setVisible(false);


    // More preferences are in the ...

    final JLabel morePreferenceLabel = new JLabel(Language.text("preferences.file"));
    morePreferenceLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    morePreferenceLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        // Starting in 4.0.1, open the Wiki page about the prefs
        Platform.openURL("https://github.com/processing/processing4/wiki/Preferences");
      }

      // Light this up in blue like a hyperlink
      public void mouseEntered(MouseEvent e) {
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        morePreferenceLabel.setForeground(Theme.getColor("laf.accent.color"));
      }

      // Set the text back to black when the mouse is outside
      public void mouseExited(MouseEvent e) {
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        morePreferenceLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
      }
    });

    JLabel preferencePathLabel = new JLabel(Preferences.getPreferencesPath());
    final JLabel clickable = preferencePathLabel;
    preferencePathLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        Platform.openFolder(Base.getSettingsFolder());
      }

      // Light this up in blue like a hyperlink
      public void mouseEntered(MouseEvent e) {
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clickable.setForeground(Theme.getColor("laf.accent.color"));
      }

      // Set the text back to black when the mouse is outside
      public void mouseExited(MouseEvent e) {
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        clickable.setForeground(UIManager.getColor("Label.foreground"));
      }
    });

    JLabel preferenceHintLabel = new JLabel(Language.text("preferences.file.hint"));
    //preferenceHintLabel.setForeground(Color.gray);
    preferenceHintLabel.setEnabled(false);


    // [  OK  ] [ Cancel ]

    restartLabel = new JLabel(Language.text("preferences.restart_required"));

    okButton = new JButton(Language.getPrompt("ok"));
    okButton.addActionListener(e -> {
      applyFrame();
      disposeFrame();
    });

    JButton cancelButton = new JButton(Language.getPrompt("cancel"));
    cancelButton.addActionListener(e -> disposeFrame());

    Box axis = Box.createVerticalBox();

    addRow(axis, sketchbookLocationLabel, sketchbookLocationField);

    addRow(axis, namingLabel, namingSelectionBox);

    //

    JPanel layoutPanel = new JPanel();
    layoutPanel.setBorder(new TitledBorder("Interface and Fonts"));
    layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));

    addRow(layoutPanel, fontLabel, fontSelectionBox);

    addRow(layoutPanel, fontSizeLabel, fontSizeField,
                        //Box.createHorizontalStrut(H_GAP),  // uglier
                        consoleFontSizeLabel, consoleFontSizeField);

    //addRow(layoutPanel, zoomLabel, zoomAutoBox, zoomSelectionBox, zoomRestartLabel);
    addRow(layoutPanel, zoomLabel, zoomAutoBox, zoomSelectionBox);

    if (Platform.isWindows()) {
      //addRow(layoutPanel, hidpiDisableBox, hidpiRestartLabel);
      addRow(layoutPanel, hidpiDisableBox);
    }

    axis.add(layoutPanel);

    //

//    JPanel languagePanel = new JPanel();
//    languagePanel.setBorder(new TitledBorder("Language"));
//    languagePanel.setLayout(new BoxLayout(languagePanel, BoxLayout.Y_AXIS));

//    //addRow(layoutPanel, languageLabel, languageSelectionBox, languageRestartLabel);
//    addRow(layoutPanel, languageLabel, languageSelectionBox);
//    //addRow(layoutPanel, inputMethodBox, inputMethodExample, inputRestartLabel);
//    addRow(layoutPanel, inputMethodBox, inputMethodExample);

    addRow(layoutPanel, languageLabel, languageSelectionBox, inputMethodBox);

//    axis.add(languagePanel);

    //

    JPanel codingPanel = new JPanel();
    codingPanel.setBorder(new TitledBorder("Coding"));
    codingPanel.setLayout(new BoxLayout(codingPanel, BoxLayout.Y_AXIS));

    addRow(codingPanel, errorCheckerBox, warningsCheckerBox);
    addRow(codingPanel, codeCompletionBox, importSuggestionsBox);

    axis.add(codingPanel);

    //

    JPanel runningPanel = new JPanel();
    runningPanel.setBorder(new TitledBorder("Running"));
    runningPanel.setLayout(new BoxLayout(runningPanel, BoxLayout.Y_AXIS));

    addRow(runningPanel, displayLabel, displaySelectionBox);
    addRow(runningPanel, backgroundColorLabel, presentColor);
    addRow(runningPanel, memoryOverrideBox, memoryField, mbLabel);

    axis.add(runningPanel);

    //

//    addRow(axis, deletePreviousBox);
    addRow(axis, checkUpdatesBox);
    addRow(axis, syncSketchNameBox);

    if (Platform.isWindows()) {
      addRow(axis, autoAssociateBox);
    }

    // Put these in a separate container so there's no extra gap
    // between the rows.
    Box blurb = Box.createVerticalBox();
    blurb.add(morePreferenceLabel);
    blurb.add(preferencePathLabel);
    blurb.add(preferenceHintLabel);
    addRow(axis, blurb);

    //JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    Box row = Box.createHorizontalBox();
    row.add(Box.createHorizontalStrut(ROW_H_GAP));
    row.add(restartLabel);
    row.add(Box.createHorizontalGlue());
    row.add(okButton);  // buttonWidth
    row.add(Box.createHorizontalStrut(ROW_H_GAP));
    row.add(cancelButton);  // buttonWidth
    axis.add(row);

    axis.setBorder(new EmptyBorder(13, 13, 13, 13));
    pain.add(axis);

    // closing the window is same as hitting cancel button
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        disposeFrame();
      }
    });

    ActionListener disposer = actionEvent -> disposeFrame();
    Toolkit.registerWindowCloseKeys(frame.getRootPane(), disposer);

    // for good measure, and set link/highlight colors
    updateTheme();

    // finishing up
    Toolkit.setIcon(frame);
    frame.setResizable(false);
    frame.pack();
    frame.setLocationRelativeTo(null);

    // handle window closing commands for ctrl/cmd-W or hitting ESC.
    pain.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        KeyStroke wc = Toolkit.WINDOW_CLOSE_KEYSTROKE;
        if ((e.getKeyCode() == KeyEvent.VK_ESCAPE) ||
            (KeyStroke.getKeyStrokeForEvent(e).equals(wc))) {
          disposeFrame();
        }
      }
    });
  }


  static private void addRow(Container axis, Component... components) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, ROW_H_GAP, ROW_V_GAP));
    for (Component comp : components) {
      row.add(comp);
    }
    axis.add(row);
  }


  private void updateRestart(String key, boolean value) {
    restartMap.put(key, value);
    restartLabel.setVisible(restartMap.containsValue(true));
  }


  private void updateZoomRestartRequired() {
    // TODO If this is too wide for the window, it may not appear.
    //      Redo layout in the window on change to be sure.
    //      May cause window to resize but need the message. [fry 220502]
    //zoomRestartLabel.setVisible(
    updateRestart("zoom",
      zoomAutoBox.isSelected() != Preferences.getBoolean("editor.zoom.auto") ||
      !Preferences.get("editor.zoom").equals(String.valueOf(zoomSelectionBox.getSelectedItem()))
    );
  }


  class LanguageRenderer extends JLabel implements ListCellRenderer<String> {
    final int fontSize = languageSelectionBox.getFont().getSize();
    final Font sansFont = Toolkit.getSansFont(fontSize, Font.PLAIN);
    final Font fallbackFont = new Font("Dialog", Font.PLAIN,fontSize);

    @Override
    public Component getListCellRendererComponent(JList<? extends String> list, String text, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
      if (sansFont.canDisplayUpTo(text) == -1) {
        // if the sans font can display the chars, use it
        setFont(sansFont);
      } else {
        // otherwise, use the fallback font (Dialog)
        setFont(fallbackFont);
      }
      setText(text);
      return this;
    }
  }


  /** Callback for the folder selector. */
  public void sketchbookCallback(File file) {
    if (file != null) {  // null if cancel or closed
      sketchbookLocationField.setText(file.getAbsolutePath());
    }
  }


  /** Close the window after an OK or Cancel. */
  protected void disposeFrame() {
    frame.dispose();
  }


  /**
   * Change internal settings based on what was chosen in the prefs,
   * then send a message to the editor saying that it's time to do the same.
   */
  protected void applyFrame() {
//    Preferences.setBoolean("editor.smooth", //$NON-NLS-1$
//                           editorAntialiasBox.isSelected());

//    Preferences.setBoolean("export.delete_target_folder", //$NON-NLS-1$
//                           deletePreviousBox.isSelected());

    // if the sketchbook path has changed, rebuild the menus
    String oldPath = Preferences.getSketchbookPath();
    String newPath = sketchbookLocationField.getText();
    if (!newPath.equals(oldPath)) {
      base.setSketchbookFolder(new File(newPath));
    }

    Preferences.set("sketch.name.approach", (String) namingSelectionBox.getSelectedItem());

//    setBoolean("editor.external", externalEditorBox.isSelected());
    Preferences.setBoolean("update.check", checkUpdatesBox.isSelected()); //$NON-NLS-1$

    // Save Language
    /*
    Map<String, String> languages = Language.getLanguages();
    String language = null;
    for (Map.Entry<String, String> lang : languages.entrySet()) {
      if (lang.getValue().equals(String.valueOf(languageSelectionBox.getSelectedItem()))) {
        language = lang.getKey().trim().toLowerCase();
        break;
      }
    }
    */
    // first entry is always the default language
    if (languageSelectionBox.getSelectedIndex() != 0) {
      /*
      String languageCode =
        Language.nameToCode(String.valueOf(languageSelectionBox.getSelectedItem()));
      if (!Language.getLanguage().equals(languageCode)) {
        Language.saveLanguage(languageCode);
      }
      */
      Language.saveLanguage(languageToCode.get((String) languageSelectionBox.getSelectedItem()));
    }

    // The preference will have already been reset when the window was created
    if (displaySelectionBox.isEnabled()) {
      int oldDisplayNum = Preferences.getInteger("run.display");
      int displayNum = -1;  // use the default display
      for (int d = 0; d < displaySelectionBox.getItemCount(); d++) {
        if (displaySelectionBox.getSelectedIndex() == d) {
          if (d == defaultDisplayNum-1) {
            // if it's the default display, store -1 instead of its index,
            // because displays can get renumbered when others are attached
            displayNum = -1;
          } else {
            displayNum = d + 1;
          }
        }
      }
      if (displayNum != oldDisplayNum) {
        Preferences.setInteger("run.display", displayNum); //$NON-NLS-1$
        // Reset the location of the sketch, the window has changed
        for (Editor editor : base.getEditors()) {
          editor.setSketchLocation(null);
        }
      }
    }

    Preferences.setBoolean("run.options.memory", memoryOverrideBox.isSelected()); //$NON-NLS-1$
    int memoryMin = Preferences.getInteger("run.options.memory.initial"); //$NON-NLS-1$
    int memoryMax = Preferences.getInteger("run.options.memory.maximum"); //$NON-NLS-1$
    try {
      memoryMax = Integer.parseInt(memoryField.getText().trim());
      // make sure memory setting isn't too small
      if (memoryMax < memoryMin) {
        memoryMax = memoryMin;
      }
      Preferences.setInteger("run.options.memory.maximum", memoryMax); //$NON-NLS-1$
    } catch (NumberFormatException e) {
      System.err.println("Ignoring bad memory setting");
    }

    // Don't change anything if the user closes the window before fonts load
    if (fontSelectionBox.isEnabled()) {
      String fontFamily = (String) fontSelectionBox.getSelectedItem();
      if (Toolkit.getMonoFontName().equals(fontFamily)) {
        fontFamily = "processing.mono";
      }
      Preferences.set("editor.font.family", fontFamily);
    }

    try {
      Object selection = fontSizeField.getSelectedItem();
      if (selection instanceof String) {
        // Replace with Integer version
        selection = Integer.parseInt((String) selection);
      }
      Preferences.set("editor.font.size", String.valueOf(selection));

    } catch (NumberFormatException e) {
      Messages.log("Ignoring invalid font size " + fontSizeField); //$NON-NLS-1$
      fontSizeField.setSelectedItem(Preferences.getInteger("editor.font.size"));
    }

    try {
      Object selection = consoleFontSizeField.getSelectedItem();
      if (selection instanceof String) {
        // Replace with Integer version
        selection = Integer.parseInt((String) selection);
      }
      Preferences.set("console.font.size", String.valueOf(selection));

    } catch (NumberFormatException e) {
      Messages.log("Ignoring invalid font size " + consoleFontSizeField); //$NON-NLS-1$
      consoleFontSizeField.setSelectedItem(Preferences.getInteger("console.font.size"));
    }

    Preferences.setBoolean("editor.zoom.auto", zoomAutoBox.isSelected());
    Preferences.set("editor.zoom",
                    String.valueOf(zoomSelectionBox.getSelectedItem()));

    if (Platform.isWindows()) {
      Splash.setDisableHiDPI(hidpiDisableBox.isSelected());
    }
    Preferences.setBoolean("editor.sync_folder_and_filename", syncSketchNameBox.isSelected());

    Preferences.setColor("run.present.bgcolor", presentColor.getBackground());

    Preferences.setBoolean("editor.input_method_support", inputMethodBox.isSelected()); //$NON-NLS-1$

    if (autoAssociateBox != null) {
      Preferences.setBoolean("platform.auto_file_type_associations", //$NON-NLS-1$
                             autoAssociateBox.isSelected());
    }

    Preferences.setBoolean("pdex.errorCheckEnabled", errorCheckerBox.isSelected());
    Preferences.setBoolean("pdex.warningsEnabled", warningsCheckerBox.isSelected());
    Preferences.setBoolean("pdex.completion", codeCompletionBox.isSelected());
    Preferences.setBoolean("pdex.suggest.imports", importSuggestionsBox.isSelected());

    for (Editor editor : base.getEditors()) {
      editor.applyPreferences();
    }

    // https://github.com/processing/processing4/issues/608
    Preferences.save();
  }


  public void showFrame() {
    //editorAntialiasBox.setSelected(Preferences.getBoolean("editor.smooth")); //$NON-NLS-1$
    inputMethodBox.setSelected(Preferences.getBoolean("editor.input_method_support")); //$NON-NLS-1$
    errorCheckerBox.setSelected(Preferences.getBoolean("pdex.errorCheckEnabled"));
    warningsCheckerBox.setSelected(Preferences.getBoolean("pdex.warningsEnabled"));
    warningsCheckerBox.setEnabled(errorCheckerBox.isSelected());
    codeCompletionBox.setSelected(Preferences.getBoolean("pdex.completion"));
    importSuggestionsBox.setSelected(Preferences.getBoolean("pdex.suggest.imports"));
//    deletePreviousBox.setSelected(Preferences.getBoolean("export.delete_target_folder")); //$NON-NLS-1$

    sketchbookLocationField.setText(Preferences.getSketchbookPath());

    namingSelectionBox.setSelectedItem(Preferences.get("sketch.name.approach"));
    if (namingSelectionBox.getSelectedIndex() < 0) {
      // If no selection, revert to the classic style, and set the pref as well
      namingSelectionBox.setSelectedItem(SketchName.CLASSIC);
      Preferences.set("sketch.name.approach", SketchName.CLASSIC);
    }

    checkUpdatesBox.setSelected(Preferences.getBoolean("update.check")); //$NON-NLS-1$

    defaultDisplayNum = updateDisplayList();
    int displayNum = Preferences.getInteger("run.display"); //$NON-NLS-1$
    if (displayNum < 1 || displayNum > displayCount) {
      // set the display on close instead; too much weird logic here
      //Preferences.setInteger("run.display", displayNum);
      displayNum = defaultDisplayNum;
    }
    displaySelectionBox.setSelectedIndex(displayNum-1);

    // This takes a while to load, so run it from a separate thread
    //EventQueue.invokeLater(new Runnable() {
    new Thread(this::initFontList).start();

    fontSizeField.setSelectedItem(Preferences.getInteger("editor.font.size"));
    consoleFontSizeField.setSelectedItem(Preferences.getInteger("console.font.size"));

    boolean zoomAuto = Preferences.getBoolean("editor.zoom.auto");
    if (zoomAuto) {
      zoomAutoBox.setSelected(true);
      zoomSelectionBox.setEnabled(false);
    }
    String zoomSel = Preferences.get("editor.zoom");
    int zoomIndex = Toolkit.zoomOptions.index(zoomSel);
    if (zoomIndex != -1) {
      zoomSelectionBox.setSelectedIndex(zoomIndex);
    } else {
      zoomSelectionBox.setSelectedIndex(0);
    }
    if (Platform.isWindows()) {
      hidpiDisableBox.setSelected(Splash.getDisableHiDPI());
    }
    syncSketchNameBox.setSelected(Preferences.getBoolean("editor.sync_folder_and_filename"));

    presentColor.setBackground(Preferences.getColor("run.present.bgcolor"));
    //presentColorHex.setText(Preferences.get("run.present.bgcolor").substring(1));

    memoryOverrideBox.
      setSelected(Preferences.getBoolean("run.options.memory")); //$NON-NLS-1$
    memoryField.
      setText(Preferences.get("run.options.memory.maximum")); //$NON-NLS-1$
    memoryField.setEnabled(memoryOverrideBox.isSelected());

    if (autoAssociateBox != null) {
      autoAssociateBox.setSelected(Preferences.getBoolean("platform.auto_file_type_associations")); //$NON-NLS-1$
    }
    // The OK Button has to be set as the default button every time the
    // PrefWindow is to be displayed
    frame.getRootPane().setDefaultButton(okButton);

    // Prevent the location field from being highlighted by default
    sketchbookLocationField.select(0, 0);
    // Could make the Cancel button the default, but seems odd
    okButton.requestFocusInWindow();

    // The pack is called again here second time to fix layout bugs
    // due to HTML rendering of components. [akarshit 150430]
    // https://netbeans.org/bugzilla/show_bug.cgi?id=79967
    frame.pack();

    frame.setVisible(true);
  }


  public void updateTheme() {
    // Required to update changes to accent color or light/dark mode.
    // (No custom components, so safe to call on this Window object.)
    SwingUtilities.updateComponentTreeUI(frame);
    restartLabel.setForeground(Theme.getColor("laf.accent.color"));
  }


//  /**
//   * I have some ideas on how we could make Swing even more obtuse for the
//   * most basic usage scenarios. Is there someone on the team I can contact?
//   * Are you an Oracle staffer reading this? This could be your meal ticket.
//   */
//  static class FontNamer extends JLabel implements ListCellRenderer<Font> {
//    public Component getListCellRendererComponent(JList<? extends Font> list,
//                                                  Font value, int index,
//                                                  boolean isSelected,
//                                                  boolean cellHasFocus) {
//      setText(value.getFamily() + " / " + value.getName() + " (" + value.getPSName() + ")");
//      return this;
//    }
//  }


  void initFontList() {
    if (monoFontFamilies == null) {
      monoFontFamilies = Toolkit.getMonoFontFamilies();

      EventQueue.invokeLater(() -> {
        fontSelectionBox.setModel(new DefaultComboBoxModel<>(monoFontFamilies));
        String family = Preferences.get("editor.font.family");
        String defaultName = Toolkit.getMonoFontName();
        if ("processing.mono".equals(family)) {
          family = defaultName;
        }

        // Set a reasonable default, in case selecting the family fails
        fontSelectionBox.setSelectedItem(defaultName);
        // Now try to select the family (will fail silently, see prev line)
        fontSelectionBox.setSelectedItem(family);
        fontSelectionBox.setEnabled(true);
      });
    }
  }


  /**
   * @return the number (1..whatever, not 0-indexed) of the default display
   */
  int updateDisplayList() {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice defaultDevice = ge.getDefaultScreenDevice();
    GraphicsDevice[] devices = ge.getScreenDevices();

    int defaultNum = -1;
    displayCount = devices.length;
    String[] items = new String[displayCount];
    for (int i = 0; i < displayCount; i++) {
      DisplayMode mode = devices[i].getDisplayMode();
      //String title = String.format("%d (%d \u2715 %d)",  // or \u00d7?
      String title = String.format("%d (%d \u00d7 %d)",  // or \u2715?
                                   i + 1, mode.getWidth(), mode.getHeight());
      if (devices[i] == defaultDevice) {
        title += " default";
        defaultNum = i + 1;
      }
      items[i] = title;
    }
    displaySelectionBox.setModel(new DefaultComboBoxModel<>(items));

    // Disable it if you can't actually change the default display
    displaySelectionBox.setEnabled(displayCount != 1);

    // Send back the number (1-indexed) of the default display
    return defaultNum;
  }
}
