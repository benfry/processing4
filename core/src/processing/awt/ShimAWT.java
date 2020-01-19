package processing.awt;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.UIManager;

import processing.core.PApplet;
import processing.core.PConstants;


/**
 * This class exists as an abstraction layer to remove AWT from PApplet.
 * It is a staging area for AWT-specific code that's shared by the Java2D,
 * JavaFX, and JOGL renderers. Once PSurfaceFX and PSurfaceJOGL have
 * their own implementations, these methods will move to PSurfaceAWT.
 */
public class ShimAWT {
  /*
  PGraphics graphics;
  PApplet sketch;


  public ShimAWT(PApplet sketch) {
    this.graphics = graphics;
    this.sketch = sketch;
  }
  */


  /*
  public int displayDensity() {
    if (sketch.display != PConstants.SPAN && (sketch.fullScreen || sketch.present)) {
      return displayDensity(sketch.display);
    }
    // walk through all displays, use 2 if any display is 2
    for (int i = 0; i < displayDevices.length; i++) {
      if (displayDensity(i+1) == 2) {
        return 2;
      }
    }
    // If nobody's density is 2 then everyone is 1
    return 1;
  }
  */


 /**
  * @param display the display number to check
  * (1-indexed to match the Preferences dialog box)
  */
  /*
  public int displayDensity(int display) {
    if (display > 0 && display <= displayDevices.length) {
      GraphicsConfiguration graphicsConfig =
        displayDevices[display - 1].getDefaultConfiguration();
      AffineTransform tx = graphicsConfig.getDefaultTransform();
      return (int) Math.round(tx.getScaleX());
    }

    System.err.println("Display " + display + " does not exist, returning ");
    return 1;  // not the end of the world, so don't throw a RuntimeException
  }
  */


  static public void selectInput(String prompt, String callbackMethod,
                                 File file, Object callbackObject) {
    EventQueue.invokeLater(() -> {
      selectImpl(prompt, callbackMethod, file,
                 callbackObject, null, FileDialog.LOAD);
    });
  }


  /*
  static public void selectOutput(String prompt, String callbackMethod,
                                  File file, Object callbackObject, Frame parent) {
    selectImpl(prompt, callbackMethod, file, callbackObject, parent, FileDialog.SAVE, null);
  }


  static public void selectOutput(String prompt, String callbackMethod,
                                  File file, Object callbackObject, Frame parent,
                                  PApplet sketch) {
    selectImpl(prompt, callbackMethod, file, callbackObject, parent, FileDialog.SAVE, sketch);
  }
  */


  static public void selectOutput(String prompt, String callbackMethod,
                                  File file, Object callbackObject) {
    EventQueue.invokeLater(() -> {
      selectImpl(prompt, callbackMethod, file,
                 callbackObject, null, FileDialog.SAVE);
    });
  }


  /*
  // Will remove the 'sketch' parameter once we get an upstream JOGL fix
  // https://github.com/processing/processing/issues/3831
  static protected void selectEvent(final String prompt,
                                   final String callbackMethod,
                                   final File defaultSelection,
                                   final Object callbackObject,
                                   final Frame parentFrame,
                                   final int mode,
                                   final PApplet sketch) {
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        boolean hide = (sketch != null) &&
          (sketch.g instanceof PGraphicsOpenGL) &&
          (PApplet.platform == PConstants.WINDOWS);
        if (hide) sketch.getSurface().setVisible(false);

        selectImpl(prompt, callbackMethod, defaultSelection, callbackObject,
                   parentFrame, mode, sketch);

        if (hide) sketch.getSurface().setVisible(true);
      }
    });
  }
  */


  static public void selectImpl(final String prompt,
                                final String callbackMethod,
                                final File defaultSelection,
                                final Object callbackObject,
                                final Frame parentFrame,
                                final int mode) {
    File selectedFile = null;

    if (PApplet.useNativeSelect) {
      FileDialog dialog = new FileDialog(parentFrame, prompt, mode);
      if (defaultSelection != null) {
        dialog.setDirectory(defaultSelection.getParent());
        dialog.setFile(defaultSelection.getName());
      }

      dialog.setVisible(true);
      String directory = dialog.getDirectory();
      String filename = dialog.getFile();
      if (filename != null) {
        selectedFile = new File(directory, filename);
      }

    } else {
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle(prompt);
      if (defaultSelection != null) {
        chooser.setSelectedFile(defaultSelection);
      }

      int result = -1;
      if (mode == FileDialog.SAVE) {
        result = chooser.showSaveDialog(parentFrame);
      } else if (mode == FileDialog.LOAD) {
        result = chooser.showOpenDialog(parentFrame);
      }
      if (result == JFileChooser.APPROVE_OPTION) {
        selectedFile = chooser.getSelectedFile();
      }
    }
    PApplet.selectCallback(selectedFile, callbackMethod, callbackObject);
  }


  static public void selectFolder(final String prompt,
                                  final String callbackMethod,
                                  final File defaultSelection,
                                  final Object callbackObject) {
    EventQueue.invokeLater(() -> {
      selectFolderImpl(prompt, callbackMethod, defaultSelection,
                       callbackObject, null);
    });
  }


  /*
  static public void selectFolder(final String prompt,
                                  final String callbackMethod,
                                  final File defaultSelection,
                                  final Object callbackObject,
                                  final Frame parentFrame) {
    selectFolderEvent(prompt, callbackMethod, defaultSelection, callbackObject, parentFrame, null);
  }


  // Will remove the 'sketch' parameter once we get an upstream JOGL fix
  // https://github.com/processing/processing/issues/3831
  static public void selectFolderEvent(final String prompt,
                                       final String callbackMethod,
                                       final File defaultSelection,
                                       final Object callbackObject,
                                       final Frame parentFrame,
                                       final PApplet sketch) {
    EventQueue.invokeLater(() -> {
      selectFolderImpl(prompt, callbackMethod, defaultSelection,
                       callbackObject, parentFrame, sketch);
    });
  }
  */


  static public void selectFolderImpl(final String prompt,
                                      final String callbackMethod,
                                      final File defaultSelection,
                                      final Object callbackObject,
                                      final Frame parentFrame) {
    File selectedFile = null;
    if (PApplet.platform == PConstants.MACOS && PApplet.useNativeSelect) {
      FileDialog fileDialog =
        new FileDialog(parentFrame, prompt, FileDialog.LOAD);
      if (defaultSelection != null) {
        fileDialog.setDirectory(defaultSelection.getAbsolutePath());
      }
      System.setProperty("apple.awt.fileDialogForDirectories", "true");
      fileDialog.setVisible(true);
      System.setProperty("apple.awt.fileDialogForDirectories", "false");
      String filename = fileDialog.getFile();
      if (filename != null) {
        selectedFile = new File(fileDialog.getDirectory(), fileDialog.getFile());
      }
    } else {
      checkLookAndFeel();
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle(prompt);
      fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      if (defaultSelection != null) {
        fileChooser.setCurrentDirectory(defaultSelection);
      }

      int result = fileChooser.showOpenDialog(parentFrame);
      if (result == JFileChooser.APPROVE_OPTION) {
        selectedFile = fileChooser.getSelectedFile();
      }
    }
    PApplet.selectCallback(selectedFile, callbackMethod, callbackObject);
  }


  static private boolean lookAndFeelCheck;

  /**
   * Initialize the Look & Feel if it hasn't been already.
   * Call this before using any Swing-related code in PApplet methods.
   */
  static private void checkLookAndFeel() {
    if (!lookAndFeelCheck) {
      if (PApplet.platform == PConstants.WINDOWS) {
        // Windows is defaulting to Metal or something else awful.
        // Which also is not scaled properly with HiDPI interfaces.
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) { }
      }
      lookAndFeelCheck = true;
    }
  }
}