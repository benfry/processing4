package processing.app.laf;

import javax.swing.JTextPane;
import javax.swing.text.html.HTMLDocument;


// not in use; this all works with JLabel, but it's confusing as heck
public class PdeLabel extends JTextPane {
  String message;
  String css;


  public PdeLabel() {
    setEditable(false);
    setOpaque(false);
    setContentType("text/html");

    /*
    pane.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        if (e.getURL() != null) {
          Platform.openURL(e.getURL().toString());
        }
      }
    });
    */
  }


  @Override
  public void setText(String message) {
    this.message = message;
    rebuild();
  }


  public void setText(String message, String css) {
    this.message = message;
    this.css = css;
    rebuild();
  }


  private void rebuild() {
    StringBuffer buffer = new StringBuffer("<html><head>");
//    if (css != null) {
//      buffer.append("<style type='text/css'>").append(css).append("</style>");
//    }
    buffer.append("</head><body>");
    buffer.append(message);
//    System.out.println(buffer);
    super.setText(buffer.toString());
    if (css != null) {
      ((HTMLDocument) getDocument()).getStyleSheet().addRule(css);
    }
    /*
    if (css != null) {  // reapply the styles
      setStyle(css);
    }
    */
  }


  public void setStyle(String css) {
    //((HTMLDocument) getDocument()).getStyleSheet().addRule(css);
    this.css = css;
    rebuild();
  }


  /*
  public void setURL(String url) {
    this.url = url;
  }
  */
}