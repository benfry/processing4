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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

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

  Color urlRolloverColor;
  Color urlPressedColor;

  ImageIcon clipboardEnabledIcon;
  ImageIcon clipboardRolloverIcon;
  ImageIcon clipboardPressedIcon;

  ImageIcon collapseEnabledIcon;
  ImageIcon collapseRolloverIcon;
  ImageIcon collapsePressedIcon;

  ImageIcon expandEnabledIcon;
  ImageIcon expandRolloverIcon;
  ImageIcon expandPressedIcon;

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
        updateMouse(e.getX(), false);
      }

      @Override
      public void mousePressed(MouseEvent e) {
        updateMouse(e.getX(), true);
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
        updateMouse(e.getX(), false);  // no longer pressed
      }

      @Override
      public void mouseExited(MouseEvent e) {
        updateMouse(-100, false);
      }
    });

    addMouseMotionListener(new MouseMotionListener() {
      @Override
      public void mouseDragged(MouseEvent e) {
        // BasicSplitPaneUI.startDragging gets called even when you click but
        // don't drag, so we can't expand the console whenever that gets called
        // or the button wouldn't work.
        setCollapsed(false);

        updateMouse(e.getX(), true);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        updateMouse(e.getX(), false);
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


  void updateMouse(int mouseX, boolean pressed) {
    mouseState = NONE;
    if (mouseX > sizeW - buttonEach && mouseX < sizeW) {
      mouseState = pressed ? COLLAPSE_PRESSED : COLLAPSE_ROLLOVER;

    } else if (message != null && !message.isEmpty()) {
      if (sizeW - 2* buttonEach < mouseX) {
        mouseState = pressed ? CLIPBOARD_PRESSED : CLIPBOARD_ROLLOVER;

      } else if (url != null && mouseX > LEFT_MARGIN && mouseX < messageRight) {
        mouseState = pressed ? URL_PRESSED : URL_ROLLOVER;
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
    urlRolloverColor = Theme.getColor("status.url.rollover.color");
    urlPressedColor = Theme.getColor("status.url.pressed.color");

    String buttonEnabledColor = Theme.get("status.button.enabled.color");
    String buttonRolloverColor = Theme.get("status.button.rollover.color");
    String buttonPressedColor = Theme.get("status.button.pressed.color");

    clipboardEnabledIcon = Toolkit.renderIcon("status/copy-to-clipboard", buttonEnabledColor, ICON_SIZE);
    clipboardRolloverIcon = Toolkit.renderIcon("status/copy-to-clipboard", buttonRolloverColor, ICON_SIZE);
    clipboardPressedIcon = Toolkit.renderIcon("status/copy-to-clipboard", buttonPressedColor, ICON_SIZE);

    collapseEnabledIcon = Toolkit.renderIcon("status/console-collapse", buttonEnabledColor, ICON_SIZE);
    collapseRolloverIcon = Toolkit.renderIcon("status/console-collapse", buttonRolloverColor, ICON_SIZE);
    collapsePressedIcon = Toolkit.renderIcon("status/console-collapse", buttonPressedColor, ICON_SIZE);

    expandEnabledIcon = Toolkit.renderIcon("status/console-expand", buttonEnabledColor, ICON_SIZE);
    expandRolloverIcon = Toolkit.renderIcon("status/console-expand", buttonRolloverColor, ICON_SIZE);
    expandPressedIcon = Toolkit.renderIcon("status/console-expand", buttonPressedColor, ICON_SIZE);

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
      // set the highlight color on rollover so that the user's not surprised
      // to see the web browser open when they click
      if (mouseState == URL_ROLLOVER) {
        g.setColor(urlRolloverColor);
      } else if (mouseState == URL_PRESSED) {
        g.setColor(urlPressedColor);
      } else {
        g.setColor(fgColor[mode]);
      }
      // calculate right edge of the text for rollovers (otherwise the pane
      // cannot be resized up or down whenever a URL is being displayed)
      messageRight += g.getFontMetrics().stringWidth(message);

      g.drawString(message, LEFT_MARGIN, (sizeH + ascent) / 2);
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
      ImageIcon glyph = clipboardEnabledIcon;
      if (mouseState == CLIPBOARD_ROLLOVER) {
        glyph = clipboardRolloverIcon;
      } else if (mouseState == CLIPBOARD_PRESSED) {
        glyph = clipboardPressedIcon;
      }
      drawButton(g, glyph, 1);
      g.setFont(font);
    }

    // draw collapse/expand button
    ImageIcon glyph;
    if (collapsed) {
      if (mouseState == COLLAPSE_ROLLOVER) {
        glyph = expandRolloverIcon;
      } else if (mouseState == COLLAPSE_PRESSED) {
        glyph = expandPressedIcon;
      } else {
        glyph = expandEnabledIcon;
      }
    } else {
      if (mouseState == COLLAPSE_ROLLOVER) {
        glyph = collapseRolloverIcon;
      } else if (mouseState == COLLAPSE_PRESSED) {
        glyph = collapsePressedIcon;
      } else {
        glyph = collapseEnabledIcon;
      }
    }
    drawButton(g, glyph, 0);
  }


  /**
   * @param pos A zero-based button index with 0 as the rightmost button
   */
  //private void drawButton(Graphics g, String symbol, int pos, boolean highlight) {
  private void drawButton(Graphics g, ImageIcon icon, int pos) {
    int left = sizeW - (pos + 1) * buttonEach;
    icon.paintIcon(this, g,
                left + (buttonEach - icon.getIconWidth()) / 2,
                (buttonEach - icon.getIconHeight()) / 2);
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
