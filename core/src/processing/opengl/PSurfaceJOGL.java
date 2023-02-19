/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-23 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

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

package processing.opengl;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;

import com.jogamp.common.util.IOUtil;
import com.jogamp.common.util.IOUtil.ClassResources;
import com.jogamp.nativewindow.NativeSurface;
import com.jogamp.nativewindow.ScalableSurface;
import com.jogamp.nativewindow.util.Dimension;
import com.jogamp.nativewindow.util.PixelFormat;
import com.jogamp.nativewindow.util.PixelRectangle;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.nativewindow.MutableGraphicsConfiguration;
import com.jogamp.nativewindow.WindowClosingProtocol;
import com.jogamp.newt.Display;
import com.jogamp.newt.Display.PointerIcon;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.newt.awt.NewtCanvasAWT;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.util.FPSAnimator;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.core.PSurface;
import processing.event.KeyEvent;
import processing.event.MouseEvent;
import processing.awt.PImageAWT;

// have this removed by 4.0 final
import processing.awt.ShimAWT;


public class PSurfaceJOGL implements PSurface {
  /** Selected GL profile */
  static public GLProfile profile;

  public PJOGL pgl;

  protected GLWindow window;
  protected FPSAnimator animator;
  protected Rectangle screenRect;

  private Thread drawExceptionHandler;

  protected PApplet sketch;
  protected PGraphics graphics;

  protected int sketchWidthRequested;
  protected int sketchHeightRequested;

  protected int sketchWidth;
  protected int sketchHeight;

  protected Display display;
  protected Screen screen;
  protected Rectangle displayRect;
  protected Throwable drawException;
  private final Object drawExceptionMutex = new Object();

  protected NewtCanvasAWT canvas;

  protected static GLAutoDrawable sharedDrawable;
  protected static ArrayList<FPSAnimator> animators = new ArrayList<>();
  private final static Object sharedSyncMutex = new Object();
  private Object syncMutex;

  protected int windowScaleFactor;

  protected float[] currentPixelScale = { 0, 0 };


  public PSurfaceJOGL(PGraphics graphics) {
    this.graphics = graphics;
    this.pgl = (PJOGL) ((PGraphicsOpenGL) graphics).pgl;
  }


  public void initOffscreen(PApplet sketch) {
    this.sketch = sketch;

    sketchWidth = sketch.sketchWidth();
    sketchHeight = sketch.sketchHeight();

    if (window != null) {
      canvas = new NewtCanvasAWT(window);
      canvas.setBounds(0, 0, window.getWidth(), window.getHeight());
      canvas.setFocusable(true);
    }
  }


  public void initFrame(PApplet sketch) {
    this.sketch = sketch;

    initIcons();
    // For 4.0.2, swapped the order to do initGL() before initDisplay()
    // https://github.com/processing/processing4/issues/544
    initGL();
    initDisplay();
    initWindow();
    initListeners();
    initAnimator();
  }


  public Object getNative() {
    return window;
  }


  protected void initDisplay() {
    display = NewtFactory.createDisplay(null);
    display.addReference();
    screen = NewtFactory.createScreen(display, 0);
    screen.addReference();

    int displayNum = sketch.sketchDisplay();
    displayRect = getDisplayBounds(displayNum);
  }


  // TODO This code is mostly copied from code found in PSurfaceAWT.
  //      It should probably be merged to avoid divergence. [fry 211122]
  static protected Rectangle getDisplayBounds(int displayNum) {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] awtDevices = ge.getScreenDevices();

    GraphicsDevice awtDisplayDevice = null;

    if (displayNum > 0) {  // if -1, use the default device
      if (displayNum <= awtDevices.length) {
        awtDisplayDevice = awtDevices[displayNum-1];
      } else {
        System.err.format("Display %d does not exist, " +
          "using the default display instead.%n", displayNum);
        for (int i = 0; i < awtDevices.length; i++) {
          System.err.format("Display %d is %s%n", i+1, awtDevices[i]);
        }
      }
//    } else if (0 < awtDevices.length) {
      // TODO this seems like a bad idea: in lots of situations [0] will *not*
      //      be the default device. Not sure why this was added instead of
      //      just using getDefaultScreenDevice() below. [fry 211122]
      // TODO Removing for 4.2, clean this code on next visit. [fry 230218]
//      awtDisplayDevice = awtDevices[0];
    }

    if (awtDisplayDevice == null) {
      awtDisplayDevice = ge.getDefaultScreenDevice();
    }

    //return awtDisplayDevice.getDefaultConfiguration().getBounds();
    Rectangle bounds = awtDisplayDevice.getDefaultConfiguration().getBounds();
    float uiScale = Float.parseFloat(System.getProperty("sun.java2d.uiScale", "1.0"));
    return new Rectangle((int) (uiScale * bounds.getX()),
              (int) (uiScale * bounds.getY()),
              (int) (uiScale * bounds.getWidth()),
              (int) (uiScale * bounds.getHeight()));
  }


  protected void initGL() {
    if (profile == null) {
      if (PJOGL.profile == 1) {
        try {
          profile = GLProfile.getGL2ES1();
        } catch (GLException ex) {
          profile = GLProfile.getMaxFixedFunc(true);
        }
      } else if (PJOGL.profile == 2) {
        try {
          profile = GLProfile.getGL2ES2();

          // workaround for https://jogamp.org/bugzilla/show_bug.cgi?id=1347
          if (!profile.isHardwareRasterizer()) {
            GLProfile hardware = GLProfile.getMaxProgrammable(true);
            if (hardware.isGL2ES2()) {
              profile = hardware;
            }
          }

        } catch (GLException ex) {
          profile = GLProfile.getMaxProgrammable(true);
        }
      } else if (PJOGL.profile == 3) {
        try {
          profile = GLProfile.getGL2GL3();
        } catch (GLException ex) {
          profile = GLProfile.getMaxProgrammable(true);
        }
        if (!profile.isGL3()) {
          PGraphics.showWarning("Requested profile GL3 but is not available, got: " + profile);
        }
      } else if (PJOGL.profile == 4) {
        try {
          profile = GLProfile.getGL4ES3();
        } catch (GLException ex) {
          profile = GLProfile.getMaxProgrammable(true);
        }
        if (!profile.isGL4()) {
          PGraphics.showWarning("Requested profile GL4 but is not available, got: " + profile);
        }
      } else throw new RuntimeException(PGL.UNSUPPORTED_GLPROF_ERROR);
    }

    // Setting up the desired capabilities;
    GLCapabilities caps = new GLCapabilities(profile);
    caps.setAlphaBits(PGL.REQUESTED_ALPHA_BITS);
    caps.setDepthBits(PGL.REQUESTED_DEPTH_BITS);
    caps.setStencilBits(PGL.REQUESTED_STENCIL_BITS);

//  caps.setPBuffer(false);
//  caps.setFBO(false);

//    pgl.reqNumSamples = PGL.smoothToSamples(graphics.smooth);
    caps.setSampleBuffers(true);
    caps.setNumSamples(PGL.smoothToSamples(graphics.smooth));
    caps.setBackgroundOpaque(true);
    caps.setOnscreen(true);
    pgl.setCaps(caps);

    if (sharedDrawable == null) {
      // Create a shared drawable to enable context sharing across multiple GL windows
      // https://jogamp.org/deployment/jogamp-next/javadoc/jogl/javadoc/com/jogamp/opengl/GLSharedContextSetter.html
      sharedDrawable = GLDrawableFactory.getFactory(profile).createDummyAutoDrawable(null, true, caps, null);
      sharedDrawable.display();
    }
  }


  // To properly deal with synchronization when context sharing across multiple drawables (windows), we need a
  // synchronization mutex object to ensure that rendering of each frame completes in their respective animator thread:
  // https://jogamp.org/deployment/jogamp-next/javadoc/jogl/javadoc/com/jogamp/opengl/GLSharedContextSetter.html#synchronization
  private Object getSyncMutex(GLAutoDrawable drawable) {
    pgl.getGL(drawable);
    if (pgl.needSharedObjectSync()) {
      syncMutex = sharedSyncMutex;
    } else {
      if (syncMutex == null) {
        syncMutex = new Object();
      }
    }
    return syncMutex;
  }


  protected void initWindow() {
    window = GLWindow.create(screen, pgl.getCaps());

    // Make sure that we pass the window close through to exit(), otherwise
    // we're likely to have OpenGL try to shut down halfway through rendering
    // a frame. Particularly problematic for complex/slow apps.
    // https://github.com/processing/processing/issues/4690
    window.setDefaultCloseOperation(WindowClosingProtocol.WindowClosingMode.DO_NOTHING_ON_CLOSE);

    // macOS pixel density is handled transparently by the OS
    windowScaleFactor =
      (PApplet.platform == PConstants.MACOS) ? 1 : sketch.pixelDensity;

    boolean spanDisplays = sketch.sketchDisplay() == PConstants.SPAN;

    screenRect = spanDisplays ?
      // TODO probably need to apply this to the spanning version [fry 230218]
      new Rectangle(screen.getX(), screen.getY(),
                    screen.getWidth(), screen.getHeight()) :
            new Rectangle((int) displayRect.getX(),
                          (int) displayRect.getY(),
                          (int) displayRect.getWidth(),
                          (int) displayRect.getHeight());

//      new Rectangle((int) (uiScale * displayRect.getX()),
//                    (int) (uiScale * displayRect.getY()),
//                    (int) (uiScale * displayRect.getWidth()),
//                    (int) (uiScale * displayRect.getHeight()));

    // Set the displayWidth/Height variables inside PApplet, so that they're
    // usable and can even be returned by the sketchWidth()/Height() methods.
    sketch.displayWidth = screenRect.width;
    sketch.displayHeight = screenRect.height;

    // Sometimes the window manager or OS will resize the window.
    // Keep track of the requested width/height to notify the user.
    sketchWidthRequested = sketch.sketchWidth();
    sketchHeightRequested = sketch.sketchHeight();

    sketchWidth = sketch.sketchWidth();
    sketchHeight = sketch.sketchHeight();

    boolean fullScreen = sketch.sketchFullScreen();

    // Before 3.x, we would set the window to full screen when the requested
    // width/height was the size of the screen. But not everyone *wants* that
    // to be full screen (they might want to drag the window, or who knows),
    // so that code was removed because fullScreen() is easy to use instead.
    // https://github.com/processing/processing/issues/3545

    if (fullScreen || spanDisplays) {
//      sketchWidth = (int) (uiScale * screenRect.width / windowScaleFactor);
//      sketchHeight = (int) (uiScale * screenRect.height / windowScaleFactor);
      sketchWidth = screenRect.width / windowScaleFactor;
      sketchHeight = screenRect.height / windowScaleFactor;
    }

    sketch.setSize(sketchWidth, sketchHeight);

    // https://jogamp.org/deployment/jogamp-next/javadoc/jogl/javadoc/com/jogamp/newt/opengl/GLWindow.html#setSurfaceScale(float%5B%5D)
    float surfaceScale =
      (graphics.is2X() && PApplet.platform == PConstants.MACOS) ?
      ScalableSurface.AUTOMAX_PIXELSCALE :
      ScalableSurface.IDENTITY_PIXELSCALE;
    window.setSurfaceScale(new float[] { surfaceScale, surfaceScale });

    window.setSize(sketchWidth * windowScaleFactor, sketchHeight * windowScaleFactor);
    window.setResizable(false);
    setSize(sketchWidth, sketchHeight);
    if (fullScreen) {
      PApplet.hideMenuBar();
      if (spanDisplays) {
        window.setFullscreen(screen.getMonitorDevices());
      } else {
        window.setUndecorated(true);
        window.setTopLevelPosition((int) displayRect.getX(), (int) displayRect.getY());
        window.setTopLevelSize((int) displayRect.getWidth(), (int) displayRect.getHeight());
      }
    }

    window.setSharedAutoDrawable(sharedDrawable);
  }


  protected void initListeners() {
    window.addMouseListener(new NEWTMouseListener());
    window.addKeyListener(new NEWTKeyListener());
    window.addWindowListener(new NEWTWindowListener());
    window.addGLEventListener(new DrawListener());
  }


  protected void initAnimator() {
    if (PApplet.platform == PConstants.WINDOWS) {
      // Force Windows to keep timer resolution high by creating a dummy
      // thread that sleeps for a time that is not a multiple of 10 ms.
      // See section titled "Clocks and Timers on Windows" in this post:
      // https://web.archive.org/web/20160308031939/https://blogs.oracle.com/dholmes/entry/inside_the_hotspot_vm_clocks
      Thread highResTimerThread = new Thread(() -> {
        try {
          Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignore) { }
      }, "HighResTimerThread");
      highResTimerThread.setDaemon(true);
      highResTimerThread.start();
    }

    animator = new FPSAnimator(window, 60);
    animators.add(animator);

    drawException = null;
    animator.setUncaughtExceptionHandler((animator, drawable, cause) -> {
      synchronized (drawExceptionMutex) {
        drawException = cause;
        drawExceptionMutex.notify();
      }
    });

    drawExceptionHandler = new Thread(() -> {
      synchronized (drawExceptionMutex) {
        try {
          while (drawException == null) {
            drawExceptionMutex.wait();
          }
          Throwable cause = drawException.getCause();
          if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
          } else if (cause instanceof UnsatisfiedLinkError) {
            throw new UnsatisfiedLinkError(cause.getMessage());
          } else if (cause == null) {
            throw new RuntimeException(drawException.getMessage());
          } else {
            throw new RuntimeException(cause);
          }
        } catch (InterruptedException ignored) { }
      }
    });
    drawExceptionHandler.start();
  }


  @Override
  public void setTitle(final String title) {
    display.getEDTUtil().invoke(false, () -> window.setTitle(title));
  }


  @Override
  public void setVisible(final boolean visible) {
    display.getEDTUtil().invoke(false, () -> window.setVisible(visible));
  }


  @Override
  public void setResizable(final boolean resizable) {
    display.getEDTUtil().invoke(false, () -> window.setResizable(resizable));
  }


  public void setIcon(PImage icon) {
    PGraphics.showWarning("Window icons for OpenGL sketches can only be set in settings()\n" +
                          "using PJOGL.setIcon(filename).");
  }


  @Override
  public void setAlwaysOnTop(final boolean always) {
    display.getEDTUtil().invoke(false, () -> window.setAlwaysOnTop(always));
  }


  protected void initIcons() {
    IOUtil.ClassResources res;
    if (PJOGL.icons == null || PJOGL.icons.length == 0) {
      // Default Processing icons
      final int[] sizes = { 16, 32, 48, 64, 128, 256, 512 };
      String[] iconImages = new String[sizes.length];
      for (int i = 0; i < sizes.length; i++) {
         iconImages[i] = "/icon/icon-" + sizes[i] + ".png";
       }
       res = new ClassResources(iconImages,
                                PApplet.class.getClassLoader(),
                                PApplet.class);
    } else {
      // Loading custom icons from user-provided files.
      String[] iconImages = new String[PJOGL.icons.length];
      for (int i = 0; i < PJOGL.icons.length; i++) {
        iconImages[i] = resourceFilename(PJOGL.icons[i]);
      }

      res = new ClassResources(iconImages,
                               sketch.getClass().getClassLoader(),
                               sketch.getClass());
    }
    NewtFactory.setWindowIcons(res);
  }


  private String resourceFilename(String filename) {
    // The code below comes from PApplet.createInputRaw() with a few adaptations
    InputStream stream;
    try {
      // First see if it's in a data folder. This may fail by throwing
      // a SecurityException. If so, this whole block will be skipped.
      File file = new File(sketch.dataPath(filename));
      if (!file.exists()) {
        // next see if it's just in the sketch folder
        file = sketch.sketchFile(filename);
      }

      if (file.exists() && !file.isDirectory()) {
        try {
          // handle case sensitivity check
          String filePath = file.getCanonicalPath();
          String filenameActual = new File(filePath).getName();
          // make sure there isn't a subfolder prepended to the name
          String filenameShort = new File(filename).getName();
          // if the actual filename is the same, but capitalized
          // differently, warn the user.
          //if (filenameActual.equalsIgnoreCase(filenameShort) &&
          //!filenameActual.equals(filenameShort)) {
          if (!filenameActual.equals(filenameShort)) {
            throw new RuntimeException("This file is named " +
                                       filenameActual + " not " +
                                       filename + ". Rename the file " +
                                       "or change your code.");
          }
        } catch (IOException ignored) { }
      }

      stream = new FileInputStream(file);
      stream.close();
      return file.getCanonicalPath();

      // have to break these out because a general Exception might
      // catch the RuntimeException being thrown above
    } catch (IOException | SecurityException ignored) { }

    ClassLoader cl = sketch.getClass().getClassLoader();

    try {
      // by default, data files are exported to the root path of the jar.
      // (not the data folder) so check there first.
      stream = cl.getResourceAsStream("data/" + filename);
      if (stream != null) {
        String cn = stream.getClass().getName();
        // this is an irritation of sun's java plug-in, which will return
        // a non-null stream for an object that doesn't exist. like all good
        // things, this is probably introduced in java 1.5. awesome!
        // https://download.processing.org/bugzilla/359.html
        if (!cn.equals("sun.plugin.cache.EmptyInputStream")) {
          stream.close();
          return "data/" + filename;
        }
      }

      // When used with an online script, also need to check without the
      // data folder, in case it's not in a subfolder called 'data'.
      // https://download.processing.org/bugzilla/389.html
      stream = cl.getResourceAsStream(filename);
      if (stream != null) {
        String cn = stream.getClass().getName();
        if (!cn.equals("sun.plugin.cache.EmptyInputStream")) {
          stream.close();
          return filename;
        }
      }
    } catch (IOException ignored) { }

    try {
      // attempt to load from a local file
      try {
        String path = sketch.dataPath(filename);
        stream = new FileInputStream(path);
        stream.close();
        return path;
      } catch (IOException ignored) { }

      try {
        String path = sketch.sketchPath(filename);
        stream = new FileInputStream(path);
        stream.close();
        return path;
      } catch (Exception ignored) { }

      try {
        stream = new FileInputStream(filename);
        stream.close();
        return filename;
      } catch (IOException ignored) { }

    } catch (Exception e) {
      //die(e.getMessage(), e);
      e.printStackTrace();
    }

    return "";
  }


  @Override
  public void placeWindow(int[] location, int[] editorLocation) {
    if (sketch.sketchFullScreen()) {
      return;
    }

    int x = window.getX() - window.getInsets().getLeftWidth();
    int y = window.getY() - window.getInsets().getTopHeight();
    int w = window.getWidth() + window.getInsets().getTotalWidth();
    int h = window.getHeight() + window.getInsets().getTotalHeight();

    if (location != null) {
      window.setTopLevelPosition(location[0], location[1]);

    } else if (editorLocation != null) {
      int locationX = editorLocation[0] - 20;
      int locationY = editorLocation[1];

      if (locationX - w > 10) {
        // if it fits to the left of the window
        window.setTopLevelPosition(locationX - w, locationY);

      } else {  // doesn't fit
        locationX = (sketch.displayWidth - w) / 2;
        locationY = (sketch.displayHeight - h) / 2;
        window.setTopLevelPosition(locationX, locationY);
      }
    } else {  // just center on screen
      // Can't use frame.setLocationRelativeTo(null) because it sends the
      // frame to the main display, which undermines the --display setting.
      window.setTopLevelPosition(screenRect.x + (screenRect.width - sketchWidth) / 2,
                                 screenRect.y + (screenRect.height - sketchHeight) / 2);
    }

    Point frameLoc = new Point(x, y);
    if (frameLoc.y < 0) {
      // Windows actually allows you to place frames where they can't be
      // closed. Awesome. https://download.processing.org/bugzilla/1508.html
      window.setTopLevelPosition(frameLoc.x, 30);
    }
  }


  public void placePresent(int stopColor) {
    float scale = getPixelScale();
    pgl.initPresentMode(0.5f * (screenRect.width/scale - sketchWidth),
                        0.5f * (screenRect.height/scale - sketchHeight), stopColor);
    PApplet.hideMenuBar();

    window.setUndecorated(true);
    window.setTopLevelPosition((int) displayRect.getX(), (int) displayRect.getY());
    window.setTopLevelSize((int) displayRect.getWidth(), (int) displayRect.getHeight());
  }


  /*
  public void setupExternalMessages() {
    external = true;
  }
  */


  public void startThread() {
    if (animator != null) {
      animator.start();
    }
  }


  public void pauseThread() {
    if (animator != null) {
      animator.pause();
    }
  }


  public void resumeThread() {
    if (animator != null) {
      animator.resume();
    }
  }


  public boolean stopThread() {
    if (drawExceptionHandler != null) {
      drawExceptionHandler.interrupt();
      drawExceptionHandler = null;
    }
    if (animator != null) {
      // Stops all other animators to avoid exceptions when closing a window in a multiple window configuration
      for (FPSAnimator ani: animators) {
        if (ani != animator) ani.stop();
      }
      return animator.stop();
    } else {
      return false;
    }
  }


  public boolean isStopped() {
    if (animator != null) {
      return !animator.isAnimating();
    } else {
      return true;
    }
  }


  public void setLocation(final int x, final int y) {
    display.getEDTUtil().invoke(false, () -> window.setTopLevelPosition(x, y));
  }


  public void setSize(int wide, int high) {
    if (pgl.presentMode()) return;

    // When the surface is set to resizable via surface.setResizable(true),
    // a crash may occur if the user sets the window to size zero.
    // https://github.com/processing/processing/issues/5052
    if (high <= 0) {
      high = 1;
    }
    if (wide <= 0) {
      wide = 1;
    }

    boolean changed = sketch.width != wide || sketch.height != high;

    sketchWidth = wide;
    sketchHeight = high;

    sketch.setSize(wide, high);
    graphics.setSize(wide, high);

    if (changed) {
      window.setSize(wide * windowScaleFactor, high * windowScaleFactor);
    }
  }


  public float getPixelScale() {
    if (graphics.pixelDensity == 1) {
      return 1;
    }

    if (PApplet.platform == PConstants.MACOS) {
      return getCurrentPixelScale();
    }

    return 2;
  }


  protected float getCurrentPixelScale() {
    // Even if the graphics are retina, the user might have moved the window
    // into a non-retina monitor, so we need to check
    return window.getCurrentSurfaceScale(currentPixelScale)[0];
  }


  public Component getComponent() {
    return canvas;
  }


  public void setSmooth(int level) {
    pgl.reqNumSamples = level;
    GLCapabilities caps = new GLCapabilities(profile);
    caps.setAlphaBits(PGL.REQUESTED_ALPHA_BITS);
    caps.setDepthBits(PGL.REQUESTED_DEPTH_BITS);
    caps.setStencilBits(PGL.REQUESTED_STENCIL_BITS);
    caps.setSampleBuffers(true);
    caps.setNumSamples(pgl.reqNumSamples);
    caps.setBackgroundOpaque(true);
    caps.setOnscreen(true);
    NativeSurface target = window.getNativeSurface();
    MutableGraphicsConfiguration config = (MutableGraphicsConfiguration) target.getGraphicsConfiguration();
    config.setChosenCapabilities(caps);
  }


  public void setFrameRate(float fps) {
    if (fps < 1) {
      PGraphics.showWarning(
        "The OpenGL renderer cannot have a frame rate lower than 1.\n" +
        "Your sketch will run at 1 frame per second.");
      fps = 1;
    } else if (fps > 1000) {
      PGraphics.showWarning(
        "The OpenGL renderer cannot have a frame rate higher than 1000.\n" +
        "Your sketch will run at 1000 frames per second.");
      fps = 1000;
    }
    if (animator != null) {
      animator.stop();
      animator.setFPS((int)fps);
      pgl.setFps(fps);
      animator.start();
    }
  }


  public void requestFocus() {
    display.getEDTUtil().invoke(false, () -> window.requestFocus());
  }


  public class DrawListener implements GLEventListener {
    private boolean isInit = false;

    public void display(GLAutoDrawable drawable) {
      if (!isInit) return;

      if (display.getEDTUtil().isCurrentThreadEDT()) {
        // For some unknown reason, a few frames of the animator run on
        // the EDT. For those, we just skip this draw call to avoid badness.
        // See below for explanation of this two line hack.
        pgl.beginRender();
        pgl.endRender(sketch.sketchWindowColor());
        return;
      }

      if (sketch.frameCount == 0) {
        if (sketchWidth != sketchWidthRequested || sketchHeight != sketchHeightRequested) {
          if (!sketch.sketchFullScreen()) {
            // don't show the message when using fullScreen()
            PGraphics.showWarning(
              "The sketch has been resized from " +
                "%dx%d to %dx%d by the operating system.%n" +
                "This happened outside Processing, " +
                "and may be a limitation of the OS or window manager.",
              sketchWidthRequested, sketchHeightRequested, sketchWidth, sketchHeight);
          }
        }
        requestFocus();
      }

      if (!sketch.finished) {
        synchronized (getSyncMutex(drawable)) {
          pgl.getGL(drawable);
          int prevFrameCount = sketch.frameCount;
          sketch.handleDraw();
          if (prevFrameCount == sketch.frameCount || sketch.finished) {
            // This hack allows the FBO layer to be swapped normally even if
            // the sketch is no looping or finished because it does not call draw(),
            // otherwise background artifacts may occur (depending on the hardware/drivers).
            pgl.beginRender();
            pgl.endRender(sketch.sketchWindowColor());
          }
          PGraphicsOpenGL.completeFinishedPixelTransfers();
        }
      }

      if (sketch.exitCalled()) {
        PGraphicsOpenGL.completeAllPixelTransfers();

        sketch.dispose(); // calls stopThread(), which stops the animator.
        sketch.exitActual();
      }
    }

    public void dispose(GLAutoDrawable drawable) {
      // do nothing, sketch.dispose() will be called with exitCalled()
    }

    public void init(GLAutoDrawable drawable) {
      if (display.getEDTUtil().isCurrentThreadEDT()) {
        return;
      }

      synchronized (getSyncMutex(drawable)) {
        pgl.init(drawable);
        sketch.start();

        int c = graphics.backgroundColor;
        pgl.clearColor(((c >> 16) & 0xff) / 255f,
                       ((c >>  8) & 0xff) / 255f,
                       (c & 0xff) / 255f,
                       ((c >> 24) & 0xff) / 255f);
        pgl.clear(PGL.COLOR_BUFFER_BIT);
        isInit = true;
      }
    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
      if (!isInit) return;

      if (display.getEDTUtil().isCurrentThreadEDT()) {
        return;
      }

      synchronized (getSyncMutex(drawable)) {
        pgl.resetFBOLayer();
        float scale = PApplet.platform == PConstants.MACOS ?
            getCurrentPixelScale() : getPixelScale();
        setSize((int) (w / scale), (int) (h / scale));
      }
    }
  }


  protected class NEWTWindowListener implements com.jogamp.newt.event.WindowListener {
    public NEWTWindowListener() {
      super();
    }
    @Override
    public void windowGainedFocus(com.jogamp.newt.event.WindowEvent arg0) {
      sketch.focused = true;
      sketch.focusGained();
    }

    @Override
    public void windowLostFocus(com.jogamp.newt.event.WindowEvent arg0) {
      sketch.focused = false;
      sketch.focusLost();
    }

    @Override
    public void windowDestroyNotify(com.jogamp.newt.event.WindowEvent arg0) {
      sketch.exit();
    }

    @Override
    public void windowDestroyed(com.jogamp.newt.event.WindowEvent arg0) {
      sketch.exit();
    }

    @Override
    public void windowMoved(com.jogamp.newt.event.WindowEvent arg0) {
      /*
      if (external) {
        sketch.frameMoved(window.getX(), window.getY());
      }
      */
      sketch.postWindowMoved(window.getX(), window.getY());
    }

    @Override
    public void windowRepaint(com.jogamp.newt.event.WindowUpdateEvent arg0) {
    }

    @Override
    public void windowResized(com.jogamp.newt.event.WindowEvent arg0) {
      sketch.postWindowResized(window.getWidth(), window.getHeight());
    }
  }


  // NEWT mouse listener
  protected class NEWTMouseListener extends com.jogamp.newt.event.MouseAdapter {
    public NEWTMouseListener() {
      super();
    }
    @Override
    public void mousePressed(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.PRESS);
    }
    @Override
    public void mouseReleased(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.RELEASE);
    }
    @Override
    public void mouseClicked(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.CLICK);
    }
    @Override
    public void mouseDragged(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.DRAG);
    }
    @Override
    public void mouseMoved(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.MOVE);
    }
    @Override
    public void mouseWheelMoved(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.WHEEL);
    }
    @Override
    public void mouseEntered(com.jogamp.newt.event.MouseEvent e) {
//      System.out.println("enter");
      nativeMouseEvent(e, MouseEvent.ENTER);
    }
    @Override
    public void mouseExited(com.jogamp.newt.event.MouseEvent e) {
//      System.out.println("exit");
      nativeMouseEvent(e, MouseEvent.EXIT);
    }
  }


  // NEWT key listener
  protected class NEWTKeyListener extends com.jogamp.newt.event.KeyAdapter {
    public NEWTKeyListener() {
      super();
    }
    @Override
    public void keyPressed(com.jogamp.newt.event.KeyEvent e) {
      nativeKeyEvent(e, KeyEvent.PRESS);
    }
    @Override
    public void keyReleased(com.jogamp.newt.event.KeyEvent e) {
      nativeKeyEvent(e, KeyEvent.RELEASE);
    }
    public void keyTyped(com.jogamp.newt.event.KeyEvent e)  {
      nativeKeyEvent(e, KeyEvent.TYPE);
    }
  }


  protected void nativeMouseEvent(com.jogamp.newt.event.MouseEvent nativeEvent,
                                  int peAction) {
    // SHIFT, CTRL, META, and ALT are identical to the processing.event.Event,
    // so the modifiers are left intact here.
    int modifiers = nativeEvent.getModifiers();
    // Could limit to just the specific modifiers, but why bother?
    /*
    int peModifiers = modifiers &
                      (InputEvent.SHIFT_MASK |
                       InputEvent.CTRL_MASK |
                       InputEvent.META_MASK |
                       InputEvent.ALT_MASK);
     */

    int peButton = switch (nativeEvent.getButton()) {
      case com.jogamp.newt.event.MouseEvent.BUTTON1 -> PConstants.LEFT;
      case com.jogamp.newt.event.MouseEvent.BUTTON2 -> PConstants.CENTER;
      case com.jogamp.newt.event.MouseEvent.BUTTON3 -> PConstants.RIGHT;
      default -> 0;
    };

    int peCount;
    if (peAction == MouseEvent.WHEEL) {
      // Invert wheel rotation count so it matches JAVA2D's
      // https://github.com/processing/processing/issues/3840
      peCount = -(nativeEvent.isShiftDown() ? (int)nativeEvent.getRotation()[0]:
                                              (int)nativeEvent.getRotation()[1]);
    } else {
      peCount = nativeEvent.getClickCount();
    }

    int scale;
    if (PApplet.platform == PConstants.MACOS) {
      scale = (int) getCurrentPixelScale();
    } else {
      scale = (int) getPixelScale();
    }
    int sx = nativeEvent.getX() / scale;
    int sy = nativeEvent.getY() / scale;
    int mx = sx;
    int my = sy;

    if (pgl.presentMode()) {
      mx -= (int)pgl.presentX;
      my -= (int)pgl.presentY;
      //noinspection IntegerDivisionInFloatingPointContext
      if (peAction == KeyEvent.RELEASE &&
          pgl.insideStopButton(sx, sy - screenRect.height / windowScaleFactor)) {
        sketch.exit();
      }
      if (mx < 0 || sketchWidth < mx || my < 0 || sketchHeight < my) {
        return;
      }
    }

    MouseEvent me = new MouseEvent(nativeEvent, nativeEvent.getWhen(),
                                   peAction, modifiers,
                                   mx, my,
                                   peButton,
                                   peCount);

    sketch.postEvent(me);
  }


  protected void nativeKeyEvent(com.jogamp.newt.event.KeyEvent nativeEvent,
                                int peAction) {
    // SHIFT, CTRL, META, and ALT are identical to processing.event.Event
    int modifiers = nativeEvent.getModifiers();
//    int peModifiers = nativeEvent.getModifiers() &
//                      (InputEvent.SHIFT_MASK |
//                       InputEvent.CTRL_MASK |
//                       InputEvent.META_MASK |
//                       InputEvent.ALT_MASK);

    short code = nativeEvent.getKeyCode();
    char keyChar;
    int keyCode;
    if (isPCodedKey(code, nativeEvent.isPrintableKey())) {
      keyCode = mapToPConst(code);
      keyChar = PConstants.CODED;
    } else if (isHackyKey(code)) {
      // we can return only one char for ENTER, let it be \n everywhere
      keyCode = code == com.jogamp.newt.event.KeyEvent.VK_ENTER ?
          PConstants.ENTER : code;
      keyChar = hackToChar(code, nativeEvent.getKeyChar());
    } else {
      keyCode = code;
      keyChar = nativeEvent.getKeyChar();
    }

    // From http://jogamp.org/deployment/v2.1.0/javadoc/jogl/javadoc/com/jogamp/newt/event/KeyEvent.html
    // public final short getKeySymbol()
    // Returns the virtual key symbol reflecting the current keyboard layout.
    // public final short getKeyCode()
    // Returns the virtual key code using a fixed mapping to the US keyboard layout.
    // In contrast to key symbol, key code uses a fixed US keyboard layout and therefore is keyboard layout independent.
    // E.g. virtual key code VK_Y denotes the same physical key regardless whether keyboard layout QWERTY or QWERTZ is active. The key symbol of the former is VK_Y, where the latter produces VK_Y.
    KeyEvent ke = new KeyEvent(nativeEvent, nativeEvent.getWhen(),
                               peAction, modifiers,
                               keyChar,
                               keyCode,
                               nativeEvent.isAutoRepeat());

    sketch.postEvent(ke);

    if (!isPCodedKey(code, nativeEvent.isPrintableKey()) && !isHackyKey(code)) {
      if (peAction == KeyEvent.PRESS) {
        // Create key typed event
        // TODO: combine dead keys with the following key
        KeyEvent tke = new KeyEvent(nativeEvent, nativeEvent.getWhen(),
                                    KeyEvent.TYPE, modifiers,
                                    keyChar,
                                    0,
                                    nativeEvent.isAutoRepeat());

        sketch.postEvent(tke);
      }
    }
  }


  private static boolean isPCodedKey(short code, boolean printable) {
    return code == com.jogamp.newt.event.KeyEvent.VK_UP ||
           code == com.jogamp.newt.event.KeyEvent.VK_DOWN ||
           code == com.jogamp.newt.event.KeyEvent.VK_LEFT ||
           code == com.jogamp.newt.event.KeyEvent.VK_RIGHT ||
           code == com.jogamp.newt.event.KeyEvent.VK_ALT ||
           code == com.jogamp.newt.event.KeyEvent.VK_CONTROL ||
           code == com.jogamp.newt.event.KeyEvent.VK_SHIFT ||
           code == com.jogamp.newt.event.KeyEvent.VK_WINDOWS ||
           (!printable && !isHackyKey(code));
  }


  // Why do we need this mapping?
  // Relevant discussion and links here:
  // http://forum.jogamp.org/Newt-wrong-keycode-for-key-td4033690.html#a4033697
  // (I don't think this is a complete solution).
  private static int mapToPConst(short code) {
    return switch (code) {
      case com.jogamp.newt.event.KeyEvent.VK_UP -> PConstants.UP;
      case com.jogamp.newt.event.KeyEvent.VK_DOWN -> PConstants.DOWN;
      case com.jogamp.newt.event.KeyEvent.VK_LEFT -> PConstants.LEFT;
      case com.jogamp.newt.event.KeyEvent.VK_RIGHT -> PConstants.RIGHT;
      case com.jogamp.newt.event.KeyEvent.VK_ALT -> PConstants.ALT;
      case com.jogamp.newt.event.KeyEvent.VK_CONTROL -> PConstants.CONTROL;
      case com.jogamp.newt.event.KeyEvent.VK_SHIFT -> PConstants.SHIFT;
      case com.jogamp.newt.event.KeyEvent.VK_WINDOWS -> java.awt.event.KeyEvent.VK_META;
      default -> code;
    };
  }


  private static boolean isHackyKey(short code) {
    return (code == com.jogamp.newt.event.KeyEvent.VK_BACK_SPACE ||
            code == com.jogamp.newt.event.KeyEvent.VK_TAB ||
            code == com.jogamp.newt.event.KeyEvent.VK_ENTER ||
            code == com.jogamp.newt.event.KeyEvent.VK_ESCAPE ||
            code == com.jogamp.newt.event.KeyEvent.VK_DELETE);
  }


  private static char hackToChar(short code, char def) {
    return switch (code) {
      case com.jogamp.newt.event.KeyEvent.VK_BACK_SPACE -> PConstants.BACKSPACE;
      case com.jogamp.newt.event.KeyEvent.VK_TAB -> PConstants.TAB;
      case com.jogamp.newt.event.KeyEvent.VK_ENTER -> PConstants.ENTER;
      case com.jogamp.newt.event.KeyEvent.VK_ESCAPE -> PConstants.ESC;
      case com.jogamp.newt.event.KeyEvent.VK_DELETE -> PConstants.DELETE;
      default -> def;
    };
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  // CURSORS


  class CursorInfo {
    PImage image;
    int x, y;

    CursorInfo(PImage image, int x, int y) {
      this.image = image;
      this.x = x;
      this.y = y;
    }

    void set() {
      setCursor(image, x, y);
    }
  }

  static Map<Integer, CursorInfo> cursors = new HashMap<>();
  static Map<Integer, String> cursorNames = new HashMap<>();
  static {
    cursorNames.put(PConstants.ARROW, "arrow");
    cursorNames.put(PConstants.CROSS, "cross");
    cursorNames.put(PConstants.WAIT, "wait");
    cursorNames.put(PConstants.MOVE, "move");
    cursorNames.put(PConstants.HAND, "hand");
    cursorNames.put(PConstants.TEXT, "text");
  }


  public void setCursor(int kind) {
    if (!cursorNames.containsKey(kind)) {
      PGraphics.showWarning("Unknown cursor type: " + kind);
      return;
    }
    CursorInfo cursor = cursors.get(kind);
    if (cursor == null) {
      String name = cursorNames.get(kind);
      if (name != null) {
        URL url = getClass().getResource("cursors/" + name + ".png");
        if (url != null) {
          ImageIcon icon = new ImageIcon(url);
          PImage img = new PImageAWT(icon.getImage());
          // Most cursors just use the center as the hotspot...
          int x = img.width / 2;
          int y = img.height / 2;
          // ...others are more specific
          if (kind == PConstants.ARROW) {
            x = 10;
            y = 7;
          } else if (kind == PConstants.HAND) {
            x = 12;
            y = 8;
          } else if (kind == PConstants.TEXT) {
            x = 16;
            y = 22;
          }
          cursor = new CursorInfo(img, x, y);
          cursors.put(kind, cursor);
        }
      }
    }
    if (cursor != null) {
      cursor.set();
    } else {
      PGraphics.showWarning("Cannot load cursor type: " + kind);
    }
  }


  public void setCursor(PImage image, int hotspotX, int hotspotY) {
    // TODO why is this first getting 'display' from 'window' instead of using
    //      this.display which is already set? In addition, this.display is
    //      even used to call getEDTUtil() down below. [fry 211123]
    Display display = window.getScreen().getDisplay();
    BufferedImage img = (BufferedImage) image.getNative();
    int[] imagePixels =
      ((DataBufferInt) img.getData().getDataBuffer()).getData();
    ByteBuffer pixels = ByteBuffer.allocate(imagePixels.length * 4);
    pixels.asIntBuffer().put(imagePixels);
    PixelFormat format = PixelFormat.ARGB8888;
    final Dimension size = new Dimension(img.getWidth(), img.getHeight());
    PixelRectangle rect =
      new PixelRectangle.GenericPixelRect(format, size, 0, false, pixels);
    final PointerIcon pi = display.createPointerIcon(rect, hotspotX, hotspotY);
    this.display.getEDTUtil().invoke(false, () -> {
      window.setPointerVisible(true);
      window.setPointerIcon(pi);
    });
  }


  public void showCursor() {
    display.getEDTUtil().invoke(false, () -> window.setPointerVisible(true));
  }


  public void hideCursor() {
    display.getEDTUtil().invoke(false, () -> window.setPointerVisible(false));
  }



  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  // TOOLKIT


  @Override
  public PImage loadImage(String path, Object... args) {
    // Would like to rewrite this for 4.x, but the strategies for loading
    // image data with GL seem unnecessarily complex, and not 100% necessary:
    // we haven't had to remove as much AWT as expected. [fry 211123]
    return ShimAWT.loadImage(sketch, path, args);
  }


  @Override
  public boolean openLink(String url) {
    return ShimAWT.openLink(url);
  }


  @Override
  public void selectInput(String prompt, String callbackMethod,
                          File file, Object callbackObject) {
    EventQueue.invokeLater(() -> {
      // https://github.com/processing/processing/issues/3831
      boolean hide = (sketch != null) &&
              (PApplet.platform == PConstants.WINDOWS);
      if (hide) setVisible(false);

      ShimAWT.selectImpl(prompt, callbackMethod, file,
              callbackObject, null, FileDialog.LOAD);

      if (hide) setVisible(true);
    });
  }


  @Override
  public void selectOutput(String prompt, String callbackMethod,
                           File file, Object callbackObject) {
    EventQueue.invokeLater(() -> {
      // https://github.com/processing/processing/issues/3831
      boolean hide = (sketch != null) &&
              (PApplet.platform == PConstants.WINDOWS);
      if (hide) setVisible(false);

      ShimAWT.selectImpl(prompt, callbackMethod, file,
              callbackObject, null, FileDialog.SAVE);

      if (hide) setVisible(true);
    });
  }


  @Override
  public void selectFolder(String prompt, String callbackMethod,
                           File file, Object callbackObject) {
    EventQueue.invokeLater(() -> {
      // https://github.com/processing/processing/issues/3831
      boolean hide = (sketch != null) &&
              (PApplet.platform == PConstants.WINDOWS);
      if (hide) setVisible(false);

      ShimAWT.selectFolderImpl(prompt, callbackMethod, file,
              callbackObject, null);

      if (hide) setVisible(true);
    });
  }
}
