package processing.app.tools;
/*
 * This GUI is from the old Movie Maker which was based on code from:
 * Copyright (c) 2010-2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 * (However, he should not be held responsible for the current mess of a hack
 * that it has become.)
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Arrays;
import java.util.prefs.Preferences;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;

import processing.app.Base;
import processing.app.Language;

import ch.randelshofer.gui.datatransfer.FileTextFieldTransferHandler;
import processing.app.Platform;


/**
 * Tool for creating movie files from sequences of images.
 * Originally hacked from Werner Randelshofer's QuickTimeWriter demo,
 * reorganized for Processing 4 to instead use FFmpeg.
 */
public class MovieMaker extends JFrame implements Tool {
  private Preferences prefs;
  //private QuickTimeEngine engine;
  private FFmpegEngine engine;


  public String getMenuTitle() {
    return Language.text("movie_maker");
  }


  public void run() {
    setVisible(true);
  }


  public void init(Base base) {
    //engine = new QuickTimeEngine(this);
    engine = new FFmpegEngine(this);
    //initComponents(base.getActiveEditor() == null);
    initComponents(base == null);

    ((JComponent) getContentPane()).setBorder(new EmptyBorder(12, 18, 18, 18));
    imageFolderField.setTransferHandler(new FileTextFieldTransferHandler(JFileChooser.DIRECTORIES_ONLY));
    soundFileField.setTransferHandler(new FileTextFieldTransferHandler());

    JComponent[] smallComponents = {
      compressionBox,
      compressionLabel,
      fpsField,
      fpsLabel,
      widthField,
      widthLabel,
      heightField,
      heightLabel,
      originalSizeCheckBox,
    };
    for (JComponent c : smallComponents) {
      c.putClientProperty("JComponent.sizeVariant", "small");
    }

    // Get Preferences
    prefs = Preferences.userNodeForPackage(MovieMaker.class);
    imageFolderField.setText(prefs.get("movie.imageFolder", ""));
    soundFileField.setText(prefs.get("movie.soundFile", ""));
    widthField.setText("" + prefs.getInt("movie.width", 640));
    heightField.setText("" + prefs.getInt("movie.height", 480));
    boolean original = prefs.getBoolean("movie.originalSize", false);
    originalSizeCheckBox.setSelected(original);
    widthField.setEnabled(!original);
    heightField.setEnabled(!original);
    String fps = "" + prefs.getDouble("movie.fps", 30);
    if (fps.endsWith(".0")) {
      fps = fps.substring(0, fps.length() - 2);
    }
    fpsField.setText(fps);
    compressionBox.setSelectedIndex(Math.max(0, Math.min(compressionBox.getItemCount() - 1, prefs.getInt("movie.compression", 0))));

    originalSizeCheckBox.addActionListener(e -> {
      boolean enabled = !originalSizeCheckBox.isSelected();
      widthField.setEnabled(enabled);
      heightField.setEnabled(enabled);
    });

    // scoot everybody around
    pack();
    // center the frame on screen
    setLocationRelativeTo(null);
  }


  /**
   * Registers key events for a Ctrl-W and ESC with an ActionListener
   * that will take care of disposing the window.
   */
  static public void registerWindowCloseKeys(JRootPane root,
                                             ActionListener disposer) {
    KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    root.registerKeyboardAction(disposer, stroke,
                                JComponent.WHEN_IN_FOCUSED_WINDOW);

    int modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    stroke = KeyStroke.getKeyStroke('W', modifiers);
    root.registerKeyboardAction(disposer, stroke,
                                JComponent.WHEN_IN_FOCUSED_WINDOW);
  }


  private void initComponents(final boolean standalone) {
    JLabel imageFolderHelpLabel = new JLabel();
    imageFolderField = new JTextField();
    JButton chooseImageFolderButton = new JButton();
    JLabel soundFileHelpLabel = new JLabel();
    soundFileField = new JTextField();
    JButton chooseSoundFileButton = new JButton();
    createMovieButton = new JButton();
    widthLabel = new JLabel();
    widthField = new JTextField();
    heightLabel = new JLabel();
    heightField = new JTextField();
    compressionLabel = new JLabel();
    compressionBox = new JComboBox<>();
    fpsLabel = new JLabel();
    fpsField = new JTextField();
    originalSizeCheckBox = new JCheckBox();

    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        setVisible(false);
      }
    });
    registerWindowCloseKeys(getRootPane(), actionEvent -> {
      if (standalone) {
        System.exit(0);
      } else {
        setVisible(false);
      }
    });
    setTitle(Language.text("movie_maker.two.title"));

    JLabel aboutLabel = new JLabel(Language.text("movie_maker.two.blurb"));
    imageFolderHelpLabel.setText(Language.text("movie_maker.image_folder_help_label"));
    chooseImageFolderButton.setText(Language.text("movie_maker.choose_button"));
    //chooseImageFolderButton.addActionListener(formListener);
    chooseImageFolderButton.addActionListener(e -> Chooser.selectFolder(MovieMaker.this,
                         Language.text("movie_maker.select_image_folder"),
                         new File(imageFolderField.getText()),
                         new Chooser.Callback() {
      void select(File file) {
        if (file != null) {
          imageFolderField.setText(file.getAbsolutePath());
        }
      }
    }));


    soundFileHelpLabel.setText(Language.text("movie_maker.sound_file_help_label"));
    chooseSoundFileButton.setText(Language.text("movie_maker.choose_button"));
    //chooseSoundFileButton.addActionListener(formListener);
    chooseSoundFileButton.addActionListener(e -> Chooser.selectInput(MovieMaker.this,
                        Language.text("movie_maker.select_sound_file"),
                        new File(soundFileField.getText()),
                        new Chooser.Callback() {

      void select(File file) {
        if (file != null) {
          soundFileField.setText(file.getAbsolutePath());
        }
      }
    }));

    createMovieButton.setText(Language.text("movie_maker.create_movie_button"));
    createMovieButton.addActionListener(e -> {
      String lastPath = prefs.get("movie.outputFile", null);
      File lastFile = lastPath == null ? null : new File(lastPath);
      Chooser.selectOutput(MovieMaker.this,
                           Language.text("movie_maker.save_dialog_prompt"),
                           lastFile,
                           new Chooser.Callback() {
        @Override
        void select(File file) {
          createMovie(file);
        }
      });
    });

    Font font = new Font("Dialog", Font.PLAIN, 11);

    widthLabel.setFont(font);
    widthLabel.setText(Language.text("movie_maker.width"));
    widthField.setColumns(4);
    widthField.setFont(font);
    widthField.setText("320");

    heightLabel.setFont(font);
    heightLabel.setText(Language.text("movie_maker.height"));
    heightField.setColumns(4);
    heightField.setFont(font);
    heightField.setText("240");

    compressionLabel.setFont(font);
    compressionLabel.setText(Language.text("movie_maker.compression"));
    compressionBox.setFont(font);
    //compressionBox.setModel(new DefaultComboBoxModel(new String[] { "None", "Animation", "JPEG", "PNG" }));
    compressionBox.setModel(new DefaultComboBoxModel<>(engine.getFormats()));

    fpsLabel.setFont(font);
    fpsLabel.setText(Language.text("movie_maker.framerate"));
    fpsField.setColumns(4);
    fpsField.setFont(font);
    fpsField.setText("30");

    originalSizeCheckBox.setFont(font);
    originalSizeCheckBox.setText(Language.text("movie_maker.orig_size_button"));
    originalSizeCheckBox.setToolTipText(Language.text("movie_maker.orig_size_tooltip"));

    GroupLayout layout = new GroupLayout(getContentPane());
    getContentPane().setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
      .addGroup(layout.createSequentialGroup()
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
          .addGroup(layout.createSequentialGroup()
            .addGap(61, 61, 61)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
              .addComponent(widthLabel)
              .addComponent(fpsLabel))
              .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
              .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                  .addComponent(fpsField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(compressionLabel)
                  .addGap(1, 1, 1)
                  .addComponent(compressionBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(originalSizeCheckBox))
                  .addGroup(layout.createSequentialGroup()
                    .addComponent(widthField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                    .addComponent(heightLabel)
                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(heightField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)))
                    .addGap(41, 41, 41))
                    .addGroup(layout.createSequentialGroup()
                      .addContainerGap()
                      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(aboutLabel, GroupLayout.DEFAULT_SIZE, 484, Short.MAX_VALUE)
                        .addComponent(imageFolderHelpLabel)
                        .addComponent(soundFileHelpLabel)
                        .addGroup(layout.createSequentialGroup()
                          .addComponent(soundFileField, GroupLayout.DEFAULT_SIZE, 372, Short.MAX_VALUE)
                          .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                          .addComponent(chooseSoundFileButton))
                          .addComponent(createMovieButton, GroupLayout.Alignment.TRAILING)
                          .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                            .addComponent(imageFolderField, GroupLayout.DEFAULT_SIZE, 372, Short.MAX_VALUE)
                            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(chooseImageFolderButton))))
                            .addGroup(layout.createSequentialGroup()
                              .addContainerGap())))
    );
    layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
      .addGroup(layout.createSequentialGroup()
        .addContainerGap()
        .addComponent(aboutLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
        .addGap(18, 18, 18)
        .addComponent(imageFolderHelpLabel)
        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
          .addComponent(imageFolderField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
          .addComponent(chooseImageFolderButton))
          .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
          .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(widthLabel)
            .addComponent(widthField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(heightLabel)
            .addComponent(heightField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
              .addComponent(compressionBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
              .addComponent(fpsLabel)
              .addComponent(fpsField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
              .addComponent(compressionLabel)
              .addComponent(originalSizeCheckBox))
              .addGap(18, 18, 18)
              .addComponent(soundFileHelpLabel)
              .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
              .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(soundFileField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(chooseSoundFileButton))
                .addGap(18, 18, 18)
                .addComponent(createMovieButton)
                .addContainerGap())
    );

    pack();
  }


//  private void chooseSoundFile(ActionEvent evt) {
//    if (soundFileChooser == null) {
//      soundFileChooser = new JFileChooser();
//      if (soundFileField.getText().length() > 0) {
//        soundFileChooser.setSelectedFile(new File(soundFileField.getText()));
//      } else if (imageFolderField.getText().length() > 0) {
//        soundFileChooser.setCurrentDirectory(new File(imageFolderField.getText()));
//      }
//    }
//    if (JFileChooser.APPROVE_OPTION == soundFileChooser.showOpenDialog(this)) {
//      soundFileField.setText(soundFileChooser.getSelectedFile().getPath());
//    }
//  }


  // this is super naughty, and shouldn't be out here. it's a hack to get the
  // ImageIcon width/height setting to work. there are better ways to do this
  // given a bit of time. you know, time? the infinite but non-renewable resource?
  int width, height;

  private void createMovie(final File movieFile) {
    if (movieFile == null) return;

    /*
    if (movieFile != null) {
      String path = movieFile.getAbsolutePath();
      if (!path.toLowerCase().endsWith(".mov")) {
        path += ".mov";
      }
      prefs.put("movie.outputFile", path);
      createMovie(new File(path));
    }
    */

    createMovieButton.setEnabled(false);

    // ---------------------------------
    // Check input
    // ---------------------------------
    final File soundFile = soundFileField.getText().trim().length() == 0 ? null : new File(soundFileField.getText().trim());
    final File imageFolder = imageFolderField.getText().trim().length() == 0 ? null : new File(imageFolderField.getText().trim());
    if (soundFile == null && imageFolder == null) {
      JOptionPane.showMessageDialog(this, Language.text("movie_maker.error.need_input"));
      return;
    }

    final double fps;
    try {
      width = Integer.parseInt(widthField.getText());
      height = Integer.parseInt(heightField.getText());
      fps = Double.parseDouble(fpsField.getText());
    } catch (Throwable t) {
      JOptionPane.showMessageDialog(this, Language.text("movie_maker.error.badnumbers"));
      return;
    }
    if (width < 1 || height < 1 || fps < 1) {
      JOptionPane.showMessageDialog(this, Language.text("movie_maker.error.badnumbers"));
      return;
    }

    /*
    final QuickTimeWriter.VideoFormat videoFormat;
    switch (compressionBox.getSelectedIndex()) {
//    case 0:
//      videoFormat = QuickTimeWriter.VideoFormat.RAW;
//      break;
      case 0://1:
        videoFormat = QuickTimeWriter.VideoFormat.RLE;
        break;
      case 1://2:
        videoFormat = QuickTimeWriter.VideoFormat.JPG;
        break;
      case 2://3:
      default:
        videoFormat = QuickTimeWriter.VideoFormat.PNG;
        break;
    }
     */

    // ---------------------------------
    // Update Preferences
    // ---------------------------------
    prefs.put("movie.imageFolder", imageFolderField.getText());
    prefs.put("movie.soundFile", soundFileField.getText());
    prefs.putInt("movie.width", width);
    prefs.putInt("movie.height", height);
    prefs.putDouble("movie.fps", fps);
    prefs.putInt("movie.compression", compressionBox.getSelectedIndex());
    prefs.putBoolean("movie.originalSize", originalSizeCheckBox.isSelected());

    final boolean originalSize = originalSizeCheckBox.isSelected();

    // ---------------------------------
    // Create the QuickTime movie
    // ---------------------------------
    new SwingWorker<Throwable, Object>() {

      @Override
      protected Throwable doInBackground() {
        try {
          // Read image files
          File[] imgFiles;
          if (imageFolder != null) {
            imgFiles = imageFolder.listFiles(new FileFilter() {
              final FileSystemView fsv = FileSystemView.getFileSystemView();

              public boolean accept(File f) {
                return f.isFile() && !fsv.isHiddenFile(f) &&
                  !f.getName().equals("Thumbs.db");
              }
            });
            if (imgFiles == null || imgFiles.length == 0) {
              return new RuntimeException(Language.text("movie_maker.error.no_images_found"));
            }
            Arrays.sort(imgFiles);

            // Get the width and height if we're preserving size.
            if (originalSize) {
              width = 0;
              height = 0;
            }

            // Delete movie file if it already exists.
            if (movieFile.exists()) {
              if (!movieFile.delete()) {
                return new RuntimeException("Could not replace " + movieFile.getAbsolutePath());
              }
            }

            String formatName = (String) compressionBox.getSelectedItem();
            engine.write(movieFile, imgFiles, soundFile, width, height, fps, formatName);
          }
          return null;

        } catch (Throwable t) {
          return t;
        }
      }

      @Override
      protected void done() {
        Throwable t;
        try {
          t = get();
        } catch (Exception ex) {
          t = ex;
        }
        if (t != null) {
          t.printStackTrace();
          JOptionPane.showMessageDialog(MovieMaker.this,
            Language.text("movie_maker.error.movie_failed") + "\n" +
              (t.getMessage() == null ? t.toString() : t.getMessage()),
            Language.text("movie_maker.error.sorry"),
            JOptionPane.ERROR_MESSAGE);
        }
        createMovieButton.setEnabled(true);
      }
    }.execute();

  }//GEN-LAST:event_createMovie


  static public void main(String[] args) {
    EventQueue.invokeLater(() -> {
      Base.setCommandLine();
      Platform.init();

      MovieMaker m = new MovieMaker();
      m.init(null);
      m.setVisible(true);
    });
  }


  private JComboBox<String> compressionBox;
  private JLabel compressionLabel;
  private JTextField fpsField;
  private JLabel fpsLabel;
  private JTextField heightField;
  private JLabel heightLabel;
  private JTextField imageFolderField;
  private JCheckBox originalSizeCheckBox;
  private JTextField soundFileField;
  private JTextField widthField;
  private JLabel widthLabel;
  private JButton createMovieButton;
}
