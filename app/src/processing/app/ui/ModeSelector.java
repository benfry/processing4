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

import processing.app.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


public class ModeSelector extends JPanel {
  // corner radius for the dropdown
  static final int RADIUS = Toolkit.zoom(3);

  Image offscreen;
  int width, height;

  String title;
  Font titleFont;
  Color titleColor;
  int titleAscent;
  int titleWidth;

  final int MODE_GAP_WIDTH = processing.app.ui.Toolkit.zoom(13);
  final int ARROW_GAP_WIDTH = processing.app.ui.Toolkit.zoom(6);
  final int ARROW_WIDTH = processing.app.ui.Toolkit.zoom(6);
  final int ARROW_TOP = processing.app.ui.Toolkit.zoom(12);
  final int ARROW_BOTTOM = processing.app.ui.Toolkit.zoom(18);

  int[] triangleX = new int[3];
  int[] triangleY = new int[] { ARROW_TOP, ARROW_TOP, ARROW_BOTTOM };

  //    Image background;
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

    backgroundColor = Theme.getColor("mode.background.color");
    outlineColor = Theme.getColor("mode.outline.color");
  }

  @Override
  public void paintComponent(Graphics screen) {
    Dimension size = getSize();
    width = 0;
    if (width != size.width || height != size.height) {
      offscreen = processing.app.ui.Toolkit.offscreenGraphics(this, size.width, size.height);
      width = size.width;
      height = size.height;
    }

    Graphics g = offscreen.getGraphics();
    Graphics2D g2 = processing.app.ui.Toolkit.prepareGraphics(g);

    g.setFont(titleFont);
    if (titleAscent == 0) {
      titleAscent = (int) processing.app.ui.Toolkit.getAscent(g); //metrics.getAscent();
    }
    FontMetrics metrics = g.getFontMetrics();
    titleWidth = metrics.stringWidth(title);

    // clear the background
    g.setColor(backgroundColor);
    g.fillRect(0, 0, width, height);

    // draw the outline for this feller
    g.setColor(outlineColor);
    //Toolkit.dpiStroke(g2);
    g2.draw(Toolkit.createRoundRect(1, 1, width-1, height-1,
        RADIUS, RADIUS, RADIUS, RADIUS));

    g.setColor(titleColor);
    g.drawString(title, MODE_GAP_WIDTH, (height + titleAscent) / 2);

    int x = MODE_GAP_WIDTH + titleWidth + ARROW_GAP_WIDTH;
    triangleX[0] = x;
    triangleX[1] = x + ARROW_WIDTH;
    triangleX[2] = x + ARROW_WIDTH/2;
    g.fillPolygon(triangleX, triangleY, 3);

    screen.drawImage(offscreen, 0, 0, width, height, this);
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