/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2015-19 The Processing Foundation

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation, Inc.
  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/

package processing.app.ui;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import processing.app.Mode;


abstract public class EditorButton extends JComponent
implements MouseListener, MouseMotionListener, ActionListener {
  static public final int DIM = Toolkit.zoom(30);

  /** Button's description. */
  protected String title;
  /** Description of alternate behavior when shift is down. */
  protected String titleShift;
  /** Description of alternate behavior when alt is down. */
  protected String titleAlt;

  protected boolean pressed;
  protected boolean selected;
  protected boolean rollover;
  protected boolean shift;

  protected Image enabledImage;
  protected Image disabledImage;
  protected Image selectedImage;
  protected Image rolloverImage;
  protected Image pressedImage;

  protected Image gradient;

  protected EditorToolbar toolbar;


  public EditorButton(EditorToolbar parent, String name, String title) {
    this(parent, name, title, title, title);
  }


  public EditorButton(EditorToolbar parent, String name,
                      String title, String titleShift) {
    this(parent, name, title, titleShift, title);
  }


  public EditorButton(EditorToolbar parent, String name,
                      String title, String titleShift, String titleAlt) {
    this.toolbar = parent;
    this.title = title;
    this.titleShift = titleShift;
    this.titleAlt = titleAlt;

    Mode mode = toolbar.mode;

    disabledImage = mode.loadImageX(name + "-disabled");
    enabledImage = mode.loadImageX(name + "-enabled");
    selectedImage = mode.loadImageX(name + "-selected");
    pressedImage = mode.loadImageX(name + "-pressed");
    rolloverImage = mode.loadImageX(name + "-rollover");

    if (disabledImage == null) {
      disabledImage = enabledImage;
    }
    if (selectedImage == null) {
      selectedImage = enabledImage;
    }
    if (pressedImage == null) {
      pressedImage = enabledImage;  // could be selected image
    }
    if (rolloverImage == null) {
      rolloverImage = enabledImage;  // could be pressed image
    }
    addMouseListener(this);
    addMouseMotionListener(this);
  }


  @Override
  public void paintComponent(Graphics g) {
    Image image = enabledImage;
    if (!isEnabled()) {
      image = disabledImage;
    } else if (selected) {
      image = selectedImage;
    } else if (pressed) {
      image = pressedImage;
    } else if (rollover) {
      image = rolloverImage;
    }

    Graphics2D g2 = (Graphics2D) g;
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);

    int dim = getSize().width;  // width == height
    if (gradient != null) {
      //g.drawImage(gradient, 0, 0, DIM, DIM, this);
      g.drawImage(gradient, 0, 0, dim, dim, this);
    }
    //g.drawImage(image, 0, 0, DIM, DIM, this);
    g.drawImage(image, 0, 0, dim, dim, this);
  }


  @Override
  public void mouseClicked(MouseEvent e) { }


  public boolean isShiftDown() {
    return shift;
  }


  @Override
  public void mousePressed(MouseEvent e) {
    // Using mousePressed() (or mouseReleased()) because mouseClicked()
    // won't be fired if the user nudges the mouse while clicking.
    // https://github.com/processing/processing/issues/3529
    setPressed(true);

    shift = e.isShiftDown();

    // It looks like ActionEvent expects old-style modifiers,
    // so the e.getModifiers() call is actually correct.
    // There's an open JDK bug for this, but it remains unresolved:
    // https://bugs.openjdk.java.net/browse/JDK-8186024

    // Mostly this is only used for shift, but some modes also make use of
    // alt as a way to control debug stepping and whatnot. [fry 210813]
    // https://github.com/processing/processing4/issues/67

    //noinspection deprecation
    actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                                    null, e.getModifiers()));
  }


  @Override
  public void mouseReleased(MouseEvent e) {
    setPressed(false);
  }


  public void setPressed(boolean pressed) {
    if (isEnabled()) {
      this.pressed = pressed;
      repaint();
    }
  }


  public void setSelected(boolean selected) {
    this.selected = selected;
  }


  public String getRolloverText(InputEvent e) {
    if (e.isShiftDown()) {
      return titleShift;
    } else if (e.isAltDown()) {
      return titleAlt;
    }
    return title;
  }


  @Override
  public void mouseEntered(MouseEvent e) {
    toolbar.setRollover(this, e);
    rollover = true;
    repaint();
  }


  @Override
  public void mouseExited(MouseEvent e) {
    toolbar.setRollover(null, e);
    rollover = false;
    repaint();
  }


  @Override
  public void mouseDragged(MouseEvent e) { }


  @Override
  public void mouseMoved(MouseEvent e) { }


  abstract public void actionPerformed(ActionEvent e);


  @Override
  public Dimension getPreferredSize() {
    return new Dimension(DIM, DIM);
  }


  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }


  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }
}