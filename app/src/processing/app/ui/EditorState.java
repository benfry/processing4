/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-21 The Processing Foundation
  Copyright (c) 2011-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package processing.app.ui;

import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.List;

import processing.app.Preferences;


/**
 * Stores state information (location, size, display device) for an Editor window.
 * Originally was used to restore sketch windows on startup, though that was removed
 * at some point (3.0?) because it was unreliable and not always appreciated.
 *
 * This version is primarily just used to get the location (and display) to open
 * a new Editor window, though some of the vestigial bits are still in there in case
 * we want to restore the ability to restore windows on startup.
 *
 * Previous scenarios:
 * <ul>
 *     <li>new untitled sketch (needs device, needs bounds)</li>
 *     <li>restoring sketch from recent menu
 *       <ul>
 *         <li>device is found and size matches</li>
 *         <li>device cannot be found</li>
 *         <li>device is found but its size has changed</li>
 *       </ul>
 *     </li>
 *     <li>re-opening sketch in a new mode</li>
 * </ul>
 */
public class EditorState {
  Rectangle editorBounds;
  int dividerLocation;
  boolean isMaximized;
  GraphicsConfiguration deviceConfig;

  /** How far to offset a new window from the previous window */
  static final int WINDOW_OFFSET = 28;

  /**
   * Keep a reference to the last device config so we know which display to
   * use when creating a new window after all windows have been closed.
   */
  static GraphicsConfiguration lastConfig;


  /**
   * Create a fresh editor state object from the default screen device and
   * set its placement relative to the last opened window.
   * @param editors List of active editor objects
   */
  static public EditorState nextEditor(List<Editor> editors) {
    Editor lastEditor = null;
    int editorCount = editors.size();
    if (editorCount > 0) {
      lastEditor = editors.get(editorCount-1);
    }

    // update lastConfig so it can be set for this Editor and
    // for the next Editor created if the last window is closed.
    if (lastEditor != null) {
      lastConfig = lastEditor.getGraphicsConfiguration();
    }
    if (lastConfig == null) {
      lastConfig = getDefaultConfig();
    }

    EditorState outgoing = new EditorState();
    outgoing.initLocation(lastConfig, lastEditor);
    return outgoing;
  }


  public String toString() {
    return (editorBounds.x + "," +
            editorBounds.y + "," +
            editorBounds.width + "," +
            editorBounds.height + "," +
            dividerLocation + "," +
            deviceConfig);
  }


  public GraphicsConfiguration getConfig() {
    return deviceConfig;
  }


  static GraphicsConfiguration getDefaultConfig() {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice device = ge.getDefaultScreenDevice();
    return device.getDefaultConfiguration();
  }


  /**
   * Figure out the next location by sizing up the last editor in the list.
   * If no editors are opened, it'll just open on the main screen.
   * @param lastOpened The last Editor opened, used to determine display and location
   */
  void initLocation(GraphicsConfiguration lastConfig, Editor lastOpened) {
    deviceConfig = lastConfig;
    Rectangle deviceBounds = deviceConfig.getBounds();

    int defaultWidth =
      Toolkit.zoom(Preferences.getInteger("editor.window.width.default"));
    int defaultHeight =
      Toolkit.zoom(Preferences.getInteger("editor.window.height.default"));

    defaultWidth = Math.min(defaultWidth, deviceBounds.width);
    defaultHeight = Math.min(defaultHeight, deviceBounds.height);

    if (lastOpened == null) {
      // If no current active editor, use default placement.
      // Center the window on ths screen, taking into account that the
      // upper-left corner of the device may have a non (0, 0) origin.
      int editorX =
        deviceBounds.x + (deviceBounds.width - defaultWidth) / 2;
      int editorY =
        deviceBounds.y + (deviceBounds.height - defaultHeight) / 2;
      editorBounds =
        new Rectangle(editorX, editorY, defaultWidth, defaultHeight);
      dividerLocation = 0;

    } else {
      // With a currently active editor, open the new window using the same
      // dimensions and divider location, but offset slightly.
      //GraphicsDevice device = lastOpened.getGraphicsConfiguration().getDevice();
      //System.out.println("last opened device is " + device);

      isMaximized = (lastOpened.getExtendedState() == Frame.MAXIMIZED_BOTH);
      editorBounds = lastOpened.getBounds();
      editorBounds.x += WINDOW_OFFSET;
      editorBounds.y += WINDOW_OFFSET;
      dividerLocation = lastOpened.getDividerLocation();

      if (!deviceBounds.contains(editorBounds)) {
        // Warp the next window to a random-ish location on screen.
        editorBounds.x = deviceBounds.x +
          (int) (Math.random() * (deviceBounds.width - defaultWidth));
        editorBounds.y = deviceBounds.y +
          (int) (Math.random() * (deviceBounds.height - defaultHeight));
      }
      if (isMaximized) {
        editorBounds.width = defaultWidth;
        editorBounds.height = defaultHeight;
      }
    }
  }


  void apply(Editor editor) {
    editor.setBounds(editorBounds);

    if (dividerLocation == 0) {
      dividerLocation = 2 * editor.getSize().height / 3;
    }
    editor.setDividerLocation(dividerLocation);

    if (isMaximized) {
      editor.setExtendedState(Frame.MAXIMIZED_BOTH);
    }

    // note: doesn't do anything with the device, though that could be
    // added if it's something that would be necessary (i.e. to store windows and
    // re-open them on when re-opening Processing)
  }
}
