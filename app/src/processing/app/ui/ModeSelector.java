/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-22 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, version 2.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app.ui;

import java.awt.Font;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;

import processing.app.Messages;


public class ModeSelector extends JPanel {
  // corner radius for the dropdown
  static final int RADIUS = Toolkit.zoom(3);

  String title;
  Font titleFont;
  Color titleColor;
  int titleAscent;
  int titleWidth;

  final int MODE_GAP_WIDTH = Toolkit.zoom(10);
  final int ARROW_GAP_WIDTH = Toolkit.zoom(6);
  final int ARROW_WIDTH = Toolkit.zoom(6);
  final int ARROW_TOP = Toolkit.zoom(16);
  final int ARROW_BOTTOM = Toolkit.zoom(22);

  int[] triangleX = new int[3];
  int[] triangleY = new int[] { ARROW_TOP, ARROW_TOP, ARROW_BOTTOM };

  Color backgroundColor;
  Color outlineColor;

  public ModeSelector(Editor editor) {
    title = editor.getMode().getTitle(); //.toUpperCase();

    addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent event) {
        JPopupMenu popup = editor.getModePopup();
        popup.show(ModeSelector.this, event.getX(), event.getY());
      }
    });

    updateTheme();
  }

  public void updateTheme() {
    titleFont = Theme.getFont("mode.title.font");
    titleColor = Theme.getColor("mode.title.color");

    // getGraphics() is null (even for editor) and no offscreen yet
    //titleWidth = getToolkit().getFontMetrics(titleFont).stringWidth(title);
    //titleWidth = editor.getGraphics().getFontMetrics(titleFont).stringWidth(title);

    // Theme for mode popup is handled inside Editor.handleTheme()
    // because Editor owns the parent object.

    backgroundColor = Theme.getColor("mode.background.color");
    outlineColor = Theme.getColor("mode.outline.color");
  }

  @Override
  public void paintComponent(Graphics g) {
    Graphics2D g2 = Toolkit.prepareGraphics(g);

    g.setFont(titleFont);
    if (titleAscent == 0) {
      titleAscent = (int) processing.app.ui.Toolkit.getAscent(g); //metrics.getAscent();
    }
    FontMetrics metrics = g.getFontMetrics();
    titleWidth = metrics.stringWidth(title);

    final int width = getWidth();
    final int height = getHeight();
    final int inset = Toolkit.zoom(4);
    final int outline = Toolkit.zoom(1);

    // clear the background
    g.setColor(backgroundColor);
    g.fillRect(0, 0, width, height);

    // draw the outline for this feller
    g.setColor(outlineColor);
    //Toolkit.dpiStroke(g2);
    g2.draw(Toolkit.createRoundRect(outline, outline + inset, width - outline, height - outline - inset,
        RADIUS, RADIUS, RADIUS, RADIUS));

    g.setColor(titleColor);
    g.drawString(title, MODE_GAP_WIDTH, (height + titleAscent) / 2 + 1);

    int x = MODE_GAP_WIDTH + titleWidth + ARROW_GAP_WIDTH;
    triangleX[0] = x;
    triangleX[1] = x + ARROW_WIDTH;
    triangleX[2] = x + ARROW_WIDTH/2;
    g.fillPolygon(triangleX, triangleY, 3);
  }

  @Override
  public Dimension getPreferredSize() {
    int tempWidth = titleWidth;  // with any luck, this is set
    if (tempWidth == 0) {
      Graphics g = getGraphics();
      // the Graphics object may not be ready yet, being careful
      if (g != null) {
        tempWidth = getFontMetrics(titleFont).stringWidth(title);
      } else {
        Messages.err("null Graphics in EditorToolbar.getPreferredSize()");
      }
    }
    return new Dimension(MODE_GAP_WIDTH + tempWidth +
        ARROW_GAP_WIDTH + ARROW_WIDTH + MODE_GAP_WIDTH,
        EditorButton.DIM);
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