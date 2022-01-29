/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 * TextAreaPainter.java - Paints the text area
 * Copyright (C) 1999 Slava Pestov
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */
package processing.app.syntax;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;

import javax.swing.ToolTipManager;
import javax.swing.text.*;
import javax.swing.JComponent;

import processing.app.Preferences;
import processing.app.syntax.im.CompositionTextPainter;


/**
 * The text area repaint manager. It performs double buffering and paints
 * lines of text.
 * @author Slava Pestov
 */
public class TextAreaPainter extends JComponent implements TabExpander {
  /** A specific painter composed by the InputMethod.*/
  protected CompositionTextPainter compositionTextPainter;

  protected JEditTextArea textArea;
  protected TextAreaDefaults defaults;

  // moved from TextAreaDefaults
  private Font plainFont;
  private Font boldFont;
  private boolean antialias;

  protected int tabSize;
//  protected FontMetrics fm;
  protected FontMetrics fontMetrics;

  protected Highlight highlights;

  int currentLineIndex;
  Token currentLineTokens;
  Segment currentLine;


  /**
   * Creates a new repaint manager. This should be not be called directly.
   */
  public TextAreaPainter(JEditTextArea textArea, TextAreaDefaults defaults) {
    this.textArea = textArea;
    this.defaults = defaults;

    setAutoscrolls(true);
//    setDoubleBuffered(true);
    setOpaque(true);

    ToolTipManager.sharedInstance().registerComponent(this);

    currentLine = new Segment();
    currentLineIndex = -1;

    setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

    updateTheme();
  }


  protected void updateTheme() {
    setForeground(defaults.fgcolor);
    setBackground(defaults.bgcolor);

    /*
    // Ensure that our monospaced font is loaded
    // https://github.com/processing/processing/pull/4639
    Toolkit.getMonoFontName();
     */
    plainFont = Preferences.getFont("editor.font.family", "editor.font.size", Font.PLAIN);
    boldFont = Preferences.getFont("editor.font.family", "editor.font.size", Font.BOLD);
    antialias = Preferences.getBoolean("editor.smooth");

    // moved from setFont() override (never quite comfortable w/ that override)
    fontMetrics = super.getFontMetrics(plainFont);
    tabSize = fontMetrics.charWidth(' ') * Preferences.getInteger("editor.tabs.size");
    textArea.recalculateVisibleLines();
  }


  /**
   * Get CompositionTextPainter, creating one if it doesn't exist.
   */
   public CompositionTextPainter getCompositionTextPainter() {
     if (compositionTextPainter == null) {
       compositionTextPainter = new CompositionTextPainter(textArea);
     }
     return compositionTextPainter;
   }


  /**
   * Returns the syntax styles used to paint colorized text. Entry <i>n</i>
   * will be used to paint tokens with id = <i>n</i>.
   * @see processing.app.syntax.Token
   */
  public final SyntaxStyle[] getStyles() {
    return defaults.styles;
  }


  /**
   * Enables or disables current line highlighting.
   */
  public final void setLineHighlightEnabled(boolean lineHighlight) {
    defaults.lineHighlight = lineHighlight;
    invalidateSelectedLines();
  }


  /**
   * Returns true if bracket highlighting is enabled, false otherwise.
   * When bracket highlighting is enabled, the bracket matching the
   * one before the caret (if any) is highlighted.
   */
  public final boolean isBracketHighlightEnabled() {
//    return bracketHighlight;
    return defaults.bracketHighlight;
  }


  /**
   * Returns true if the caret should be drawn as a block, false otherwise.
   */
  public final boolean isBlockCaretEnabled() {
    return defaults.blockCaret;
  }


  /**
   * Highlight interface.
   */
  public interface Highlight {
    /**
     * Called after the highlight painter has been added.
     * @param textArea The text area
     * @param next The painter this one should delegate to
     */
    void init(JEditTextArea textArea, Highlight next);

    /**
     * This should paint the highlight and delegate to the
     * next highlight painter.
     * @param gfx The graphics context
     * @param line The line number
     * @param y The y co-ordinate of the line
     */
    void paintHighlight(Graphics gfx, int line, int y);

    /**
     * Returns the tool tip to display at the specified
     * location. If this highlighter doesn't know what to
     * display, it should delegate to the next highlight
     * painter.
     * @param evt The mouse event
     */
    String getToolTipText(MouseEvent evt);
  }


  /** Updates and returns the font metrics used by this component. */
  public FontMetrics getFontMetrics() {
    return fontMetrics = getFontMetrics(plainFont);
  }


  public FontMetrics getFontMetrics(SyntaxStyle style) {
    return getFontMetrics(style.isBold() ? boldFont : plainFont);
  }


  // fry [160806 for 3.2]
  public int getLineHeight() {
    return fontMetrics.getHeight() + fontMetrics.getDescent();
  }


  // how much space a line might take up [fry 220119]
  protected int getLineDisplacement() {
    return fontMetrics.getLeading() + fontMetrics.getMaxDescent();
  }


  /**
   * Repaints the text.
   * @param gfx The graphics context
   */
  public void paint(Graphics gfx) {
    // Good time to update the metrics; about to draw
    fontMetrics = getFontMetrics(plainFont);

    Graphics2D g2 = (Graphics2D) gfx;
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        antialias ?
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON :
                        RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

    // no effect, one way or the other
    //g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
    //                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);

    Rectangle clipRect = gfx.getClipBounds();

    gfx.setColor(getBackground());
    gfx.fillRect(clipRect.x, clipRect.y, clipRect.width, clipRect.height);

    // We don't use yToLine() here because that method doesn't
    // return lines past the end of the document
    int height = fontMetrics.getHeight();
    int firstLine = textArea.getFirstLine();
    int firstInvalid = firstLine + clipRect.y / height;
    // Because the clipRect height is usually an even multiple
    // of the font height, we subtract 1 from it, otherwise one
    // too many lines will always be painted.
    int lastInvalid = firstLine + (clipRect.y + clipRect.height - 1) / height;

    try {
      TokenMarkerState tokenMarker = textArea.getDocument().getTokenMarker();
      int x = textArea.getHorizontalOffset();

      for (int line = firstInvalid; line <= lastInvalid; line++) {
        paintLine(gfx, line, x, tokenMarker);
      }

      if (tokenMarker != null && tokenMarker.isNextLineRequested()) {
        int h = clipRect.y + clipRect.height;
        repaint(0, h, getWidth(), getHeight() - h);
      }
    } catch (Exception e) {
      System.err.println("Error repainting line" +
                         " range {" + firstInvalid + "," + lastInvalid + "}:");
      e.printStackTrace();
    }
  }


  /**
   * Marks a line as needing a repaint.
   * @param line The line to invalidate
   */
  final public void invalidateLine(int line) {
    repaint(0, textArea.lineToY(line) + getLineDisplacement(),
            getWidth(), fontMetrics.getHeight());
  }


  /**
   * Marks a range of lines as needing a repaint.
   * @param firstLine The first line to invalidate
   * @param lastLine The last line to invalidate
   */
  final void invalidateLineRange(int firstLine, int lastLine) {
    repaint(0,textArea.lineToY(firstLine) + getLineDisplacement(),
            getWidth(),(lastLine - firstLine + 1) * fontMetrics.getHeight());
  }


  /** Repaints the lines containing the selection. */
  final void invalidateSelectedLines() {
    invalidateLineRange(textArea.getSelectionStartLine(),
                        textArea.getSelectionStopLine());
  }


  /** Returns next tab stop after a specified point. */
  @Override
  public float nextTabStop(float x, int tabOffset) {
    int offset = textArea.getHorizontalOffset();
    int tabCount = ((int)x - offset) / tabSize;
    return (tabCount + 1) * tabSize + offset;
  }


  public Dimension getPreferredSize() {
    fontMetrics = getFontMetrics(plainFont);
    return new Dimension(fontMetrics.charWidth('w') * defaults.cols,
      fontMetrics.getHeight() * defaults.rows);
  }


  public Dimension getMinimumSize() {
    return getPreferredSize();
  }


  /**
   * Accessor used by tools that want to hook in and grab the formatting.
   */
  public int getCurrentLineIndex() {
    return currentLineIndex;
  }


  /**
   * Accessor used by tools that want to hook in and grab the formatting.
   */
  public void setCurrentLineIndex(int what) {
    currentLineIndex = what;
  }


  /**
   * Accessor used by tools that want to hook in and grab the formatting.
   */
  public Token getCurrentLineTokens() {
    return currentLineTokens;
  }


  /**
   * Accessor used by tools that want to hook in and grab the formatting.
   */
  public void setCurrentLineTokens(Token tokens) {
    currentLineTokens = tokens;
  }


  /**
   * Accessor used by tools that want to hook in and grab the formatting.
   */
  public Segment getCurrentLine() {
    return currentLine;
  }


  protected void paintLine(Graphics gfx, int line, int x,
                           TokenMarkerState tokenMarker) {
    currentLineIndex = line;
    int y = textArea.lineToY(line);

    if (tokenMarker == null) {
      //paintPlainLine(gfx, line, defaultFont, defaultColor, x, y);
      paintPlainLine(gfx, line, x, y);
    } else if (line >= 0 && line < textArea.getLineCount()) {
      //paintSyntaxLine(gfx, tokenMarker, line, defaultFont, defaultColor, x, y);
      paintSyntaxLine(gfx, line, x, y, tokenMarker);
    }
  }


  protected void paintPlainLine(Graphics gfx, int line, int x, int y) {
    paintHighlight(gfx, line, y);

    // don't try to draw lines past where they exist in the document
    // https://github.com/processing/processing/issues/5628
    if (line < textArea.getLineCount()) {
      textArea.getLineText(line, currentLine);

      int x0 = x - textArea.getHorizontalOffset();
      // prevent the blinking from drawing with last color used
      // https://github.com/processing/processing/issues/5628
      gfx.setColor(defaults.fgcolor);
      gfx.setFont(plainFont);

      y += fontMetrics.getHeight();
      for (int i = 0; i < currentLine.count; i++) {
        gfx.drawChars(currentLine.array, currentLine.offset + i, 1, x, y);
        if (currentLine.array[currentLine.offset + i] == '\t') {
          x = x0 + (int) nextTabStop(x - x0, i);
        } else {
          x += fontMetrics.charWidth(currentLine.array[currentLine.offset + i]);  // TODO why this char?
        }
        //textArea.offsetToX(line, currentLine.offset + i);
      }

      // Draw characters via input method.
      if (compositionTextPainter != null &&
        compositionTextPainter.hasComposedTextLayout()) {
        compositionTextPainter.draw(gfx, defaults.lineHighlightColor);
      }
    }
    if (defaults.eolMarkers) {
      gfx.setColor(defaults.eolMarkerColor);
      gfx.drawString(".", x, y);
    }
  }


  protected void paintSyntaxLine(Graphics gfx, int line, int x, int y,
                                 TokenMarkerState tokenMarker) {
    textArea.getLineText(currentLineIndex, currentLine);
    currentLineTokens = tokenMarker.markTokens(currentLine, currentLineIndex);

//    gfx.setFont(plainFont);
    paintHighlight(gfx, line, y);

//    gfx.setFont(defaultFont);
//    gfx.setColor(defaultColor);
    y += fontMetrics.getHeight();
//    x = paintSyntaxLine(currentLine,
//                        currentLineTokens,
//                        defaults.styles, this, gfx, x, y);
    x = paintSyntaxLine(gfx, currentLine, x, y,
                        currentLineTokens,
                        defaults.styles);
    // Draw characters via input method.
    if (compositionTextPainter != null &&
        compositionTextPainter.hasComposedTextLayout()) {
      compositionTextPainter.draw(gfx, defaults.lineHighlightColor);
    }
    if (defaults.eolMarkers) {
      gfx.setColor(defaults.eolMarkerColor);
      gfx.drawString(".", x, y);
    }
  }


  /**
   * Paints the specified line onto the graphics context. Note that this
   * method modifies the offset and count values of the segment.
   * @param line The line segment
   * @param tokens The token list for the line
   * @param styles The syntax style list
   * @param gfx The graphics context
   * @param x The x co-ordinate
   * @param y The y co-ordinate
   * @return The x co-ordinate, plus the width of the painted string
   */
//  public int paintSyntaxLine(Segment line, Token tokens, SyntaxStyle[] styles,
//                             TabExpander expander, Graphics gfx,
//                             int x, int y) {
  protected int paintSyntaxLine(Graphics gfx, Segment line, int x, int y,
                                Token tokens, SyntaxStyle[] styles) {
//    Font defaultFont = gfx.getFont();
//    Color defaultColor = gfx.getColor();

    int x0 = x - textArea.getHorizontalOffset();

//    for (byte id = tokens.id; id != Token.END; tokens = tokens.next) {
    for (;;) {
      byte id = tokens.id;
      if (id == Token.END)
        break;

      int length = tokens.length;
      if (id == Token.NULL) {
//        if(!defaultColor.equals(gfx.getColor()))
//          gfx.setColor(defaultColor);
//        if(!defaultFont.equals(gfx.getFont()))
//          gfx.setFont(defaultFont);
        gfx.setColor(defaults.fgcolor);
        gfx.setFont(plainFont);
      } else {
        //styles[id].setGraphicsFlags(gfx,defaultFont);
        SyntaxStyle ss = styles[id];
        gfx.setColor(ss.getColor());
        gfx.setFont(ss.isBold() ? boldFont : plainFont);
      }
      line.count = length;  // huh? suspicious
      for (int i = 0; i < line.count; i++) {
        gfx.drawChars(line.array, line.offset + i, 1, x, y);
        if (line.array[line.offset + i] == '\t') {
          x = x0 + (int) nextTabStop(x - x0, i);
        } else {
          x += fontMetrics.charWidth(line.array[line.offset + i]);
        }
      }
      line.offset += length;
      tokens = tokens.next;
    }

    return x;
  }


  protected void paintHighlight(Graphics gfx, int line, int y) {
    if (line >= textArea.getSelectionStartLine() &&
        line <= textArea.getSelectionStopLine()) {
      paintLineHighlight(gfx, line, y);
    }

    if (highlights != null) {
      highlights.paintHighlight(gfx, line, y);
    }

    if (defaults.bracketHighlight && line == textArea.getBracketLine()) {
      paintBracketHighlight(gfx, line, y);
    }

    if (line == textArea.getCaretLine()) {
      paintCaret(gfx, line, y);
    }
  }


  protected void paintLineHighlight(Graphics gfx, int line, int y) {
    int height = fontMetrics.getHeight();
    y += getLineDisplacement();

    int selectionStart = textArea.getSelectionStart();
    int selectionEnd = textArea.getSelectionStop();

    if (selectionStart == selectionEnd) {
      if (defaults.lineHighlight) {
        gfx.setColor(defaults.lineHighlightColor);
        gfx.fillRect(0, y, getWidth(), height);
      }
    } else {
      gfx.setColor(defaults.selectionColor);

      int selectionStartLine = textArea.getSelectionStartLine();
      int selectionEndLine = textArea.getSelectionStopLine();
      int lineStart = textArea.getLineStartOffset(line);

      int x1, x2;
      if (selectionStartLine == selectionEndLine) {
        x1 = textArea._offsetToX(line, selectionStart - lineStart);
        x2 = textArea._offsetToX(line, selectionEnd - lineStart);

      } else if (line == selectionStartLine) {
        x1 = textArea._offsetToX(line, selectionStart - lineStart);
        x2 = getWidth();

      } else if (line == selectionEndLine) {
        //x1 = 0;
        // hack from Stendahl to avoid doing weird side selection thing
        x1 = textArea._offsetToX(line, 0);
        // attempt at getting the gutter too, but doesn't seem to work
        //x1 = textArea._offsetToX(line, -textArea.getHorizontalOffset());
        x2 = textArea._offsetToX(line, selectionEnd - lineStart);
      } else {
        //x1 = 0;
        // hack from Stendahl to avoid doing weird side selection thing
        x1 = textArea._offsetToX(line, 0);
        // attempt at getting the gutter too, but doesn't seem to work
        //x1 = textArea._offsetToX(line, -textArea.getHorizontalOffset());
        x2 = getWidth();
      }

      gfx.fillRect(Math.min(x1, x2), y, x1 > x2 ? (x1 - x2) : (x2 - x1), height);
    }
  }


  protected void paintBracketHighlight(Graphics gfx, int line, int y) {
    int position = textArea.getBracketPosition();
    if (position != -1) {
      y += getLineDisplacement();
      int x = textArea._offsetToX(line, position);
      gfx.setColor(defaults.bracketHighlightColor);
      // Hack!!! Since there is no fast way to get the character
      // from the bracket matching routine, we use ( since all
      // brackets probably have the same width anyway
      gfx.drawRect(x, y,fontMetrics.charWidth('(') - 1, fontMetrics.getHeight() - 1);
    }
  }


  protected void paintCaret(Graphics gfx, int line, int y) {
    if (textArea.isCaretVisible()) {
      int offset =
        textArea.getCaretPosition() - textArea.getLineStartOffset(line);
      int caretX = textArea._offsetToX(line, offset);
      int caretWidth = 1;
      if (defaults.blockCaret || textArea.isOverwriteEnabled()) {
        caretWidth = fontMetrics.charWidth('w');
      }
      y += getLineDisplacement();
      int height = fontMetrics.getHeight();

      gfx.setColor(defaults.caretColor);

      if (textArea.isOverwriteEnabled()) {
        gfx.fillRect(caretX, y + height - 1, caretWidth,1);

      } else {
        // Some machines don't like the drawRect when the caret is a
        // single pixel wide. This caused a lot of hell because on that
        // minority of machines, the caret wouldn't show up past
        // the first column. The fix is to use drawLine() instead.
        if (caretWidth == 1) {
          //gfx.drawLine(caretX, y, caretX, y + height - 1);
          // workaround for single pixel dots showing up when caret
          // is rendered a single pixel too tall [fry 220129]
          ((Graphics2D) gfx).draw(new Line2D.Float(caretX, y + 0.5f, caretX, y + height - 0.5f));
        } else {
          gfx.drawRect(caretX, y, caretWidth - 1, height - 1);
        }
        //gfx.drawRect(caretX, y, caretWidth, height - 1);
      }
    }
  }


  public int getScrollWidth() {
    // https://github.com/processing/processing/issues/3591
    return super.getWidth();
  }
}
