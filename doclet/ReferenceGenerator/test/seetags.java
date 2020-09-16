/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-13 The Processing Foundation
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

package processing.core;

import processing.data.*;
import processing.event.*;
import processing.event.Event;
import processing.opengl.*;

import java.applet.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileSystemView;


/**
 * Stripped-down "source" for testing reference generator
 * @usage Web &amp; Application
 */
public class PApplet extends Applet
  implements PConstants, Runnable,
             MouseListener, MouseWheelListener, MouseMotionListener, KeyListener, FocusListener
{
  /**
   * ( begin auto-generated from mousePressed.xml )
   *
   * The <b>mousePressed()</b> function is called once after every time a
   * mouse button is pressed. The <b>mouseButton</b> variable (see the
   * related reference entry) can be used to determine which button has been pressed.
   *
   * ( end auto-generated )
   * <h3>Advanced</h3>
   *
   * If you must, use
   * int button = mouseEvent.getButton();
   * to figure out which button was clicked. It will be one of:
   * MouseEvent.BUTTON1, MouseEvent.BUTTON2, MouseEvent.BUTTON3
   * Note, however, that this is completely inconsistent across
   * platforms.
   * @webref input:mouse
   * @webDescription The <b>mousePressed()</b> function is called once after every time a
   * mouse button is pressed. The <b>mouseButton</b> variable (see the
   * related reference entry) can be used to determine which button has been pressed.
   * @webExample examples1
   */
  public void mousePressed() { }


  public void mousePressed(MouseEvent event) {
    mousePressed();
  }

  /**
   * ( begin auto-generated from mouseDragged.xml )
   *
   * The <b>mouseDragged()</b> function is called once every time the mouse
   * moves and a mouse button is pressed.
   *
   * ( end auto-generated )
   * @webref input:mouse
   * @webDescription test
   * @webExample examples/example2.js
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   */
  public void mouseDragged() { }


  public void mouseDragged(MouseEvent event) {
    mouseDragged();
  }

    /**
   * ( begin auto-generated from mousePressed_var.xml )
   *
   * Variable storing if a mouse button is pressed. The value of the system
   * variable <b>mousePressed</b> is true if a mouse button is pressed and
   * false if a button is not pressed.
   *
   * ( end auto-generated )
   * @webref input:mouse
   * @webDescription test
   * @webExample examples/example2.js
   */
  public boolean mousePressed;

}
