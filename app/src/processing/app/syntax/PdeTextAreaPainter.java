/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org
Copyright (c) 2012-16 The Processing Foundation

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License version 2
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation, Inc.
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/

package processing.app.syntax;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.util.List;

import javax.swing.text.BadLocationException;

import processing.app.Problem;
import processing.app.ui.Editor;
import processing.app.ui.Theme;


/**
 * Adds support to TextAreaPainter for background colors,
 * and the left-hand gutter area with background color and text.
 */
public class PdeTextAreaPainter extends TextAreaPainter {
  public Color errorUnderlineColor;
  public Color warningUnderlineColor;

  protected Font gutterTextFont;
  protected Color gutterTextActiveColor;
  protected Color gutterTextInactiveColor;
  protected Color gutterHighlightColor;


  public PdeTextAreaPainter(JEditTextArea textArea, TextAreaDefaults defaults) {
    super(textArea, defaults);

    // Was looking a little flickery on Windows, but not 100% sure
    // that adding this is actually doing anything. [fry 220129]
    setDoubleBuffered(true);

    // Handle mouse clicks to toggle breakpoints
    addMouseListener(new MouseAdapter() {
      long lastTime;  // OS X seems to be firing multiple mouse events

      public void mousePressed(MouseEvent event) {
        // Don't toggle breakpoints when the debugger isn't enabled
        // https://github.com/processing/processing/issues/3306
        if (getEditor().isDebuggerEnabled()) {
          long thisTime = event.getWhen();
          if (thisTime - lastTime > 100) {
            if (event.getX() < Editor.LEFT_GUTTER) {
              int offset = textArea.xyToOffset(event.getX(), event.getY());
              if (offset >= 0) {
                int lineIndex = textArea.getLineOfOffset(offset);
                getEditor().toggleBreakpoint(lineIndex);
              }
            }
            lastTime = thisTime;
          }
        }
      }
    });
  }


  /**
   * Loads theme for TextAreaPainter. This is handled here because in the olden
   * days, Modes had different visual design from one another. Now, these are
   * just pulling the defaults from the standard theme, though there may be
   * minor additions or overrides added in a Mode's own theme.txt file.
   */
  //public void setMode(Mode mode) {
  @Override
  protected void updateTheme() {
    errorUnderlineColor = Theme.getColor("editor.error.underline.color");
    warningUnderlineColor = Theme.getColor("editor.warning.underline.color");

    gutterTextFont = Theme.getFont("editor.gutter.text.font");

    Color textColor = Theme.getColor("editor.gutter.text.color");
    int textRGB = textColor.getRGB() & 0xFFFFFF;

    int activeAlpha = 255 * Theme.getInteger("editor.gutter.text.active.alpha") / 100;
    gutterTextActiveColor = new Color(activeAlpha << 24 | textRGB, true);

    int inactiveAlpha = 255 * Theme.getInteger("editor.gutter.text.inactive.alpha") / 100;
    gutterTextInactiveColor = new Color(inactiveAlpha << 24 | textRGB, true);

    gutterHighlightColor = Theme.getColor("editor.gutter.highlight.color");

    // pull in changes for syntax style, as well as foreground and background color
    if (defaults instanceof PdeTextAreaDefaults) {
      ((PdeTextAreaDefaults) defaults).updateTheme();
    }

    // needs to happen *after* PdeTextAreaDefaults.updateTheme()
    super.updateTheme();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Paint a line. Paints the gutter (with background color and text)
   * then the line (background color and text).
   *
   * @param gfx the graphics context
   * @param line 0-based line number
   * @param x horizontal position
   */
  @Override
  protected void paintLine(Graphics gfx, int line, int x, TokenMarkerState marker) {
    try {
      // TODO This line is causing NPEs randomly ever since I added the
      //      toggle for Java Mode/Debugger toolbar. [Manindra]
      super.paintLine(gfx, line, x + Editor.LEFT_GUTTER, marker);

    } catch (Exception e) {
      e.printStackTrace();
    }

    paintLeftGutter(gfx, line, x);
    paintErrorLine(gfx, line, x);
  }


  /**
   * Paints the underline for an error/warning line
   */
  protected void paintErrorLine(Graphics gfx, int line, int x) {
    List<Problem> problems = getEditor().findProblems(line);
    for (Problem problem : problems) {
      int lineOffsetStart = textArea.getLineStartOffset(line);
      int lineOffsetStop = textArea.getLineStopOffset(line);

      int wiggleStart = lineOffsetStart + problem.getStartOffset();
      int stopOffset = Editor.getProblemEditorLineStop(problem, lineOffsetStart, lineOffsetStop);
      int wiggleStop = lineOffsetStart + stopOffset;

      int y = textArea.lineToY(line) + getLineDisplacement();

      try {
        String badCode;
        String goodCode;
        try {
          SyntaxDocument doc = textArea.getDocument();
          badCode = doc.getText(wiggleStart, wiggleStop - wiggleStart);
          goodCode = doc.getText(
            lineOffsetStart,
            wiggleStart - lineOffsetStart
          );
          //log("paintErrorLine() LineText GC: " + goodCode);
          //log("paintErrorLine() LineText BC: " + badCode);
        } catch (BadLocationException bl) {
          // Error in the import statements or end of code.
          // System.out.print("BL caught. " + ta.getLineCount() + " ,"
          // + line + " ,");
          // log((ta.getLineStopOffset(line) - start - 1));
          return;
        }

        int trimmedLength = badCode.trim().length();
        int rightTrimmedLength = trimRight(badCode).length();
        int leftTrimLength = rightTrimmedLength - trimmedLength;

        // Fix offsets when bad code is just whitespace
        if (trimmedLength == 0) {
          leftTrimLength = 0;
          rightTrimmedLength = badCode.length();
        }

        int x1 = textArea.offsetToX(line, goodCode.length() + leftTrimLength);
        int x2 = textArea.offsetToX(line, goodCode.length() + rightTrimmedLength);
        if (x1 == x2) x2 += fontMetrics.stringWidth(" ");
        int y1 = y + fontMetrics.getHeight() - 2;

        if (line != problem.getLineNumber()) {
          x1 = Editor.LEFT_GUTTER; // on the following lines, wiggle extends to the left border
        }

        gfx.setColor(errorUnderlineColor);
        if (problem.isWarning()) {
          gfx.setColor(warningUnderlineColor);
        }
        paintSquiggle(gfx, y1, x1, x2);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }


  /**
   * Paint the gutter: draw the background, draw line numbers, break points.
   * @param gfx the graphics context
   * @param line 0-based line number
   * @param x horizontal position
   */
  protected void paintLeftGutter(Graphics gfx, int line, int x) {
    int y = textArea.lineToY(line) + getLineDisplacement();
    if (line == textArea.getSelectionStopLine()) {
      gfx.setColor(gutterHighlightColor);
      gfx.fillRect(0, y, Editor.LEFT_GUTTER, fontMetrics.getHeight());
    } else {
      Rectangle clip = gfx.getClipBounds();
      gfx.setClip(0, y, Editor.LEFT_GUTTER, fontMetrics.getHeight());
      gfx.drawImage(((PdeTextArea) textArea).getGutterGradient(), 0, 0, getWidth(), getHeight(), this);
      gfx.setClip(clip);  // reset
    }

    String text = null;
    if (getEditor().isDebuggerEnabled()) {
      text = getPdeTextArea().getGutterText(line);
    }

    gfx.setColor(line < textArea.getLineCount() ? gutterTextActiveColor : gutterTextInactiveColor);
//    if (line >= textArea.getLineCount()) {
//      //gfx.setColor(new Color(gutterTextColor.getRGB(), );
//    }
    int textRight = Editor.LEFT_GUTTER - Editor.GUTTER_MARGIN;
    int textBaseline = textArea.lineToY(line) + fontMetrics.getHeight();

    if (text != null) {
      if (text.equals(PdeTextArea.BREAK_MARKER)) {
        drawDiamond(gfx, textRight - 8, textBaseline - 8, 8, 8);

      } else if (text.equals(PdeTextArea.STEP_MARKER)) {
        //drawRightArrow(gfx, textRight - 7, textBaseline - 7, 7, 6);
        drawRightArrow(gfx, textRight - 7, textBaseline - 7.5f, 7, 7);
      }
    } else {
      // if no special text for a breakpoint, just show the line number
      text = String.valueOf(line + 1);
      //text = makeOSF(String.valueOf(line + 1));

      gfx.setFont(gutterTextFont);
//      ((Graphics2D) gfx).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
//                                          RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
      // Right-align the text
      char[] txt = text.toCharArray();
      int tx = textRight - gfx.getFontMetrics().charsWidth(txt, 0, txt.length);
      /*
      // Using 'fm' here because it's relative to the editor text size,
      // not the numbers in the gutter
      Utilities.drawTabbedText(new Segment(txt, 0, text.length()),
                               (float) tx, (float) textBaseline,
                               (Graphics2D) gfx, this, 0);
       */
      gfx.drawString(text, tx, textBaseline);
    }
  }


  @SuppressWarnings("SameParameterValue")
  static private void drawDiamond(Graphics g,
                                  float x, float y, float w, float h) {
    Graphics2D g2 = (Graphics2D) g;
    GeneralPath path = new GeneralPath();
    path.moveTo(x + w/2, y);
    path.lineTo(x + w, y + h/2);
    path.lineTo(x + w/2, y + h);
    path.lineTo(x, y + h/2);
    path.closePath();
    g2.fill(path);
  }


  @SuppressWarnings("SameParameterValue")
  static private void drawRightArrow(Graphics g,
                                     float x, float y, float w, float h) {
    Graphics2D g2 = (Graphics2D) g;
    GeneralPath path = new GeneralPath();
    path.moveTo(x, y);
    path.lineTo(x + w, y + h/2);
    path.lineTo(x, y + h);
    path.closePath();
    g2.fill(path);
  }


  /**
   * Remove all trailing whitespace from a line
   */
  static private String trimRight(String str) {
    int i = str.length() - 1;
    while (i >= 0 && Character.isWhitespace(str.charAt(i))) {
      i--;
    }
    return str.substring(0, i+1);
  }


  static private void paintSquiggle(Graphics g, int y, int x1, int x2) {
    int xx = x1;

    while (xx < x2) {
      g.drawLine(xx, y, xx + 2, y + 1);
      xx += 2;
      g.drawLine(xx, y + 1, xx + 2, y);
      xx += 2;
    }
  }


  @Override
  public String getToolTipText(MouseEvent event) {
    fontMetrics = getFontMetrics();
    int line = event.getY() / fontMetrics.getHeight() + textArea.getFirstLine();
    if (line >= 0 || line < textArea.getLineCount()) {
      List<Problem> problems = getEditor().findProblems(line);
      for (Problem problem : problems) {
        int lineStart = textArea.getLineStartOffset(line);
        int lineEnd = textArea.getLineStopOffset(line);

        int errorStart = lineStart + problem.getStartOffset();
        int stopOffsetLine = Editor.getProblemEditorLineStop(problem, lineStart, lineEnd);
        int errorEnd = lineStart + stopOffsetLine;

        int startOffset = Math.max(errorStart, lineStart) - lineStart;
        int stopOffset = Math.min(errorEnd, lineEnd) - lineStart;

        int x = event.getX();

        if (x >= textArea.offsetToX(line, startOffset) &&
            x <= textArea.offsetToX(line, stopOffset)) {
          getEditor().statusToolTip(this, problem.getMessage(), problem.isError());
          return super.getToolTipText(event);
        }
      }
    }
    setToolTipText(null);
    return super.getToolTipText(event);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  @Override
  public int getScrollWidth() {
    // https://github.com/processing/processing/issues/3591
    return super.getWidth() - Editor.LEFT_GUTTER;
  }


  public Editor getEditor() {
    return getPdeTextArea().editor;
  }


  public PdeTextArea getPdeTextArea() {
    return (PdeTextArea) textArea;
  }
}
