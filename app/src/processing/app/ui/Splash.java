package processing.app.ui;

import processing.app.Platform;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import javax.swing.JComponent;
import javax.swing.JFrame;
import java.io.File;


/**
 * Show a splash screen window. Loosely based on SplashWindow.java from
 * Werner Randelshofer, but rewritten to use Swing because the java.awt
 * version doesn't render properly on Windows.
 */
public class Splash extends JFrame {
  static private Splash instance;
  private Image image;


  private boolean paintCalled = false;

  private Splash(File imageFile, boolean hidpi) {
    this.image =
      Toolkit.getDefaultToolkit().createImage(imageFile.getAbsolutePath());

    // Load the image
    MediaTracker mt = new MediaTracker(this);
    mt.addImage(image,0);
    try {
      mt.waitForID(0);
    } catch(InterruptedException ie){}

    // Abort on failure
    if (mt.isErrorID(0)) {
      setSize(0,0);
      System.err.println("Warning: SplashWindow couldn't load splash image.");
      synchronized(this) {
        paintCalled = true;
        notifyAll();
      }
    } else {
      // Center the window on the screen
      final int imgWidth = image.getWidth(this);
      final int imgHeight = image.getHeight(this);
      final int imgScale = hidpi ? 2 : 1;

      setUndecorated(true);

      JComponent comp = new JComponent() {
        public void paintComponent(Graphics g) {
          System.out.println("drawing " + getSize() + " " + Splash.this.getSize());
          g.drawImage(image, 0, 0, imgWidth / imgScale, imgHeight / imgScale, this);
        }

        public Dimension getPreferredSize() {
          return new Dimension(imgWidth / imgScale, imgHeight / imgScale);
        }
      };
      comp.setSize(imgWidth, imgHeight);
      setLayout(new FlowLayout());
      getContentPane().add(comp);
      pack();

      setLocationRelativeTo(null);
    }
  }


  /** Open a splash window using the specified image. */
  static void showSplash(File imageFile, boolean hidpi) {
    if (instance == null) {
      instance = new Splash(imageFile, hidpi);
      instance.setVisible(true);
    }
  }


  /** Closes the splash window when finished. */
  static void disposeSplash() {
    if (instance != null) {
      //instance.getOwner().dispose();
      instance.dispose();
      instance = null;
    }
  }


  /**
   * Invokes the main method of the provided class name.
   * @param args the command line arguments
   */
  static void invokeMain(String className, String[] args) {
    try {
      Class.forName(className)
        .getMethod("main", new Class[] {String[].class})
        .invoke(null, new Object[] { args });

    } catch (Exception e) {
      InternalError error = new InternalError("Failed to invoke main method");
      error.initCause(e);
      throw error;
    }
  }


  static public void main(String[] args) {
    try {
      final boolean hidpi = processing.app.ui.Toolkit.highResImages();
      final String filename = "lib/about-" + (hidpi ? 2 : 1) + "x.png";
      File splashFile = Platform.getContentFile(filename);
      showSplash(splashFile, hidpi);
      invokeMain("processing.app.Base", args);
      disposeSplash();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
