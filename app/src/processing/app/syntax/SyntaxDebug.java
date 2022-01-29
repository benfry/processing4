package processing.app.syntax;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Dimension;

import processing.app.Platform;
import processing.app.Preferences;
import processing.app.ui.Theme;


public class SyntaxDebug extends JFrame {
  JEditTextArea area;

  public SyntaxDebug() {
    Platform.init();
    Preferences.init();
    Theme.init();

    area = new JEditTextArea(new PdeTextAreaDefaults(),
      new PdeInputHandler(null));

    area.setText("abcdefghijklmnopqrstuvwxyz0123456789");

    add(area);
    //setMinimumSize(new Dimension(500, 400));
    pack();
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(500, 400));
    setVisible(true);
  }


  static public void main(String[] args) {
    new SyntaxDebug();
  }
}