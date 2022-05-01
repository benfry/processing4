/*
 * Copyright (c) 2002, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package processing.app.laf;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

import static java.awt.RenderingHints.KEY_TEXT_ANTIALIASING;
import static java.awt.RenderingHints.KEY_TEXT_LCD_CONTRAST;


/**
 * Hacked up from the source of sun.swing.SwingUtilities2.
 */
class SwingUtilities2 {
  private static final int CHAR_BUFFER_SIZE = 100;
  private static final Object charsBufferLock = new Object();
  private static char[] charsBuffer = new char[CHAR_BUFFER_SIZE];

  public static final FontRenderContext DEFAULT_FRC =
    new FontRenderContext(null, false, false);

  /**
   * Fill the character buffer cache.  Return the buffer length.
   */
  private static int syncCharsBuffer(String s) {
    int length = s.length();
    if ((charsBuffer == null) || (charsBuffer.length < length)) {
      charsBuffer = s.toCharArray();
    } else {
      s.getChars(0, length, charsBuffer, 0);
    }
    return length;
  }

  /**
   * checks whether TextLayout is required to handle characters.
   *
   * @param text characters to be tested
   * @param start start
   * @param limit limit
   * @return {@code true}  if TextLayout is required
   *         {@code false} if TextLayout is not required
   */
  public static final boolean isComplexLayout(char[] text, int start, int limit) {
    //return FontUtilities.isComplexText(text, start, limit);
    return false;
  }

  /**
   * Returns the FontMetrics for the current Font of the passed
   * in Graphics.  This method is used when a Graphics
   * is available, typically when painting.  If a Graphics is not
   * available the JComponent method of the same name should be used.
   * <p>
   * Callers should pass in a non-null JComponent, the exception
   * to this is if a JComponent is not readily available at the time of
   * painting.
   * <p>
   * This does not necessarily return the FontMetrics from the
   * Graphics.
   *
   * @param c JComponent requesting FontMetrics, may be null
   * @param g Graphics Graphics
   */
  public static FontMetrics getFontMetrics(JComponent c, Graphics g) {
    return getFontMetrics(c, g, g.getFont());
  }


  /**
   * Returns the FontMetrics for the specified Font.
   * This method is used when a Graphics is available, typically when
   * painting.  If a Graphics is not available the JComponent method of
   * the same name should be used.
   * <p>
   * Callers should pass in a non-null JComonent, the exception
   * to this is if a JComponent is not readily available at the time of
   * painting.
   * <p>
   * This does not necessarily return the FontMetrics from the
   * Graphics.
   *
   * @param c JComponent requesting FontMetrics, may be null
   * @param c Graphics Graphics
   * @param font Font to get FontMetrics for
   */
  @SuppressWarnings("deprecation")
  public static FontMetrics getFontMetrics(JComponent c, Graphics g,
                                           Font font) {
    if (c != null) {
      // Note: We assume that we're using the FontMetrics
      // from the widget to layout out text, otherwise we can get
      // mismatches when printing.
      return c.getFontMetrics(font);
    }
    return Toolkit.getDefaultToolkit().getFontMetrics(font);
  }


  /**
   * Returns the width of the passed in String.
   * If the passed String is {@code null}, returns zero.
   *
   * @param c JComponent that will display the string, may be null
   * @param fm FontMetrics used to measure the String width
   * @param string String to get the width of
   */
  public static int stringWidth(JComponent c, FontMetrics fm, String string) {
    return (int) stringWidth(c, fm, string, false);
  }

  /**
   * Returns the width of the passed in String.
   * If the passed String is {@code null}, returns zero.
   *
   * @param c JComponent that will display the string, may be null
   * @param fm FontMetrics used to measure the String width
   * @param string String to get the width of
   * @param useFPAPI use floating point API
   */
  public static float stringWidth(JComponent c, FontMetrics fm, String string,
                                  boolean useFPAPI){
    if (string == null || string.isEmpty()) {
      return 0;
    }
    boolean needsTextLayout = ((c != null) &&
      (c.getClientProperty(TextAttribute.NUMERIC_SHAPING) != null));
    if (needsTextLayout) {
      synchronized(charsBufferLock) {
        int length = syncCharsBuffer(string);
        needsTextLayout = isComplexLayout(charsBuffer, 0, length);
      }
    }
    if (needsTextLayout) {
      TextLayout layout = createTextLayout(c, string,
        fm.getFont(), fm.getFontRenderContext());
      return layout.getAdvance();
    } else {
      return getFontStringWidth(string, fm, useFPAPI);
    }
  }

  public static float getFontStringWidth(String data, FontMetrics fm,
                                         boolean useFPAPI)
  {
    if (useFPAPI) {
      Rectangle2D bounds = fm.getFont()
        .getStringBounds(data, fm.getFontRenderContext());
      return (float) bounds.getWidth();
    } else {
      return fm.stringWidth(data);
    }
  }

  private static TextLayout createTextLayout(JComponent c, String s,
                                             Font f, FontRenderContext frc) {
    Object shaper = (c == null ?
      null : c.getClientProperty(TextAttribute.NUMERIC_SHAPING));
    if (shaper == null) {
      return new TextLayout(s, f, frc);
    } else {
      Map<TextAttribute, Object> a = new HashMap<TextAttribute, Object>();
      a.put(TextAttribute.FONT, f);
      a.put(TextAttribute.NUMERIC_SHAPING, shaper);
      return new TextLayout(s, a, frc);
    }
  }


  /**
   * Draws the string at the specified location.
   *
   * @param c JComponent that will display the string, may be null
   * @param g Graphics to draw the text to
   * @param text String to display
   * @param x X coordinate to draw the text at
   * @param y Y coordinate to draw the text at
   * @param useFPAPI use floating point API
   */
  public static void drawString(JComponent c, Graphics g, String text,
                                float x, float y, boolean useFPAPI) {
    // c may be null

    // All non-editable widgets that draw strings call into this
    // methods.  By non-editable that means widgets like JLabel, JButton
    // but NOT JTextComponents.
    if ( text == null || text.length() <= 0 ) { //no need to paint empty strings
      return;
    }
    /*
    if (isPrinting(g)) {
      Graphics2D g2d = getGraphics2D(g);
      if (g2d != null) {
        String trimmedText = trimTrailingSpaces(text);
        if (!trimmedText.isEmpty()) {
          float screenWidth = (float) g2d.getFont().getStringBounds
            (trimmedText, getFontRenderContext(c)).getWidth();
          TextLayout layout = createTextLayout(c, text, g2d.getFont(),
            g2d.getFontRenderContext());

          // If text fits the screenWidth, then do not need to justify
          if (sun.swing.SwingUtilities2.stringWidth(c, g2d.getFontMetrics(),
            trimmedText) > screenWidth) {
            layout = layout.getJustifiedLayout(screenWidth);
          }
          // Use alternate print color if specified
          Color col = g2d.getColor();
          if (col instanceof PrintColorUIResource) {
            g2d.setColor(((PrintColorUIResource)col).getPrintColor());
          }

          layout.draw(g2d, x, y);

          g2d.setColor(col);
        }

        return;
      }
    }
    */

    // If we get here we're not printing
    if (g instanceof Graphics2D) {
      Graphics2D g2 = (Graphics2D)g;

      boolean needsTextLayout = ((c != null) &&
        (c.getClientProperty(TextAttribute.NUMERIC_SHAPING) != null));

      if (needsTextLayout) {
        synchronized(charsBufferLock) {
          int length = syncCharsBuffer(text);
          needsTextLayout = isComplexLayout(charsBuffer, 0, length);
        }
      }

      Object aaHint = (c == null)
        ? null
        : c.getClientProperty(KEY_TEXT_ANTIALIASING);
      if (aaHint != null) {
        Object oldContrast = null;
        Object oldAAValue = g2.getRenderingHint(KEY_TEXT_ANTIALIASING);
        if (aaHint != oldAAValue) {
          g2.setRenderingHint(KEY_TEXT_ANTIALIASING, aaHint);
        } else {
          oldAAValue = null;
        }

        Object lcdContrastHint = c.getClientProperty(
          KEY_TEXT_LCD_CONTRAST);
        if (lcdContrastHint != null) {
          oldContrast = g2.getRenderingHint(KEY_TEXT_LCD_CONTRAST);
          if (lcdContrastHint.equals(oldContrast)) {
            oldContrast = null;
          } else {
            g2.setRenderingHint(KEY_TEXT_LCD_CONTRAST,
              lcdContrastHint);
          }
        }

        if (needsTextLayout) {
          TextLayout layout = createTextLayout(c, text, g2.getFont(),
            g2.getFontRenderContext());
          layout.draw(g2, x, y);
        } else {
          g2.drawString(text, x, y);
        }

        if (oldAAValue != null) {
          g2.setRenderingHint(KEY_TEXT_ANTIALIASING, oldAAValue);
        }
        if (oldContrast != null) {
          g2.setRenderingHint(KEY_TEXT_LCD_CONTRAST, oldContrast);
        }

        return;
      }

      if (needsTextLayout){
        TextLayout layout = createTextLayout(c, text, g2.getFont(),
          g2.getFontRenderContext());
        layout.draw(g2, x, y);
        return;
      }
    }

    g.drawString(text, (int) x, (int) y);
  }


  /**
   * Draws the string at the specified location underlining the specified
   * character.
   *
   * @param c JComponent that will display the string, may be null
   * @param g Graphics to draw the text to
   * @param text String to display
   * @param underlinedIndex Index of a character in the string to underline
   * @param x X coordinate to draw the text at
   * @param y Y coordinate to draw the text at
   */

  public static void drawStringUnderlineCharAt(JComponent c,Graphics g,
                                               String text, int underlinedIndex, int x, int y) {
    drawStringUnderlineCharAt(c, g, text, underlinedIndex, x, y, false);
  }


  /**
   * Draws the string at the specified location underlining the specified
   * character.
   *
   * @param c JComponent that will display the string, may be null
   * @param g Graphics to draw the text to
   * @param text String to display
   * @param underlinedIndex Index of a character in the string to underline
   * @param x X coordinate to draw the text at
   * @param y Y coordinate to draw the text at
   * @param useFPAPI use floating point API
   */
  public static void drawStringUnderlineCharAt(JComponent c, Graphics g,
                                               String text, int underlinedIndex,
                                               float x, float y,
                                               boolean useFPAPI) {
    if (text == null || text.length() <= 0) {
      return;
    }
    drawString(c, g, text, x, y, useFPAPI);
    int textLength = text.length();
    if (underlinedIndex >= 0 && underlinedIndex < textLength ) {
      float underlineRectY = y;
      int underlineRectHeight = 1;
      float underlineRectX = 0;
      int underlineRectWidth = 0;
//      boolean isPrinting = isPrinting(g);
//      boolean needsTextLayout = isPrinting;
      boolean needsTextLayout = false;
      if (!needsTextLayout) {
        synchronized (charsBufferLock) {
          syncCharsBuffer(text);
          needsTextLayout =
            isComplexLayout(charsBuffer, 0, textLength);
        }
      }
      if (!needsTextLayout) {
        FontMetrics fm = g.getFontMetrics();
        underlineRectX = x +
          stringWidth(c,fm,
            text.substring(0,underlinedIndex));
        underlineRectWidth = fm.charWidth(text.
          charAt(underlinedIndex));
      } else {
        Graphics2D g2d = getGraphics2D(g);
        if (g2d != null) {
          TextLayout layout =
            createTextLayout(c, text, g2d.getFont(),
              g2d.getFontRenderContext());
//          if (isPrinting) {
//            float screenWidth = (float)g2d.getFont().
//              getStringBounds(text, getFontRenderContext(c)).getWidth();
//            // If text fits the screenWidth, then do not need to justify
//            if (stringWidth(c, g2d.getFontMetrics(),
//              text) > screenWidth) {
//              layout = layout.getJustifiedLayout(screenWidth);
//            }
//          }
          TextHitInfo leading =
            TextHitInfo.leading(underlinedIndex);
          TextHitInfo trailing =
            TextHitInfo.trailing(underlinedIndex);
          Shape shape =
            layout.getVisualHighlightShape(leading, trailing);
          Rectangle rect = shape.getBounds();
          underlineRectX = x + rect.x;
          underlineRectWidth = rect.width;
        }
      }
      g.fillRect((int) underlineRectX, (int) underlineRectY + 1,
        underlineRectWidth, underlineRectHeight);
    }
  }


  /*
   * Tries it best to get Graphics2D out of the given Graphics
   * returns null if can not derive it.
   */
  public static Graphics2D getGraphics2D(Graphics g) {
    if (g instanceof Graphics2D) {
      return (Graphics2D) g;
//    } else if (g instanceof ProxyPrintGraphics) {
//      return (Graphics2D)(((ProxyPrintGraphics)g).getGraphics());
    } else {
      return null;
    }
  }


  /*
   * Returns FontRenderContext associated with Component.
   * FontRenderContext from Component.getFontMetrics is associated
   * with the component.
   *
   * Uses Component.getFontMetrics to get the FontRenderContext from.
   * see JComponent.getFontMetrics and TextLayoutStrategy.java
   */
  public static FontRenderContext getFontRenderContext(Component c) {
    assert c != null;
    if (c == null) {
      return DEFAULT_FRC;
    } else {
      return c.getFontMetrics(c.getFont()).getFontRenderContext();
    }
  }
}