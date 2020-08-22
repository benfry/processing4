/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-15 The Processing Foundation
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

import java.awt.*;
import java.awt.event.*;

import com.jogamp.nativewindow.ScalableSurface;
import com.jogamp.opengl.GLAnimatorControl;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.FPSAnimator;

import processing.awt.FrameBasedSurface;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;


public class PSurfaceJOGL extends FrameBasedSurface {

  public static GLProfile profile;

  private FPSAnimator animator;
  private GLCanvas canvas;
  private Throwable drawException;
  private Thread drawExceptionHandler;
  private Object lazyDrawExceptionMutex;

  public PSurfaceJOGL(PGraphics graphics) {
    super(graphics);
  }

  public float getPixelScale() {
    return 1; // TODO: Check that this is no longer needed.
  }

  public void swapBuffers() {
    canvas.swapBuffers();
  }

  @Override
  protected void initalizeCanvas() {
    initGL();
    initCanvas();
    initAnimator();

    DrawListener drawlistener = new DrawListener();
    canvas.addGLEventListener(drawlistener);
  }

  @Override
  protected void renderInternal() {
    // Running in separate thread
  }

  @Override
  protected void addCanvas(Frame frame) {
    frame.add(canvas);
  }

  @Override
  protected void requestCanvasFocus() {
    canvas.requestFocus();
  }

  @Override
  protected void setCanvasBounds(int contentWidth, int contentHeight, int sketchWidth, int sketchHeight) {
    canvas.setBounds(
        (contentWidth - sketchWidth) / 2,
        (contentHeight - sketchHeight) / 2,
        sketchWidth,
        sketchHeight
    );

    canvas.setSize(sketchWidth, sketchHeight); // TODO: Need to check this and surface scale
  }

  @Override
  protected void addMouseListener(MouseListener listener) {
    canvas.addMouseListener(listener);
  }

  @Override
  protected void addMouseMotionListener(MouseMotionListener listener) {
    canvas.addMouseMotionListener(listener);
  }

  @Override
  protected void addMouseWheelListener(MouseWheelListener listener) {
    canvas.addMouseWheelListener(listener);
  }

  @Override
  protected void addKeyListener(KeyListener listener) {
    canvas.addKeyListener(listener);
  }

  @Override
  protected void addFocusListener(FocusListener listener) {
    canvas.addFocusListener(listener);
  }

  @Override
  protected void setCursor(Cursor cursor) {
    canvas.setCursor(cursor);
  }

  @Override
  protected Cursor createCursor(Image image, int x, int y, String name) {
    return canvas.getToolkit().createCustomCursor(
            image,
            new Point(x, y),
            name
    );
  }
  @Override
  public void startThread() {
    if (animator != null) {
      animator.start();
    }
  }

  @Override
  public void pauseThread() {
    if (animator != null) {
      animator.pause();
    }
  }

  @Override
  public void resumeThread() {
    if (animator != null) {
      animator.resume();
    }
  }

  @Override
  public boolean stopThread() {
    if (drawExceptionHandler != null) {
      drawExceptionHandler.interrupt();
      drawExceptionHandler = null;
    }
    if (animator != null) {
      return animator.stop();
    } else {
      return false;
    }
  }

  @Override
  public boolean isStopped() {
    if (animator != null) {
      return !animator.isAnimating();
    } else {
      return true;
    }
  }

  protected void initGL() {
//  System.out.println("*******************************");
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

    PJOGL pgl = getPgl();
    pgl.setCaps(caps);
  }

  private PJOGL getPgl() {
    return (PJOGL) ((PGraphicsOpenGL)graphics).pgl;
  }

  private void initCanvas() {
    GLCapabilities capabilities = new GLCapabilities(profile);
    canvas = new GLCanvas(capabilities);

    float[] reqSurfacePixelScale;

    if (graphics.is2X() && PApplet.platform == PConstants.MACOS) {
      // Retina
      reqSurfacePixelScale = new float[] { ScalableSurface.AUTOMAX_PIXELSCALE,
              ScalableSurface.AUTOMAX_PIXELSCALE };
    } else {
      // Non-retina
      reqSurfacePixelScale = new float[] { ScalableSurface.IDENTITY_PIXELSCALE,
              ScalableSurface.IDENTITY_PIXELSCALE };
    }

    canvas.setSurfaceScale(reqSurfacePixelScale);
  }

  protected Object getDrawExceptionMutex() {
    if (lazyDrawExceptionMutex == null) {
      lazyDrawExceptionMutex = new Object();
    }
    return lazyDrawExceptionMutex;
  }

  protected void initAnimator() {
    if (PApplet.platform == PConstants.WINDOWS) {
      // Force Windows to keep timer resolution high by
      // sleeping for time which is not a multiple of 10 ms.
      // See section "Clocks and Timers on Windows":
      //   https://blogs.oracle.com/dholmes/entry/inside_the_hotspot_vm_clocks
      Thread highResTimerThread = new Thread(() -> {
        try {
          Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignore) { }
      }, "HighResTimerThread");
      highResTimerThread.setDaemon(true);
      highResTimerThread.start();
    }

    animator = new FPSAnimator(canvas, 60);
    drawException = null;
    animator.setUncaughtExceptionHandler(new GLAnimatorControl.UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(final GLAnimatorControl animator,
                                    final GLAutoDrawable drawable,
                                    final Throwable cause) {
        Object drawExceptionMutex = getDrawExceptionMutex();
        synchronized (drawExceptionMutex) {
          drawException = cause;
          drawExceptionMutex.notify();
        }
      }
    });

    drawExceptionHandler = new Thread(new Runnable() {
      public void run() {
        Object drawExceptionMutex = getDrawExceptionMutex();
        synchronized (drawExceptionMutex) {
          try {
            while (drawException == null) {
              drawExceptionMutex.wait();
            }
            // System.err.println("Caught exception: " + drawException.getMessage());
            if (drawException != null) {
              Throwable cause = drawException.getCause();
              if (cause instanceof ThreadDeath) {
                // System.out.println("caught ThreadDeath");
                // throw (ThreadDeath)cause;
              } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
              } else if (cause instanceof UnsatisfiedLinkError) {
                throw new UnsatisfiedLinkError(cause.getMessage());
              } else if (cause == null) {
                throw new RuntimeException(drawException.getMessage());
              } else {
                throw new RuntimeException(cause);
              }
            }
          } catch (InterruptedException e) {
            return;
          }
        }
      }
    });
    drawExceptionHandler.start();
  }

  class DrawListener implements GLEventListener {

    private PJOGL pgl;

    public DrawListener() {
      pgl = getPgl();
    }

    public void display(GLAutoDrawable drawable) {
      /*if (display.getEDTUtil().isCurrentThreadEDT()) {
        // For some reason, the first two frames of the animator are run on the
        // EDT, skipping rendering Processing's frame in that case.
        return;
      }

      if (sketch.frameCount == 0) {
        if (sketchWidth < sketchWidth0 || sketchHeight < sketchHeight0) {
          PGraphics.showWarning("The sketch has been automatically resized to fit the screen resolution");
        }
//        System.out.println("display: " + window.getWidth() + " "+ window.getHeight() + " - " + sketchWidth + " " + sketchHeight);
        requestFocus();
      }*/

      if (!sketch.finished) {
        pgl.getGL(drawable);
        int pframeCount = sketch.frameCount;
        sketch.handleDraw();
        if (pframeCount == sketch.frameCount || sketch.finished) {
          // This hack allows the FBO layer to be swapped normally even if
          // the sketch is no looping or finished because it does not call draw(),
          // otherwise background artifacts may occur (depending on the hardware/drivers).
          pgl.beginRender();
          pgl.endRender(sketch.sketchWindowColor());
        }
        PGraphicsOpenGL.completeFinishedPixelTransfers();
      }

      if (sketch.exitCalled()) {
        PGraphicsOpenGL.completeAllPixelTransfers();

        sketch.dispose(); // calls stopThread(), which stops the animator.
        sketch.exitActual();
      }
    }
    public void dispose(GLAutoDrawable drawable) {
//      sketch.dispose();
    }
    public void init(GLAutoDrawable drawable) {
      pgl.getGL(drawable);
      pgl.init(drawable);
      sketch.start();

      int c = graphics.backgroundColor;
      pgl.clearColor(((c >> 16) & 0xff) / 255f,
              ((c >>  8) & 0xff) / 255f,
              ((c >>  0) & 0xff) / 255f,
              ((c >> 24) & 0xff) / 255f);
      pgl.clear(PGL.COLOR_BUFFER_BIT);
    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
      pgl.resetFBOLayer();
      pgl.getGL(drawable);
      float scale = getPixelScale(); //PApplet.platform == PConstants.MACOS ? getCurrentPixelScale() : getPixelScale();
      setSize((int) (w / scale), (int) (h / scale));
    }
  }

}
