package processing.app.ui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;

import java.io.File;
import java.io.FileOutputStream;

import javax.swing.JComponent;
import javax.swing.JFrame;

import processing.app.Platform;


/**
 * Show a splash screen window. Loosely based on SplashWindow.java
 * from Werner Randelshofer, but rewritten to use Swing because the
 * java.awt version doesn't render properly on Windows.
 */
public class Splash extends JFrame {
  static private Splash instance;


//  private Splash(File imageFile, boolean hidpi) {
  private Splash(File image2xFile) {
    // Putting this inside try/catch because it's not essential,
    // and it's definitely not essential enough to prevent startup.
    try {
      // change default java window icon to processing icon
      processing.app.ui.Toolkit.setIcon(this);
    } catch (Exception e) {
      // ignored
    }
    
    Image image2x =
      Toolkit.getDefaultToolkit().createImage(image2xFile.getAbsolutePath());

    MediaTracker tracker = new MediaTracker(this);
    tracker.addImage(image2x, 0);
    try {
      tracker.waitForID(0);
    } catch (InterruptedException ignored) { }

    if (tracker.isErrorID(0)) {  // abort on failure
      setSize(0,0);
      System.err.println("Warning: SplashWindow couldn't load splash image.");
      synchronized (this) {
        notifyAll();
      }
    } else {
      final int wide = image2x.getWidth(this) / 2;
      final int high = image2x.getHeight(this) / 2;

      JComponent comp = new JComponent() {
        public void paintComponent(Graphics g) {
          processing.app.ui.Toolkit.prepareGraphics(g);
          g.drawImage(image2x, 0, 0, wide, high, this);
        }

        public Dimension getPreferredSize() {
          return new Dimension(wide, high);
        }
      };
      comp.setSize(wide, high);
      setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
      getContentPane().add(comp);
      setUndecorated(true);  // before pack()
      pack();
      setLocationRelativeTo(null);  // center on screen
    }
  }


  /** Open a splash window using the specified image. */
//  static void showSplash(File imageFile, boolean hidpi) {
  static void showSplash(File image2xFile) {
    if (instance == null) {
      instance = new Splash(image2xFile);
      instance.setVisible(true);
    }
  }


  /** Closes the splash window when finished. */
  static void disposeSplash() {
    if (instance != null) {
      instance.dispose();
      instance = null;
    }
  }


  /**
   * Invokes the main method of the provided class name.
   * @param args the command line arguments
   */
  @SuppressWarnings("SameParameterValue")
  static void invokeMain(String className, String[] args) {
    try {
      Class.forName(className)
        .getMethod("main", String[].class)
        .invoke(null, new Object[] { args });

    } catch (Exception e) {
      throw new InternalError("Failed to invoke main method", e);
    }
  }


//  /**
//   * Load the optional properties.txt file from the 'lib' sub-folder
//   * that can be used to pass entries to System.properties.
//   */
//  static private void initProperties() {
//    try {
//      File propsFile = Platform.getContentFile("properties.txt");
//      if (propsFile != null && propsFile.exists()) {
//        Settings props = new Settings(propsFile);
//        for (Map.Entry<String, String> entry : props.getMap().entrySet()) {
//          System.setProperty(entry.getKey(), entry.getValue());
//        }
//      }
//    } catch (Exception e) {
//      // No crying over spilt milk, but...
//      e.printStackTrace();
//    }
//  }


  static public boolean getDisableHiDPI() {
    File propsFile = Platform.getContentFile("disable_hidpi");
    return propsFile != null && propsFile.exists();
  }


  // Should only be called from Windows, but not restricted to Windows
  // so not enforced. Unlikely to work on macOS because it modifies
  // a file inside the .app, but may be useful on Linux.
  static public void setDisableHiDPI(boolean disabled) {
    try {
      File propsFile = Platform.getContentFile("disable_hidpi");
      if (propsFile != null) {
        if (disabled) {
          if (!propsFile.exists()) {  // don't recreate if exists
            new FileOutputStream(propsFile).close();
          }
        } else if (propsFile.exists()) {
          boolean success = propsFile.delete();
          if (!success) {
            System.err.println("Could not delete disable_hidpi");
          }
        }
      }
    } catch (Exception e) {
      // No crying over spilt milk, but...
      e.printStackTrace();
    }
  }


  static public void main(String[] args) {
    // Has to be done before AWT is initialized, so the hack lives here
    // instead of Base or anywhere else that might make more sense.
    if (getDisableHiDPI()) {
      System.setProperty("sun.java2d.uiScale.enabled", "false");
    }
    try {
      showSplash(Platform.getContentFile("lib/about-2x.png"));
      invokeMain("processing.app.Base", args);
      disposeSplash();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
