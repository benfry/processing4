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
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.plaf.basic.BasicTextUI;
import javax.swing.text.JTextComponent;
import java.awt.*;


// Not currently in use / not yet finished
// https://github.com/AdoptOpenJDK/openjdk-jdk8u/blob/master/jdk/src/share/classes/javax/swing/plaf/basic/BasicTextFieldUI.java
public class PdeTextFieldUI extends BasicTextFieldUI {
  final String prefix;

  JTextComponent jtc;


  public PdeTextFieldUI(String prefix) {
    this.prefix = prefix;
  }


  public void installUI(JComponent c) {
    super.installUI(c);

    jtc = (JTextComponent) c;

//    manager.list.search.text.color = #000000
//    manager.list.search.placeholder.color = #cccccc
//    manager.list.search.background.color = #ffffff

//    jtc.setBorder(null);  // makes text too flush to the sides
    jtc.setBorder(new EmptyBorder(0, 5, 0, 5));

    jtc.setBackground(Theme.getColor(prefix + ".background.color"));
    jtc.setForeground(Theme.getColor(prefix + ".text.color"));

    // not yet in use, so leaving out for now
    //jtc.setDisabledTextColor(Theme.getColor(prefix + ".disabled.text.color"));

    jtc.setSelectionColor(Theme.getColor(prefix + ".selection.background.color"));
    jtc.setSelectedTextColor(Theme.getColor(prefix + ".selection.text.color"));

    jtc.setCaretColor(Theme.getColor(prefix + ".caret.color"));

    //JComponent editor = ((BasicTextUI) c).getComponent();
//    JComponent editor = ((BasicTextUI) c.getUI()).getComponent();
//    editor.setBackground(UIManager.getColor(prefix + ".background"));

  /*
  Color fg = editor.getForeground();
        if ((fg == null) || (fg instanceof UIResource)) {
    editor.setForeground(UIManager.getColor(prefix + ".foreground"));
  }

  Color color = editor.getCaretColor();
        if ((color == null) || (color instanceof UIResource)) {
    editor.setCaretColor(UIManager.getColor(prefix + ".caretForeground"));
  }

  Color s = editor.getSelectionColor();
        if ((s == null) || (s instanceof UIResource)) {
    editor.setSelectionColor(UIManager.getColor(prefix + ".selectionBackground"));
  }

  Color sfg = editor.getSelectedTextColor();
        if ((sfg == null) || (sfg instanceof UIResource)) {
    editor.setSelectedTextColor(UIManager.getColor(prefix + ".selectionForeground"));
  }

  Color dfg = editor.getDisabledTextColor();
        if ((dfg == null) || (dfg instanceof UIResource)) {
    editor.setDisabledTextColor(UIManager.getColor(prefix + ".inactiveForeground"));
  }

  Border b = editor.getBorder();
        if ((b == null) || (b instanceof UIResource)) {
    editor.setBorder(UIManager.getBorder(prefix + ".border"));
  }

  Insets margin = editor.getMargin();
        if (margin == null || margin instanceof UIResource) {
    editor.setMargin(UIManager.getInsets(prefix + ".margin"));
  }
  */
  }


  public void updateTheme() {
    //System.out.println("updating theme inside PdeTextFieldUI");
    if (jtc != null) {
      //System.out.println("reinstalling ui");
      installUI(jtc);
    }
  }
}