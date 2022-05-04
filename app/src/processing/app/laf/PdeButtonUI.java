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
import processing.app.ui.Toolkit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;


// https://github.com/AdoptOpenJDK/openjdk-jdk8u/blob/master/jdk/src/share/classes/javax/swing/plaf/basic/BasicButtonUI.java
public class PdeButtonUI extends BasicButtonUI {
  final String prefix;

  Color enabledFgColor;
  Color enabledBgColor;
  Color pressedFgColor;
  Color pressedBgColor;
  Color disabledFgColor;
  Color disabledBgColor;


  public PdeButtonUI(String prefix) {
    this.prefix = prefix;
    updateTheme();
  }


  protected void installDefaults(AbstractButton b) {
    super.installDefaults(b);
    //b.setBorder(null);
    b.setBorder(new EmptyBorder(2, 14, 2, 14));
//    b.setBorder(new EmptyBorder(2, 2, 2, 2));
    b.setOpaque(false);  // so that rounded rect works properly
  }


  @Override
  public void update(Graphics g, JComponent c) {
    ButtonModel model = ((AbstractButton) c).getModel();

//    if (c.isOpaque()) {
      //g.setColor(c.getBackground());
      //g.fillRect(0, 0, c.getWidth(),c.getHeight());
    if (model.isPressed()) {
      g.setColor(pressedBgColor);
    } else if (model.isEnabled()) {
      g.setColor(enabledBgColor);
    } else {
      g.setColor(disabledBgColor);
    }
    //g.setColor(c.isEnabled() ? bgColor : disabledBgColor);
    Toolkit.prepareGraphics(g);
    g.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 8, 8);
//    }
    paint(g, c);
  }


  /*
  @Override
  public void paint(Graphics g, JComponent c) {
    //c.setBorder(null);
    //c.setBorder(new EmptyBorder(2, 14, 2, 14));
    c.setBackground(c.isEnabled() ? bgColor : disabledBgColor);
//    c.setBackground(null);

    // this didn't seem to draw anything?
//    g.setColor(c.isEnabled() ? bgColor : disabledBgColor);
//    Rectangle r = c.getBounds();
//    System.out.println("bounds = " + r);
//    ((Graphics2D) g).fill(c.getBounds());
//    g.fillRoundRect(r.x, r.y, r.width, r.height, 4, 4);

    super.paint(g, c);
  }
  */


  protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
    AbstractButton b = (AbstractButton) c;
    ButtonModel model = b.getModel();
    FontMetrics fm = SwingUtilities2.getFontMetrics(c, g);
//    FontMetrics fm = c.getFontMetrics(g.getFont());
    int mnemonicIndex = b.getDisplayedMnemonicIndex();

    if (model.isPressed()) {
      g.setColor(pressedFgColor);
    } else if (model.isEnabled()) {
      g.setColor(enabledFgColor);
    } else {
      g.setColor(disabledFgColor);
    }
    SwingUtilities2.drawStringUnderlineCharAt(c, g,text, mnemonicIndex,
      textRect.x + getTextShiftOffset(),
      textRect.y + fm.getAscent() + getTextShiftOffset());

//    } else {
//      //g.setColor(b.getBackground().brighter());
//      g.setColor(disabledFgColor);
//      SwingUtilities2.drawStringUnderlineCharAt(c, g, text, mnemonicIndex,
//        textRect.x, textRect.y + fm.getAscent());
//    }
  }


  public void updateTheme() {
    enabledFgColor = Theme.getColor(prefix + ".enabled.text.color");
    enabledBgColor = Theme.getColor(prefix + ".enabled.background.color");
    pressedFgColor = Theme.getColor(prefix + ".pressed.text.color");
    pressedBgColor = Theme.getColor(prefix + ".pressed.background.color");
    disabledFgColor = Theme.getColor(prefix + ".disabled.text.color");
    disabledBgColor = Theme.getColor(prefix + ".disabled.background.color");
  }
}