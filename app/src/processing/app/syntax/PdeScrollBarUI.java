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

package processing.app.syntax;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;


/**
 * Custom scroll bar style for the editor.
 * Originally based on https://stackoverflow.com/a/53662678
 */
public class PdeScrollBarUI extends BasicScrollBarUI {
  private final Dimension none = new Dimension();


  @Override
  protected JButton createDecreaseButton(int orientation) {
    return new JButton() {

      @Override
      public Dimension getPreferredSize() {
        return none;
      }
    };
  }


  @Override
  protected JButton createIncreaseButton(int orientation) {
    return new JButton() {

      @Override
      public Dimension getPreferredSize() {
        return none;
      }
    };
  }


  @Override
  protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
    g.setColor(Color.ORANGE);
    g.fillRect(0, 0, c.getWidth(), c.getHeight());
  }


  @Override
  protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
    Graphics2D g2 = (Graphics2D) g.create();
    // this can't really be necessary, can it?
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    Color color;
    JScrollBar sb = (JScrollBar) c;
    if (!sb.isEnabled()) {
      return;
    } else if (isDragging) {
      color = Color.DARK_GRAY; // change color
    } else if (isThumbRollover()) {
      color = Color.LIGHT_GRAY; // change color
    } else {
      color = Color.GRAY; // change color
    }
    g2.setPaint(color);
    int shorter = Math.min(c.getWidth(), c.getHeight());
    int arc = shorter;
    g2.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
    g2.setPaint(Color.WHITE);
    g2.drawRoundRect(r.x, r.y, r.width, r.height, arc, arc);
    g2.dispose();
  }


  @Override
  protected void setThumbBounds(int x, int y, int width, int height) {
    super.setThumbBounds(x, y, width, height);
    scrollbar.repaint();
  }
}