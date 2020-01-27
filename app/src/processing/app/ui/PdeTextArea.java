package processing.app.ui;

import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import java.awt.Image;

import javax.swing.JScrollPane;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import processing.app.Mode;


public class PdeTextArea extends RSyntaxTextArea {
  protected final Editor editor;
  protected Image gutterGradient;


  public PdeTextArea(Editor editor) {
    this.editor = editor;

    // load settings from theme.txt
    Mode mode = editor.getMode();
    gutterGradient = mode.makeGradient("editor", Editor.LEFT_GUTTER, 500);
  }


  public JScrollPane createScrollPane() {
    //return RTextScrollPane(this);
    RTextScrollPane rsp = new RTextScrollPane();
    rsp.setViewportView(this);
    return rsp;
  }


  public Image getGutterGradient() {
    return gutterGradient;
  }


  public void setMode(Mode mode) {
    /*
    ((PdeTextAreaPainter) painter).setMode(mode);
    */
  }


  public void updateAppearance() {
    /*
    // Update fonts and other items controllable from the prefs
    textArea.getPainter().updateAppearance();
    textArea.repaint();
    */
  }


  public void copyAsHTML() {
    // lots of code inside JEditTextArea for this, but RSyntax may have its own
  }


  public boolean isOverwriteEnabled() {
    return getTextMode() == RTextArea.OVERWRITE_MODE;
  }


  public boolean isSelectionActive() {
    return getSelectionStart() != getSelectionEnd();
  }


  // Keeping this separate, since all calls to this function may be expecting
  // different behavior, and they should be replaced with replaceSelection()
  // one by one, once the behavior has been determined.
  public void setSelectedText(String s) {
    replaceSelection(s);
  }


  public int getSelectionStop() {
    return getSelectionEnd();
  }


  // TODO remove this?
  public int getDocumentLength() {
    return getDocument().getLength();
  }
}