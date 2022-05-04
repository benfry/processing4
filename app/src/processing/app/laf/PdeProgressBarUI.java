package processing.app.laf;

import processing.app.ui.Theme;

import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.Color;


// https://github.com/AdoptOpenJDK/openjdk-jdk8u/blob/master/jdk/src/share/classes/javax/swing/plaf/basic/BasicProgressBarUI.java
public class PdeProgressBarUI extends BasicProgressBarUI {
  final String prefix;

  Color enabledFgColor;
  Color enabledBgColor;
  Color selectedFgColor;
  Color selectedBgColor;


  public PdeProgressBarUI(String prefix) {
    this.prefix = prefix;
  }


  @Override
  protected Color getSelectionForeground() {
    return selectedFgColor;
  }


  @Override
  protected Color getSelectionBackground() {
    return selectedBgColor;
  }


  public void updateTheme() {
    enabledFgColor = Theme.getColor(prefix + ".incomplete.fgcolor");
    enabledBgColor = Theme.getColor(prefix + ".incomplete.bgcolor");
    progressBar.setForeground(enabledFgColor);
    progressBar.setBackground(enabledBgColor);

    selectedFgColor = Theme.getColor(prefix + ".complete.fgcolor");
    selectedBgColor = Theme.getColor(prefix + ".complete.bgcolor");
  }
}