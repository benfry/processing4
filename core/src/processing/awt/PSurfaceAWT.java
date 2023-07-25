/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2014-23 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.awt;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.core.PSurfaceNone;
import processing.event.Event;
import processing.event.KeyEvent;
import processing.event.MouseEvent;


public class PSurfaceAWT extends PSurfaceNone {
  GraphicsDevice displayDevice;

  // used for canvas to determine whether resizable or not
//  boolean resizable;  // default is false

  // Internally, we know it's always a JFrame (not just a Frame)
//  JFrame frame;
  // Trying Frame again with a11 to see if this avoids some Swing nastiness.
  // In the past, AWT Frames caused some problems on Windows and Linux,
  // but those may not be a problem for our reworked PSurfaceAWT class.
  JFrame frame;

  // Note that x and y may not be zero, depending on the display configuration
  Rectangle screenRect;

  // Used for resizing, at least on Windows insets size changes when
  // frame.setResizable() is called, and in resize listener we need
  // to know what size the window was before.
  Insets currentInsets = new Insets(0, 0, 0, 0);

  // 3.0a5 didn't use strategy, and active was shut off during init() w/ retina
//  boolean useStrategy = true;

  Canvas canvas;
//  Component canvas;

//  PGraphics graphics;  // moved to PSurfaceNone

  int sketchWidth;
  int sketchHeight;

  // int windowScaleFactor;
  final int windowScaleFactor = 1;


  public PSurfaceAWT(PGraphics graphics) {
    //this.graphics = graphics;
    super(graphics);

    /*
    if (checkRetina()) {
//      System.out.println("retina in use");

      // The active-mode rendering seems to be 2x slower, so disable it
      // with retina. On a non-retina machine, however, useActive seems
      // the only (or best) way to handle the rendering.
//      useActive = false;
//      canvas = new JPanel(true) {
//        @Override
//        public void paint(Graphics screen) {
////          if (!sketch.insideDraw) {
//          screen.drawImage(PSurfaceAWT.this.graphics.image, 0, 0, sketchWidth, sketchHeight, null);
////          }
//        }
//      };
      // Under 1.8 and the current 3.0a6 threading regime, active mode w/o
      // strategy is far faster, but perhaps only because it's blitting with
      // flicker--pushing pixels out before the screen has finished rendering.
//      useStrategy = false;
    }
    */
    canvas = new SmoothCanvas();
//    if (useStrategy) {
    //canvas.setIgnoreRepaint(true);
//    }

    // Pass tab key to the sketch, rather than moving between components
    canvas.setFocusTraversalKeysEnabled(false);

    canvas.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (!sketch.isLooping()) {
          // make sure this is a real resize event, not just initial setup
          // https://github.com/processing/processing/issues/3310
          Dimension canvasSize = canvas.getSize();
          if (canvasSize.width != sketch.sketchWidth() ||
              canvasSize.height != sketch.sketchHeight()) {
            sketch.redraw();
          }
        }
      }
    });
    addListeners();
  }


//  /**
//   * Handle grabbing the focus on startup. Other renderers can override this
//   * if handling needs to be different. For the AWT, the request is invoked
//   * later on the EDT. Other implementations may not require that, so the
//   * invokeLater() happens in here rather than requiring the caller to wrap it.
//   */
//  @Override
//  void requestFocus() {
////    System.out.println("requesFocus() outer " + EventQueue.isDispatchThread());
//    // for 2.0a6, moving this request to the EDT
//    EventQueue.invokeLater(new Runnable() {
//      public void run() {
//        // Call the request focus event once the image is sure to be on
//        // screen and the component is valid. The OpenGL renderer will
//        // request focus for its canvas inside beginDraw().
//        // http://java.sun.com/j2se/1.4.2/docs/api/java/awt/doc-files/FocusSpec.html
//        // Disabling for 0185, because it causes an assertion failure on OS X
//        // https://github.com/processing/processing/issues/297
//        //        requestFocus();
//
//        // Changing to this version for 0187
//        // https://github.com/processing/processing/issues/318
//        //requestFocusInWindow();
//
//        // For 3.0, just call this directly on the Canvas object
//        if (canvas != null) {
//          //System.out.println("requesting focus " + EventQueue.isDispatchThread());
//          //System.out.println("requesting focus " + frame.isVisible());
//          //canvas.requestFocusInWindow();
//          canvas.requestFocus();
//        }
//      }
//    });
//  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public class SmoothCanvas extends Canvas {
    private Dimension oldSize = new Dimension(0, 0);


    // Turns out getParent() returns a JPanel on a JFrame. Yech.
    public Frame getFrame() {
      return frame;
    }


    @Override
    public Dimension getPreferredSize() {
      return new Dimension(sketchWidth, sketchHeight);
    }


    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }


    @Override
    public Dimension getMaximumSize() {
      //return resizable ? super.getMaximumSize() : getPreferredSize();
      return frame.isResizable() ? super.getMaximumSize() : getPreferredSize();
    }


    @Override
    public void validate() {
      super.validate();
      Dimension newSize = getSize();
      if (!oldSize.equals(newSize)) {
        oldSize = newSize;
        sketch.setSize(newSize.width / windowScaleFactor, newSize.height / windowScaleFactor);
        render();
      }
    }


    @Override
    public void update(Graphics g) {
      paint(g);
    }


    @Override
    public void paint(Graphics screen) {
      render();
    }
  }


  synchronized protected void render() {
    if (canvas.isDisplayable() &&
        graphics.image != null) {
      if (canvas.getBufferStrategy() == null) {
        canvas.createBufferStrategy(2);
      }
      BufferStrategy strategy = canvas.getBufferStrategy();
      if (strategy != null) {
        // Render single frame
        do {
          // The following loop ensures that the contents of the drawing buffer
          // are consistent in case the underlying surface was recreated
          do {
            Graphics2D draw = (Graphics2D) strategy.getDrawGraphics();
            // draw to width/height, since this may be a 2x image
            draw.drawImage(graphics.image, 0, 0, sketchWidth, sketchHeight, null);
            draw.dispose();
          } while (strategy.contentsRestored());

          // Display the buffer
          strategy.show();

          // Repeat the rendering if the drawing buffer was lost
        } while (strategy.contentsLost());
      }
    }
  }


  @Override
  public void selectInput(String prompt, String callback,
                          File file, Object callbackObject) {
    ShimAWT.selectInput(prompt, callback, file, callbackObject);
  }


  @Override
  public void selectOutput(String prompt, String callback,
                           File file, Object callbackObject) {
    ShimAWT.selectOutput(prompt, callback, file, callbackObject);
  }


  @Override
  public void selectFolder(String prompt, String callback,
                           File file, Object callbackObject) {
    ShimAWT.selectFolder(prompt, callback, file, callbackObject);
  }


  // what needs to happen here?
  @Override
  public void initOffscreen(PApplet sketch) {
    this.sketch = sketch;
  }


  @Override
  public void initFrame(final PApplet sketch) {/*, int backgroundColor,
                        int deviceIndex, boolean fullScreen, boolean spanDisplays) {*/
    this.sketch = sketch;

    GraphicsEnvironment environment =
      GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice defaultDevice =
      environment.getDefaultScreenDevice();

    int displayNum = sketch.sketchDisplay();
//    System.out.println("display from sketch is " + displayNum);
    if (displayNum > 0) {  // if -1, use the default device
      GraphicsDevice[] devices = environment.getScreenDevices();
      if (displayNum <= devices.length) {
        displayDevice = devices[displayNum - 1];
      } else {
        System.err.format("Display %d does not exist, " +
          "using the default display instead.%n", displayNum);
        if (devices.length > 1) {
          System.err.println("Available displays:");
          // The code below is cribbed from the version in PreferencesFrame,
          // though it uses \u00d7 and removes the space between because
          // it's printing in a monospace font to the console.
          for (int i = 0; i < devices.length; i++) {
            DisplayMode mode = devices[i].getDisplayMode();
            // \u00d7 supported more widely than \u2715 (and a better size)
            String title = String.format("%d (%d\u00d7%d)",
                i + 1, mode.getWidth(), mode.getHeight());
            if (devices[i] == defaultDevice) {
              title += " default";
            }
            System.err.println(title);
          }
        }
      }
    }
    if (displayDevice == null) {
      displayDevice = defaultDevice;
    }

    // Need to save the window bounds at full screen,
    // because pack() will cause the bounds to go to zero.
    // https://download.processing.org/bugzilla/923.html
    boolean spanDisplays = sketch.sketchDisplay() == PConstants.SPAN;
    screenRect = spanDisplays ? getDisplaySpan() :
      displayDevice.getDefaultConfiguration().getBounds();
    // DisplayMode doesn't work here, because we can't get the upper-left
    // corner of the display, which is important for multi-display setups.

    // Set the displayWidth/Height variables inside PApplet, so that they're
    // usable and can even be returned by the sketchWidth()/Height() methods.
    sketch.displayWidth = screenRect.width;
    sketch.displayHeight = screenRect.height;

    // windowScaleFactor = PApplet.platform == PConstants.MACOS ?
    //     1 : sketch.pixelDensity;

    sketchWidth = sketch.sketchWidth() * windowScaleFactor;
    sketchHeight = sketch.sketchHeight() * windowScaleFactor;

    boolean fullScreen = sketch.sketchFullScreen();
    // Removing the section below because sometimes people want to do the
    // full screen size in a window, and it also breaks insideSettings().
    // With 3.x, fullScreen() is so easy, that it's just better that way.
    // https://github.com/processing/processing/issues/3545
    /*
    // Sketch has already requested to be the same as the screen's
    // width and height, so let's roll with full screen mode.
    if (screenRect.width == sketchWidth &&
        screenRect.height == sketchHeight) {
      fullScreen = true;
      sketch.fullScreen();  // won't change the renderer
    }
    */

    if (fullScreen || spanDisplays) {
      sketchWidth = screenRect.width;
      sketchHeight = screenRect.height;
    }

    // Using a JFrame fixes a Windows problem with Present mode. This might
    // be our error, but usually this is the sort of crap we usually get from
    // OS X. It's time for a turnaround: Redmond is thinking different too!
    // https://github.com/processing/processing/issues/1955
    frame = new JFrame(displayDevice.getDefaultConfiguration());
//    frame = new Frame(displayDevice.getDefaultConfiguration());
//    // Default Processing gray, which will be replaced below if another
//    // color is specified on the command line (i.e. in the prefs).
//    ((JFrame) frame).getContentPane().setBackground(WINDOW_BGCOLOR);
//    // Cannot call setResizable(false) until later due to OS X (issue #467)

//    // Removed code above, also removed from what's now in the placeXxxx()
//    // methods. Not sure why it was being double-set; hopefully anachronistic.
//    if (backgroundColor == 0) {
//      backgroundColor = WINDOW_BGCOLOR;
//    }
    final Color windowColor = new Color(sketch.sketchWindowColor(), false);
    frame.getContentPane().setBackground(windowColor);

    // Put the p5 logo in the Frame's corner to override the Java coffee cup.
    setProcessingIcon(frame);

    // For 0149, moving this code (up to the pack() method) before init().
    // For OpenGL (and perhaps other renderers in the future), a peer is
    // needed before a GLDrawable can be created. So pack() needs to be
    // called on the Frame before init(), which itself calls size(),
    // and launches the Thread that will kick off setup().
    // https://download.processing.org/bugzilla/891.html
    // https://download.processing.org/bugzilla/908.html

    frame.add(canvas);
    setSize(sketchWidth / windowScaleFactor, sketchHeight / windowScaleFactor);

    /*
    if (fullScreen) {
      // Called here because the graphics device is needed before we can
      // determine whether the sketch wants size(displayWidth, displayHeight),
      // and getting the graphics device will be PSurface-specific.
      PApplet.hideMenuBar();

      // Tried to use this to fix the 'present' mode issue.
      // Did not help, and the screenRect setup seems to work fine.
      //frame.setExtendedState(Frame.MAXIMIZED_BOTH);

      // https://github.com/processing/processing/pull/3162
      frame.dispose();  // release native resources, allows setUndecorated()
      frame.setUndecorated(true);
      // another duplicate?
//      if (backgroundColor != null) {
//        frame.getContentPane().setBackground(backgroundColor);
//      }
      // this may be the bounds of all screens
      frame.setBounds(screenRect);
      // will be set visible in placeWindow() [3.0a10]
      //frame.setVisible(true);  // re-add native resources
    }
    */
    frame.setLayout(null);

    // Need to pass back our new sketchWidth/Height here, because it may have
    // been overridden by numbers we calculated above if fullScreen and/or
    // spanScreens was in use.
//    pg = sketch.makePrimaryGraphics(sketchWidth, sketchHeight);
//    pg = sketch.makePrimaryGraphics();

    // resize sketch to sketchWidth/sketchHeight here

    if (fullScreen) {
      frame.invalidate();
//    } else {
//      frame.pack();
    }

    // insufficient, places the 100x100 sketches offset strangely
    //frame.validate();

    // disabling resize has to happen after pack() to avoid apparent Apple bug
    // https://github.com/processing/processing/issues/506
    frame.setResizable(false);

    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        sketch.exit();  // don't quit, need to just shut everything down (0133)
      }
    });

//    sketch.setFrame(frame);
  }


  @Override
  public Object getNative() {
    return canvas;
  }


//  public Toolkit getToolkit() {
//    return canvas.getToolkit();
//  }


  /** Set the window (and dock, or whatever necessary) title. */
  @Override
  public void setTitle(String title) {
    frame.setTitle(title);
    // Workaround for apparent Java bug on OS X?
    // https://github.com/processing/processing/issues/3472
    if (cursorVisible &&
        (PApplet.platform == PConstants.MACOS) &&
        (cursorType != PConstants.ARROW)) {
      hideCursor();
      showCursor();
    }
  }


  /** Set true if we want to resize things (default is not resizable) */
  @Override
  public void setResizable(boolean resizable) {
    //this.resizable = resizable;  // really only used for canvas

    if (frame != null) {
      frame.setResizable(resizable);
    }
  }


  @Override
  public void setIcon(PImage image) {
    Image awtImage = (Image) image.getNative();

    if (PApplet.platform != PConstants.MACOS) {
      frame.setIconImage(awtImage);

    } else {
      try {
        final String td = "processing.core.ThinkDifferent";
        Class<?> thinkDifferent =
          Thread.currentThread().getContextClassLoader().loadClass(td);
        Method method =
          thinkDifferent.getMethod("setIconImage", Image.class);
        method.invoke(null, awtImage);
      } catch (Exception e) {
        e.printStackTrace();  // That's unfortunate
      }
    }
  }


  @Override
  public void setAlwaysOnTop(boolean always) {
    frame.setAlwaysOnTop(always);
  }


  @Override
  public void setLocation(int x, int y) {
    frame.setLocation(x, y);
  }


  List<Image> iconImages;

  protected void setProcessingIcon(Frame frame) {
    // On OS X, this only affects what shows up in the dock when minimized.
    // So replacing it is actually a step backwards. Brilliant.
    if (PApplet.platform != PConstants.MACOS) {
      //Image image = Toolkit.getDefaultToolkit().createImage(ICON_IMAGE);
      //frame.setIconImage(image);
      try {
        if (iconImages == null) {
          iconImages = new ArrayList<>();
          final int[] sizes = { 16, 32, 48, 64, 128, 256, 512 };

          for (int sz : sizes) {
            URL url = PApplet.class.getResource("/icon/icon-" + sz + ".png");
            Image image = Toolkit.getDefaultToolkit().getImage(url);
            iconImages.add(image);
          }
        }
        frame.setIconImages(iconImages);

      } catch (Exception ignored) { }  // harmless; keep this to ourselves

    } else {  // handle OS X differently
      if (!dockIconSpecified()) {  // don't override existing -Xdock param
        // On OS X, set this for AWT surfaces, which handles the dock image
        // as well as the cmd-tab image that's shown. Just one size, I guess.
        URL url = PApplet.class.getResource("/icon/icon-512.png");
        // Seems dangerous to have this in code instead of using reflection, no?
        //ThinkDifferent.setIconImage(Toolkit.getDefaultToolkit().getImage(url));
        try {
          final String td = "processing.core.ThinkDifferent";
          Class<?> thinkDifferent =
            Thread.currentThread().getContextClassLoader().loadClass(td);
          Method method =
            thinkDifferent.getMethod("setIconImage", Image.class);
          method.invoke(null, Toolkit.getDefaultToolkit().getImage(url));
        } catch (Exception e) {
          e.printStackTrace();  // That's unfortunate
        }
      }
    }
  }


  /**
   * @return true if -Xdock:icon was specified on the command line
   */
  private boolean dockIconSpecified() {
    // TODO This is incomplete... Haven't yet found a way to figure out if
    //      the app has an icns file specified already. Help?
    List<String> jvmArgs =
      ManagementFactory.getRuntimeMXBean().getInputArguments();
    for (String arg : jvmArgs) {
      if (arg.startsWith("-Xdock:icon")) {
        return true;  // dock image already set
      }
    }
    return false;
  }


  @Override
  public void setVisible(boolean visible) {
    frame.setVisible(visible);

    // Generally useful whenever setting the frame visible
    if (canvas != null) {
      //canvas.requestFocusInWindow();
      canvas.requestFocus();
    }

    // removing per https://github.com/processing/processing/pull/3162
    // can remove the code below once 3.0a6 is tested and behaving
/*
    if (visible && PApplet.platform == PConstants.LINUX) {
      // Linux doesn't deal with insets the same way. We get fake insets
      // earlier, and then the window manager will slap its own insets
      // onto things once the frame is realized on the screen. Awzm.
      if (PApplet.platform == PConstants.LINUX) {
        Insets insets = frame.getInsets();
        frame.setSize(Math.max(sketchWidth, MIN_WINDOW_WIDTH) +
                      insets.left + insets.right,
                      Math.max(sketchHeight, MIN_WINDOW_HEIGHT) +
                      insets.top + insets.bottom);
      }
    }
*/
  }


  //public void placeFullScreen(boolean hideStop) {
  @Override
  public void placePresent(int stopColor) {
    setFullFrame();

    // After the pack(), the screen bounds are gonna be 0s
//    frame.setBounds(screenRect);  // already called in setFullFrame()
    canvas.setBounds((screenRect.width - sketchWidth) / 2,
                     (screenRect.height - sketchHeight) / 2,
                     sketchWidth, sketchHeight);

//    if (PApplet.platform == PConstants.MACOSX) {
//      macosxFullScreenEnable(frame);
//      macosxFullScreenToggle(frame);
//    }

    if (stopColor != 0) {
      Label label = new Label("stop");
      label.setForeground(new Color(stopColor, false));
      label.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
          sketch.exit();
        }
      });
      frame.add(label);

      Dimension labelSize = label.getPreferredSize();
      // sometimes shows up truncated on mac
      //System.out.println("label width is " + labelSize.width);
      labelSize = new Dimension(100, labelSize.height);
      label.setSize(labelSize);
      label.setLocation(20, screenRect.height - labelSize.height - 20);
    }
  }


  private void setCanvasSize() {
    int contentW = Math.max(sketchWidth, MIN_WINDOW_WIDTH);
    int contentH = Math.max(sketchHeight, MIN_WINDOW_HEIGHT);

    canvas.setBounds((contentW - sketchWidth)/2,
                     (contentH - sketchHeight)/2,
                     sketchWidth, sketchHeight);
  }


  /** Resize frame for these sketch (canvas) dimensions. */
  private Dimension setFrameSize() {  //int sketchWidth, int sketchHeight) {
    // https://github.com/processing/processing/pull/3162
    frame.addNotify();  // using instead of show() to add the peer [fry]

//    System.out.format("setting frame size %d %d %n", sketchWidth, sketchHeight);
//    new Exception().printStackTrace(System.out);
    currentInsets = frame.getInsets();
    int windowW = Math.max(sketchWidth, MIN_WINDOW_WIDTH) +
      currentInsets.left + currentInsets.right;
    int windowH = Math.max(sketchHeight, MIN_WINDOW_HEIGHT) +
      currentInsets.top + currentInsets.bottom;
    frame.setSize(windowW, windowH);
    return new Dimension(windowW, windowH);
  }


  private void setFrameCentered() {
    // Can't use frame.setLocationRelativeTo(null) because it sends the
    // frame to the main display, which undermines the --display setting.
    frame.setLocation(screenRect.x + (screenRect.width - sketchWidth) / 2,
                      screenRect.y + (screenRect.height - sketchHeight) / 2);
  }


  /** Hide the menu bar, make the Frame undecorated, set it to screenRect. */
  private void setFullFrame() {
    // Called here because the graphics device is needed before we can
    // determine whether the sketch wants size(displayWidth, displayHeight),
    // and getting the graphics device will be PSurface-specific.
    PApplet.hideMenuBar();

    // Tried to use this to fix the 'present' mode issue.
    // Did not help, and the screenRect setup seems to work fine.
    //frame.setExtendedState(Frame.MAXIMIZED_BOTH);

    // https://github.com/processing/processing/pull/3162
    //frame.dispose();  // release native resources, allows setUndecorated()
    frame.removeNotify();
    frame.setUndecorated(true);
    frame.addNotify();

    // this may be the bounds of all screens
    frame.setBounds(screenRect);
    // will be set visible in placeWindow() [3.0a10]
    //frame.setVisible(true);  // re-add native resources
  }


  @Override
  public void placeWindow(int[] location, int[] editorLocation) {
    //Dimension window = setFrameSize(sketchWidth, sketchHeight);
    Dimension window = setFrameSize(); //sketchWidth, sketchHeight);

    int contentW = Math.max(sketchWidth, MIN_WINDOW_WIDTH);
    int contentH = Math.max(sketchHeight, MIN_WINDOW_HEIGHT);

    if (sketch.sketchFullScreen()) {
      setFullFrame();
    }

    // Ignore placement of previous window and editor when full screen
    if (!sketch.sketchFullScreen()) {
      if (location != null) {
        // a specific location was received from the Runner
        // (sketch has been run more than once, user placed window)
        frame.setLocation(location[0], location[1]);

      } else if (editorLocation != null) {
        int locationX = editorLocation[0] - 20;
        int locationY = editorLocation[1];

        if (locationX - window.width > 10) {
          // if it fits to the left of the window
          frame.setLocation(locationX - window.width, locationY);

        } else {  // doesn't fit
          // if it fits inside the editor window,
          // offset slightly from upper lefthand corner
          // so that it's plunked inside the text area
          //locationX = editorLocation[0] + 66;
          //locationY = editorLocation[1] + 66;
          locationX = (sketch.displayWidth - window.width) / 2;
          locationY = (sketch.displayHeight - window.height) / 2;

          /*
          if ((locationX + window.width > sketch.displayWidth - 33) ||
            (locationY + window.height > sketch.displayHeight - 33)) {
            // otherwise center on screen
            locationX = (sketch.displayWidth - window.width) / 2;
            locationY = (sketch.displayHeight - window.height) / 2;
          }
          */
          frame.setLocation(locationX, locationY);
        }
      } else {  // just center on screen
        setFrameCentered();
      }
      Point frameLoc = frame.getLocation();
      if (frameLoc.y < 0) {
        // Windows actually allows you to place frames where they can't be
        // closed. Awesome. https://download.processing.org/bugzilla/1508.html
        frame.setLocation(frameLoc.x, 30);
      }
      // make sure that windowX and windowY are set on startup
      sketch.postWindowMoved(frame.getX(), frame.getY());
    }

    canvas.setBounds((contentW - sketchWidth)/2,
                     (contentH - sketchHeight)/2,
                     sketchWidth, sketchHeight);

    // handle frame resizing events
    setupFrameResizeListener();

    /*
    // If displayable() is false, then PSurfaceNone should be used, but...
    if (sketch.getGraphics().displayable()) {
      frame.setVisible(true);
//      System.out.println("setting visible on EDT? " + EventQueue.isDispatchThread());
      //requestFocus();
//      if (canvas != null) {
//        //canvas.requestFocusInWindow();
//        canvas.requestFocus();
//      }
    }
    */
//    if (sketch.getGraphics().displayable()) {
//      setVisible(true);
//    }
  }


  // needs to resize the frame, which will resize the canvas, and so on...
  @Override
  public void setSize(int wide, int high) {
    // When the surface is set to resizable via surface.setResizable(true),
    // a crash may occur if the user sets the window to size zero.
    // https://github.com/processing/processing/issues/5052
    if (high <= 0) {
      high = 1;
    }
    if (wide <= 0) {
      wide = 1;
    }

//    if (PApplet.DEBUG) {
//      //System.out.format("frame visible %b, setSize(%d, %d) %n", frame.isVisible(), wide, high);
//      new Exception(String.format("setSize(%d, %d)", wide, high)).printStackTrace(System.out);
//    }

    //if (wide == sketchWidth && high == sketchHeight) {  // doesn't work on launch
    if (wide == sketch.width && high == sketch.height &&
        (frame == null || currentInsets.equals(frame.getInsets()))) {
//      if (PApplet.DEBUG) {
//        new Exception("w/h unchanged " + wide + " " + high).printStackTrace(System.out);
//      }
      return;  // unchanged, don't rebuild everything
    }

    sketchWidth = wide * windowScaleFactor;
    sketchHeight = high * windowScaleFactor;

//    canvas.setSize(wide, high);
//    frame.setSize(wide, high);
    if (frame != null) {  // skip if just a canvas
      setFrameSize(); //wide, high);
    }
    setCanvasSize();
//    if (frame != null) {
//      frame.setLocationRelativeTo(null);
//    }

    //initImage(graphics, wide, high);

    //throw new RuntimeException("implement me, see readme.md");
    sketch.setSize(wide, high);
//    sketch.width = wide;
//    sketch.height = high;

    // set PGraphics variables for width/height/pixelWidth/pixelHeight
    graphics.setSize(wide, high);
//    System.out.println("out of setSize()");
  }


  //public void initImage(PGraphics gr, int wide, int high) {
  /*
  @Override
  public void initImage(PGraphics graphics) {
    GraphicsConfiguration gc = canvas.getGraphicsConfiguration();
    // If not realized (off-screen, i.e the Color Selector Tool), gc will be null.
    if (gc == null) {
      System.err.println("GraphicsConfiguration null in initImage()");
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
    }

    // Formerly this was broken into separate versions based on offscreen or
    // not, but we may as well create a compatible image; it won't hurt, right?
    int wide = graphics.width * graphics.pixelFactor;
    int high = graphics.height * graphics.pixelFactor;
    graphics.image = gc.createCompatibleImage(wide, high);
  }
  */


//  @Override
//  public Component getComponent() {
//    return canvas;
//  }


//  @Override
//  public void setSmooth(int level) {
//  }


  /*
  private boolean checkRetina() {
    if (PApplet.platform == PConstants.MACOSX) {
      // This should probably be reset each time there's a display change.
      // A 5-minute search didn't turn up any such event in the Java 7 API.
      // Also, should we use the Toolkit associated with the editor window?
      final String javaVendor = System.getProperty("java.vendor");
      if (javaVendor.contains("Oracle")) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();

        try {
          Field field = device.getClass().getDeclaredField("scale");
          if (field != null) {
            field.setAccessible(true);
            Object scale = field.get(device);

            if (scale instanceof Integer && ((Integer)scale).intValue() == 2) {
              return true;
            }
          }
        } catch (Exception ignore) { }
      }
    }
    return false;
  }
  */


  /** Get the bounds rectangle for all displays. */
  static Rectangle getDisplaySpan() {
    Rectangle bounds = new Rectangle();
    GraphicsEnvironment environment =
      GraphicsEnvironment.getLocalGraphicsEnvironment();
    for (GraphicsDevice device : environment.getScreenDevices()) {
      for (GraphicsConfiguration config : device.getConfigurations()) {
        Rectangle2D.union(bounds, config.getBounds(), bounds);
      }
    }
    return bounds;
  }


  /*
  private void checkDisplaySize() {
    if (canvas.getGraphicsConfiguration() != null) {
      GraphicsDevice displayDevice = getGraphicsConfiguration().getDevice();

      if (displayDevice != null) {
        Rectangle screenRect =
          displayDevice.getDefaultConfiguration().getBounds();

        displayWidth = screenRect.width;
        displayHeight = screenRect.height;
      }
    }
  }
  */


//  /**
//   * Set this sketch to communicate its state back to the PDE.
//   * <p/>
//   * This uses the stderr stream to write positions of the window
//   * (so that it will be saved by the PDE for the next run) and
//   * notify on quit. See more notes in the Worker class.
//   */
//  @Override
//  public void setupExternalMessages() {
//    frame.addComponentListener(new ComponentAdapter() {
//      @Override
//      public void componentMoved(ComponentEvent e) {
//        Point where = ((Frame) e.getSource()).getLocation();
//        //sketch.frameMoved(where.x, where.y);
//        sketch.queueWindowPosition(where.x, where.y);
//      }
//    });
//  }


  /**
   * Set up a listener that will fire proper component resize events
   * in cases where frame.setResizable(true) is called.
   */
  private void setupFrameResizeListener() {
    // Detect when the frame is resized to handle a macOS bug:
    // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=8036935
    frame.addWindowStateListener(e -> {
      // This seems to be firing when dragging the window on OS X
      // https://github.com/processing/processing/issues/3092
      if (Frame.MAXIMIZED_BOTH == e.getNewState()) {
        // Supposedly, sending the frame to back and then front is a
        // workaround for this bug:
        // http://stackoverflow.com/a/23897602
        // but is not working for me...
        //frame.toBack();
        //frame.toFront();
        // Packing the frame works, but that causes the window to collapse
        // on OS X when the window is dragged. Changing to addNotify() for
        // https://github.com/processing/processing/issues/3092
        //frame.pack();
        frame.addNotify();
      }
    });

    frame.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        // Ignore bad resize events fired during setup to fix
        // https://download.processing.org/bugzilla/341.html
        // This should also fix the blank screen on Linux bug
        // https://download.processing.org/bugzilla/282.html
        if (frame.isResizable()) {
          // might be multiple resize calls before visible (i.e. first
          // when pack() is called, then when it's resized for use).
          // ignore them because it's not the user resizing things.
          Frame farm = (Frame) e.getComponent();
          if (farm.isVisible()) {
            Dimension windowSize = farm.getSize();
            int x = farm.getX() + currentInsets.left;
            int y = farm.getY() + currentInsets.top;

            // JFrame (unlike java.awt.Frame) doesn't include the left/top
            // insets for placement (though it does seem to need them for
            // overall size of the window. Perhaps JFrame sets its coord
            // system so that (0, 0) is always the upper-left of the content
            // area. Which seems nice, but breaks any f*ing AWT-based code.
            int w = windowSize.width - currentInsets.left - currentInsets.right;
            int h = windowSize.height - currentInsets.top - currentInsets.bottom;
            setSize(w / windowScaleFactor, h / windowScaleFactor);
            // notify the sketch that the window has been resized
            sketch.postWindowResized(w / windowScaleFactor, h / windowScaleFactor);

            // correct the location when inset size changes
            setLocation(x - currentInsets.left, y - currentInsets.top);
            //sketch.postWindowMoved(x - currentInsets.left, y - currentInsets.top);
            sketch.postWindowMoved(x, y);  // presumably user wants drawing area
          }
        }
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        Point where = ((Frame) e.getSource()).getLocation();
        sketch.postWindowMoved(where.x, where.y);
      }
    });
  }


//  /**
//   * (No longer in use) Use reflection to call
//   * <code>com.apple.eawt.FullScreenUtilities.setWindowCanFullScreen(window, true);</code>
//   */
//  static void macosxFullScreenEnable(Window window) {
//    try {
//      Class<?> util = Class.forName("com.apple.eawt.FullScreenUtilities");
//      Class params[] = new Class[] { Window.class, Boolean.TYPE };
//      Method method = util.getMethod("setWindowCanFullScreen", params);
//      method.invoke(util, window, true);
//
//    } catch (ClassNotFoundException cnfe) {
//      // ignored
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//  }
//
//
//  /**
//   * (No longer in use) Use reflection to call
//   * <code>com.apple.eawt.Application.getApplication().requestToggleFullScreen(window);</code>
//   */
//  static void macosxFullScreenToggle(Window window) {
//    try {
//      Class<?> appClass = Class.forName("com.apple.eawt.Application");
//
//      Method getAppMethod = appClass.getMethod("getApplication");
//      Object app = getAppMethod.invoke(null, new Object[0]);
//
//      Method requestMethod =
//        appClass.getMethod("requestToggleFullScreen", Window.class);
//      requestMethod.invoke(app, window);
//
//    } catch (ClassNotFoundException cnfe) {
//      // ignored
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//  }


  //////////////////////////////////////////////////////////////


  /*
  // disabling for now; requires Java 1.7 and "precise" semantics are odd...
  // returns 0.1 for tick-by-tick scrolling on OS X, but it's not a matter of
  // calling ceil() on the value: 1.5 goes to 1, but 2.3 goes to 2.
  // "precise" is a whole different animal, so add later API to shore that up.
  static protected Method preciseWheelMethod;
  static {
    try {
      preciseWheelMethod = MouseWheelEvent.class.getMethod("getPreciseWheelRotation", new Class[] { });
    } catch (Exception e) {
      // ignored, the method will just be set to null
    }
  }
  */


  /**
   * Figure out how to process a mouse event. When loop() has been
   * called, the events will be queued up until drawing is complete.
   * If noLoop() has been called, then events will happen immediately.
   */
  protected void nativeMouseEvent(java.awt.event.MouseEvent nativeEvent) {
    // the 'amount' is the number of button clicks for a click event,
    // or the number of steps/clicks on the wheel for a mouse wheel event.
    int peCount = nativeEvent.getClickCount();

    int peAction = 0;
    switch (nativeEvent.getID()) {
    case java.awt.event.MouseEvent.MOUSE_PRESSED:
      peAction = MouseEvent.PRESS;
      break;
    case java.awt.event.MouseEvent.MOUSE_RELEASED:
      peAction = MouseEvent.RELEASE;
      break;
    case java.awt.event.MouseEvent.MOUSE_CLICKED:
      peAction = MouseEvent.CLICK;
      break;
    case java.awt.event.MouseEvent.MOUSE_DRAGGED:
      peAction = MouseEvent.DRAG;
      break;
    case java.awt.event.MouseEvent.MOUSE_MOVED:
      peAction = MouseEvent.MOVE;
      break;
    case java.awt.event.MouseEvent.MOUSE_ENTERED:
      peAction = MouseEvent.ENTER;
      break;
    case java.awt.event.MouseEvent.MOUSE_EXITED:
      peAction = MouseEvent.EXIT;
      break;
    //case java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL:
    case java.awt.event.MouseEvent.MOUSE_WHEEL:
      peAction = MouseEvent.WHEEL;
      /*
      if (preciseWheelMethod != null) {
        try {
          peAmount = ((Double) preciseWheelMethod.invoke(nativeEvent, (Object[]) null)).floatValue();
        } catch (Exception e) {
          preciseWheelMethod = null;
        }
      }
      */
      peCount = ((MouseWheelEvent) nativeEvent).getWheelRotation();
      break;
    }

    // Switching to getModifiersEx() for 4.0a2 because of Java 9 deprecation.
    // Had trouble with this in the past and rolled it back because it was
    // optional at the time. This time around, just need to iron out the issue.
    // https://github.com/processing/processing/issues/1332
    // https://github.com/processing/processing/issues/1370
    int modifiers = nativeEvent.getModifiersEx();

    int peButton = 0;
    // Because there isn't a button "press" associated with drag,
    // pass this over to SwingUtilities to properly decode the button.
    // https://github.com/processing/processing4/issues/281
    if (SwingUtilities.isLeftMouseButton(nativeEvent)) {
      peButton = PConstants.LEFT;
    } else if (SwingUtilities.isMiddleMouseButton(nativeEvent)) {
      peButton = PConstants.CENTER;
    } else if (SwingUtilities.isRightMouseButton(nativeEvent)) {
      peButton = PConstants.RIGHT;
    }

    // getModifiersEx() has different constants, so need to re-map
    // to the masks we're using in processing.event.Event
    int peModifiers = 0;
    if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
      peModifiers |= Event.SHIFT;
    }
    if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
      peModifiers |= Event.CTRL;
    }
    if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
      peModifiers |= Event.META;
    }
    if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
      peModifiers |= Event.ALT;
    }

    sketch.postEvent(new MouseEvent(nativeEvent, nativeEvent.getWhen(),
                                    peAction, peModifiers,
                                    nativeEvent.getX() / windowScaleFactor,
                                    nativeEvent.getY() / windowScaleFactor,
                                    peButton,
                                    peCount));
  }


  protected void nativeKeyEvent(java.awt.event.KeyEvent event) {
    int peAction = 0;
    switch (event.getID()) {
    case java.awt.event.KeyEvent.KEY_PRESSED:
      peAction = KeyEvent.PRESS;
      break;
    case java.awt.event.KeyEvent.KEY_RELEASED:
      peAction = KeyEvent.RELEASE;
      break;
    case java.awt.event.KeyEvent.KEY_TYPED:
      peAction = KeyEvent.TYPE;
      break;
    }

    int modifiers = event.getModifiersEx();

    // getModifiersEx() has different constants, so need to re-map
    // to the masks we're using in processing.event.Event.
    // If authors want more detail, they can use the native object.
    int peModifiers = 0;
    if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
      peModifiers |= Event.SHIFT;
    }
    if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
      peModifiers |= Event.CTRL;
    }
    if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
      peModifiers |= Event.META;
    }
    if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
      peModifiers |= Event.ALT;
    }
    sketch.postEvent(new KeyEvent(event, event.getWhen(),
                                  peAction, peModifiers,
                                  event.getKeyChar(), event.getKeyCode()));
  }


  // listeners, for all my men!
  protected void addListeners() {

    canvas.addMouseListener(new MouseListener() {

      public void mousePressed(java.awt.event.MouseEvent e) {
        nativeMouseEvent(e);
      }

      public void mouseReleased(java.awt.event.MouseEvent e) {
        nativeMouseEvent(e);
      }

      public void mouseClicked(java.awt.event.MouseEvent e) {
        nativeMouseEvent(e);
      }

      public void mouseEntered(java.awt.event.MouseEvent e) {
        nativeMouseEvent(e);
      }

      public void mouseExited(java.awt.event.MouseEvent e) {
        nativeMouseEvent(e);
      }
    });

    canvas.addMouseMotionListener(new MouseMotionListener() {

      public void mouseDragged(java.awt.event.MouseEvent e) {
        nativeMouseEvent(e);
      }

      public void mouseMoved(java.awt.event.MouseEvent e) {
        nativeMouseEvent(e);
      }
    });

    canvas.addMouseWheelListener(this::nativeMouseEvent);

    canvas.addKeyListener(new KeyListener() {

      public void keyPressed(java.awt.event.KeyEvent e) {
        nativeKeyEvent(e);
      }

      public void keyReleased(java.awt.event.KeyEvent e) {
        nativeKeyEvent(e);
      }

      public void keyTyped(java.awt.event.KeyEvent e) {
        nativeKeyEvent(e);
      }
    });

    canvas.addFocusListener(new FocusListener() {

      public void focusGained(FocusEvent e) {
        sketch.focused = true;
        sketch.focusGained();
      }

      public void focusLost(FocusEvent e) {
        sketch.focused = false;
        sketch.focusLost();
      }
    });
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  int cursorType = PConstants.ARROW;
  boolean cursorVisible = true;
  Cursor invisibleCursor;


  @Override
  public void setCursor(int kind) {
    // Swap the HAND cursor because MOVE doesn't seem to be available on OS X
    // https://github.com/processing/processing/issues/2358
    if (PApplet.platform == PConstants.MACOS && kind == PConstants.MOVE) {
      kind = PConstants.HAND;
    }
    //noinspection MagicConstant
    canvas.setCursor(Cursor.getPredefinedCursor(kind));
    cursorVisible = true;
    this.cursorType = kind;
  }


  @Override
  public void setCursor(PImage img, int x, int y) {
    // Don't set cursorType, instead use cursorType to save the last
    // regular cursor type used for when cursor() is called.
    //cursor_type = Cursor.CUSTOM_CURSOR;

    // this is a temporary workaround for the CHIP, will be removed
    Dimension cursorSize = Toolkit.getDefaultToolkit().getBestCursorSize(img.width, img.height);
    if (cursorSize.width == 0 || cursorSize.height == 0) {
      return;
    }

    Cursor cursor =
      canvas.getToolkit().createCustomCursor((Image) img.getNative(),
                                             new Point(x, y),
                                             "custom");
    canvas.setCursor(cursor);
    cursorVisible = true;
  }


  @Override
  public void showCursor() {
    // Maybe should always set here? Seems dangerous, since it's likely that
    // Java will set the cursor to something else on its own, and the sketch
    // will be stuck b/c p5 thinks the cursor is set to one particular thing.
    if (!cursorVisible) {
      cursorVisible = true;
      //noinspection MagicConstant
      canvas.setCursor(Cursor.getPredefinedCursor(cursorType));
    }
  }


  @Override
  public void hideCursor() {
    // Because the OS may have shown the cursor on its own,
    // don't return if 'cursorVisible' is set to true. [rev 0216]

    if (invisibleCursor == null) {
      BufferedImage cursorImg =
        new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
      // this is a temporary workaround for the CHIP, will be removed
      Dimension cursorSize = Toolkit.getDefaultToolkit().getBestCursorSize(16, 16);
      if (cursorSize.width == 0 || cursorSize.height == 0) {
        invisibleCursor = Cursor.getDefaultCursor();
      } else {
        invisibleCursor =
          canvas.getToolkit().createCustomCursor(cursorImg, new Point(8, 8), "blank");
      }
    }
    canvas.setCursor(invisibleCursor);
    cursorVisible = false;
  }


  @Override
  public boolean openLink(String url) {
    return ShimAWT.openLink(url);
  }


  @Override
  public Thread createThread() {
    return new AnimationThread() {
      @Override
      public void callDraw() {
        sketch.handleDraw();
        render();
      }
    };
  }
}
