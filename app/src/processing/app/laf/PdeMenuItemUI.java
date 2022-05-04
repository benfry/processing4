package processing.app.laf;

import processing.app.ui.Theme;

import javax.swing.plaf.basic.BasicMenuItemUI;
import java.awt.Color;


// https://github.com/AdoptOpenJDK/openjdk-jdk8u/blob/master/jdk/src/share/classes/javax/swing/plaf/basic/BasicMenuItemUI.java
public class PdeMenuItemUI extends BasicMenuItemUI {
  final String prefix;

  Color enabledFgColor;
  Color enabledBgColor;
  Color disabledFgColor;
  Color disabledBgColor;
  Color selectedFgColor;
  Color selectedBgColor;


  public PdeMenuItemUI(String prefix) {
    this.prefix = prefix;
  }


  public void updateTheme() {
    enabledFgColor = Theme.getColor(prefix + ".enabled.fgcolor");
    enabledBgColor = Theme.getColor(prefix + ".enabled.bgcolor");
    disabledFgColor = Theme.getColor(prefix + ".disabled.fgcolor");
    disabledBgColor = Theme.getColor(prefix + ".disabled.bgcolor");
    selectedFgColor = Theme.getColor(prefix + ".selected.fgcolor");
    selectedBgColor = Theme.getColor(prefix + ".selected.bgcolor");

    // when drawing, this will be overridden when disabled or selected
    menuItem.setForeground(enabledFgColor);

    // set bg color of the parent item instead of setting everything opaque
//    menuItem.setOpaque(true);
//    menuItem.setBackground(enabledBgColor);

    acceleratorForeground = enabledFgColor;
    acceleratorSelectionForeground = selectedFgColor;
    selectionBackground = selectedBgColor;
    selectionForeground = selectedFgColor;
    disabledForeground = disabledFgColor;
  }
}