package processing.app.laf;

import processing.app.ui.Theme;

import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.Color;


// https://github.com/AdoptOpenJDK/openjdk-jdk8u/blob/master/jdk/src/share/classes/javax/swing/plaf/basic/BasicProgressBarUI.java
public class PdeProgressBarUI extends BasicProgressBarUI {
  final String prefix;

  Color incompleteFgColor;
  Color incompleteBgColor;
  Color completeFgColor;
  Color completeBgColor;


  public PdeProgressBarUI(String prefix) {
    this.prefix = prefix;
  }


  @Override
  protected void installDefaults() {
    super.installDefaults();
    updateTheme();
  }


  /**
   * The "selectionForeground" is the color of the text when it is painted
   * over a filled area of the progress bar.
   */
  @Override
  protected Color getSelectionForeground() {
    return completeFgColor;
  }


  /**
   * The "selectionBackground" is the color of the text when it is painted
   * over an unfilled area of the progress bar.
   *
   * @return the color of the selected background
   */
  @Override
  protected Color getSelectionBackground() {
    return incompleteFgColor;
  }


  public void updateTheme() {
    incompleteFgColor = Theme.getColor(prefix + ".incomplete.fgcolor");  // green
    incompleteBgColor = Theme.getColor(prefix + ".incomplete.bgcolor");  // blue
    completeFgColor = Theme.getColor(prefix + ".complete.fgcolor");  // black
    completeBgColor = Theme.getColor(prefix + ".complete.bgcolor");  // red

    progressBar.setForeground(completeBgColor);
    progressBar.setBackground(incompleteBgColor);
  }
}