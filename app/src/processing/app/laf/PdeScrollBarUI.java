/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  PdeScrollBarUI - Custom scroll bar for the editor
  Part of the Processing project - https://processing.org

  Copyright (c) 2022 The Processing Foundation

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation, Inc.
  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/

package processing.app.laf;

import processing.app.ui.Theme;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;


/**
 * Custom scroll bar style for the editor.
 * Originally based on <a href="https://stackoverflow.com/a/53662678">this post</a>
 * and extends <a href="https://github.com/AdoptOpenJDK/openjdk-jdk8u/blob/master/jdk/src/share/classes/javax/swing/plaf/basic/BasicScrollBarUI.java">BasicScrollBarUI</a>.
 */
public class PdeScrollBarUI extends BasicScrollBarUI {
  private final Dimension NONE = new Dimension();

  final private String backgroundAttr;
  final private String pressedAttr;
  final private String rolloverAttr;
  final private String enabledAttr;

  private Color backgroundColor;
  private Color pressedColor;
  private Color rolloverColor;
  private Color enabledColor;


  public PdeScrollBarUI(String prefix) {
    /*
    this(prefix + ".scrollbar.color",
         prefix + ".scrollbar.thumb.pressed.color",
         prefix + ".scrollbar.thumb.rollover.color",
         prefix + ".scrollbar.thumb.enabled.color");

  }

  public PdeScrollBarUI(String backgroundAttr, String pressedAttr,
                        String rolloverAttr, String enabledAttr) {
    this.backgroundAttr = backgroundAttr;
    this.pressedAttr = pressedAttr;
    this.rolloverAttr = rolloverAttr;
    this.enabledAttr = enabledAttr;
    */

    this.backgroundAttr = prefix + ".color";
    this.pressedAttr = prefix + ".thumb.pressed.color";
    this.rolloverAttr = prefix + ".thumb.rollover.color";
    this.enabledAttr = prefix + ".thumb.enabled.color";
  }


  public void updateTheme() {
    backgroundColor = Theme.getColor(backgroundAttr);
    pressedColor = Theme.getColor(pressedAttr);
    rolloverColor = Theme.getColor(rolloverAttr);
    enabledColor = Theme.getColor(enabledAttr);
  }


  @Override
  protected JButton createDecreaseButton(int orientation) {
    return new JButton() {

      @Override
      public Dimension getPreferredSize() {
        return NONE;
      }
    };
  }


  @Override
  protected JButton createIncreaseButton(int orientation) {
    return new JButton() {

      @Override
      public Dimension getPreferredSize() {
        return NONE;
      }
    };
  }


  /*
  @Override
  public void paint(Graphics g, JComponent c) {
    // this takes care of the track as well as the corner notch
    // where the horizontal and vertical scrollbars meet (edit: nope)
    g.setColor(Theme.getColor("editor.scrollbar.color"));
    g.fillRect(0, 0, c.getWidth(), c.getHeight());
    super.paint(g, c);
  }
  */


  @Override
  protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
    g.setColor(backgroundColor);
    g.fillRect(0, 0, c.getWidth(), c.getHeight());
  }


  @Override
  protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
    Graphics2D g2 = (Graphics2D) g.create();
    // this can't really be necessary, can it?
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    Color color;
    JScrollBar sb = (JScrollBar) c;
    if (sb.isEnabled()) {
      if (isDragging) {
        color = pressedColor;
      } else if (isThumbRollover()) {
        color = rolloverColor;
      } else {
        color = enabledColor;
      }
      g2.setPaint(color);
      int inset = 3;
      int arc = Math.min(c.getWidth(), c.getHeight()) - inset*2;
      g2.fillRoundRect(r.x + inset, r.y + inset, r.width - inset*2, r.height - inset*2, arc, arc);
      g2.dispose();
    }
  }


  @Override
  protected void setThumbBounds(int x, int y, int width, int height) {
    super.setThumbBounds(x, y, width, height);
    scrollbar.repaint();
  }
}