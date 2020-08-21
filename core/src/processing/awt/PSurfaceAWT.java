/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2014-15 The Processing Foundation

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

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
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

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.core.PSurfaceNone;
import processing.event.KeyEvent;
import processing.event.MouseEvent;


public class PSurfaceAWT extends FrameBasedSurface {

  Canvas canvas;

  public PSurfaceAWT(PGraphics graphics) {
    super(graphics);
  }

  @Override
  protected void initalizeCanvas() {
    canvas = new SmoothCanvas();

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
  }

  @Override
  protected void renderInternal() {
    if (canvas.isDisplayable() &&
            graphics.image != null) {
      if (canvas.getBufferStrategy() == null) {
        canvas.createBufferStrategy(2);
      }
      BufferStrategy strategy = canvas.getBufferStrategy();
      if (strategy != null) {
        // Render single frame
//        try {
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
  protected void addCanvas(Frame frame) {
    frame.add(canvas);
  }

  @Override
  public Object getNative() {
    return canvas;
  }

  @Override
  protected void requestCanvasFocus() {
    // Generally useful whenever setting the frame visible
    if (canvas != null) {
      //canvas.requestFocusInWindow();
      canvas.requestFocus();
    }
  }

  @Override
  protected void setCanvasBounds(int contentWidth, int contentHeight, int sketchWidth, int sketchHeight) {
    canvas.setBounds(
      (contentWidth - sketchWidth) / 2,
      (contentHeight - sketchHeight) / 2,
      sketchWidth,
      sketchHeight
    );
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
        "custom"
    );
  }

  public class SmoothCanvas extends Canvas {
    private Dimension oldSize = new Dimension(0, 0);
    private Dimension newSize = new Dimension(0, 0);


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
      newSize.width = getWidth();
      newSize.height = getHeight();
//      if (oldSize.equals(newSize)) {
////        System.out.println("validate() return " + oldSize);
//        return;
//      } else {
      if (!oldSize.equals(newSize)) {
//        System.out.println("validate() render old=" + oldSize + " -> new=" + newSize);
        oldSize = newSize;
        sketch.setSize(newSize.width / windowScaleFactor, newSize.height / windowScaleFactor);
//        try {
        render();
//        } catch (IllegalStateException ise) {
//          System.out.println(ise.getMessage());
//        }
      }
    }


    @Override
    public void update(Graphics g) {
//      System.out.println("updating");
      paint(g);
    }


    @Override
    public void paint(Graphics screen) {
//      System.out.println("painting");
//      if (useStrategy) {
      render();
      /*
      if (graphics != null) {
        System.out.println("drawing to screen " + canvas);
        screen.drawImage(graphics.image, 0, 0, sketchWidth, sketchHeight, null);
      }
      */

//      } else {
////        new Exception("painting").printStackTrace(System.out);
////        if (graphics.image != null) { // && !sketch.insideDraw) {
//        if (onscreen != null) {
////          synchronized (graphics.image) {
//          // Needs the width/height to be set so that retina images are properly scaled down
////          screen.drawImage(graphics.image, 0, 0, sketchWidth, sketchHeight, null);
//          synchronized (offscreenLock) {
//            screen.drawImage(onscreen, 0, 0, sketchWidth, sketchHeight, null);
//          }
//        }
//      }
    }
  }

}
