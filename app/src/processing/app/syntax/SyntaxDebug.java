package processing.app.syntax;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;

import processing.app.Platform;
import processing.app.Preferences;
import processing.app.ui.Editor;
import processing.app.ui.Theme;
import processing.app.ui.Toolkit;


public class SyntaxDebug extends JFrame {
  JEditTextArea area;

  public SyntaxDebug() {
    Platform.init();
    Preferences.init();
    Theme.init();

    area = new JEditTextArea(new PdeTextAreaDefaults(),
      new PdeInputHandler(null)) {

      protected TextAreaPainter createPainter(final TextAreaDefaults defaults) {
        return new TextAreaPainter(this, defaults) {
          public void paint(Graphics g) {
//            System.out.println("painting");
            super.paint(g);
            showLatest();
          }
        };
      }
    };
//    System.out.println(area.getPainter());  // not a PdeTextAreaPainter

    String what = "Not Lucida Grande abcdefghijklmnopqrstuvwxyz0123456789";
    area.setText(what);
    area.select(12, 25);
    System.out.println("should be selecting " + what.substring(area.getSelectionStart(), area.getSelectionStop()));

    add(area);
    pack();
    setLocationRelativeTo(null);
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    Toolkit.registerWindowCloseKeys(getRootPane(), e -> System.exit(0));
    setMinimumSize(new Dimension(500, 400));
    setVisible(true);
  }


  void showLatest() {
    System.out.println("getting font is " + getFont());
    FontMetrics metrics = getFontMetrics(area.getPainter().getFont());
    System.out.println(metrics);

    String what = area.getText();
    System.out.println(what.length());
    System.out.println(floatWidth(" "));
    System.out.println(floatWidth(what));
  }


  float floatWidth(String what) {
    FontMetrics metrics = getFontMetrics(area.getPainter().getFont());
    // data, offset, offset + len, fm.getFontRenderContext());
    Rectangle2D bounds = metrics.getStringBounds(what, getGraphics());
    return (float) bounds.getWidth();
  }


  /*
  void drawError() {
    PdeTextAreaPainter painter = (PdeTextAreaPainter) area.getPainter();

    int x1 = area.offsetToX(line, leftCol);
    int x2 = area.offsetToX(line, rightCol);
    //if (x1 == x2) x2 += fontMetrics.stringWidth(" ");

    gfx.setColor(painter.errorUnderlineColor);
    //gfx.setColor(painter.warningUnderlineColor);
    painter.paintSquiggle(gfx, y1, x1, x2);
  }
  */


  static public void main(String[] args) {
    new SyntaxDebug();
  }
}