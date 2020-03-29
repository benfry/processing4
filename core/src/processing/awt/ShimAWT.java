package processing.awt;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import java.awt.DisplayMode;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.geom.AffineTransform;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.UIManager;

// used by desktopFile() method
import javax.swing.filechooser.FileSystemView;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PImage;


/**
 * This class exists as an abstraction layer to remove AWT from PApplet.
 * It is a staging area for AWT-specific code that's shared by the Java2D,
 * JavaFX, and JOGL renderers. Once PSurfaceFX and PSurfaceJOGL have
 * their own implementations, these methods will move to PSurfaceAWT.
 */
public class ShimAWT implements PConstants {
  /*
  PGraphics graphics;
  PApplet sketch;


  public ShimAWT(PApplet sketch) {
    this.graphics = graphics;
    this.sketch = sketch;
  }
  */
  static private ShimAWT instance;

  private GraphicsDevice[] displayDevices;

  private int displayWidth;
  private int displayHeight;


  /** Only needed for display functions */
  static private ShimAWT getInstance() {
    if (instance == null) {
      instance = new ShimAWT();
    }
    return instance;
  }


  private ShimAWT() {
    // Need the list of display devices to be queried already for usage below.
    // https://github.com/processing/processing/issues/3295
    // https://github.com/processing/processing/issues/3296
    // Not doing this from a static initializer because it may cause
    // PApplet to cache and the values to stick through subsequent runs.
    // Instead make it a runtime thing and a local variable.
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice device = ge.getDefaultScreenDevice();
    displayDevices = ge.getScreenDevices();

//    // Default or unparsed will be -1, spanning will be 0, actual displays will
//    // be numbered from 1 because it's too weird to say "display 0" in prefs.
//    if (display > 0 && display <= displayDevices.length) {
//      device = displayDevices[display-1];
//    }
    // When this was called, display will always be unset (even in 3.x),
    // since this happens before settings() is called.

    // Set displayWidth and displayHeight for people still using those.
    DisplayMode displayMode = device.getDisplayMode();
    displayWidth = displayMode.getWidth();
    displayHeight = displayMode.getHeight();
  }


  static public int getDisplayWidth() {
    return getInstance().displayWidth;
  }


  static public int getDisplayHeight() {
    return getInstance().displayHeight;
  }


  static public int getDisplayCount() {
    return getInstance().displayDevices.length;
  }


  static public int getDisplayDensity(int num) {
    return getInstance().displayDensityImpl(num);
  }


  /*
  private int displayDensityImpl() {
    if (display != SPAN && (fullScreen || present)) {
      return displayDensity(display);
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


  private int displayDensityImpl(int display) {
    if (display > 0 && display <= displayDevices.length) {
      GraphicsConfiguration graphicsConfig =
        displayDevices[display - 1].getDefaultConfiguration();
      AffineTransform tx = graphicsConfig.getDefaultTransform();
      return (int) Math.round(tx.getScaleX());
    }

    System.err.println("Display " + display + " does not exist, " +
                       "returning 1 for displayDensity(" + display + ")");
    return 1;  // not the end of the world, so don't throw a RuntimeException
  }


  static public PImage loadImage(PApplet sketch, String filename, Object... args) {
    String extension = null;
    if (args != null && args.length > 0) {
      // the only one that's supported for now
      extension = (String) args[0];
    }

    if (extension == null) {
      String lower = filename.toLowerCase();
      int dot = filename.lastIndexOf('.');
      if (dot == -1) {
        extension = "unknown";  // no extension found

      } else {
        extension = lower.substring(dot + 1);

        // check for, and strip any parameters on the url, i.e.
        // filename.jpg?blah=blah&something=that
        int question = extension.indexOf('?');
        if (question != -1) {
          extension = extension.substring(0, question);
        }
      }
    }

    // just in case. them users will try anything!
    extension = extension.toLowerCase();

    if (extension.equals("tga")) {
      try {
        InputStream input = sketch.createInput(filename);
        if (input == null) return null;

        PImage image = PImage.loadTGA(input);
        image.parent = sketch;
        return image;

      } catch (IOException e) {
        e.printStackTrace();
        return null;
      }
    }

    if (extension.equals("tif") || extension.equals("tiff")) {
      InputStream input = sketch.createInput(filename);
      PImage image =  (input == null) ? null : PImage.loadTIFF(input);
      return image;
    }

    // For jpeg, gif, and png, load them using createImage(),
    // because the javax.imageio code was found to be much slower.
    // http://dev.processing.org/bugs/show_bug.cgi?id=392
    try {
      if (extension.equals("jpg") || extension.equals("jpeg") ||
          extension.equals("gif") || extension.equals("png") ||
          extension.equals("unknown")) {
        byte[] bytes = sketch.loadBytes(filename);
        if (bytes == null) {
          return null;
        } else {
          //Image awtImage = Toolkit.getDefaultToolkit().createImage(bytes);
          Image awtImage = new ImageIcon(bytes).getImage();

          if (awtImage instanceof BufferedImage) {
            BufferedImage buffImage = (BufferedImage) awtImage;
            int space = buffImage.getColorModel().getColorSpace().getType();
            if (space == ColorSpace.TYPE_CMYK) {
              System.err.println(filename + " is a CMYK image, " +
                                 "only RGB images are supported.");
              return null;
              /*
              // wishful thinking, appears to not be supported
              // https://community.oracle.com/thread/1272045?start=0&tstart=0
              BufferedImage destImage =
                new BufferedImage(buffImage.getWidth(),
                                  buffImage.getHeight(),
                                  BufferedImage.TYPE_3BYTE_BGR);
              ColorConvertOp op = new ColorConvertOp(null);
              op.filter(buffImage, destImage);
              image = new PImage(destImage);
              */
            }
          }

          PImage image = new PImageAWT(awtImage);
          if (image.width == -1) {
            System.err.println("The file " + filename +
                               " contains bad image data, or may not be an image.");
          }

          // if it's a .gif image, test to see if it has transparency
          if (extension.equals("gif") || extension.equals("png") ||
              extension.equals("unknown")) {
            image.checkAlpha();
          }

          image.parent = sketch;
          return image;
        }
      }
    } catch (Exception e) {
      // show error, but move on to the stuff below, see if it'll work
      e.printStackTrace();
    }

    if (loadImageFormats == null) {
      loadImageFormats = ImageIO.getReaderFormatNames();
    }
    if (loadImageFormats != null) {
      for (int i = 0; i < loadImageFormats.length; i++) {
        if (extension.equals(loadImageFormats[i])) {
          return loadImageIO(sketch, filename);
        }
      }
    }

    // failed, could not load image after all those attempts
    System.err.println("Could not find a method to load " + filename);
    return null;
  }

  static protected String[] loadImageFormats;


  /**
   * Use Java 1.4 ImageIO methods to load an image.
   */
  static protected PImage loadImageIO(PApplet sketch, String filename) {
    InputStream stream = sketch.createInput(filename);
    if (stream == null) {
      System.err.println("The image " + filename + " could not be found.");
      return null;
    }

    try {
      BufferedImage bi = ImageIO.read(stream);
      //PImage outgoing = new PImage(bi.getWidth(), bi.getHeight());
      PImage outgoing = new PImageAWT(bi);
      outgoing.parent = sketch;

      //bi.getRGB(0, 0, outgoing.width, outgoing.height,
      //          outgoing.pixels, 0, outgoing.width);

      // check the alpha for this image
      // was gonna call getType() on the image to see if RGB or ARGB,
      // but it's not actually useful, since gif images will come through
      // as TYPE_BYTE_INDEXED, which means it'll still have to check for
      // the transparency. also, would have to iterate through all the other
      // types and guess whether alpha was in there, so.. just gonna stick
      // with the old method.
      outgoing.checkAlpha();

      stream.close();
      // return the image
      return outgoing;

    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }


  static public void initRun() {
    // Supposed to help with flicker, but no effect on OS X.
    // TODO IIRC this helped on Windows, but need to double check.
    System.setProperty("sun.awt.noerasebackground", "true");

    // Remove 60fps limit on the JavaFX "pulse" timer
    System.setProperty("javafx.animation.fullspeed", "true");

    // Catch any HeadlessException to provide more useful feedback
    try {
      // Call validate() while resize events are in progress
      Toolkit.getDefaultToolkit().setDynamicLayout(true);
    } catch (HeadlessException e) {
      System.err.println("Cannot run sketch without a display. Read this for possible solutions:");
      System.err.println("https://github.com/processing/processing/wiki/Running-without-a-Display");
      System.exit(1);
    }
  }


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


  // TODO maybe call this with reflection from inside PApplet?
  // longer term, develop a more general method for other platforms
  static public File getWindowsDesktop() {
    return FileSystemView.getFileSystemView().getHomeDirectory();
  }


  static public boolean openLink(String url) {
    try {
      if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(new URI(url));
        return true;
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    return false;
  }
}