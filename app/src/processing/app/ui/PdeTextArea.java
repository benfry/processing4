package processing.app.ui;

import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import java.awt.*;

import javax.swing.JScrollPane;
import javax.swing.text.BadLocationException;


public class PdeTextArea extends RSyntaxTextArea {
  protected final Editor editor;
//  protected Image gutterGradient;


  public PdeTextArea(Editor editor) {
    this.editor = editor;

//    // load settings from theme.txt
//    Mode mode = editor.getMode();
//    gutterGradient = mode.makeGradient("editor", Editor.LEFT_GUTTER, 500);
  }


  public JScrollPane createScrollPane() {
    //return RTextScrollPane(this);
    RTextScrollPane rsp = new RTextScrollPane();
    rsp.setViewportView(this);
    return rsp;
  }


  /*
//  public Image getGutterGradient() {
//    return gutterGradient;
//  }


  public void setMode(Mode mode) {
    ((PdeTextAreaPainter) painter).setMode(mode);
  }
  */


  public void updateTheme() {
    System.err.println("PdeTextArea.updateTheme() incomplete");
//    // Update fonts and other items controllable from the prefs
//    textArea.getPainter().updateAppearance();
//    textArea.repaint();
  }


  /*
  public void copyAsHTML() {
    // lots of code inside JEditTextArea for this, but RSyntax may have its own
  }
  */

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


  public int getCaretLine() throws BadLocationException {
    return getLineOfOffset(getCaretPosition());
  }


//  public int getLineStartOffset(int index) {
//
//  }


  public int getLineStartNonWhiteSpaceOffset(int line) throws BadLocationException {
    int start = getLineStartOffset(line);
    int stop = getLineEndOffset(line);
    String str = getText(start, stop - start);

    if (str != null) {
      for (int i = 0; i < str.length(); i++) {
        if (!Character.isWhitespace(str.charAt(i))) {
          return start + i;
        }
      }
    }
    return stop;  // TODO do we need to step over the \n as well?
  }


  public int getLineStopOffset(int line) throws BadLocationException {
    return getLineEndOffset(line);
  }


  public String getLineText(int index) throws BadLocationException {
    int start = getLineStartOffset(index);
    int end = getLineEndOffset(index);
    return getText(start, end - start);
  }


  /** TODO Used by File > Print */
  public String getTextAsHtml() {
    throw new RuntimeException("PdeTextArea.getTextAsHtml() not implemented");
  }


  public void scrollToCaret() {
    Point pt = getCaret().getMagicCaretPosition();
    Rectangle rect = new Rectangle(pt, new Dimension(1, getLineHeight()));
    scrollRectToVisible(rect);
  }

  public int getDocumentLength() {
    return getDocument().getLength();
  }


  public int getSelectionStartLine() throws BadLocationException {
    return getLineOfOffset(getSelectionStart());
  }


  public int getSelectionStopLine() throws BadLocationException {
    return getLineOfOffset(getSelectionEnd());
  }
}