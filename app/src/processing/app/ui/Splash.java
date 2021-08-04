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
  private final Image image;


  private Splash(File imageFile, boolean hidpi) {
    this.image =
      Toolkit.getDefaultToolkit().createImage(imageFile.getAbsolutePath());

    MediaTracker tracker = new MediaTracker(this);
    tracker.addImage(image,0);
    try {
      tracker.waitForID(0);
    } catch (InterruptedException ignored) { }

    if (tracker.isErrorID(0)) {
      // Abort on failure
      setSize(0,0);
      System.err.println("Warning: SplashWindow couldn't load splash image.");
      synchronized (this) {
        notifyAll();
      }
    } else {
      final int imgWidth = image.getWidth(this);
      final int imgHeight = image.getHeight(this);
      final int imgScale = hidpi ? 2 : 1;

      JComponent comp = new JComponent() {
        final int wide = imgWidth / imgScale;
        final int high = imgHeight / imgScale;

        public void paintComponent(Graphics g) {
          g.drawImage(image, 0, 0, wide, high, this);
        }

        public Dimension getPreferredSize() {
          return new Dimension(wide, high);
        }
      };
      comp.setSize(imgWidth, imgHeight);
      setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
      getContentPane().add(comp);
      setUndecorated(true);  // before pack()
      pack();
      setLocationRelativeTo(null);  // center on screen
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
      throw new InternalError("Failed to invoke main method", e);
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
