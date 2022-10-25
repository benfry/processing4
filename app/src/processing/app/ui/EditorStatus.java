/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-22 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import processing.app.Platform;
import processing.app.Preferences;
import processing.core.PApplet;


/**
 * Panel just below the editing area that contains status messages.
 */
public class EditorStatus extends BasicSplitPaneDivider {
  static final int HIGH = Toolkit.zoom(28);
  static final int ICON_SIZE = Toolkit.zoom(16);
  static final int LEFT_MARGIN = Editor.LEFT_GUTTER;
  static final int RIGHT_MARGIN = Toolkit.zoom(20);

  Color[] fgColor;
  Color[] bgColor;

  @SuppressWarnings("hiding")
  static public final int ERROR = 1;
  static public final int CURSOR_LINE_ERROR = 2;
  static public final int WARNING = 3;
  static public final int CURSOR_LINE_WARNING = 4;
  static public final int NOTICE = 0;

  Editor editor;

  int mode;
  String message = "";

  int messageRight;
  String url;

  static final int NONE = 0;
  static final int URL_ROLLOVER = 1;
  static final int URL_PRESSED = 2;
  static final int COLLAPSE_ROLLOVER = 3;
  static final int COLLAPSE_PRESSED = 4;
  static final int CLIPBOARD_ROLLOVER = 5;
  static final int CLIPBOARD_PRESSED = 6;
  int mouseState;

  Font font;
  FontMetrics metrics;
  int ascent;

  boolean shiftDown;

  ImageIcon[] clipboardIcon;
  ImageIcon[] searchIcon;
  ImageIcon[] collapseIcon;
  ImageIcon[] expandIcon;

  float btnEnabledAlpha;
  float btnRolloverAlpha;
  float btnPressedAlpha;

  int urlEnabledAlpha;
  int urlRolloverAlpha;
  int urlPressedAlpha;

  int sizeW, sizeH;
  // size of the glyph buttons (width and height are identical)
  int buttonEach;
  boolean collapsed = false;

  boolean indeterminate;
  Thread thread;


  public EditorStatus(BasicSplitPaneUI ui, Editor editor) {
    super(ui);
    this.editor = editor;
    empty();
    updateTheme();

    addMouseListener(new MouseAdapter() {

      @Override
      public void mouseEntered(MouseEvent e) {
        updateMouse(e, false);
      }

      @Override
      public void mousePressed(MouseEvent e) {
        updateMouse(e, true);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (mouseState == URL_PRESSED) {
          Platform.openURL(url);

        } else if (mouseState == CLIPBOARD_PRESSED) {
          if (e.isShiftDown()) {
            // open the text in a browser window as a search
            final String fmt = Preferences.get("search.format");
            Platform.openURL(String.format(fmt, PApplet.urlEncode(message)));

          } else {
            // copy the text to the clipboard
            Clipboard clipboard = getToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(message), null);
            System.out.println("Copied to the clipboard. " +
                               "Use shift-click to search the web instead.");
          }

        } else if (mouseState == COLLAPSE_PRESSED) {
          setCollapsed(!collapsed);
        }
        updateMouse(e, false);  // no longer pressed
      }

      @Override
      public void mouseExited(MouseEvent e) {
        updateMouse(null, false);
      }
    });

    addMouseMotionListener(new MouseMotionListener() {
      @Override
      public void mouseDragged(MouseEvent e) {
        // BasicSplitPaneUI.startDragging gets called even when you click but
        // don't drag, so we can't expand the console whenever that gets called
        // or the button wouldn't work.
        setCollapsed(false);

        updateMouse(e, true);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        updateMouse(e, false);
      }
    });

    editor.getTextArea().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (shiftDown != e.isShiftDown()) {
          shiftDown = e.isShiftDown();
          repaint();
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        if (shiftDown != e.isShiftDown()) {
          shiftDown = e.isShiftDown();
          repaint();
        }
      }
    });
  }


  void setCollapsed(boolean newState) {
    if (collapsed != newState) {
      collapsed = newState;
      editor.footer.setVisible(!newState);
      splitPane.resetToPreferredSizes();
    }
  }


  void updateMouse(MouseEvent e, boolean pressed) {
    mouseState = NONE;
    shiftDown = false;

    if (e != null) {
      int mouseX = e.getX();
      shiftDown = e.isShiftDown();

      if (mouseX > sizeW - buttonEach && mouseX < sizeW) {
        mouseState = pressed ? COLLAPSE_PRESSED : COLLAPSE_ROLLOVER;

      } else if (message != null && !message.isEmpty()) {
        if (sizeW - 2 * buttonEach < mouseX) {
          mouseState = pressed ? CLIPBOARD_PRESSED : CLIPBOARD_ROLLOVER;

        } else if (url != null && mouseX > LEFT_MARGIN && mouseX < messageRight) {
          mouseState = pressed ? URL_PRESSED : URL_ROLLOVER;
        }
      }
    }

    // only change on the rollover, no need to update on press
    switch (mouseState) {
    case CLIPBOARD_ROLLOVER:
    case URL_ROLLOVER:
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      break;
    case COLLAPSE_ROLLOVER:
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      break;
    case NONE:
      setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      break;
    }
    repaint();
  }


  static String findURL(String message) {
    String[] m = PApplet.match(message, "http\\S+");
    if (m != null) {
      return m[0];
    }
    return null;
  }


  protected void updateTheme() {
    urlEnabledAlpha = 255 * Theme.getInteger("status.url.enabled.alpha") / 100;
    urlRolloverAlpha = 255 * Theme.getInteger("status.url.rollover.alpha") / 100;
    urlPressedAlpha = 255 * Theme.getInteger("status.url.pressed.alpha") / 100;

    String[] stateColors = new String[] {
      Theme.get("status.notice.fgcolor"),
      Theme.get("status.error.fgcolor"),
      Theme.get("status.error.fgcolor"),
      Theme.get("status.warning.fgcolor"),
      Theme.get("status.warning.fgcolor")
    };

    clipboardIcon = renderIcons("status/copy-to-clipboard", stateColors);
    searchIcon = renderIcons("status/search", stateColors);
    collapseIcon = renderIcons("status/console-collapse", stateColors);
    expandIcon = renderIcons("status/console-expand", stateColors);

    btnEnabledAlpha = Theme.getInteger("status.button.enabled.alpha") / 100f;
    btnRolloverAlpha = Theme.getInteger("status.button.rollover.alpha") / 100f;
    btnPressedAlpha = Theme.getInteger("status.button.pressed.alpha") / 100f;

    fgColor = new Color[] {
      Theme.getColor("status.notice.fgcolor"),
      Theme.getColor("status.error.fgcolor"),
      Theme.getColor("status.error.fgcolor"),
      Theme.getColor("status.warning.fgcolor"),
      Theme.getColor("status.warning.fgcolor")
    };

    bgColor = new Color[] {
      Theme.getColor("status.notice.bgcolor"),
      Theme.getColor("status.error.bgcolor"),
      Theme.getColor("status.error.bgcolor"),
      Theme.getColor("status.warning.bgcolor"),
      Theme.getColor("status.warning.bgcolor")
    };

    font = Theme.getFont("status.font");
    metrics = null;
  }


  static private ImageIcon[] renderIcons(String path, String[] hexColors) {
    int count = hexColors.length;
    ImageIcon[] outgoing = new ImageIcon[count];
    for (int i = 0; i < count; i++) {
      outgoing[i] = Toolkit.renderIcon(path, hexColors[i], ICON_SIZE);
    }
    return outgoing;
  }

  public void empty() {
    mode = NOTICE;
    message = "";
    url = null;
    repaint();
  }


  public void message(String message, int mode) {
    this.message = message;
    this.mode = mode;

    url = findURL(message);
    repaint();
  }


  public void notice(String message) {
    message(message, NOTICE);
  }


  public void warning(String message) {
    message(message, WARNING);
  }


  public void error(String message) {
    message(message, ERROR);
  }


  public void startIndeterminate() {
    indeterminate = true;
    thread = new Thread("Editor Status") {
      public void run() {
        while (Thread.currentThread() == thread) {
          repaint();
          try {
            Thread.sleep(1000 / 10);
          } catch (InterruptedException ignored) { }
        }
      }
    };
    thread.start();
  }


  public void stopIndeterminate() {
    indeterminate = false;
    thread = null;
    repaint();
  }


  public void paint(Graphics g) {
    Toolkit.prepareGraphics(g);
    sizeW = getWidth();
    sizeH = getHeight();
    buttonEach = sizeH;

    g.setFont(font);
    if (metrics == null) {
      metrics = g.getFontMetrics();
      ascent = metrics.getAscent();
    }

    g.setColor(bgColor[mode]);
    g.fillRect(0, 0, sizeW, sizeH);

    messageRight = LEFT_MARGIN;  // needs to be reset (even) if msg null
    // https://github.com/processing/processing/issues/3265
    if (message != null) {
      // font needs to be set each time on osx
      g.setFont(font);
      // calculate right edge of the text for rollovers (otherwise the pane
      // cannot be resized up or down whenever a URL is being displayed)
      messageRight += g.getFontMetrics().stringWidth(message);

      // set the highlight color on rollover so that the user is
      // not surprised to see the web browser open when they click
      int alpha = 255;
      if (url != null) {
        if (mouseState == URL_ROLLOVER) {
          alpha = urlRolloverAlpha;
        } else if (mouseState == URL_PRESSED) {
          alpha = urlPressedAlpha;
        } else {
          alpha = urlEnabledAlpha;
        }
      }
      if (alpha == 255) {
        g.setColor(fgColor[mode]);
      } else {
        g.setColor(new Color((alpha << 24) | (fgColor[mode].getRGB() & 0xFFFFFF), true));
      }
      g.drawString(message, LEFT_MARGIN, (sizeH / 2) + (ascent / 4) + 1);
    }

    if (indeterminate) {
      int w = Toolkit.getButtonWidth();
      int x = getWidth() - Math.max(RIGHT_MARGIN, (int)(buttonEach * 1.2)) - w;
      int y = sizeH / 3;
      int h = sizeH / 3;
      g.setColor(new Color(0x80000000, true));
      g.drawRect(x, y, w, h);
      for (int i = 0; i < 10; i++) {
        int r = (int) (x + Math.random() * w);
        g.drawLine(r, y, r, y+h);
      }

    } else if (message != null && !message.isEmpty()) {
      ImageIcon glyph;
      float alpha;
      if (shiftDown) {
        glyph = searchIcon[mode];
      } else {
        glyph = clipboardIcon[mode];
      }
      if (mouseState == CLIPBOARD_ROLLOVER) {
        alpha = btnRolloverAlpha;
      } else if (mouseState == CLIPBOARD_PRESSED) {
        alpha = btnPressedAlpha;
      } else {
        alpha = btnEnabledAlpha;
      }
      drawButton(g, 1, glyph, alpha);
      g.setFont(font);
    }

    // draw collapse/expand button
    ImageIcon glyph;
    float alpha;
    if (collapsed) {
      glyph = expandIcon[mode];
    } else {
      glyph = collapseIcon[mode];
    }
    if (mouseState == COLLAPSE_ROLLOVER) {
      alpha = btnRolloverAlpha;
    } else if (mouseState == COLLAPSE_PRESSED) {
      alpha = btnPressedAlpha;
    } else {
      alpha = btnEnabledAlpha;
    }
    drawButton(g, 0, glyph, alpha);
  }


  /**
   * @param pos A zero-based button index with 0 as the rightmost button
   */
  //private void drawButton(Graphics g, String symbol, int pos, boolean highlight) {
  private void drawButton(Graphics g, int pos, ImageIcon icon, float alpha) {
    int left = sizeW - (pos + 1) * buttonEach;

    Graphics2D g2 = (Graphics2D) g.create();
    g2.setComposite(AlphaComposite.SrcAtop.derive(alpha));
    //icon.paintIcon(c, g2, x, y);
    icon.paintIcon(this, g2,
      left + (buttonEach - icon.getIconWidth()) / 2,
      (buttonEach - icon.getIconHeight()) / 2);
    g2.dispose();
  }


  public Dimension getPreferredSize() {
    return getMinimumSize();
  }


  public Dimension getMinimumSize() {
    return new Dimension(Toolkit.zoom(300), HIGH);
  }


  public Dimension getMaximumSize() {
    return new Dimension(super.getMaximumSize().width, HIGH);
  }
}
