/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-22 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.core;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.*;

// loadXML() error handling
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

// TODO have this removed by 4.0 final
import processing.awt.ShimAWT;

import processing.data.*;
import processing.event.*;
import processing.opengl.*;


/**
 * Base class for all sketches that use processing.core.
 * <p/>
 * The <A HREF="https://github.com/processing/processing/wiki/Window-Size-and-Full-Screen">
 * Window Size and Full Screen</A> page on the Wiki has useful information
 * about sizing, multiple displays, full screen, etc.
 * <p/>
 * Processing uses active mode rendering. All animation tasks happen on the
 * "Processing Animation Thread". The setup() and draw() methods are handled
 * by that thread, and events (like mouse movement and key presses, which are
 * fired by the event dispatch thread or EDT) are queued to be safely handled
 * at the end of draw().
 * <p/>
 * Starting with 3.0a6, blit operations are on the EDT, so as not to cause
 * GUI problems with Swing and AWT. In the case of the default renderer, the
 * sketch renders to an offscreen image, then the EDT is asked to bring that
 * image to the screen.
 * <p/>
 * For code that needs to run on the EDT, use EventQueue.invokeLater(). When
 * doing so, be careful to synchronize between that code and the Processing
 * animation thread. That is, you can't call Processing methods from the EDT
 * or at any random time from another thread. Use of a callback function or
 * the registerXxx() methods in PApplet can help ensure that your code doesn't
 * do something naughty.
 * <p/>
 * As of Processing 3.0, we have removed Applet as the base class for PApplet.
 * This means that we can remove lots of legacy code, however one downside is
 * that it's no longer possible (without extra code) to embed a PApplet into
 * another Java application.
 * <p/>
 * As of Processing 3.0, we have discontinued support for versions of Java
 * prior to 1.8. We don't have enough people to support it, and for a
 * project of our (tiny) size, we should be focusing on the future, rather
 * than working around legacy Java code.
 */
@SuppressWarnings({"unused", "FinalStaticMethod", "ManualMinMaxCalculation"})
public class PApplet implements PConstants {
//public class PApplet extends PSketch {  // possible in the next alpha
  /** Full name of the Java version (i.e. 1.5.0_11). */
  static public final String javaVersionName =
    System.getProperty("java.version");

  static public final int javaPlatform;
  static {
    String version = javaVersionName;
    if (javaVersionName.startsWith("1.")) {
      version = version.substring(2);
      javaPlatform = parseInt(version.substring(0, version.indexOf('.')));
    } else {
      // Remove -xxx and .yyy from java.version (@see JEP-223)
      javaPlatform = parseInt(version.replaceAll("-.*","").replaceAll("\\..*",""));
    }
  }

  /**
   * Do not use; javaPlatform or javaVersionName are better options.
   * For instance, javaPlatform is useful when you need a number for
   * comparison, i.e. "if (javaPlatform >= 9)".
   */
  @Deprecated
  public static final float javaVersion = 1 + javaPlatform / 10f;

  /**
   * Current platform in use, one of the PConstants WINDOWS, MACOS, LINUX or OTHER.
   */
  static public int platform;

  static {
    final String name = System.getProperty("os.name");

    if (name.contains("Mac")) {
      platform = MACOS;

    } else if (name.contains("Windows")) {
      platform = WINDOWS;

    } else if (name.equals("Linux")) {  // true for the ibm vm
      platform = LINUX;

    } else {
      platform = OTHER;
    }
  }

  /**
   * Whether to use native (AWT) dialogs for selectInput and selectOutput.
   * The native dialogs on some platforms can be ugly, buggy, or missing
   * features. For 3.3.5, this defaults to true on all platforms.
   */
  static public boolean useNativeSelect = true;

  /** The PGraphics renderer associated with this PApplet */
  public PGraphics g;

  /**
   * System variable that stores the width of the computer screen.
   * For example, if the current screen resolution is 1920x1080,
   * <b>displayWidth</b> is 1920 and <b>displayHeight</b> is 1080.
   *
   * @webref environment
   * @webBrief Variable that stores the width of the computer screen
   * @see PApplet#displayHeight
   * @see PApplet#size(int, int)
   */
  public int displayWidth;

  /**
   * System variable that stores the height of the computer screen.
   * For example, if the current screen resolution is 1920x1080,
   * <b>displayWidth</b> is 1920 and <b>displayHeight</b> is 1080.
   *
   * @webref environment
   * @webBrief Variable that stores the height of the computer screen
   * @see PApplet#displayWidth
   * @see PApplet#size(int, int)
   */
  public int displayHeight;

  public int windowX;
  public int windowY;

  /** A leech graphics object that is echoing all events. */
  public PGraphics recorder;

  /**
   * Command line options passed in from main().
   * This does not include the arguments passed in to PApplet itself.
   * @see PApplet#main
   */
  public String[] args;

  /**
   * Path to sketch folder. Previously undocumented, and made private
   * in 3.0 alpha 5 so that people use the sketchPath() method which
   * will initialize it properly. Call sketchPath() once to set it.
   */
  private String sketchPath;

  static final boolean DEBUG = false;
//  static final boolean DEBUG = true;

  /** Default width and height for sketch when not specified */
  static public final int DEFAULT_WIDTH = 100;
  static public final int DEFAULT_HEIGHT = 100;

  /**
   * The <b>pixels[]</b> array contains the values for all the pixels in the
   * display window. These values are of the color datatype. This array is
   * defined by the size of the display window. For example, if the window is
   * 100 x 100 pixels, there will be 10,000 values and if the window is
   * 200 x 300 pixels, there will be 60,000 values. When the pixel density is
   * set to higher than 1 with the <b>pixelDensity()</b> function, these values
   * will change. See the reference for <b>pixelWidth</b> or <b>pixelHeight</b>
   * for more information.
   * <br /><br />
   * Before accessing this array, the data must be loaded with the <b>loadPixels()</b>
   * function. Failure to do so may result in a NullPointerException. Subsequent
   * changes to the display window will not be reflected in <b>pixels</b> until
   * <b>loadPixels()</b> is called again. After <b>pixels</b> has been modified,
   * the <b>updatePixels()</b> function must be run to update the content of the
   * display window.
   *
   * @webref image:pixels
   * @webBrief Array containing the values for all the pixels in the display window
   * @see PApplet#loadPixels()
   * @see PApplet#updatePixels()
   * @see PApplet#get(int, int, int, int)
   * @see PApplet#set(int, int, int)
   * @see PImage
   * @see PApplet#pixelDensity(int)
   * @see PApplet#pixelWidth
   * @see PApplet#pixelHeight
   */
  public int[] pixels;

  /**
   *
   * System variable which stores the width of the display window. This value
   * is set by the first parameter of the <b>size()</b> function. For
   * example, the function call <b>size(320, 240)</b> sets the <b>width</b>
   * variable to the value 320. The value of <b>width</b> defaults to 100 if
   * <b>size()</b> is not used in a program.
   *
   * @webref environment
   * @webBrief System variable which stores the width of the display window
   * @see PApplet#height
   * @see PApplet#size(int, int)
   */
  public int width = DEFAULT_WIDTH;

  /**
   *
   * System variable which stores the height of the display window. This
   * value is set by the second parameter of the <b>size()</b> function. For
   * example, the function call <b>size(320, 240)</b> sets the <b>height</b>
   * variable to the value 240. The value of <b>height</b> defaults to 100 if
   * <b>size()</b> is not used in a program.
   *
   * @webref environment
   * @webBrief System variable which stores the height of the display window
   * @see PApplet#width
   * @see PApplet#size(int, int)
   */
  public int height = DEFAULT_HEIGHT;

  /**
   *
   * When <b>pixelDensity(2)</b> is used to make use of a high resolution
   * display (called a Retina display on OS X or high-dpi on Windows and
   * Linux), the width and height of the sketch do not change, but the
   * number of pixels is doubled. As a result, all operations that use pixels
   * (like <b>loadPixels()</b>, <b>get()</b>, <b>set()</b>, etc.) happen
   * in this doubled space. As a convenience, the variables <b>pixelWidth</b>
   * and <b>pixelHeight</b> hold the actual width and height of the sketch
   * in pixels. This is useful for any sketch that uses the <b>pixels[]</b>
   * array, for instance, because the number of elements in the array will
   * be <b>pixelWidth*pixelHeight</b>, not <b>width*height</b>.
   *
   * @webref environment
   * @webBrief The actual pixel width when using high resolution display
   * @see PApplet#pixelHeight
   * @see #pixelDensity(int)
   * @see #displayDensity()
   */
  public int pixelWidth;


  /**
   * When <b>pixelDensity(2)</b> is used to make use of a high resolution
   * display (called a Retina display on OS X or high-dpi on Windows and
   * Linux), the width and height of the sketch do not change, but the
   * number of pixels is doubled. As a result, all operations that use pixels
   * (like <b>loadPixels()</b>, <b>get()</b>, <b>set()</b>, etc.) happen
   * in this doubled space. As a convenience, the variables <b>pixelWidth</b>
   * and <b>pixelHeight</b> hold the actual width and height of the sketch
   * in pixels. This is useful for any sketch that uses the <b>pixels[]</b>
   * array, for instance, because the number of elements in the array will
   * be <b>pixelWidth*pixelHeight</b>, not <b>width*height</b>.
   *
   * @webref environment
   * @webBrief The actual pixel height when using high resolution display
   * @see PApplet#pixelWidth
   * @see #pixelDensity(int)
   * @see #displayDensity()
   */
  public int pixelHeight;

  // Making this private until we have a compelling reason to make it public.
  // Seems problematic/weird for it to be possible to set windowRatio = false
  // relative to how other API works. And not sure what the use case would be.
  private boolean windowRatio;

  /**
   * Version of mouseX/mouseY to use with windowRatio().
   */
  public int rmouseX;
  public int rmouseY;

  /**
   * Version of width/height to use with windowRatio().
   */
  public int rwidth;
  public int rheight;

  /** Offset from left when windowRatio is in use. */
  public float ratioLeft;

  /** Offset from the top when windowRatio is in use. */
  public float ratioTop;

  /** Amount of scaling to be applied for the window ratio. */
  public float ratioScale;

  /**
   * Keeps track of ENABLE_KEY_REPEAT hint
   */
  protected boolean keyRepeatEnabled = false;

  /**
   * The system variable <b>mouseX</b> always contains the current horizontal
   * coordinate of the mouse.
   * <br /><br />
   * Note that Processing can only track the mouse position when the pointer
   * is over the current window. The default value of <b>mouseX</b> is <b>0</b>,
   * so <b>0</b> will be returned until the mouse moves in front of the sketch
   * window. (This typically happens when a sketch is first run.)  Once the
   * mouse moves away from the window, <b>mouseX</b> will continue to report
   * its most recent position.
   *
   * @webref input:mouse
   * @webBrief The system variable that always contains the current horizontal coordinate of the mouse
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public int mouseX;

  /**
   * The system variable <b>mouseY</b> always contains the current
   * vertical coordinate of the mouse.
   * <br /><br />
   * Note that Processing can only track the mouse position when the pointer
   * is over the current window. The default value of <b>mouseY</b> is <b>0</b>,
   * so <b>0</b> will be returned until the mouse moves in front of the sketch
   * window. (This typically happens when a sketch is first run.)  Once the
   * mouse moves away from the window, <b>mouseY</b> will continue to report
   * its most recent position.
   *
   * @webref input:mouse
   * @webBrief The system variable that always contains the current vertical coordinate of the mouse
   * @see PApplet#mouseX
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   *
   */
  public int mouseY;

  /**
   * The system variable <b>pmouseX</b> always contains the horizontal
   * position of the mouse in the frame previous to the current frame.<br />
   * <br />
   * You may find that <b>pmouseX</b> and <b>pmouseY</b> have different values
   * when referenced inside of <b>draw()</b> and inside of mouse events like
   * <b>mousePressed()</b> and <b>mouseMoved()</b>. Inside <b>draw()</b>,
   * <b>pmouseX</b> and <b>pmouseY</b> update only once per frame (once per trip
   * through the <b>draw()</b> loop). But inside mouse events, they update each
   * time the event is called. If these values weren't updated immediately during
   * events, then the mouse position would be read only once per frame, resulting
   * in slight delays and choppy interaction. If the mouse variables were always
   * updated multiple times per frame, then something like <b>line(pmouseX, pmouseY,
   * mouseX, mouseY)</b> inside <b>draw()</b> would have lots of gaps, because
   * <b>pmouseX</b> may have changed several times in between the calls to
   * <b>line()</b>.<br /><br />
   * If you want values relative to the previous frame, use <b>pmouseX</b> and
   * <b>pmouseY</b> inside <b>draw()</b>. If you want continuous response, use
   * <b>pmouseX</b> and <b>pmouseY</b> inside the mouse event functions.
   *
   * @webref input:mouse
   * @webBrief The system variable that always contains the horizontal
   * position of the mouse in the frame previous to the current frame
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public int pmouseX;

  /**
   * The system variable <b>pmouseY</b> always contains the vertical position
   * of the mouse in the frame previous to the current frame. More detailed
   * information about how <b>pmouseY</b> is updated inside of <b>draw()</b>
   * and mouse events is explained in the reference for <b>pmouseX</b>.
   *
   * @webref input:mouse
   * @webBrief The system variable that always contains the vertical position
   * of the mouse in the frame previous to the current frame
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public int pmouseY;

  /**
   * Previous mouseX/Y for the draw loop, separated out because this is
   * separate from the pmouseX/Y when inside the mouse event handlers.
   * See emouseX/Y for an explanation.
   */
  protected int dmouseX, dmouseY;

  /**
   * The pmouseX/Y for the event handlers (mousePressed(), mouseDragged() etc)
   * these are different because mouse events are queued to the end of
   * draw, so the previous position has to be updated on each event,
   * as opposed to the pmouseX/Y that's used inside draw, which is expected
   * to be updated once per trip through draw().
   */
  protected int emouseX, emouseY;

  /**
   * Used to set pmouseX/Y to mouseX/Y the first time mouseX/Y are used,
   * otherwise pmouseX/Y are always zero, causing a nasty jump.
   * <p>
   * Just using (frameCount == 0) won't work since mouseXxxxx()
   * may not be called until a couple frames into things.
   * <p>
   * @deprecated Please refrain from using this variable, it will be removed
   * from future releases of Processing because it cannot be used consistently
   * across platforms and input methods.
   */
  @Deprecated
  public boolean firstMouse = true;

  /**
   * When a mouse button is pressed, the value of the system variable
   * <b>mouseButton</b> is set to either <b>LEFT</b>, <b>RIGHT</b>, or
   * <b>CENTER</b>, depending on which button is pressed. (If no button is
   * pressed, <b>mouseButton</b> may be reset to <b>0</b>. For that reason,
   * it's best to use <b>mousePressed</b> first to test if any button is being
   * pressed, and only then test the value of <b>mouseButton</b>, as shown in
   * the examples above.)
   *
   * <h3>Advanced:</h3>
   *
   * If running on macOS, a ctrl-click will be interpreted as the right-hand
   * mouse button (unlike Java, which reports it as the left mouse).
   * @webref input:mouse
   * @webBrief Shows which mouse button is pressed
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public int mouseButton;

  /**
   * The <b>mousePressed</b> variable stores whether a mouse button has been pressed.
   * The <b>mouseButton</b> variable (see the related reference entry) can be used to
   * determine which button has been pressed.
   * <br /><br />
   * Mouse and keyboard events only work when a program has <b>draw()</b>.
   * Without <b>draw()</b>, the code is only run once and then stops
   * listening for events.
   *
   * @webref input:mouse
   * @webBrief Variable storing if a mouse button is pressed
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public boolean mousePressed;

  // macOS: Ctrl + Left Mouse is converted to Right Mouse.
  // This boolean tracks whether the conversion happened on PRESS,
  // to report the same button during DRAG and on RELEASE,
  // even though CTRL might have been released already.
  // Otherwise, the events are inconsistent.
  // https://github.com/processing/processing/issues/5672
  private boolean macosCtrlClick;


  /** @deprecated Use a mouse event handler that passes an event instead. */
  @Deprecated
  public MouseEvent mouseEvent;

  /**
   * The system variable <b>key</b> always contains the value of the most
   * recent key on the keyboard that was used (either pressed or released).
   * <br/> <br/>
   * For non-ASCII keys, use the <b>keyCode</b> variable. The keys included
   * in the ASCII specification (BACKSPACE, TAB, ENTER, RETURN, ESC, and
   * DELETE) do not require checking to see if they key is coded, and you
   * should simply use the <b>key</b> variable instead of <b>keyCode</b> If
   * you're making cross-platform projects, note that the ENTER key is
   * commonly used on PCs and Unix and the RETURN key is used instead on
   * Macintosh. Check for both ENTER and RETURN to make sure your program
   * will work for all platforms.
   * <br /><br />
   * There are issues with how <b>keyCode</b> behaves across different
   * renderers and operating systems. Watch out for unexpected behavior as
   * you switch renderers and operating systems.
   *
   * <h3>Advanced</h3>
   *
   * Last key pressed.
   * <p>
   * If it's a coded key, i.e. UP/DOWN/CTRL/SHIFT/ALT,
   * this will be set to CODED (0xffff or 65535).
   *
   * @webref input:keyboard
   * @webBrief The system variable that always contains the value of the most
   * recent key on the keyboard that was used (either pressed or released)
   * @see PApplet#keyCode
   * @see PApplet#keyPressed
   * @see PApplet#keyPressed()
   * @see PApplet#keyReleased()
   */
  public char key;

  /**
   * The variable <b>keyCode</b> is used to detect special keys such as the
   * UP, DOWN, LEFT, RIGHT arrow keys and ALT, CONTROL, SHIFT.
   * <br /><br />
   * When checking for these keys, it can be useful to first check if the key
   * is coded. This is done with the conditional <b>if (key == CODED)</b>, as
   * shown in the example above.
   * <br/> <br/>
   * The keys included in the ASCII specification (BACKSPACE, TAB, ENTER,
   * RETURN, ESC, and DELETE) do not require checking to see if the key is
   * coded; for those keys, you should simply use the <b>key</b> variable
   * directly (and not <b>keyCode</b>).  If you're making cross-platform
   * projects, note that the ENTER key is commonly used on PCs and Unix,
   * while the RETURN key is used on Macs. Make sure your program will work
   * on all platforms by checking for both ENTER and RETURN.
   * <br/> <br/>
   * For those familiar with Java, the values for UP and DOWN are simply
   * shorter versions of Java's <b>KeyEvent.VK_UP</b> and <b>KeyEvent.VK_DOWN</b>.
   *  Other <b>keyCode</b> values can be found in the Java
   * <a href="https://docs.oracle.com/javase/8/docs/api/java/awt/event/KeyEvent.html">KeyEvent</a>
   * reference.
   * <br /><br />
   * There are issues with how <b>keyCode</b> behaves across different
   * renderers and operating systems. Watch out for unexpected behavior
   * as you switch renderers and operating systems, and also whenever
   * you are using keys not mentioned in this reference entry.
   * <br /><br />
   * If you are using P2D or P3D as your renderer, use the
   * <a href="https://jogamp.org/deployment/jogamp-next/javadoc/jogl/javadoc/com/jogamp/newt/event/KeyEvent.html">NEWT KeyEvent constants</a>.
   *
   * <h3>Advanced</h3>
   * When "key" is set to CODED, this will contain a Java key code.
   * <p>
   * For the arrow keys, keyCode will be one of UP, DOWN, LEFT and RIGHT.
   * ALT, CONTROL and SHIFT are also available. A full set of constants
   * can be obtained from java.awt.event.KeyEvent, from the VK_XXXX variables.
   *
   * @webref input:keyboard
   * @webBrief Used to detect special keys such as the UP, DOWN, LEFT, RIGHT arrow keys and ALT, CONTROL, SHIFT
   * @see PApplet#key
   * @see PApplet#keyPressed
   * @see PApplet#keyPressed()
   * @see PApplet#keyReleased()
   */
  public int keyCode;

  /**
   * The boolean system variable <b>keyPressed</b> is <b>true</b>
   * if any key is pressed and <b>false</b> if no keys are pressed.
   * <br /><br />
   * Note that there is a similarly named function called <b>keyPressed()</b>.
   * See its reference page for more information.
   *
   * @webref input:keyboard
   * @webBrief The boolean system variable that is <b>true</b> if any key
   * is pressed and <b>false</b> if no keys are pressed
   * @see PApplet#key
   * @see PApplet#keyCode
   * @see PApplet#keyPressed()
   * @see PApplet#keyReleased()
   */
  public boolean keyPressed;
  List<Long> pressedKeys = new ArrayList<>(6);

  /**
   * The last KeyEvent object passed into a mouse function.
   * @deprecated Use a key event handler that passes an event instead.
   */
  @Deprecated
  public KeyEvent keyEvent;

  /**
   *
   * Confirms if a Processing program is "focused", meaning that it is active
   * and will accept input from mouse or keyboard. This variable is <b>true</b> if
   * it is focused and <b>false</b> if not.
   *
   * @webref environment
   * @webBrief Confirms if a Processing program is "focused"
   */
  public boolean focused = false;

  /**
   * Time in milliseconds when the sketch was started.
   * <p>
   * Used by the millis() function.
   */
  long millisOffset = System.currentTimeMillis();

  /**
   *
   * The system variable <b>frameRate</b> contains the approximate frame rate
   * of the software as it executes. The initial value is 10 fps and is
   * updated with each frame. The value is averaged (integrated) over several
   * frames. As such, this value won't be valid until after 5-10 frames.
   *
   * @webref environment
   * @webBrief The system variable that contains the approximate frame rate
   * of the software as it executes
   * @see PApplet#frameRate(float)
   * @see PApplet#frameCount
   */
  public float frameRate = 60;

  protected boolean looping = true;

  /** flag set to true when redraw() is called by the user */
  protected boolean redraw = true;

  /**
   * The system variable <b>frameCount</b> contains the number o
   * frames displayed since the program started. Inside <b>setup()</b>
   * the value is 0 and during the first iteration of draw it is 1, etc.
   *
   * @webref environment
   * @webBrief The system variable that contains the number of frames
   * displayed since the program started
   * @see PApplet#frameRate(float)
   * @see PApplet#frameRate
   */
  public int frameCount;

  /** true if the sketch has stopped permanently. */
  public volatile boolean finished;

  /** used by the UncaughtExceptionHandler, so has to be static */
  static Throwable uncaughtThrowable;

  /**
   * true if exit() has been called so that things shut down
   * once the main thread kicks off.
   */
  protected boolean exitCalled;

  // ok to be static because it's not possible to mix enabled/disabled
  static protected boolean disableAWT;

  // messages to send if attached as an external vm

  /**
   * Position of the upper left-hand corner of the editor window
   * that launched this sketch.
   */
  static public final String ARGS_EDITOR_LOCATION = "--editor-location";

  static public final String ARGS_EXTERNAL = "--external";

  /**
   * Location for where to position the sketch window on screen.
   * <p>
   * This is used by the editor to when saving the previous sketch
   * location, or could be used by other classes to launch at a
   * specific position on-screen.
   */
  static public final String ARGS_LOCATION = "--location";

  /** Used by the PDE to suggest a display (set in prefs, passed on Run) */
  static public final String ARGS_DISPLAY = "--display";

  /** Disable AWT so that LWJGL and others can run */
  static public final String ARGS_DISABLE_AWT = "--disable-awt";

//  static public final String ARGS_SPAN_DISPLAYS = "--span";

  static public final String ARGS_BGCOLOR = "--bgcolor";

  static public final String ARGS_FULL_SCREEN = "--full-screen";

  static public final String ARGS_WINDOW_COLOR = "--window-color";

  static public final String ARGS_PRESENT = "--present";

  static public final String ARGS_STOP_COLOR = "--stop-color";

  static public final String ARGS_HIDE_STOP = "--hide-stop";

  /**
   * Allows the user or PdeEditor to set a specific sketch folder path.
   * <p>
   * Used by PdeEditor to pass in the location where saveFrame()
   * and all that stuff should write things.
   */
  static public final String ARGS_SKETCH_FOLDER = "--sketch-path";

  static public final String ARGS_UI_SCALE = "--ui-scale";

  /**
   * When run externally to a PdeEditor,
   * this is sent by the sketch when it quits.
   */
  static public final String EXTERNAL_STOP = "__STOP__";

  /**
   * When run externally to a PDE Editor, this is sent by the sketch
   * whenever the window is moved.
   * <p>
   * This is used so that the editor can re-open the sketch window
   * in the same position as the user last left it.
   */
  static public final String EXTERNAL_MOVE = "__MOVE__";

  /** true if this sketch is being run by the PDE */
  boolean external = false;

  static final String ERROR_MIN_MAX =
    "Cannot use min() or max() on an empty array.";


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  protected PSurface surface;


  public PSurface getSurface() {
    return surface;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  boolean insideSettings;

  String renderer = JAVA2D;
  int smooth = 1;  // default smoothing (whatever that means for the renderer)

  boolean fullScreen;
  int display = -1;  // use default
  // Unlike the others above, needs to be public to support
  // the pixelWidth and pixelHeight fields.
  public int pixelDensity = 1;

  boolean present;

  String outputPath;
  OutputStream outputStream;

  // Background default needs to be different from the default value in
  // PGraphics.backgroundColor, otherwise sketches that have size(100, 100)
  // appear to be larger than they are, because the bg color matches.
  // https://github.com/processing/processing/issues/2297
  int windowColor = 0xffDDDDDD;


  /**
   * @param method "size" or "fullScreen"
   * @param args parameters passed to the function to show the user
   * @return true if safely inside the settings() method
   */
  boolean insideSettings(String method, Object... args) {
    if (insideSettings) {
      return true;
    }
    final String url = "https://processing.org/reference/" + method + "_.html";
    if (!external) {  // post a warning for users of Eclipse and other IDEs
      StringList argList = new StringList(args);
      System.err.println("When not using the PDE, " + method + "() can only be used inside settings().");
      System.err.println("Remove the " + method + "() method from setup(), and add the following:");
      System.err.println("public void settings() {");
      System.err.println("  " + method + "(" + argList.join(", ") + ");");
      System.err.println("}");
    }
    throw new IllegalStateException(method + "() cannot be used here, see " + url);
  }


  void handleSettings() {
    insideSettings = true;

    if (!disableAWT) {
      displayWidth = ShimAWT.getDisplayWidth();
      displayHeight = ShimAWT.getDisplayHeight();
    } else {
      // https://github.com/processing/processing4/issues/57
      System.err.println("AWT disabled, displayWidth/displayHeight will be 0");
    }

    // Here's where size(), fullScreen(), smooth(N) and noSmooth() might
    // be called, conjuring up the demons of various rendering configurations.
    settings();

    if (display == SPAN && platform == MACOS) {
      // Make sure "Displays have separate Spaces" is unchecked
      // in System Preferences > Mission Control
      Process p = exec("defaults", "read", "com.apple.spaces", "spans-displays");
      BufferedReader outReader = createReader(p.getInputStream());
      BufferedReader errReader = createReader(p.getErrorStream());
      StringBuilder stdout = new StringBuilder();
      StringBuilder stderr = new StringBuilder();
      String line;
      try {
        while ((line = outReader.readLine()) != null) {
          stdout.append(line);
        }
        while ((line = errReader.readLine()) != null) {
          stderr.append(line);
        }
      } catch (IOException e) {
        printStackTrace(e);
      }

      int resultCode = -1;
      try {
        resultCode = p.waitFor();
      } catch (InterruptedException ignored) { }

      if (resultCode == 1) {
        String msg = trim(stderr.toString());
        // This message is confusing, so don't print if it's something typical
        if (!(msg.contains("The domain/default pair") && msg.contains("does not exist"))) {
          System.err.println("Could not check the status of “Displays have separate spaces.”");
          System.err.println("Result for 'defaults read' was " + resultCode);
          System.err.println(msg);
        }
      }

      String processOutput = trim(stdout.toString());
      // On Catalina, the option may not be set, so resultCode
      // will be 1 (an error, since the param doesn't exist.)
      // But "Displays have separate spaces" is on by default.
      // For Monterey, it appears to not be set until the user
      // has visited the Mission Control preference pane once.
      if (resultCode == 1 || "0".equals(processOutput)) {
        System.err.println("To use fullScreen(SPAN), visit System Preferences → Mission Control");
        System.err.println("and make sure that “Displays have separate spaces” is turned off.");
        System.err.println("Then log out and log back in.");
      }
    }

    insideSettings = false;
  }


  /**
   * The <b>settings()</b> function is new with Processing 3.0.
   * It's not needed in most sketches. It's only useful when it's
   * absolutely necessary to define the parameters to <b>size()</b>
   * with a variable. Alternately, the <b>settings()</b> function
   * is necessary when using Processing code outside the
   * Processing Development Environment (PDE). For example, when
   * using the Eclipse code editor, it's necessary to use
   * <b>settings()</b> to define the <b>size()</b> and
   * <b>smooth()</b> values for a sketch.</b>.
   * <br /> <br />
   * The <b>settings()</b> method runs before the sketch has been
   * set up, so other Processing functions cannot be used at that
   * point. For instance, do not use loadImage() inside settings().
   * The settings() method runs "passively" to set a few variables,
   * compared to the <b>setup()</b> command that call commands in
   * the Processing API.
   *
   * @webref environment
   * @webBrief Used when absolutely necessary to define the parameters to <b>size()</b>
   * with a variable
   * @see PApplet#fullScreen()
   * @see PApplet#setup()
   * @see PApplet#size(int,int)
   * @see PApplet#smooth()
   */
  public void settings() {
    // is this necessary? (doesn't appear to be, so removing)
    //size(DEFAULT_WIDTH, DEFAULT_HEIGHT, JAVA2D);
  }


  final public int sketchWidth() {
    return width;
  }


  final public int sketchHeight() {
    return height;
  }


  final public String sketchRenderer() {
    return renderer;
  }


  // smoothing 1 is default.. 0 is none.. 2,4,8 depend on renderer
  final public int sketchSmooth() {
    return smooth;
  }


  final public boolean sketchFullScreen() {
    return fullScreen;
  }


  // Numbered from 1, SPAN (0) means all displays, -1 means the default display
  final public int sketchDisplay() {
    return display;
  }


  final public String sketchOutputPath() {
    return outputPath;
  }


  final public OutputStream sketchOutputStream() {
    return outputStream;
  }


  final public int sketchWindowColor() {
    return windowColor;
  }


  final public int sketchPixelDensity() {
    return pixelDensity;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

 /**
  *
  * This function returns the number "2" if the screen is a high-density
  * screen (called a Retina display on OS X or high-dpi on Windows and Linux)
  * and a "1" if not. This information is useful for a program to adapt to
  * run at double the pixel density on a screen that supports it.
  *
  * @webref environment
  * @webBrief Returns "2" if the screen is high-density and "1" if not
  * @see PApplet#pixelDensity(int)
  * @see PApplet#size(int,int)
  */
  public int displayDensity() {
    if (display != SPAN && (fullScreen || present)) {
      return displayDensity(display);
    }

    int displayCount = 0;
    if (!disableAWT) {
      displayCount = ShimAWT.getDisplayCount();
    } else {
      // https://github.com/processing/processing4/issues/57
      System.err.println("display count needs to be implemented for non-AWT");
    }
    // walk through all displays, use 2 if any display is 2
    for (int i = 0; i < displayCount; i++) {
      if (displayDensity(i+1) == 2) {
        return 2;
      }
    }
    // If nobody's density is 2 then everyone is 1
    return 1;
  }


 /**
  * @param display the display number to check
  * (1-indexed to match the Preferences dialog box)
  */
  public int displayDensity(int display) {
    if (!disableAWT) {
      return ShimAWT.getDisplayDensity(display);
    }
    /*
    if (display > 0 && display <= displayDevices.length) {
      GraphicsConfiguration graphicsConfig =
        displayDevices[display - 1].getDefaultConfiguration();
      AffineTransform tx = graphicsConfig.getDefaultTransform();
      return (int) Math.round(tx.getScaleX());
    }

    System.err.println("Display " + display + " does not exist, " +
                       "returning 1 for displayDensity(" + display + ")");
    */
    // https://github.com/processing/processing4/issues/57
    System.err.println("displayDensity() unavailable because AWT is disabled");
    return 1;  // not the end of the world, so don't throw a RuntimeException
  }


 /**
  * This function makes it possible to render using all the pixels
  * on high resolutions screens like Apple Retina and Windows HiDPI.
  * This function can only be run once within a program, and must
  * be called right after <b>size()</b> in a program without a
  * <b>setup()</b> function, or within <b>setup()</b> if present.
  * <p/>
  * <b>pixelDensity()</b> should only be used with hardcoded
  * numbers (in almost all cases this number will be 2)
  * or in combination with <b>displayDensity()</b> as in the
  * third example above.
  * <p/>
  * When the pixel density is set to more than 1, it changes  the
  * pixel operations including the way <b>get()</b>, <b>set()</b>,
  * <b>blend()</b>, <b>copy()</b>, and <b>updatePixels()</b>
  * all work. See the reference for <b>pixelWidth</b> and
  * pixelHeight for more information.
  * <p/>
  * To use variables as the arguments to <b>pixelDensity()</b>
  * function, place the <b>pixelDensity()</b> function within
  * the <b>settings()</b> function. There is more information
  * about this on the <b>settings()</b> reference page.
  *
  * @webref environment
  * @webBrief It makes it possible for Processing to render using all the
  * pixels on high resolutions screens
  * @param density 1 or 2
  * @see PApplet#pixelWidth
  * @see PApplet#pixelHeight
  */
  public void pixelDensity(int density) {
    //println(density + " " + this.pixelDensity);
    if (density != this.pixelDensity) {
      if (insideSettings("pixelDensity", density)) {
        if (density != 1 && density != 2) {
          throw new RuntimeException("pixelDensity() can only be 1 or 2");
        }
        if (!FX2D.equals(renderer) && density == 2 && displayDensity() == 1) {
          // FX has its own check in PSurfaceFX
          // Don't throw exception because the sketch should still work
          System.err.println("pixelDensity(2) is not available for this display");
          this.pixelDensity = 1;
        } else {
          this.pixelDensity = density;
        }
      } else {
        System.err.println("not inside settings");
        // this should only be reachable when not running in the PDE,
        // so saying it's a settings()--not just setup()--issue should be ok
        throw new RuntimeException("pixelDensity() can only be used inside settings()");
      }
    }
  }


  /**
   * Called by PSurface objects to set the width and height variables,
   * and update the pixelWidth and pixelHeight variables.
   */
  public void setSize(int width, int height) {
    this.width = width;
    this.height = height;
    pixelWidth = width * pixelDensity;
    pixelHeight = height * pixelDensity;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * @nowebref
   */
  public void smooth() {
    smooth(1);
  }

  /**
   * Draws all geometry with smooth (anti-aliased) edges.
   * This behavior is the default, so <b>smooth()</b> only needs
   * to be used when a program needs to set the smoothing
   * in a different way. The level parameter increases
   * the amount of smoothness. This is the level of over
   * sampling applied to the graphics buffer.
   * <p/>
   * With the P2D and P3D renderers, <b>smooth(2)</b> is the
   * default, this is called "2x anti-aliasing." The code
   * <b>smooth(4)</b> is used for 4x anti-aliasing and <b>smooth(8)</b>
   * is specified for "8x anti-aliasing." The maximum
   * anti-aliasing level is determined by the hardware of
   * the machine that is running the software, so <b>smooth(4)</b>
   * and <b>smooth(8)</b> will not work with every computer.
   * <p/>
   * The default renderer uses <b>smooth(3)</b> by default. This
   * is bicubic smoothing. The other option for the default
   * renderer is <b>smooth(2)</b>, which is bilinear smoothing.
   * <p/>
   * With Processing 3.0, <b>smooth()</b> is handled differently
   * than in earlier releases. In 2.x and earlier, it was possible
   * to use <b>smooth()</b> and <b>noSmooth()</b> to turn on
   * and off antialiasing within a sketch. Now, because of
   * how the software has changed, <b>smooth()</b> can only be set
   * once within a sketch. It can be used either at the top
   * of a sketch without a <b>setup()</b>, or after the <b>size()</b>
   * function when used in a sketch with <b>setup()</b>. The
   * <b>noSmooth()</b> function also follows the same rules.
   * <p/>
   * When <b>smooth()</b> is used with a PGraphics object, it should
   * be run right after the object is created with
   * <b>createGraphics()</b>, as shown in the Reference in the third
   * example.
   *
   * @webref environment
   * @webBrief Draws all geometry with smooth (anti-aliased) edges
   * @param level either 2, 3, 4, or 8 depending on the renderer
   */
  public void smooth(int level) {
    if (insideSettings) {
      this.smooth = level;

    } else if (this.smooth != level) {
      smoothWarning("smooth");
    }
  }

  /**
   * Draws all geometry and fonts with jagged (aliased)
   * edges and images with hard edges between the pixels
   * when enlarged rather than interpolating pixels. Note
   * that <b>smooth()</b> is active by default, so it is necessary
   * to call <b>noSmooth()</b> to disable smoothing of geometry,
   * fonts, and images. Since the release of Processing 3.0,
   * the <b>noSmooth()</b> function can only be run once for each
   * sketch, either at the top of a sketch without a <b>setup()</b>,
   * or after the <b>size()</b> function when used in a sketch with
   * <b>setup()</b>. See the examples above for both scenarios.
   *
   * @webref environment
   * @webBrief Draws all geometry and fonts with jagged (aliased)
   * edges and images with hard edges between the pixels
   * when enlarged rather than interpolating pixels
   */
  public void noSmooth() {
    if (insideSettings) {
      this.smooth = 0;

    } else if (this.smooth != 0) {
      smoothWarning("noSmooth");
    }
  }


  private void smoothWarning(String method) {
    // When running from the PDE, say setup(), otherwise say settings()
    final String where = external ? "setup" : "settings";
    PGraphics.showWarning("%s() can only be used inside %s()", method, where);
    if (external) {
      PGraphics.showWarning("When run from the PDE, %s() is automatically moved from setup() to settings()", method);
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public PGraphics getGraphics() {
    return g;
  }


  // TODO should this join the sketchXxxx() functions specific to settings()?
  public void orientation(int which) {
    // ignore calls to the orientation command
  }


  /**
   * Called by the application context to start running (or resume) the sketch.
   */
  public void start() {
    resume();
    handleMethods("resume");
    surface.resumeThread();
  }


  /**
   * Called by the application context to stop running (or pause) the sketch.
   */
  public void stop() {
    pause();
    handleMethods("pause");
    // calling this down here, since it's another thread it's safer to call
    // pause() and the registered pause methods first.
    surface.pauseThread();
  }


  /**
   * Sketch has been paused. Called when switching tabs in a browser or
   * swapping to a different application on Android. Also called just before
   * quitting. Use to safely disable things like serial, sound, or sensors.
   */
  public void pause() { }


  /**
   * Sketch has resumed. Called when switching tabs in a browser or
   * swapping to this application on Android. Also called on startup.
   * Use this to safely disable things like serial, sound, or sensors.
   */
  public void resume() { }


  //////////////////////////////////////////////////////////////


  /** Map of registered methods, stored by method name. */
  Map<String, RegisteredMethods> registerMap = new ConcurrentHashMap<>();


  class RegisteredMethods {
    /**
     * List of the objects for which the method is registered.
     * This is an ordered collection because the order of calls
     * likely matters, or at a minimum, needs to be stable.
     */
    Queue<Object> entries = new ConcurrentLinkedQueue<>();

    /**
     * A reference to the Method inside each Object, stored so that we're
     * not redoing the same reflection call inside a tight loop like draw().
     */
    Map<Object, Method> methods = new ConcurrentHashMap<>();

    /** While handle() is being called, store any removals in this Set. */
    Set<Object> removals = null;

    /** Create and store this once. */
    final Object[] emptyArgs = new Object[] { };

    @SuppressWarnings("unused")
    void handle() {
      handle(emptyArgs);
    }

    void handle(Object[] args) {
      // Queue removed entries until done iterating, i.e. so the Video Library
      // can call unregisterMethod("dispose") from inside its dispose() method
      // https://github.com/processing/processing4/pull/199
      removals = ConcurrentHashMap.newKeySet();

      for (Object entry : entries) {
        try {
          //methods[i].invoke(objects[i], args);
          //entry.method.invoke(entry.object, args);
          methods.get(entry).invoke(entry, args);
        } catch (Exception e) {
          // check for wrapped exception, get root exception
          Throwable t;
          if (e instanceof InvocationTargetException ite) {
            t = ite.getCause();
          } else {
            t = e;
          }
          // check for RuntimeException, and allow it to bubble up
          if (t instanceof RuntimeException) {
            // re-throw exception
            throw (RuntimeException) t;
          } else {
            // trap and print as usual
            printStackTrace(t);
          }
        }
      }
      // Clear the entries queued for removal (if any)
      for (Object object : removals) {
        entries.remove(object);
        methods.remove(object);
      }
      removals = null;  // clear this out
    }


    void add(Object object, Method method) {
      if (!entries.contains(object)) {
        entries.add(object);
        methods.put(object, method);
      } else {
        die(method.getName() + "() already added for this instance of " +
            object.getClass().getName());
      }
    }


    /**
     * Removes first object/method pair matched (and only the first,
     * must be called multiple times if object is registered multiple times).
     */
    public void remove(Object object) {
      if (removals == null) {
        // If the removals list is null, that means we're not currently iterating
        // the entries, so it's safe to remove the entry immediately.
        entries.remove(object);
        methods.remove(object);
      } else {
        // Iterates the list of methods, remove this afterwards
        removals.add(object);
      }
    }
  }


  /**
   * Register a built-in event so that it can be fired for libraries, etc.
   * Supported events include:
   * <ul>
   * <li>pre – at the very top of the draw() method (safe to draw)
   * <li>draw – at the end of the draw() method (safe to draw)
   * <li>post – after draw() has exited (not safe to draw)
   * <li>pause – called when the sketch is paused
   * <li>resume – called when the sketch is resumed
   * <li>dispose – when the sketch is shutting down (definitely not safe to draw)
   * <ul>
   * In addition, the new (for 2.0) <tt>processing.event</tt> classes are passed to
   * the following event types:
   * <ul>
   * <li>mouseEvent
   * <li>keyEvent
   * <li>touchEvent
   * </ul>
   * The older java.awt events are no longer supported.
   * See the Library Wiki page for more details.
   * @param methodName name of the method to be called
   * @param target the target object that should receive the event
   */
  public void registerMethod(String methodName, Object target) {
    switch (methodName) {
      case "mouseEvent" -> registerWithArgs("mouseEvent", target, new Class[] { MouseEvent.class });
      case "keyEvent" -> registerWithArgs("keyEvent", target, new Class[] { KeyEvent.class });
      case "touchEvent" -> registerWithArgs("touchEvent", target, new Class[] { TouchEvent.class });
      default -> registerNoArgs(methodName, target);
    }
  }


  private void registerNoArgs(String name, Object o) {
    Class<?> c = o.getClass();
    try {
      Method method = c.getMethod(name);
      RegisteredMethods meth = registerMap.get(name);
      if (meth == null) {
        meth = new RegisteredMethods();
        registerMap.put(name, meth);
      }
      meth.add(o, method);
    } catch (NoSuchMethodException nsme) {
      die("There is no public " + name + "() method in the class " +
          o.getClass().getName());

    } catch (Exception e) {
      die("Could not register " + name + " + () for " + o, e);
    }
  }


  private void registerWithArgs(String name, Object o, Class<?>[] cargs) {
    Class<?> c = o.getClass();
    try {
      Method method = c.getMethod(name, cargs);
      RegisteredMethods meth = registerMap.get(name);
      if (meth == null) {
        meth = new RegisteredMethods();
        registerMap.put(name, meth);
      }
      meth.add(o, method);
    } catch (NoSuchMethodException nsme) {
      die("There is no public " + name + "() method in the class " +
          o.getClass().getName());

    } catch (Exception e) {
      die("Could not register " + name + " + () for " + o, e);
    }
  }


  public void unregisterMethod(String name, Object target) {
    RegisteredMethods meth = registerMap.get(name);
    if (meth == null) {
      die("No registered methods with the name " + name + "() were found.");

    } else {
      try {
        meth.remove(target);
      } catch (Exception e) {
        die("Could not unregister " + name + "() for " + target, e);
      }
    }
  }

  protected void handleMethods(String methodName, Object...args) {
    RegisteredMethods meth = registerMap.get(methodName);
    if (meth != null) {
      meth.handle(args);
    }
  }



  //////////////////////////////////////////////////////////////

/**
   *
   * The <b>setup()</b> function is run once, when the program starts. It's used
   * to define initial environment properties such as screen size and to load media
   * such as images and fonts as the program starts. There can only be one
   * <b>setup()</b> function for each program, and it shouldn't be called again
   * after its initial execution.<br />
   * <br />
   * If the sketch is a different dimension than the default, the <b>size()</b>
   * function or <b>fullScreen()</b> function must be the first line in
   * <b>setup()</b>.<br />
   * <br />
   * Note: Variables declared within <b>setup()</b> are not accessible within
   * other functions, including <b>draw()</b>.
   *
 * @webref structure
 * @webBrief  The <b>setup()</b> function is called once when the program starts
 * @usage web_application
 * @see PApplet#size(int, int)
 * @see PApplet#loop()
 * @see PApplet#noLoop()
 * @see PApplet#draw()
 */
  public void setup() {
  }

/**
   *
   * Called directly after <b>setup()</b>, the <b>draw()</b> function continuously
   * executes the lines of code contained inside its block until the program is
   * stopped or <b>noLoop()</b> is called. <b>draw()</b> is called automatically
   * and should never be called explicitly. All Processing programs update the
   * screen at the end of draw(), never earlier.<br />
   * <br />
   * To stop the code inside of <b>draw()</b> from running continuously, use
   * <b>noLoop()</b>, <b>redraw()</b> and <b>loop()</b>. If <b>noLoop()</b> is
   * used to stop the code in <b>draw()</b> from running, then <b>redraw()</b>
   * will cause the code inside <b>draw()</b> to run a single time, and
   * <b>loop()</b> will cause the code inside <b>draw()</b> to resume running
   * continuously.<br />
   * <br />
   * The number of times <b>draw()</b> executes in each second may be controlled
   * with the <b>frameRate()</b> function.<br />
   * <br />
   * It is common to call <b>background()</b> near the beginning of the
   * <b>draw()</b> loop to clear the contents of the window, as shown in the first
   * example above.  Since pixels drawn to the window are cumulative, omitting
   * <b>background()</b> may result in unintended results.<br />
   * <br />
   * There can only be one <b>draw()</b> function for each sketch, and <b>draw()</b>
   * must exist if you want the code to run continuously, or to process events such
   * as <b>mousePressed()</b>. Sometimes, you might have an empty call to
   * <b>draw()</b> in your program, as shown in the second example above.
   *
 * @webref structure
 * @webBrief Called directly after <b>setup()</b> and continuously executes the lines
 * of code contained inside its block until the program is stopped or
 * <b>noLoop()</b> is called
 * @usage web_application
 * @see PApplet#setup()
 * @see PApplet#loop()
 * @see PApplet#noLoop()
 * @see PApplet#redraw()
 * @see PApplet#frameRate(float)
 * @see PGraphics#background(float, float, float, float)
 */
  public void draw() {
    // if no draw method, then shut things down
    //System.out.println("no draw method, goodbye");
    finished = true;
  }


  //////////////////////////////////////////////////////////////


  /*
  protected void resizeRenderer(int newWidth, int newHeight) {
    debug("resizeRenderer request for " + newWidth + " " + newHeight);
    if (width != newWidth || height != newHeight) {
      debug("  former size was " + width + " " + height);
      g.setSize(newWidth, newHeight);
      width = newWidth;
      height = newHeight;
    }
  }
  */


  /**
   * Create a full-screen sketch using the default renderer.
   */
  public void fullScreen() {
    if (!fullScreen) {
      if (insideSettings("fullScreen")) {
        this.fullScreen = true;
      }
    }
  }


  public void fullScreen(int display) {
    if (!fullScreen || display != this.display) {
      if (insideSettings("fullScreen", display)) {
        this.fullScreen = true;
        this.display = display;
      }
    }
  }


/**
  * This function is new for Processing 3.0. It opens a sketch using the full
  * size of the computer's display. This function must be the first line in
  * <b>setup()</b>. The <b>size()</b> and <b>fullScreen()</b> functions cannot
  * both be used in the same program, just choose one.<br />
  * <br />
  * When <b>fullScreen()</b> is used without a parameter, it draws the sketch
  * to the screen currently selected inside the Preferences window. When it is
  * used with a single parameter, this number defines the screen to display to
  * program on (e.g. 1, 2, 3...). When used with two parameters, the first
  * defines the renderer to use (e.g. P2D) and the second defines the screen.
  * The <b>SPAN</b> parameter can be used in place of a screen number to draw
  * the sketch as a full-screen window across all the attached displays if
  * there are more than one.<br />
  * <br />
  * Prior to Processing 3.0, a full-screen program was defined with
  * <b>size(displayWidth, displayHeight)</b>.
  *
  * @webref environment
  * @webBrief Opens a sketch using the full size of the computer's display
  * @param renderer the renderer to use, e.g. P2D, P3D, JAVA2D (default)
  * @see PApplet#settings()
  * @see PApplet#setup()
  * @see PApplet#size(int,int)
  * @see PApplet#smooth()
  */
  public void fullScreen(String renderer) {
    if (!fullScreen ||
        !renderer.equals(this.renderer)) {
      if (insideSettings("fullScreen", renderer)) {
        this.fullScreen = true;
        this.renderer = renderer;
      }
    }
  }


  /**
   * @param display the screen to run the sketch on (1, 2, 3, etc. or on multiple screens using SPAN)
   */

  public void fullScreen(String renderer, int display) {
    if (!fullScreen ||
        !renderer.equals(this.renderer) ||
        display != this.display) {
      if (insideSettings("fullScreen", renderer, display)) {
        this.fullScreen = true;
        this.renderer = renderer;
        this.display = display;
      }
    }
  }


  /**
   * Defines the dimension of the display window width and height in units of
   * pixels. In a program that has the <b>setup()</b> function, the
   * <b>size()</b> function must be the first line of code inside
   * <b>setup()</b>, and the <b>setup()</b> function must appear in the code tab
   * with the same name as your sketch folder.<br />
   * <br />
   * The built-in variables <b>width</b> and <b>height</b> are set by the
   * parameters passed to this function. For example, running <b>size(640,
   * 480)</b> will assign 640 to the <b>width</b> variable and 480 to the height
   * <b>variable</b>. If <b>size()</b> is not used, the window will be given a
   * default size of 100 x 100 pixels.<br />
   * <br />
   * The <b>size()</b> function can only be used once inside a sketch, and it
   * cannot be used for resizing. Use <b>windowResize()</b> instead.<br />
   * <br />
   * To run a sketch that fills the screen, use the <b>fullScreen()</b> function,
   * rather than using <b>size(displayWidth, displayHeight)</b>.<br />
   * <br />
   * The <b>renderer</b> parameter selects which rendering engine to use. For
   * example, if you will be drawing 3D shapes, use <b>P3D</b>. The default
   * renderer is slower for some situations (for instance large or
   * high-resolution displays) but generally has higher quality than the
   * other renderers for 2D drawing. <br />
   * <br />
   * In addition to the default renderer, other renderers are:<br />
   * <br />
   * <b>P2D</b> (Processing 2D): 2D graphics renderer that makes use of
   * OpenGL-compatible graphics hardware.<br />
   * <br />
   * <b>P3D</b> (Processing 3D): 3D graphics renderer that makes use of
   * OpenGL-compatible graphics hardware.<br />
   * <br />
   * <b>FX2D</b> (JavaFX 2D): A 2D renderer that uses JavaFX, which may be
   * faster for some applications, but has some compatibility quirks.
   * Use “Manage Libraries” to download and install the JavaFX library.<br />
   * <br />
   * <b>PDF</b>: The PDF renderer draws 2D graphics directly to an Acrobat PDF
   * file. This produces excellent results when you need vector shapes for
   * high-resolution output or printing. You must first use Import Library
   * &rarr; PDF to make use of the library. More information can be found in the
   * PDF library reference.<br />
   * <br />
   * <b>SVG</b>: The SVG renderer draws 2D graphics directly to an SVG file.
   * This is great for importing into other vector programs or using for
   * digital fabrication. It is not as feature-complete as other renderers.
   * Like PDF, you must first use Import Library &rarr; SVG Export to
   * make use the SVG library.<br />
   * <br />
   * As of Processing 3.0, to use variables as the parameters to <b>size()</b>
   * function, place the <b>size()</b> function within the <b>settings()</b>
   * function (instead of <b>setup()</b>). There is more information about this
   * on the <b>settings()</b> reference page.<br />
   * <br />
   * The maximum width and height is limited by your operating system, and is
   * usually the width and height of your actual screen. On some machines it may
   * simply be the number of pixels on your current screen, meaning that a
   * screen of 800 x 600 could support <b>size(1600, 300)</b>, since that is the
   * same number of pixels. This varies widely, so you'll have to try different
   * rendering modes and sizes until you get what you're looking for. If you
   * need something larger, use <b>createGraphics</b> to create a non-visible
   * drawing surface.<br />
   * <br />
   * The minimum width and height is around 100 pixels in each direction. This
   * is the smallest that is supported across Windows, macOS, and Linux. We
   * enforce the minimum size so that sketches will run identically on different
   * machines. <br />
   * <br />
   *
   * @webref environment
   * @webBrief Defines the dimension of the display window in units of pixels
   * @param width
   *          width of the display window in units of pixels
   * @param height
   *          height of the display window in units of pixels
   * @see PApplet#width
   * @see PApplet#height
   * @see PApplet#setup()
   * @see PApplet#settings()
   * @see PApplet#fullScreen()
   */
  public void size(int width, int height) {
    // Check to make sure the width/height have actually changed. It's ok to
    // have size() duplicated (and may be better to not remove it from where
    // it sits in the code anyway when adding it to settings()). Only take
    // action if things have changed.
    if (width != this.width ||
        height != this.height) {
      if (insideSettings("size", width, height)) {
        this.width = width;
        this.height = height;
      }
    }
  }


  public void size(int width, int height, String renderer) {
    if (width != this.width ||
        height != this.height ||
        !renderer.equals(this.renderer)) {
      //println(width, height, renderer, this.width, this.height, this.renderer);
      if (insideSettings("size", width, height, "\"" + renderer + "\"")) {
        this.width = width;
        this.height = height;
        this.renderer = renderer;
      }
    }
  }


  /**
   * @nowebref
   */
  public void size(int width, int height, String renderer, String path) {
    // Don't bother checking path, it's probably been modified to absolute,
    // so it would always trigger. But the alternative is comparing the
    // canonical file, which seems overboard.
    if (width != this.width ||
        height != this.height ||
        !renderer.equals(this.renderer)) {
      if (insideSettings("size", width, height, "\"" + renderer + "\"",
                         "\"" + path + "\"")) {
        this.width = width;
        this.height = height;
        this.renderer = renderer;
        this.outputPath = path;
      }
    }
  }


  public PGraphics createGraphics(int w, int h) {
    return createGraphics(w, h, JAVA2D);
  }


  /**
   *
   * Creates and returns a new <b>PGraphics</b> object. Use this class if you
   * need to draw into an offscreen graphics buffer. The first two parameters
   * define the width and height in pixels. The third, optional parameter
   * specifies the renderer. It can be defined as P2D, P3D, PDF, or SVG. If the
   * third parameter isn't used, the default renderer is set. The PDF and SVG
   * renderers require the filename parameter.<br />
   * <br />
   * It's important to consider the renderer used with <b>createGraphics()</b>
   * in relation to the main renderer specified in <b>size()</b>. For example,
   * it's only possible to use P2D or P3D with <b>createGraphics()</b> when one
   * of them is defined in <b>size()</b>. Unlike Processing 1.0, P2D and P3D use
   * OpenGL for drawing, and when using an OpenGL renderer it's necessary for
   * the main drawing surface to be OpenGL-based. If P2D or P3D are used as the
   * renderer in <b>size()</b>, then any of the options can be used with
   * <b>createGraphics()</b>. If the default renderer is used in <b>size()</b>,
   * then only the default, PDF, or SVG can be used with
   * <b>createGraphics()</b>.<br />
   * <br />
   * It's important to run all drawing functions between the <b>beginDraw()</b>
   * and <b>endDraw()</b>. As the exception to this rule, <b>smooth()</b> should
   * be run on the PGraphics object before <b>beginDraw()</b>. See the reference
   * for <b>smooth()</b> for more detail.<br />
   * <br />
   * The <b>createGraphics()</b> function should almost never be used inside
   * <b>draw()</b> because of the memory and time needed to set up the graphics.
   * One-time or occasional use during <b>draw()</b> might be acceptable, but
   * code that calls <b>createGraphics()</b> at 60 frames per second might run
   * out of memory or freeze your sketch.<br />
   * <br />
   * Unlike the main drawing surface which is completely opaque, surfaces
   * created with <b>createGraphics()</b> can have transparency. This makes it
   * possible to draw into a graphics and maintain the alpha channel. By using
   * <b>save()</b> to write a PNG or TGA file, the transparency of the graphics
   * object will be honored.
   *
   * <h3>Advanced</h3> Create an offscreen PGraphics object for drawing. This
   * can be used for bitmap or vector images drawing or rendering.
   * <UL>
   * <LI>Do not use "new PGraphicsXxxx()", use this method. This method ensures
   * that internal variables are set up properly that tie the new graphics
   * context back to its parent PApplet.
   * <LI>The basic way to create bitmap images is to use the
   * <A HREF="http://processing.org/reference/saveFrame_.html">saveFrame()</A>
   * function.
   * <LI>If you want to create a really large scene and write that, first make
   * sure that you've allocated a lot of memory in the Preferences.
   * <LI>If you want to create images that are larger than the screen, you
   * should create your own PGraphics object, draw to that, and use
   * <A HREF="http://processing.org/reference/save_.html">save()</A>.
   *
   * <PRE>
   *
   * PGraphics big;
   *
   * void setup() {
   *   big = createGraphics(3000, 3000);
   *
   *   big.beginDraw();
   *   big.background(128);
   *   big.line(20, 1800, 1800, 900);
   *   // etc..
   *   big.endDraw();
   *
   *   // make sure the file is written to the sketch folder
   *   big.save("big.tif");
   * }
   *
   * </PRE>
   *
   * <LI>It's important to always wrap drawing to createGraphics() with
   * beginDraw() and endDraw() (beginFrame() and endFrame() prior to revision
   * 0115). The reason is that the renderer needs to know when drawing has
   * stopped, so that it can update itself internally. This also handles calling
   * the defaults() method, for people familiar with that.
   * <LI>With Processing 0115 and later, it's possible to write images in
   * formats other than the default .tga and .tiff. The exact formats and
   * background information can be found in the developer's reference for
   * <A HREF=
   * "http://dev.processing.org/reference/core/javadoc/processing/core/PImage.html#save(java.lang.String)">PImage.save()</A>.
   * </UL>
   *
   * @webref rendering
   * @webBrief Creates and returns a new <b>PGraphics</b> object of the types
   *           P2D or P3D
   * @param w
   *          width in pixels
   * @param h
   *          height in pixels
   * @param renderer
   *          Either P2D, P3D, or PDF
   * @see PGraphics#PGraphics
   *
   */
  public PGraphics createGraphics(int w, int h, String renderer) {
    return createGraphics(w, h, renderer, null);
  }


  /**
   * Create an offscreen graphics surface for drawing, in this case
   * for a renderer that writes to a file (such as PDF or DXF).
   * @param path the name of the file (can be an absolute or relative path)
   */
  public PGraphics createGraphics(int w, int h,
                                  String renderer, String path) {
    return makeGraphics(w, h, renderer, path, false);
  }


  /**
   * Version of createGraphics() used internally.
   * @param path A path (or null if none), can be absolute or relative ({@link PApplet#savePath} will be called)
   */
  protected PGraphics makeGraphics(int w, int h,
                                   String renderer, String path,
                                   boolean primary) {
    if (!primary && !g.isGL()) {
      if (renderer.equals(P2D)) {
        throw new RuntimeException("createGraphics() with P2D requires size() to use P2D or P3D");
      } else if (renderer.equals(P3D)) {
        throw new RuntimeException("createGraphics() with P3D or OPENGL requires size() to use P2D or P3D");
      }
    }

    try {
      Class<?> rendererClass =
        Thread.currentThread().getContextClassLoader().loadClass(renderer);

      Constructor<?> constructor = rendererClass.getConstructor();
      PGraphics pg = (PGraphics) constructor.newInstance();

      pg.setParent(this);
      pg.setPrimary(primary);
      if (path != null) {
        pg.setPath(savePath(path));
      }
//      pg.setQuality(sketchQuality());
//      if (!primary) {
//        surface.initImage(pg, w, h);
//      }
      pg.setSize(w, h);

      // everything worked, return it
      return pg;

    } catch (InvocationTargetException ite) {
      String msg = ite.getTargetException().getMessage();
      if ((msg != null) &&
          (msg.contains("no jogl in java.library.path"))) {
        // Is this true anymore, since the JARs contain the native libs?
        throw new RuntimeException("The jogl library folder needs to be " +
          "specified with -Djava.library.path=/path/to/jogl");

      } else {
        printStackTrace(ite.getTargetException());
        Throwable target = ite.getTargetException();
        /*
        // removing for 3.2, we'll see
        if (platform == MACOSX) {
          target.printStackTrace(System.out);  // OS X bug (still true?)
        }
        */
        throw new RuntimeException(target.getMessage());
      }

    } catch (ClassNotFoundException cnfe) {
      // Clarify the error message for less confusion on 4.x
      if (renderer.equals(FX2D)) {
        renderer = "JavaFX";
      }
      if (external) {
        throw new RuntimeException("Please use Sketch → Import Library " +
                                   "to add " + renderer + " to your sketch.");
      } else {
        throw new RuntimeException("The " + renderer +
                                   " renderer is not in the class path.");
      }

    } catch (Exception e) {
      if ((e instanceof IllegalArgumentException) ||
          (e instanceof NoSuchMethodException) ||
          (e instanceof IllegalAccessException)) {
        if (e.getMessage().contains("cannot be <= 0")) {
          // IllegalArgumentException will be thrown if w/h is <= 0
          // https://github.com/processing/processing/issues/1021
          throw new RuntimeException(e);

        } else {
          printStackTrace(e);
          String msg = renderer + " needs to be updated " +
            "for the current release of Processing.";
          throw new RuntimeException(msg);
        }
      } else {
        /*
        if (platform == MACOSX) {
          e.printStackTrace(System.out);  // OS X bug (still true?)
        }
        */
        printStackTrace(e);
        throw new RuntimeException(e.getMessage());
      }
    }
  }


  /** Create default renderer, likely to be resized, but needed for surface init. */
  protected PGraphics createPrimaryGraphics() {
    return makeGraphics(sketchWidth(), sketchHeight(),
                        sketchRenderer(), sketchOutputPath(), true);
  }


  /**
   *
   * Creates a new PImage (the datatype for storing images). This provides a
   * fresh buffer of pixels to play with. Set the size of the buffer with the
   * <b>width</b> and <b>height</b> parameters. The <b>format</b> parameter
   * defines how the pixels are stored. See the PImage reference for more information.
   * <br/> <br/>
   * Be sure to include all three parameters, specifying only the width and
   * height (but no format) will produce a strange error.
   * <br/> <br/>
   * Advanced users please note that createImage() should be used instead of
   * the syntax <tt>new PImage()</tt>.
   *
   * <h3>Advanced</h3>
   * Preferred method of creating new PImage objects, ensures that a
   * reference to the parent PApplet is included, which makes save() work
   * without needing an absolute path.
   *
   * @webref image
   * @webBrief Creates a new <b>PImage</b> (the datatype for storing images)
   * @param w width in pixels
   * @param h height in pixels
   * @param format Either RGB, ARGB, ALPHA (grayscale alpha channel)
   * @see PImage
   * @see PGraphics
   */
  public PImage createImage(int w, int h, int format) {
    PImage image = new PImage(w, h, format);
    image.parent = this;  // make save() work
    return image;
  }


  //////////////////////////////////////////////////////////////


  protected boolean insideDraw;

  /** Last time in nanoseconds that frameRate was checked */
  protected long frameRateLastNanos = 0;


  public void handleDraw() {
    if (g == null) return;
    if (!looping && !redraw) return;

    if (insideDraw) {
      System.err.println("handleDraw() called before finishing");
      System.exit(1);
    }

    insideDraw = true;
    g.beginDraw();
    if (recorder != null) {
      recorder.beginDraw();
    }

    // apply window ratio if set
    if (windowRatio) {
      float aspectH = width / (float) rwidth;
      float aspectV = height / (float) rheight;
      ratioScale = min(aspectH, aspectV);
      ratioTop = (height - ratioScale* rheight) / 2;
      ratioLeft = (width - ratioScale* rwidth) / 2;
      translate(ratioLeft, ratioTop);
      scale(ratioScale);
    }

    long now = System.nanoTime();

    if (frameCount == 0) {
      setup();

    } else {  // frameCount > 0, meaning an actual draw()
      // update the current frameRate

      // Calculate frameRate through average frame times, not average fps, e.g.:
      //
      // Alternating 2 ms and 20 ms frames (JavaFX or JOGL sometimes does this)
      // is around 90.91 fps (two frames in 22 ms, one frame 11 ms).
      //
      // However, averaging fps gives us: (500 fps + 50 fps) / 2 = 275 fps.
      // This is because we had 500 fps for 2 ms and 50 fps for 20 ms, but we
      // counted them with equal weight.
      //
      // If we average frame times instead, we get the right result:
      // (2 ms + 20 ms) / 2 = 11 ms per frame, which is 1000/11 = 90.91 fps.
      //
      // The counter below uses exponential moving average. To do the
      // calculation, we first convert the accumulated frame rate to average
      // frame time, then calculate the exponential moving average, and then
      // convert the average frame time back to frame rate.
      {
        // Get the frame time of the last frame
        double frameTimeSecs = (now - frameRateLastNanos) / 1e9;
        // Convert average frames per second to average frame time
        double avgFrameTimeSecs = 1.0 / frameRate;
        // Calculate exponential moving average of frame time
        final double alpha = 0.05;
        avgFrameTimeSecs = (1.0 - alpha) * avgFrameTimeSecs + alpha * frameTimeSecs;
        // Convert frame time back to frames per second
        frameRate = (float) (1.0 / avgFrameTimeSecs);
      }

      // post move and resize events to the sketch here
      dequeueWindowEvents();

      handleMethods("pre");

      // use dmouseX/Y as previous mouse pos, since this is the
      // last position the mouse was in during the previous draw.
      pmouseX = dmouseX;
      pmouseY = dmouseY;

      draw();

      // dmouseX/Y is updated only once per frame (unlike emouseX/Y)
      dmouseX = mouseX;
      dmouseY = mouseY;

      // these are called *after* loop so that valid
      // drawing commands can be run inside them. it can't
      // be before, since a call to background() would wipe
      // out anything that had been drawn so far.
      dequeueEvents();

      handleMethods("draw");

      redraw = false;  // unset 'redraw' flag in case it was set
      // (only do this once draw() has run, not just setup())
    }
    g.endDraw();

    if (recorder != null) {
      recorder.endDraw();
    }
    insideDraw = false;

    if (frameCount != 0) {
      handleMethods("post");
    }

    frameRateLastNanos = now;
    frameCount++;
  }


//  /** Not official API, not guaranteed to work in the future. */
//  public boolean canDraw() {
//    return g != null && (looping || redraw);
//  }


  //////////////////////////////////////////////////////////////


/**
 *
 * Executes the code within <b>draw()</b> one time. This functions allows the
 * program to update the display window only when necessary, for example when an
 * event registered by <b>mousePressed()</b> or <b>keyPressed()</b> occurs.
 * <br/>
 * <br/>
 * In structuring a program, it only makes sense to call redraw() within events
 * such as <b>mousePressed()</b>. This is because <b>redraw()</b> does not run
 * <b>draw()</b> immediately (it only sets a flag that indicates an update is
 * needed). <br/>
 * <br/>
 * The <b>redraw()</b> function does not work properly when called inside
 * <b>draw()</b>. To enable/disable animations, use <b>loop()</b> and
 * <b>noLoop()</b>.
 *
 * @webref structure
 * @webBrief Executes the code within <b>draw()</b> one time
 * @usage web_application
 * @see PApplet#draw()
 * @see PApplet#loop()
 * @see PApplet#noLoop()
 * @see PApplet#frameRate(float)
 */
  synchronized public void redraw() {
    if (!looping) {
      redraw = true;
//      if (thread != null) {
//        // wake from sleep (necessary otherwise it'll be
//        // up to 10 seconds before update)
//        if (CRUSTY_THREADS) {
//          thread.interrupt();
//        } else {
//          synchronized (blocker) {
//            blocker.notifyAll();
//          }
//        }
//      }
    }
  }

/**
 *
 * By default, Processing loops through <b>draw()</b> continuously, executing
 * the code within it. However, the <b>draw()</b> loop may be stopped by calling
 * <b>noLoop()</b>. In that case, the <b>draw()</b> loop can be resumed with
 * <b>loop()</b>.
 *
 * @webref structure
 * @webBrief Causes Processing to continuously execute the code within
 *           <b>draw()</b>
 * @usage web_application
 * @see PApplet#noLoop()
 * @see PApplet#redraw()
 * @see PApplet#draw()
 */
  synchronized public void loop() {
    if (!looping) {
      looping = true;
    }
  }

/**
   *
   * Stops Processing from continuously executing the code within
   * <b>draw()</b>. If <b>loop()</b> is called, the code in <b>draw()</b>
   * begin to run continuously again. If using <b>noLoop()</b> in
   * <b>setup()</b>, it should be the last line inside the block.
   * <br/> <br/>
   * When <b>noLoop()</b> is used, it's not possible to manipulate or access
   * the screen inside event handling functions such as <b>mousePressed()</b>
   * or <b>keyPressed()</b>. Instead, use those functions to call
   * <b>redraw()</b> or <b>loop()</b>, which will run <b>draw()</b>, which
   * can update the screen properly. This means that when noLoop() has been
   * called, no drawing can happen, and functions like saveFrame() or
   * loadPixels() may not be used.
   * <br/> <br/>
   * Note that if the sketch is resized, <b>redraw()</b> will be called to
   * update the sketch, even after <b>noLoop()</b> has been specified.
   * Otherwise, the sketch would enter an odd state until <b>loop()</b> was called.
   *
 * @webref structure
 * @webBrief Stops Processing from continuously executing the code within <b>draw()</b>
 * @usage web_application
 * @see PApplet#loop()
 * @see PApplet#redraw()
 * @see PApplet#draw()
 */
  synchronized public void noLoop() {
    if (looping) {
      looping = false;
    }
  }


  public boolean isLooping() {
    return looping;
  }


  //////////////////////////////////////////////////////////////


  BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();
  private final Object eventQueueDequeueLock = new Object[0];


  /**
   * Add an event to the internal event queue, or process it immediately if
   * the sketch is not currently looping.
   */
  public void postEvent(processing.event.Event pe) {
    eventQueue.add(pe);

    if (!looping) {
      dequeueEvents();
    }
  }


  protected void dequeueEvents() {
    synchronized (eventQueueDequeueLock) {
      while (!eventQueue.isEmpty()) {
        Event e = eventQueue.remove();
        switch (e.getFlavor()) {
          case Event.MOUSE -> handleMouseEvent((MouseEvent) e);
          case Event.KEY -> handleKeyEvent((KeyEvent) e);
        }
      }
    }
  }


  //////////////////////////////////////////////////////////////


  /**
   * Actually take action based on a mouse event.
   * Internally updates mouseX, mouseY, mousePressed, and mouseEvent.
   * Then it calls the event type with no params,
   * i.e. mousePressed() or mouseReleased() that the user may have
   * overloaded to do something more useful.
   */
  protected void handleMouseEvent(MouseEvent event) {
    // https://processing.org/bugs/bugzilla/170.html
    // also prevents mouseExited() on the mac from hosing the mouse
    // position, because x/y are bizarre values on the exit event.
    // see also the id check below... both of these go together.
    // Not necessary to set mouseX/Y on RELEASE events because the
    // actual position will have been set by a PRESS or DRAG event.
    // However, PRESS events might come without a preceding move,
    // if the sketch window gains focus on that PRESS.
    final int action = event.getAction();
    if (action == MouseEvent.DRAG ||
        action == MouseEvent.MOVE ||
        action == MouseEvent.PRESS) {
      pmouseX = emouseX;
      pmouseY = emouseY;
      if (windowRatio) {
        rmouseX = floor((event.getX() - ratioLeft) / ratioScale);
        rmouseY = floor((event.getY() - ratioTop) / ratioScale);
      }
      mouseX = event.getX();
      mouseY = event.getY();
    }

    int button = event.getButton();

    // If running on macOS, allow ctrl-click as right mouse click.
    // Handled inside PApplet so that the same logic need not be redone
    // for each Surface independently, since the code seems to be identical:
    // no native code backing Surface objects (AWT, JavaFX, JOGL) handle it.
    if (PApplet.platform == PConstants.MACOS &&
        button == PConstants.LEFT) {
      if (action == MouseEvent.PRESS && event.isControlDown()) {
        // The ctrl key may only be down during the press, but we need to store
        // it so that the drag or release still is considered a right-click.
        macosCtrlClick = true;
      }
      if (macosCtrlClick) {
        button = PConstants.RIGHT;
        // Recreate the Event object as a right-click, and unset the CTRL flag,
        // since it's not a ctrl-right-click, it's just a right click.
        int modifiers = event.getModifiers() & ~Event.CTRL;
        event = new MouseEvent(event.getNative(), event.getMillis(),
                               event.getAction(), modifiers,
                               event.getX(), event.getY(),
                               button, event.getCount());
      }
      if (action == MouseEvent.CLICK) {
        // Un-set the variable for the next time around.
        // (This won't affect the current event being handled.)
        // Changed to CLICK instead of RELEASE for 4.0a6, because the click
        // event will fire after the press/drag/release events have fired.
        macosCtrlClick = false;
      }
    }

    // Get the (already processed) button code
    mouseButton = button;

    /*
    // Compatibility for older code (these have AWT object params, not P5)
    if (mouseEventMethods != null) {
      // Probably also good to check this, in case anyone tries to call
      // postEvent() with an artificial event they've created.
      if (event.getNative() != null) {
        mouseEventMethods.handle(new Object[] { event.getNative() });
      }
    }
    */

    // this used to only be called on mouseMoved and mouseDragged
    // change it back if people run into trouble
    if (firstMouse) {
      pmouseX = mouseX;
      pmouseY = mouseY;
      dmouseX = mouseX;
      dmouseY = mouseY;
      firstMouse = false;
    }

    mouseEvent = event;

    // Do this up here in case a registered method relies on the
    // boolean for mousePressed.

    switch (action) {
      case MouseEvent.PRESS -> mousePressed = true;
      case MouseEvent.RELEASE -> mousePressed = false;
    }

    handleMethods("mouseEvent", event);

    switch (action) {
      case MouseEvent.PRESS -> mousePressed(event);
      case MouseEvent.RELEASE -> mouseReleased(event);
      case MouseEvent.CLICK -> mouseClicked(event);
      case MouseEvent.DRAG -> mouseDragged(event);
      case MouseEvent.MOVE -> mouseMoved(event);
      case MouseEvent.ENTER -> mouseEntered(event);
      case MouseEvent.EXIT -> mouseExited(event);
      case MouseEvent.WHEEL -> mouseWheel(event);
    }

    if ((action == MouseEvent.DRAG) ||
        (action == MouseEvent.MOVE)) {
      emouseX = mouseX;
      emouseY = mouseY;
    }
  }


  /**
   *
   * The <b>mousePressed()</b> function is called once after every time a mouse
   * button is pressed. The <b>mouseButton</b> variable (see the related
   * reference entry) can be used to determine which button has been pressed.
   * <br />
   * <br />
   * Mouse and keyboard events only work when a program has <b>draw()</b>.
   * Without <b>draw()</b>, the code is only run once and then stops listening
   * for events.
   *
   * <h3>Advanced</h3>
   *
   * If you must, use int button = mouseEvent.getButton(); to figure out which
   * button was clicked. It will be one of: MouseEvent.BUTTON1,
   * MouseEvent.BUTTON2, MouseEvent.BUTTON3 Note, however, that this is
   * completely inconsistent across platforms.
   *
   * @webref input:mouse
   * @webBrief Called once after every time a mouse button is pressed
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public void mousePressed() { }


  public void mousePressed(MouseEvent event) {
    mousePressed();
  }


  /**
   *
   * The <b>mouseReleased()</b> function is called every time a mouse button is
   * released. <br />
   * <br />
   * Mouse and keyboard events only work when a program has <b>draw()</b>.
   * Without <b>draw()</b>, the code is only run once and then stops listening
   * for events.
   *
   * @webref input:mouse
   * @webBrief Called every time a mouse button is released
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public void mouseReleased() { }


  public void mouseReleased(MouseEvent event) {
    mouseReleased();
  }


  /**
   *
   * The <b>mouseClicked()</b> function is called <i>after</i> a mouse button
   * has been pressed and then released. <br />
   * <br />
   * Mouse and keyboard events only work when a program has <b>draw()</b>.
   * Without <b>draw()</b>, the code is only run once and then stops listening
   * for events.
   *
   * <h3>Advanced</h3> When the mouse is clicked, mousePressed() will be called,
   * then mouseReleased(), then mouseClicked(). Note that mousePressed is
   * already false inside mouseClicked().
   *
   * @webref input:mouse
   * @webBrief Called once after a mouse button has been pressed and then
   *           released
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public void mouseClicked() { }


  public void mouseClicked(MouseEvent event) {
    mouseClicked();
  }


  /**
   *
   * The <b>mouseDragged()</b> function is called once every time the mouse
   * moves while a mouse button is pressed. (If a button <i>is not</i> being
   * pressed, <b>mouseMoved()</b> is called instead.) <br />
   * <br />
   * Mouse and keyboard events only work when a program has <b>draw()</b>.
   * Without <b>draw()</b>, the code is only run once and then stops listening
   * for events.
   *
   * @webref input:mouse
   * @webBrief Called once every time the mouse moves and a mouse button is
   *           pressed
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public void mouseDragged() { }


  public void mouseDragged(MouseEvent event) {
    mouseDragged();
  }


  /**
   *
   * The <b>mouseMoved()</b> function is called every time the mouse moves and a
   * mouse button is not pressed. (If a button <i>is</i> being pressed,
   * <b>mouseDragged()</b> is called instead.) <br />
   * <br />
   * Mouse and keyboard events only work when a program has <b>draw()</b>.
   * Without <b>draw()</b>, the code is only run once and then stops listening
   * for events.
   *
   * @webref input:mouse
   * @webBrief Called every time the mouse moves and a mouse button is not
   *           pressed
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public void mouseMoved() { }


  public void mouseMoved(MouseEvent event) {
    mouseMoved();
  }


  public void mouseEntered() { }


  public void mouseEntered(MouseEvent event) {
    mouseEntered();
  }


  public void mouseExited() { }


  public void mouseExited(MouseEvent event) {
    mouseExited();
  }

  /**
   * @nowebref
   */
  public void mouseWheel() { }

  /**
   * The code within the <b>mouseWheel()</b> event function
   * is run when the mouse wheel is moved. (Some mice don't
   * have wheels and this function is only applicable with
   * mice that have a wheel.) The <b>getCount()</b> function
   * used within <b>mouseWheel()</b> returns positive values
   * when the mouse wheel is rotated down (toward the user),
   * and negative values for the other direction (up or away
   * from the user). On OS X with "natural" scrolling enabled,
   * the values are opposite.
   * <br /><br />
   * Mouse and keyboard events only work when a program has
   * <b>draw()</b>. Without <b>draw()</b>, the code is only
   * run once and then stops listening for events.
   *
   * @webref input:mouse
   * @webBrief The code within the <b>mouseWheel()</b> event function
   * is run when the mouse wheel is moved
   * @param event the MouseEvent
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   */
  public void mouseWheel(MouseEvent event) {
    mouseWheel();
  }



  //////////////////////////////////////////////////////////////


  protected void handleKeyEvent(KeyEvent event) {

    // Get rid of auto-repeating keys if desired and supported
    if (!keyRepeatEnabled && event.isAutoRepeat()) return;

    keyEvent = event;
    key = event.getKey();
    keyCode = event.getKeyCode();

    switch (event.getAction()) {
      case KeyEvent.PRESS -> {
        Long hash = ((long) keyCode << Character.SIZE) | key;
        if (!pressedKeys.contains(hash)) pressedKeys.add(hash);
        keyPressed = true;
        keyPressed(keyEvent);
      }
      case KeyEvent.RELEASE -> {
        pressedKeys.remove(((long) keyCode << Character.SIZE) | key);
        keyPressed = !pressedKeys.isEmpty();
        keyReleased(keyEvent);
      }
      case KeyEvent.TYPE -> keyTyped(keyEvent);
    }

    /*
    if (keyEventMethods != null) {
      keyEventMethods.handle(new Object[] { event.getNative() });
    }
    */

    handleMethods("keyEvent", event);

    // if someone else wants to intercept the key, they should
    // set key to zero (or something besides the ESC).
    if (event.getAction() == KeyEvent.PRESS) {
      //if (key == java.awt.event.KeyEvent.VK_ESCAPE) {
      if (key == ESC) {
        exit();
      }
      // When running tethered to the Processing application, respond to
      // Ctrl-W (or Cmd-W) events by closing the sketch. Not enabled when
      // running independently, because this sketch may be one component
      // embedded inside an application that has its own close behavior.
      if (external &&
          event.getKeyCode() == 'W' &&
          ((event.isMetaDown() && platform == MACOS) ||
           (event.isControlDown() && platform != MACOS))) {
        // Can't use this native stuff b/c the native event might be NEWT
//      if (external && event.getNative() instanceof java.awt.event.KeyEvent &&
//          ((java.awt.event.KeyEvent) event.getNative()).getModifiers() ==
//            Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() &&
//          event.getKeyCode() == 'W') {
        exit();
      }
    }
  }


  /**
   *
   *
   * The <b>keyPressed()</b> function is called once every time a key is
   * pressed. The key that was pressed is stored in the <b>key</b> variable.
   * <br />
   * <br />
   * For non-ASCII keys, use the <b>keyCode</b> variable. The keys included in
   * the ASCII specification (BACKSPACE, TAB, ENTER, RETURN, ESC, and DELETE) do
   * not require checking to see if the key is coded; for those keys, you should
   * simply use the <b>key</b> variable directly (and not <b>keyCode</b>). If
   * you're making cross-platform projects, note that the ENTER key is commonly
   * used on PCs and Unix, while the RETURN key is used on Macs. Make sure your
   * program will work on all platforms by checking for both ENTER and RETURN.
   * <br />
   * <br />
   * Because of how operating systems handle key repeats, holding down a key may
   * cause multiple calls to <b>keyPressed()</b>. The rate of repeat is set by
   * the operating system, and may be configured differently on each computer.
   * <br />
   * <br />
   * Note that there is a similarly named boolean variable called
   * <b>keyPressed</b>. See its reference page for more information. <br />
   * <br />
   * Mouse and keyboard events only work when a program has <b>draw()</b>.
   * Without <b>draw()</b>, the code is only run once and then stops listening
   * for events. <br />
   * <br />
   * With the release of macOS Sierra, Apple changed how key repeat works, so
   * keyPressed may not function as expected. See <a href=
   * "https://github.com/processing/processing/wiki/Troubleshooting#key-repeat-on-macos-sierra">here</a>
   * for details of the problem and how to fix it.
   *
   * <h3>Advanced</h3>
   *
   * Called each time a single key on the keyboard is pressed. Because of how
   * operating systems handle key repeats, holding down a key will cause
   * multiple calls to keyPressed(), because the OS repeat takes over.
   * <p>
   * Examples for key handling: (Tested on Windows XP, please notify if
   * different on other platforms, I have a feeling macOS and Linux may do
   * otherwise)
   *
   * <PRE>
   * 1. Pressing 'a' on the keyboard:
   *    keyPressed  with key == 'a' and keyCode == 'A'
   *    keyTyped    with key == 'a' and keyCode ==  0
   *    keyReleased with key == 'a' and keyCode == 'A'
   *
   * 2. Pressing 'A' on the keyboard:
   *    keyPressed  with key == 'A' and keyCode == 'A'
   *    keyTyped    with key == 'A' and keyCode ==  0
   *    keyReleased with key == 'A' and keyCode == 'A'
   *
   * 3. Pressing 'shift', then 'a' on the keyboard (caps lock is off):
   *    keyPressed  with key == CODED and keyCode == SHIFT
   *    keyPressed  with key == 'A'   and keyCode == 'A'
   *    keyTyped    with key == 'A'   and keyCode == 0
   *    keyReleased with key == 'A'   and keyCode == 'A'
   *    keyReleased with key == CODED and keyCode == SHIFT
   *
   * 4. Holding down the 'a' key.
   *    The following will happen several times,
   *    depending on your machine's "key repeat rate" settings:
   *    keyPressed  with key == 'a' and keyCode == 'A'
   *    keyTyped    with key == 'a' and keyCode ==  0
   *    When you finally let go, you'll get:
   *    keyReleased with key == 'a' and keyCode == 'A'
   *
   * 5. Pressing and releasing the 'shift' key
   *    keyPressed  with key == CODED and keyCode == SHIFT
   *    keyReleased with key == CODED and keyCode == SHIFT
   *    (note there is no keyTyped)
   *
   * 6. Pressing the tab key in a Component with Java 1.4 will
   *    normally do nothing, but PApplet dynamically shuts
   *    this behavior off if Java 1.4 is in use (tested 1.4.2_05 Windows).
   *    Java 1.1 (Microsoft VM) passes the TAB key through normally.
   *    Not tested on other platforms or for 1.3.
   * </PRE>
   *
   * @webref input:keyboard
   * @webBrief Called once every time a key is pressed
   * @see PApplet#key
   * @see PApplet#keyCode
   * @see PApplet#keyPressed
   * @see PApplet#keyReleased()
   */
  public void keyPressed() { }


  public void keyPressed(KeyEvent event) {
    keyPressed();
  }


  /**
   *
   * The <b>keyReleased()</b> function is called once every time a key is
   * released. The key that was released will be stored in the <b>key</b>
   * variable. See <b>key</b> and <b>keyCode</b> for more information. <br />
   * <br />
   * Mouse and keyboard events only work when a program has <b>draw()</b>.
   * Without <b>draw()</b>, the code is only run once and then stops listening
   * for events.
   *
   * @webref input:keyboard
   * @webBrief Called once every time a key is released
   * @see PApplet#key
   * @see PApplet#keyCode
   * @see PApplet#keyPressed
   * @see PApplet#keyPressed()
   */
  public void keyReleased() { }


  public void keyReleased(KeyEvent event) {
    keyReleased();
  }


  /**
   *
   * The <b>keyTyped()</b> function is called once every time a key is pressed,
   * but action keys such as Ctrl, Shift, and Alt are ignored. <br />
   * <br />
   * Because of how operating systems handle key repeats, holding down a key may
   * cause multiple calls to <b>keyTyped()</b>. The rate of repeat is set by the
   * operating system, and may be configured differently on each computer.
   * <br />
   * <br />
   * Mouse and keyboard events only work when a program has <b>draw()</b>.
   * Without <b>draw()</b>, the code is only run once and then stops listening
   * for events.
   *
   * @webref input:keyboard
   * @webBrief Called once every time a key is pressed, but action keys such as
   *           Ctrl, Shift, and Alt are ignored
   * @see PApplet#keyPressed
   * @see PApplet#key
   * @see PApplet#keyCode
   * @see PApplet#keyReleased()
   */
  public void keyTyped() { }


  public void keyTyped(KeyEvent event) {
    keyTyped();
  }



  //////////////////////////////////////////////////////////////

  // I am focused man, and I'm not afraid of death.
  // and I'm going all out. I circle the vultures in a van
  // and I run the block.


  public void focusGained() { }


  public void focusLost() {
    // TODO: if user overrides this without calling super it's not gonna work
    pressedKeys.clear();
  }



  //////////////////////////////////////////////////////////////

  // getting the time


  /**
   *
   * Returns the number of milliseconds (thousandths of a second) since
   * starting the sketch. This information is often used for timing animation
   * sequences.
   *
   * <h3>Advanced</h3>
   * This is a function, rather than a variable, because it may
   * change multiple times per frame.
   *
   * @webref input:time date
   * @webBrief Returns the number of milliseconds (thousandths of a second) since
   * the sketch started.
   * @see PApplet#second()
   * @see PApplet#minute()
   * @see PApplet#hour()
   * @see PApplet#day()
   * @see PApplet#month()
   * @see PApplet#year()
   *
   */
  public int millis() {
    return (int) (System.currentTimeMillis() - millisOffset);
  }

  /**
   *
   * Processing communicates with the clock on your computer. The
   * <b>second()</b> function returns the current second as a value from 0 to 59.
   *
   * @webref input:time date
   * @webBrief Returns the current second as a value from 0 to 59
   * @see PApplet#millis()
   * @see PApplet#minute()
   * @see PApplet#hour()
   * @see PApplet#day()
   * @see PApplet#month()
   * @see PApplet#year()
   * */
  static public int second() {
    return Calendar.getInstance().get(Calendar.SECOND);
  }

  /**
   *
   * Processing communicates with the clock on your computer. The
   * <b>minute()</b> function returns the current minute as a value from 0 to 59.
   * @webref input:time date
   * @webBrief Returns the current minute as a value from 0 to 59
   * @see PApplet#millis()
   * @see PApplet#second()
   * @see PApplet#hour()
   * @see PApplet#day()
   * @see PApplet#month()
   * @see PApplet#year()
   *
   * */
  static public int minute() {
    return Calendar.getInstance().get(Calendar.MINUTE);
  }

  /**
   *
   * Processing communicates with the clock on your computer. The
   * <b>hour()</b> function returns the current hour as a value from 0 to 23.
   *
   * @webref input:time date
   * @webBrief Returns the current hour as a value from 0 to 23
   * @see PApplet#millis()
   * @see PApplet#second()
   * @see PApplet#minute()
   * @see PApplet#day()
   * @see PApplet#month()
   * @see PApplet#year()
   *
   */
  static public int hour() {
    return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
  }

  /**
   *
   * Processing communicates with the clock on your computer. The
   * <b>day()</b> function returns the current day as a value from 1 to 31.
   *
   * <h3>Advanced</h3>
   * Get the current day of the month (1 through 31).
   * <p>
   * If you're looking for the day of the week (M-F or whatever)
   * or day of the year (1..365) then use java's Calendar.get()
   *
   * @webref input:time date
   * @webBrief Returns the current day as a value from 1 to 31
   * @see PApplet#millis()
   * @see PApplet#second()
   * @see PApplet#minute()
   * @see PApplet#hour()
   * @see PApplet#month()
   * @see PApplet#year()
   */
  static public int day() {
    return Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
  }

  /**
   *
   * Processing communicates with the clock on your computer. The
   * <b>month()</b> function returns the current month as a value from 1 to 12.
   *
   * @webref input:time date
   * @webBrief Returns the current month as a value from 1 to 12
   * @see PApplet#millis()
   * @see PApplet#second()
   * @see PApplet#minute()
   * @see PApplet#hour()
   * @see PApplet#day()
   * @see PApplet#year()
   */
  static public int month() {
    // months are number 0..11 so change to colloquial 1..12
    return Calendar.getInstance().get(Calendar.MONTH) + 1;
  }

  /**
   * Processing communicates with the clock on your computer. The
   * <b>year()</b> function returns the current year as an integer (2003,
   * 2004, 2005, etc).
   *
   * @webref input:time date
   * @webBrief Returns the current year as an integer (2003,
   * 2004, 2005, etc)
   * @see PApplet#millis()
   * @see PApplet#second()
   * @see PApplet#minute()
   * @see PApplet#hour()
   * @see PApplet#day()
   * @see PApplet#month()
   */
  static public int year() {
    return Calendar.getInstance().get(Calendar.YEAR);
  }


  //////////////////////////////////////////////////////////////

  // controlling time (playing god)


  /**
   *
   * The <b>delay()</b> function causes the program to halt for a specified time.
   * Delay times are specified in thousandths of a second. For example,
   * running <b>delay(3000)</b> will stop the program for three seconds and
   * <b>delay(500)</b> will stop the program for a half-second.
   * <p/>
   * The screen only updates when the end of <b>draw()</b> is reached, so <b>delay()</b>
   * cannot be used to slow down drawing. For instance, you cannot use <b>delay()</b>
   * to control the timing of an animation.
   * <p/>
   * The <b>delay()</b> function should only be used for pausing scripts (i.e.
   * a script that needs to pause a few seconds before attempting a download,
   * or a sketch that needs to wait a few milliseconds before reading from
   * the serial port).
   *
   * @webref environment
   * @webBrief The <b>delay()</b> function causes the program to halt for a specified time
   * @param napTime milliseconds to pause before running draw() again
   * @see PApplet#frameRate
   * @see PApplet#draw()
   */
  public void delay(int napTime) {
    //if (frameCount != 0) {
    //if (napTime > 0) {
    try {
      Thread.sleep(napTime);
    } catch (InterruptedException ignored) { }
    //}
    //}
  }


  /**
   *
   * Specifies the number of frames to be displayed every second. For example,
   * the function call <b>frameRate(30)</b> will attempt to refresh 30 times a
   * second. If the processor is not fast enough to maintain the specified rate,
   * the frame rate will not be achieved. Setting the frame rate within
   * <b>setup()</b> is recommended. The default rate is 60 frames per second.
   *
   * @webref environment
   * @webBrief Specifies the number of frames to be displayed every second
   * @param fps
   *          number of desired frames per second
   * @see PApplet#frameRate
   * @see PApplet#frameCount
   * @see PApplet#setup()
   * @see PApplet#draw()
   * @see PApplet#loop()
   * @see PApplet#noLoop()
   * @see PApplet#redraw()
   */
  public void frameRate(float fps) {
    surface.setFrameRate(fps);
  }


  //////////////////////////////////////////////////////////////


  /**
   * Links to a webpage either in the same window or in a new window. The
   * complete URL must be specified.
   *
   * @param url the complete URL, as a String in quotes
   */
  public void link(String url) {
    if (!surface.openLink(url)) {
      // Just pass it off to launch() and hope for the best
      launch(url);
    }
  }


  static String openLauncher;


  /**
   *
   * Attempts to open an application or file using your platform's launcher. The
   * <b>filename</b> parameter is a String specifying the file name and
   * location. The location parameter must be a full path name, or the name of
   * an executable in the system's PATH. In most cases, using a full path is the
   * best option, rather than relying on the system PATH. Be sure to make the
   * file executable before attempting to open it (chmod +x).<br />
   * <br />
   * This function (roughly) emulates what happens when you double-click an
   * application or document in the macOS Finder, the Windows Explorer, or your
   * favorite Linux file manager. If you're trying to run command line functions
   * directly, use the <b>exec()</b> function instead (see below).<br />
   * <br />
   * This function behaves differently on each platform. On Windows, the
   * parameters are sent to the Windows shell via "cmd /c". On Mac OS X, the
   * "open" command is used (type "man open" in Terminal.app for documentation).
   * On Linux, it first tries gnome-open, then kde-open, but if neither are
   * available, it sends the command to the shell and prays that something
   * useful happens.<br />
   * <br />
   * For users familiar with Java, this is not the same as Runtime.exec(),
   * because the launcher command is prepended. Instead, the
   * <b>exec(String[])</b> function is a shortcut for
   * Runtime.getRuntime.exec(String[]). The <b>exec()</b> function is documented
   * in the
   * <a href="http://processing.github.io/processing-javadocs/core/">JavaDoc</a>
   * in the <b>PApplet</b> class.
   *
   * @webref input:files
   * @webBrief Attempts to open an application or file using your platform's
   *           launcher
   * @param args
   *          arguments to the launcher, e.g. a filename.
   * @usage Application
   */
  static public Process launch(String... args) {
    String[] params = null;

    if (platform == WINDOWS) {
      // just launching the .html file via the shell works
      // but make sure to chmod +x the .html files first
      // also place quotes around it in case there's a space
      // in the user.dir part of the url
      params = new String[] { "cmd", "/c" };

    } else if (platform == MACOS) {
      params = new String[] { "open" };

    } else if (platform == LINUX) {
      // xdg-open is in the Free Desktop Specification and really should just
      // work on desktop Linux. Not risking it though.
      final String[] launchers = { "xdg-open", "gnome-open", "kde-open" };
      for (String launcher : launchers) {
        if (openLauncher != null) break;
        try {
          Process p = Runtime.getRuntime().exec(new String[] { launcher });
          /*int result =*/ p.waitFor();
          // Not installed will throw an IOException (JDK 1.4.2, Ubuntu 7.04)
          openLauncher = launcher;
        } catch (Exception ignored) { }
      }
      if (openLauncher == null) {
        System.err.println("Could not find xdg-open, gnome-open, or kde-open: " +
                           "the open() command may not work.");
      }
      if (openLauncher != null) {
        params = new String[] { openLauncher };
      }
    //} else {  // give up and just pass it to Runtime.exec()
      //open(new String[] { filename });
      //params = new String[] { filename };
    }
    if (params != null) {
      // If the 'open', 'gnome-open' or 'cmd' are already included
      if (params[0].equals(args[0])) {
        // then don't prepend those params again
        return exec(args);
      } else {
        params = concat(params, args);
        return exec(params);
      }
    } else {
      return exec(args);
    }
  }


  /**
   * Pass a set of arguments directly to the command line. Uses Java's
   * <A HREF="https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#exec-java.lang.String:A-">Runtime.exec()</A>
   * method. This is different from the <A HREF="https://processing.org/reference/launch_.html">launch()</A>
   * method, which uses the operating system's launcher to open the files.
   * It's always a good idea to use a full path to the executable here.
   * <pre>
   * exec("/usr/bin/say", "welcome to the command line");
   * </pre>
   * Or if you want to wait until it's completed, something like this:
   * <pre>
   * Process p = exec("/usr/bin/say", "waiting until done");
   * try {
   *   int result = p.waitFor();
   *   println("the process returned " + result);
   * } catch (InterruptedException e) { }
   * </pre>
   * You can also get the system output and error streams from the Process
   * object, but that's more that we'd like to cover here.
   * @return a <A HREF="https://docs.oracle.com/javase/8/docs/api/java/lang/Process.html">Process</A> object
   */
  static public Process exec(String... args) {
    try {
      return Runtime.getRuntime().exec(args);
    } catch (Exception e) {
      throw new RuntimeException("Exception while attempting " + join(args, ' '), e);
    }
  }


  static class LineThread extends Thread {
    InputStream input;
    StringList output;


    LineThread(InputStream input, StringList output) {
      this.input = input;
      this.output = output;
      start();
    }

    @Override
    public void run() {
      // It's not sufficient to use BufferedReader, because if the app being
      // called fills up stdout or stderr to quickly, the app will hang.
      // Instead, write to a byte[] array and then parse it once finished.
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        saveStream(baos, input);
        BufferedReader reader =
          createReader(new ByteArrayInputStream(baos.toByteArray()));
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }


  /**
   * Alternative version of exec() that retrieves stdout and stderr into the
   * StringList objects provided. This is a convenience function that handles
   * simple exec() calls. If the results will be more than a couple lines,
   * you shouldn't use this function, you should use a more elaborate method
   * that makes use of proper threading (to drain the shell output) and error
   * handling to address the many things that can go wrong within this method.
   *
   * @param stdout a non-null StringList object to be filled with any output
   * @param stderr a non-null StringList object to be filled with error lines
   * @param args each argument to be passed as a series of String objects
   * @return the result returned from the application, or -1 if an Exception
   *         occurs before the application is able to return a result.
   */
  static public int exec(StringList stdout, StringList stderr, String... args) {
    Process p = exec(args);

    Thread outThread = new LineThread(p.getInputStream(), stdout);
    Thread errThread = new LineThread(p.getErrorStream(), stderr);

    try {
      int result = p.waitFor();
      outThread.join();
      errThread.join();
      return result;

    } catch (InterruptedException e) {
      // Throwing the exception here because we can't give a valid 'result'
      throw new RuntimeException(e);
    }
  }


  /**
   * Same as exec() above, but prefixes the call with a shell.
   */
  static public int shell(StringList stdout, StringList stderr, String... args) {
    String shell;
    String runCmd;
    StringList argList = new StringList();
    if (platform == WINDOWS) {
      shell = System.getenv("COMSPEC");
      runCmd = "/C";
    } else {
      shell = "/bin/sh";
      runCmd = "-c";
      // attempt to emulate the behavior of an interactive shell
      // can't use -i or -l since the version of bash shipped with macOS does not support this together with -c
      // also we want to make sure no motd or similar gets returned as stdout
      argList.append("if [ -f /etc/profile ]; then . /etc/profile >/dev/null 2>&1; fi;");
      argList.append("if [ -f ~/.bash_profile ]; then . ~/.bash_profile >/dev/null 2>&1; elif [ -f ~/.bash_profile ]; then . ~/.bash_profile >/dev/null 2>&1; elif [ -f ~/.profile ]; then ~/.profile >/dev/null 2>&1; fi;");
    }
    for (String arg : args) {
      argList.append(arg);
    }
    return exec(stdout, stderr, shell, runCmd, argList.join(" "));
  }


  /*
  static private final String shellQuoted(String arg) {
    if (arg.indexOf(' ') != -1) {
      // check to see if already quoted
      if ((arg.charAt(0) != '\"' || arg.charAt(arg.length()-1) != '\"') &&
          (arg.charAt(0) != '\'' || arg.charAt(arg.length()-1) != '\'')) {

        // see which quotes we can use
        if (arg.indexOf('\"') == -1) {
          // if no double quotes, try those first
          return "\"" + arg + "\"";

        } else if (arg.indexOf('\'') == -1) {
          // if no single quotes, let's use those
          return "'" + arg + "'";
        }
      }
    }
    return arg;
  }
  */


  //////////////////////////////////////////////////////////////


  /**
   * Better way of handling e.printStackTrace() calls so that they can be
   * handled by subclasses as necessary.
   */
  protected void printStackTrace(Throwable t) {
    t.printStackTrace();
  }


  /**
   * Function for an application to kill itself and display an error.
   * Mostly this is here to be improved later.
   */
  public void die(String what) {
    dispose();
    throw new RuntimeException(what);
  }


  /**
   * Same as above but with an exception. Also needs work.
   */
  public void die(String what, Exception e) {
    if (e != null) e.printStackTrace();
    die(what);
  }


  /**
   *
   * Quits/stops/exits the program. Programs without a <b>draw()</b> function
   * exit automatically after the last line has run, but programs with
   * <b>draw()</b> run continuously until the program is manually stopped or
   * <b>exit()</b> is run.<br />
   * <br />
   * Rather than terminating immediately, <b>exit()</b> will cause the sketch
   * to exit after <b>draw()</b> has completed (or after <b>setup()</b>
   * completes if called during the <b>setup()</b> function).<br />
   * <br />
   * For Java programmers, this is <em>not</em> the same as System.exit().
   * Further, System.exit() should not be used because closing out an
   * application while <b>draw()</b> is running may cause a crash
   * (particularly with P3D).
   *
   * @webref structure
   * @webBrief Quits/stops/exits the program
   */
  public void exit() {
    if (surface.isStopped()) {
      // exit immediately, dispose() has already been called,
      // meaning that the main thread has long since exited
      exitActual();

    } else if (looping) {
      // dispose() will be called as the thread exits
      finished = true;
      // tell the code to call exitActual() to do a System.exit()
      // once the next draw() has completed
      exitCalled = true;

    } else {  // !looping
      // if not looping, shut down things explicitly,
      // because the main thread will be sleeping
      dispose();

      // now get out
      exitActual();
    }
  }


  public boolean exitCalled() {
    return exitCalled;
  }


  /**
   * Some subclasses (I'm looking at you, processing.py) might wish to do something
   * other than actually terminate the JVM. This gives them a chance to do whatever
   * they have in mind when cleaning up.
   */
  public void exitActual() {
    System.exit(0);
  }


  /**
   * Called to dispose of resources and shut down the sketch.
   * Destroys the thread, dispose the renderer,and notify listeners.
   * <p>
   * Not to be called or overridden by users. If called multiple times,
   * will only notify listeners once. Register a "dispose" listener instead.
   */
  public void dispose() {
    // moved here from stop()
    finished = true;  // let the sketch know it is shut down time

    // don't run the disposers twice
    if (surface.stopThread()) {

      // shut down renderer
      if (g != null) {
        g.dispose();
      }
      // run dispose() methods registered by libraries
      handleMethods("dispose");
    }

    if (platform == MACOS) {
      try {
        final String td = "processing.core.ThinkDifferent";
        final Class<?> thinkDifferent = getClass().getClassLoader().loadClass(td);
        thinkDifferent.getMethod("cleanup").invoke(null);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

  }



  //////////////////////////////////////////////////////////////


  /**
   * Call a method in the current class based on its name.
   * <p/>
   * Note that the function being called must be public. Inside the PDE,
   * 'public' is automatically added, but when used without the preprocessor,
   * (like from Eclipse) you'll have to do it yourself.
   */
  public void method(String name) {
    try {
      Method method = getClass().getMethod(name);
      method.invoke(this);

    } catch (IllegalArgumentException | IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.getTargetException().printStackTrace();
    } catch (NoSuchMethodException nsme) {
      System.err.println("There is no public " + name + "() method " +
                         "in the class " + getClass().getName());
    }
  }


  /**
   * Processing sketches follow a specific sequence of steps: <b>setup()</b>
   * first, followed by <b>draw()</b> over and over and over again in a loop. A
   * thread is also a series of steps with a beginning, a middle, and an end. A
   * Processing sketch is a single thread, often referred to as the "Animation"
   * thread. Other threads' sequences, however, can run independently of the
   * main animation loop. In fact, you can launch any number of threads at one
   * time, and they will all run concurrently. <br />
   * <br />
   * You cannot draw to the screen from a function called by <b>thread()</b>.
   * Because it runs independently, the code will not be synchronized to the
   * animation thread, causing strange or at least inconsistent results. Use
   * <b>thread()</b> to load files or do other tasks that take time. When the
   * task is finished, set a variable that indicates the task is complete, and
   * check that from inside your <b>draw()</b> method. <br />
   * <br />
   * Processing uses threads quite often, such as with library functions like
   * <b>captureEvent()</b> and <b>movieEvent()</b>. These functions are
   * triggered by a different thread running behind the scenes, and they alert
   * Processing whenever they have something to report. This is useful when you
   * need to perform a task that takes too long and would slow down the main
   * animation's frame rate, such as grabbing data from the network. If a
   * separate thread gets stuck or has an error, the entire program won't grind
   * to a halt, since the error only stops that individual thread. <br />
   * <br />
   * Writing your own thread can be a complex endeavor that involves extending
   * the Java <a href=
   * "https://docs.oracle.com/javase/tutorial/essential/concurrency/threads.html">Thread</a>
   * class. However, the <b>thread()</b> method is a quick and dirty way to
   * implement a simple thread in Processing. By passing in a <b>String</b> that
   * matches the name of a function declared elsewhere in the sketch, Processing
   * will execute that function in a separate thread.
   *
   * @webref structure
   * @webBrief Launch a new thread and call the specified function from that new
   *           thread
   * @usage Application
   * @param name
   *          name of the function to be executed in a separate thread
   * @see PApplet#setup()
   * @see PApplet#draw()
   * @see PApplet#loop()
   * @see PApplet#noLoop()
   */
  public void thread(final String name) {
    new Thread(() -> method(name)).start();
  }



  //////////////////////////////////////////////////////////////

  // SCREEN GRABASS


  /**
   *
   * Saves an image from the display window. Append a file extension to the name
   * of the file, to indicate the file format to be used: either TIFF (.tif),
   * TARGA (.tga), JPEG (.jpg), or PNG (.png). If no extension is included in
   * the filename, the image will save in TIFF format and <b>.tif</b> will be
   * added to the name. These files are saved to the sketch's folder, which may
   * be opened by selecting "Show sketch folder" from the "Sketch" menu.
   * Alternatively, the files can be saved to any location on the computer by
   * using an absolute path (something that starts with / on Unix and Linux, or
   * a drive letter on Windows).<br />
   * <br />
   * All images saved from the main drawing window will be opaque. To save
   * images without a background, use <b>createGraphics()</b>.
   *
   * @webref output:image
   * @webBrief Saves an image from the display window
   * @param filename
   *          any sequence of letters and numbers
   * @see PApplet#saveFrame()
   * @see PApplet#createGraphics(int, int, String)
   */
  public void save(String filename) {
    g.save(savePath(filename));
  }


  /**
   */
  public void saveFrame() {
    g.save(savePath("screen-" + nf(frameCount, 4) + ".tif"));
  }


  /**
   *
   * Saves a numbered sequence of images, one image each time the function is
   * run. To save an image that is identical to the display window, run the
   * function at the end of <b>draw()</b> or within mouse and key events such as
   * <b>mousePressed()</b> and <b>keyPressed()</b>. Use the Movie Maker program
   * in the Tools menu to combine these images to a movie.<br />
   * <br />
   * If <b>saveFrame()</b> is used without parameters, it will save files as
   * screen-0000.tif, screen-0001.tif, and so on. You can specify the name of
   * the sequence with the <b>filename</b> parameter, including hash marks
   * (####), which will be replaced by the current <b>frameCount</b> value. (The
   * number of hash marks is used to determine how many digits to include in the
   * file names.) Append a file extension, to indicate the file format to be
   * used: either TIFF (.tif), TARGA (.tga), JPEG (.jpg), or PNG (.png). Image
   * files are saved to the sketch's folder, which may be opened by selecting
   * "Show Sketch Folder" from the "Sketch" menu.<br />
   * <br />
   * Alternatively, the files can be saved to any location on the computer by
   * using an absolute path (something that starts with / on Unix and Linux, or
   * a drive letter on Windows).<br />
   * <br />
   * All images saved from the main drawing window will be opaque. To save
   * images without a background, use <b>createGraphics()</b>.
   *
   * @webref output:image
   * @webBrief Saves a numbered sequence of images, one image each time the
   *           function is run
   * @see PApplet#save(String)
   * @see PApplet#createGraphics(int, int, String, String)
   * @see PApplet#frameCount
   * @param filename
   *          any sequence of letters or numbers that ends with either ".tif",
   *          ".tga", ".jpg", or ".png"
   */
  public void saveFrame(String filename) {
    g.save(savePath(insertFrame(filename)));
  }


  /**
   * Check a string for #### signs to see if the frame number should be
   * inserted. Used for functions like saveFrame() and beginRecord() to
   * replace the # marks with the frame number. If only one # is used,
   * it will be ignored, under the assumption that it's probably not
   * intended to be the frame number.
   */
  public String insertFrame(String what) {
    int first = what.indexOf('#');
    int last = what.lastIndexOf('#');

    if ((first != -1) && (last - first > 0)) {
      String prefix = what.substring(0, first);
      int count = last - first + 1;
      String suffix = what.substring(last + 1);
      return prefix + nf(frameCount, count) + suffix;
    }
    return what;  // no change
  }



  //////////////////////////////////////////////////////////////

  // CURSOR

  //


  /**
   * Set the cursor type
   * @param kind either ARROW, CROSS, HAND, MOVE, TEXT, or WAIT
   */
  public void cursor(int kind) {
    surface.setCursor(kind);
  }


  /**
   * Replace the cursor with the specified PImage. The x- and y-
   * coordinate of the center will be the center of the image.
   */
  public void cursor(PImage img) {
    cursor(img, img.width/2, img.height/2);
  }


  /**
   *
   * Sets the cursor to a predefined symbol or an image, or makes it visible if
   * already hidden. If you are trying to set an image as the cursor, the
   * recommended size is 16x16 or 32x32 pixels. The values for parameters
   * <b>x</b> and <b>y</b> must be less than the dimensions of the image. <br />
   * <br />
   * Setting or hiding the cursor does not generally work with "Present" mode
   * (when running full-screen). <br />
   * <br />
   * With the P2D and P3D renderers, a generic set of cursors are used because
   * the OpenGL renderer doesn't have access to the default cursor images for
   * each platform
   * (<a href="https://github.com/processing/processing/issues/3791">Issue
   * 3791</a>).
   *
   * @webref environment
   * @webBrief Sets the cursor to a predefined symbol, an image, or makes it
   *           visible if already hidden
   * @see PApplet#noCursor()
   * @param img
   *          any variable of type PImage
   * @param x
   *          the horizontal active spot of the cursor
   * @param y
   *          the vertical active spot of the cursor
   */
  public void cursor(PImage img, int x, int y) {
    surface.setCursor(img, x, y);
  }


  /**
   * Show the cursor after noCursor() was called.
   * Notice that the program remembers the last set cursor type
   */
  public void cursor() {
    surface.showCursor();
  }


  /**
   *
   * Hides the cursor from view. Will not work when running the program in a
   * web browser or when running in full screen (Present) mode.
   *
   * <h3>Advanced</h3>
   * Hide the cursor by creating a transparent image
   * and using it as a custom cursor.
   * @webref environment
   * @webBrief Hides the cursor from view
   * @see PApplet#cursor()
   * @usage Application
   */
  public void noCursor() {
    surface.hideCursor();
  }


  //////////////////////////////////////////////////////////////

/**
 *
 * The <b>print()</b> function writes to the console area, the black rectangle
 * at the bottom of the Processing environment. This function is often helpful
 * for looking at the data a program is producing. The companion function
 * <b>println()</b> works like <b>print()</b>, but creates a new line of text
 * for each call to the function. More than one parameter can be passed into the
 * function by separating them with commas. Alternatively, individual elements
 * can be separated with quotes ("") and joined with the addition operator
 * (+).<br />
 * <br />
 * Using <b>print()</b> on an object will output <b>null</b>, a memory location
 * that may look like "@10be08," or the result of the <b>toString()</b> method
 * from the object that's being printed. Advanced users who want more useful
 * output when calling <b>print()</b> on their own classes can add a
 * <b>toString()</b> method to the class that returns a String.<br />
 * <br />
 * Note that the console is relatively slow. It works well for occasional
 * messages, but does not support high-speed, real-time output (such as at 60
 * frames per second). It should also be noted, that a print() within a for loop
 * can sometimes lock up the program, and cause the sketch to freeze.
 *
 * @webref output:text area
 * @webBrief Writes to the console area of the Processing environment
 * @usage IDE
 * @param what
 *          data to print to console
 * @see PApplet#println()
 * @see PApplet#printArray(Object)
 * @see PApplet#join(String[], char)
 */
  static public void print(byte what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(boolean what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(char what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(int what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(long what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(float what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(double what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(String what) {
    System.out.print(what);
    System.out.flush();
  }

  /**
   * @param variables list of data, separated by commas
   */
  static public void print(Object... variables) {
    StringBuilder sb = new StringBuilder();
    for (Object o : variables) {
      if (sb.length() != 0) {
        sb.append(" ");
      }
      if (o == null) {
        sb.append("null");
      } else {
        sb.append(o);
      }
    }
    System.out.print(sb);
  }


  /**
   *
   * The <b>println()</b> function writes to the console area, the black
   * rectangle at the bottom of the Processing environment. This function is
   * often helpful for looking at the data a program is producing. Each call to
   * this function creates a new line of output. More than one parameter can be
   * passed into the function by separating them with commas. Alternatively,
   * individual elements can be separated with quotes ("") and joined with the
   * addition operator (+).<br />
   * <br />
   * Before Processing 2.1, <b>println()</b> was used to write array data to the
   * console. Now, use <b>printArray()</b> to write array data to the
   * console.<br />
   * <br />
   * Note that the console is relatively slow. It works well for occasional
   * messages, but does not support high-speed, real-time output (such as at 60
   * frames per second). It should also be noted, that a println() within a for
   * loop can sometimes lock up the program, and cause the sketch to freeze.
   *
   * @webref output:text area
   * @webBrief Writes to the text area of the Processing environment's console
   * @usage IDE
   * @see PApplet#print(byte)
   * @see PApplet#printArray(Object)
   */
  static public void println() {
    System.out.println();
  }


/**
 * @param what data to print to console
 */
  static public void println(byte what) {
    System.out.println(what);
    System.out.flush();
  }

  static public void println(boolean what) {
    System.out.println(what);
    System.out.flush();
  }

  static public void println(char what) {
    System.out.println(what);
    System.out.flush();
  }

  static public void println(int what) {
    System.out.println(what);
    System.out.flush();
  }

  static public void println(long what) {
    System.out.println(what);
    System.out.flush();
  }

  static public void println(float what) {
    System.out.println(what);
    System.out.flush();
  }

  static public void println(double what) {
    System.out.println(what);
    System.out.flush();
  }

  static public void println(String what) {
    System.out.println(what);
    System.out.flush();
  }

  /**
   * @param variables list of data, separated by commas
   */
  static public void println(Object... variables) {
//    System.out.println("got " + variables.length + " variables");
    print(variables);
    println();
  }


  /*
  // Breaking this out since the compiler doesn't know the difference between
  // Object... and just Object (with an array passed in). This should take care
  // of the confusion for at least the most common case (a String array).
  // On second thought, we're going the printArray() route, since the other
  // object types are also used frequently.
  static public void println(String[] array) {
    for (int i = 0; i < array.length; i++) {
      System.out.println("[" + i + "] \"" + array[i] + "\"");
    }
    System.out.flush();
  }
  */


  /**
   * For arrays, use printArray() instead. This function causes a warning
   * because the new print(Object...) and println(Object...) functions can't
   * be reliably bound by the compiler.
   */
  static public void println(Object what) {
    if (what == null) {
      System.out.println("null");
    } else if (what.getClass().isArray()) {
      printArray(what);
    } else {
      System.out.println(what);
      System.out.flush();
    }
  }

  /**
   *
   * The <b>printArray()</b> function writes array data to the text
   * area of the Processing environment's console. A new line
   * is put between each element of the array. This function
   * can only print one dimensional arrays.
   * Note that the console is relatively slow. It works well
   * for occasional messages, but does not support high-speed,
   * real-time output (such as at 60 frames per second).
   *
   * @webref output:text area
   * @webBrief Writes array data to the text
   * area of the Processing environment's console.
   * @param what one-dimensional array
   * @usage IDE
   * @see PApplet#print(byte)
   * @see PApplet#println()
   */
  static public void printArray(Object what) {
    if (what == null) {
      // special case since this does fugly things on > 1.1
      System.out.println("null");

    } else {
      String name = what.getClass().getName();
      if (name.charAt(0) == '[') {
        switch (name.charAt(1)) {
          case '[' ->
            // don't even mess with multidimensional arrays (case '[')
            // or anything else that's not int, float, boolean, char
            System.out.println(what);
          case 'L' -> {
            // print a 1D array of objects as individual elements
            Object[] poo = (Object[]) what;
            for (int i = 0; i < poo.length; i++) {
              if (poo[i] instanceof String) {
                System.out.println("[" + i + "] \"" + poo[i] + "\"");
              } else {
                System.out.println("[" + i + "] " + poo[i]);
              }
            }
          }
          case 'Z' -> {  // boolean
            boolean[] zz = (boolean[]) what;
            for (int i = 0; i < zz.length; i++) {
              System.out.println("[" + i + "] " + zz[i]);
            }
          }
          case 'B' -> {  // byte
            byte[] bb = (byte[]) what;
            for (int i = 0; i < bb.length; i++) {
              System.out.println("[" + i + "] " + bb[i]);
            }
          }
          case 'C' -> {  // char
            char[] cc = (char[]) what;
            for (int i = 0; i < cc.length; i++) {
              System.out.println("[" + i + "] '" + cc[i] + "'");
            }
          }
          case 'I' -> {  // int
            int[] ii = (int[]) what;
            for (int i = 0; i < ii.length; i++) {
              System.out.println("[" + i + "] " + ii[i]);
            }
          }
          case 'J' -> {  // int
            long[] jj = (long[]) what;
            for (int i = 0; i < jj.length; i++) {
              System.out.println("[" + i + "] " + jj[i]);
            }
          }
          case 'F' -> {  // float
            float[] ff = (float[]) what;
            for (int i = 0; i < ff.length; i++) {
              System.out.println("[" + i + "] " + ff[i]);
            }
          }
          case 'D' -> {  // double
            double[] dd = (double[]) what;
            for (int i = 0; i < dd.length; i++) {
              System.out.println("[" + i + "] " + dd[i]);
            }
          }
          default -> System.out.println(what);
        }
      } else {  // not an array
        System.out.println(what);
      }
    }
    System.out.flush();
  }


  static public void debug(String msg) {
    if (DEBUG) println(msg);
  }
  //

  /*
  // not very useful, because it only works for public (and protected?)
  // fields of a class, not local variables to methods
  public void printvar(String name) {
    try {
      Field field = getClass().getDeclaredField(name);
      println(name + " = " + field.get(this));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  */


  //////////////////////////////////////////////////////////////

  // MATH

  // lots of convenience methods for math with floats.
  // doubles are overkill for processing sketches, and casting
  // things all the time is annoying, thus the functions below.

/**
   *
   * Calculates the absolute value (magnitude) of a number. The absolute
   * value of a number is always positive.
   *
   * @webref math:calculation
   * @webBrief Calculates the absolute value (magnitude) of a number
   * @param n number to compute
   */
  static public final float abs(float n) {
    return (n < 0) ? -n : n;
  }

  static public final int abs(int n) {
    return (n < 0) ? -n : n;
  }

/**
   *
   * Squares a number (multiplies a number by itself). The result is always a
   * positive number, as multiplying two negative numbers always yields a
   * positive result. For example, <b>-1 * -1 = 1.</b>
   *
   * @webref math:calculation
   * @webBrief Squares a number (multiplies a number by itself)
   * @param n number to square
   * @see PApplet#sqrt(float)
   */
  static public final float sq(float n) {
    return n*n;
  }

/**
   *
   * Calculates the square root of a number. The square root of a number is
   * always positive, even though there may be a valid negative root. The
   * square root <b>s</b> of number <b>a</b> is such that <b>s*s = a</b>. It
   * is the opposite of squaring.
   *
   * @webref math:calculation
   * @webBrief Calculates the square root of a number
   * @param n non-negative number
   * @see PApplet#pow(float, float)
   * @see PApplet#sq(float)
   */
  static public final float sqrt(float n) {
    return (float)Math.sqrt(n);
  }

/**
   *
   * Calculates the natural logarithm (the base-<i>e</i> logarithm) of a
   * number. This function expects the values greater than 0.0.
   *
   * @webref math:calculation
   * @webBrief Calculates the natural logarithm (the base-<i>e</i> logarithm) of a
   * number
   * @param n number greater than 0.0
   */
  static public final float log(float n) {
    return (float)Math.log(n);
  }

/**
   *
   * Returns Euler's number <i>e</i> (2.71828...) raised to the power of the
   * <b>value</b> parameter.
   *
   * @webref math:calculation
   * @webBrief Returns Euler's number <i>e</i> (2.71828...) raised to the power of the
   * <b>value</b> parameter
   * @param n exponent to raise
   */
  static public final float exp(float n) {
    return (float)Math.exp(n);
  }

/**
   *
   * Facilitates exponential expressions. The <b>pow()</b> function is an
   * efficient way of multiplying numbers by themselves (or their reciprocal)
   * in large quantities. For example, <b>pow(3, 5)</b> is equivalent to the
   * expression 3*3*3*3*3 and <b>pow(3, -5)</b> is equivalent to 1 / 3*3*3*3*3.
   *
   * @webref math:calculation
   * @webBrief Facilitates exponential expressions
   * @param n base of the exponential expression
   * @param e power by which to raise the base
   * @see PApplet#sqrt(float)
   */
  static public final float pow(float n, float e) {
    return (float)Math.pow(n, e);
  }

/**
 *
 * Determines the largest value in a sequence of numbers, and then returns that
 * value. <b>max()</b> accepts either two or three <b>float</b> or <b>int</b>
 * values as parameters, or an array of any length.
 *
 * @webref math:calculation
 * @webBrief Determines the largest value in a sequence of numbers
 * @param a
 *          first number to compare
 * @param b
 *          second number to compare
 * @see PApplet#min(float, float, float)
 */
  static public final int max(int a, int b) {
    return (a > b) ? a : b;
  }

  static public final float max(float a, float b) {
    return (a > b) ? a : b;
  }

  /*
  static public final double max(double a, double b) {
    return (a > b) ? a : b;
  }
  */

/**
 * @param c third number to compare
 */
  static public final int max(int a, int b, int c) {
    return (a > b) ? ((a > c) ? a : c) : ((b > c) ? b : c);
  }


  static public final float max(float a, float b, float c) {
    return (a > b) ? ((a > c) ? a : c) : ((b > c) ? b : c);
  }


  /**
   * @param list array of numbers to compare
   */
  static public final int max(int[] list) {
    if (list.length == 0) {
      throw new ArrayIndexOutOfBoundsException(ERROR_MIN_MAX);
    }
    int max = list[0];
    for (int i = 1; i < list.length; i++) {
      if (list[i] > max) max = list[i];
    }
    return max;
  }

  static public final float max(float[] list) {
    if (list.length == 0) {
      throw new ArrayIndexOutOfBoundsException(ERROR_MIN_MAX);
    }
    float max = list[0];
    for (int i = 1; i < list.length; i++) {
      if (list[i] > max) max = list[i];
    }
    return max;
  }


//  /**
//   * Find the maximum value in an array.
//   * Throws an ArrayIndexOutOfBoundsException if the array is length 0.
//   * @param list the source array
//   * @return The maximum value
//   */
  /*
  static public final double max(double[] list) {
    if (list.length == 0) {
      throw new ArrayIndexOutOfBoundsException(ERROR_MIN_MAX);
    }
    double max = list[0];
    for (int i = 1; i < list.length; i++) {
      if (list[i] > max) max = list[i];
    }
    return max;
  }
  */


  static public final int min(int a, int b) {
    return (a < b) ? a : b;
  }

  static public final float min(float a, float b) {
    return (a < b) ? a : b;
  }

  /*
  static public final double min(double a, double b) {
    return (a < b) ? a : b;
  }
  */


  static public final int min(int a, int b, int c) {
    return (a < b) ? ((a < c) ? a : c) : ((b < c) ? b : c);
  }

/**
 *
 * Determines the smallest value in a sequence of numbers, and then returns that
 * value. <b>min()</b> accepts either two or three <b>float</b> or <b>int</b>
 * values as parameters, or an array of any length.
 *
 * @webref math:calculation
 * @webBrief Determines the smallest value in a sequence of numbers
 * @param a
 *          first number
 * @param b
 *          second number
 * @param c
 *          third number
 * @see PApplet#max(float, float, float)
 */
  static public final float min(float a, float b, float c) {
    return (a < b) ? ((a < c) ? a : c) : ((b < c) ? b : c);
  }

  /*
  static public final double min(double a, double b, double c) {
    return (a < b) ? ((a < c) ? a : c) : ((b < c) ? b : c);
  }
  */


  /**
   * @param list array of numbers to compare
   */
  static public final int min(int[] list) {
    if (list.length == 0) {
      throw new ArrayIndexOutOfBoundsException(ERROR_MIN_MAX);
    }
    int min = list[0];
    for (int i = 1; i < list.length; i++) {
      if (list[i] < min) min = list[i];
    }
    return min;
  }

  static public final float min(float[] list) {
    if (list.length == 0) {
      throw new ArrayIndexOutOfBoundsException(ERROR_MIN_MAX);
    }
    float min = list[0];
    for (int i = 1; i < list.length; i++) {
      if (list[i] < min) min = list[i];
    }
    return min;
  }


  /*
   * Find the minimum value in an array.
   * Throws an ArrayIndexOutOfBoundsException if the array is length 0.
   * @param list the source array
   * @return The minimum value
   */
  /*
  static public final double min(double[] list) {
    if (list.length == 0) {
      throw new ArrayIndexOutOfBoundsException(ERROR_MIN_MAX);
    }
    double min = list[0];
    for (int i = 1; i < list.length; i++) {
      if (list[i] < min) min = list[i];
    }
    return min;
  }
  */


  static public final int constrain(int amt, int low, int high) {
    return (amt < low) ? low : ((amt > high) ? high : amt);
  }

/**
   *
   * Constrains a value to not exceed a maximum and minimum value.
   *
   * @webref math:calculation
   * @webBrief Constrains a value to not exceed a maximum and minimum value
   * @param amt the value to constrain
   * @param low minimum limit
   * @param high maximum limit
   * @see PApplet#max(float, float, float)
   * @see PApplet#min(float, float, float)
   */

  static public final float constrain(float amt, float low, float high) {
    return (amt < low) ? low : ((amt > high) ? high : amt);
  }

/**
   *
   * Calculates the sine of an angle. This function expects the values of the
   * <b>angle</b> parameter to be provided in radians (values from 0 to
   * 6.28). Values are returned in the range -1 to 1.
   *
   * @webref math:trigonometry
   * @webBrief Calculates the sine of an angle
   * @param angle an angle in radians
   * @see PApplet#cos(float)
   * @see PApplet#tan(float)
   * @see PApplet#radians(float)
   */
  static public final float sin(float angle) {
    return (float)Math.sin(angle);
  }

/**
   *
   * Calculates the cosine of an angle. This function expects the values of
   * the <b>angle</b> parameter to be provided in radians (values from 0 to
   * PI*2). Values are returned in the range -1 to 1.
   *
   * @webref math:trigonometry
   * @webBrief Calculates the cosine of an angle
   * @param angle an angle in radians
   * @see PApplet#sin(float)
   * @see PApplet#tan(float)
   * @see PApplet#radians(float)
   */
  static public final float cos(float angle) {
    return (float)Math.cos(angle);
  }

/**
   *
   * Calculates the ratio of the sine and cosine of an angle. This function
   * expects the values of the <b>angle</b> parameter to be provided in
   * radians (values from 0 to PI*2). Values are returned in the range
   * <b>infinity</b> to <b>-infinity</b>.
   *
   * @webref math:trigonometry
   * @webBrief Calculates the ratio of the sine and cosine of an angle
   * @param angle an angle in radians
   * @see PApplet#cos(float)
   * @see PApplet#sin(float)
   * @see PApplet#radians(float)
   */
  static public final float tan(float angle) {
    return (float)Math.tan(angle);
  }

/**
   *
   * The inverse of <b>sin()</b>, returns the arc sine of a value. This
   * function expects the values in the range of -1 to 1 and values are
   * returned in the range <b>-PI/2</b> to <b>PI/2</b>.
   *
   * @webref math:trigonometry
   * @webBrief The inverse of <b>sin()</b>, returns the arc sine of a value
   * @param value the value whose arc sine is to be returned
   * @see PApplet#sin(float)
   * @see PApplet#acos(float)
   * @see PApplet#atan(float)
   */
  static public final float asin(float value) {
    return (float)Math.asin(value);
  }

/**
   *
   * The inverse of <b>cos()</b>, returns the arc cosine of a value. This
   * function expects the values in the range of -1 to 1 and values are
   * returned in the range <b>0</b> to <b>PI (3.1415927)</b>.
   *
   * @webref math:trigonometry
   * @webBrief The inverse of <b>cos()</b>, returns the arc cosine of a value
   * @param value the value whose arc cosine is to be returned
   * @see PApplet#cos(float)
   * @see PApplet#asin(float)
   * @see PApplet#atan(float)
   */
  static public final float acos(float value) {
    return (float)Math.acos(value);
  }

/**
   *
   * The inverse of <b>tan()</b>, returns the arc tangent of a value. This
   * function expects the values in the range of -Infinity to Infinity
   * (exclusive) and values are returned in the range <b>-PI/2</b> to <b>PI/2 </b>.
   *
   * @webref math:trigonometry
   * @webBrief The inverse of <b>tan()</b>, returns the arc tangent of a value
   * @param value -Infinity to Infinity (exclusive)
   * @see PApplet#tan(float)
   * @see PApplet#asin(float)
   * @see PApplet#acos(float)
   */
  static public final float atan(float value) {
    return (float)Math.atan(value);
  }

/**
   *
   * Calculates the angle (in radians) from a specified point to the
   * coordinate origin as measured from the positive x-axis. Values are
   * returned as a <b>float</b> in the range from <b>PI</b> to <b>-PI</b>.
   * The <b>atan2()</b> function is most often used for orienting geometry to
   * the position of the cursor.  Note: The y-coordinate of the point is the
   * first parameter and the x-coordinate is the second due the structure
   * of calculating the tangent.
   *
   * @webref math:trigonometry
   * @webBrief Calculates the angle (in radians) from a specified point to the
   * coordinate origin as measured from the positive x-axis
   * @param y y-coordinate of the point
   * @param x x-coordinate of the point
   * @see PApplet#tan(float)
   */
  static public final float atan2(float y, float x) {
    return (float)Math.atan2(y, x);
  }

/**
   *
   * Converts a radian measurement to its corresponding value in degrees.
   * Radians and degrees are two ways of measuring the same thing. There are
   * 360 degrees in a circle and 2*PI radians in a circle. For example,
   * 90&deg; = PI/2 = 1.5707964. All trigonometric functions in Processing
   * require their parameters to be specified in radians.
   *
   * @webref math:trigonometry
   * @webBrief Converts a radian measurement to its corresponding value in degrees
   * @param radians radian value to convert to degrees
   * @see PApplet#radians(float)
   */
  static public final float degrees(float radians) {
    return radians * RAD_TO_DEG;
  }

/**
   *
   * Converts a degree measurement to its corresponding value in radians.
   * Radians and degrees are two ways of measuring the same thing. There are
   * 360 degrees in a circle and 2*PI radians in a circle. For example,
   * 90&deg; = PI/2 = 1.5707964. All trigonometric functions in Processing
   * require their parameters to be specified in radians.
   *
   * @webref math:trigonometry
   * @webBrief Converts a degree measurement to its corresponding value in radians
   * @param degrees degree value to convert to radians
   * @see PApplet#degrees(float)
   */
  static public final float radians(float degrees) {
    return degrees * DEG_TO_RAD;
  }

/**
   *
   * Calculates the closest int value that is greater than or equal to the
   * value of the parameter. For example, <b>ceil(9.03)</b> returns the value 10.
   *
   * @webref math:calculation
   * @webBrief Calculates the closest int value that is greater than or equal to the
   * value of the parameter
   * @param n number to round up
   * @see PApplet#floor(float)
   * @see PApplet#round(float)
   */
  static public final int ceil(float n) {
    return (int) Math.ceil(n);
  }

/**
   *
   * Calculates the closest int value that is less than or equal to the value
   * of the parameter.
   *
   * @webref math:calculation
   * @webBrief Calculates the closest int value that is less than or equal to the value
   * of the parameter
   * @param n number to round down
   * @see PApplet#ceil(float)
   * @see PApplet#round(float)
   */
  static public final int floor(float n) {
    return (int) Math.floor(n);
  }

/**
 *
 * Calculates the integer closest to the <b>n</b> parameter. For example,
 * <b>round(133.8)</b> returns the value 134.
 *
 * @webref math:calculation
 * @webBrief Calculates the integer closest to the <b>value</b> parameter
 * @param n
 *          number to round
 * @see PApplet#floor(float)
 * @see PApplet#ceil(float)
 */
  static public final int round(float n) {
    return Math.round(n);
  }


  static public final float mag(float a, float b) {
    return (float)Math.sqrt(a*a + b*b);
  }

/**
   *
   * Calculates the magnitude (or length) of a vector. A vector is a
   * direction in space commonly used in computer graphics and linear
   * algebra. Because it has no "start" position, the magnitude of a vector
   * can be thought of as the distance from coordinate (0,0) to its (x,y)
   * value. Therefore, <b>mag()</b> is a shortcut for writing <b>dist(0, 0, x, y)</b>.
   *
   * @webref math:calculation
   * @webBrief Calculates the magnitude (or length) of a vector
   * @param a first value
   * @param b second value
   * @param c third value
   * @see PApplet#dist(float, float, float, float)
   */
  static public final float mag(float a, float b, float c) {
    return (float)Math.sqrt(a*a + b*b + c*c);
  }


  static public final float dist(float x1, float y1, float x2, float y2) {
    return sqrt(sq(x2-x1) + sq(y2-y1));
  }

/**
   *
   * Calculates the distance between two points.
   *
   * @webref math:calculation
   * @webBrief Calculates the distance between two points
   * @param x1 x-coordinate of the first point
   * @param y1 y-coordinate of the first point
   * @param z1 z-coordinate of the first point
   * @param x2 x-coordinate of the second point
   * @param y2 y-coordinate of the second point
   * @param z2 z-coordinate of the second point
   */
  static public final float dist(float x1, float y1, float z1,
                                 float x2, float y2, float z2) {
    return sqrt(sq(x2-x1) + sq(y2-y1) + sq(z2-z1));
  }

/**
   *
   * Calculates a number between two numbers at a specific increment. The
   * <b>amt</b> parameter is the amount to interpolate between the two values
   * where 0.0 equal to the first point, 0.1 is very near the first point,
   * 0.5 is half-way in between, etc. The lerp function is convenient for
   * creating motion along a straight path and for drawing dotted lines.
   *
   * @webref math:calculation
   * @webBrief Calculates a number between two numbers at a specific increment
   * @param start first value
   * @param stop second value
   * @param amt float between 0.0 and 1.0
   * @see PGraphics#curvePoint(float, float, float, float, float)
   * @see PGraphics#bezierPoint(float, float, float, float, float)
   * @see PVector#lerp(PVector, float)
   * @see PGraphics#lerpColor(int, int, float)
   */
  static public final float lerp(float start, float stop, float amt) {
    return start + (stop-start) * amt;
  }

  /**
   *
   * Normalizes a number from another range into a value between 0 and 1.
   * Identical to <b>map(value, low, high, 0, 1)</b>.<br />
   * <br />
   * Numbers outside the range are not clamped to 0 and 1, because
   * out-of-range values are often intentional and useful. (See the second
   * example above.)
   *
   * @webref math:calculation
   * @webBrief Normalizes a number from another range into a value between 0 and
   *           1
   * @param value
   *          the incoming value to be converted
   * @param start
   *          lower bound of the value's current range
   * @param stop
   *          upper bound of the value's current range
   * @see PApplet#map(float, float, float, float, float)
   * @see PApplet#lerp(float, float, float)
   */
  static public final float norm(float value, float start, float stop) {
    return (value - start) / (stop - start);
  }

  /**
   *
   * Re-maps a number from one range to another.<br />
   * <br />
   * In the first example above, the number 25 is converted from a value in the
   * range of 0 to 100 into a value that ranges from the left edge of the window
   * (0) to the right edge (width).<br />
   * <br />
   * As shown in the second example, numbers outside the range are
   * not clamped to the minimum and maximum parameters values,
   * because out-of-range values are often intentional and useful.
   *
   * @webref math:calculation
   * @webBrief Re-maps a number from one range to another
   * @param value
   *          the incoming value to be converted
   * @param start1
   *          lower bound of the value's current range
   * @param stop1
   *          upper bound of the value's current range
   * @param start2
   *          lower bound of the value's target range
   * @param stop2
   *          upper bound of the value's target range
   * @see PApplet#norm(float, float, float)
   * @see PApplet#lerp(float, float, float)
   */
  static public final float map(float value,
                                float start1, float stop1,
                                float start2, float stop2) {
    float outgoing =
      start2 + (stop2 - start2) * ((value - start1) / (stop1 - start1));
    String badness = null;
    if (outgoing != outgoing) {
      badness = "NaN (not a number)";

    } else if (outgoing == Float.NEGATIVE_INFINITY ||
               outgoing == Float.POSITIVE_INFINITY) {
      badness = "infinity";
    }
    if (badness != null) {
      final String msg =
        String.format("map(%s, %s, %s, %s, %s) called, which returns %s",
                      nf(value), nf(start1), nf(stop1),
                      nf(start2), nf(stop2), badness);
      PGraphics.showWarning(msg);
    }
    return outgoing;
  }


  /*
  static public final double map(double value,
                                 double istart, double istop,
                                 double ostart, double ostop) {
    return ostart + (ostop - ostart) * ((value - istart) / (istop - istart));
  }
  */



  //////////////////////////////////////////////////////////////

  // RANDOM NUMBERS


  Random internalRandom;

  /**
   *
   */
  public final float random(float high) {
    // avoid an infinite loop when 0 or NaN are passed in
    if (high == 0 || high != high) {
      return 0;
    }

    if (internalRandom == null) {
      internalRandom = new Random();
    }

    // for some reason (rounding error?) Math.random() * 3
    // can sometimes return '3' (once in ~30 million tries)
    // so a check was added to avoid the inclusion of 'howbig'
    float value;
    do {
      value = internalRandom.nextFloat() * high;
    } while (value == high);
    return value;
  }

  /**
   *
   * Returns a float from a random series of numbers having a mean of 0
   * and standard deviation of 1. Each time the <b>randomGaussian()</b>
   * function is called, it returns a number fitting a Gaussian, or
   * normal, distribution. There is theoretically no minimum or maximum
   * value that <b>randomGaussian()</b> might return. Rather, there is
   * just a very low probability that values far from the mean will be
   * returned; and a higher probability that numbers near the mean will
   * be returned.
   *
   * @webref math:random
   * @webBrief Returns a float from a random series of numbers having a mean of 0
   * and standard deviation of 1
   * @see PApplet#random(float,float)
   * @see PApplet#noise(float, float, float)
   */
  public final float randomGaussian() {
    if (internalRandom == null) {
      internalRandom = new Random();
    }
    return (float) internalRandom.nextGaussian();
  }


  /**
   *
   * Generates random numbers. Each time the <b>random()</b> function is called,
   * it returns an unexpected value within the specified range. If only one
   * parameter is passed to the function, it will return a float between zero
   * and the value of the <b>high</b> parameter. For example, <b>random(5)</b>
   * returns values between 0 and 5 (starting at zero, and up to, but not
   * including, 5).<br />
   * <br />
   * If two parameters are specified, the function will return a float with a
   * value between the two values. For example, <b>random(-5, 10.2)</b> returns
   * values starting at -5 and up to (but not including) 10.2. To convert a
   * floating-point random number to an integer, use the <b>int()</b> function.
   *
   * @webref math:random
   * @webBrief Generates random numbers
   * @param low
   *          lower limit
   * @param high
   *          upper limit
   * @see PApplet#randomSeed(long)
   * @see PApplet#noise(float, float, float)
   */
  public final float random(float low, float high) {
    if (low >= high) return low;
    float diff = high - low;
    float value;
    // because of rounding error, can't just add low, otherwise it may hit high
    // https://github.com/processing/processing/issues/4551
    do {
      value = random(diff) + low;
    } while (value == high);
    return value;
  }


 /**
  *
  * Sets the seed value for <b>random()</b>. By default, <b>random()</b>
  * produces different results each time the program is run. Set the <b>seed</b>
  * parameter to a constant to return the same pseudo-random numbers each time
  * the software is run.
  *
  * @webref math:random
  * @webBrief Sets the seed value for <b>random()</b>
  * @param seed
  *          seed value
  * @see PApplet#random(float,float)
  * @see PApplet#noise(float, float, float)
  * @see PApplet#noiseSeed(long)
  */
  public final void randomSeed(long seed) {
    if (internalRandom == null) {
      internalRandom = new Random();
    }
    internalRandom.setSeed(seed);
  }


  /**
   * Return a random integer from 0 up to (but not including)
   * the specified value for “high”. This is the same as calling random()
   * and casting the result to an <b>int</b>.
   */
  public final int choice(int high) {
    return (int) random(high);
  }


  /**
   * Return a random integer from “low” up to (but not including)
   * the specified value for “high”. This is the same as calling random()
   * and casting the result to an <b>int</b>.
   */
  public final int choice(int low, int high) {
    return (int) random(low, high);
  }



  //////////////////////////////////////////////////////////////

  // PERLIN NOISE

  // [toxi 040903]
  // octaves and amplitude amount per octave are now user controlled
  // via the noiseDetail() function.

  // [toxi 030902]
  // cleaned up code and now using bagel's cosine table to speed up

  // [toxi 030901]
  // implementation by the german demo group farbrausch
  // as used in their demo "art": http://www.farb-rausch.de/fr010src.zip

  static final int PERLIN_YWRAPB = 4;
  static final int PERLIN_YWRAP = 1<<PERLIN_YWRAPB;
  static final int PERLIN_ZWRAPB = 8;
  static final int PERLIN_ZWRAP = 1<<PERLIN_ZWRAPB;
  static final int PERLIN_SIZE = 4095;

  int perlin_octaves = 4; // default to medium smooth
  float perlin_amp_falloff = 0.5f; // 50% reduction/octave

  // [toxi 031112]
  // new vars needed due to recent change of cos table in PGraphics
  int perlin_TWOPI, perlin_PI;
  float[] perlin_cosTable;
  float[] perlin;

  Random perlinRandom;


  /**
   */
  public float noise(float x) {
    // is this legit? it's a dumb way to do it (but repair it later)
    return noise(x, 0f, 0f);
  }

  /**
   */
  public float noise(float x, float y) {
    return noise(x, y, 0f);
  }

  /**
   *
   * Returns the Perlin noise value at specified coordinates. Perlin noise is a
   * random sequence generator producing a more natural, harmonic succession of
   * numbers than that of the standard <b>random()</b> function. It was
   * developed by Ken Perlin in the 1980s and has been used in graphical
   * applications to generate procedural textures, shapes, terrains, and other
   * seemingly organic forms.<br />
   * <br />
   * In contrast to the <b>random()</b> function, Perlin noise is defined in an
   * infinite n-dimensional space, in which each pair of coordinates corresponds
   * to a fixed semi-random value (fixed only for the lifespan of the program).
   * The resulting value will always be between 0.0 and 1.0. Processing can
   * compute 1D, 2D and 3D noise, depending on the number of coordinates given.
   * The noise value can be animated by moving through the noise space, as
   * demonstrated in the first example above. The 2nd and 3rd dimensions can
   * also be interpreted as time.<br />
   * <br />
   * The actual noise structure is similar to that of an audio signal, in
   * respect to the function's use of frequencies. Similar to the concept of
   * harmonics in physics, Perlin noise is computed over several octaves which
   * are added together for the final result.<br />
   * <br />
   * Another way to adjust the character of the resulting sequence is the scale
   * of the input coordinates. As the function works within an infinite space,
   * the value of the coordinates doesn't matter as such; only the
   * <em>distance</em> between successive coordinates is important (such as when
   * using <b>noise()</b> within a loop). As a general rule, the smaller the
   * difference between coordinates, the smoother the resulting noise sequence.
   * Steps of 0.005-0.03 work best for most applications, but this will differ
   * depending on use.<br />
   * <br />
   * There have been debates over the accuracy of the implementation of noise in
   * Processing. For clarification, it's an implementation of "classic Perlin
   * noise" from 1983, and not the newer "simplex noise" method from 2001.
   *
   * @webref math:random
   * @webBrief Returns the Perlin noise value at specified coordinates
   * @param x
   *          x-coordinate in noise space
   * @param y
   *          y-coordinate in noise space
   * @param z
   *          z-coordinate in noise space
   * @see PApplet#noiseSeed(long)
   * @see PApplet#noiseDetail(int, float)
   * @see PApplet#random(float,float)
   */
  public float noise(float x, float y, float z) {
    if (perlin == null) {
      if (perlinRandom == null) {
        perlinRandom = new Random();
      }
      perlin = new float[PERLIN_SIZE + 1];
      for (int i = 0; i < PERLIN_SIZE + 1; i++) {
        perlin[i] = perlinRandom.nextFloat(); //(float)Math.random();
      }
      // [toxi 031112]
      // noise broke due to recent change of cos table in PGraphics
      // this will take care of it
      perlin_cosTable = PGraphics.cosLUT;
      perlin_TWOPI = perlin_PI = PGraphics.SINCOS_LENGTH;
      perlin_PI >>= 1;
    }

    if (x<0) x=-x;
    if (y<0) y=-y;
    if (z<0) z=-z;

    int xi=(int)x, yi=(int)y, zi=(int)z;
    float xf = x - xi;
    float yf = y - yi;
    float zf = z - zi;
    float rxf, ryf;

    float r=0;
    float ampl=0.5f;

    float n1,n2,n3;

    for (int i=0; i<perlin_octaves; i++) {
      int of=xi+(yi<<PERLIN_YWRAPB)+(zi<<PERLIN_ZWRAPB);

      rxf=noise_fsc(xf);
      ryf=noise_fsc(yf);

      n1  = perlin[of&PERLIN_SIZE];
      n1 += rxf*(perlin[(of+1)&PERLIN_SIZE]-n1);
      n2  = perlin[(of+PERLIN_YWRAP)&PERLIN_SIZE];
      n2 += rxf*(perlin[(of+PERLIN_YWRAP+1)&PERLIN_SIZE]-n2);
      n1 += ryf*(n2-n1);

      of += PERLIN_ZWRAP;
      n2  = perlin[of&PERLIN_SIZE];
      n2 += rxf*(perlin[(of+1)&PERLIN_SIZE]-n2);
      n3  = perlin[(of+PERLIN_YWRAP)&PERLIN_SIZE];
      n3 += rxf*(perlin[(of+PERLIN_YWRAP+1)&PERLIN_SIZE]-n3);
      n2 += ryf*(n3-n2);

      n1 += noise_fsc(zf)*(n2-n1);

      r += n1*ampl;
      ampl *= perlin_amp_falloff;
      xi<<=1; xf*=2;
      yi<<=1; yf*=2;
      zi<<=1; zf*=2;

      if (xf>=1.0f) { xi++; xf--; }
      if (yf>=1.0f) { yi++; yf--; }
      if (zf>=1.0f) { zi++; zf--; }
    }
    return r;
  }

  // [toxi 031112]
  // now adjusts to the size of the cosLUT used via
  // the new variables, defined above
  private float noise_fsc(float i) {
    // using bagel's cosine table instead
    return 0.5f*(1.0f-perlin_cosTable[(int)(i*perlin_PI)%perlin_TWOPI]);
  }

  // [toxi 040903]
  // make perlin noise quality user controlled to allow
  // for different levels of detail. lower values will produce
  // smoother results as higher octaves are suppressed

  /**
   *
   * Adjusts the character and level of detail produced by the Perlin noise
   * function. Similar to harmonics in physics, noise is computed over several
   * octaves. Lower octaves contribute more to the output signal and as such
   * define the overall intensity of the noise, whereas higher octaves create
   * finer-grained details in the noise sequence.<br />
   * <br />
   * By default, noise is computed over 4 octaves with each octave contributing
   * exactly half than its predecessor, starting at 50% strength for the first
   * octave. This falloff amount can be changed by adding a function parameter.
   * For example, a falloff factor of 0.75 means each octave will now
   * have 75% impact (25% less) of the previous lower octave. While any number
   * between 0.0 and 1.0 is valid, note that values greater than 0.5 may result
   * in <b>noise()</b> returning values greater than 1.0.<br />
   * <br />
   * By changing these parameters, the signal created by the <b>noise()</b>
   * function can be adapted to fit very specific needs and characteristics.
   *
   * @webref math:random
   * @webBrief Adjusts the character and level of detail produced by the Perlin
   *           noise function
   * @param lod
   *          number of octaves to be used by the noise
   * @see PApplet#noise(float, float, float)
   */
  public void noiseDetail(int lod) {
    if (lod>0) perlin_octaves=lod;
  }

  /**
   * @see #noiseDetail(int)
   * @param falloff falloff factor for each octave
   */
  public void noiseDetail(int lod, float falloff) {
    if (lod>0) perlin_octaves=lod;
    if (falloff>0) perlin_amp_falloff=falloff;
  }

  /**
   *
   * Sets the seed value for <b>noise()</b>. By default, <b>noise()</b>
   * produces different results each time the program is run. Set the
   * <b>value</b> parameter to a constant to return the same pseudo-random
   * numbers each time the software is run.
   *
   * @webref math:random
   * @webBrief Sets the seed value for <b>noise()</b>
   * @param seed seed value
   * @see PApplet#noise(float, float, float)
   * @see PApplet#noiseDetail(int, float)
   * @see PApplet#random(float,float)
   * @see PApplet#randomSeed(long)
   */
  public void noiseSeed(long seed) {
    if (perlinRandom == null) perlinRandom = new Random();
    perlinRandom.setSeed(seed);
    // force table reset after changing the random number seed [0122]
    perlin = null;
  }



  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   *
   * Loads an image into a variable of type <b>PImage</b>. Four types of images
   * ( <b>.gif</b>, <b>.jpg</b>, <b>.tga</b>, <b>.png</b>) images may be loaded.
   * To load correctly, images must be located in the data directory of the
   * current sketch.<br />
   * <br />
   * In most cases, load all images in <b>setup()</b> to preload them at the
   * start of the program. Loading images inside <b>draw()</b> will reduce the
   * speed of a program. Images cannot be loaded outside <b>setup()</b> unless
   * they're inside a function that's called after <b>setup()</b> has already
   * run.<br />
   * <br />
   * Alternatively, the file maybe be loaded from anywhere on the local computer
   * using an absolute path (something that starts with / on Unix and Linux, or
   * a drive letter on Windows), or the filename parameter can be a URL for a
   * file found on a network.<br />
   * <br />
   * If the file is not available or an error occurs, <b>null</b> will be
   * returned and an error message will be printed to the console. The error
   * message does not halt the program, however the <b>null</b> value may cause a
   * NullPointerException if your code does not check whether the value returned
   * is <b>null</b>.<br />
   * <br />
   * The <b>extension</b> parameter is used to determine the image type in cases
   * where the image filename does not end with a proper extension. Specify the
   * extension as the second parameter to <b>loadImage()</b>, as shown in the
   * third example on this page. Note that CMYK images are not supported.<br />
   * <br />
   * Depending on the type of error, a <b>PImage</b> object may still be
   * returned, but the width and height of the image will be set to -1. This
   * happens if bad image data is returned or cannot be decoded properly.
   * Sometimes this happens with image URLs that produce a 403 error or that
   * redirect to a password prompt, because <b>loadImage()</b> will attempt to
   * interpret the HTML as image data.
   *
   * @webref image:loading & displaying
   * @webBrief Loads an image into a variable of type <b>PImage</b>
   * @param filename
   *          name of file to load, can be .gif, .jpg, .tga, or a handful of
   *          other image types depending on your platform
   * @see PImage
   * @see PGraphics#image(PImage, float, float, float, float)
   * @see PGraphics#imageMode(int)
   * @see PGraphics#background(float, float, float, float)
   */
  public PImage loadImage(String filename) {
    return loadImage(filename, null);
  }


  /**
   * @param extension type of image to load, for example "png", "gif", "jpg"
   */
  public PImage loadImage(String filename, String extension) {
    // awaitAsyncSaveCompletion() has to run on the main thread, because P2D
    // and P3D call GL functions. If this runs on background, requestImage()
    // already called awaitAsyncSaveCompletion() on the main thread.
    if (g != null && !Thread.currentThread().getName().startsWith(REQUEST_IMAGE_THREAD_PREFIX)) {
      g.awaitAsyncSaveCompletion(filename);
    }

    // Hack so that calling loadImage() in settings() will work
    // https://github.com/processing/processing4/issues/299
    if (surface == null) {
      return ShimAWT.loadImage(this, filename, extension);
    }
    return surface.loadImage(filename, extension);
  }


  static private final String REQUEST_IMAGE_THREAD_PREFIX = "requestImage";
  // fixed-size thread pool used by requestImage()
  ExecutorService requestImagePool;


  public PImage requestImage(String filename) {
    return requestImage(filename, null);
  }


  /**
   *
   * This function loads images on a separate thread so that your sketch doesn't
   * freeze while images load during <b>setup()</b>. While the image is loading,
   * its width and height will be 0. If an error occurs while loading the image,
   * its width and height will be set to -1. You'll know when the image has
   * loaded properly because its <b>width</b> and <b>height</b> will be greater
   * than 0. Asynchronous image loading (particularly when downloading from a
   * server) can dramatically improve performance.<br />
   * <br />
   * The <b>extension</b> parameter is used to determine the image type in cases
   * where the image filename does not end with a proper extension. Specify the
   * extension as the second parameter to <b>requestImage()</b>.
   *
   * @webref image:loading & displaying
   * @webBrief Loads images on a separate thread so that your sketch does not
   *           freeze while images load during <b>setup()</b>
   * @param filename
   *          name of the file to load, can be .gif, .jpg, .tga, or a handful of
   *          other image types depending on your platform
   * @param extension
   *          the type of image to load, for example "png", "gif", "jpg"
   * @see PImage
   * @see PApplet#loadImage(String, String)
   */
  public PImage requestImage(String filename, String extension) {
    // Make sure saving to this file completes before trying to load it
    // Has to be called on main thread, because P2D and P3D need GL functions
    if (g != null) {
      g.awaitAsyncSaveCompletion(filename);
    }
    PImage vessel = createImage(0, 0, ARGB);

    // if the image loading thread pool hasn't been created, create it
    if (requestImagePool == null) {
      ThreadFactory factory = r -> new Thread(r, REQUEST_IMAGE_THREAD_PREFIX);
      requestImagePool = Executors.newFixedThreadPool(4, factory);
    }
    requestImagePool.execute(() -> {
      PImage actual = loadImage(filename, extension);

      // An error message should have already printed
      if (actual == null) {
        vessel.width = -1;
        vessel.height = -1;

      } else {
        vessel.width = actual.width;
        vessel.height = actual.height;
        vessel.format = actual.format;
        vessel.pixels = actual.pixels;

        vessel.pixelWidth = actual.width;
        vessel.pixelHeight = actual.height;
        vessel.pixelDensity = 1;
      }
    });
    return vessel;
  }


  //////////////////////////////////////////////////////////////

  // DATA I/O


  /**
   * Reads the contents of a file or URL and creates an XML
   * object with its values. If a file is specified, it must
   * be located in the sketch's "data" folder. The filename
   * parameter can also be a URL to a file found online.<br /><br />
   * All files loaded and saved by the Processing API use
   * UTF-8 encoding. If you need to load an XML file that's
   * not in UTF-8 format, see the <a href="http://processing.github.io/processing-javadocs/core/processing/data/XML.html">
   * developer's reference</a> for the XML object.
   * @webref input:files
   * @webBrief Reads the contents of a file or URL and creates an <b>XML</b>
   * object with its values
   * @param filename name of a file in the data folder or a URL.
   * @see XML
   * @see PApplet#parseXML(String)
   * @see PApplet#saveXML(XML, String)
   * @see PApplet#loadBytes(String)
   * @see PApplet#loadStrings(String)
   * @see PApplet#loadTable(String)
   */
  public XML loadXML(String filename) {
    return loadXML(filename, null);
  }


  // version that uses 'options' though there are currently no supported options
  /**
   * @nowebref
   */
  public XML loadXML(String filename, String options) {
    try {
      BufferedReader reader = createReader(filename);
      if (reader != null) {
        return new XML(reader, options);
      }
      return null;

      // can't use catch-all exception, since it might catch the
      // RuntimeException about the incorrect case sensitivity
    } catch (IOException | ParserConfigurationException | SAXException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Takes a String, parses its contents, and returns an XML object. If the
   * String does not contain XML data or cannot be parsed, a <b>null</b> value is
   * returned.<br />
   * <br />
   * <b>parseXML()</b> is most useful when pulling data dynamically, such as
   * from third-party APIs. Normally, API results would be saved to a String,
   * and then can be converted to a structured XML object using
   * <b>parseXML()</b>. Be sure to check if <b>null</b> is returned before performing
   * operations on the new XML object, in case the String content could not be
   * parsed.<br />
   * <br />
   * If your data already exists as an XML file in the data folder, it is
   * simpler to use <b>loadXML()</b>.
   *
   * @webref input:files
   * @webBrief Converts String content to an <b>XML</b> object
   * @param xmlString
   *          the content to be parsed as XML
   * @return an XML object, or null
   * @see XML
   * @see PApplet#loadXML(String)
   * @see PApplet#saveXML(XML, String)
   */
  public XML parseXML(String xmlString) {
    return parseXML(xmlString, null);
  }


  public XML parseXML(String xmlString, String options) {
    try {
      return XML.parse(xmlString, options);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Writes the contents of an XML object to a file. By default, this file is
   * saved to the sketch's folder. This folder is opened by selecting "Show
   * Sketch Folder" from the "Sketch" menu.<br />
   * <br />
   * Alternatively, the file can be saved to any location on the computer by
   * using an absolute path (something that starts with / on Unix and Linux, or
   * a drive letter on Windows).<br />
   * <br />
   * All files loaded and saved by the Processing API use UTF-8 encoding.
   *
   * @webref output:files
   * @webBrief Writes the contents of an <b>XML</b> object to a file
   * @param xml
   *          the XML object to save to disk
   * @param filename
   *          name of the file to write to
   * @see XML
   * @see PApplet#loadXML(String)
   * @see PApplet#parseXML(String)
   */
  public boolean saveXML(XML xml, String filename) {
    return saveXML(xml, filename, null);
  }

  /**
   * @nowebref
   */
  public boolean saveXML(XML xml, String filename, String options) {
    return xml.save(saveFile(filename), options);
  }

  /**
   * Takes a <b>String</b>, parses its contents, and returns a
   * <b>JSONObject</b>. If the <b>String</b> does not contain <b>JSONObject</b>
   * data or cannot be parsed, a <b>null</b> value is returned.<br />
   * <br />
   * <b>parseJSONObject()</b> is most useful when pulling data dynamically, such
   * as from third-party APIs. Normally, API results would be saved to a
   * <b>String</b>, and then can be converted to a structured <b>JSONObject</b>
   * using <b>parseJSONObject()</b>. Be sure to check if <b>null</b> is returned
   * before performing operations on the new <b>JSONObject</b> in case the
   * <b>String</b> content could not be parsed.<br />
   * <br />
   * If your data already exists as a <b>JSON</b> file in the data folder, it is
   * simpler to use <b>loadJSONObject()</b>.
   *
   * @webref input:files
   * @webBrief Takes a <b>String</b>, parses its contents, and returns a
   *           <b>JSONObject</b>
   * @param input
   *          String to parse as a JSONObject
   * @see PApplet#loadJSONObject(String)
   * @see PApplet#saveJSONObject(JSONObject, String)
   */
  public JSONObject parseJSONObject(String input) {
    try {
      return new JSONObject(new StringReader(input));
    } catch (RuntimeException e) {
      e.printStackTrace();
      return null;
    }
  }


  /**
   * Loads a JSON from the data folder or a URL, and returns a
   * <b>JSONObject</b>.<br />
   * <br />
   * All files loaded and saved by the Processing API use UTF-8 encoding.
   *
   * @webref input:files
   * @webBrief Loads a JSON from the data folder or a URL, and returns a
   *           <b>JSONObject</b>
   * @param filename
   *          name of a file in the data folder or a URL
   * @see JSONObject
   * @see JSONArray
   * @see PApplet#loadJSONArray(String)
   * @see PApplet#saveJSONObject(JSONObject, String)
   * @see PApplet#saveJSONArray(JSONArray, String)
   */
  public JSONObject loadJSONObject(String filename) {
    // can't pass of createReader() to the constructor b/c of resource leak
    BufferedReader reader = createReader(filename);
    if (reader != null) {
      JSONObject outgoing = new JSONObject(reader);
      try {
        reader.close();
      } catch (IOException e) {  // not sure what would cause this
        e.printStackTrace();
      }
      return outgoing;
    }
    return null;
  }


  /**
   * @nowebref
   */
  static public JSONObject loadJSONObject(File file) {
    // can't pass of createReader() to the constructor b/c of resource leak
    BufferedReader reader = createReader(file);
    JSONObject outgoing = new JSONObject(reader);
    try {
      reader.close();
    } catch (IOException e) {  // not sure what would cause this
      e.printStackTrace();
    }
    return outgoing;
  }


  /**
   * Writes the contents of a <b>JSONObject</b> object to a file. By default,
   * this file is saved to the sketch's folder. This folder is opened by
   * selecting "Show Sketch Folder" from the "Sketch" menu.<br />
   * <br />
   * Alternatively, the file can be saved to any location on the computer by
   * using an absolute path (something that starts with / on Unix and Linux, or
   * a drive letter on Windows).<br />
   * <br />
   * All files loaded and saved by the Processing API use UTF-8 encoding.
   *
   * @webref output:files
   * @webBrief Writes the contents of a <b>JSONObject</b> object to a file
   * @param json
   *          the JSONObject to save
   * @param filename
   *          the name of the file to save to
   * @see JSONObject
   * @see JSONArray
   * @see PApplet#loadJSONObject(String)
   * @see PApplet#loadJSONArray(String)
   * @see PApplet#saveJSONArray(JSONArray, String)
   */
  public boolean saveJSONObject(JSONObject json, String filename) {
    return saveJSONObject(json, filename, null);
  }


  /**
   * @param options "compact" and "indent=N", replace N with the number of spaces
   */
  public boolean saveJSONObject(JSONObject json, String filename, String options) {
    return json.save(saveFile(filename), options);
  }

/**
 * Takes a <b>String</b>, parses its contents, and returns a <b>JSONArray</b>.
 * If the <b>String</b> does not contain <b>JSONArray</b> data or cannot be
 * parsed, a <b>null</b> value is returned.<br />
 * <br />
 * <b>parseJSONArray()</b> is most useful when pulling data dynamically, such as
 * from third-party APIs. Normally, API results would be saved to a
 * <b>String</b>, and then can be converted to a structured <b>JSONArray</b>
 * using <b>parseJSONArray()</b>. Be sure to check if <b>null</b> is returned
 * before performing operations on the new <b>JSONArray</b> in case the
 * <b>String</b> content could not be parsed.<br />
 * <br />
 * If your data already exists as a <b>JSON</b> file in the data folder, it is
 * simpler to use <b>loadJSONArray()</b>.
 *
 * @webref input:files
 * @webBrief Takes a <b>String</b>, parses its contents, and returns a <b>JSONArray</b>
 * @param input
 *          String to parse as a JSONArray
 * @see JSONObject
 * @see PApplet#loadJSONObject(String)
 * @see PApplet#saveJSONObject(JSONObject, String)
 */
  public JSONArray parseJSONArray(String input) {
    try {
      return new JSONArray(new StringReader(input));
    } catch (RuntimeException e) {
      e.printStackTrace();
      return null;
    }
  }


  /**
   * Loads an array of JSON objects from the data folder or a URL, and returns a
   * <b>JSONArray</b>. Per standard JSON syntax, the array must be enclosed in a
   * pair of hard brackets <b>[]</b>, and each object within the array must be
   * separated by a comma.<br />
   * <br />
   * All files loaded and saved by the Processing API use UTF-8 encoding.
   *
   * @webref input:files
   * @webBrief Takes a <b>String</b>, parses its contents, and returns a
   *           <b>JSONArray</b>
   * @param filename
   *          name of a file in the data folder or a URL
   * @see JSONArray
   * @see PApplet#loadJSONObject(String)
   * @see PApplet#saveJSONObject(JSONObject, String)
   * @see PApplet#saveJSONArray(JSONArray, String)
   */
  public JSONArray loadJSONArray(String filename) {
    // can't pass of createReader() to the constructor b/c of resource leak
    BufferedReader reader = createReader(filename);
    if (reader != null) {
      JSONArray outgoing = new JSONArray(reader);
      try {
        reader.close();
      } catch (IOException e) {  // not sure what would cause this
        e.printStackTrace();
      }
      return outgoing;
    }
    return null;
  }


  static public JSONArray loadJSONArray(File file) {
    // can't pass of createReader() to the constructor b/c of resource leak
    BufferedReader reader = createReader(file);
    JSONArray outgoing = new JSONArray(reader);
    try {
      reader.close();
    } catch (IOException e) {  // not sure what would cause this
      e.printStackTrace();
    }
    return outgoing;
  }


  /**
   * Writes the contents of a <b>JSONArray</b> object to a file. By default,
   * this file is saved to the sketch's folder. This folder is opened by
   * selecting "Show Sketch Folder" from the "Sketch" menu.<br />
   * <br />
   * Alternatively, the file can be saved to any location on the computer by
   * using an absolute path (something that starts with / on Unix and Linux, or
   * a drive letter on Windows).<br />
   * <br />
   * All files loaded and saved by the Processing API use UTF-8 encoding.
   *
   * @webref output:files
   * @webBrief Writes the contents of a <b>JSONArray</b> object to a file
   * @param json
   *          the JSONArray to save
   * @param filename
   *          the name of the file to save to
   * @see JSONObject
   * @see JSONArray
   * @see PApplet#loadJSONObject(String)
   * @see PApplet#loadJSONArray(String)
   * @see PApplet#saveJSONObject(JSONObject, String)
   */
  public boolean saveJSONArray(JSONArray json, String filename) {
    return saveJSONArray(json, filename, null);
  }

  /**
   * @param options "compact" and "indent=N", replace N with the number of spaces
   */
  public boolean saveJSONArray(JSONArray json, String filename, String options) {
    return json.save(saveFile(filename), options);
  }


  /**
   * Reads the contents of a file or URL and creates a Table object with its
   * values. If a file is specified, it must be located in the sketch's "data"
   * folder. The filename parameter can also be a URL to a file found online.
   * The filename must either end in an extension or an extension must be
   * specified in the <b>options</b> parameter. For example, to use
   * tab-separated data, include "tsv" in the options parameter if the filename
   * or URL does not end in <b>.tsv</b>. Note: If an extension is in both
   * places, the extension in the <b>options</b> is used.<br />
   * <br />
   * If the file contains a header row, include "header" in the <b>options</b>
   * parameter. If the file does not have a header row, then simply omit the
   * "header" option.<br />
   * <br />
   * Some CSV files contain newline (CR or LF) characters inside cells. This is
   * rare, but adding the "newlines" option will handle them properly. (This is
   * not enabled by default because the parsing code is much slower.)<br />
   * <br />
   * When specifying multiple options, separate them with commas, as in:
   * <b>loadTable("data.csv", "header, tsv")</b><br />
   * <br />
   * All files loaded and saved by the Processing API use UTF-8 encoding.
   *
   * @webref input:files
   * @webBrief Reads the contents of a file or URL and creates a <b>Table</b> object
   *           with its values
   * @param filename
   *          name of a file in the data folder or a URL.
   * @see Table
   * @see PApplet#saveTable(Table, String)
   * @see PApplet#loadBytes(String)
   * @see PApplet#loadStrings(String)
   * @see PApplet#loadXML(String)
   */
  public Table loadTable(String filename) {
    return loadTable(filename, null);
  }


  /**
   * Options may contain "header", "tsv", "csv", or "bin" separated by commas.
   * <p/>
   * Another option is "dictionary=filename.tsv", which allows users to
   * specify a "dictionary" file that contains a mapping of the column titles
   * and the data types used in the table file. This can be far more efficient
   * (in terms of speed and memory usage) for loading and parsing tables. The
   * dictionary file can only be tab-separated values (.tsv) and its extension
   * will be ignored. This option was added in Processing 2.0.2.
   *
   * @param options may contain "header", "tsv", "csv", or "bin" separated by commas
   */
  public Table loadTable(String filename, String options) {
    try {
      String optionStr = Table.extensionOptions(true, filename, options);
      String[] optionList = trim(split(optionStr, ','));

      for (String opt : optionList) {
        if (opt.startsWith("dictionary=")) {
          Table dictionary = loadTable(opt.substring(opt.indexOf('=') + 1), "tsv");
          return dictionary.typedParse(createInput(filename), optionStr);
        }
      }
      InputStream input = createInput(filename);
      if (input == null) {
        System.err.println(filename + " does not exist or could not be read");
        return null;
      }
      return new Table(input, optionStr);

    } catch (IOException e) {
      printStackTrace(e);
      return null;
    }
  }


  /**
   * Writes the contents of a Table object to a file. By default, this file is
   * saved to the sketch's folder. This folder is opened by selecting "Show
   * Sketch Folder" from the "Sketch" menu.<br />
   * <br />
   * Alternatively, the file can be saved to any location on the computer by
   * using an absolute path (something that starts with / on Unix and Linux, or
   * a drive letter on Windows).<br />
   * <br />
   * All files loaded and saved by the Processing API use UTF-8 encoding.
   *
   * @webref output:files
   * @webBrief Writes the contents of a <b>Table</b> object to a file
   * @param table
   *          the Table object to save to a file
   * @param filename
   *          the filename to which the Table should be saved
   * @see Table
   * @see PApplet#loadTable(String)
   */
  public boolean saveTable(Table table, String filename) {
    return saveTable(table, filename, null);
  }


  /**
   * @param options can be one of "tsv", "csv", "bin", or "html"
   */
  public boolean saveTable(Table table, String filename, String options) {
//    String ext = checkExtension(filename);
//    if (ext != null) {
//      if (ext.equals("csv") || ext.equals("tsv") || ext.equals("bin") || ext.equals("html")) {
//        if (options == null) {
//          options = ext;
//        } else {
//          options = ext + "," + options;
//        }
//      }
//    }

    try {
      // Figure out location and make sure the target path exists
      File outputFile = saveFile(filename);
      // Open a stream and take care of .gz if necessary
      return table.save(outputFile, options);

    } catch (IOException e) {
      printStackTrace(e);
      return false;
    }
  }



  //////////////////////////////////////////////////////////////

  // FONT I/O

  /**
   *
   * Loads a .vlw formatted font into a <b>PFont</b> object. Create a .vlw font
   * by selecting "Create Font..." from the Tools menu. This tool creates a
   * texture for each alphanumeric character and then adds them as a .vlw file
   * to the current sketch's data folder. Because the letters are defined as
   * textures (and not vector data) the size at which the fonts are created must
   * be considered in relation to the size at which they are drawn. For example,
   * load a 32pt font if the sketch displays the font at 32 pixels or smaller.
   * Conversely, if a 12pt font is loaded and displayed at 48pts, the letters
   * will be distorted because the program will be stretching a small graphic to
   * a large size.<br />
   * <br />
   * Like <b>loadImage()</b> and other functions that load data, the
   * <b>loadFont()</b> function should not be used inside <b>draw()</b>, because
   * it will slow down the sketch considerably, as the font will be re-loaded
   * from the disk (or network) on each frame. It's recommended to load files
   * inside <b>setup()</b><br />
   * <br />
   * To load correctly, fonts must be located in the "data" folder of the
   * current sketch. Alternatively, the file maybe be loaded from anywhere on
   * the local computer using an absolute path (something that starts with / on
   * Unix and Linux, or a drive letter on Windows), or the filename parameter
   * can be a URL for a file found on a network.<br />
   * <br />
   * If the file is not available or an error occurs, <b>null</b> will be
   * returned and an error message will be printed to the console. The error
   * message does not halt the program, however the <b>null</b> value may cause a
   * NullPointerException if your code does not check whether the value returned
   * is <b>null</b>.<br />
   * <br />
   * Use <b>createFont()</b> (instead of <b>loadFont()</b>) to enable vector
   * data to be used with the default renderer setting. This can be helpful when
   * many font sizes are needed, or when using any renderer based on the default
   * renderer, such as the PDF library.
   *
   * @webref typography:loading & displaying
   * @webBrief Loads a font into a variable of type <b>PFont</b>
   * @param filename
   *          name of the font to load
   * @see PFont
   * @see PGraphics#textFont(PFont, float)
   * @see PApplet#createFont(String, float, boolean, char[])
   */
  public PFont loadFont(String filename) {
    if (!filename.toLowerCase().endsWith(".vlw")) {
      throw new IllegalArgumentException("loadFont() is for .vlw files, try createFont()");
    }
    try {
      InputStream input = createInput(filename);
      return new PFont(input);

    } catch (Exception e) {
      die("Could not load font " + filename + ". " +
          "Make sure that the font has been copied " +
          "to the data folder of your sketch.", e);
    }
    return null;
  }


  public PFont createFont(String name, float size) {
    return createFont(name, size, true, null);
  }


  public PFont createFont(String name, float size, boolean smooth) {
    return createFont(name, size, smooth, null);
  }


  /**
   *
   * Dynamically converts a font to the format used by Processing from a .ttf or
   * .otf file inside the sketch's "data" folder or a font that's installed
   * elsewhere on the computer. If you want to use a font installed on your
   * computer, use the <b>PFont.list()</b> method to first determine the names
   * for the fonts recognized by the computer and are compatible with this
   * function. Not all fonts can be used and some might work with one operating
   * system and not others. When sharing a sketch with other people or posting
   * it on the web, you may need to include a .ttf or .otf version of your font
   * in the data directory of the sketch because other people might not have the
   * font installed on their computer. Only fonts that can legally be
   * distributed should be included with a sketch.<br />
   * <br />
   * The <b>size</b> parameter states the font size you want to generate. The
   * <b>smooth</b> parameter specifies if the font should be anti-aliased or not.
   * The <b>charset</b> parameter is an array of chars that specifies the
   * characters to generate.<br />
   * <br />
   * This function allows Processing to work with the font natively in the
   * default renderer, so the letters are defined by vector geometry and are
   * rendered quickly. In the <b>P2D</b> and <b>P3D</b> renderers, the function
   * sets the project to render the font as a series of small textures. For
   * instance, when using the default renderer, the actual native version of the
   * font will be employed by the sketch, improving drawing quality and
   * performance. With the <b>P2D</b> and <b>P3D</b> renderers, the bitmapped
   * version will be used to improve speed and appearance, but the results are
   * poor when exporting if the sketch does not include the .otf or .ttf file,
   * and the requested font is not available on the machine running the sketch.
   *
   * @webref typography:loading & displaying
   * @webBrief Dynamically converts a font to the format used by Processing
   * @param name
   *          name of the font to load
   * @param size
   *          point size of the font
   * @param smooth
   *          true for an anti-aliased font, false for aliased
   * @param charset
   *          array containing characters to be generated
   * @see PFont
   * @see PGraphics#textFont(PFont, float)
   * @see PGraphics#text(String, float, float, float, float)
   * @see PApplet#loadFont(String)
   */
  public PFont createFont(String name, float size,
                          boolean smooth, char[] charset) {
    if (g == null) {
      throw new RuntimeException("createFont() can only be used inside setup() or after setup() has been called.");
    }
    return g.createFont(name, size, smooth, charset);
  }



  //////////////////////////////////////////////////////////////

  // FILE/FOLDER SELECTION


  /**
   * Open a platform-specific file chooser dialog to select a file for input.
   * After the selection is made, the selected File will be passed to the
   * 'callback' function. If the dialog is closed or canceled, <b>null</b> will be sent
   * to the function, so that the program is not waiting for additional input.
   * The callback is necessary because of how threading works.
   *
   * <h3>Advanced</h3>
   * <pre>
   * void setup() {
   *   selectInput("Select a file to process:", "fileSelected");
   * }
   *
   * void fileSelected(File selection) {
   *   if (selection == null) {
   *     println("Window was closed or the user hit cancel.");
   *   } else {
   *     println("User selected " + fileSelected.getAbsolutePath());
   *   }
   * }
   * </pre>
   *
   * For advanced users, the method must be 'public', which is true for all
   * methods inside a sketch when run from the PDE, but must explicitly be set
   * when using Eclipse or other development environments.
   *
   * @webref input:files
   * @webBrief Open a platform-specific file chooser dialog to select a file for
   *           input
   * @param prompt
   *          message to the user
   * @param callback
   *          name of the method to be called when the selection is made
   */
  public void selectInput(String prompt, String callback) {
    selectInput(prompt, callback, null);
  }


  public void selectInput(String prompt, String callback, File file) {
    selectInput(prompt, callback, file, this);
  }


  public void selectInput(String prompt, String callback,
                          File file, Object callbackObject) {
    //selectInput(prompt, callback, file, callbackObject, null, this);
    surface.selectInput(prompt, callback, file, callbackObject);
  }


  /**
   * Opens a platform-specific file chooser dialog to select a file for output.
   * After the selection is made, the selected File will be passed to the
   * 'callback' function. If the dialog is closed or canceled, <b>null</b> will be sent
   * to the function, so that the program is not waiting for additional input.
   * The callback is necessary because of how threading works.
   *
   * @webref output:files
   * @webBrief Opens a platform-specific file chooser dialog to select a file for output
   * @param prompt message to the user
   * @param callback name of the method to be called when the selection is made
   */
  public void selectOutput(String prompt, String callback) {
    selectOutput(prompt, callback, null);
  }


  public void selectOutput(String prompt, String callback, File file) {
    selectOutput(prompt, callback, file, this);
  }


  public void selectOutput(String prompt, String callback,
                           File file, Object callbackObject) {
    //selectOutput(prompt, callback, file, callbackObject, null, this);
    surface.selectOutput(prompt, callback, file, callbackObject);
  }


  /**
   * Opens a platform-specific file chooser dialog to select a folder.
   * After the selection is made, the selection will be passed to the
   * 'callback' function. If the dialog is closed or canceled, null
   * will be sent to the function, so that the program is not waiting
   * for additional input. The callback is necessary because of how
   * threading works.
   *
   * @webref input:files
   * @webBrief Opens a platform-specific file chooser dialog to select a folder
   * @param prompt message to the user
   * @param callback name of the method to be called when the selection is made
   */
  public void selectFolder(String prompt, String callback) {
    selectFolder(prompt, callback, null);
  }


  public void selectFolder(String prompt, String callback, File file) {
    selectFolder(prompt, callback, file, this);
  }


  public void selectFolder(String prompt, String callback,
                           File file, Object callbackObject) {
    //selectFolder(prompt, callback, file, callbackObject, null, this);
    surface.selectFolder(prompt, callback, file, callbackObject);
  }


  static public void selectCallback(File selectedFile,
                                    String callbackMethod,
                                    Object callbackObject) {
    try {
      Class<?> callbackClass = callbackObject.getClass();
      Method selectMethod =
        callbackClass.getMethod(callbackMethod, File.class);
      selectMethod.invoke(callbackObject, selectedFile);

    } catch (IllegalAccessException iae) {
      System.err.println(callbackMethod + "() must be public");

    } catch (InvocationTargetException ite) {
      ite.printStackTrace();

    } catch (NoSuchMethodException nsme) {
      System.err.println(callbackMethod + "() could not be found");
    }
  }


  //////////////////////////////////////////////////////////////

  // LISTING DIRECTORIES


  public String[] listPaths(String path, String... options) {
    File[] list = listFiles(path, options);

    int offset = 0;
    for (String opt : options) {
      if (opt.equals("relative")) {
        if (!path.endsWith(File.pathSeparator)) {
          path += File.pathSeparator;
        }
        offset = path.length();
        break;
      }
    }
    String[] outgoing = new String[list.length];
    for (int i = 0; i < list.length; i++) {
      // as of Java 1.8, substring(0) returns the original object
      outgoing[i] = list[i].getAbsolutePath().substring(offset);
    }
    return outgoing;
  }


  public File[] listFiles(String path, String... options) {
    File file = new File(path);
    // if not an absolute path, make it relative to the sketch folder
    if (!file.isAbsolute()) {
      file = sketchFile(path);
    }
    return listFiles(file, options);
  }


  // "relative" -> no effect with the Files version, but important for listPaths
  // "recursive"
  // "extension=js" or "extensions=js|csv|txt" (no dot)
  // "directories" -> only directories
  // "files" -> only files
  // "hidden" -> include hidden files (prefixed with .) disabled by default
  static public File[] listFiles(File base, String... options) {
    boolean recursive = false;
    String[] extensions = null;
    boolean directories = true;
    boolean files = true;
    boolean hidden = false;

    for (String opt : options) {
      if (opt.equals("recursive")) {
        recursive = true;
      } else if (opt.startsWith("extension=")) {
        extensions = new String[] { opt.substring(10) };
      } else if (opt.startsWith("extensions=")) {
        extensions = split(opt.substring(11), ',');
      } else if (opt.equals("files")) {
        directories = false;
      } else if (opt.equals("directories")) {
        files = false;
      } else if (opt.equals("hidden")) {
        hidden = true;
      } else //noinspection StatementWithEmptyBody
        if (opt.equals("relative")) {
        // ignored
      } else {
        throw new RuntimeException(opt + " is not a listFiles() option");
      }
    }

    if (extensions != null) {
      for (int i = 0; i < extensions.length; i++) {
        extensions[i] = "." + extensions[i];
      }
    }

    if (!files && !directories) {
      // just make "only files" and "only directories" mean... both
      files = true;
      directories = true;
    }

    if (!base.canRead()) {
      return null;
    }

    List<File> outgoing = new ArrayList<>();
    listFilesImpl(base, recursive, extensions, hidden, directories, files, outgoing);
    return outgoing.toArray(new File[0]);
  }


  static private boolean listFilesExt(String name, String[] extensions) {
    for (String ext : extensions) {
      if (name.toLowerCase().endsWith(ext)) {
        return true;
      }
    }
    return false;
  }


  static void listFilesImpl(File folder, boolean recursive,
                            String[] extensions, boolean hidden,
                            boolean directories, boolean files,
                            List<File> list) {
    File[] items = folder.listFiles();
    if (items != null) {
      for (File item : items) {
        String name = item.getName();
        if (!hidden && name.charAt(0) == '.') {
          continue;
        }
        if (item.isDirectory()) {
          if (recursive) {
            listFilesImpl(item, recursive, extensions, hidden, directories, files, list);
          }
          if (directories) {
            if (extensions == null || listFilesExt(item.getName(), extensions)) {
              list.add(item);
            }
          }
        } else if (files) {
          if (extensions == null || listFilesExt(item.getName(), extensions)) {
            list.add(item);
          }
        }
      }
    }
  }



  //////////////////////////////////////////////////////////////

  // EXTENSIONS


  /**
   * Get the compression-free extension for this filename.
   * @param filename The filename to check
   * @return an extension, skipping past .gz if it's present
   */
  static public String checkExtension(String filename) {
    // Don't consider the .gz as part of the name, createInput()
    // and createOutput() will take care of fixing that up.
    if (filename.toLowerCase().endsWith(".gz")) {
      filename = filename.substring(0, filename.length() - 3);
    }
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex != -1) {
      return filename.substring(dotIndex + 1).toLowerCase();
    }
    return null;
  }



  //////////////////////////////////////////////////////////////

  // READERS AND WRITERS


  /**
   *
   * Creates a <b>BufferedReader</b> object that can be used to read files
   * line-by-line as individual <b>String</b> objects. This is the complement to
   * the <b>createWriter()</b> function. For more information about the
   * <b>BufferedReader</b> class and its methods like <b>readLine()</b> and
   * <b>close</b> used in the above example, please consult a Java
   * reference.<br />
   * <br />
   * Starting with Processing release 0134, all files loaded and saved by the
   * Processing API use UTF-8 encoding. In previous releases, the default
   * encoding for your platform was used, which causes problems when files are
   * moved to other platforms.
   *
   * @webref input:files
   * @webBrief Creates a <b>BufferedReader</b> object that can be used to read
   *           files line-by-line as individual <b>String</b> objects
   * @param filename
   *          name of the file to be opened
   * @see BufferedReader
   * @see PApplet#createWriter(String)
   * @see PrintWriter
   */
  public BufferedReader createReader(String filename) {
    InputStream is = createInput(filename);
    if (is == null) {
      System.err.println("The file \"" + filename + "\" " +
                       "is missing or inaccessible, make sure " +
                       "the URL is valid or that the file has been " +
                       "added to your sketch and is readable.");
      return null;
    }
    return createReader(is);
  }


  /**
   * @nowebref
   */
  static public BufferedReader createReader(File file) {
    try {
      InputStream is = new FileInputStream(file);
      if (file.getName().toLowerCase().endsWith(".gz")) {
        is = new GZIPInputStream(is);
      }
      return createReader(is);

    } catch (IOException e) {
      // Re-wrap rather than forcing novices to learn about exceptions
      throw new RuntimeException(e);
    }
  }


  /**
   * @nowebref
   * I want to read lines from a stream. If I have to type the
   * following lines anymore I'm gonna send Sun my medical bills.
   */
  static public BufferedReader createReader(InputStream input) {
    InputStreamReader isr =
      new InputStreamReader(input, StandardCharsets.UTF_8);

    BufferedReader reader = new BufferedReader(isr);
    // consume the Unicode BOM (byte order marker) if present
    try {
      reader.mark(1);
      int c = reader.read();
      // if not the BOM, back up to the beginning again
      if (c != '\uFEFF') {
        reader.reset();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return reader;
  }


  /**
   *
   * Creates a new file in the sketch folder, and a <b>PrintWriter</b> object
   * to write to it. For the file to be made correctly, it should be flushed
   * and must be closed with its <b>flush()</b> and <b>close()</b> methods
   * (see above example).
   * <br/> <br/>
   * Starting with Processing release 0134, all files loaded and saved by the
   * Processing API use UTF-8 encoding. In previous releases, the default
   * encoding for your platform was used, which causes problems when files
   * are moved to other platforms.
   *
   * @webref output:files
   * @webBrief Creates a new file in the sketch folder, and a <b>PrintWriter</b> object
   * to write to it
   * @param filename name of the file to be created
   * @see PrintWriter
   * @see PApplet#createReader
   * @see BufferedReader
   */
  public PrintWriter createWriter(String filename) {
    return createWriter(saveFile(filename));
  }


  /**
   * @nowebref
   * I want to print lines to a file. I have RSI from typing these
   * eight lines of code so many times.
   */
  static public PrintWriter createWriter(File file) {
    if (file == null) {
      throw new RuntimeException("File passed to createWriter() was null");
    }
    try {
      createPath(file);  // make sure in-between folders exist
      OutputStream output = new FileOutputStream(file);
      if (file.getName().toLowerCase().endsWith(".gz")) {
        output = new GZIPOutputStream(output);
      }
      return createWriter(output);

    } catch (Exception e) {
      throw new RuntimeException("Couldn't create a writer for " +
                                 file.getAbsolutePath(), e);
    }
  }

  /**
   * @nowebref
   * I want to print lines to a file. Why am I always explaining myself?
   * It's the JavaSoft API engineers who need to explain themselves.
   */
  static public PrintWriter createWriter(OutputStream output) {
    BufferedOutputStream bos = new BufferedOutputStream(output, 8192);
    OutputStreamWriter osw =
      new OutputStreamWriter(bos, StandardCharsets.UTF_8);
    return new PrintWriter(osw);
  }



  //////////////////////////////////////////////////////////////

  // FILE INPUT


  /**
   * This is a function for advanced programmers to open a Java InputStream.
   * It's useful if you want to use the facilities provided by PApplet to
   * easily open files from the data folder or from a URL, but want an
   * InputStream object so that you can use other parts of Java to take more
   * control of how the stream is read.<br />
   * <br />
   * The filename passed in can be:<br />
   * - A URL, for instance <b>openStream("http://processing.org/")</b><br />
   * - A file in the sketch's <b>data</b> folder<br />
   * - The full path to a file to be opened locally (when running as an
   * application)<br />
   * <br />
   * If the requested item doesn't exist, <b>null</b> is returned. If not online,
   * this will also check to see if the user is asking for a file whose name
   * isn't properly capitalized. If capitalization is different, an error
   * will be printed to the console. This helps prevent issues that appear
   * when a sketch is exported to the web, where case sensitivity matters, as
   * opposed to running from inside the Processing Development Environment on
   * Windows or macOS, where case sensitivity is preserved but ignored.<br />
   * <br />
   * If the file ends with <b>.gz</b>, the stream will automatically be gzip
   * decompressed. If you don't want the automatic decompression, use the
   * related function <b>createInputRaw()</b>.
   * <br />
   * In earlier releases, this function was called <b>openStream()</b>.<br />
   * <br />
   *
   *
   * <h3>Advanced</h3>
   * Simplified method to open a Java InputStream.
   * <p>
   * This method is useful if you want to use the facilities provided
   * by PApplet to easily open things from the data folder or from a URL,
   * but want an InputStream object so that you can use other Java
   * methods to take more control of how the stream is read.
   * <p>
   * If the requested item doesn't exist, null is returned.
   * (Prior to 0096, die() would be called, killing the sketch)
   * <p>
   * For 0096+, the "data" folder is exported intact with subfolders,
   * and openStream() properly handles subdirectories from the data folder
   * <p>
   * If not online, this will also check to see if the user is asking
   * for a file whose name isn't properly capitalized. This helps prevent
   * issues when a sketch is exported to the web, where case sensitivity
   * matters, as opposed to Windows and the macOS default where
   * case sensitivity is preserved but ignored.
   * <p>
   * It is strongly recommended that libraries use this method to open
   * data files, so that the loading sequence is handled in the same way
   * as functions like loadBytes(), loadImage(), etc.
   * <p>
   * The filename passed in can be:
   * <UL>
   * <LI>A URL, for instance openStream("http://processing.org/");
   * <LI>A file in the sketch's data folder
   * <LI>Another file to be opened locally (when running as an application)
   * </UL>
   *
   * @webref input:files
   * @webBrief This is a function for advanced programmers to open a Java <b>InputStream</b>
   * @param filename the name of the file to use as input
   * @see PApplet#createOutput(String)
   * @see PApplet#selectOutput(String,String)
   * @see PApplet#selectInput(String,String)
   *
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  public InputStream createInput(String filename) {
    InputStream input = createInputRaw(filename);
    if (input != null) {
      // if it's gzip-encoded, automatically decode
      final String lower = filename.toLowerCase();
      if (lower.endsWith(".gz") || lower.endsWith(".svgz")) {
        try {
          // buffered has to go *around* the GZ, otherwise 25x slower
          return new BufferedInputStream(new GZIPInputStream(input));

        } catch (IOException e) {
          printStackTrace(e);
        }
      } else {
        return new BufferedInputStream(input);
      }
    }
    return null;
  }


  /**
   * Call openStream() without automatic gzip decompression.
   */
  public InputStream createInputRaw(String filename) {
    if (filename == null) return null;

    if (sketchPath == null) {
      System.err.println("The sketch path is not set.");
      throw new RuntimeException("Files must be loaded inside setup() or after it has been called.");
    }

    if (filename.length() == 0) {
      // an error will be called by the parent function
      //System.err.println("The filename passed to openStream() was empty.");
      return null;
    }

    // First check whether this looks like a URL
    if (filename.contains(":")) {  // at least smells like URL
      try {
        URL url = new URL(filename);
        URLConnection conn = url.openConnection();

        if (conn instanceof HttpURLConnection httpConn) {
          // Will not handle a protocol change (see below)
          httpConn.setInstanceFollowRedirects(true);
          int response = httpConn.getResponseCode();
          // Default won't follow HTTP -> HTTPS redirects for security reasons
          // http://stackoverflow.com/a/1884427
          if (response >= 300 && response < 400) {
            String newLocation = httpConn.getHeaderField("Location");
            return createInputRaw(newLocation);
          }
          return conn.getInputStream();
        } else if (conn instanceof JarURLConnection) {
          return url.openStream();
        }
      } catch (MalformedURLException mfue) {
        // not a URL, that's fine

      } catch (FileNotFoundException fnfe) {
        // Added in 0119 b/c Java 1.5 throws FNFE when URL not available.
        // https://download.processing.org/bugzilla/403.html

      } catch (IOException e) {
        // changed for 0117, shouldn't be throwing exception
        printStackTrace(e);
        //System.err.println("Error downloading from URL " + filename);
        return null;
        //throw new RuntimeException("Error downloading from URL " + filename);
      }
    }

    InputStream stream;

    // Moved this earlier than the getResourceAsStream() checks, because
    // calling getResourceAsStream() on a directory lists its contents.
    // https://download.processing.org/bugzilla/716.html
    try {
      // First see if it's in a data folder. This may fail by throwing
      // a SecurityException. If so, this whole block will be skipped.
      File file = new File(dataPath(filename));
      if (!file.exists()) {
        // next see if it's just in the sketch folder
        file = sketchFile(filename);
      }

      if (file.isDirectory()) {
        return null;
      }
      if (file.exists()) {
        try {
          // handle case sensitivity check
          String filePath = file.getCanonicalPath();
          String filenameActual = new File(filePath).getName();
          // make sure there isn't a subfolder prepended to the name
          String filenameShort = new File(filename).getName();
          // if the actual filename is the same, but capitalized
          // differently, warn the user.
          //if (filenameActual.equalsIgnoreCase(filenameShort) &&
          //!filenameActual.equals(filenameShort)) {
          if (!filenameActual.equals(filenameShort)) {
            throw new RuntimeException("This file is named " +
                                       filenameActual + " not " +
                                       filename + ". Rename the file " +
                                       "or change your code.");
          }
        } catch (IOException ignored) { }
      }

      // if this file is ok, may as well just load it
      return new FileInputStream(file);

      // have to break these out because a general Exception might
      // catch the RuntimeException being thrown above
    } catch (IOException | SecurityException ignored) { }

    // Using getClassLoader() prevents java from converting dots
    // to slashes or requiring a slash at the beginning.
    // (a slash as a prefix means that it'll load from the root of
    // the jar, rather than trying to dig into the package location)
    ClassLoader cl = getClass().getClassLoader();

    // by default, data files are exported to the root path of the jar.
    // (not the data folder) so check there first.
    stream = cl.getResourceAsStream("data/" + filename);
    if (stream != null) {
      String cn = stream.getClass().getName();
      // this is an irritation of sun's java plug-in, which will return
      // a non-null stream for an object that doesn't exist. like all good
      // things, this is probably introduced in java 1.5. awesome!
      // https://download.processing.org/bugzilla/359.html
      if (!cn.equals("sun.plugin.cache.EmptyInputStream")) {
        return stream;
      }
    }

    // When used with an online script, also need to check without the
    // data folder, in case it's not in a subfolder called 'data'.
    // https://download.processing.org/bugzilla/389.html
    stream = cl.getResourceAsStream(filename);
    if (stream != null) {
      String cn = stream.getClass().getName();
      if (!cn.equals("sun.plugin.cache.EmptyInputStream")) {
        return stream;
      }
    }

    try {
      // attempt to load from a local file
      try {  // first try to catch any security exceptions
        try {
          return new FileInputStream(dataPath(filename));
        } catch (IOException ignored) { }

        try {
          return new FileInputStream(sketchPath(filename));
        } catch (Exception ignored) { }

        try {
          return new FileInputStream(filename);
        } catch (IOException ignored) { }

      } catch (SecurityException ignored) { }  // online, whups

    } catch (Exception e) {
      printStackTrace(e);
    }

    return null;
  }


  /**
   * @nowebref
   */
  static public InputStream createInput(File file) {
    if (file == null) {
      throw new IllegalArgumentException("File passed to createInput() was null");
    }
    if (!file.exists()) {
      System.err.println(file + " does not exist, createInput() will return null");
      return null;
    }
    try {
      InputStream input = new FileInputStream(file);
      final String lower = file.getName().toLowerCase();
      if (lower.endsWith(".gz") || lower.endsWith(".svgz")) {
        return new BufferedInputStream(new GZIPInputStream(input));
      }
      return new BufferedInputStream(input);

    } catch (IOException e) {
      System.err.println("Could not createInput() for " + file);
      e.printStackTrace();
      return null;
    }
  }


  /**
   *
   * Reads the contents of a file and places it in a byte array. If the name of
   * the file is used as the parameter, as in the above example, the file must
   * be loaded in the sketch's "data" directory/folder. <br />
   * <br />
   * Alternatively, the file maybe be loaded from anywhere on the local computer
   * using an absolute path (something that starts with / on Unix and Linux, or
   * a drive letter on Windows), or the filename parameter can be a URL for a
   * file found on a network.<br />
   * <br />
   * If the file is not available or an error occurs, <b>null</b> will be
   * returned and an error message will be printed to the console. The error
   * message does not halt the program, however the <b>null</b> value may cause a
   * NullPointerException if your code does not check whether the value returned
   * is <b>null</b>.<br />
   *
   * @webref input:files
   * @webBrief Reads the contents of a file or url and places it in a byte
   *           array
   * @param filename
   *          name of a file in the data folder or a URL.
   * @see PApplet#loadStrings(String)
   * @see PApplet#saveStrings(String, String[])
   * @see PApplet#saveBytes(String, byte[])
   *
   */
  public byte[] loadBytes(String filename) {
    String lower = filename.toLowerCase();
    // If it's not a .gz file, then we might be able to uncompress it into
    // a fixed-size buffer, which should help speed because we won't have to
    // reallocate and resize the target array each time it gets full.
    if (!lower.endsWith(".gz")) {
      // If this looks like a URL, try to load it that way. Use the fact that
      // URL connections may have a content length header to size the array.
      if (filename.contains(":")) {  // at least smells like URL
        InputStream input = null;
        try {
          URL url = new URL(filename);
          URLConnection conn = url.openConnection();
          int length = -1;

          if (conn instanceof HttpURLConnection httpConn) {
            // Will not handle a protocol change (see below)
            httpConn.setInstanceFollowRedirects(true);
            int response = httpConn.getResponseCode();
            // Default won't follow HTTP -> HTTPS redirects for security reasons
            // http://stackoverflow.com/a/1884427
            if (response >= 300 && response < 400) {
              String newLocation = httpConn.getHeaderField("Location");
              return loadBytes(newLocation);
            }
            length = conn.getContentLength();
            input = conn.getInputStream();
          } else if (conn instanceof JarURLConnection) {
            length = conn.getContentLength();
            input = url.openStream();
          }

          if (input != null) {
            byte[] buffer;
            if (length != -1) {
              buffer = new byte[length];
              int count;
              int offset = 0;
              while ((count = input.read(buffer, offset, length - offset)) > 0) {
                offset += count;
              }
            } else {
              buffer = loadBytes(input);
            }
            input.close();
            return buffer;
          }
        } catch (MalformedURLException mfue) {
          // not a url, that's fine

        } catch (FileNotFoundException fnfe) {
          // Java 1.5+ throws FNFE when URL not available
          // https://download.processing.org/bugzilla/403.html

        } catch (IOException e) {
          printStackTrace(e);
          return null;

        } finally {
          if (input != null) {
            try {
              input.close();
            } catch (IOException e) {
              // just deal
            }
          }
        }
      }
    }

    InputStream is = createInput(filename);
    if (is != null) {
      byte[] outgoing = loadBytes(is);
      try {
        is.close();
      } catch (IOException e) {
        printStackTrace(e);  // shouldn't happen
      }
      return outgoing;
    }

    System.err.println("The file \"" + filename + "\" " +
                       "is missing or inaccessible, make sure " +
                       "the URL is valid or that the file has been " +
                       "added to your sketch and is readable.");
    return null;
  }


  /**
   * @nowebref
   */
  static public byte[] loadBytes(InputStream input) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];

      int bytesRead = input.read(buffer);
      while (bytesRead != -1) {
        out.write(buffer, 0, bytesRead);
        bytesRead = input.read(buffer);
      }
      out.flush();
      return out.toByteArray();

    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }


  /**
   * @nowebref
   */
  static public byte[] loadBytes(File file) {
    if (!file.exists()) {
      System.err.println(file + " does not exist, loadBytes() will return null");
      return null;
    }

    try {
      InputStream input;
      int length;

      if (file.getName().toLowerCase().endsWith(".gz")) {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek(raf.length() - 4);
        int b4 = raf.read();
        int b3 = raf.read();
        int b2 = raf.read();
        int b1 = raf.read();
        length = (b1 << 24) | (b2 << 16) + (b3 << 8) + b4;
        raf.close();

        // buffered has to go *around* the GZ, otherwise 25x slower
        input = new BufferedInputStream(new GZIPInputStream(new FileInputStream(file)));

      } else {
        long len = file.length();
        // http://stackoverflow.com/a/3039805
        int maxArraySize = Integer.MAX_VALUE - 5;
        if (len > maxArraySize) {
          System.err.println("Cannot use loadBytes() on a file larger than " + maxArraySize);
          return null;
        }
        length = (int) len;
        input = new BufferedInputStream(new FileInputStream(file));
      }
      byte[] buffer = new byte[length];
      int count;
      int offset = 0;
      // count will come back 0 when complete (or -1 if somehow going long?)
      while ((count = input.read(buffer, offset, length - offset)) > 0) {
        offset += count;
      }
      input.close();
      return buffer;

    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }


  /**
   * @nowebref
   */
  static public String[] loadStrings(File file) {
    if (!file.exists()) {
      System.err.println(file + " does not exist, loadStrings() will return null");
      return null;
    }

    InputStream is = createInput(file);
    if (is != null) {
      String[] outgoing = loadStrings(is);
      try {
        is.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      return outgoing;
    }
    return null;
  }


  /**
   *
   * Reads the contents of a file and creates a String array of its individual
   * lines. If the name of the file is used as the parameter, as in the above
   * example, the file must be loaded in the sketch's "data" directory/folder.
   * <br />
   * <br />
   * Alternatively, the file maybe be loaded from anywhere on the local computer
   * using an absolute path (something that starts with / on Unix and Linux, or
   * a drive letter on Windows), or the filename parameter can be a URL for a
   * file found on a network.<br />
   * <br />
   * If the file is not available or an error occurs, <b>null</b> will be
   * returned and an error message will be printed to the console. The error
   * message does not halt the program, however the <b>null</b> value may cause a
   * NullPointerException if your code does not check whether the value returned
   * is <b>null</b>.<br />
   * <br />
   * Starting with Processing release 0134, all files loaded and saved by the
   * Processing API use UTF-8 encoding. In previous releases, the default
   * encoding for your platform was used, which causes problems when files are
   * moved to other platforms.
   *
   * <h3>Advanced</h3> Load data from a file and shove it into a String array.
   * <p>
   * Exceptions are handled internally, when an error, occurs, an exception is
   * printed to the console and 'null' is returned, but the program continues
   * running. This is a tradeoff between 1) showing the user that there was a
   * problem but 2) not requiring that all i/o code is contained in try/catch
   * blocks, for the sake of new users (or people who are just trying to get
   * things done in an informal "scripting" fashion). If you want to handle
   * exceptions, use Java methods for I/O.
   *
   * @webref input:files
   * @webBrief Reads the contents of a file or url and creates a <b>String</b> array of
   *           its individual lines
   * @param filename
   *          name of the file or url to load
   * @see PApplet#loadBytes(String)
   * @see PApplet#saveStrings(String, String[])
   * @see PApplet#saveBytes(String, byte[])
   */
  public String[] loadStrings(String filename) {
    InputStream is = createInput(filename);
    if (is != null) {
      String[] strArr = loadStrings(is);
      try {
        is.close();
      } catch (IOException e) {
        printStackTrace(e);
      }
      return strArr;
    }

    System.err.println("The file \"" + filename + "\" " +
                       "is missing or inaccessible, make sure " +
                       "the URL is valid or that the file has been " +
                       "added to your sketch and is readable.");
    return null;
  }

  /**
   * @nowebref
   */
  static public String[] loadStrings(InputStream input) {
    BufferedReader reader =
      new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    return loadStrings(reader);
  }


  static public String[] loadStrings(BufferedReader reader) {
    try {
      String[] lines = new String[100];
      int lineCount = 0;
      String line;
      while ((line = reader.readLine()) != null) {
        if (lineCount == lines.length) {
          String[] temp = new String[lineCount << 1];
          System.arraycopy(lines, 0, temp, 0, lineCount);
          lines = temp;
        }
        lines[lineCount++] = line;
      }
      reader.close();

      if (lineCount == lines.length) {
        return lines;
      }

      // resize array to appropriate amount for these lines
      String[] output = new String[lineCount];
      System.arraycopy(lines, 0, output, 0, lineCount);
      return output;

    } catch (IOException e) {
      e.printStackTrace();
      //throw new RuntimeException("Error inside loadStrings()");
    }
    return null;
  }



  //////////////////////////////////////////////////////////////

  // FILE OUTPUT


  /**
   *
   * Similar to <b>createInput()</b>, this creates a Java <b>OutputStream</b>
   * for a given filename or path. The file will be created in the sketch
   * folder, or in the same folder as an exported application. <br />
   * <br />
   * If the path does not exist, intermediate folders will be created. If an
   * exception occurs, it will be printed to the console, and <b>null</b> will
   * be returned. <br />
   * <br />
   * This function is a convenience over the Java approach that requires you to
   * 1) create a FileOutputStream object, 2) determine the exact file location,
   * and 3) handle exceptions. Exceptions are handled internally by the
   * function, which is more appropriate for "sketch" projects. <br />
   * <br />
   * If the output filename ends with <b>.gz</b>, the output will be
   * automatically GZIP compressed as it is written.
   *
   * @webref output:files
   * @webBrief Similar to <b>createInput()</b>, this creates a Java
   *           <b>OutputStream</b> for a given filename or path
   * @param filename
   *          name of the file to open
   * @see PApplet#createInput(String)
   * @see PApplet#selectOutput(String,String)
   */
  public OutputStream createOutput(String filename) {
    return createOutput(saveFile(filename));
  }

  /**
   * @nowebref
   */
  static public OutputStream createOutput(File file) {
    try {
      createPath(file);  // make sure the path exists
      OutputStream output = new FileOutputStream(file);
      if (file.getName().toLowerCase().endsWith(".gz")) {
        return new BufferedOutputStream(new GZIPOutputStream(output));
      }
      return new BufferedOutputStream(output);

    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }


  /**
   *
   * Save the contents of a stream to a file in the sketch folder. This is
   * basically <b>saveBytes(blah, loadBytes())</b>, but done more efficiently
   * (and with less confusing syntax).<br />
   * <br />
   * The <b>target</b> parameter can be either a String specifying a file name,
   * or, for greater control over the file location, a <b>File</b> object. (Note
   * that, unlike some other functions, this will not automatically compress or
   * uncompress gzip files.)
   *
   * @webref output:files
   * @webBrief Save the contents of a stream to a file in the sketch folder
   * @param target
   *          name of the file to write to
   * @param source
   *          location to read from (a filename, path, or URL)
   * @see PApplet#createOutput(String)
   */
  public boolean saveStream(String target, String source) {
    return saveStream(saveFile(target), source);
  }

  /**
   * Identical to the other saveStream(), but writes to a File
   * object, for greater control over the file location.
   * <p/>
   * Note that unlike other api methods, this will not automatically
   * compress or uncompress gzip files.
   */
  public boolean saveStream(File target, String source) {
    return saveStream(target, createInputRaw(source));
  }

  /**
   * @nowebref
   */
  public boolean saveStream(String target, InputStream source) {
    return saveStream(saveFile(target), source);
  }

  /**
   * @nowebref
   */
  static public boolean saveStream(File target, InputStream source) {
    File tempFile = null;
    try {
      // make sure that this path actually exists before writing
      createPath(target);
      tempFile = createTempFile(target);
      FileOutputStream targetStream = new FileOutputStream(tempFile);

      saveStream(targetStream, source);
      targetStream.close();

      if (target.exists()) {
        if (!target.delete()) {
          System.err.println("Could not replace " + target);
        }
      }
      if (!tempFile.renameTo(target)) {
        System.err.println("Could not rename temporary file " + tempFile);
        return false;
      }
      return true;

    } catch (IOException e) {
      if (tempFile != null) {
        if (!tempFile.delete()) {
          System.err.println("Could not rename temporary file " + tempFile);
        }
      }
      e.printStackTrace();
      return false;
    }
  }

  /**
   * @nowebref
   */
  static public void saveStream(OutputStream target,
                                InputStream source) throws IOException {
    BufferedInputStream bis = new BufferedInputStream(source, 16384);
    BufferedOutputStream bos = new BufferedOutputStream(target);

    byte[] buffer = new byte[8192];
    int bytesRead;
    while ((bytesRead = bis.read(buffer)) != -1) {
      bos.write(buffer, 0, bytesRead);
    }

    bos.flush();
  }


  /**
   *
   * As the opposite of <b>loadBytes()</b>, this function will write an entire
   * array of bytes to a file. The data is saved in binary format. This file is
   * saved to the sketch's folder, which is opened by selecting "Show Sketch
   * Folder" from the "Sketch" menu. Alternatively, the files can be saved to
   * any location on the computer by using an absolute path (something that
   * starts with / on Unix and Linux, or a drive letter on Windows).
   *
   * @webref output:files
   * @webBrief Opposite of <b>loadBytes()</b>, will write an entire array of
   *           bytes to a file
   * @param filename
   *          name of the file to write to
   * @param data
   *          array of bytes to be written
   * @see PApplet#loadStrings(String)
   * @see PApplet#loadBytes(String)
   * @see PApplet#saveStrings(String, String[])
   */
  public void saveBytes(String filename, byte[] data) {
    saveBytes(saveFile(filename), data);
  }


  /**
   * Creates a temporary file based on the name/extension of another file
   * and in the same parent directory. Ensures that the same extension is used
   * (i.e. so that .gz files are gzip compressed on output) and that it's done
   * from the same directory so that renaming the file later won't cross file
   * system boundaries.
   */
  static private File createTempFile(File file) throws IOException {
    File parentDir = file.getParentFile();
    if (!parentDir.exists()) {
      if (!parentDir.mkdirs()) {
        throw new IOException("Could not make directories for " + parentDir);
      }
    }
    String name = file.getName();
    String prefix;
    String suffix = null;
    int dot = name.lastIndexOf('.');
    if (dot == -1) {
      prefix = name;
    } else {
      // preserve the extension so that .gz works properly
      prefix = name.substring(0, dot);
      suffix = name.substring(dot);
    }
    // Prefix must be three characters
    if (prefix.length() < 3) {
      prefix += "processing";
    }
    return File.createTempFile(prefix, suffix, parentDir);
  }


  /**
   * @nowebref
   * Saves bytes to a specific File location specified by the user.
   */
  static public void saveBytes(File file, byte[] data) {
    File tempFile = null;
    try {
      tempFile = createTempFile(file);

      OutputStream output = createOutput(tempFile);
      if (output != null) {
        saveBytes(output, data);
        output.close();
      } else {
        System.err.println("Could not write to " + tempFile);
      }

      if (file.exists()) {
        if (!file.delete()) {
          System.err.println("Could not replace " + file);
        }
      }

      if (!tempFile.renameTo(file)) {
        System.err.println("Could not rename temporary file " + tempFile);
      }

    } catch (IOException e) {
      System.err.println("error saving bytes to " + file);
      if (tempFile != null) {
        if (!tempFile.delete()) {
          System.err.println("Could not delete temporary file " + tempFile);
        }
      }
      e.printStackTrace();
    }
  }


  /**
   * @nowebref
   * Spews a buffer of bytes to an OutputStream.
   */
  static public void saveBytes(OutputStream output, byte[] data) {
    try {
      output.write(data);
      output.flush();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  //

  /**
   *
   * Writes an array of Strings to a file, one line per String. By default, this
   * file is saved to the sketch's folder. This folder is opened by selecting
   * "Show Sketch Folder" from the "Sketch" menu.<br />
   * <br />
   * Alternatively, the file can be saved to any location on the computer by
   * using an absolute path (something that starts with / on Unix and Linux, or
   * a drive letter on Windows).<br />
   * <br />
   * Starting with Processing 1.0, all files loaded and saved by the Processing
   * API use UTF-8 encoding. In earlier releases, the default encoding for your
   * platform was used, which causes problems when files are moved to other
   * platforms.
   *
   * @webref output:files
   * @webBrief Writes an array of strings to a file, one line per string
   * @param filename
   *          filename for output
   * @param data
   *          string array to be written
   * @see PApplet#loadStrings(String)
   * @see PApplet#loadBytes(String)
   * @see PApplet#saveBytes(String, byte[])
   */
  public void saveStrings(String filename, String[] data) {
    saveStrings(saveFile(filename), data);
  }


  /**
   * @nowebref
   */
  static public void saveStrings(File file, String[] data) {
    saveStrings(createOutput(file), data);
  }


  /**
   * @nowebref
   */
  static public void saveStrings(OutputStream output, String[] data) {
    PrintWriter writer = createWriter(output);
    for (String item : data) {
      writer.println(item);
    }
    writer.flush();
    writer.close();
  }


  //////////////////////////////////////////////////////////////


  static protected String calcSketchPath() {
    // try to get the user folder. if running under java web start,
    // this may cause a security exception if the code is not signed.
    // http://processing.org/discourse/yabb_beta/YaBB.cgi?board=Integrate;action=display;num=1159386274
    String folder = null;
    try {
      folder = System.getProperty("user.dir");

      URL jarURL =
          PApplet.class.getProtectionDomain().getCodeSource().getLocation();
      // Decode URL
      String jarPath = jarURL.toURI().getSchemeSpecificPart();

      // Workaround for bug in Java for OS X from Oracle (7u51)
      // https://github.com/processing/processing/issues/2181
      if (platform == MACOS) {
        if (jarPath.contains("Contents/Java/")) {
          String appPath = jarPath.substring(0, jarPath.indexOf(".app") + 4);
          File containingFolder = new File(appPath).getParentFile();
          folder = containingFolder.getAbsolutePath();
        }
      } else {
        // Working directory may not be set properly, try some options
        // https://github.com/processing/processing/issues/2195
        if (jarPath.contains("/lib/")) {
          // Windows or Linux, back up a directory to get the executable
          folder = new File(jarPath, "../..").getCanonicalPath();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return folder;
  }


  public String sketchPath() {
    if (sketchPath == null) {
      sketchPath = calcSketchPath();
    }
    return sketchPath;
  }


  /**
   * Prepend the sketch folder path to the filename (or path) that is
   * passed in. External libraries should use this function to save to
   * the sketch folder.
   * <p/>
   * This will also cause an error if the sketch is not inited properly,
   * meaning that init() was never called on the PApplet when hosted
   * my some other main() or by other code. For proper use of init(),
   * see the examples in the main description text for PApplet.
   */
  public String sketchPath(String where) {
    if (sketchPath() == null) {
      return where;
    }
    // isAbsolute() could throw an access exception, but so will writing
    // to the local disk using the sketch path, so this is safe here.
    // for 0120, added a try/catch anyways.
    try {
      if (new File(where).isAbsolute()) {
        return where;
      }
    } catch (Exception e) {
      // do nothing
    }
    return sketchPath() + File.separator + where;
  }


  public File sketchFile(String where) {
    return new File(sketchPath(where));
  }


  /**
   * Returns a path adjacent the application to save to. Like sketchPath(),
   * but creates any in-between folders so that things save properly.
   * <p/>
   * All saveXxxx() functions use the path to the sketch folder, rather than
   * its data folder. Once exported, the data folder will be found inside the
   * jar file of the exported application. In this case, it's not
   * possible to save data into the jar file, because it will often be running
   * from a server, or marked in-use if running from a local file system.
   * With this in mind, saving to the data path doesn't make sense anyway.
   * If you know you're running locally, and want to save to the data folder,
   * use <TT>saveXxxx("data/blah.dat")</TT>.
   */
  public String savePath(String where) {
    if (where == null) return null;
    String filename = sketchPath(where);
    createPath(filename);
    return filename;
  }


  /**
   * Identical to savePath(), but returns a File object.
   */
  public File saveFile(String where) {
    return new File(savePath(where));
  }


  static File desktopFolder;

  static public File desktopFile(String what) {
    if (desktopFolder == null) {
      // Should work on Linux and OS X (on OS X, even with the localized version).
      desktopFolder = new File(System.getProperty("user.home"), "Desktop");
      if (!desktopFolder.exists()) {
        if (platform == WINDOWS && !disableAWT) {
          desktopFolder = ShimAWT.getWindowsDesktop();
        } else {
          throw new UnsupportedOperationException("Could not find a suitable Desktop folder");
        }
      }
    }
    return new File(desktopFolder, what);
  }


  static public String desktopPath(String what) {
    return desktopFile(what).getAbsolutePath();
  }


  /**
   * <b>This function almost certainly does not do the thing you want it to.</b>
   * The data path is handled differently on each platform, and should not be
   * considered a location to write files. It should also not be assumed that
   * this location can be read from or listed. This function is used internally
   * as a possible location for reading files. It's still "public" as a
   * holdover from earlier code.
   * <p>
   * Libraries should use createInput() to get an InputStream or createOutput()
   * to get an OutputStream. sketchPath() can be used to get a location
   * relative to the sketch. Again, <b>do not</b> use this to get relative
   * locations of files. You'll be disappointed when your app runs on different
   * platforms.
   */
  public String dataPath(String where) {
    return dataFile(where).getAbsolutePath();
  }


  /**
   * Return a full path to an item in the data folder as a File object.
   * See the dataPath() method for more information.
   */
  public File dataFile(String where) {
    // isAbsolute() could throw an access exception, but so will writing
    // to the local disk using the sketch path, so this is safe here.
    File why = new File(where);
    if (why.isAbsolute()) return why;

    URL jarURL = getClass().getProtectionDomain().getCodeSource().getLocation();
    // Decode URL
    String jarPath;
    try {
      jarPath = jarURL.toURI().getPath();
    } catch (URISyntaxException e) {
      e.printStackTrace();
      return null;
    }
    if (jarPath.contains("Contents/Java/")) {
      File containingFolder = new File(jarPath).getParentFile();
      File dataFolder = new File(containingFolder, "data");
      return new File(dataFolder, where);
    }
    // Windows, Linux, or when not using a Mac OS X .app file
    return new File(sketchPath + File.separator + "data" + File.separator + where);
  }


  /**
   * Takes a path and creates any in-between folders if they don't
   * already exist. Useful when trying to save to a subfolder that
   * may not actually exist.
   */
  static public void createPath(String path) {
    createPath(new File(path));
  }


  static public void createPath(File file) {
    try {
      String parent = file.getParent();
      if (parent != null) {
        File unit = new File(parent);
        if (!unit.exists()) {
          boolean result = unit.mkdirs();
          if (!result) {
            System.err.println("Could not create " + unit);
          }
        }
      }
    } catch (SecurityException se) {
      System.err.println("You don't have permissions to create " +
                         file.getAbsolutePath());
    }
  }


  static public String getExtension(String filename) {
    String extension;

    String lower = filename.toLowerCase();
    int dot = filename.lastIndexOf('.');
    if (dot == -1) {
      return "";  // no extension found
    }
    extension = lower.substring(dot + 1);

    // check for, and strip any parameters on the url, i.e.
    // filename.jpg?blah=blah&something=that
    int question = extension.indexOf('?');
    if (question != -1) {
      extension = extension.substring(0, question);
    }

    return extension;
  }


  //////////////////////////////////////////////////////////////

  // URL ENCODING


  static public String urlEncode(String str) {
    return URLEncoder.encode(str, StandardCharsets.UTF_8);
  }


  // DO NOT use for file paths, URLDecoder can't handle RFC2396
  // "The recommended way to manage the encoding and decoding of
  // URLs is to use URI, and to convert between these two classes
  // using toURI() and URI.toURL()."
  // https://docs.oracle.com/javase/8/docs/api/java/net/URL.html
  static public String urlDecode(String str) {
    return URLDecoder.decode(str, StandardCharsets.UTF_8);
  }



  //////////////////////////////////////////////////////////////

  // SORT


  /**
   *
   * Sorts an array of numbers from smallest to largest, or puts an array of
   * words in alphabetical order. The original array is not modified; a
   * re-ordered array is returned. The <b>count</b> parameter states the number
   * of elements to sort. For example, if there are 12 elements in an array and
   * <b>count</b> is set to 5, only the first 5 elements in the array will be
   * sorted. <!--As of release 0126, the alphabetical ordering is case
   * insensitive.-->
   *
   * @webref data:array functions
   * @webBrief Sorts an array of numbers from smallest to largest and puts an
   *           array of words in alphabetical order
   * @param list
   *          array to sort
   * @see PApplet#reverse(boolean[])
   */
  static public byte[] sort(byte[] list) {
    return sort(list, list.length);
  }

  /**
        * @param count number of elements to sort, starting from 0
   */
  static public byte[] sort(byte[] list, int count) {
    byte[] outgoing = new byte[list.length];
    System.arraycopy(list, 0, outgoing, 0, list.length);
    Arrays.sort(outgoing, 0, count);
    return outgoing;
  }

  static public char[] sort(char[] list) {
    return sort(list, list.length);
  }

  static public char[] sort(char[] list, int count) {
    char[] outgoing = new char[list.length];
    System.arraycopy(list, 0, outgoing, 0, list.length);
    Arrays.sort(outgoing, 0, count);
    return outgoing;
  }

  static public int[] sort(int[] list) {
    return sort(list, list.length);
  }

  static public int[] sort(int[] list, int count) {
    int[] outgoing = new int[list.length];
    System.arraycopy(list, 0, outgoing, 0, list.length);
    Arrays.sort(outgoing, 0, count);
    return outgoing;
  }

  static public float[] sort(float[] list) {
    return sort(list, list.length);
  }

  static public float[] sort(float[] list, int count) {
    float[] outgoing = new float[list.length];
    System.arraycopy(list, 0, outgoing, 0, list.length);
    Arrays.sort(outgoing, 0, count);
    return outgoing;
  }

  static public String[] sort(String[] list) {
    return sort(list, list.length);
  }

  static public String[] sort(String[] list, int count) {
    String[] outgoing = new String[list.length];
    System.arraycopy(list, 0, outgoing, 0, list.length);
    Arrays.sort(outgoing, 0, count);
    return outgoing;
  }



  //////////////////////////////////////////////////////////////

  // ARRAY UTILITIES


  /**
   *
   * Copies an array (or part of an array) to another array. The <b>src</b>
   * array is copied to the <b>dst</b> array, beginning at the position
   * specified by <b>srcPosition</b> and into the position specified by
   * <b>dstPosition</b>. The number of elements to copy is determined by
   * <b>length</b>. Note that copying values overwrites existing values in the
   * destination array. To append values instead of overwriting them, use
   * <b>concat()</b>.<br />
   * <br />
   * The simplified version with only two arguments &mdash; <b>arrayCopy(src,
   * dst)</b> &mdash; copies an entire array to another of the same size. It is
   * equivalent to <b>arrayCopy(src, 0, dst, 0, src.length)</b>.<br />
   * <br />
   * Using this function is far more efficient for copying array data than
   * iterating through a <b>for()</b> loop and copying each element
   * individually. This function only copies references, which means that for
   * most purposes it only copies one-dimensional arrays (a single set of
   * brackets). If used with a two (or three or more) dimensional array, it will
   * only copy the references at the first level, because a two-dimensional
   * array is simply an "array of arrays". This does not produce an error,
   * however, because this is often the desired behavior. Internally, this
   * function calls Java's <a href=
   * "https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#arraycopy-java.lang.Object-int-java.lang.Object-int-int-">System.arraycopy()</a>
   * method, so most things that apply there are inherited.
   *
   * @webref data:array functions
   * @webBrief Copies an array (or part of an array) to another array
   * @param src
   *          the source array
   * @param srcPosition
   *          starting position in the source array
   * @param dst
   *          the destination array of the same data type as the source array
   * @param dstPosition
   *          starting position in the destination array
   * @param length
   *          number of array elements to be copied
   * @see PApplet#concat(boolean[], boolean[])
   */
  @SuppressWarnings("SuspiciousSystemArraycopy")
  static public void arrayCopy(Object src, int srcPosition,
                               Object dst, int dstPosition,
                               int length) {
    System.arraycopy(src, srcPosition, dst, dstPosition, length);
  }

  /**
   * Convenience method for arraycopy().
   * Identical to <CODE>arraycopy(src, 0, dst, 0, length);</CODE>
   */
  @SuppressWarnings("SuspiciousSystemArraycopy")
  static public void arrayCopy(Object src, Object dst, int length) {
    System.arraycopy(src, 0, dst, 0, length);
  }

  /**
   * Shortcut to copy the entire contents of
   * the source into the destination array.
   * Identical to <CODE>arraycopy(src, 0, dst, 0, src.length);</CODE>
   */
  @SuppressWarnings("SuspiciousSystemArraycopy")
  static public void arrayCopy(Object src, Object dst) {
    System.arraycopy(src, 0, dst, 0, Array.getLength(src));
  }

  /**
   * Use arrayCopy() instead.
   */
  @SuppressWarnings("SuspiciousSystemArraycopy")
  @Deprecated
  static public void arraycopy(Object src, int srcPosition,
                               Object dst, int dstPosition,
                               int length) {
    System.arraycopy(src, srcPosition, dst, dstPosition, length);
  }

  /**
   * Use arrayCopy() instead.
   */
  @SuppressWarnings("SuspiciousSystemArraycopy")
  @Deprecated
  static public void arraycopy(Object src, Object dst, int length) {
    System.arraycopy(src, 0, dst, 0, length);
  }

  /**
   * Use arrayCopy() instead.
   */
  @SuppressWarnings("SuspiciousSystemArraycopy")
  @Deprecated
  static public void arraycopy(Object src, Object dst) {
    System.arraycopy(src, 0, dst, 0, Array.getLength(src));
  }


  /**
   *
   * Increases the size of a one-dimensional array. By default, this function
   * doubles the size of the array, but the optional <b>newSize</b> parameter
   * provides precise control over the increase in size.
   * <p/>
   * When using an array of objects, the data returned from the function must be
   * cast to the object array's data type. For example: <em>SomeClass[] items =
   * (SomeClass[]) expand(originalArray)</em>
   *
   * @webref data:array functions
   * @webBrief Increases the size of an array
   * @param list
   *          the array to expand
   * @see PApplet#shorten(boolean[])
   */
  static public boolean[] expand(boolean[] list) {
    return expand(list, list.length > 0 ? list.length << 1 : 1);
  }

  /**
   * @param newSize new size for the array
   */
  static public boolean[] expand(boolean[] list, int newSize) {
    boolean[] temp = new boolean[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

  static public byte[] expand(byte[] list) {
    return expand(list, list.length > 0 ? list.length << 1 : 1);
  }

  static public byte[] expand(byte[] list, int newSize) {
    byte[] temp = new byte[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

  static public char[] expand(char[] list) {
    return expand(list, list.length > 0 ? list.length << 1 : 1);
  }

  static public char[] expand(char[] list, int newSize) {
    char[] temp = new char[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

  static public int[] expand(int[] list) {
    return expand(list, list.length > 0 ? list.length << 1 : 1);
  }

  static public int[] expand(int[] list, int newSize) {
    int[] temp = new int[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

  static public long[] expand(long[] list) {
    return expand(list, list.length > 0 ? list.length << 1 : 1);
  }

  static public long[] expand(long[] list, int newSize) {
    long[] temp = new long[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

  static public float[] expand(float[] list) {
    return expand(list, list.length > 0 ? list.length << 1 : 1);
  }

  static public float[] expand(float[] list, int newSize) {
    float[] temp = new float[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

  static public double[] expand(double[] list) {
    return expand(list, list.length > 0 ? list.length << 1 : 1);
  }

  static public double[] expand(double[] list, int newSize) {
    double[] temp = new double[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

  static public String[] expand(String[] list) {
    return expand(list, list.length > 0 ? list.length << 1 : 1);
  }

  static public String[] expand(String[] list, int newSize) {
    String[] temp = new String[newSize];
    // in case the new size is smaller than list.length
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

 /**
  * @nowebref
  */
  static public Object expand(Object array) {
    int len = Array.getLength(array);
    return expand(array, len > 0 ? len << 1 : 1);
  }

  @SuppressWarnings("SuspiciousSystemArraycopy")
  static public Object expand(Object list, int newSize) {
    Class<?> type = list.getClass().getComponentType();
    Object temp = Array.newInstance(type, newSize);
    System.arraycopy(list, 0, temp, 0,
                     Math.min(Array.getLength(list), newSize));
    return temp;
  }

  // contract() has been removed in revision 0124, use subset() instead.
  // (expand() is also functionally equivalent)

  /**
   *
   * Expands an array by one element and adds data to the new position. The
   * datatype of the <b>element</b> parameter must be the same as the
   * datatype of the array.
   * <br/> <br/>
   * When using an array of objects, the data returned from the function must
   * be cast to the object array's data type. For example: <em>SomeClass[]
   * items = (SomeClass[]) append(originalArray, element)</em>.
   *
   * @webref data:array functions
   * @webBrief Expands an array by one element and adds data to the new position
   * @param array array to append
   * @param value new data for the array
   * @see PApplet#shorten(boolean[])
   * @see PApplet#expand(boolean[])
   */
  static public byte[] append(byte[] array, byte value) {
    array = expand(array, array.length + 1);
    array[array.length-1] = value;
    return array;
  }

  static public char[] append(char[] array, char value) {
    array = expand(array, array.length + 1);
    array[array.length-1] = value;
    return array;
  }

  static public int[] append(int[] array, int value) {
    array = expand(array, array.length + 1);
    array[array.length-1] = value;
    return array;
  }

  static public float[] append(float[] array, float value) {
    array = expand(array, array.length + 1);
    array[array.length-1] = value;
    return array;
  }

  static public String[] append(String[] array, String value) {
    array = expand(array, array.length + 1);
    array[array.length-1] = value;
    return array;
  }

  static public Object append(Object array, Object value) {
    int length = Array.getLength(array);
    array = expand(array, length + 1);
    Array.set(array, length, value);
    return array;
  }


 /**
   *
   * Decreases an array by one element and returns the shortened array.
   * <br/> <br/>
   * When using an array of objects, the data returned from the function must
   * be cast to the object array's data type. For example: <em>SomeClass[]
   * items = (SomeClass[]) shorten(originalArray)</em>.
   *
   * @webref data:array functions
   * @webBrief Decreases an array by one element and returns the shortened array
   * @param list array to shorten
   * @see PApplet#append(byte[], byte)
   * @see PApplet#expand(boolean[])
   */
  static public boolean[] shorten(boolean[] list) {
    return subset(list, 0, list.length-1);
  }

  static public byte[] shorten(byte[] list) {
    return subset(list, 0, list.length-1);
  }

  static public char[] shorten(char[] list) {
    return subset(list, 0, list.length-1);
  }

  static public int[] shorten(int[] list) {
    return subset(list, 0, list.length-1);
  }

  static public float[] shorten(float[] list) {
    return subset(list, 0, list.length-1);
  }

  static public String[] shorten(String[] list) {
    return subset(list, 0, list.length-1);
  }

  static public Object shorten(Object list) {
    int length = Array.getLength(list);
    return subset(list, 0, length - 1);
  }


  /**
   *
   * Inserts a value or an array of values into an existing array. The first two
   * parameters must be arrays of the same datatype. The first parameter
   * specifies the initial array to be modified, and the second parameter
   * defines the data to be inserted. The third parameter is an index value
   * which specifies the array position from which to insert data. (Remember
   * that array index numbering starts at zero, so the first position is 0, the
   * second position is 1, and so on.)<br />
   * <br />
   * When splicing an array of objects, the data returned from the function must
   * be cast to the object array's data type. For example: <em>SomeClass[] items
   * = (SomeClass[]) splice(array1, array2, index)</em>
   *
   * @webref data:array functions
   * @webBrief Inserts a value or array of values into an existing array
   * @param list
   *          array to splice into
   * @param value
   *          value to be spliced in
   * @param index
   *          position in the array from which to insert data
   * @see PApplet#concat(boolean[], boolean[])
   * @see PApplet#subset(boolean[], int, int)
   */
  static final public boolean[] splice(boolean[] list,
                                       boolean value, int index) {
    boolean[] outgoing = new boolean[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = value;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public boolean[] splice(boolean[] list,
                                       boolean[] value, int index) {
    boolean[] outgoing = new boolean[list.length + value.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(value, 0, outgoing, index, value.length);
    System.arraycopy(list, index, outgoing, index + value.length,
                     list.length - index);
    return outgoing;
  }

  static final public byte[] splice(byte[] list,
                                    byte value, int index) {
    byte[] outgoing = new byte[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = value;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public byte[] splice(byte[] list,
                                    byte[] value, int index) {
    byte[] outgoing = new byte[list.length + value.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(value, 0, outgoing, index, value.length);
    System.arraycopy(list, index, outgoing, index + value.length,
                     list.length - index);
    return outgoing;
  }


  static final public char[] splice(char[] list,
                                    char value, int index) {
    char[] outgoing = new char[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = value;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public char[] splice(char[] list,
                                    char[] value, int index) {
    char[] outgoing = new char[list.length + value.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(value, 0, outgoing, index, value.length);
    System.arraycopy(list, index, outgoing, index + value.length,
                     list.length - index);
    return outgoing;
  }

  static final public int[] splice(int[] list,
                                   int value, int index) {
    int[] outgoing = new int[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = value;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public int[] splice(int[] list,
                                   int[] value, int index) {
    int[] outgoing = new int[list.length + value.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(value, 0, outgoing, index, value.length);
    System.arraycopy(list, index, outgoing, index + value.length,
                     list.length - index);
    return outgoing;
  }

  static final public float[] splice(float[] list,
                                     float value, int index) {
    float[] outgoing = new float[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = value;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public float[] splice(float[] list,
                                     float[] value, int index) {
    float[] outgoing = new float[list.length + value.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(value, 0, outgoing, index, value.length);
    System.arraycopy(list, index, outgoing, index + value.length,
                     list.length - index);
    return outgoing;
  }

  static final public String[] splice(String[] list,
                                      String value, int index) {
    String[] outgoing = new String[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = value;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public String[] splice(String[] list,
                                      String[] value, int index) {
    String[] outgoing = new String[list.length + value.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(value, 0, outgoing, index, value.length);
    System.arraycopy(list, index, outgoing, index + value.length,
                     list.length - index);
    return outgoing;
  }

  @SuppressWarnings("SuspiciousSystemArraycopy")
  static final public Object splice(Object list, Object value, int index) {
    Class<?> type = list.getClass().getComponentType();
    Object outgoing;
    int length = Array.getLength(list);

    // check whether item being spliced in is an array
    if (value.getClass().getName().charAt(0) == '[') {
      int vlength = Array.getLength(value);
      outgoing = Array.newInstance(type, length + vlength);
      System.arraycopy(list, 0, outgoing, 0, index);
      System.arraycopy(value, 0, outgoing, index, vlength);
      System.arraycopy(list, index, outgoing, index + vlength, length - index);

    } else {
      outgoing = Array.newInstance(type, length + 1);
      System.arraycopy(list, 0, outgoing, 0, index);
      Array.set(outgoing, index, value);
      System.arraycopy(list, index, outgoing, index + 1, length - index);
    }
    return outgoing;
  }


  static public boolean[] subset(boolean[] list, int start) {
    return subset(list, start, list.length - start);
  }


 /**
  *
  * Extracts an array of elements from an existing array. The <b>list</b>
  * parameter defines the array from which the elements will be copied, and the
  * <b>start</b> and <b>count</b> parameters specify which elements to extract.
  * If no <b>count</b> is given, elements will be extracted from the
  * <b>start</b> to the end of the array. When specifying the <b>start</b>,
  * remember that the first array element is 0. This function does not change
  * the source array.<br />
  * <br />
  * When using an array of objects, the data returned from the function must be
  * cast to the object array's data type. For example: <em>SomeClass[] items =
  * (SomeClass[]) subset(originalArray, 0, 4)</em>
  *
  * @webref data:array functions
  * @webBrief Extracts an array of elements from an existing array
  * @param list
  *          array to extract from
  * @param start
  *          position to begin
  * @param count
  *          number of values to extract
  * @see PApplet#splice(boolean[], boolean, int)
  */
  static public boolean[] subset(boolean[] list, int start, int count) {
    boolean[] output = new boolean[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public byte[] subset(byte[] list, int start) {
    return subset(list, start, list.length - start);
  }


  static public byte[] subset(byte[] list, int start, int count) {
    byte[] output = new byte[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public char[] subset(char[] list, int start) {
    return subset(list, start, list.length - start);
  }


  static public char[] subset(char[] list, int start, int count) {
    char[] output = new char[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public int[] subset(int[] list, int start) {
    return subset(list, start, list.length - start);
  }


  static public int[] subset(int[] list, int start, int count) {
    int[] output = new int[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public long[] subset(long[] list, int start) {
    return subset(list, start, list.length - start);
  }


  static public long[] subset(long[] list, int start, int count) {
    long[] output = new long[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public float[] subset(float[] list, int start) {
    return subset(list, start, list.length - start);
  }


  static public float[] subset(float[] list, int start, int count) {
    float[] output = new float[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public double[] subset(double[] list, int start) {
    return subset(list, start, list.length - start);
  }


  static public double[] subset(double[] list, int start, int count) {
    double[] output = new double[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public String[] subset(String[] list, int start) {
    return subset(list, start, list.length - start);
  }


  static public String[] subset(String[] list, int start, int count) {
    String[] output = new String[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public Object subset(Object list, int start) {
    int length = Array.getLength(list);
    return subset(list, start, length - start);
  }


  @SuppressWarnings("SuspiciousSystemArraycopy")
  static public Object subset(Object list, int start, int count) {
    Class<?> type = list.getClass().getComponentType();
    Object outgoing = Array.newInstance(type, count);
    System.arraycopy(list, start, outgoing, 0, count);
    return outgoing;
  }


 /**
  *
  * Concatenates two arrays. For example, concatenating the array { 1, 2, 3 }
  * and the array { 4, 5, 6 } yields { 1, 2, 3, 4, 5, 6 }. Both parameters must
  * be arrays of the same datatype. <br />
  * <br />
  * When using an array of objects, the data returned from the function must be
  * cast to the object array's data type. For example: <em>SomeClass[] items =
  * (SomeClass[]) concat(array1, array2)</em>.
  *
  * @webref data:array functions
  * @webBrief Concatenates two arrays
  * @param a
  *          first array to concatenate
  * @param b
  *          second array to concatenate
  * @see PApplet#splice(boolean[], boolean, int)
  * @see PApplet#arrayCopy(Object, int, Object, int, int)
  */
  static public boolean[] concat(boolean[] a, boolean[] b) {
    boolean[] c = new boolean[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  static public byte[] concat(byte[] a, byte[] b) {
    byte[] c = new byte[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  static public char[] concat(char[] a, char[] b) {
    char[] c = new char[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  static public int[] concat(int[] a, int[] b) {
    int[] c = new int[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  static public float[] concat(float[] a, float[] b) {
    float[] c = new float[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  static public String[] concat(String[] a, String[] b) {
    String[] c = new String[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  @SuppressWarnings("SuspiciousSystemArraycopy")
  static public Object concat(Object a, Object b) {
    Class<?> type = a.getClass().getComponentType();
    int alength = Array.getLength(a);
    int blength = Array.getLength(b);
    Object outgoing = Array.newInstance(type, alength + blength);
    System.arraycopy(a, 0, outgoing, 0, alength);
    System.arraycopy(b, 0, outgoing, alength, blength);
    return outgoing;
  }

  //


 /**
   *
   * Reverses the order of an array.
   *
  * @webref data:array functions
  * @webBrief Reverses the order of an array
  * @param list booleans[], bytes[], chars[], ints[], floats[], or Strings[]
  * @see PApplet#sort(String[], int)
  */
  static public boolean[] reverse(boolean[] list) {
    boolean[] outgoing = new boolean[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public byte[] reverse(byte[] list) {
    byte[] outgoing = new byte[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public char[] reverse(char[] list) {
    char[] outgoing = new char[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public int[] reverse(int[] list) {
    int[] outgoing = new int[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public float[] reverse(float[] list) {
    float[] outgoing = new float[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public String[] reverse(String[] list) {
    String[] outgoing = new String[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public Object reverse(Object list) {
    Class<?> type = list.getClass().getComponentType();
    int length = Array.getLength(list);
    Object outgoing = Array.newInstance(type, length);
    for (int i = 0; i < length; i++) {
      Array.set(outgoing, i, Array.get(list, (length - 1) - i));
    }
    return outgoing;
  }



  //////////////////////////////////////////////////////////////

  // STRINGS


  /**
   *
   * Removes whitespace characters from the beginning and end of a String. In
   * addition to standard whitespace characters such as space, carriage
   * return, and tab, this function also removes the Unicode "nbsp" (U+00A0)
   * character and the zero width no-break space (U+FEFF) character.
   *
   * @webref data:string_functions
   * @webBrief Removes whitespace characters from the beginning and end of a <b>String</b>
   * @param str any string
   * @see PApplet#split(String, String)
   * @see PApplet#join(String[], char)
   */
  static public String trim(String str) {
    if (str == null) {
      return null;
    }
    // remove nbsp *and* zero width no-break space
    return str.replace('\u00A0', ' ').replace('\uFEFF', ' ').trim();
  }


 /**
  * @param array a String array
  */
  static public String[] trim(String[] array) {
    if (array == null) {
      return null;
    }
    String[] outgoing = new String[array.length];
    for (int i = 0; i < array.length; i++) {
      if (array[i] != null) {
        outgoing[i] = trim(array[i]);
      }
    }
    return outgoing;
  }


  /**
   *
   * Combines an array of Strings into one String, each separated by the
   * character(s) used for the <b>separator</b> parameter. To join arrays of
   * ints or floats, it's necessary to first convert them to Strings using
   * <b>nf()</b> or <b>nfs()</b>.
   *
   * @webref data:string_functions
   * @webBrief Combines an array of <b>Strings</b> into one <b>String</b>, each separated by the
   * character(s) used for the <b>separator</b> parameter
   * @param list array of Strings
   * @param separator char or String to be placed between each item
   * @see PApplet#split(String, String)
   * @see PApplet#trim(String)
   * @see PApplet#nf(float, int, int)
   * @see PApplet#nfs(float, int, int)
   */
  static public String join(String[] list, char separator) {
    return join(list, String.valueOf(separator));
  }


  static public String join(String[] list, String separator) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.length; i++) {
      if (i != 0) sb.append(separator);
      sb.append(list[i]);
    }
    return sb.toString();
  }


  static public String[] splitTokens(String value) {
    return splitTokens(value, WHITESPACE);
  }


  /**
   *
   * The <b>splitTokens()</b> function splits a <b>String</b> at one or many character
   * delimiters or "tokens". The <b>delim</b> parameter specifies the character
   * or characters to be used as a boundary.<br />
   * <br />
   * If no <b>delim</b> characters are specified, any whitespace character is
   * used to split. Whitespace characters include tab (&#92;t), line feed
   * (&#92;n), carriage return (&#92;r), form feed (&#92;f), and space.<br />
   * <br />
   * After using this function to parse incoming data, it is common to convert
   * the data from Strings to integers or floats by using the datatype
   * conversion functions <b>int()</b> and <b>float()</b>.
   *
   * @webref data:string_functions
   * @webBrief The <b>splitTokens()</b> function splits a <b>String</b> at one or many
   *           character "tokens"
   * @param value
   *          the String to be split
   * @param delim
   *          list of individual characters that will be used as separators
   * @see PApplet#split(String, String)
   * @see PApplet#join(String[], String)
   * @see PApplet#trim(String)
   */
  static public String[] splitTokens(String value, String delim) {
    StringTokenizer toker = new StringTokenizer(value, delim);
    String[] pieces = new String[toker.countTokens()];

    int index = 0;
    while (toker.hasMoreTokens()) {
      pieces[index++] = toker.nextToken();
    }
    return pieces;
  }


  /**
   *
   * The <b>split()</b> function breaks a String into pieces using a character
   * or string as the delimiter. The <b>delim</b> parameter specifies the
   * character or characters that mark the boundaries between each piece. A
   * String[] array is returned that contains each of the pieces. <br />
   * <br />
   * If the result is a set of numbers, you can convert the String[] array to a
   * float[] or int[] array using the datatype conversion functions <b>int()</b>
   * and <b>float()</b>. (See the second example above.) <br />
   * <br />
   * The <b>splitTokens()</b> function works in a similar fashion, except that
   * it splits using a range of characters instead of a specific character or
   * sequence. <!-- <br />
   * <br />
   * This function uses regular expressions to determine how the <b>delim</b>
   * parameter divides the <b>str</b> parameter. Therefore, if you use
   * characters such parentheses and brackets that are used with regular
   * expressions as a part of the <b>delim</b> parameter, you'll need to put two
   * backslashes (\\\\) in front of the character (see example above). You can
   * read more about
   * <a href="http://en.wikipedia.org/wiki/Regular_expression">regular
   * expressions</a> and
   * <a href="http://en.wikipedia.org/wiki/Escape_character">escape
   * characters</a> on Wikipedia. -->
   *
   * @webref data:string_functions
   * @webBrief The <b>split()</b> function breaks a string into pieces using a
   *           character or string as the divider
   * @usage web_application
   * @param value
   *          the String to be split
   * @param delim
   *          the character or String used to separate the data
   */
  static public String[] split(String value, char delim) {
    // do this so that the exception occurs inside the user's
    // program, rather than appearing to be a bug inside split()
    if (value == null) return null;
    //return split(what, String.valueOf(delim));  // huh

    char[] chars = value.toCharArray();
    int splitCount = 0; //1;
    for (char ch : chars) {
      if (ch == delim) splitCount++;
    }
    // make sure that there is something in the input string
    //if (chars.length > 0) {
      // if the last char is a delimiter, get rid of it..
      //if (chars[chars.length-1] == delim) splitCount--;
      // on second thought, i don't agree with this, will disable
    //}
    if (splitCount == 0) {
      String[] splits = new String[1];
      splits[0] = value;
      return splits;
    }
    //int pieceCount = splitCount + 1;
    String[] splits = new String[splitCount + 1];
    int splitIndex = 0;
    int startIndex = 0;
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] == delim) {
        splits[splitIndex++] =
          new String(chars, startIndex, i-startIndex);
        startIndex = i + 1;
      }
    }
    //if (startIndex != chars.length) {
      splits[splitIndex] =
        new String(chars, startIndex, chars.length-startIndex);
    //}
    return splits;
  }


  static public String[] split(String value, String delim) {
    List<String> items = new ArrayList<>();
    int index;
    int offset = 0;
    while ((index = value.indexOf(delim, offset)) != -1) {
      items.add(value.substring(offset, index));
      offset = index + delim.length();
    }
    items.add(value.substring(offset));
    String[] outgoing = new String[items.size()];
    items.toArray(outgoing);
    return outgoing;
  }


  static protected LinkedHashMap<String, Pattern> matchPatterns;

  static Pattern matchPattern(String regexp) {
    Pattern p = null;
    if (matchPatterns == null) {
      matchPatterns = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
          // Limit the number of match patterns at 10 most recently used
          return size() == 10;
        }
      };
    } else {
      p = matchPatterns.get(regexp);
    }
    if (p == null) {
      p = Pattern.compile(regexp, Pattern.MULTILINE | Pattern.DOTALL);
      matchPatterns.put(regexp, p);
    }
    return p;
  }


  /**
   *
   * This function is used to apply a regular expression to a piece of text, and
   * return matching groups (elements found inside parentheses) as a String
   * array. If there are no matches, a <b>null</b> value will be returned. If no groups
   * are specified in the regular expression, but the sequence matches, an array
   * of length 1 (with the matched text as the first element of the array) will
   * be returned.<br />
   * <br />
   * To use the function, first check to see if the result is <b>null</b>. If the
   * result is null, then the sequence did not match at all. If the sequence did
   * match, an array is returned.<br />
   * <br />
   * If there are groups (specified by sets of parentheses) in the regular
   * expression, then the contents of each will be returned in the array.
   * Element [0] of a regular expression match returns the entire matching
   * string, and the match groups start at element [1] (the first group is [1],
   * the second [2], and so on).<br />
   * <br />
   * The syntax can be found in the reference for Java's <a href=
   * "https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html">Pattern</a>
   * class. For regular expression syntax, read the
   * <a href="https://docs.oracle.com/javase/tutorial/essential/regex/">Java
   * Tutorial</a> on the topic.
   *
   * @webref data:string_functions
   * @webBrief The function is used to apply a regular expression to a
   *           piece of text, and return matching groups (elements found inside
   *           parentheses) as a <b>String</b> array
   * @param str
   *          the String to be searched
   * @param regexp
   *          the regexp to be used for matching
   * @see PApplet#matchAll(String, String)
   * @see PApplet#split(String, String)
   * @see PApplet#splitTokens(String, String)
   * @see PApplet#join(String[], String)
   * @see PApplet#trim(String)
   */
  static public String[] match(String str, String regexp) {
    Pattern p = matchPattern(regexp);
    Matcher m = p.matcher(str);
    if (m.find()) {
      int count = m.groupCount() + 1;
      String[] groups = new String[count];
      for (int i = 0; i < count; i++) {
        groups[i] = m.group(i);
      }
      return groups;
    }
    return null;
  }


  /**
   *
   * This function is used to apply a regular expression to a piece of text,
   * and return a list of matching groups (elements found inside parentheses)
   * as a two-dimensional String array. If there are no matches, a <b>null</b>
   * value will be returned. If no groups are specified in the regular
   * expression, but the sequence matches, a two-dimensional array is still
   * returned, but the second dimension is only of length one.<br />
   * <br />
   * To use the function, first check to see if the result is <b>null</b>. If the
   * result is null, then the sequence did not match at all. If the sequence did
   * match, a 2D array is returned.<br />
   * <br />
   * If there are groups (specified by sets of parentheses) in the regular
   * expression, then the contents of each will be returned in the array.
   * Assuming a loop with counter variable i, element [i][0] of a regular
   * expression match returns the entire matching string, and the match groups
   * start at element [i][1] (the first group is [i][1], the second [i][2], and
   * so on).<br />
   * <br />
   * The syntax can be found in the reference for Java's <a href=
   * "https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html">Pattern</a>
   * class. For regular expression syntax, read the
   * <a href="https://docs.oracle.com/javase/tutorial/essential/regex/">Java
   * Tutorial</a> on the topic.
   *
   * @webref data:string_functions
   * @webBrief This function is used to apply a regular expression to a piece of
   *           text
   * @param str
   *          the String to be searched
   * @param regexp
   *          the regexp to be used for matching
   * @see PApplet#match(String, String)
   * @see PApplet#split(String, String)
   * @see PApplet#splitTokens(String, String)
   * @see PApplet#join(String[], String)
   * @see PApplet#trim(String)
   */
  static public String[][] matchAll(String str, String regexp) {
    Pattern p = matchPattern(regexp);
    Matcher m = p.matcher(str);
    List<String[]> results = new ArrayList<>();
    int count = m.groupCount() + 1;
    while (m.find()) {
      String[] groups = new String[count];
      for (int i = 0; i < count; i++) {
        groups[i] = m.group(i);
      }
      results.add(groups);
    }
    if (results.isEmpty()) {
      return null;
    }
    String[][] matches = new String[results.size()][count];
    for (int i = 0; i < matches.length; i++) {
      matches[i] = results.get(i);
    }
    return matches;
  }



  //////////////////////////////////////////////////////////////

  // CASTING FUNCTIONS, INSERTED BY PREPROC


  /**
   * <p>Convert an integer to a boolean. Because of how Java handles upgrading
   * numbers, this will also cover byte and char (as they will upgrade to
   * an int without any sort of explicit cast).</p>
   * <p>The preprocessor will convert boolean(what) to parseBoolean(what).</p>
   * @return false if 0, true if any other number
   */
  static final public boolean parseBoolean(int what) {
    return (what != 0);
  }

  /**
   * Convert the string "true" or "false" to a boolean.
   * @return true if 'what' is "true" or "TRUE", false otherwise
   */
  static final public boolean parseBoolean(String what) {
    return Boolean.parseBoolean(what);
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  /**
   * Convert an int array to a boolean array. An int equal
   * to zero will return false, and any other value will return true.
   * @return array of boolean elements
   */
  static final public boolean[] parseBoolean(int[] what) {
    boolean[] outgoing = new boolean[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (what[i] != 0);
    }
    return outgoing;
  }

  static final public boolean[] parseBoolean(String[] what) {
    boolean[] outgoing = new boolean[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = Boolean.parseBoolean(what[i]);
    }
    return outgoing;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public byte parseByte(boolean what) {
    return what ? (byte)1 : 0;
  }

  static final public byte parseByte(char what) {
    return (byte) what;
  }

  static final public byte parseByte(int what) {
    return (byte) what;
  }

  static final public byte parseByte(float what) {
    return (byte) what;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public byte[] parseByte(boolean[] what) {
    byte[] outgoing = new byte[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = what[i] ? (byte)1 : 0;
    }
    return outgoing;
  }

  static final public byte[] parseByte(char[] what) {
    byte[] outgoing = new byte[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (byte) what[i];
    }
    return outgoing;
  }

  static final public byte[] parseByte(int[] what) {
    byte[] outgoing = new byte[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (byte) what[i];
    }
    return outgoing;
  }

  static final public byte[] parseByte(float[] what) {
    byte[] outgoing = new byte[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (byte) what[i];
    }
    return outgoing;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public char parseChar(byte what) {
    return (char) (what & 0xff);
  }

  static final public char parseChar(int what) {
    return (char) what;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public char[] parseChar(byte[] what) {
    char[] outgoing = new char[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (char) (what[i] & 0xff);
    }
    return outgoing;
  }

  static final public char[] parseChar(int[] what) {
    char[] outgoing = new char[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (char) what[i];
    }
    return outgoing;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public int parseInt(boolean what) {
    return what ? 1 : 0;
  }

  /**
   * Note that parseInt() will un-sign a signed byte value.
   */
  static final public int parseInt(byte what) {
    return what & 0xff;
  }

  /**
   * Note that parseInt('5') is unlike String in the sense that it
   * won't return 5, but the ascii value. This is because ((int) someChar)
   * returns the ascii value, and parseInt() is just longhand for the cast.
   */
  static final public int parseInt(char what) {
    return what;
  }

  /**
   * Same as floor(), or an (int) cast.
   */
  static final public int parseInt(float what) {
    return (int) what;
  }

  /**
   * Parse a String into an int value. Returns 0 if the value is bad.
   */
  static final public int parseInt(String what) {
    return parseInt(what, 0);
  }

  /**
   * Parse a String to an int, and provide an alternate value that
   * should be used when the number is invalid. If there's a decimal place,
   * it will be truncated, making this more of a toInt() than parseInt()
   * function. This is because the method is used internally for casting.
   * Not ideal, but the name was chosen before that clarification was made.
   */
  static final public int parseInt(String what, int otherwise) {
    try {
      int offset = what.indexOf('.');
      if (offset == -1) {
        return Integer.parseInt(what);
      } else {
        return Integer.parseInt(what.substring(0, offset));
      }
    } catch (NumberFormatException e) {
      return otherwise;
    }
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public int[] parseInt(boolean[] what) {
    int[] list = new int[what.length];
    for (int i = 0; i < what.length; i++) {
      list[i] = what[i] ? 1 : 0;
    }
    return list;
  }

  static final public int[] parseInt(byte[] what) {  // note this un-signs
    int[] list = new int[what.length];
    for (int i = 0; i < what.length; i++) {
      list[i] = (what[i] & 0xff);
    }
    return list;
  }

  static final public int[] parseInt(char[] what) {
    int[] list = new int[what.length];
    for (int i = 0; i < what.length; i++) {
      list[i] = what[i];
    }
    return list;
  }

  static public int[] parseInt(float[] what) {
    int[] inties = new int[what.length];
    for (int i = 0; i < what.length; i++) {
      inties[i] = (int)what[i];
    }
    return inties;
  }

  /**
   * Make an array of int elements from an array of String objects.
   * If the String can't be parsed as a number, it will be set to zero.
   * <pre>
   * String s[] = { "1", "300", "44" };
   * int numbers[] = parseInt(s);
   * // numbers will contain { 1, 300, 44 }
   * </pre>
   */
  static public int[] parseInt(String[] what) {
    return parseInt(what, 0);
  }

  /**
   * Make an array of int elements from an array of String objects.
   * If the String can't be parsed as a number, its entry in the
   * array will be set to the value of the "missing" parameter.
   * <pre>
   * String s[] = { "1", "300", "apple", "44" };
   * int numbers[] = parseInt(s, 9999);
   * // numbers will contain { 1, 300, 9999, 44 }
   * </pre>
   */
  static public int[] parseInt(String[] what, int missing) {
    int[] output = new int[what.length];
    for (int i = 0; i < what.length; i++) {
      try {
        output[i] = Integer.parseInt(what[i]);
      } catch (NumberFormatException e) {
        output[i] = missing;
      }
    }
    return output;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  /**
   * Convert an int to a float value. Also handles bytes because of
   * Java's rules for upgrading values.
   */
  static final public float parseFloat(int what) {  // also handles byte
    return what;
  }

  static final public float parseFloat(String what) {
    return parseFloat(what, Float.NaN);
  }

  static final public float parseFloat(String what, float otherwise) {
    try {
      return Float.parseFloat(what);
    } catch (NumberFormatException ignored) { }

    return otherwise;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public float[] parseFloat(byte[] what) {
    float[] floaties = new float[what.length];
    for (int i = 0; i < what.length; i++) {
      floaties[i] = what[i];
    }
    return floaties;
  }

  static final public float[] parseFloat(int[] what) {
    float[] floaties = new float[what.length];
    for (int i = 0; i < what.length; i++) {
      floaties[i] = what[i];
    }
    return floaties;
  }

  static final public float[] parseFloat(String[] what) {
    return parseFloat(what, Float.NaN);
  }

  static final public float[] parseFloat(String[] what, float missing) {
    float[] output = new float[what.length];
    for (int i = 0; i < what.length; i++) {
      try {
        output[i] = Float.parseFloat(what[i]);
      } catch (NumberFormatException e) {
        output[i] = missing;
      }
    }
    return output;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public String str(boolean value) {
    return String.valueOf(value);
  }

  static final public String str(byte value) {
    return String.valueOf(value);
  }

  static final public String str(char value) {
    return String.valueOf(value);
  }

  static final public String str(int value) {
    return String.valueOf(value);
  }

  static final public String str(float value) {
    return String.valueOf(value);
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public String[] str(boolean[] values) {
    String[] s = new String[values.length];
    for (int i = 0; i < values.length; i++) s[i] = String.valueOf(values[i]);
    return s;
  }

  static final public String[] str(byte[] values) {
    String[] s = new String[values.length];
    for (int i = 0; i < values.length; i++) s[i] = String.valueOf(values[i]);
    return s;
  }

  static final public String[] str(char[] values) {
    String[] s = new String[values.length];
    for (int i = 0; i < values.length; i++) s[i] = String.valueOf(values[i]);
    return s;
  }

  static final public String[] str(int[] values) {
    String[] s = new String[values.length];
    for (int i = 0; i < values.length; i++) s[i] = String.valueOf(values[i]);
    return s;
  }

  static final public String[] str(float[] values) {
    String[] s = new String[values.length];
    for (int i = 0; i < values.length; i++) s[i] = String.valueOf(values[i]);
    return s;
  }


  //////////////////////////////////////////////////////////////

  // INT NUMBER FORMATTING


  static public String nf(float num) {
    int inum = (int) num;
    if (num == inum) {
      return str(inum);
    }
    return str(num);
  }


  static public String[] nf(float[] nums) {
    String[] outgoing = new String[nums.length];
    for (int i = 0; i < nums.length; i++) {
      outgoing[i] = nf(nums[i]);
    }
    return outgoing;
  }


  /**
   * Integer number formatter.
   */
  static private NumberFormat int_nf;
  static private int int_nf_digits;
  static private boolean int_nf_commas;


  /**
   * Utility function for formatting numbers into strings. There are two
   * versions: one for formatting floats, and one for formatting ints. The
   * values for the <b>digits</b> and <b>right</b> parameters should always be
   * positive integers. The <b>left</b> parameter should be positive or 0. If it
   * is zero, only the right side is formatted.<br />
   * <br />
   * As shown in the above example, <b>nf()</b> is used to add zeros to the left
   * and/or right of a number. This is typically for aligning a list of numbers.
   * To <em>remove</em> digits from a floating-point number, use the
   * <b>int()</b>, <b>ceil()</b>, <b>floor()</b>, or <b>round()</b> functions.
   *
   * @webref data:string_functions
   * @webBrief Utility function for formatting numbers into strings
   * @param nums
   *          the numbers to format
   * @param digits
   *          number of digits to pad with zero
   * @see PApplet#nfs(float, int, int)
   * @see PApplet#nfp(float, int, int)
   * @see PApplet#nfc(float, int)
   * @see <a href=
   *      "https://processing.org/reference/intconvert_.html">int(float)</a>
   */
  static public String[] nf(int[] nums, int digits) {
    String[] formatted = new String[nums.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nf(nums[i], digits);
    }
    return formatted;
  }


  /**
   * @param num the number to format
   */
  static public String nf(int num, int digits) {
    if ((int_nf != null) &&
        (int_nf_digits == digits) &&
        !int_nf_commas) {
      return int_nf.format(num);
    }

    int_nf = NumberFormat.getInstance();
    int_nf.setGroupingUsed(false); // no commas
    int_nf_commas = false;
    int_nf.setMinimumIntegerDigits(digits);
    int_nf_digits = digits;
    return int_nf.format(num);
  }


  /**
   * Utility function for formatting numbers into strings and placing
   * appropriate commas to mark units of 1000. There are four versions: one for
   * formatting ints, one for formatting an array of ints, one for formatting
   * floats, and one for formatting an array of floats.<br />
   * <br />
   * The value for the <b>right</b> parameter should always be a positive
   * integer.<br />
   * <br />
   * For a non-US locale, this will insert periods instead of commas,
   * or whatever is appropriate for that region.
   *
   * @webref data:string_functions
   * @webBrief Utility function for formatting numbers into strings and placing
   *           appropriate commas to mark units of 1000
   * @param nums
   *          the numbers to format
   * @see PApplet#nf(float, int, int)
   * @see PApplet#nfp(float, int, int)
   * @see PApplet#nfs(float, int, int)
   */
  static public String[] nfc(int[] nums) {
    String[] formatted = new String[nums.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfc(nums[i]);
    }
    return formatted;
  }


  /**
   * @param num the number to format
   */
  static public String nfc(int num) {
    if ((int_nf != null) &&
        (int_nf_digits == 0) &&
        int_nf_commas) {
      return int_nf.format(num);
    }

    int_nf = NumberFormat.getInstance();
    int_nf.setGroupingUsed(true);
    int_nf_commas = true;
    int_nf.setMinimumIntegerDigits(0);
    int_nf_digits = 0;
    return int_nf.format(num);
  }


  /**
   * Utility function for formatting numbers into strings. Similar to
   * <b>nf()</b> but leaves a blank space in front of positive numbers, so
   * they align with negative numbers in spite of the minus symbol. There are
   * two versions, one for formatting floats and one for formatting ints. The
   * values for the <b>digits</b>, <b>left</b>, and <b>right</b> parameters
   * should always be positive integers.
   *
   * @webref data:string_functions
   * @webBrief Utility function for formatting numbers into strings
   * @param num the number to format
   * @param digits number of digits to pad with zeroes
   * @see PApplet#nf(float, int, int)
   * @see PApplet#nfp(float, int, int)
   * @see PApplet#nfc(float, int)
   */
  static public String nfs(int num, int digits) {
    return (num < 0) ? nf(num, digits) : (' ' + nf(num, digits));
  }


  /**
   * @param nums the numbers to format
   */
  static public String[] nfs(int[] nums, int digits) {
    String[] formatted = new String[nums.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfs(nums[i], digits);
    }
    return formatted;
  }


 /**
  * Utility function for formatting numbers into strings. Similar to <b>nf()</b>
  * but puts a "+" in front of positive numbers and a "-" in front of negative
  * numbers. There are two versions: one for formatting floats, and one for
  * formatting ints. The values for the <b>digits</b>, <b>left</b>, and
  * <b>right</b> parameters should always be positive integers.
  *
  * @webref data:string_functions
  * @webBrief Utility function for formatting numbers into strings
  * @param num
  *          the number to format
  * @param digits
  *          number of digits to pad with zeroes
  * @see PApplet#nf(float, int, int)
  * @see PApplet#nfs(float, int, int)
  * @see PApplet#nfc(float, int)
  */
  static public String nfp(int num, int digits) {
    return (num < 0) ? nf(num, digits) : ('+' + nf(num, digits));
  }


  /**
   * @param nums the numbers to format
   */
  static public String[] nfp(int[] nums, int digits) {
    String[] formatted = new String[nums.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfp(nums[i], digits);
    }
    return formatted;
  }



  //////////////////////////////////////////////////////////////

  // FLOAT NUMBER FORMATTING

  static private NumberFormat float_nf;
  static private int float_nf_left, float_nf_right;
  static private boolean float_nf_commas;

  /**
   * @param left number of digits to the left of the decimal point
   * @param right number of digits to the right of the decimal point
   */
  static public String[] nf(float[] nums, int left, int right) {
    String[] formatted = new String[nums.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nf(nums[i], left, right);
    }
    return formatted;
  }

  static public String nf(float num, int left, int right) {
    if ((float_nf != null) &&
        (float_nf_left == left) &&
        (float_nf_right == right) &&
        !float_nf_commas) {
      return float_nf.format(num);
    }

    float_nf = NumberFormat.getInstance();
    float_nf.setGroupingUsed(false);
    float_nf_commas = false;

    if (left != 0) float_nf.setMinimumIntegerDigits(left);
    if (right != 0) {
      float_nf.setMinimumFractionDigits(right);
      float_nf.setMaximumFractionDigits(right);
    }
    float_nf_left = left;
    float_nf_right = right;
    return float_nf.format(num);
  }

  /**
   * @param right number of digits to the right of the decimal point
  */
  static public String[] nfc(float[] nums, int right) {
    String[] formatted = new String[nums.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfc(nums[i], right);
    }
    return formatted;
  }

  static public String nfc(float num, int right) {
    if ((float_nf != null) &&
        (float_nf_left == 0) &&
        (float_nf_right == right) &&
        float_nf_commas) {
      return float_nf.format(num);
    }

    float_nf = NumberFormat.getInstance();
    float_nf.setGroupingUsed(true);
    float_nf_commas = true;

    if (right != 0) {
      float_nf.setMinimumFractionDigits(right);
      float_nf.setMaximumFractionDigits(right);
    }
    float_nf_left = 0;
    float_nf_right = right;
    return float_nf.format(num);
  }


 /**
  * @param left the number of digits to the left of the decimal point
  * @param right the number of digits to the right of the decimal point
  */
  static public String[] nfs(float[] nums, int left, int right) {
    String[] formatted = new String[nums.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfs(nums[i], left, right);
    }
    return formatted;
  }

  static public String nfs(float num, int left, int right) {
    return (num < 0) ? nf(num, left, right) :  (' ' + nf(num, left, right));
  }

 /**
  * @param left the number of digits to the left of the decimal point
  * @param right the number of digits to the right of the decimal point
  */
  static public String[] nfp(float[] nums, int left, int right) {
    String[] formatted = new String[nums.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfp(nums[i], left, right);
    }
    return formatted;
  }

  static public String nfp(float num, int left, int right) {
    return (num < 0) ? nf(num, left, right) :  ('+' + nf(num, left, right));
  }



  //////////////////////////////////////////////////////////////

  // HEX/BINARY CONVERSION


  /**
   *
   * Converts an <b>int</b>, <b>byte</b>, <b>char</b>, or <b>color</b> to a
   * <b>String</b> containing the equivalent hexadecimal notation. For example,
   * the <b>color</b> value produced by <b>color(0, 102, 153)</b> will convert
   * to the <b>String</b> value <b>"FF006699"</b>. This function can help make
   * your geeky debugging sessions much happier.<br />
   * <br />
   * Note that the maximum number of digits is 8, because an <b>int</b> value
   * can only represent up to 32 bits. Specifying more than 8 digits will not
   * increase the length of the <b>String</b> further.
   *
   * @webref data:conversion
   * @webBrief Converts a <b>byte</b>, <b>char</b>, <b>int</b>, or <b>color</b> to a <b>String</b> containing the
   *           equivalent hexadecimal notation
   * @param value
   *          the value to convert
   * @see PApplet#unhex(String)
   * @see PApplet#binary(byte)
   * @see PApplet#unbinary(String)
   */
  static final public String hex(byte value) {
    return hex(value, 2);
  }

  static final public String hex(char value) {
    return hex(value, 4);
  }

  static final public String hex(int value) {
    return hex(value, 8);
  }
/**
 * @param digits the number of digits (maximum 8)
 */
  static final public String hex(int value, int digits) {
    String stuff = Integer.toHexString(value).toUpperCase();
    if (digits > 8) {
      digits = 8;
    }

    int length = stuff.length();
    if (length > digits) {
      return stuff.substring(length - digits);

    } else if (length < digits) {
      return "00000000".substring(8 - (digits-length)) + stuff;
    }
    return stuff;
  }

 /**
  *
  * Converts a <b>String</b> representation of a hexadecimal number to its
  * equivalent integer value.
  *
  * @webref data:conversion
  * @webBrief Converts a <b>String</b> representation of a hexadecimal number to its
  *           equivalent integer value
  * @param value
  *          String to convert to an integer
  * @see PApplet#hex(int, int)
  * @see PApplet#binary(byte)
  * @see PApplet#unbinary(String)
  */
  static final public int unhex(String value) {
    // has to parse as a Long so that it'll work for numbers bigger than 2^31
    return (int) (Long.parseLong(value, 16));
  }

  //

  /**
   * Returns a String that contains the binary value of a byte.
   * The returned value will always have 8 digits.
   */
  static final public String binary(byte value) {
    return binary(value, 8);
  }

  /**
   * Returns a String that contains the binary value of a char.
   * The returned value will always have 16 digits because chars
   * are two bytes long.
   */
  static final public String binary(char value) {
    return binary(value, 16);
  }

  /**
   * Returns a String that contains the binary value of an int. The length
   * depends on the size of the number itself. If you want a specific number
   * of digits use binary(int what, int digits) to specify how many.
   */
  static final public String binary(int value) {
    return binary(value, 32);
  }

  /*
   * Returns a String that contains the binary value of an int.
   * The digits parameter determines how many digits will be used.
   */

 /**
  *
  * Converts an <b>int</b>, <b>byte</b>, <b>char</b>, or <b>color</b> to a
  * <b>String</b> containing the equivalent binary notation. For example, the
  * <b>color</b> value produced by <b>color(0, 102, 153, 255)</b> will convert
  * to the <b>String</b> value <b>"11111111000000000110011010011001"</b>. This
  * function can help make your geeky debugging sessions much happier.<br />
  * <br />
  * Note that the maximum number of digits is 32, because an <b>int</b> value
  * can only represent up to 32 bits. Specifying more than 32 digits will have
  * no effect.
  *
  * @webref data:conversion
  * @webBrief Converts an <b>int</b>, <b>byte</b>, <b>char</b>, or <b>color</b> to a
  * <b>String</b> containing the equivalent binary notation
  * @param value
  *          value to convert
  * @param digits
  *          number of digits to return
  * @see PApplet#unbinary(String)
  * @see PApplet#hex(int,int)
  * @see PApplet#unhex(String)
  */
  static final public String binary(int value, int digits) {
    String stuff = Integer.toBinaryString(value);
    if (digits > 32) {
      digits = 32;
    }

    int length = stuff.length();
    if (length > digits) {
      return stuff.substring(length - digits);

    } else if (length < digits) {
      int offset = 32 - (digits-length);
      return "00000000000000000000000000000000".substring(offset) + stuff;
    }
    return stuff;
  }


 /**
  *
  * Converts a <b>String</b> representation of a binary number to its equivalent
  * integer value. For example, <b>unbinary("00001000")</b> will return
  * <b>8</b>.
  *
  * @webref data:conversion
  * @webBrief Converts a <b>String</b> representation of a binary number to its
  *           equivalent <b>integer</b> value
  * @param value
  *          String to convert to an integer
  * @see PApplet#binary(byte)
  * @see PApplet#hex(int,int)
  * @see PApplet#unhex(String)
  */
  static final public int unbinary(String value) {
    return Integer.parseInt(value, 2);
  }



  //////////////////////////////////////////////////////////////

  // COLOR FUNCTIONS

  // moved here so that they can work without
  // the graphics actually being instantiated (outside setup)


  /**
   *
   * Creates colors for storing in variables of the <b>color</b> datatype. The
   * parameters are interpreted as RGB or HSB values depending on the current
   * <b>colorMode()</b>. The default mode is RGB values from 0 to 255 and,
   * therefore, <b>color(255, 204, 0)</b> will return a bright yellow color (see
   * the first example above).<br />
   * <br />
   * Note that if only one value is provided to <b>color()</b>, it will be
   * interpreted as a grayscale value. Add a second value, and it will be used
   * for alpha transparency. When three values are specified, they are
   * interpreted as either RGB or HSB values. Adding a fourth value applies
   * alpha transparency.<br />
   * <br />
   * Note that when using hexadecimal notation, it is not necessary to use
   * <b>color()</b>, as in: <b>color c = #006699</b><br />
   * <br />
   * More about how colors are stored can be found in the reference for the
   * <a href="color_datatype.html">color</a> datatype.
   *
   * @webref color:creating & reading
   * @webBrief Creates colors for storing in variables of the <b>color</b>
   *           datatype
   * @param gray
   *          number specifying value between white and black
   * @see PApplet#colorMode(int)
   */
  public final int color(int gray) {
    if (g == null) {
      if (gray > 255) gray = 255; else if (gray < 0) gray = 0;
      return 0xff000000 | (gray << 16) | (gray << 8) | gray;
    }
    return g.color(gray);
  }


  /**
   * @nowebref
   * @param fgray number specifying value between white and black
   */
  public final int color(float fgray) {
    if (g == null) {
      int gray = (int) fgray;
      if (gray > 255) gray = 255; else if (gray < 0) gray = 0;
      return 0xff000000 | (gray << 16) | (gray << 8) | gray;
    }
    return g.color(fgray);
  }


  /**
   * As of 0116 this also takes color(#FF8800, alpha)
   * @param alpha relative to current color range
   */
  public final int color(int gray, int alpha) {
    if (g == null) {
      if (alpha > 255) alpha = 255; else if (alpha < 0) alpha = 0;
      if (gray > 255) {
        // then assume this is actually a #FF8800
        return (alpha << 24) | (gray & 0xFFFFFF);
      } else {
        //if (gray > 255) gray = 255; else if (gray < 0) gray = 0;
        return (alpha << 24) | (gray << 16) | (gray << 8) | gray;
      }
    }
    return g.color(gray, alpha);
  }


  /**
   * @nowebref
   */
  public final int color(float fgray, float falpha) {
    if (g == null) {
      int gray = (int) fgray;
      int alpha = (int) falpha;
      if (gray > 255) gray = 255; else if (gray < 0) gray = 0;
      if (alpha > 255) alpha = 255; else if (alpha < 0) alpha = 0;
      return (alpha << 24) | (gray << 16) | (gray << 8) | gray;
    }
    return g.color(fgray, falpha);
  }


  /**
   * @param v1 red or hue values relative to the current color range
   * @param v2 green or saturation values relative to the current color range
   * @param v3 blue or brightness values relative to the current color range
   */
  public final int color(int v1, int v2, int v3) {
    if (g == null) {
      if (v1 > 255) v1 = 255; else if (v1 < 0) v1 = 0;
      if (v2 > 255) v2 = 255; else if (v2 < 0) v2 = 0;
      if (v3 > 255) v3 = 255; else if (v3 < 0) v3 = 0;

      return 0xff000000 | (v1 << 16) | (v2 << 8) | v3;
    }
    return g.color(v1, v2, v3);
  }


  public final int color(int v1, int v2, int v3, int alpha) {
    if (g == null) {
      if (alpha > 255) alpha = 255; else if (alpha < 0) alpha = 0;
      if (v1 > 255) v1 = 255; else if (v1 < 0) v1 = 0;
      if (v2 > 255) v2 = 255; else if (v2 < 0) v2 = 0;
      if (v3 > 255) v3 = 255; else if (v3 < 0) v3 = 0;

      return (alpha << 24) | (v1 << 16) | (v2 << 8) | v3;
    }
    return g.color(v1, v2, v3, alpha);
  }


  public final int color(float v1, float v2, float v3) {
    if (g == null) {
      if (v1 > 255) v1 = 255; else if (v1 < 0) v1 = 0;
      if (v2 > 255) v2 = 255; else if (v2 < 0) v2 = 0;
      if (v3 > 255) v3 = 255; else if (v3 < 0) v3 = 0;

      return 0xff000000 | ((int)v1 << 16) | ((int)v2 << 8) | (int)v3;
    }
    return g.color(v1, v2, v3);
  }


  public final int color(float v1, float v2, float v3, float alpha) {
    if (g == null) {
      if (alpha > 255) alpha = 255; else if (alpha < 0) alpha = 0;
      if (v1 > 255) v1 = 255; else if (v1 < 0) v1 = 0;
      if (v2 > 255) v2 = 255; else if (v2 < 0) v2 = 0;
      if (v3 > 255) v3 = 255; else if (v3 < 0) v3 = 0;

      return ((int)alpha << 24) | ((int)v1 << 16) | ((int)v2 << 8) | (int)v3;
    }
    return g.color(v1, v2, v3, alpha);
  }


  /**
   *
   * Calculates a new <b>color</b> that is a blend of two other colors. The <b>amt</b> parameter 
   * controls the amount of each color to use where an amount of 0.0 will produce 
   * the first color, 1.0 will return the second color, and 0.5 is halfway in 
   * between. Values between 0.0 and 1.0 will interpolate between the two colors in
   * that proportion. <br />
   * An amount below 0 will be treated as 0. Likewise, amounts above 1 will be
   * capped at 1. This is different from the behavior of <b>lerp()</b>, but necessary
   * because otherwise numbers outside the range will produce strange and
   * unexpected colors.
   *
   * @webref color:creating & reading
   * @webBrief Calculates a <b>color</b> or <b>colors</b> between two <b>colors</b> at a specific
   *           increment
   * @usage web_application
   * @param c1
   *          interpolate from this color
   * @param c2
   *          interpolate to this color
   * @param amt
   *          between 0.0 and 1.0
   * @see PImage#blendColor(int, int, int)
   * @see PGraphics#color(float, float, float, float)
   * @see PApplet#lerp(float, float, float)
   */
  public int lerpColor(int c1, int c2, float amt) {
    if (g != null) {
      return g.lerpColor(c1, c2, amt);
    }
    // use the default mode (RGB) if lerpColor is called before setup()
    return PGraphics.lerpColor(c1, c2, amt, RGB);
  }


  static public int blendColor(int c1, int c2, int mode) {
    return PImage.blendColor(c1, c2, mode);
  }



  //////////////////////////////////////////////////////////////


  /*
  public void frameMoved(int x, int y) {
    if (!fullScreen) {
      System.err.println(EXTERNAL_MOVE + " " + x + " " + y);
      System.err.flush();  // doesn't seem to help or hurt
    }
  }


  public void frameResized(int w, int h) {
  }
  */


  //////////////////////////////////////////////////////////////

  // WINDOW METHODS

  Map<String, Integer> windowEventQueue = new ConcurrentHashMap<>();


  public void windowTitle(String title) {
    surface.setTitle(title);
  }


  public void windowResize(int newWidth, int newHeight) {
    surface.setSize(newWidth, newHeight);
  }


  /**
   * Internal use only: called by Surface objects to queue a resize
   * event to call windowResized() when it's safe, which is after
   * the beginDraw() call and before the draw(). Note that this is
   * only the notification that the resize has happened.
   */
  public void postWindowResized(int newWidth, int newHeight) {
    windowEventQueue.put("w", newWidth);
    windowEventQueue.put("h", newHeight);
  }


  /** Called when window is resized. */
  public void windowResized() {  }


  public void windowResizable(boolean resizable) {
    surface.setResizable(resizable);
  }


  public void windowMove(int x, int y) {
    surface.setLocation(x, y);
  }


  /**
   * When running from the PDE, this saves the window position for
   * next time the sketch is run. Needs to remain a separate method
   * so that it can be overridden by Python Mode.
   */
  public void frameMoved(int newX, int newY) {
    System.err.println(EXTERNAL_MOVE + " " + newX + " " + newY);
    System.err.flush();  // doesn't seem to help or hurt
  }


  /**
   * Internal use only: called by Surface objects to queue a position
   * event to call windowPositioned() when it's safe, which is after
   * the beginDraw() call and before the draw(). Note that this is
   * only the notification that the window is in a new position.
   */
  public void postWindowMoved(int newX, int newY) {
    if (external && !fullScreen) {
      frameMoved(newX, newY);
    }

    windowEventQueue.put("x", newX);
    windowEventQueue.put("y", newY);
  }


  /** Called when the window is moved */
  public void windowMoved() {  }


  private void dequeueWindowEvents() {
    if (windowEventQueue.containsKey("x")) {
      windowX = windowEventQueue.remove("x");
      windowY = windowEventQueue.remove("y");
      windowMoved();
    }
    if (windowEventQueue.containsKey("w")) {
      // these should already match width/height
      //windowResized(windowEventQueue.remove("w"),
      //              windowEventQueue.remove("h"));
      windowEventQueue.remove("w");
      windowEventQueue.remove("h");
      windowResized();
    }
  }


  /**
   * Scale the sketch as if it fits this specific width and height.
   * This will also scale the mouseX and mouseY variables (as well as
   * pmouseX and pmouseY). Note that it will not have an effect on
   * MouseEvent objects (i.e. event.getX() and event.getY()) because
   * their exact behavior may interact strangely with other libraries.
   */
  public void windowRatio(int wide, int high) {
    rwidth = wide;
    rheight = high;
    windowRatio = true;
  }


  //////////////////////////////////////////////////////////////

  // MAIN


  /**
   * main() method for running this class from the command line.
   * <p>
   * Usage: PApplet [options] &lt;class name&gt; [sketch args]
   * <ul>
   * <li>The [options] are one or several of the parameters seen below.
   * <li>The class name is required. If you're running outside the PDE and
   * your class is in a package, this should include the full name. That means
   * that if the class is called Sketchy and the package is com.sketchycompany
   * then com.sketchycompany.Sketchy should be used as the class name.
   * <li>The [sketch args] are any command line parameters you want to send to
   * the sketch itself. These will be passed into the args[] array in PApplet.
   * <p>
   * The simplest way to turn and sketch into an application is to
   * add the following code to your program:
   * <PRE>static public void main(String args[]) {
   *   PApplet.main("YourSketchName");
   * }</PRE>
   * That will properly launch your code from a double-clickable .jar
   * or from the command line.
   * <PRE>
   * Parameters useful for launching or also used by the PDE:
   *
   * --location=x,y         Upper left-hand corner of where the sketch
   *                        should appear on screen. If not used,
   *                        the default is to center on the main screen.
   *
   * --present              Presentation mode: blanks the entire screen and
   *                        shows the sketch by itself. If the sketch is
   *                        smaller than the screen, the surrounding area
   *                        will use the --window-color setting.
   *
   * --hide-stop            Use to hide the stop button in situations where
   *                        you don't want to allow users to exit. also
   *                        see the FAQ on information for capturing the ESC
   *                        key when running in presentation mode.
   *
   * --stop-color=#xxxxxx   Color of the 'stop' text used to quit a
   *                        sketch when it's in present mode.
   *
   * --window-color=#xxxxxx Background color of the window. The color used
   *                        around the sketch when it's smaller than the
   *                        minimum window size for the OS, and the matte
   *                        color when using 'present' mode.
   *
   * --sketch-path          Location of where to save files from functions
   *                        like saveStrings() or saveFrame(). defaults to
   *                        the folder that the java application was
   *                        launched from, which means if this isn't set by
   *                        the pde, everything goes into the same folder
   *                        as processing.exe.
   *
   * --display=n            Set what display should be used by this sketch.
   *                        Displays are numbered starting from 1. This will
   *                        be overridden by fullScreen() calls that specify
   *                        a display. Omitting this option will cause the
   *                        default display to be used.
   *
   * Parameters used by Processing when running via the PDE
   *
   * --external             set when the sketch is being used by the PDE
   *
   * --editor-location=x,y  position of the upper left-hand corner of the
   *                        editor window, for placement of sketch window
   *
   * All parameters *after* the sketch class name are passed to the sketch
   * itself and available from its 'args' array while the sketch is running.
   *
   * @see PApplet#args
   * </PRE>
   */
  static public void main(final String[] args) {
    runSketch(args, null);
  }


  /**
   * Convenience method so that PApplet.main(YourSketch.class)
   * launches a sketch, rather than having to call getName() on it.
   */
  static public void main(final Class<?> mainClass, String... args) {
    main(mainClass.getName(), args);
  }


  /**
   * Convenience method so that PApplet.main("YourSketch") launches a sketch,
   * rather than having to wrap it into a single element String array.
   * @param mainClass name of the class to load (with package if any)
   */
  static public void main(final String mainClass) {
    main(mainClass, null);
  }


  /**
   * Convenience method so that PApplet.main("YourSketch", args) launches a
   * sketch, rather than having to wrap it into a String array, and appending
   * the 'args' array when not null.
   * @param mainClass name of the class to load (with package if any)
   * @param sketchArgs command line arguments to pass to the sketch's 'args'
   *             array. Note that this is <i>not</i> the same as the args passed
   *             to (and understood by) PApplet such as --display.
   */
  static public void main(final String mainClass, final String[] sketchArgs) {
    String[] args = new String[] { mainClass };
    if (sketchArgs != null) {
      args = concat(args, sketchArgs);
    }
    runSketch(args, null);
  }


  // Moving this back off the EDT for 3.0 alpha 10. Not sure if we're helping
  // or hurting, but unless we do, errors inside settings() are never passed
  // through to the PDE. There are other ways around that, no doubt, but I'm
  // also suspecting that these "not showing up" bugs might be EDT issues.
  static public void runSketch(final String[] args,
                               final PApplet constructedSketch) {
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
      e.printStackTrace();
      uncaughtThrowable = e;
    });

    // This doesn't work, need to mess with Info.plist instead
    /*
    // In an exported application, add the Contents/Java folder to the
    // java.library.path, so that native libraries work properly.
    // Without this, the library path is only set to Contents/MacOS
    // where the launcher binary lives.
    if (platform == MACOSX) {
      URL coreJarURL =
        PApplet.class.getProtectionDomain().getCodeSource().getLocation();
      // The jarPath from above will/may be URL encoded (%20 for spaces)
      String coreJarPath = urlDecode(coreJarURL.getPath());
      if (coreJarPath.endsWith("/Contents/Java/core.jar")) {
        // remove the /core.jar part from the end
        String javaPath = coreJarPath.substring(0, coreJarPath.length() - 9);
        String libraryPath = System.getProperty("java.library.path");
        libraryPath += File.pathSeparator + javaPath;
        System.setProperty("java.library.path", libraryPath);
      }
    }
    */

    // So that the system proxy setting are used by default
    System.setProperty("java.net.useSystemProxies", "true");

    if (args.length < 1) {
      System.err.println("Usage: PApplet [options] <class name> [sketch args]");
      System.err.println("See the Javadoc for PApplet for an explanation.");
      System.exit(1);
    }

    boolean external = false;
    int[] location = null;
    int[] editorLocation = null;

    String name = null;
    int windowColor = 0;
    int stopColor = 0xff808080;
    boolean hideStop = false;

    int displayNum = -1;  // use default
    boolean present = false;
    boolean fullScreen = false;
    float uiScale = 0;

    String param, value;
    String folder = calcSketchPath();

    int argIndex = 0;
    label:
    while (argIndex < args.length) {
      int equals = args[argIndex].indexOf('=');
      if (equals != -1) {
        param = args[argIndex].substring(0, equals);
        value = args[argIndex].substring(equals + 1);

        //noinspection EnhancedSwitchMigration
        switch (param) {
          case ARGS_EDITOR_LOCATION:
            external = true;
            editorLocation = parseInt(split(value, ','));
            break;

          case ARGS_DISPLAY:
            displayNum = parseInt(value, -2);
            if (displayNum == -2) {
              // this means the display value couldn't be parsed properly
              System.err.println(value + " is not a valid choice for " + ARGS_DISPLAY);
              displayNum = -1;  // use the default
            }
            break;

          case ARGS_DISABLE_AWT:
            disableAWT = true;
            break;

          case ARGS_WINDOW_COLOR:
            if (value.charAt(0) == '#' && value.length() == 7) {
              value = value.substring(1);
              windowColor = 0xff000000 | Integer.parseInt(value, 16);
            } else {
              System.err.println(ARGS_WINDOW_COLOR + " should be a # followed by six digits");
            }
            break;

          case ARGS_STOP_COLOR:
            if (value.charAt(0) == '#' && value.length() == 7) {
              value = value.substring(1);
              stopColor = 0xff000000 | Integer.parseInt(value, 16);
            } else {
              System.err.println(ARGS_STOP_COLOR + " should be a # followed by six digits");
            }
            break;

          case ARGS_SKETCH_FOLDER:
            folder = value;
            break;

          case ARGS_LOCATION:
            location = parseInt(split(value, ','));
            break;

          case ARGS_UI_SCALE:
            uiScale = parseFloat(value, 0);
            if (uiScale == 0) {
              System.err.println("Could not parse " + value + " for " + ARGS_UI_SCALE);
            }
            break;
        }
      } else {
        switch (args[argIndex]) {
          case ARGS_PRESENT:
            present = true;
            break;

          case ARGS_HIDE_STOP:
            hideStop = true;
            break;

          case ARGS_EXTERNAL:
            external = true;
            break;

          case ARGS_FULL_SCREEN:
            fullScreen = true;
            break;

          default:
            name = args[argIndex];
            break label;  // because of break, argIndex won't increment again
        }
      }
      argIndex++;
    }

    if (platform == WINDOWS) {
      // Set DPI scaling to either 1 or 2, but avoid fractional
      // settings such as 125% and 250% that make things look gross.
      // Also applies to 300% since that is not even a thing.

      // no longer possible to set prop after this line initializes AWT
      //int dpi = java.awt.Toolkit.getDefaultToolkit().getScreenResolution();

      // Attempt to get the resolution using a helper app. This code is
      // fairly conservative: if there is trouble, we go with the default.
      if (uiScale == 0) {
        int dpi = getWindowsDPI();
        if (dpi != 0) {
          //uiScale = constrain(dpi / 96, 1, 2);
          // If larger than 150% set scale to 2. Using scale 1 at 175% feels
          // reeaally small. 150% is more of a tossup; it could also use 2.
          uiScale = (dpi > 144) ? 2 : 1;
        }
      }
      if (uiScale != 0) {
        System.setProperty("sun.java2d.uiScale", String.valueOf(uiScale));
      //} else {
        //System.err.println("Could not identify Windows DPI, not setting sun.java2d.uiScale");
      }
    }

    if (!disableAWT) {
      ShimAWT.initRun();
    }

    final PApplet sketch;
    if (constructedSketch != null) {
      sketch = constructedSketch;
    } else {
      try {
        Class<?> c =
          Thread.currentThread().getContextClassLoader().loadClass(name);
        sketch = (PApplet) c.getDeclaredConstructor().newInstance();
      } catch (RuntimeException re) {
        // Don't re-package runtime exceptions
        throw re;
      } catch (Exception e) {
        // Package non-runtime exceptions so we can throw them freely
        throw new RuntimeException(e);
      }
    }

    // TODO When disabling AWT for LWJGL or others, we need to figure out
    //      how to make Cmd-Q and the rest of this still work properly.
    if (platform == MACOS && !disableAWT) {
      try {
        final String td = "processing.core.ThinkDifferent";
        Class<?> thinkDifferent =
          Thread.currentThread().getContextClassLoader().loadClass(td);
        Method method =
          thinkDifferent.getMethod("init", PApplet.class);
        method.invoke(null, sketch);
      } catch (Exception e) {
        e.printStackTrace();  // That's unfortunate
      }
    }

    // Set the suggested display that's coming from the command line
    // (and most likely, from the PDE's preference setting).
    sketch.display = displayNum;

    sketch.present = present;
    sketch.fullScreen = fullScreen;

    // For 3.0.1, moved this above handleSettings() so that loadImage() can be
    // used inside settings(). Sets a terrible precedent, but the alternative
    // of not being able to size a sketch to an image is driving people loopy.
    sketch.sketchPath = folder;

    // Don't set 'args' to a zero-length array if it should be null [3.0a8]
    if (args.length != argIndex + 1) {
      // pass everything after the class name in as args to the sketch itself
      // (fixed for 2.0a5, this was just subsetting by 1, which didn't skip opts)
      sketch.args = PApplet.subset(args, argIndex + 1);
    }

    // Call the settings() method which will give us our size() call
    sketch.handleSettings();

    sketch.external = external;

    if (windowColor != 0) {
      sketch.windowColor = windowColor;
    }

    final PSurface surface = sketch.initSurface();

    if (present) {
      if (hideStop) {
        stopColor = 0;  // they'll get the hint
      }
      surface.placePresent(stopColor);
    } else {
      surface.placeWindow(location, editorLocation);
    }

    /*
    // not always running externally when in present mode
    // moved above setVisible() in 3.0 alpha 11
    if (sketch.external) {
      surface.setupExternalMessages();
    }
    */

    sketch.showSurface();
    sketch.startSurface();
  }


  /** Danger: available for advanced subclassing, but here be dragons. */
  protected void showSurface() {
    if (getGraphics().displayable()) {
      surface.setVisible(true);
    }
  }


  /** See warning in showSurface() */
  protected void startSurface() {
    surface.startThread();
  }


  protected PSurface initSurface() {
    g = createPrimaryGraphics();
    surface = g.createSurface();

    // Create fake Frame object to warn user about the changes
    if (g.displayable()) {
      /*
      if (!disableAWT) {
        frame = new Frame() {
          @Override
          public void setResizable(boolean resizable) {
            deprecationWarning("setResizable");
            surface.setResizable(resizable);
          }

          @Override
          public void setVisible(boolean visible) {
            deprecationWarning("setVisible");
            surface.setVisible(visible);
          }

          @Override
          public void setTitle(String title) {
            deprecationWarning("setTitle");
            surface.setTitle(title);
          }

          @Override
          public void setUndecorated(boolean ignored) {
            throw new RuntimeException("'frame' has been removed from Processing 3, " +
              "use fullScreen() to get an undecorated full screen frame");
          }
          */
          /*
          // Can't override this one because it's called by Window's constructor
          @Override
          public void setLocation(int x, int y) {
            deprecationWarning("setLocation");
            surface.setLocation(x, y);
          }
          */
          /*
          @Override
          public void setSize(int w, int h) {
            deprecationWarning("setSize");
            surface.setSize(w, h);
          }

          private void deprecationWarning(String method) {
            PGraphics.showWarning("Use surface." + method + "() instead of " +
                                  "frame." + method + " in Processing 3");
            //new Exception(method).printStackTrace(System.out);
          }
        };
      }
      */
      surface.initFrame(this);
      surface.setTitle(getClass().getSimpleName());

    } else {
      surface.initOffscreen(this);  // for PDF/PSurfaceNone and friends
    }

    return surface;
  }


  /** Convenience method, should only be called by PSurface subclasses. */
  static public void hideMenuBar() {
    if (platform == MACOS) {
      // Call some native code to remove the menu bar on macOS. Not necessary
      // on Linux and Windows, who are happy to make full screen windows.
      try {
        final String td = "processing.core.ThinkDifferent";
        final Class<?> thinkDifferent = PApplet.class.getClassLoader().loadClass(td);
        thinkDifferent.getMethod("hideMenuBar").invoke(null);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }


  /**
   * Find the location of fenster.exe by walking through java.library.path.
   * (It will be on the path because it's part of core/library/windows-amd64)
   */
  static private String findFenster() {
    String libraryPath = System.getProperty("java.library.path");
    // Should not be null, but cannot assume
    if (libraryPath != null) {
      String[] folders = split(libraryPath, ';');
      // Usually, the most relevant paths will be at the front of the list,
      // so hopefully this will not walk several entries.
      for (String folder : folders) {
        File file = new File(folder, "fenster.exe");
        if (file.exists()) {
          return file.getAbsolutePath();
        }
      }
    }
    return null;
  }


  /**
   * Get the display scaling for Windows by calling out to a helper app.
   * More <a href="https://github.com/processing/processing4/tree/master/build/windows/fenster">here</a>.
   */
  static private int getWindowsDPI() {
    String fensterPath = findFenster();
    if (fensterPath != null) {
      StringList stdout = new StringList();
      StringList stderr = new StringList();
      int result = exec(stdout, stderr, fensterPath);
      if (result == 0) {
        return parseInt(stdout.join(""), 0);
      }
    }
    return 0;
  }


  /**
   * Convenience method for Python Mode to run an already-constructed sketch.
   * This makes it easy to launch a sketch in Jython:
   *
   * <pre>class MySketch(PApplet):
   *     pass
   *
   *MySketch().runSketch();</pre>
   */
  protected void runSketch(final String[] args) {
    final String[] argsWithSketchName = new String[args.length + 1];
    System.arraycopy(args, 0, argsWithSketchName, 0, args.length);
    final String className = this.getClass().getSimpleName();
    final String cleanedClass =
      className.replaceAll("__[^_]+__\\$", "").replaceAll("\\$\\d+", "");
    argsWithSketchName[args.length] = cleanedClass;
    runSketch(argsWithSketchName, this);
  }


  /** Convenience method for Python Mode */
  protected void runSketch() {
    runSketch(new String[0]);
  }


  //////////////////////////////////////////////////////////////


  /**
   *
   * Opens a new file and all subsequent drawing functions are echoed to this
   * file as well as the display window. The <b>beginRecord()</b> function
   * requires two parameters, the first is the renderer and the second is the
   * file name. This function is always used with <b>endRecord()</b> to stop the
   * recording process and close the file. <br />
   * <br />
   * Note that <b>beginRecord()</b> will only pick up any settings that happen
   * after it has been called. For instance, if you call <b>textFont()</b>
   * before <b>beginRecord()</b>, then that font will not be set for the file
   * that you're recording to. <br />
   * <br />
   * <b>beginRecord()</b> works only with the PDF and SVG renderers.
   *
   * @webref output:files
   * @webBrief Opens a new file and all subsequent drawing functions are echoed
   *           to this file as well as the display window
   * @param renderer
   *          PDF or SVG
   * @param filename
   *          filename for output
   * @see PApplet#endRecord()
   */
  public PGraphics beginRecord(String renderer, String filename) {
    filename = insertFrame(filename);
    PGraphics rec = createGraphics(width, height, renderer, filename);
    beginRecord(rec);
    return rec;
  }


  /**
   * @nowebref
   * Begin recording (echoing) commands to the specified PGraphics object.
   */
  public void beginRecord(PGraphics recorder) {
    this.recorder = recorder;
    recorder.beginDraw();
  }


 /**
   *
   * Stops the recording process started by <b>beginRecord()</b> and closes
   * the file.
   *
  * @webref output:files
  * @webBrief Stops the recording process started by <b>beginRecord()</b> and closes
   * the file
  * @see PApplet#beginRecord(String, String)
  */
  public void endRecord() {
    if (recorder != null) {
      recorder.endDraw();
      recorder.dispose();
      recorder = null;
    }
  }


  /**
   *
   * To create vectors from 3D data, use the <b>beginRaw()</b> and
   * <b>endRaw()</b> commands. These commands will grab the shape data just
   * before it is rendered to the screen. At this stage, your entire scene is
   * nothing but a long list of individual lines and triangles. This means
   * that a shape created with <b>sphere()</b> function will be made up of
   * hundreds of triangles, rather than a single object. Or that a
   * multi-segment line shape (such as a curve) will be rendered as
   * individual segments.
   * <br /><br />
   * When using <b>beginRaw()</b> and <b>endRaw()</b>, it's possible to write
   * to either a 2D or 3D renderer. For instance, <b>beginRaw()</b> with the
   * PDF library will write the geometry as flattened triangles and lines,
   * even if recording from the <b>P3D</b> renderer.
   * <br /><br />
   * If you want a background to show up in your files, use <b>rect(0, 0,
   * width, height)</b> after setting the <b>fill()</b> to the background
   * color. Otherwise, the background will not be rendered to the file because
   * the background is not shape.
   * <br /><br />
   * Using <b>hint(ENABLE_DEPTH_SORT)</b> can improve the appearance of 3D
   * geometry drawn to 2D file formats. See the <b>hint()</b> reference for
   * more details.
   * <br /><br />
   * See examples in the reference for the <b>PDF</b> and <b>DXF</b>
   * libraries for more information.
   *
   * @webref output:files
   * @webBrief To create vectors from 3D data, use the <b>beginRaw()</b> and
   * <b>endRaw()</b> commands
   * @param renderer for example, PDF or DXF
   * @param filename filename for output
   * @see PApplet#endRaw()
   * @see PApplet#hint(int)
   */
  public PGraphics beginRaw(String renderer, String filename) {
    filename = insertFrame(filename);
    PGraphics rec = createGraphics(width, height, renderer, filename);
    g.beginRaw(rec);
    return rec;
  }



  /**
   * @nowebref
   * Begin recording raw shape data to the specified renderer.
   * <p/>
   * This simply echoes to g.beginRaw(), but since is placed here (rather than
   * generated by preproc.pl) for clarity and so that it doesn't echo the
   * command should beginRecord() be in use.
   *
   * @param rawGraphics PGraphics context that raw shapes will be written to
   */
  public void beginRaw(PGraphics rawGraphics) {
    g.beginRaw(rawGraphics);
  }


  /**
   *
   * Complement to <b>beginRaw()</b>; they must always be used together. See
   * the <b>beginRaw()</b> reference for details.
   *
   * @webref output:files
   * @webBrief Complement to <b>beginRaw()</b>; they must always be used together
   * @see PApplet#beginRaw(String, String)
   */
  public void endRaw() {
    g.endRaw();
  }



  //////////////////////////////////////////////////////////////


  /**
   *
   * Loads the pixel data of the current display window into the <b>pixels[]</b>
   * array. This function must always be called before reading from or writing
   * to <b>pixels[]</b>. Subsequent changes to the display window will not be
   * reflected in <b>pixels</b> until <b>loadPixels()</b> is called again.
   *
   * <h3>Advanced</h3> Override the g.pixels[] function to set the pixels[]
   * array that's part of the PApplet object. Allows the use of pixels[] in the
   * code, rather than g.pixels[].
   *
   * @webref image:pixels
   * @webBrief Loads the pixel data for the display window into the
   *           <b>pixels[]</b> array
   * @see PApplet#pixels
   * @see PApplet#updatePixels()
   */
  public void loadPixels() {
    g.loadPixels();
    pixels = g.pixels;
  }

 /**
  *
  * Updates the display window with the data in the <b>pixels[]</b> array. Use
  * in conjunction with <b>loadPixels()</b>. If you're only reading pixels from
  * the array, there's no need to call <b>updatePixels()</b> &mdash; updating is
  * only necessary to apply changes.
  *
  * @webref image:pixels
  * @webBrief Updates the display window with the data in the <b>pixels[]</b>
  *           array
  * @see PApplet#loadPixels()
  * @see PApplet#pixels
  */
  public void updatePixels() {
    g.updatePixels();
  }

  /**
   * @nowebref
   * @param x1 x-coordinate of the upper-left corner
   * @param y1 y-coordinate of the upper-left corner
   * @param x2 width of the region
   * @param y2 height of the region
   */
  public void updatePixels(int x1, int y1, int x2, int y2) {
    g.updatePixels(x1, y1, x2, y2);
  }


  //////////////////////////////////////////////////////////////

  // EVERYTHING BELOW THIS LINE IS AUTOMATICALLY GENERATED. DO NOT TOUCH!
  // This includes the Javadoc comments, which are automatically copied from
  // the PImage and PGraphics source code files.

  // public functions for processing.core


  public PGL beginPGL() {
    return g.beginPGL();
  }


  public void endPGL() {
    if (recorder != null) recorder.endPGL();
    g.endPGL();
  }


  public void flush() {
    if (recorder != null) recorder.flush();
    g.flush();
  }


  public void hint(int which) {
    if (recorder != null) recorder.hint(which);
    g.hint(which);
  }


  /**
   * Start a new shape of type POLYGON
   */
  public void beginShape() {
    if (recorder != null) recorder.beginShape();
    g.beginShape();
  }


  /**
   *
   * Using the <b>beginShape()</b> and <b>endShape()</b> functions allow creating
   * more complex forms. <b>beginShape()</b> begins recording vertices for a shape
   * and <b>endShape()</b> stops recording. The value of the <b>kind</b> parameter
   * tells it which types of shapes to create from the provided vertices. With no
   * mode specified, the shape can be any irregular polygon. The parameters
   * available for beginShape() are POINTS, LINES, TRIANGLES, TRIANGLE_FAN,
   * TRIANGLE_STRIP, QUADS, and QUAD_STRIP. After calling the <b>beginShape()</b>
   * function, a series of <b>vertex()</b> commands must follow. To stop drawing
   * the shape, call <b>endShape()</b>. The <b>vertex()</b> function with two
   * parameters specifies a position in 2D and the <b>vertex()</b> function with
   * three parameters specifies a position in 3D. Each shape will be outlined with
   * the current stroke color and filled with the fill color. <br />
   * <br />
   * Transformations such as <b>translate()</b>, <b>rotate()</b>, and
   * <b>scale()</b> do not work within <b>beginShape()</b>. It is also not
   * possible to use other shapes, such as <b>ellipse()</b> or <b>rect()</b>
   * within <b>beginShape()</b>. <br />
   * <br />
   * The P2D and P3D renderers allow <b>stroke()</b> and <b>fill()</b> to be
   * altered on a per-vertex basis, but the default renderer does not. Settings
   * such as <b>strokeWeight()</b>, <b>strokeCap()</b>, and <b>strokeJoin()</b>
   * cannot be changed while inside a <b>beginShape()</b>/<b>endShape()</b> block
   * with any renderer.
   *
   * @webref shape:vertex
   * @webBrief Using the <b>beginShape()</b> and <b>endShape()</b> functions allow
   *           creating more complex forms
   * @param kind Either POINTS, LINES, TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP,
   *             QUADS, or QUAD_STRIP
   * @see PShape
   * @see PGraphics#endShape()
   * @see PGraphics#vertex(float, float, float, float, float)
   * @see PGraphics#curveVertex(float, float, float)
   * @see PGraphics#bezierVertex(float, float, float, float, float, float, float,
   *      float, float)
   */
  public void beginShape(int kind) {
    if (recorder != null) recorder.beginShape(kind);
    g.beginShape(kind);
  }


  /**
   * Sets whether the upcoming vertex is part of an edge.
   * Equivalent to glEdgeFlag(), for people familiar with OpenGL.
   */
  public void edge(boolean edge) {
    if (recorder != null) recorder.edge(edge);
    g.edge(edge);
  }


  /**
   *
   * Sets the current normal vector. Used for drawing three-dimensional
   * shapes and surfaces, <b>normal()</b> specifies a vector perpendicular
   * to a shape's surface which, in turn, determines how lighting affects it.
   * Processing attempts to automatically assign normals to shapes, but since
   * that's imperfect, this is a better option when you want more control.
   * This function is identical to <b>glNormal3f()</b> in OpenGL.
   *
   * @webref lights_camera:lights
   * @webBrief Sets the current normal vector
   * @param nx x direction
   * @param ny y direction
   * @param nz z direction
   * @see PGraphics#beginShape(int)
   * @see PGraphics#endShape(int)
   * @see PGraphics#lights()
   */
  public void normal(float nx, float ny, float nz) {
    if (recorder != null) recorder.normal(nx, ny, nz);
    g.normal(nx, ny, nz);
  }


  public void attribPosition(String name, float x, float y, float z) {
    if (recorder != null) recorder.attribPosition(name, x, y, z);
    g.attribPosition(name, x, y, z);
  }


  public void attribNormal(String name, float nx, float ny, float nz) {
    if (recorder != null) recorder.attribNormal(name, nx, ny, nz);
    g.attribNormal(name, nx, ny, nz);
  }


  public void attribColor(String name, int color) {
    if (recorder != null) recorder.attribColor(name, color);
    g.attribColor(name, color);
  }


  public void attrib(String name, float... values) {
    if (recorder != null) recorder.attrib(name, values);
    g.attrib(name, values);
  }


  public void attrib(String name, int... values) {
    if (recorder != null) recorder.attrib(name, values);
    g.attrib(name, values);
  }


  public void attrib(String name, boolean... values) {
    if (recorder != null) recorder.attrib(name, values);
    g.attrib(name, values);
  }


  /**
   *
   * Sets the coordinate space for texture mapping. The default mode is
   * <b>IMAGE</b>, which refers to the actual coordinates of the image.
   * <b>NORMAL</b> refers to a normalized space of values ranging from 0 to 1.
   * This function only works with the P2D and P3D renderers.<br />
   * <br />
   * With <b>IMAGE</b>, if an image is 100 x 200 pixels, mapping the image onto
   * the entire size of a quad would require the points (0,0) (100, 0) (100,200)
   * (0,200). The same mapping in <b>NORMAL</b> is (0,0) (1,0) (1,1) (0,1).
   *
   * @webref image:textures
   * @webBrief Sets the coordinate space for texture mapping
   * @param mode either IMAGE or NORMAL
   * @see PGraphics#texture(PImage)
   * @see PGraphics#textureWrap(int)
   */
  public void textureMode(int mode) {
    if (recorder != null) recorder.textureMode(mode);
    g.textureMode(mode);
  }


  /**
   * Defines if textures repeat or draw once within a texture map.
   * The two parameters are CLAMP (the default behavior) and REPEAT.
   * This function only works with the P2D and P3D renderers.
   *
   * @webref image:textures
   * @webBrief Defines if textures repeat or draw once within a texture map
   * @param wrap Either CLAMP (default) or REPEAT
   * @see PGraphics#texture(PImage)
   * @see PGraphics#textureMode(int)
   */
  public void textureWrap(int wrap) {
    if (recorder != null) recorder.textureWrap(wrap);
    g.textureWrap(wrap);
  }


  /**
   * Sets a texture to be applied to vertex points. The <b>texture()</b> function
   * must be called between <b>beginShape()</b> and <b>endShape()</b> and before
   * any calls to <b>vertex()</b>. This function only works with the P2D and P3D
   * renderers.
   * <p/>
   * When textures are in use, the fill color is ignored. Instead, use
   * <b>tint()</b> to specify the color of the texture as it is applied to the
   * shape.
   *
   * @webref image:textures
   * @webBrief Sets a texture to be applied to vertex points
   * @param image reference to a PImage object
   * @see PGraphics#textureMode(int)
   * @see PGraphics#textureWrap(int)
   * @see PGraphics#beginShape(int)
   * @see PGraphics#endShape(int)
   * @see PGraphics#vertex(float, float, float, float, float)
   */
  public void texture(PImage image) {
    if (recorder != null) recorder.texture(image);
    g.texture(image);
  }


  /**
   * Removes texture image for current shape.
   * Needs to be called between beginShape and endShape
   *
   */
  public void noTexture() {
    if (recorder != null) recorder.noTexture();
    g.noTexture();
  }


  public void vertex(float x, float y) {
    if (recorder != null) recorder.vertex(x, y);
    g.vertex(x, y);
  }


  public void vertex(float x, float y, float z) {
    if (recorder != null) recorder.vertex(x, y, z);
    g.vertex(x, y, z);
  }


  /**
   * Used by renderer subclasses or PShape to efficiently pass in already
   * formatted vertex information.
   * @param v vertex parameters, as a float array of length VERTEX_FIELD_COUNT
   */
  public void vertex(float[] v) {
    if (recorder != null) recorder.vertex(v);
    g.vertex(v);
  }


  public void vertex(float x, float y, float u, float v) {
    if (recorder != null) recorder.vertex(x, y, u, v);
    g.vertex(x, y, u, v);
  }


/**
 *
 * All shapes are constructed by connecting a series of vertices.
 * <b>vertex()</b> is used to specify the vertex coordinates for points, lines,
 * triangles, quads, and polygons. It is used exclusively within the
 * <b>beginShape()</b> and <b>endShape()</b> functions. <br />
 * <br />
 * Drawing a vertex in 3D using the <b>z</b> parameter requires the P3D
 * parameter in combination with size, as shown in the above example. <br />
 * <br />
 * This function is also used to map a texture onto geometry. The
 * <b>texture()</b> function declares the texture to apply to the geometry and
 * the <b>u</b> and <b>v</b> coordinates set define the mapping of this texture
 * to the form. By default, the coordinates used for <b>u</b> and <b>v</b> are
 * specified in relation to the image's size in pixels, but this relation can be
 * changed with <b>textureMode()</b>.
 *
 * @webref shape:vertex
 * @webBrief All shapes are constructed by connecting a series of vertices
 * @param x x-coordinate of the vertex
 * @param y y-coordinate of the vertex
 * @param z z-coordinate of the vertex
 * @param u horizontal coordinate for the texture mapping
 * @param v vertical coordinate for the texture mapping
 * @see PGraphics#beginShape(int)
 * @see PGraphics#endShape(int)
 * @see PGraphics#bezierVertex(float, float, float, float, float, float, float,
 *      float, float)
 * @see PGraphics#quadraticVertex(float, float, float, float, float, float)
 * @see PGraphics#curveVertex(float, float, float)
 * @see PGraphics#texture(PImage)
 */
  public void vertex(float x, float y, float z, float u, float v) {
    if (recorder != null) recorder.vertex(x, y, z, u, v);
    g.vertex(x, y, z, u, v);
  }


  /**
   * Use the <b>beginContour()</b> and <b>endContour()</b> function to
   * create negative shapes within shapes such as the center of the
   * letter "O". <b>beginContour()</b> begins recording vertices for the
   * shape and <b>endContour()</b> stops recording. The vertices that
   * define a negative shape must "wind" in the opposite direction from
   * the exterior shape. First draw vertices for the exterior shape in
   * clockwise order, then for internal shapes, draw vertices counterclockwise.<br />
   * <br />
   * These functions can only be used within a <b>beginShape()</b>/<b>endShape()</b>
   * pair and transformations such as <b>translate()</b>, <b>rotate()</b>, and
   * <b>scale()</b> do not work within a <b>beginContour()</b>/<b>endContour()</b>
   * pair. It is also not possible to use other shapes, such as <b>ellipse()</b>
   * or <b>rect()</b> within.
   *
   * @webref shape:vertex
   * @webBrief Begins recording vertices for the shape
   */
  public void beginContour() {
    if (recorder != null) recorder.beginContour();
    g.beginContour();
  }


  /**
   * Use the <b>beginContour()</b> and <b>endContour()</b> function to
   * create negative shapes within shapes such as the center of the
   * letter "O". <b>beginContour()</b> begins recording vertices for
   * the shape and <b>endContour()</b> stops recording. The vertices
   * that define a negative shape must "wind" in the opposite direction
   * from the exterior shape. First draw vertices for the exterior shape
   * in clockwise order, then for internal shapes, draw vertices counterclockwise.<br />
   * <br />
   * These functions can only be used within a <b>beginShape()</b>/<b>endShape()</b>
   * pair and transformations such as <b>translate()</b>, <b>rotate()</b>, and
   * <b>scale()</b> do not work within a <b>beginContour()</b>/<b>endContour()</b>
   * pair. It is also not possible to use other shapes, such as <b>ellipse()</b>
   * or <b>rect()</b> within.
   *
   * @webref shape:vertex
   * @webBrief Stops recording vertices for the shape
   */
  public void endContour() {
    if (recorder != null) recorder.endContour();
    g.endContour();
  }


  public void endShape() {
    if (recorder != null) recorder.endShape();
    g.endShape();
  }


  /**
   *
   * The <b>endShape()</b> function is the companion to <b>beginShape()</b>
   * and may only be called after <b>beginShape()</b>. When <b>endshape()</b>
   * is called, all the image data defined since the previous call to
   * <b>beginShape()</b> is written into the image buffer. The constant CLOSE
   * as the value for the MODE parameter to close the shape (to connect the
   * beginning and the end).
   *
   * @webref shape:vertex
   * @webBrief the companion to <b>beginShape()</b> and may only be called after <b>beginShape()</b>
   * @param mode use CLOSE to close the shape
   * @see PShape
   * @see PGraphics#beginShape(int)
   */
  public void endShape(int mode) {
    if (recorder != null) recorder.endShape(mode);
    g.endShape(mode);
  }


  /**
   * Loads geometry into a variable of type <b>PShape</b>. SVG and OBJ
   * files may be loaded. To load correctly, the file must be located
   * in the data directory of the current sketch. In most cases,
   * <b>loadShape()</b> should be used inside <b>setup()</b> because
   * loading shapes inside <b>draw()</b> will reduce the speed of a sketch.<br />
   * <br />
   * Alternatively, the file maybe be loaded from anywhere on the local
   * computer using an absolute path (something that starts with / on
   * Unix and Linux, or a drive letter on Windows), or the filename
   * parameter can be a URL for a file found on a network.<br />
   * <br />
   * If the file is not available or an error occurs, <b>null</b> will
   * be returned and an error message will be printed to the console.
   * The error message does not halt the program, however the null value
   * may cause a NullPointerException if your code does not check whether
   * the value returned is null.<br />
   *
   * @webref shape
   * @webBrief Loads geometry into a variable of type <b>PShape</b>
   * @param filename name of file to load, can be .svg or .obj
   * @see PShape
   * @see PApplet#createShape()
   */
  public PShape loadShape(String filename) {
    return g.loadShape(filename);
  }


  /**
   * @nowebref
   */
  public PShape loadShape(String filename, String options) {
    return g.loadShape(filename, options);
  }


  /**
   * The <b>createShape()</b> function is used to define a new shape.
   * Once created, this shape can be drawn with the <b>shape()</b>
   * function. The basic way to use the function defines new primitive
   * shapes. One of the following parameters are used as the first
   * parameter: <b>ELLIPSE</b>, <b>RECT</b>, <b>ARC</b>, <b>TRIANGLE</b>,
   * <b>SPHERE</b>, <b>BOX</b>, <b>QUAD</b>, or <b>LINE</b>. The
   * parameters for each of these different shapes are the same as their
   * corresponding functions: <b>ellipse()</b>, <b>rect()</b>, <b>arc()</b>,
   * <b>triangle()</b>, <b>sphere()</b>, <b>box()</b>, <b>quad()</b>, and
   * <b>line()</b>. The first example above clarifies how this works.<br />
   * <br />
   * Custom, unique shapes can be made by using <b>createShape()</b> without
   * a parameter. After the shape is started, the drawing attributes and
   * geometry can be set directly to the shape within the <b>beginShape()</b>
   * and <b>endShape()</b> methods. See the second example above for specifics,
   * and the reference for <b>beginShape()</b> for all of its options.<br />
   * <br />
   * The  <b>createShape()</b> function can also be used to make a complex
   * shape made of other shapes. This is called a "group" and it's created by
   * using the parameter <b>GROUP</b> as the first parameter. See the fourth
   * example above to see how it works.<br />
   * <br />
   * After using <b>createShape()</b>, stroke and fill color can be set by
   * calling methods like <b>setFill()</b> and <b>setStroke()</b>, as seen
   * in the examples above. The complete list of methods and fields for the
   * PShape class are in the <a href="http://processing.github.io/processing-javadocs/core/">Processing Javadoc</a>.
   *
   * @webref shape
   * @webBrief The <b>createShape()</b> function is used to define a new shape
   * @see PShape
   * @see PShape#endShape()
   * @see PApplet#loadShape(String)
   */
  public PShape createShape() {
    return g.createShape();
  }


  public PShape createShape(int type) {
    return g.createShape(type);
  }


  /**
   * @param kind either POINT, LINE, TRIANGLE, QUAD, RECT, ELLIPSE, ARC, BOX, SPHERE
   * @param p parameters that match the kind of shape
   */
  public PShape createShape(int kind, float... p) {
    return g.createShape(kind, p);
  }


  /**
   * Loads a shader into the <b>PShader</b> object. The shader file must be
   * loaded in the sketch's "data" folder/directory to load correctly.
   * Shaders are compatible with the P2D and P3D renderers, but not
   * with the default renderer.<br />
   * <br />
   * Alternatively, the file maybe be loaded from anywhere on the local
   * computer using an absolute path (something that starts with / on
   * Unix and Linux, or a drive letter on Windows), or the filename
   * parameter can be a URL for a file found on a network.<br />
   * <br />
   * If the file is not available or an error occurs, <b>null</b> will
   * be returned and an error message will be printed to the console.
   * The error message does not halt the program, however the null
   * value may cause a NullPointerException if your code does not check
   * whether the value returned is null.<br />
   *
   *
   * @webref rendering:shaders
   * @webBrief Loads a shader into the <b>PShader</b> object
   * @param fragFilename name of fragment shader file
   */
  public PShader loadShader(String fragFilename) {
    return g.loadShader(fragFilename);
  }


  /**
   * @param vertFilename name of vertex shader file
   */
  public PShader loadShader(String fragFilename, String vertFilename) {
    return g.loadShader(fragFilename, vertFilename);
  }


  /**
   *
   * Applies the shader specified by the parameters. It's compatible with
   * the P2D and P3D renderers, but not with the default renderer.
   *
   * @webref rendering:shaders
   * @webBrief Applies the shader specified by the parameters
   * @param shader name of shader file
   */
  public void shader(PShader shader) {
    if (recorder != null) recorder.shader(shader);
    g.shader(shader);
  }


  /**
   * @param kind type of shader, either POINTS, LINES, or TRIANGLES
   */
  public void shader(PShader shader, int kind) {
    if (recorder != null) recorder.shader(shader, kind);
    g.shader(shader, kind);
  }


  /**
   * Restores the default shaders. Code that runs after <b>resetShader()</b>
   * will not be affected by previously defined shaders.
   *
   * @webref rendering:shaders
   * @webBrief Restores the default shaders
   */
  public void resetShader() {
    if (recorder != null) recorder.resetShader();
    g.resetShader();
  }


  /**
   * @param kind type of shader, either POINTS, LINES, or TRIANGLES
   */
  public void resetShader(int kind) {
    if (recorder != null) recorder.resetShader(kind);
    g.resetShader(kind);
  }


  /**
   * @param shader the fragment shader to apply
   */
  public void filter(PShader shader) {
    if (recorder != null) recorder.filter(shader);
    g.filter(shader);
  }


  /**
   *
   * Limits the rendering to the boundaries of a rectangle defined
   * by the parameters. The boundaries are drawn based on the state
   * of the <b>imageMode()</b> function, either CORNER, CORNERS, or CENTER.
   *
   * @webref rendering
   * @webBrief Limits the rendering to the boundaries of a rectangle defined
   * by the parameters
   * @param a x-coordinate of the rectangle, by default
   * @param b y-coordinate of the rectangle, by default
   * @param c width of the rectangle, by default
   * @param d height of the rectangle, by default
   */
  public void clip(float a, float b, float c, float d) {
    if (recorder != null) recorder.clip(a, b, c, d);
    g.clip(a, b, c, d);
  }


  /**
   * Disables the clipping previously started by the <b>clip()</b> function.
   *
   * @webref rendering
   * @webBrief Disables the clipping previously started by the <b>clip()</b> function
   */
  public void noClip() {
    if (recorder != null) recorder.noClip();
    g.noClip();
  }


  /**
   *
   * Blends the pixels in the display window according to a defined mode.
   * There is a choice of the following modes to blend the source pixels (A)
   * with the ones of pixels already in the display window (B). Each pixel's
   * final color is the result of applying one of the blend modes with each
   * channel of (A) and (B) independently. The red channel is compared with
   * red, green with green, and blue with blue.<br />
   * <br />
   * BLEND - linear interpolation of colors: <b>C = A*factor + B</b>. This is the default.<br />
   * <br />
   * ADD - additive blending with white clip: <b>C = min(A*factor + B, 255)</b><br />
   * <br />
   * SUBTRACT - subtractive blending with black clip: <b>C = max(B - A*factor, 0)</b><br />
   * <br />
   * DARKEST - only the darkest color succeeds: <b>C = min(A*factor, B)</b><br />
   * <br />
   * LIGHTEST - only the lightest color succeeds: <b>C = max(A*factor, B)</b><br />
   * <br />
   * DIFFERENCE - subtract colors from underlying image.<br />
   * <br />
   * EXCLUSION - similar to DIFFERENCE, but less extreme.<br />
   * <br />
   * MULTIPLY - multiply the colors, result will always be darker.<br />
   * <br />
   * SCREEN - opposite multiply, uses inverse values of the colors.<br />
   * <br />
   * REPLACE - the pixels entirely replace the others and don't utilize alpha (transparency) values<br />
   * <br />
   * We recommend using <b>blendMode()</b> and not the previous <b>blend()</b>
   * function. However, unlike <b>blend()</b>, the <b>blendMode()</b> function
   * does not support the following: HARD_LIGHT, SOFT_LIGHT, OVERLAY, DODGE,
   * BURN. On older hardware, the LIGHTEST, DARKEST, and DIFFERENCE modes might
   * not be available as well.
   *
   * @webref rendering
   * @webBrief Blends the pixels in the display window according to a defined mode
   * @param mode the blending mode to use
   */
  public void blendMode(int mode) {
    if (recorder != null) recorder.blendMode(mode);
    g.blendMode(mode);
  }


  public void bezierVertex(float x2, float y2,
                           float x3, float y3,
                           float x4, float y4) {
    if (recorder != null) recorder.bezierVertex(x2, y2, x3, y3, x4, y4);
    g.bezierVertex(x2, y2, x3, y3, x4, y4);
  }


/**
   *
   * Specifies vertex coordinates for Bézier curves. Each call to
   * <b>bezierVertex()</b> defines the position of two control points and one
   * anchor point of a Bézier curve, adding a new segment to a line or shape.
   * The first time <b>bezierVertex()</b> is used within a
   * <b>beginShape()</b> call, it must be prefaced with a call to
   * <b>vertex()</b> to set the first anchor point. This function must be
   * used between <b>beginShape()</b> and <b>endShape()</b> and only when
   * there is no MODE parameter specified to <b>beginShape()</b>. Using the
   * 3D version requires rendering with P3D (see the Environment reference
   * for more information).
   *
 * @webref shape:vertex
 * @webBrief Specifies vertex coordinates for Bezier curves
 * @param x2 the x-coordinate of the 1st control point
 * @param y2 the y-coordinate of the 1st control point
 * @param z2 the z-coordinate of the 1st control point
 * @param x3 the x-coordinate of the 2nd control point
 * @param y3 the y-coordinate of the 2nd control point
 * @param z3 the z-coordinate of the 2nd control point
 * @param x4 the x-coordinate of the anchor point
 * @param y4 the y-coordinate of the anchor point
 * @param z4 the z-coordinate of the anchor point
 * @see PGraphics#curveVertex(float, float, float)
 * @see PGraphics#vertex(float, float, float, float, float)
 * @see PGraphics#quadraticVertex(float, float, float, float, float, float)
 * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
 */
  public void bezierVertex(float x2, float y2, float z2,
                           float x3, float y3, float z3,
                           float x4, float y4, float z4) {
    if (recorder != null) recorder.bezierVertex(x2, y2, z2, x3, y3, z3, x4, y4, z4);
    g.bezierVertex(x2, y2, z2, x3, y3, z3, x4, y4, z4);
  }


  /**
   * Specifies vertex coordinates for quadratic Bézier curves. Each call
   * to <b>quadraticVertex()</b> defines the position of one control
   * point and one anchor point of a Bézier curve, adding a new segment
   * to a line or shape. The first time <b>quadraticVertex()</b> is used
   * within a <b>beginShape()</b> call, it must be prefaced with a call
   * to <b>vertex()</b> to set the first anchor point. This function must
   * be used between <b>beginShape()</b> and <b>endShape()</b> and only
   * when there is no MODE parameter specified to <b>beginShape()</b>.
   * Using the 3D version requires rendering with P3D (see the Environment
   * reference for more information).
   *
   * @webref shape:vertex
   * @webBrief Specifies vertex coordinates for quadratic Bezier curves
   * @param cx the x-coordinate of the control point
   * @param cy the y-coordinate of the control point
   * @param x3 the x-coordinate of the anchor point
   * @param y3 the y-coordinate of the anchor point
   * @see PGraphics#curveVertex(float, float, float)
   * @see PGraphics#vertex(float, float, float, float, float)
   * @see PGraphics#bezierVertex(float, float, float, float, float, float)
   * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
   */
  public void quadraticVertex(float cx, float cy,
                              float x3, float y3) {
    if (recorder != null) recorder.quadraticVertex(cx, cy, x3, y3);
    g.quadraticVertex(cx, cy, x3, y3);
  }


  /**
   * @param cz the z-coordinate of the control point
   * @param z3 the z-coordinate of the anchor point
   */
  public void quadraticVertex(float cx, float cy, float cz,
                              float x3, float y3, float z3) {
    if (recorder != null) recorder.quadraticVertex(cx, cy, cz, x3, y3, z3);
    g.quadraticVertex(cx, cy, cz, x3, y3, z3);
  }


 /**
  * Specifies vertex coordinates for curves. This function may only be used
  * between <b>beginShape()</b> and <b>endShape()</b> and only when there is
  * no MODE parameter specified to <b>beginShape()</b>. The first and last
  * points in a series of <b>curveVertex()</b> lines will be used to guide
  * the beginning and end of the curve. A minimum of four points is
  * required to draw a tiny curve between the second and third points.
  * Adding a fifth point with <b>curveVertex()</b> will draw the curve
  * between the second, third, and fourth points. The <b>curveVertex()</b>
  * function is an implementation of Catmull-Rom splines. Using the 3D
  * version requires rendering with P3D (see the Environment reference for
  * more information).
  *
  * @webref shape:vertex
  * @webBrief Specifies vertex coordinates for curves
  * @param x the x-coordinate of the vertex
  * @param y the y-coordinate of the vertex
  * @see PGraphics#curve(float, float, float, float, float, float, float, float, float, float, float, float)
  * @see PGraphics#beginShape(int)
  * @see PGraphics#endShape(int)
  * @see PGraphics#vertex(float, float, float, float, float)
  * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
  * @see PGraphics#quadraticVertex(float, float, float, float, float, float)
  */
  public void curveVertex(float x, float y) {
    if (recorder != null) recorder.curveVertex(x, y);
    g.curveVertex(x, y);
  }


  /**
   * @param z the z-coordinate of the vertex
   */
  public void curveVertex(float x, float y, float z) {
    if (recorder != null) recorder.curveVertex(x, y, z);
    g.curveVertex(x, y, z);
  }


  /**
   *
   * Draws a point, a coordinate in space at the dimension of one pixel. The first
   * parameter is the horizontal value for the point, the second value is the
   * vertical value for the point, and the optional third value is the depth
   * value. Drawing this shape in 3D with the <b>z</b> parameter requires the P3D
   * parameter in combination with <b>size()</b> as shown in the above example.
   * <br />
   * <br />
   * Use <b>stroke()</b> to set the color of a <b>point()</b>. <br />
   * <br />
   * Point appears round with the default <b>strokeCap(ROUND)</b> and square with
   * <b>strokeCap(PROJECT)</b>. Points are invisible with <b>strokeCap(SQUARE)</b>
   * (no cap). <br />
   * <br />
   * Using point() with strokeWeight(1) or smaller may draw nothing to the screen,
   * depending on the graphics settings of the computer. Workarounds include
   * setting the pixel using <b>set()</s> or drawing the point using either
   * <b>circle()</b> or <b>square()</b>.
   *
   * @webref shape:2d primitives
   * @webBrief Draws a point, a coordinate in space at the dimension of one pixel
   * @param x x-coordinate of the point
   * @param y y-coordinate of the point
   * @see PGraphics#stroke(int)
   */
  public void point(float x, float y) {
    if (recorder != null) recorder.point(x, y);
    g.point(x, y);
  }


  /**
   * @param z z-coordinate of the point
   */
  public void point(float x, float y, float z) {
    if (recorder != null) recorder.point(x, y, z);
    g.point(x, y, z);
  }


  /**
   *
   * Draws a line (a direct path between two points) to the screen. The
   * version of <b>line()</b> with four parameters draws the line in 2D.  To
   * color a line, use the <b>stroke()</b> function. A line cannot be filled,
   * therefore the <b>fill()</b> function will not affect the color of a
   * line. 2D lines are drawn with a width of one pixel by default, but this
   * can be changed with the <b>strokeWeight()</b> function. The version with
   * six parameters allows the line to be placed anywhere within XYZ space.
   * Drawing this shape in 3D with the <b>z</b> parameter requires the P3D
   * parameter in combination with <b>size()</b> as shown in the above example.
   *
   * @webref shape:2d primitives
   * @webBrief Draws a line (a direct path between two points) to the screen
   * @param x1 x-coordinate of the first point
   * @param y1 y-coordinate of the first point
   * @param x2 x-coordinate of the second point
   * @param y2 y-coordinate of the second point
   * @see PGraphics#strokeWeight(float)
   * @see PGraphics#strokeJoin(int)
   * @see PGraphics#strokeCap(int)
   * @see PGraphics#beginShape()
   */
  public void line(float x1, float y1, float x2, float y2) {
    if (recorder != null) recorder.line(x1, y1, x2, y2);
    g.line(x1, y1, x2, y2);
  }


  /**
   * @param z1 z-coordinate of the first point
   * @param z2 z-coordinate of the second point
   */
  public void line(float x1, float y1, float z1,
                   float x2, float y2, float z2) {
    if (recorder != null) recorder.line(x1, y1, z1, x2, y2, z2);
    g.line(x1, y1, z1, x2, y2, z2);
  }


  /**
   *
   * A triangle is a plane created by connecting three points. The first two
   * arguments specify the first point, the middle two arguments specify the
   * second point, and the last two arguments specify the third point.
   *
   * @webref shape:2d primitives
   * @webBrief A triangle is a plane created by connecting three points
   * @param x1 x-coordinate of the first point
   * @param y1 y-coordinate of the first point
   * @param x2 x-coordinate of the second point
   * @param y2 y-coordinate of the second point
   * @param x3 x-coordinate of the third point
   * @param y3 y-coordinate of the third point
   * @see PApplet#beginShape()
   */
  public void triangle(float x1, float y1, float x2, float y2,
                       float x3, float y3) {
    if (recorder != null) recorder.triangle(x1, y1, x2, y2, x3, y3);
    g.triangle(x1, y1, x2, y2, x3, y3);
  }


  /**
   *
   * A quad is a quadrilateral, a four sided polygon. It is similar to a
   * rectangle, but the angles between its edges are not constrained to
   * ninety degrees. The first pair of parameters (x1,y1) sets the first
   * vertex and the subsequent pairs should proceed clockwise or
   * counter-clockwise around the defined shape.
   *
   * @webref shape:2d primitives
   * @webBrief A quad is a quadrilateral, a four sided polygon
   * @param x1 x-coordinate of the first corner
   * @param y1 y-coordinate of the first corner
   * @param x2 x-coordinate of the second corner
   * @param y2 y-coordinate of the second corner
   * @param x3 x-coordinate of the third corner
   * @param y3 y-coordinate of the third corner
   * @param x4 x-coordinate of the fourth corner
   * @param y4 y-coordinate of the fourth corner
   */
  public void quad(float x1, float y1, float x2, float y2,
                   float x3, float y3, float x4, float y4) {
    if (recorder != null) recorder.quad(x1, y1, x2, y2, x3, y3, x4, y4);
    g.quad(x1, y1, x2, y2, x3, y3, x4, y4);
  }


  /**
   *
   * Modifies the location from which rectangles are drawn by changing the way in
   * which parameters given to <b>rect()</b> are interpreted.<br />
   * <br />
   * The default mode is <b>rectMode(CORNER)</b>, which interprets the first two
   * parameters of <b>rect()</b> as the upper-left corner of the shape, while the
   * third and fourth parameters are its width and height.<br />
   * <br />
   * <b>rectMode(CORNERS)</b> interprets the first two parameters of <b>rect()</b>
   * as the location of one corner, and the third and fourth parameters as the
   * location of the opposite corner.<br />
   * <br />
   * <b>rectMode(CENTER)</b> interprets the first two parameters of <b>rect()</b>
   * as the shape's center point, while the third and fourth parameters are its
   * width and height.<br />
   * <br />
   * <b>rectMode(RADIUS)</b> also uses the first two parameters of <b>rect()</b>
   * as the shape's center point, but uses the third and fourth parameters to
   * specify half of the shape's width and height.<br />
   * <br />
   * The parameter must be written in ALL CAPS because Processing is a
   * case-sensitive language.
   *
   * @webref shape:attributes
   * @webBrief Modifies the location from which rectangles draw
   * @param mode either CORNER, CORNERS, CENTER, or RADIUS
   * @see PGraphics#rect(float, float, float, float)
   */
  public void rectMode(int mode) {
    if (recorder != null) recorder.rectMode(mode);
    g.rectMode(mode);
  }


  /**
   *
   * Draws a rectangle to the screen. A rectangle is a four-sided shape with every
   * angle at ninety degrees. By default, the first two parameters set the
   * location of the upper-left corner, the third sets the width, and the fourth
   * sets the height. The way these parameters are interpreted, however, may be
   * changed with the <b>rectMode()</b> function.<br />
   * <br />
   * To draw a rounded rectangle, add a fifth parameter, which is used as the
   * radius value for all four corners.<br />
   * <br />
   * To use a different radius value for each corner, include eight parameters.
   * When using eight parameters, the latter four set the radius of the arc at
   * each corner separately, starting with the top-left corner and moving
   * clockwise around the rectangle.
   *
   * @webref shape:2d primitives
   * @webBrief Draws a rectangle to the screen
   * @param a x-coordinate of the rectangle by default
   * @param b y-coordinate of the rectangle by default
   * @param c width of the rectangle by default
   * @param d height of the rectangle by default
   * @see PGraphics#rectMode(int)
   * @see PGraphics#quad(float, float, float, float, float, float, float, float)
   */
  public void rect(float a, float b, float c, float d) {
    if (recorder != null) recorder.rect(a, b, c, d);
    g.rect(a, b, c, d);
  }


  /**
   * @param r radii for all four corners
   */
  public void rect(float a, float b, float c, float d, float r) {
    if (recorder != null) recorder.rect(a, b, c, d, r);
    g.rect(a, b, c, d, r);
  }


  /**
   * @param tl radius for top-left corner
   * @param tr radius for top-right corner
   * @param br radius for bottom-right corner
   * @param bl radius for bottom-left corner
   */
  public void rect(float a, float b, float c, float d,
                   float tl, float tr, float br, float bl) {
    if (recorder != null) recorder.rect(a, b, c, d, tl, tr, br, bl);
    g.rect(a, b, c, d, tl, tr, br, bl);
  }


  /**
   *
   * Draws a square to the screen. A square is a four-sided shape with
   * every angle at ninety degrees and each side is the same length.
   * By default, the first two parameters set the location of the
   * upper-left corner, the third sets the width and height. The way
   * these parameters are interpreted, however, may be changed with the
   * <b>rectMode()</b> function.
   *
   * @webref shape:2d primitives
   * @webBrief Draws a square to the screen
   * @param x x-coordinate of the rectangle by default
   * @param y y-coordinate of the rectangle by default
   * @param extent width and height of the rectangle by default
   * @see PGraphics#rect(float, float, float, float)
   * @see PGraphics#rectMode(int)
   */
  public void square(float x, float y, float extent) {
    if (recorder != null) recorder.square(x, y, extent);
    g.square(x, y, extent);
  }


  /**
   *
   * Modifies the location from which ellipses are drawn by changing the way in
   * which parameters given to <b>ellipse()</b> are interpreted.<br />
   * <br />
   * The default mode is <b>ellipseMode(CENTER)</b>, which interprets the first
   * two parameters of <b>ellipse()</b> as the shape's center point, while the
   * third and fourth parameters are its width and height.<br />
   * <br />
   * <b>ellipseMode(RADIUS)</b> also uses the first two parameters of
   * <b>ellipse()</b> as the shape's center point, but uses the third and fourth
   * parameters to specify half of the shape's width and height.<br />
   * <br />
   * <b>ellipseMode(CORNER)</b> interprets the first two parameters of
   * <b>ellipse()</b> as the upper-left corner of the shape, while the third and
   * fourth parameters are its width and height.<br />
   * <br />
   * <b>ellipseMode(CORNERS)</b> interprets the first two parameters of
   * <b>ellipse()</b> as the location of one corner of the ellipse's bounding box,
   * and the third and fourth parameters as the location of the opposite
   * corner.<br />
   * <br />
   * The parameter must be written in ALL CAPS because Processing is a
   * case-sensitive language.
   *
   * @webref shape:attributes
   * @webBrief The origin of the ellipse is modified by the <b>ellipseMode()</b>
   *           function
   * @param mode either CENTER, RADIUS, CORNER, or CORNERS
   * @see PApplet#ellipse(float, float, float, float)
   * @see PApplet#arc(float, float, float, float, float, float)
   */
  public void ellipseMode(int mode) {
    if (recorder != null) recorder.ellipseMode(mode);
    g.ellipseMode(mode);
  }


  /**
   *
   * Draws an ellipse (oval) to the screen. An ellipse with equal width and height
   * is a circle. By default, the first two parameters set the location, and the
   * third and fourth parameters set the shape's width and height. The origin may
   * be changed with the <b>ellipseMode()</b> function.
   *
   * @webref shape:2d primitives
   * @webBrief Draws an ellipse (oval) in the display window
   * @param a x-coordinate of the ellipse
   * @param b y-coordinate of the ellipse
   * @param c width of the ellipse by default
   * @param d height of the ellipse by default
   * @see PApplet#ellipseMode(int)
   * @see PApplet#arc(float, float, float, float, float, float)
   */
  public void ellipse(float a, float b, float c, float d) {
    if (recorder != null) recorder.ellipse(a, b, c, d);
    g.ellipse(a, b, c, d);
  }


  /**
   *
   * Draws an arc to the screen. Arcs are drawn along the outer edge of an ellipse
   * defined by the <b>a</b>, <b>b</b>, <b>c</b>, and <b>d</b> parameters. The
   * origin of the arc's ellipse may be changed with the <b>ellipseMode()</b>
   * function. Use the <b>start</b> and <b>stop</b> parameters to specify the
   * angles (in radians) at which to draw the arc. The start/stop values must be
   * in clockwise order. <br />
   * <br />
   * There are three ways to draw an arc; the rendering technique used is defined
   * by the optional seventh parameter. The three options, depicted in the above
   * examples, are PIE, OPEN, and CHORD. The default mode is the OPEN stroke with
   * a PIE fill. <br />
   * <br />
   * In some cases, the <b>arc()</b> function isn't accurate enough for smooth
   * drawing. For example, the shape may jitter on screen when rotating slowly. If
   * you're having an issue with how arcs are rendered, you'll need to draw the
   * arc yourself with <b>beginShape()</b>/<b>endShape()</b> or a <b>PShape</b>.
   *
   * @webref shape:2d primitives
   * @webBrief Draws an arc in the display window
   * @param a     x-coordinate of the arc's ellipse
   * @param b     y-coordinate of the arc's ellipse
   * @param c     width of the arc's ellipse by default
   * @param d     height of the arc's ellipse by default
   * @param start angle to start the arc, specified in radians
   * @param stop  angle to stop the arc, specified in radians
   * @see PApplet#ellipse(float, float, float, float)
   * @see PApplet#ellipseMode(int)
   * @see PApplet#radians(float)
   * @see PApplet#degrees(float)
   */
  public void arc(float a, float b, float c, float d,
                  float start, float stop) {
    if (recorder != null) recorder.arc(a, b, c, d, start, stop);
    g.arc(a, b, c, d, start, stop);
  }


  /*
   * @param mode either OPEN, CHORD, or PIE
   */
  public void arc(float a, float b, float c, float d,
                  float start, float stop, int mode) {
    if (recorder != null) recorder.arc(a, b, c, d, start, stop, mode);
    g.arc(a, b, c, d, start, stop, mode);
  }


  /**
   *
   * Draws a circle to the screen. By default, the first two parameters
   * set the location of the center, and the third sets the shape's width
   * and height. The origin may be changed with the <b>ellipseMode()</b>
   * function.
   *
   * @webref shape:2d primitives
   * @webBrief Draws a circle to the screen
   * @param x x-coordinate of the ellipse
   * @param y y-coordinate of the ellipse
   * @param extent width and height of the ellipse by default
   * @see PApplet#ellipse(float, float, float, float)
   * @see PApplet#ellipseMode(int)
   */
  public void circle(float x, float y, float extent) {
    if (recorder != null) recorder.circle(x, y, extent);
    g.circle(x, y, extent);
  }


  /**
   * A box is an extruded <b>rectangle</b>. A box with equal dimension
   * on all sides is a cube.
   *
   * @webref shape:3d primitives
   * @webBrief A box is an extruded <b>rectangle</b>
   * @param size dimension of the box in all dimensions (creates a cube)
   * @see PGraphics#sphere(float)
   */
  public void box(float size) {
    if (recorder != null) recorder.box(size);
    g.box(size);
  }


  /**
   * @param w dimension of the box in the x-dimension
   * @param h dimension of the box in the y-dimension
   * @param d dimension of the box in the z-dimension
   */
  public void box(float w, float h, float d) {
    if (recorder != null) recorder.box(w, h, d);
    g.box(w, h, d);
  }


  /**
   *
   * Controls the detail used to render a sphere by adjusting the number of
   * vertices of the sphere mesh. The default resolution is 30, which creates
   * a fairly detailed sphere definition with vertices every 360/30 = 12
   * degrees. If you're going to render a great number of spheres per frame,
   * it is advised to reduce the level of detail using this function. The
   * setting stays active until <b>sphereDetail()</b> is called again with a
   * new parameter and so should <i>not</i> be called prior to every
   * <b>sphere()</b> statement, unless you wish to render spheres with
   * different settings, e.g. using less detail for smaller spheres or ones
   * further away from the camera. To control the detail of the horizontal
   * and vertical resolution independently, use the version of the functions
   * with two parameters.
   *
   * <h3>Advanced</h3>
   * Code for sphereDetail() submitted by toxi [031031].
   * Code for enhanced u/v version from davbol [080801].
   *
   * @param res number of segments (minimum 3) used per full circle revolution
   * @webref shape:3d primitives
   * @webBrief Controls the detail used to render a sphere by adjusting the number of
   * vertices of the sphere mesh
   * @see PGraphics#sphere(float)
   */
  public void sphereDetail(int res) {
    if (recorder != null) recorder.sphereDetail(res);
    g.sphereDetail(res);
  }


  /**
   * @param ures number of segments used longitudinally per full circle revolution
   * @param vres number of segments used latitudinally from top to bottom
   */
  public void sphereDetail(int ures, int vres) {
    if (recorder != null) recorder.sphereDetail(ures, vres);
    g.sphereDetail(ures, vres);
  }


  /**
   * A sphere is a hollow ball made from tessellated triangles.
   *
   * <h3>Advanced</h3>
   * <P>
   * Implementation notes:
   * <P>
   * cache all the points of the sphere in a static array
   * top and bottom are just a bunch of triangles that land
   * in the center point
   * <P>
   * sphere is a series of concentric circles who radii vary
   * along the shape, based on, err... cos or something
   * <PRE>
   * [toxi 031031] new sphere code. removed all multiplies with
   * radius, as scale() will take care of that anyway
   *
   * [toxi 031223] updated sphere code (removed modulo)
   * and introduced sphereAt(x,y,z,r)
   * to avoid additional translate()'s on the user/sketch side
   *
   * [davbol 080801] now using separate sphereDetailU/V
   * </PRE>
   *
   * @webref shape:3d primitives
   * @webBrief A sphere is a hollow ball made from tessellated triangles
   * @param r the radius of the sphere
   * @see PGraphics#sphereDetail(int)
   */
  public void sphere(float r) {
    if (recorder != null) recorder.sphere(r);
    g.sphere(r);
  }


  /**
   *
   * Evaluates the Bezier at point t for points a, b, c, d. The parameter t
   * varies between 0 and 1, a and d are points on the curve, and b and c are
   * the control points. This can be done once with the x coordinates and a
   * second time with the y coordinates to get the location of a Bézier curve
   * at t.
   *
   * <h3>Advanced</h3>
   * For instance, to convert the following example:<PRE>
   * stroke(255, 102, 0);
   * line(85, 20, 10, 10);
   * line(90, 90, 15, 80);
   * stroke(0, 0, 0);
   * bezier(85, 20, 10, 10, 90, 90, 15, 80);
   *
   * // draw it in gray, using 10 steps instead of the default 20
   * // this is a slower way to do it, but useful if you need
   * // to do things with the coordinates at each step
   * stroke(128);
   * beginShape(LINE_STRIP);
   * for (int i = 0; i <= 10; i++) {
   *   float t = i / 10.0f;
   *   float x = bezierPoint(85, 10, 90, 15, t);
   *   float y = bezierPoint(20, 10, 90, 80, t);
   *   vertex(x, y);
   * }
   * endShape();</PRE>
   *
   * @webref shape:curves
   * @webBrief Evaluates the Bezier at point t for points a, b, c, d
   * @param a coordinate of first point on the curve
   * @param b coordinate of first control point
   * @param c coordinate of second control point
   * @param d coordinate of second point on the curve
   * @param t value between 0 and 1
   * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#bezierVertex(float, float, float, float, float, float)
   * @see PGraphics#curvePoint(float, float, float, float, float)
   */
  public float bezierPoint(float a, float b, float c, float d, float t) {
    return g.bezierPoint(a, b, c, d, t);
  }


  /**
   * Calculates the tangent of a point on a Bézier curve. There is a good
   * definition of <a href="http://en.wikipedia.org/wiki/Tangent"
   * target="new"><em>tangent</em> on Wikipedia</a>.
   *
   * <h3>Advanced</h3>
   * Code submitted by Dave Bollinger (davbol) for release 0136.
   *
   * @webref shape:curves
   * @webBrief Calculates the tangent of a point on a Bézier curve
   * @param a coordinate of first point on the curve
   * @param b coordinate of first control point
   * @param c coordinate of second control point
   * @param d coordinate of second point on the curve
   * @param t value between 0 and 1
   * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#bezierVertex(float, float, float, float, float, float)
   * @see PGraphics#curvePoint(float, float, float, float, float)
   */
  public float bezierTangent(float a, float b, float c, float d, float t) {
    return g.bezierTangent(a, b, c, d, t);
  }


  /**
   * Sets the resolution at which Bézier curves display. The default value is 20.
   * This function is only useful when using the <b>P2D</b> or <b>P3D</b> renderer;
   * the default (JAVA2D) renderer does not use this information.
   *
   * @webref shape:curves
   * @webBrief Sets the resolution at which Bézier curves display
   * @param detail resolution of the curves
   * @see PGraphics#curve(float, float, float, float, float, float, float, float,
   *      float, float, float, float)
   * @see PGraphics#curveVertex(float, float, float)
   * @see PGraphics#curveTightness(float)
   */
  public void bezierDetail(int detail) {
    if (recorder != null) recorder.bezierDetail(detail);
    g.bezierDetail(detail);
  }


  public void bezier(float x1, float y1,
                     float x2, float y2,
                     float x3, float y3,
                     float x4, float y4) {
    if (recorder != null) recorder.bezier(x1, y1, x2, y2, x3, y3, x4, y4);
    g.bezier(x1, y1, x2, y2, x3, y3, x4, y4);
  }


  /**
   *
   * Draws a Bézier curve on the screen. These curves are defined by a series
   * of anchor and control points. The first two parameters specify the first
   * anchor point and the last two parameters specify the other anchor point.
   * The middle parameters specify the control points which define the shape
   * of the curve. The curves were developed by French engineer Pierre
   * Bezier. Using the 3D version requires rendering with P3D (see the
   * Environment reference for more information).
   *
   * <h3>Advanced</h3>
   * Draw a cubic Bézier curve. The first and last points are
   * the on-curve points. The middle two are the 'control' points,
   * or 'handles' in an application like Illustrator.
   * <P>
   * Identical to typing:
   * <PRE>beginShape();
   * vertex(x1, y1);
   * bezierVertex(x2, y2, x3, y3, x4, y4);
   * endShape();
   * </PRE>
   * In Postscript-speak, this would be:
   * <PRE>moveto(x1, y1);
   * curveto(x2, y2, x3, y3, x4, y4);</PRE>
   * If you were to try and continue that curve like so:
   * <PRE>curveto(x5, y5, x6, y6, x7, y7);</PRE>
   * This would be done in processing by adding these statements:
   * <PRE>bezierVertex(x5, y5, x6, y6, x7, y7)
   * </PRE>
   * To draw a quadratic (instead of cubic) curve,
   * use the control point twice by doubling it:
   * <PRE>bezier(x1, y1, cx, cy, cx, cy, x2, y2);</PRE>
   *
   * @webref shape:curves
   * @webBrief Draws a Bézier curve on the screen
   * @param x1 coordinates for the first anchor point
   * @param y1 coordinates for the first anchor point
   * @param z1 coordinates for the first anchor point
   * @param x2 coordinates for the first control point
   * @param y2 coordinates for the first control point
   * @param z2 coordinates for the first control point
   * @param x3 coordinates for the second control point
   * @param y3 coordinates for the second control point
   * @param z3 coordinates for the second control point
   * @param x4 coordinates for the second anchor point
   * @param y4 coordinates for the second anchor point
   * @param z4 coordinates for the second anchor point
   *
   * @see PGraphics#bezierVertex(float, float, float, float, float, float)
   * @see PGraphics#curve(float, float, float, float, float, float, float, float, float, float, float, float)
   */
  public void bezier(float x1, float y1, float z1,
                     float x2, float y2, float z2,
                     float x3, float y3, float z3,
                     float x4, float y4, float z4) {
    if (recorder != null) recorder.bezier(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4);
    g.bezier(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4);
  }


  /**
   * Evaluates the curve at point <b>t</b> for points <b>a</b>, <b>b</b>,
   * <b>c</b>, <b>d</b>. The parameter <b>t</b> may range from 0 (the start of the
   * curve) and 1 (the end of the curve). <b>a</b> and <b>d</b> are the control
   * points, and <b>b</b> and <b>c</b> are points on the curve. As seen in the
   * example above, this can be used once with the <b>x</b> coordinates and a
   * second time with the <b>y</b> coordinates to get the location of a curve at
   * <b>t</b>.
   *
   * @webref shape:curves
   * @webBrief Evaluates the curve at point t for points a, b, c, d
   * @param a coordinate of first control point
   * @param b coordinate of first point on the curve
   * @param c coordinate of second point on the curve
   * @param d coordinate of second control point
   * @param t value between 0 and 1
   * @see PGraphics#curve(float, float, float, float, float, float, float, float,
   *      float, float, float, float)
   * @see PGraphics#curveVertex(float, float)
   * @see PGraphics#bezierPoint(float, float, float, float, float)
   */
  public float curvePoint(float a, float b, float c, float d, float t) {
    return g.curvePoint(a, b, c, d, t);
  }


  /**
   * Calculates the tangent of a point on a curve. There's a good definition
   * of <em><a href="http://en.wikipedia.org/wiki/Tangent"
   * target="new">tangent</em> on Wikipedia</a>.
   *
   * <h3>Advanced</h3>
   * Code thanks to Dave Bollinger (Bug #715)
   *
   * @webref shape:curves
   * @webBrief Calculates the tangent of a point on a curve
   * @param a coordinate of first point on the curve
   * @param b coordinate of first control point
   * @param c coordinate of second control point
   * @param d coordinate of second point on the curve
   * @param t value between 0 and 1
   * @see PGraphics#curve(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#curveVertex(float, float)
   * @see PGraphics#curvePoint(float, float, float, float, float)
   * @see PGraphics#bezierTangent(float, float, float, float, float)
   */
  public float curveTangent(float a, float b, float c, float d, float t) {
    return g.curveTangent(a, b, c, d, t);
  }


  /**
   * Sets the resolution at which curves display. The default value is 20.
   * This function is only useful when using the P3D renderer as the default
   * P2D renderer does not use this information.
   *
   * @webref shape:curves
   * @webBrief Sets the resolution at which curves display
   * @param detail resolution of the curves
   * @see PGraphics#curve(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#curveVertex(float, float)
   * @see PGraphics#curveTightness(float)
   */
  public void curveDetail(int detail) {
    if (recorder != null) recorder.curveDetail(detail);
    g.curveDetail(detail);
  }


  /**
   *
   * Modifies the quality of forms created with <b>curve()</b> and
   * <b>curveVertex()</b>. The parameter <b>tightness</b> determines how the curve
   * fits to the vertex points. The value 0.0 is the default value for
   * <b>tightness</b> (this value defines the curves to be Catmull-Rom splines)
   * and the value 1.0 connects all the points with straight lines. Values within
   * the range -5.0 and 5.0 will deform the curves but will leave them
   * recognizable and as values increase in magnitude, they will continue to
   * deform.
   *
   * @webref shape:curves
   * @webBrief Modifies the quality of forms created with <b>curve()</b> and
   *           <b>curveVertex()</b>
   * @param tightness amount of deformation from the original vertices
   * @see PGraphics#curve(float, float, float, float, float, float, float, float,
   *      float, float, float, float)
   * @see PGraphics#curveVertex(float, float)
   */
  public void curveTightness(float tightness) {
    if (recorder != null) recorder.curveTightness(tightness);
    g.curveTightness(tightness);
  }


  /**
   *
   * Draws a curved line on the screen. The first and second parameters
   * specify the beginning control point and the last two parameters specify
   * the ending control point. The middle parameters specify the start and
   * stop of the curve. Longer curves can be created by putting a series of
   * <b>curve()</b> functions together or using <b>curveVertex()</b>. An
   * additional function called <b>curveTightness()</b> provides control for
   * the visual quality of the curve. The <b>curve()</b> function is an
   * implementation of Catmull-Rom splines. Using the 3D version requires
   * rendering with P3D (see the Environment reference for more information).
   *
   * <h3>Advanced</h3>
   * As of revision 0070, this function no longer doubles the first
   * and last points. The curves are a bit more boring, but it's more
   * mathematically correct, and properly mirrored in curvePoint().
   * <p/>
   * Identical to typing out:<PRE>
   * beginShape();
   * curveVertex(x1, y1);
   * curveVertex(x2, y2);
   * curveVertex(x3, y3);
   * curveVertex(x4, y4);
   * endShape();
   * </PRE>
   *
   * @webref shape:curves
   * @webBrief Draws a curved line on the screen
   * @param x1 coordinates for the beginning control point
   * @param y1 coordinates for the beginning control point
   * @param x2 coordinates for the first point
   * @param y2 coordinates for the first point
   * @param x3 coordinates for the second point
   * @param y3 coordinates for the second point
   * @param x4 coordinates for the ending control point
   * @param y4 coordinates for the ending control point
   * @see PGraphics#curveVertex(float, float)
   * @see PGraphics#curveTightness(float)
   * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
   */
  public void curve(float x1, float y1,
                    float x2, float y2,
                    float x3, float y3,
                    float x4, float y4) {
    if (recorder != null) recorder.curve(x1, y1, x2, y2, x3, y3, x4, y4);
    g.curve(x1, y1, x2, y2, x3, y3, x4, y4);
  }


   /**
    * @param z1 coordinates for the beginning control point
    * @param z2 coordinates for the first point
    * @param z3 coordinates for the second point
    * @param z4 coordinates for the ending control point
    */
  public void curve(float x1, float y1, float z1,
                    float x2, float y2, float z2,
                    float x3, float y3, float z3,
                    float x4, float y4, float z4) {
    if (recorder != null) recorder.curve(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4);
    g.curve(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4);
  }


  /**
   *
   * Modifies the location from which images are drawn by changing the way in
   * which parameters given to <b>image()</b> are interpreted.<br />
   * <br />
   * The default mode is <b>imageMode(CORNER)</b>, which interprets the second and
   * third parameters of <b>image()</b> as the upper-left corner of the image. If
   * two additional parameters are specified, they are used to set the image's
   * width and height.<br />
   * <br />
   * <b>imageMode(CORNERS)</b> interprets the second and third parameters of
   * <b>image()</b> as the location of one corner, and the fourth and fifth
   * parameters as the opposite corner.<br />
   * <br />
   * <b>imageMode(CENTER)</b> interprets the second and third parameters of
   * <b>image()</b> as the image's center point. If two additional parameters are
   * specified, they are used to set the image's width and height.<br />
   * <br />
   * The parameter must be written in ALL CAPS because Processing is a
   * case-sensitive language.
   *
   * @webref image:loading & displaying
   * @webBrief Modifies the location from which images draw
   * @param mode either CORNER, CORNERS, or CENTER
   * @see PApplet#loadImage(String, String)
   * @see PImage
   * @see PGraphics#image(PImage, float, float, float, float)
   * @see PGraphics#background(float, float, float, float)
   */
  public void imageMode(int mode) {
    if (recorder != null) recorder.imageMode(mode);
    g.imageMode(mode);
  }


  /**
   *
   * The <b>image()</b> function draws an image to the display window. Images must
   * be in the sketch's "data" directory to load correctly. Select "Add file..."
   * from the "Sketch" menu to add the image to the data directory, or just drag
   * the image file onto the sketch window. Processing currently works with GIF,
   * JPEG, and PNG images. <br />
   * <br />
   * The <b>img</b> parameter specifies the image to display and by default the
   * <b>a</b> and <b>b</b> parameters define the location of its upper-left
   * corner. The image is displayed at its original size unless the <b>c</b> and
   * <b>d</b> parameters specify a different size. The <b>imageMode()</b> function
   * can be used to change the way these parameters draw the image.<br />
   * <br />
   * The color of an image may be modified with the <b>tint()</b> function. This
   * function will maintain transparency for GIF and PNG images.
   *
   * <h3>Advanced</h3> Starting with release 0124, when using the default (JAVA2D)
   * renderer, smooth() will also improve image quality of resized images.
   *
   * @webref image:loading & displaying
   * @webBrief Displays images to the screen
   * @param img the image to display
   * @param a   x-coordinate of the image by default
   * @param b   y-coordinate of the image by default
   * @see PApplet#loadImage(String, String)
   * @see PImage
   * @see PGraphics#imageMode(int)
   * @see PGraphics#tint(float)
   * @see PGraphics#background(float, float, float, float)
   * @see PGraphics#alpha(int)
   */
  public void image(PImage img, float a, float b) {
    if (recorder != null) recorder.image(img, a, b);
    g.image(img, a, b);
  }


  /**
   * @param c width to display the image by default
   * @param d height to display the image by default
   */
  public void image(PImage img, float a, float b, float c, float d) {
    if (recorder != null) recorder.image(img, a, b, c, d);
    g.image(img, a, b, c, d);
  }


  /**
   * Draw an image(), also specifying u/v coordinates.
   * In this method, the  u, v coordinates are always based on image space
   * location, regardless of the current textureMode().
   *
   * @nowebref
   */
  public void image(PImage img,
                    float a, float b, float c, float d,
                    int u1, int v1, int u2, int v2) {
    if (recorder != null) recorder.image(img, a, b, c, d, u1, v1, u2, v2);
    g.image(img, a, b, c, d, u1, v1, u2, v2);
  }


  /**
   *
   * Modifies the location from which shapes draw. The default mode is
   * <b>shapeMode(CORNER)</b>, which specifies the location to be the upper
   * left corner of the shape and uses the third and fourth parameters of
   * <b>shape()</b> to specify the width and height. The syntax
   * <b>shapeMode(CORNERS)</b> uses the first and second parameters of
   * <b>shape()</b> to set the location of one corner and uses the third and
   * fourth parameters to set the opposite corner. The syntax
   * <b>shapeMode(CENTER)</b> draws the shape from its center point and uses
   * the third and forth parameters of <b>shape()</b> to specify the width
   * and height. The parameter must be written in "ALL CAPS" because
   * Processing is a case-sensitive language.
   *
   * @webref shape:loading & displaying
   * @webBrief Modifies the location from which shapes draw
   * @param mode either CORNER, CORNERS, CENTER
   * @see PShape
   * @see PGraphics#shape(PShape)
   * @see PGraphics#rectMode(int)
   */
  public void shapeMode(int mode) {
    if (recorder != null) recorder.shapeMode(mode);
    g.shapeMode(mode);
  }


  public void shape(PShape shape) {
    if (recorder != null) recorder.shape(shape);
    g.shape(shape);
  }


  /**
   * Draws shapes to the display window. Shapes must be in the sketch's "data"
   * directory to load correctly. Select "Add file..." from the "Sketch" menu to
   * add the shape. Processing currently works with SVG, OBJ, and custom-created
   * shapes. The <b>shape</b> parameter specifies the shape to display and the
   * coordinate parameters define the location of the shape from its upper-left
   * corner. The shape is displayed at its original size unless the <b>c</b> and
   * <b>d</b> parameters specify a different size. The <b>shapeMode()</b> function
   * can be used to change the way these parameters are interpreted.
   *
   * @webref shape:loading & displaying
   * @webBrief Displays shapes to the screen
   * @param shape the shape to display
   * @param x     x-coordinate of the shape
   * @param y     y-coordinate of the shape
   * @see PShape
   * @see PApplet#loadShape(String)
   * @see PGraphics#shapeMode(int)
   */
  public void shape(PShape shape, float x, float y) {
    if (recorder != null) recorder.shape(shape, x, y);
    g.shape(shape, x, y);
  }


  /**
   * @param a x-coordinate of the shape
   * @param b y-coordinate of the shape
   * @param c width to display the shape
   * @param d height to display the shape
   */
  public void shape(PShape shape, float a, float b, float c, float d) {
    if (recorder != null) recorder.shape(shape, a, b, c, d);
    g.shape(shape, a, b, c, d);
  }


  public void textAlign(int alignX) {
    if (recorder != null) recorder.textAlign(alignX);
    g.textAlign(alignX);
  }


  /**
   * Sets the current alignment for drawing text. The parameters LEFT, CENTER, and
   * RIGHT set the display characteristics of the letters in relation to the
   * values for the <b>x</b> and <b>y</b> parameters of the <b>text()</b>
   * function. <br />
   * <br />
   * An optional second parameter can be used to vertically align the text.
   * BASELINE is the default, and the vertical alignment will be reset to BASELINE
   * if the second parameter is not used. The TOP and CENTER parameters are
   * straightforward. The BOTTOM parameter offsets the line based on the current
   * <b>textDescent()</b>. For multiple lines, the final line will be aligned to
   * the bottom, with the previous lines appearing above it. <br />
   * <br />
   * When using <b>text()</b> with width and height parameters, BASELINE is
   * ignored, and treated as TOP. (Otherwise, text would by default draw outside
   * the box, since BASELINE is the default setting. BASELINE is not a useful
   * drawing mode for text drawn in a rectangle.) <br />
   * <br />
   * The vertical alignment is based on the value of <b>textAscent()</b>, which
   * many fonts do not specify correctly. It may be necessary to use a hack and
   * offset by a few pixels by hand so that the offset looks correct. To do this
   * as less of a hack, use some percentage of <b>textAscent()</b> or
   * <b>textDescent()</b> so that the hack works even if you change the size of
   * the font.
   *
   * @webref typography:attributes
   * @webBrief Sets the current alignment for drawing text
   * @param alignX horizontal alignment, either LEFT, CENTER, or RIGHT
   * @param alignY vertical alignment, either TOP, BOTTOM, CENTER, or BASELINE
   * @see PApplet#loadFont(String)
   * @see PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textSize(float)
   * @see PGraphics#textAscent()
   * @see PGraphics#textDescent()
   */
  public void textAlign(int alignX, int alignY) {
    if (recorder != null) recorder.textAlign(alignX, alignY);
    g.textAlign(alignX, alignY);
  }


  /**
   * Returns ascent of the current font at its current size. This information is
   * useful for determining the height of the font above the baseline.
   *
   * @webref typography:metrics
   * @webBrief Returns ascent of the current font at its current size
   * @see PGraphics#textDescent()
   */
  public float textAscent() {
    return g.textAscent();
  }


  /**
   * Returns descent of the current font at its current size. This information is
   * useful for determining the height of the font below the baseline.
   *
   * @webref typography:metrics
   * @webBrief Returns descent of the current font at its current size
   * @see PGraphics#textAscent()
   */
  public float textDescent() {
    return g.textDescent();
  }


  /**
   *
   * Sets the current font that will be drawn with the <b>text()</b> function.
   * Fonts must be created for Processing with <b>createFont()</b> or loaded with
   * <b>loadFont()</b> before they can be used. The font set through
   * <b>textFont()</b> will be used in all subsequent calls to the <b>text()</b>
   * function. If no <b>size</b> parameter is specified, the font size defaults to
   * the original size (the size in which it was created with the "Create Font..."
   * tool) overriding any previous calls to <b>textFont()</b> or
   * <b>textSize()</b>.<br />
   * <br />
   * When fonts are rendered as an image texture (as is the case with the P2D and
   * P3D renderers as well as with <b>loadFont()</b> and vlw files), you should
   * create fonts at the sizes that will be used most commonly. Using
   * <b>textFont()</b> without the size parameter will result in the cleanest
   * type.<br />
   * <br />
   *
   *
   * @webref typography:loading & displaying
   * @webBrief Sets the current font that will be drawn with the <b>text()</b>
   *           function
   * @param which any variable of the type PFont
   * @see PApplet#createFont(String, float, boolean)
   * @see PApplet#loadFont(String)
   * @see PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textSize(float)
   */
  public void textFont(PFont which) {
    if (recorder != null) recorder.textFont(which);
    g.textFont(which);
  }


  /**
   * @param size the size of the letters in units of pixels
   */
  public void textFont(PFont which, float size) {
    if (recorder != null) recorder.textFont(which, size);
    g.textFont(which, size);
  }


  /**
   * Sets the spacing between lines of text in units of pixels. This setting will
   * be used in all subsequent calls to the <b>text()</b> function. Note, however,
   * that the leading is reset by <b>textSize()</b>. For example, if the leading
   * is set to 20 with <b>textLeading(20)</b>, then if <b>textSize(48)</b> is run
   * at a later point, the leading will be reset to the default for the text size
   * of 48.
   *
   * @webref typography:attributes
   * @webBrief Sets the spacing between lines of text in units of pixels
   * @param leading the size in pixels for spacing between lines
   * @see PApplet#loadFont(String)
   * @see PFont#PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textFont(PFont)
   * @see PGraphics#textSize(float)
   */
  public void textLeading(float leading) {
    if (recorder != null) recorder.textLeading(leading);
    g.textLeading(leading);
  }


  /**
   * Sets the way text draws to the screen, either as texture maps or as vector
   * geometry. The default <b>textMode(MODEL)</b>, uses textures to render the
   * fonts. The <b>textMode(SHAPE)</b> mode draws text using the glyph outlines of
   * individual characters rather than as textures. This mode is only supported
   * with the <b>PDF</b> and <b>P3D</b> renderer settings. With the <b>PDF</b>
   * renderer, you must call <b>textMode(SHAPE)</b> before any other drawing
   * occurs. If the outlines are not available, then <b>textMode(SHAPE)</b> will
   * be ignored and <b>textMode(MODEL)</b> will be used instead.<br />
   * <br />
   * The <b>textMode(SHAPE)</b> option in <b>P3D</b> can be combined with
   * <b>beginRaw()</b> to write vector-accurate text to 2D and 3D output files,
   * for instance <b>DXF</b> or <b>PDF</b>. The <b>SHAPE</b> mode is not currently
   * optimized for <b>P3D</b>, so if recording shape data, use
   * <b>textMode(MODEL)</b> until you're ready to capture the geometry with
   * <b>beginRaw()</b>.
   *
   * @webref typography:attributes
   * @webBrief Sets the way text draws to the screen
   * @param mode either MODEL or SHAPE
   * @see PApplet#loadFont(String)
   * @see PFont#PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textFont(PFont)
   * @see PGraphics#beginRaw(PGraphics)
   * @see PApplet#createFont(String, float, boolean)
   */
  public void textMode(int mode) {
    if (recorder != null) recorder.textMode(mode);
    g.textMode(mode);
  }


  /**
   * Sets the current font size. This size will be used in all subsequent
   * calls to the <b>text()</b> function. Font size is measured in units of pixels.
   *
   * @webref typography:attributes
   * @webBrief Sets the current font size
   * @param size the size of the letters in units of pixels
   * @see PApplet#loadFont(String)
   * @see PFont#PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textFont(PFont)
   */
  public void textSize(float size) {
    if (recorder != null) recorder.textSize(size);
    g.textSize(size);
  }


  /**
   * @param c the character to measure
   */
  public float textWidth(char c) {
    return g.textWidth(c);
  }


  /**
   * Calculates and returns the width of any character or text string.
   *
   * @webref typography:attributes
   * @webBrief Calculates and returns the width of any character or text string
   * @param str the String of characters to measure
   * @see PApplet#loadFont(String)
   * @see PFont#PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textFont(PFont)
   * @see PGraphics#textSize(float)
   */
  public float textWidth(String str) {
    return g.textWidth(str);
  }


  /**
   * @nowebref
   */
  public float textWidth(char[] chars, int start, int length) {
    return g.textWidth(chars, start, length);
  }


  /**
   * Draws text to the screen. Displays the information specified in the first
   * parameter on the screen in the position specified by the additional
   * parameters. A default font will be used unless a font is set with the
   * <b>textFont()</b> function and a default size will be used unless a font is
   * set with <b>textSize()</b>. Change the color of the text with the
   * <b>fill()</b> function. The text displays in relation to the
   * <b>textAlign()</b> function, which gives the option to draw to the left,
   * right, and center of the coordinates.<br />
   * <br />
   * The <b>x2</b> and <b>y2</b> parameters define a rectangular area to display
   * within and may only be used with string data. When these parameters are
   * specified, they are interpreted based on the current <b>rectMode()</b>
   * setting. Text that does not fit completely within the rectangle specified
   * will not be drawn to the screen.<br />
   * <br />
   * Note that Processing now lets you call <b>text()</b> without first specifying
   * a PFont with <b>textFont()</b>. In that case, a generic sans-serif font will
   * be used instead. (See the third example above.)
   *
   * @webref typography:loading & displaying
   * @webBrief Draws text to the screen
   * @param c the alphanumeric character to be displayed
   * @param x x-coordinate of text
   * @param y y-coordinate of text
   * @see PGraphics#textAlign(int, int)
   * @see PGraphics#textFont(PFont)
   * @see PGraphics#textMode(int)
   * @see PGraphics#textSize(float)
   * @see PGraphics#textLeading(float)
   * @see PGraphics#textWidth(String)
   * @see PGraphics#textAscent()
   * @see PGraphics#textDescent()
   * @see PGraphics#rectMode(int)
   * @see PGraphics#fill(int, float)
   * @see_external String
   */
  public void text(char c, float x, float y) {
    if (recorder != null) recorder.text(c, x, y);
    g.text(c, x, y);
  }


  /**
   * @param z z-coordinate of text
   */
  public void text(char c, float x, float y, float z) {
    if (recorder != null) recorder.text(c, x, y, z);
    g.text(c, x, y, z);
  }


  /**
   * <h3>Advanced</h3>
   * Draw a chunk of text.
   * Newlines that are \n (Unix newline or linefeed char, ascii 10)
   * are honored, but \r (carriage return, Windows and Mac OS) are
   * ignored.
   */
  public void text(String str, float x, float y) {
    if (recorder != null) recorder.text(str, x, y);
    g.text(str, x, y);
  }


  /**
   * <h3>Advanced</h3>
   * Method to draw text from an array of chars. This method will usually be
   * more efficient than drawing from a String object, because the String will
   * not be converted to a char array before drawing.
   * @param chars the alphanumeric symbols to be displayed
   * @param start array index at which to start writing characters
   * @param stop array index at which to stop writing characters
   */
  public void text(char[] chars, int start, int stop, float x, float y) {
    if (recorder != null) recorder.text(chars, start, stop, x, y);
    g.text(chars, start, stop, x, y);
  }


  /**
   * Same as above but with a z coordinate.
   */
  public void text(String str, float x, float y, float z) {
    if (recorder != null) recorder.text(str, x, y, z);
    g.text(str, x, y, z);
  }


  public void text(char[] chars, int start, int stop,
                   float x, float y, float z) {
    if (recorder != null) recorder.text(chars, start, stop, x, y, z);
    g.text(chars, start, stop, x, y, z);
  }


  /**
   * <h3>Advanced</h3>
   * Draw text in a box that is constrained to a particular size.
   * The current rectMode() determines what the coordinates mean
   * (whether x1/y1/x2/y2 or x/y/w/h).
   * <P/>
   * Note that the x,y coords of the start of the box
   * will align with the *ascent* of the text, not the baseline,
   * as is the case for the other text() functions.
   * <P/>
   * Newlines that are \n (Unix newline or linefeed char, ascii 10)
   * are honored, and \r (carriage return, Windows and Mac OS) are
   * ignored.
   *
   * @param x1 by default, the x-coordinate of text, see rectMode() for more info
   * @param y1 by default, the y-coordinate of text, see rectMode() for more info
   * @param x2 by default, the width of the text box, see rectMode() for more info
   * @param y2 by default, the height of the text box, see rectMode() for more info
   */
  public void text(String str, float x1, float y1, float x2, float y2) {
    if (recorder != null) recorder.text(str, x1, y1, x2, y2);
    g.text(str, x1, y1, x2, y2);
  }


  public void text(int num, float x, float y) {
    if (recorder != null) recorder.text(num, x, y);
    g.text(num, x, y);
  }


  public void text(int num, float x, float y, float z) {
    if (recorder != null) recorder.text(num, x, y, z);
    g.text(num, x, y, z);
  }


  /**
   * This does a basic number formatting, to avoid the
   * generally ugly appearance of printing floats.
   * Users who want more control should use their own nf() command,
   * or if they want the long, ugly version of float,
   * use String.valueOf() to convert the float to a String first.
   *
   * @param num the numeric value to be displayed
   */
  public void text(float num, float x, float y) {
    if (recorder != null) recorder.text(num, x, y);
    g.text(num, x, y);
  }


  public void text(float num, float x, float y, float z) {
    if (recorder != null) recorder.text(num, x, y, z);
    g.text(num, x, y, z);
  }


  /**
   *
   * The <b>push()</b> function saves the current drawing style
   * settings and transformations, while <b>pop()</b> restores these
   * settings. Note that these functions are always used together.
   * They allow you to change the style and transformation settings
   * and later return to what you had. When a new state is started
   * with push(), it builds on the current style and transform
   * information.<br />
   * <br />
   * <b>push()</b> stores information related to the current
   * transformation state and style settings controlled by the
   * following functions: <b>rotate()</b>, <b>translate()</b>,
   * <b>scale()</b>, <b>fill()</b>, <b>stroke()</b>, <b>tint()</b>,
   * <b>strokeWeight()</b>, <b>strokeCap()</b>, <b>strokeJoin()</b>,
   * <b>imageMode()</b>, <b>rectMode()</b>, <b>ellipseMode()</b>,
   * <b>colorMode()</b>, <b>textAlign()</b>, <b>textFont()</b>,
   * <b>textMode()</b>, <b>textSize()</b>, <b>textLeading()</b>.<br />
   * <br />
   * The <b>push()</b> and <b>pop()</b> functions were added with
   * Processing 3.5. They can be used in place of <b>pushMatrix()</b>,
   * <b>popMatrix()</b>, <b>pushStyles()</b>, and <b>popStyles()</b>.
   * The difference is that push() and pop() control both the
   * transformations (rotate, scale, translate) and the drawing styles
   * at the same time.
   *
   * @webref structure
   * @webBrief The <b>push()</b> function saves the current drawing style
   * settings and transformations, while <b>pop()</b> restores these
   * settings
   * @see PGraphics#pop()
   */
  public void push() {
    if (recorder != null) recorder.push();
    g.push();
  }


  /**
   *
   * The <b>pop()</b> function restores the previous drawing style
   * settings and transformations after <b>push()</b> has changed them.
   * Note that these functions are always used together. They allow
   * you to change the style and transformation settings and later
   * return to what you had. When a new state is started with push(),
   * it builds on the current style and transform information.
   * <br />
   * <br />
   * <b>push()</b> stores information related to the current
   * transformation state and style settings controlled by the
   * following functions: <b>rotate()</b>, <b>translate()</b>,
   * <b>scale()</b>, <b>fill()</b>, <b>stroke()</b>, <b>tint()</b>,
   * <b>strokeWeight()</b>, <b>strokeCap()</b>, <b>strokeJoin()</b>,
   * <b>imageMode()</b>, <b>rectMode()</b>, <b>ellipseMode()</b>,
   * <b>colorMode()</b>, <b>textAlign()</b>, <b>textFont()</b>,
   * <b>textMode()</b>, <b>textSize()</b>, <b>textLeading()</b>.<br />
   * <br />
   * The <b>push()</b> and <b>pop()</b> functions were added with
   * Processing 3.5. They can be used in place of <b>pushMatrix()</b>,
   * <b>popMatrix()</b>, <b>pushStyles()</b>, and <b>popStyles()</b>.
   * The difference is that push() and pop() control both the
   * transformations (rotate, scale, translate) and the drawing styles
   * at the same time.
   *
   * @webref structure
   * @webBrief The <b>pop()</b> function restores the previous drawing style
   * settings and transformations after <b>push()</b> has changed them
   * @see PGraphics#push()
   */
  public void pop() {
    if (recorder != null) recorder.pop();
    g.pop();
  }


  /**
   * Pushes the current transformation matrix onto the matrix stack.
   * Understanding <b>pushMatrix()</b> and <b>popMatrix()</b> requires
   * understanding the concept of a matrix stack. The <b>pushMatrix()</b>
   * function saves the current coordinate system to the stack and
   * <b>popMatrix()</b> restores the prior coordinate system.
   * <b>pushMatrix()</b> and <b>popMatrix()</b> are used in conjunction with
   * the other transformation functions and may be embedded to control the
   * scope of the transformations.
   *
   * @webref transform
   * @webBrief Pushes the current transformation matrix onto the matrix stack
   * @see PGraphics#popMatrix()
   * @see PGraphics#translate(float, float, float)
   * @see PGraphics#scale(float)
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#rotateZ(float)
   */
  public void pushMatrix() {
    if (recorder != null) recorder.pushMatrix();
    g.pushMatrix();
  }


  /**
   * Pops the current transformation matrix off the matrix stack.
   * Understanding pushing and popping requires understanding the concept of
   * a matrix stack. The <b>pushMatrix()</b> function saves the current
   * coordinate system to the stack and <b>popMatrix()</b> restores the prior
   * coordinate system. <b>pushMatrix()</b> and <b>popMatrix()</b> are used
   * in conjunction with the other transformation functions and may be
   * embedded to control the scope of the transformations.
   *
   * @webref transform
   * @webBrief Pops the current transformation matrix off the matrix stack
   * @see PGraphics#pushMatrix()
   */
  public void popMatrix() {
    if (recorder != null) recorder.popMatrix();
    g.popMatrix();
  }


  /**
   * Specifies an amount to displace objects within the display window. The
   * <b>x</b> parameter specifies left/right translation, the <b>y</b> parameter
   * specifies up/down translation, and the <b>z</b> parameter specifies
   * translations toward/away from the screen. Using this function with the
   * <b>z</b> parameter requires using P3D as a parameter in combination with size
   * as shown in the above example.
   * <p/>
   * Transformations are cumulative and apply to everything that happens after and
   * subsequent calls to the function accumulates the effect. For example, calling
   * <b>translate(50, 0)</b> and then <b>translate(20, 0)</b> is the same as
   * <b>translate(70, 0)</b>. If <b>translate()</b> is called within
   * <b>draw()</b>, the transformation is reset when the loop begins again. This
   * function can be further controlled by using <b>pushMatrix()</b> and
   * <b>popMatrix()</b>.
   *
   * @webref transform
   * @webBrief Specifies an amount to displace objects within the display window
   * @param x left/right translation
   * @param y up/down translation
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#rotateZ(float)
   * @see PGraphics#scale(float, float, float)
   */
  public void translate(float x, float y) {
    if (recorder != null) recorder.translate(x, y);
    g.translate(x, y);
  }


  /**
   * @param z forward/backward translation
   */
  public void translate(float x, float y, float z) {
    if (recorder != null) recorder.translate(x, y, z);
    g.translate(x, y, z);
  }


  /**
   *
   * Rotates a shape the amount specified by the <b>angle</b> parameter.
   * Angles should be specified in radians (values from 0 to TWO_PI) or
   * converted to radians with the <b>radians()</b> function.
   * <br/> <br/>
   * Objects are always rotated around their relative position to the origin
   * and positive numbers rotate objects in a clockwise direction.
   * Transformations apply to everything that happens after and subsequent
   * calls to the function accumulates the effect. For example, calling
   * <b>rotate(HALF_PI)</b> and then <b>rotate(HALF_PI)</b> is the same as
   * <b>rotate(PI)</b>. All transformations are reset when <b>draw()</b>
   * begins again.
   * <br/> <br/>
   * Technically, <b>rotate()</b> multiplies the current transformation
   * matrix by a rotation matrix. This function can be further controlled by
   * the <b>pushMatrix()</b> and <b>popMatrix()</b>.
   *
   * @webref transform
   * @webBrief Rotates a shape the amount specified by the <b>angle</b> parameter
   * @param angle angle of rotation specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#rotateZ(float)
   * @see PGraphics#scale(float, float, float)
   * @see PApplet#radians(float)
   */
  public void rotate(float angle) {
    if (recorder != null) recorder.rotate(angle);
    g.rotate(angle);
  }


  /**
   *
   * Rotates a shape around the x-axis the amount specified by the
   * <b>angle</b> parameter. Angles should be specified in radians (values
   * from 0 to PI*2) or converted to radians with the <b>radians()</b>
   * function. Objects are always rotated around their relative position to
   * the origin and positive numbers rotate objects in a counterclockwise
   * direction. Transformations apply to everything that happens after and
   * subsequent calls to the function accumulates the effect. For example,
   * calling <b>rotateX(PI/2)</b> and then <b>rotateX(PI/2)</b> is the same
   * as <b>rotateX(PI)</b>. If <b>rotateX()</b> is called within the
   * <b>draw()</b>, the transformation is reset when the loop begins again.
   * This function requires using P3D as a third parameter to <b>size()</b>
   * as shown in the example above.
   *
   * @webref transform
   * @webBrief Rotates a shape around the x-axis the amount specified by the
   * <b>angle</b> parameter
   * @param angle angle of rotation specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#rotateZ(float)
   * @see PGraphics#scale(float, float, float)
   * @see PGraphics#translate(float, float, float)
   */
  public void rotateX(float angle) {
    if (recorder != null) recorder.rotateX(angle);
    g.rotateX(angle);
  }


  /**
   *
   * Rotates a shape around the y-axis the amount specified by the
   * <b>angle</b> parameter. Angles should be specified in radians (values
   * from 0 to PI*2) or converted to radians with the <b>radians()</b>
   * function. Objects are always rotated around their relative position to
   * the origin and positive numbers rotate objects in a counterclockwise
   * direction. Transformations apply to everything that happens after and
   * subsequent calls to the function accumulates the effect. For example,
   * calling <b>rotateY(PI/2)</b> and then <b>rotateY(PI/2)</b> is the same
   * as <b>rotateY(PI)</b>. If <b>rotateY()</b> is called within the
   * <b>draw()</b>, the transformation is reset when the loop begins again.
   * This function requires using P3D as a third parameter to <b>size()</b>
   * as shown in the examples above.
   *
   * @webref transform
   * @webBrief Rotates a shape around the y-axis the amount specified by the
   * <b>angle</b> parameter
   * @param angle angle of rotation specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateZ(float)
   * @see PGraphics#scale(float, float, float)
   * @see PGraphics#translate(float, float, float)
   */
  public void rotateY(float angle) {
    if (recorder != null) recorder.rotateY(angle);
    g.rotateY(angle);
  }


  /**
   *
   * Rotates a shape around the z-axis the amount specified by the
   * <b>angle</b> parameter. Angles should be specified in radians (values
   * from 0 to PI*2) or converted to radians with the <b>radians()</b>
   * function. Objects are always rotated around their relative position to
   * the origin and positive numbers rotate objects in a counterclockwise
   * direction. Transformations apply to everything that happens after and
   * subsequent calls to the function accumulates the effect. For example,
   * calling <b>rotateZ(PI/2)</b> and then <b>rotateZ(PI/2)</b> is the same
   * as <b>rotateZ(PI)</b>. If <b>rotateZ()</b> is called within the
   * <b>draw()</b>, the transformation is reset when the loop begins again.
   * This function requires using P3D as a third parameter to <b>size()</b>
   * as shown in the examples above.
   *
   * @webref transform
   * @webBrief Rotates a shape around the z-axis the amount specified by the
   * <b>angle</b> parameter
   * @param angle angle of rotation specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#scale(float, float, float)
   * @see PGraphics#translate(float, float, float)
   */
  public void rotateZ(float angle) {
    if (recorder != null) recorder.rotateZ(angle);
    g.rotateZ(angle);
  }


  /**
   * <h3>Advanced</h3>
   * Rotate about a vector in space. Same as the glRotatef() function.
   * @nowebref
   */
  public void rotate(float angle, float x, float y, float z) {
    if (recorder != null) recorder.rotate(angle, x, y, z);
    g.rotate(angle, x, y, z);
  }


  /**
   *
   * Increases or decreases the size of a shape by expanding and contracting
   * vertices. Objects always scale from their relative origin to the coordinate
   * system. Scale values are specified as decimal percentages. For example, the
   * function call <b>scale(2.0)</b> increases the dimension of a shape by
   * 200%.<br />
   * <br />
   * Transformations apply to everything that happens after and subsequent calls
   * to the function multiply the effect. For example, calling <b>scale(2.0)</b>
   * and then <b>scale(1.5)</b> is the same as <b>scale(3.0)</b>. If
   * <b>scale()</b> is called within <b>draw()</b>, the transformation is reset
   * when the loop begins again. Using this function with the <b>z</b> parameter
   * requires using P3D as a parameter for <b>size()</b>, as shown in the third
   * example above. This function can be further controlled with
   * <b>pushMatrix()</b> and <b>popMatrix()</b>.
   *
   * @webref transform
   * @webBrief Increases or decreases the size of a shape by expanding and
   *           contracting vertices
   * @param s percentage to scale the object
   * @see PGraphics#pushMatrix()
   * @see PGraphics#popMatrix()
   * @see PGraphics#translate(float, float, float)
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#rotateZ(float)
   */
  public void scale(float s) {
    if (recorder != null) recorder.scale(s);
    g.scale(s);
  }


  /**
   * <h3>Advanced</h3>
   * Scale in X and Y. Equivalent to scale(sx, sy, 1).
   * <p/>
   * Not recommended for use in 3D, because the z-dimension is just
   * scaled by 1, since there's no way to know what else to scale it by.
   *
   * @param x percentage to scale the object in the x-axis
   * @param y percentage to scale the object in the y-axis
   */
  public void scale(float x, float y) {
    if (recorder != null) recorder.scale(x, y);
    g.scale(x, y);
  }


  /**
   * @param z percentage to scale the object in the z-axis
   */
  public void scale(float x, float y, float z) {
    if (recorder != null) recorder.scale(x, y, z);
    g.scale(x, y, z);
  }


  /**
   *
   * Shears a shape around the x-axis the amount specified by the
   * <b>angle</b> parameter. Angles should be specified in radians (values
   * from 0 to PI*2) or converted to radians with the <b>radians()</b>
   * function. Objects are always sheared around their relative position to
   * the origin and positive numbers shear objects in a clockwise direction.
   * Transformations apply to everything that happens after and subsequent
   * calls to the function accumulates the effect. For example, calling
   * <b>shearX(PI/2)</b> and then <b>shearX(PI/2)</b> is the same as
   * <b>shearX(PI)</b>. If <b>shearX()</b> is called within the
   * <b>draw()</b>, the transformation is reset when the loop begins again.
   * <br/> <br/>
   * Technically, <b>shearX()</b> multiplies the current transformation
   * matrix by a rotation matrix. This function can be further controlled by
   * the <b>pushMatrix()</b> and <b>popMatrix()</b> functions.
   *
   * @webref transform
   * @webBrief Shears a shape around the x-axis the amount specified by the
   * <b>angle</b> parameter
   * @param angle angle of shear specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#shearY(float)
   * @see PGraphics#scale(float, float, float)
   * @see PGraphics#translate(float, float, float)
   * @see PApplet#radians(float)
   */
  public void shearX(float angle) {
    if (recorder != null) recorder.shearX(angle);
    g.shearX(angle);
  }


  /**
   *
   * Shears a shape around the y-axis the amount specified by the
   * <b>angle</b> parameter. Angles should be specified in radians (values
   * from 0 to PI*2) or converted to radians with the <b>radians()</b>
   * function. Objects are always sheared around their relative position to
   * the origin and positive numbers shear objects in a clockwise direction.
   * Transformations apply to everything that happens after and subsequent
   * calls to the function accumulates the effect. For example, calling
   * <b>shearY(PI/2)</b> and then <b>shearY(PI/2)</b> is the same as
   * <b>shearY(PI)</b>. If <b>shearY()</b> is called within the
   * <b>draw()</b>, the transformation is reset when the loop begins again.
   * <br/> <br/>
   * Technically, <b>shearY()</b> multiplies the current transformation
   * matrix by a rotation matrix. This function can be further controlled by
   * the <b>pushMatrix()</b> and <b>popMatrix()</b> functions.
   *
   * @webref transform
   * @webBrief Shears a shape around the y-axis the amount specified by the
   * <b>angle</b> parameter
   * @param angle angle of shear specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#shearX(float)
   * @see PGraphics#scale(float, float, float)
   * @see PGraphics#translate(float, float, float)
   * @see PApplet#radians(float)
   */
  public void shearY(float angle) {
    if (recorder != null) recorder.shearY(angle);
    g.shearY(angle);
  }


  /**
   * Replaces the current matrix with the identity matrix.
   * The equivalent function in OpenGL is <b>glLoadIdentity()</b>.
   *
   * @webref transform
   * @webBrief Replaces the current matrix with the identity matrix
   * @see PGraphics#pushMatrix()
   * @see PGraphics#popMatrix()
   * @see PGraphics#applyMatrix(PMatrix)
   * @see PGraphics#printMatrix()
   */
  public void resetMatrix() {
    if (recorder != null) recorder.resetMatrix();
    g.resetMatrix();
  }


  /**
   * Multiplies the current matrix by the one specified through the
   * parameters. This is very slow because it will try to calculate the
   * inverse of the transform, so avoid it whenever possible. The equivalent
   * function in OpenGL is <b>glMultMatrix()</b>.
   *
   * @webref transform
   * @webBrief Multiplies the current matrix by the one specified in the
   * parameter
   * @see PGraphics#pushMatrix()
   * @see PGraphics#popMatrix()
   * @see PGraphics#resetMatrix()
   * @see PGraphics#printMatrix()
   */
  public void applyMatrix(PMatrix source) {
    if (recorder != null) recorder.applyMatrix(source);
    g.applyMatrix(source);
  }


  public void applyMatrix(PMatrix2D source) {
    if (recorder != null) recorder.applyMatrix(source);
    g.applyMatrix(source);
  }


  /**
   * @param n00 numbers which define the 4x4 matrix to be multiplied
   * @param n01 numbers which define the 4x4 matrix to be multiplied
   * @param n02 numbers which define the 4x4 matrix to be multiplied
   * @param n10 numbers which define the 4x4 matrix to be multiplied
   * @param n11 numbers which define the 4x4 matrix to be multiplied
   * @param n12 numbers which define the 4x4 matrix to be multiplied
   */
  public void applyMatrix(float n00, float n01, float n02,
                          float n10, float n11, float n12) {
    if (recorder != null) recorder.applyMatrix(n00, n01, n02, n10, n11, n12);
    g.applyMatrix(n00, n01, n02, n10, n11, n12);
  }


  public void applyMatrix(PMatrix3D source) {
    if (recorder != null) recorder.applyMatrix(source);
    g.applyMatrix(source);
  }


  /**
   * @param n03 numbers which define the 4x4 matrix to be multiplied
   * @param n13 numbers which define the 4x4 matrix to be multiplied
   * @param n20 numbers which define the 4x4 matrix to be multiplied
   * @param n21 numbers which define the 4x4 matrix to be multiplied
   * @param n22 numbers which define the 4x4 matrix to be multiplied
   * @param n23 numbers which define the 4x4 matrix to be multiplied
   * @param n30 numbers which define the 4x4 matrix to be multiplied
   * @param n31 numbers which define the 4x4 matrix to be multiplied
   * @param n32 numbers which define the 4x4 matrix to be multiplied
   * @param n33 numbers which define the 4x4 matrix to be multiplied
   */
  public void applyMatrix(float n00, float n01, float n02, float n03,
                          float n10, float n11, float n12, float n13,
                          float n20, float n21, float n22, float n23,
                          float n30, float n31, float n32, float n33) {
    if (recorder != null) recorder.applyMatrix(n00, n01, n02, n03, n10, n11, n12, n13, n20, n21, n22, n23, n30, n31, n32, n33);
    g.applyMatrix(n00, n01, n02, n03, n10, n11, n12, n13, n20, n21, n22, n23, n30, n31, n32, n33);
  }


  public PMatrix getMatrix() {
    return g.getMatrix();
  }


  /**
   * Copy the current transformation matrix into the specified target.
   * Pass in null to create a new matrix.
   */
  public PMatrix2D getMatrix(PMatrix2D target) {
    return g.getMatrix(target);
  }


  /**
   * Copy the current transformation matrix into the specified target.
   * Pass in null to create a new matrix.
   */
  public PMatrix3D getMatrix(PMatrix3D target) {
    return g.getMatrix(target);
  }


  /**
   * Set the current transformation matrix to the contents of another.
   */
  public void setMatrix(PMatrix source) {
    if (recorder != null) recorder.setMatrix(source);
    g.setMatrix(source);
  }


  /**
   * Set the current transformation to the contents of the specified source.
   */
  public void setMatrix(PMatrix2D source) {
    if (recorder != null) recorder.setMatrix(source);
    g.setMatrix(source);
  }


  /**
   * Set the current transformation to the contents of the specified source.
   */
  public void setMatrix(PMatrix3D source) {
    if (recorder != null) recorder.setMatrix(source);
    g.setMatrix(source);
  }


  /**
   * Prints the current matrix to the Console (the text window at the bottom
   * of Processing).
   *
   * @webref transform
   * @webBrief Prints the current matrix to the Console (the text window at the bottom
   * of Processing)
   * @see PGraphics#pushMatrix()
   * @see PGraphics#popMatrix()
   * @see PGraphics#resetMatrix()
   * @see PGraphics#applyMatrix(PMatrix)
   */
  public void printMatrix() {
    if (recorder != null) recorder.printMatrix();
    g.printMatrix();
  }


  /**
   * The <b>beginCamera()</b> and <b>endCamera()</b> functions enable
   * advanced customization of the camera space. The functions are useful if
   * you want to more control over camera movement, however for most users,
   * the <b>camera()</b> function will be sufficient.<br /><br />The camera
   * functions will replace any transformations (such as <b>rotate()</b> or
   * <b>translate()</b>) that occur before them in <b>draw()</b>, but they
   * will not automatically replace the camera transform itself. For this
   * reason, camera functions should be placed at the beginning of
   * <b>draw()</b> (so that transformations happen afterwards), and the
   * <b>camera()</b> function can be used after <b>beginCamera()</b> if you
   * want to reset the camera before applying transformations.<br /><br
   * />This function sets the matrix mode to the camera matrix so calls such
   * as <b>translate()</b>, <b>rotate()</b>, applyMatrix() and resetMatrix()
   * affect the camera. <b>beginCamera()</b> should always be used with a
   * following <b>endCamera()</b> and pairs of <b>beginCamera()</b> and
   * <b>endCamera()</b> cannot be nested.
   *
   * @webref lights_camera:camera
   * @webBrief The <b>beginCamera()</b> and <b>endCamera()</b> functions enable
   * advanced customization of the camera space
   * @see PGraphics#camera()
   * @see PGraphics#endCamera()
   * @see PGraphics#applyMatrix(PMatrix)
   * @see PGraphics#resetMatrix()
   * @see PGraphics#translate(float, float, float)
   * @see PGraphics#scale(float, float, float)
   */
  public void beginCamera() {
    if (recorder != null) recorder.beginCamera();
    g.beginCamera();
  }


  /**
   * The <b>beginCamera()</b> and <b>endCamera()</b> functions enable
   * advanced customization of the camera space. Please see the reference for
   * <b>beginCamera()</b> for a description of how the functions are used.
   *
   * @webref lights_camera:camera
   * @webBrief The <b>beginCamera()</b> and <b>endCamera()</b> functions enable
   * advanced customization of the camera space
   * @see PGraphics#beginCamera()
   * @see PGraphics#camera(float, float, float, float, float, float, float, float, float)
   */
  public void endCamera() {
    if (recorder != null) recorder.endCamera();
    g.endCamera();
  }


  /**
   * Sets the position of the camera through setting the eye position, the
   * center of the scene, and which axis is facing upward. Moving the eye
   * position and the direction it is pointing (the center of the scene)
   * allows the images to be seen from different angles. The version without
   * any parameters sets the camera to the default position, pointing to the
   * center of the display window with the Y axis as up. The default values
   * are <b>camera(width/2.0, height/2.0, (height/2.0) / tan(PI*30.0 /
   * 180.0), width/2.0, height/2.0, 0, 0, 1, 0)</b>. This function is similar
   * to <b>gluLookAt()</b> in OpenGL, but it first clears the current camera settings.
   *
   * @webref lights_camera:camera
   * @webBrief Sets the position of the camera
   * @see PGraphics#beginCamera()
   * @see PGraphics#endCamera()
   * @see PGraphics#frustum(float, float, float, float, float, float)
   */
  public void camera() {
    if (recorder != null) recorder.camera();
    g.camera();
  }


/**
 * @param eyeX x-coordinate for the eye
 * @param eyeY y-coordinate for the eye
 * @param eyeZ z-coordinate for the eye
 * @param centerX x-coordinate for the center of the scene
 * @param centerY y-coordinate for the center of the scene
 * @param centerZ z-coordinate for the center of the scene
 * @param upX usually 0.0, 1.0, or -1.0
 * @param upY usually 0.0, 1.0, or -1.0
 * @param upZ usually 0.0, 1.0, or -1.0
 */
  public void camera(float eyeX, float eyeY, float eyeZ,
                     float centerX, float centerY, float centerZ,
                     float upX, float upY, float upZ) {
    if (recorder != null) recorder.camera(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ);
    g.camera(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ);
  }


/**
   *
   * Prints the current camera matrix to the Console (the text window at the
   * bottom of Processing).
   *
 * @webref lights_camera:camera
 * @webBrief Prints the current camera matrix to the Console (the text window at the
 * bottom of Processing)
 * @see PGraphics#camera(float, float, float, float, float, float, float, float, float)
 */
  public void printCamera() {
    if (recorder != null) recorder.printCamera();
    g.printCamera();
  }


  /**
   *
   * Sets an orthographic projection and defines a parallel clipping volume. All
   * objects with the same dimension appear the same size, regardless of whether
   * they are near or far from the camera. The parameters to this function specify
   * the clipping volume where left and right are the minimum and maximum x
   * values, top and bottom are the minimum and maximum y values, and near and far
   * are the minimum and maximum z values. If no parameters are given, the default
   * is used: <b>ortho(-width/2, width/2, -height/2, height/2)</b>.
   *
   * @webref lights_camera:camera
   * @webBrief Sets an orthographic projection and defines a parallel clipping
   *           volume
   */
  public void ortho() {
    if (recorder != null) recorder.ortho();
    g.ortho();
  }


  /**
   * @param left left plane of the clipping volume
   * @param right right plane of the clipping volume
   * @param bottom bottom plane of the clipping volume
   * @param top top plane of the clipping volume
   */
  public void ortho(float left, float right,
                    float bottom, float top) {
    if (recorder != null) recorder.ortho(left, right, bottom, top);
    g.ortho(left, right, bottom, top);
  }


  /**
   * @param near maximum distance from the origin to the viewer
   * @param far maximum distance from the origin away from the viewer
   */
  public void ortho(float left, float right,
                    float bottom, float top,
                    float near, float far) {
    if (recorder != null) recorder.ortho(left, right, bottom, top, near, far);
    g.ortho(left, right, bottom, top, near, far);
  }


  /**
   *
   * Sets a perspective projection applying foreshortening, making distant
   * objects appear smaller than closer ones. The parameters define a viewing
   * volume with the shape of truncated pyramid. Objects near to the front of
   * the volume appear their actual size, while farther objects appear
   * smaller. This projection simulates the perspective of the world more
   * accurately than orthographic projection. The version of perspective
   * without parameters sets the default perspective and the version with
   * four parameters allows the programmer to set the area precisely. The
   * default values are: <b>perspective(PI/3.0, width/height, cameraZ/10.0,
   * cameraZ*10.0)</b> where cameraZ is <b>((height/2.0) / tan(PI*60.0/360.0))</b>
   *
   * @webref lights_camera:camera
   * @webBrief Sets a perspective projection applying foreshortening, making distant
   * objects appear smaller than closer ones
   */
  public void perspective() {
    if (recorder != null) recorder.perspective();
    g.perspective();
  }


  /**
   * @param fovy field-of-view angle (in radians) for vertical direction
   * @param aspect ratio of width to height
   * @param zNear z-position of nearest clipping plane
   * @param zFar z-position of the farthest clipping plane
   */
  public void perspective(float fovy, float aspect, float zNear, float zFar) {
    if (recorder != null) recorder.perspective(fovy, aspect, zNear, zFar);
    g.perspective(fovy, aspect, zNear, zFar);
  }


  /**
   *
   * Sets a perspective matrix as defined by the parameters.<br />
   * <br />
   * A frustum is a geometric form: a pyramid with its top cut off. With the
   * viewer's eye at the imaginary top of the pyramid, the six planes of the
   * frustum act as clipping planes when rendering a 3D view. Thus, any form
   * inside the clipping planes is rendered and visible; anything outside those
   * planes is not visible.<br />
   * <br />
   * Setting the frustum has the effect of changing the <em>perspective</em> with
   * which the scene is rendered. This can be achieved more simply in many cases
   * by using <strong>perspective()</strong>.<br />
   * <br />
   * Note that the near value must be greater than zero (as the point of the
   * frustum "pyramid" cannot converge "behind" the viewer). Similarly, the far
   * value must be greater than the near value (as the "far" plane of the frustum
   * must be "farther away" from the viewer than the near plane).<br />
   * <br />
   * Works like glFrustum, except it wipes out the current perspective matrix
   * rather than multiplying itself with it.
   *
   * @webref lights_camera:camera
   * @webBrief Sets a perspective matrix defined through the parameters
   * @param left   left coordinate of the clipping plane
   * @param right  right coordinate of the clipping plane
   * @param bottom bottom coordinate of the clipping plane
   * @param top    top coordinate of the clipping plane
   * @param near   near component of the clipping plane; must be greater than zero
   * @param far    far component of the clipping plane; must be greater than the
   *               near value
   * @see PGraphics#camera(float, float, float, float, float, float, float, float,
   *      float)
   * @see PGraphics#beginCamera()
   * @see PGraphics#endCamera()
   * @see PGraphics#perspective(float, float, float, float)
   */
  public void frustum(float left, float right,
                      float bottom, float top,
                      float near, float far) {
    if (recorder != null) recorder.frustum(left, right, bottom, top, near, far);
    g.frustum(left, right, bottom, top, near, far);
  }


  /**
   * Prints the current projection matrix to the Console (the text window at
   * the bottom of Processing).
   *
   * @webref lights_camera:camera
   * @webBrief Prints the current projection matrix to the Console
   * @see PGraphics#camera(float, float, float, float, float, float, float, float, float)
   */
  public void printProjection() {
    if (recorder != null) recorder.printProjection();
    g.printProjection();
  }


  /**
   * Takes a three-dimensional X, Y, Z position and returns the X value for
   * where it will appear on a (two-dimensional) screen.
   *
   * @webref lights_camera:coordinates
   * @webBrief Takes a three-dimensional X, Y, Z position and returns the X value for
   * where it will appear on a (two-dimensional) screen
   * @param x 3D x-coordinate to be mapped
   * @param y 3D y-coordinate to be mapped
   * @see PGraphics#screenY(float, float, float)
   * @see PGraphics#screenZ(float, float, float)
   */
  public float screenX(float x, float y) {
    return g.screenX(x, y);
  }


  /**
   * Takes a three-dimensional X, Y, Z position and returns the Y value for
   * where it will appear on a (two-dimensional) screen.
   *
   * @webref lights_camera:coordinates
   * @webBrief Takes a three-dimensional X, Y, Z position and returns the Y value for
   * where it will appear on a (two-dimensional) screen
   * @param x 3D x-coordinate to be mapped
   * @param y 3D y-coordinate to be mapped
   * @see PGraphics#screenX(float, float, float)
   * @see PGraphics#screenZ(float, float, float)
   */
  public float screenY(float x, float y) {
    return g.screenY(x, y);
  }


  /**
   * @param z 3D z-coordinate to be mapped
   */
  public float screenX(float x, float y, float z) {
    return g.screenX(x, y, z);
  }


  /**
   * @param z 3D z-coordinate to be mapped
   */
  public float screenY(float x, float y, float z) {
    return g.screenY(x, y, z);
  }


  /**
   * Takes a three-dimensional X, Y, Z position and returns the Z value for
   * where it will appear on a (two-dimensional) screen.
   *
   * @webref lights_camera:coordinates
   * @webBrief Takes a three-dimensional X, Y, Z position and returns the Z value for
   * where it will appear on a (two-dimensional) screen
   * @param x 3D x-coordinate to be mapped
   * @param y 3D y-coordinate to be mapped
   * @param z 3D z-coordinate to be mapped
   * @see PGraphics#screenX(float, float, float)
   * @see PGraphics#screenY(float, float, float)
   */
  public float screenZ(float x, float y, float z) {
    return g.screenZ(x, y, z);
  }


  /**
   *
   * Returns the three-dimensional X, Y, Z position in model space. This
   * returns the X value for a given coordinate based on the current set of
   * transformations (scale, rotate, translate, etc.) The X value can be used
   * to place an object in space relative to the location of the original
   * point once the transformations are no longer in use.
   * <br/> <br/>
   * In the example, the <b>modelX()</b>, <b>modelY()</b>, and
   * <b>modelZ()</b> functions record the location of a box in space after
   * being placed using a series of translate and rotate commands. After
   * <b>popMatrix()</b> is called, those transformations no longer apply, but the
   * <b>(x, y, z)</b> coordinate returned by the model functions is used to place
   * another box in the same location.
   *
   * @webref lights_camera:coordinates
   * @webBrief Returns the three-dimensional X, Y, Z position in model space
   * @param x 3D x-coordinate to be mapped
   * @param y 3D y-coordinate to be mapped
   * @param z 3D z-coordinate to be mapped
   * @see PGraphics#modelY(float, float, float)
   * @see PGraphics#modelZ(float, float, float)
   */
  public float modelX(float x, float y, float z) {
    return g.modelX(x, y, z);
  }


  /**
   * Returns the three-dimensional X, Y, Z position in model space. This
   * returns the Y value for a given coordinate based on the current set of
   * transformations (scale, rotate, translate, etc.) The Y value can be used
   * to place an object in space relative to the location of the original
   * point once the transformations are no longer in use.<br />
   * <br />
   * In the example, the <b>modelX()</b>, <b>modelY()</b>, and
   * <b>modelZ()</b> functions record the location of a box in space after
   * being placed using a series of translate and rotate commands. After
   * <b>popMatrix()</b> is called, those transformations no longer apply, but the
   * <b>(x, y, z)</b> coordinate returned by the model functions is used to place
   * another box in the same location.
   *
   * @webref lights_camera:coordinates
   * @webBrief Returns the three-dimensional X, Y, Z position in model space
   * @param x 3D x-coordinate to be mapped
   * @param y 3D y-coordinate to be mapped
   * @param z 3D z-coordinate to be mapped
   * @see PGraphics#modelX(float, float, float)
   * @see PGraphics#modelZ(float, float, float)
   */
  public float modelY(float x, float y, float z) {
    return g.modelY(x, y, z);
  }


  /**
   *
   * Returns the three-dimensional X, Y, Z position in model space. This
   * returns the Z value for a given coordinate based on the current set of
   * transformations (scale, rotate, translate, etc.) The Z value can be used
   * to place an object in space relative to the location of the original
   * point once the transformations are no longer in use.<br />
   * <br />
   * In the example, the <b>modelX()</b>, <b>modelY()</b>, and
   * <b>modelZ()</b> functions record the location of a box in space after
   * being placed using a series of translate and rotate commands. After
   * <b>popMatrix()</b> is called, those transformations no longer apply, but the
   * <b>(x, y, z)</b> coordinate returned by the model functions is used to place
   * another box in the same location.
   *
   * @webref lights_camera:coordinates
   * @webBrief Returns the three-dimensional X, Y, Z position in model space
   * @param x 3D x-coordinate to be mapped
   * @param y 3D y-coordinate to be mapped
   * @param z 3D z-coordinate to be mapped
   * @see PGraphics#modelX(float, float, float)
   * @see PGraphics#modelY(float, float, float)
   */
  public float modelZ(float x, float y, float z) {
    return g.modelZ(x, y, z);
  }


  /**
   *
   * The <b>pushStyle()</b> function saves the current style settings and
   * <b>popStyle()</b> restores the prior settings. Note that these functions
   * are always used together. They allow you to change the style settings
   * and later return to what you had. When a new style is started with
   * <b>pushStyle()</b>, it builds on the current style information. The
   * <b>pushStyle()</b> and <b>popStyle()</b> functions can be embedded to
   * provide more control (see the second example above for a demonstration.)
   * <br /><br />
   * The style information controlled by the following functions are included
   * in the style:
   * <b>fill()<b>, <b>stroke()</b>, <b>tint()</b>, <b>strokeWeight()</b>, <b>strokeCap()</b>,<b>strokeJoin()</b>,
   * <b>imageMode()</b>, <b>rectMode()</b>, <b>ellipseMode()</b>, <b>shapeMode()</b>, <b>colorMode()</b>,
   * <b>textAlign()</b>, <b>textFont()</b>, <b>textMode()</b>, <b>textSize()</b>, <b>textLeading()</b>,
   * <b>emissive()</b>, <b>specular()</b>, <b>shininess()</b>, <b>ambient()</b>
   *
   * @webref structure
   * @webBrief Saves the current style settings and <b>popStyle()</b> restores the prior settings
   * @see PGraphics#popStyle()
   */
  public void pushStyle() {
    if (recorder != null) recorder.pushStyle();
    g.pushStyle();
  }


  /**
   * The <b>pushStyle()</b> function saves the current style settings and
   * <b>popStyle()</b> restores the prior settings; these functions are
   * always used together. They allow you to change the style settings and
   * later return to what you had. When a new style is started with
   * <b>pushStyle()</b>, it builds on the current style information. The
   * <b>pushStyle()</b> and <b>popStyle()</b> functions can be embedded to
   * provide more control (see the second example above for a demonstration.)
   *
   * @webref structure
   * @webBrief Saves the current style settings and <b>popStyle()</b> restores the prior settings
   * @see PGraphics#pushStyle()
   */
  public void popStyle() {
    if (recorder != null) recorder.popStyle();
    g.popStyle();
  }


  public void style(PStyle s) {
    if (recorder != null) recorder.style(s);
    g.style(s);
  }


  /**
   * Sets the width of the stroke used for lines, points, and the border around
   * shapes. All widths are set in units of pixels. <br />
   * <br />
   * Using point() with strokeWeight(1) or smaller may draw nothing to the screen,
   * depending on the graphics settings of the computer. Workarounds include
   * setting the pixel using <b>set()</s> or drawing the point using either
   * <b>circle()</b> or <b>square()</b>.
   *
   * @webref shape:attributes
   * @webBrief Sets the width of the stroke used for lines, points, and the border
   *           around shapes
   * @param weight the weight (in pixels) of the stroke
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#strokeJoin(int)
   * @see PGraphics#strokeCap(int)
   */
  public void strokeWeight(float weight) {
    if (recorder != null) recorder.strokeWeight(weight);
    g.strokeWeight(weight);
  }


  /**
   * Sets the style of the joints which connect line segments. These joints are
   * either mitered, beveled, or rounded and specified with the corresponding
   * parameters MITER, BEVEL, and ROUND. The default joint is MITER.
   *
   * @webref shape:attributes
   * @webBrief Sets the style of the joints which connect line segments
   * @param join either MITER, BEVEL, ROUND
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#strokeWeight(float)
   * @see PGraphics#strokeCap(int)
   */
  public void strokeJoin(int join) {
    if (recorder != null) recorder.strokeJoin(join);
    g.strokeJoin(join);
  }


  /**
   * Sets the style for rendering line endings. These ends are either squared,
   * extended, or rounded, each of which specified with the corresponding
   * parameters: SQUARE, PROJECT, and ROUND. The default cap is ROUND. <br />
   * <br />
   * To make <b>point()</b> appear square, use <b>strokeCap(PROJECT)</b>. Using
   * <b>strokeCap(SQUARE)</b> (no cap) causes points to become invisible.
   *
   * @webref shape:attributes
   * @webBrief Sets the style for rendering line endings
   * @param cap either SQUARE, PROJECT, or ROUND
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#strokeWeight(float)
   * @see PGraphics#strokeJoin(int)
   * @see PApplet#size(int, int, String, String)
   */
  public void strokeCap(int cap) {
    if (recorder != null) recorder.strokeCap(cap);
    g.strokeCap(cap);
  }


  /**
   * Disables drawing the stroke (outline). If both <b>noStroke()</b> and
   * <b>noFill()</b> are called, nothing will be drawn to the screen.
   *
   * @webref color:setting
   * @webBrief Disables drawing the stroke (outline)
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#fill(float, float, float, float)
   * @see PGraphics#noFill()
   */
  public void noStroke() {
    if (recorder != null) recorder.noStroke();
    g.noStroke();
  }


  /**
   *
   * Sets the color used to draw lines and borders around shapes. This color is
   * either specified in terms of the RGB or HSB color depending on the current
   * <b>colorMode().</b> The default color space is RGB, with each value in the
   * range from 0 to 255. <br />
   * <br />
   * When using hexadecimal notation to specify a color, use "<b>#</b>" or
   * "<b>0x</b>" before the values (e.g., <b>#CCFFAA</b> or <b>0xFFCCFFAA</b>).
   * The <b>#</b> syntax uses six digits to specify a color (just as colors are
   * typically specified in HTML and CSS). When using the hexadecimal notation
   * starting with "<b>0x</b>", the hexadecimal value must be specified with eight
   * characters; the first two characters define the alpha component, and the
   * remainder define the red, green, and blue components. <br />
   * <br />
   * The value for the gray parameter must be less than or equal to the current
   * maximum value as specified by <b>colorMode()</b>. The default maximum value
   * is 255. <br />
   * <br />
   * When drawing in 2D with the default renderer, you may need
   * <b>hint(ENABLE_STROKE_PURE)</b> to improve drawing quality (at the expense of
   * performance). See the hint() documentation for more details.
   *
   * @webref color:setting
   * @webBrief Sets the color used to draw lines and borders around shapes
   * @param rgb color value in hexadecimal notation
   * @see PGraphics#noStroke()
   * @see PGraphics#strokeWeight(float)
   * @see PGraphics#strokeJoin(int)
   * @see PGraphics#strokeCap(int)
   * @see PGraphics#fill(int, float)
   * @see PGraphics#noFill()
   * @see PGraphics#tint(int, float)
   * @see PGraphics#background(float, float, float, float)
   * @see PGraphics#colorMode(int, float, float, float, float)
   */
  public void stroke(int rgb) {
    if (recorder != null) recorder.stroke(rgb);
    g.stroke(rgb);
  }


  /**
   * @param alpha opacity of the stroke
   */
  public void stroke(int rgb, float alpha) {
    if (recorder != null) recorder.stroke(rgb, alpha);
    g.stroke(rgb, alpha);
  }


  /**
   * @param gray specifies a value between white and black
   */
  public void stroke(float gray) {
    if (recorder != null) recorder.stroke(gray);
    g.stroke(gray);
  }


  public void stroke(float gray, float alpha) {
    if (recorder != null) recorder.stroke(gray, alpha);
    g.stroke(gray, alpha);
  }


  /**
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   */
  public void stroke(float v1, float v2, float v3) {
    if (recorder != null) recorder.stroke(v1, v2, v3);
    g.stroke(v1, v2, v3);
  }


  public void stroke(float v1, float v2, float v3, float alpha) {
    if (recorder != null) recorder.stroke(v1, v2, v3, alpha);
    g.stroke(v1, v2, v3, alpha);
  }


  /**
   *
   * Removes the current fill value for displaying images and reverts to
   * displaying images with their original hues.
   *
   * @webref image:loading & displaying
   * @webBrief Removes the current fill value for displaying images and reverts to
   * displaying images with their original hues
   * @usage web_application
   * @see PGraphics#tint(float, float, float, float)
   * @see PGraphics#image(PImage, float, float, float, float)
   */
  public void noTint() {
    if (recorder != null) recorder.noTint();
    g.noTint();
  }


  /**
   *
   * Sets the fill value for displaying images. Images can be tinted to specified
   * colors or made transparent by including an alpha value.<br />
   * <br />
   * To apply transparency to an image without affecting its color, use white as
   * the tint color and specify an alpha value. For instance, <b>tint(255,
   * 128)</b> will make an image 50% transparent (assuming the default alpha range
   * of 0-255, which can be changed with <b>colorMode()</b>). <br />
   * <br />
   * When using hexadecimal notation to specify a color, use "<b>#</b>" or
   * "<b>0x</b>" before the values (e.g., <b>#CCFFAA</b> or <b>0xFFCCFFAA</b>).
   * The <b>#</b> syntax uses six digits to specify a color (just as colors are
   * typically specified in HTML and CSS). When using the hexadecimal notation
   * starting with "<b>0x</b>", the hexadecimal value must be specified with eight
   * characters; the first two characters define the alpha component, and the
   * remainder define the red, green, and blue components. <br />
   * <br />
   * The value for the gray parameter must be less than or equal to the current
   * maximum value as specified by <b>colorMode()</b>. The default maximum value
   * is 255. <br />
   * <br />
   * The <b>tint()</b> function is also used to control the coloring of textures
   * in 3D.
   *
   * @webref image:loading & displaying
   * @webBrief Sets the fill value for displaying images
   * @usage web_application
   * @param rgb color value in hexadecimal notation
   * @see PGraphics#noTint()
   * @see PGraphics#image(PImage, float, float, float, float)
   */
  public void tint(int rgb) {
    if (recorder != null) recorder.tint(rgb);
    g.tint(rgb);
  }


  /**
   * @param alpha opacity of the image
   */
  public void tint(int rgb, float alpha) {
    if (recorder != null) recorder.tint(rgb, alpha);
    g.tint(rgb, alpha);
  }


  /**
   * @param gray specifies a value between white and black
   */
  public void tint(float gray) {
    if (recorder != null) recorder.tint(gray);
    g.tint(gray);
  }


  public void tint(float gray, float alpha) {
    if (recorder != null) recorder.tint(gray, alpha);
    g.tint(gray, alpha);
  }


/**
 * @param v1 red or hue value (depending on current color mode)
 * @param v2 green or saturation value (depending on current color mode)
 * @param v3 blue or brightness value (depending on current color mode)
 */
  public void tint(float v1, float v2, float v3) {
    if (recorder != null) recorder.tint(v1, v2, v3);
    g.tint(v1, v2, v3);
  }


  public void tint(float v1, float v2, float v3, float alpha) {
    if (recorder != null) recorder.tint(v1, v2, v3, alpha);
    g.tint(v1, v2, v3, alpha);
  }


  /**
   *
   * Disables filling geometry. If both <b>noStroke()</b> and <b>noFill()</b>
   * are called, nothing will be drawn to the screen.
   *
   * @webref color:setting
   * @webBrief Disables filling geometry
   * @usage web_application
   * @see PGraphics#fill(float, float, float, float)
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#noStroke()
   */
  public void noFill() {
    if (recorder != null) recorder.noFill();
    g.noFill();
  }


  /**
   *
   * Sets the color used to fill shapes. For example, if you run <b>fill(204,
   * 102, 0)</b>, all subsequent shapes will be filled with orange. This
   * color is either specified in terms of the RGB or HSB color depending on
   * the current <b>colorMode()</b> (the default color space is RGB, with
   * each value in the range from 0 to 255).
   * <br/> <br/>
   * When using hexadecimal notation to specify a color, use "#" or "0x"
   * before the values (e.g. #CCFFAA, 0xFFCCFFAA). The # syntax uses six
   * digits to specify a color (the way colors are specified in HTML and
   * CSS). When using the hexadecimal notation starting with "0x", the
   * hexadecimal value must be specified with eight characters; the first two
   * characters define the alpha component and the remainder the red, green,
   * and blue components.
   * <br/> <br/>
   * The value for the parameter "gray" must be less than or equal to the
   * current maximum value as specified by <b>colorMode()</b>. The default
   * maximum value is 255.
   * <br/> <br/>
   * To change the color of an image (or a texture), use tint().
   *
   * @webref color:setting
   * @webBrief Sets the color used to fill shapes
   * @usage web_application
   * @param rgb color variable or hex value
   * @see PGraphics#noFill()
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#noStroke()
   * @see PGraphics#tint(int, float)
   * @see PGraphics#background(float, float, float, float)
   * @see PGraphics#colorMode(int, float, float, float, float)
   */
  public void fill(int rgb) {
    if (recorder != null) recorder.fill(rgb);
    g.fill(rgb);
  }


  /**
   * @param alpha opacity of the fill
   */
  public void fill(int rgb, float alpha) {
    if (recorder != null) recorder.fill(rgb, alpha);
    g.fill(rgb, alpha);
  }


  /**
   * @param gray number specifying value between white and black
   */
  public void fill(float gray) {
    if (recorder != null) recorder.fill(gray);
    g.fill(gray);
  }


  public void fill(float gray, float alpha) {
    if (recorder != null) recorder.fill(gray, alpha);
    g.fill(gray, alpha);
  }


  /**
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   */
  public void fill(float v1, float v2, float v3) {
    if (recorder != null) recorder.fill(v1, v2, v3);
    g.fill(v1, v2, v3);
  }


  public void fill(float v1, float v2, float v3, float alpha) {
    if (recorder != null) recorder.fill(v1, v2, v3, alpha);
    g.fill(v1, v2, v3, alpha);
  }


  /**
   *
   * Sets the ambient reflectance for shapes drawn to the screen. This is
   * combined with the ambient light component of environment. The color
   * components set through the parameters define the reflectance. For
   * example in the default color mode, setting v1=255, v2=126, v3=0, would
   * cause all the red light to reflect and half of the green light to
   * reflect. Used in combination with <b>emissive()</b>, <b>specular()</b>,
   * and <b>shininess()</b> in setting the material properties of shapes.
   *
   * @webref lights_camera:material properties
   * @webBrief Sets the ambient reflectance for shapes drawn to the screen
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#emissive(float, float, float)
   * @see PGraphics#specular(float, float, float)
   * @see PGraphics#shininess(float)
   */
  public void ambient(int rgb) {
    if (recorder != null) recorder.ambient(rgb);
    g.ambient(rgb);
  }


/**
 * @param gray number specifying value between white and black
 */
  public void ambient(float gray) {
    if (recorder != null) recorder.ambient(gray);
    g.ambient(gray);
  }


/**
 * @param v1 red or hue value (depending on current color mode)
 * @param v2 green or saturation value (depending on current color mode)
 * @param v3 blue or brightness value (depending on current color mode)
 */
  public void ambient(float v1, float v2, float v3) {
    if (recorder != null) recorder.ambient(v1, v2, v3);
    g.ambient(v1, v2, v3);
  }


  /**
   * Sets the specular color of the materials used for shapes drawn to the
   * screen, which sets the color of highlights. Specular refers to light
   * which bounces off a surface in a preferred direction (rather than
   * bouncing in all directions like a diffuse light). Used in combination
   * with <b>emissive()</b>, <b>ambient()</b>, and <b>shininess()</b> in
   * setting the material properties of shapes.
   *
   * @webref lights_camera:material properties
   * @webBrief Sets the specular color of the materials used for shapes drawn to the
   * screen, which sets the color of highlights
   * @usage web_application
   * @param rgb color to set
   * @see PGraphics#lightSpecular(float, float, float)
   * @see PGraphics#ambient(float, float, float)
   * @see PGraphics#emissive(float, float, float)
   * @see PGraphics#shininess(float)
   */
  public void specular(int rgb) {
    if (recorder != null) recorder.specular(rgb);
    g.specular(rgb);
  }


/**
 * gray number specifying value between white and black
 *
 * @param gray value between black and white, by default 0 to 255
 */
  public void specular(float gray) {
    if (recorder != null) recorder.specular(gray);
    g.specular(gray);
  }


/**
 * @param v1 red or hue value (depending on current color mode)
 * @param v2 green or saturation value (depending on current color mode)
 * @param v3 blue or brightness value (depending on current color mode)
 */
  public void specular(float v1, float v2, float v3) {
    if (recorder != null) recorder.specular(v1, v2, v3);
    g.specular(v1, v2, v3);
  }


  /**
   *
   * Sets the amount of gloss in the surface of shapes. Used in combination
   * with <b>ambient()</b>, <b>specular()</b>, and <b>emissive()</b> in
   * setting the material properties of shapes.
   *
   * @webref lights_camera:material properties
   * @webBrief Sets the amount of gloss in the surface of shapes
   * @usage web_application
   * @param shine degree of shininess
   * @see PGraphics#emissive(float, float, float)
   * @see PGraphics#ambient(float, float, float)
   * @see PGraphics#specular(float, float, float)
   */
  public void shininess(float shine) {
    if (recorder != null) recorder.shininess(shine);
    g.shininess(shine);
  }


  /**
   *
   * Sets the emissive color of the material used for drawing shapes drawn to
   * the screen. Used in combination with <b>ambient()</b>,
   * <b>specular()</b>, and <b>shininess()</b> in setting the material
   * properties of shapes.
   *
   * @webref lights_camera:material properties
   * @webBrief Sets the emissive color of the material used for drawing shapes drawn to
   * the screen
   * @usage web_application
   * @param rgb color to set
   * @see PGraphics#ambient(float, float, float)
   * @see PGraphics#specular(float, float, float)
   * @see PGraphics#shininess(float)
   */
  public void emissive(int rgb) {
    if (recorder != null) recorder.emissive(rgb);
    g.emissive(rgb);
  }


  /**
   * gray number specifying value between white and black
   *
   * @param gray value between black and white, by default 0 to 255
   */
  public void emissive(float gray) {
    if (recorder != null) recorder.emissive(gray);
    g.emissive(gray);
  }


  /**
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   */
  public void emissive(float v1, float v2, float v3) {
    if (recorder != null) recorder.emissive(v1, v2, v3);
    g.emissive(v1, v2, v3);
  }


  /**
   *
   * Sets the default ambient light, directional light, falloff, and specular
   * values. The defaults are <b>ambientLight(128, 128, 128)</b> and
   * <b>directionalLight(128, 128, 128, 0, 0, -1)</b>, <b>lightFalloff(1, 0, 0)</b>, and
   * <b>lightSpecular(0, 0, 0)</b>. Lights need to be included in the <b>draw()</b> to
   * remain persistent in a looping program. Placing them in the <b>setup()</b> of a
   * looping program will cause them to only have an effect the first time
   * through the loop.
   *
   * @webref lights_camera:lights
   * @webBrief Sets the default ambient light, directional light, falloff, and specular
   * values
   * @usage web_application
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   * @see PGraphics#directionalLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#noLights()
   */
  public void lights() {
    if (recorder != null) recorder.lights();
    g.lights();
  }


  /**
   *
   * Disable all lighting. Lighting is turned off by default and enabled with
   * the <b>lights()</b> function. This function can be used to disable
   * lighting so that 2D geometry (which does not require lighting) can be
   * drawn after a set of lighted 3D geometry.
   *
   * @webref lights_camera:lights
   * @webBrief Disable all lighting
   * @usage web_application
   * @see PGraphics#lights()
   */
  public void noLights() {
    if (recorder != null) recorder.noLights();
    g.noLights();
  }


  /**
   *
   * Adds an ambient light. Ambient light doesn't come from a specific direction,
   * the rays of light have bounced around so much that objects are evenly lit
   * from all sides. Ambient lights are almost always used in combination with
   * other types of lights. Lights need to be included in the <b>draw()</b> to
   * remain persistent in a looping program. Placing them in the <b>setup()</b> of
   * a looping program will cause them to only have an effect the first time
   * through the loop. The <b>v1</b>, <b>v2</b>, and <b>v3</b> parameters are
   * interpreted as either RGB or HSB values, depending on the current color mode.
   *
   * @webref lights_camera:lights
   * @webBrief Adds an ambient light
   * @usage web_application
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   * @see PGraphics#lights()
   * @see PGraphics#directionalLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float,
   *      float, float, float, float)
   */
  public void ambientLight(float v1, float v2, float v3) {
    if (recorder != null) recorder.ambientLight(v1, v2, v3);
    g.ambientLight(v1, v2, v3);
  }


  /**
   * @param x x-coordinate of the light
   * @param y y-coordinate of the light
   * @param z z-coordinate of the light
   */
  public void ambientLight(float v1, float v2, float v3,
                           float x, float y, float z) {
    if (recorder != null) recorder.ambientLight(v1, v2, v3, x, y, z);
    g.ambientLight(v1, v2, v3, x, y, z);
  }


  /**
   *
   * Adds a directional light. Directional light comes from one direction and
   * is stronger when hitting a surface squarely and weaker if it hits at a
   * gentle angle. After hitting a surface, a directional lights scatters in
   * all directions. Lights need to be included in the <b>draw()</b> to
   * remain persistent in a looping program. Placing them in the
   * <b>setup()</b> of a looping program will cause them to only have an
   * effect the first time through the loop. The affect of the <b>v1</b>,
   * <b>v2</b>, and <b>v3</b> parameters is determined by the current color
   * mode. The <b>nx</b>, <b>ny</b>, and <b>nz</b> parameters specify the
   * direction the light is facing. For example, setting <b>ny</b> to -1 will
   * cause the geometry to be lit from below (the light is facing directly upward).
   *
   * @webref lights_camera:lights
   * @webBrief Adds a directional light
   * @usage web_application
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   * @param nx direction along the x-axis
   * @param ny direction along the y-axis
   * @param nz direction along the z-axis
   * @see PGraphics#lights()
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float, float, float, float, float)
   */
  public void directionalLight(float v1, float v2, float v3,
                               float nx, float ny, float nz) {
    if (recorder != null) recorder.directionalLight(v1, v2, v3, nx, ny, nz);
    g.directionalLight(v1, v2, v3, nx, ny, nz);
  }


  /**
   *
   * Adds a point light. Lights need to be included in the <b>draw()</b> to remain
   * persistent in a looping program. Placing them in the <b>setup()</b> of a
   * looping program will cause them to only have an effect the first time through
   * the loop. The <b>v1</b>, <b>v2</b>, and <b>v3</b> parameters are interpreted
   * as either RGB or HSB values, depending on the current color mode. The
   * <b>x</b>, <b>y</b>, and <b>z</b> parameters set the position of the light.
   *
   * @webref lights_camera:lights
   * @webBrief Adds a point light
   * @usage web_application
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   * @param x  x-coordinate of the light
   * @param y  y-coordinate of the light
   * @param z  z-coordinate of the light
   * @see PGraphics#lights()
   * @see PGraphics#directionalLight(float, float, float, float, float, float)
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float,
   *      float, float, float, float)
   */
  public void pointLight(float v1, float v2, float v3,
                         float x, float y, float z) {
    if (recorder != null) recorder.pointLight(v1, v2, v3, x, y, z);
    g.pointLight(v1, v2, v3, x, y, z);
  }


  /**
   *
   * Adds a spotlight. Lights need to be included in the <b>draw()</b> to remain
   * persistent in a looping program. Placing them in the <b>setup()</b> of a
   * looping program will cause them to only have an effect the first time through
   * the loop. The <b>v1</b>, <b>v2</b>, and <b>v3</b> parameters are interpreted
   * as either RGB or HSB values, depending on the current color mode. The
   * <b>x</b>, <b>y</b>, and <b>z</b> parameters specify the position of the light
   * and <b>nx</b>, <b>ny</b>, <b>nz</b> specify the direction of light. The
   * <b>angle</b> parameter affects angle of the spotlight cone, while
   * <b>concentration</b> sets the bias of light focusing toward the center of
   * that cone.
   *
   * @webref lights_camera:lights
   * @webBrief Adds a spotlight
   * @usage web_application
   * @param v1            red or hue value (depending on current color mode)
   * @param v2            green or saturation value (depending on current color
   *                      mode)
   * @param v3            blue or brightness value (depending on current color
   *                      mode)
   * @param x             x-coordinate of the light
   * @param y             y-coordinate of the light
   * @param z             z-coordinate of the light
   * @param nx            direction along the x-axis
   * @param ny            direction along the y-axis
   * @param nz            direction along the z-axis
   * @param angle         angle of the spotlight cone
   * @param concentration exponent determining the center bias of the cone
   * @see PGraphics#lights()
   * @see PGraphics#directionalLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   */
  public void spotLight(float v1, float v2, float v3,
                        float x, float y, float z,
                        float nx, float ny, float nz,
                        float angle, float concentration) {
    if (recorder != null) recorder.spotLight(v1, v2, v3, x, y, z, nx, ny, nz, angle, concentration);
    g.spotLight(v1, v2, v3, x, y, z, nx, ny, nz, angle, concentration);
  }


  /**
   *
   * Sets the falloff rates for point lights, spotlights, and ambient lights.
   * Like <b>fill()</b>, it affects only the elements which are created after it
   * in the code. The default value is <b>lightFalloff(1.0, 0.0, 0.0)</b>, and the
   * parameters are used to calculate the falloff with the following
   * equation:<br />
   * <br />
   * d = distance from light position to vertex position<br />
   * falloff = 1 / (CONSTANT + d * LINEAR + (d*d) * QUADRATIC)<br />
   * <br />
   * Thinking about an ambient light with a falloff can be tricky. If you want a
   * region of your scene to be ambient lit with one color and another region to
   * be ambient lit with another color, you could use an ambient light with
   * location and falloff. You can think of it as a point light that doesn't care
   * which direction a surface is facing.
   *
   * @webref lights_camera:lights
   * @webBrief Sets the falloff rates for point lights, spotlights, and ambient
   *           lights
   * @usage web_application
   * @param constant  constant value or determining falloff
   * @param linear    linear value for determining falloff
   * @param quadratic quadratic value for determining falloff
   * @see PGraphics#lights()
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float,
   *      float, float, float, float)
   * @see PGraphics#lightSpecular(float, float, float)
   */
  public void lightFalloff(float constant, float linear, float quadratic) {
    if (recorder != null) recorder.lightFalloff(constant, linear, quadratic);
    g.lightFalloff(constant, linear, quadratic);
  }


  /**
   *
   * Sets the specular color for lights. Like <b>fill()</b>, it affects only
   * the elements which are created after it in the code. Specular refers to
   * light which bounces off a surface in a preferred direction (rather than
   * bouncing in all directions like a diffuse light) and is used for
   * creating highlights. The specular quality of a light interacts with the
   * specular material qualities set through the <b>specular()</b> and
   * <b>shininess()</b> functions.
   *
   * @webref lights_camera:lights
   * @webBrief Sets the specular color for lights
   * @usage web_application
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   * @see PGraphics#specular(float, float, float)
   * @see PGraphics#lights()
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float, float, float, float, float)
   */
  public void lightSpecular(float v1, float v2, float v3) {
    if (recorder != null) recorder.lightSpecular(v1, v2, v3);
    g.lightSpecular(v1, v2, v3);
  }


  /**
   *
   * The <b>background()</b> function sets the color used for the background of
   * the Processing window. The default background is light gray. This function is
   * typically used within <b>draw()</b> to clear the display window at the
   * beginning of each frame, but it can be used inside <b>setup()</b> to set the
   * background on the first frame of animation or if the background need only be
   * set once. <br />
   * <br />
   * An image can also be used as the background for a sketch, although the
   * image's width and height must match that of the sketch window. Images used
   * with <b>background()</b> will ignore the current <b>tint()</b> setting. To
   * resize an image to the size of the sketch window, use image.resize(width,
   * height). <br />
   * <br />
   * It is not possible to use the transparency <b>alpha</b> parameter with
   * background colors on the main drawing surface. It can only be used along with
   * a <b>PGraphics</b> object and <b>createGraphics()</b>.
   *
   * <h3>Advanced</h3>
   * <p>
   * Clear the background with a color that includes an alpha value. This can only
   * be used with objects created by createGraphics(), because the main drawing
   * surface cannot be set transparent.
   * </p>
   * <p>
   * It might be tempting to use this function to partially clear the screen on
   * each frame, however that's not how this function works. When calling
   * background(), the pixels will be replaced with pixels that have that level of
   * transparency. To do a semi-transparent overlay, use fill() with alpha and
   * draw a rectangle.
   * </p>
   *
   * @webref color:setting
   * @webBrief Sets the color used for the background of the Processing window
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#stroke(float)
   * @see PGraphics#fill(float)
   * @see PGraphics#tint(float)
   * @see PGraphics#colorMode(int)
   */
  public void background(int rgb) {
    if (recorder != null) recorder.background(rgb);
    g.background(rgb);
  }


  /**
   * @param alpha opacity of the background
   */
  public void background(int rgb, float alpha) {
    if (recorder != null) recorder.background(rgb, alpha);
    g.background(rgb, alpha);
  }


  /**
   * @param gray specifies a value between white and black
   */
  public void background(float gray) {
    if (recorder != null) recorder.background(gray);
    g.background(gray);
  }


  public void background(float gray, float alpha) {
    if (recorder != null) recorder.background(gray, alpha);
    g.background(gray, alpha);
  }


  /**
   * @param v1 red or hue value (depending on the current color mode)
   * @param v2 green or saturation value (depending on the current color mode)
   * @param v3 blue or brightness value (depending on the current color mode)
   */
  public void background(float v1, float v2, float v3) {
    if (recorder != null) recorder.background(v1, v2, v3);
    g.background(v1, v2, v3);
  }


  public void background(float v1, float v2, float v3, float alpha) {
    if (recorder != null) recorder.background(v1, v2, v3, alpha);
    g.background(v1, v2, v3, alpha);
  }


  /**
   * Clears the pixels within a buffer. This function only works on
   * <b>PGraphics</b> objects created with the <b>createGraphics()</b>
   * function. Unlike the main graphics context (the display window),
   * pixels in additional graphics areas created with <b>createGraphics()</b>
   * can be entirely or partially transparent. This function clears
   * everything in a <b>PGraphics</b> object to make all the pixels
   * 100% transparent.
   *
   * @webref color:setting
   * @webBrief Clears the pixels within a buffer
   */
  public void clear() {
    if (recorder != null) recorder.clear();
    g.clear();
  }


  /**
   * Takes an RGB or ARGB image and sets it as the background.
   * The width and height of the image must be the same size as the sketch.
   * Use image.resize(width, height) to make short work of such a task.<br/>
   * <br/>
   * Note that even if the image is set as RGB, the high 8 bits of each pixel
   * should be set opaque (0xFF000000) because the image data will be copied
   * directly to the screen, and non-opaque background images may have strange
   * behavior. Use image.filter(OPAQUE) to handle this easily.<br/>
   * <br/>
   * When using 3D, this will also clear the zbuffer (if it exists).
   *
   * @param image PImage to set as background (must be same size as the sketch window)
   */
  public void background(PImage image) {
    if (recorder != null) recorder.background(image);
    g.background(image);
  }


  /**
   *
   * Changes the way Processing interprets color data. By default, the parameters
   * for <b>fill()</b>, <b>stroke()</b>, <b>background()</b>, and <b>color()</b>
   * are defined by values between 0 and 255 using the RGB color model. The
   * <b>colorMode()</b> function is used to change the numerical range used for
   * specifying colors and to switch color systems. For example, calling
   * <b>colorMode(RGB, 1.0)</b> will specify that values are specified between 0
   * and 1. The limits for defining colors are altered by setting the parameters
   * <b>max</b>, <b>max1</b>, <b>max2</b>, <b>max3</b>, and <b>maxA</b>. <br />
   * <br />
   * After changing the range of values for colors with code like
   * <b>colorMode(HSB, 360, 100, 100)</b>, those ranges remain in use until they
   * are explicitly changed again. For example, after running <b>colorMode(HSB,
   * 360, 100, 100)</b> and then changing back to <b>colorMode(RGB)</b>, the range
   * for R will be 0 to 360 and the range for G and B will be 0 to 100. To avoid
   * this, be explicit about the ranges when changing the color mode. For
   * instance, instead of <b>colorMode(RGB)</b>, write <b>colorMode(RGB, 255, 255,
   * 255)</b>.
   *
   * @webref color:setting
   * @webBrief Changes the way Processing interprets color data
   * @usage web_application
   * @param mode Either RGB or HSB, corresponding to Red/Green/Blue and
   *             Hue/Saturation/Brightness
   * @see PGraphics#background(float)
   * @see PGraphics#fill(float)
   * @see PGraphics#stroke(float)
   */
  public void colorMode(int mode) {
    if (recorder != null) recorder.colorMode(mode);
    g.colorMode(mode);
  }


  /**
   * @param max range for all color elements
   */
  public void colorMode(int mode, float max) {
    if (recorder != null) recorder.colorMode(mode, max);
    g.colorMode(mode, max);
  }


  /**
   * @param max1 range for the red or hue depending on the current color mode
   * @param max2 range for the green or saturation depending on the current color mode
   * @param max3 range for the blue or brightness depending on the current color mode
   */
  public void colorMode(int mode, float max1, float max2, float max3) {
    if (recorder != null) recorder.colorMode(mode, max1, max2, max3);
    g.colorMode(mode, max1, max2, max3);
  }


  /**
   * @param maxA range for the alpha
   */
  public void colorMode(int mode,
                        float max1, float max2, float max3, float maxA) {
    if (recorder != null) recorder.colorMode(mode, max1, max2, max3, maxA);
    g.colorMode(mode, max1, max2, max3, maxA);
  }


  /**
   *
   * Extracts the alpha value from a color.
   *
   * @webref color:creating & reading
   * @webBrief Extracts the alpha value from a color
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#red(int)
   * @see PGraphics#green(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#saturation(int)
   * @see PGraphics#brightness(int)
   */
  public final float alpha(int rgb) {
    return g.alpha(rgb);
  }


  /**
   *
   * Extracts the red value from a color, scaled to match current
   * <b>colorMode()</b>. The value is always returned as a float, so be careful
   * not to assign it to an int value.<br />
   * <br />
   * The <b>red()</b> function is easy to use and understand, but it is slower
   * than a technique called bit shifting. When working in <b>colorMode(RGB,
   * 255)</b>, you can achieve the same results as <b>red()</b> but with greater
   * speed by using the right shift operator (<b>>></b>) with a bit mask. For
   * example, the following two lines of code are equivalent means of getting the
   * red value of the color value <b>c</b>:<br />
   * <br />
   *
   * <pre>
   * float r1 = red(c); // Simpler, but slower to calculate
   * float r2 = c >> 16 & 0xFF; // Very fast to calculate
   * </pre>
   *
   *
   * @webref color:creating & reading
   * @webBrief Extracts the red value from a color, scaled to match current
   *           <b>colorMode()</b>
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#green(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#saturation(int)
   * @see PGraphics#brightness(int)
   * @see_external rightshift
   */
  public final float red(int rgb) {
    return g.red(rgb);
  }


  /**
   *
   * Extracts the green value from a color, scaled to match current
   * <b>colorMode()</b>. The value is always returned as a float, so be careful
   * not to assign it to an int value.<br />
   * <br />
   * The <b>green()</b> function is easy to use and understand, but it is slower
   * than a technique called bit shifting. When working in <b>colorMode(RGB,
   * 255)</b>, you can achieve the same results as <b>green()</b> but with greater
   * speed by using the right shift operator (<b>>></b>) with a bit mask. For
   * example, the following two lines of code are equivalent means of getting the
   * green value of the color value <b>c</b>:<br />
   * <br />
   *
   * <pre>
   * float g1 = green(c); // Simpler, but slower to calculate
   * float g2 = c >> 8 & 0xFF; // Very fast to calculate
   * </pre>
   *
   *
   * @webref color:creating & reading
   * @webBrief Extracts the green value from a color, scaled to match current
   *           <b>colorMode()</b>
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#red(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#saturation(int)
   * @see PGraphics#brightness(int)
   * @see_external rightshift
   */
  public final float green(int rgb) {
    return g.green(rgb);
  }


  /**
   *
   * Extracts the blue value from a color, scaled to match current
   * <b>colorMode()</b>. The value is always returned as a float, so be careful
   * not to assign it to an int value.<br />
   * <br />
   * The <b>blue()</b> function is easy to use and understand, but it is slower
   * than a technique called bit masking. When working in <b>colorMode(RGB,
   * 255)</b>, you can achieve the same results as <b>blue()</b> but with greater
   * speed by using a bit mask to remove the other color components. For example,
   * the following two lines of code are equivalent means of getting the blue
   * value of the color value <b>c</b>:<br />
   * <br />
   *
   * <pre>
   * float b1 = blue(c); // Simpler, but slower to calculate
   * float b2 = c & 0xFF; // Very fast to calculate
   * </pre>
   *
   *
   * @webref color:creating & reading
   * @webBrief Extracts the blue value from a color, scaled to match current
   *           <b>colorMode()</b>
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#red(int)
   * @see PGraphics#green(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#saturation(int)
   * @see PGraphics#brightness(int)
   * @see_external rightshift
   */
  public final float blue(int rgb) {
    return g.blue(rgb);
  }


  /**
   *
   * Extracts the hue value from a color.
   *
   * @webref color:creating & reading
   * @webBrief Extracts the hue value from a color
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#red(int)
   * @see PGraphics#green(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#saturation(int)
   * @see PGraphics#brightness(int)
   */
  public final float hue(int rgb) {
    return g.hue(rgb);
  }


  /**
   *
   * Extracts the saturation value from a color.
   *
   * @webref color:creating & reading
   * @webBrief Extracts the saturation value from a color
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#red(int)
   * @see PGraphics#green(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#brightness(int)
   */
  public final float saturation(int rgb) {
    return g.saturation(rgb);
  }


  /**
   * Extracts the brightness value from a color.
   *
   * @webref color:creating & reading
   * @webBrief Extracts the brightness value from a color
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#red(int)
   * @see PGraphics#green(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#saturation(int)
   */
  public final float brightness(int rgb) {
    return g.brightness(rgb);
  }


  /**
   * @nowebref
   * Interpolate between two colors. Like lerp(), but for the
   * individual color components of a color supplied as an int value.
   */
  static public int lerpColor(int c1, int c2, float amt, int mode) {
    return PGraphics.lerpColor(c1, c2, amt, mode);
  }


  /**
   * Display a warning that the specified method is only available with 3D.
   * @param method The method name (no parentheses)
   */
  static public void showDepthWarning(String method) {
    PGraphics.showDepthWarning(method);
  }


  /**
   * Display a warning that the specified method that takes x, y, z parameters
   * can only be used with x and y parameters in this renderer.
   * @param method The method name (no parentheses)
   */
  static public void showDepthWarningXYZ(String method) {
    PGraphics.showDepthWarningXYZ(method);
  }


  /**
   * Display a warning that the specified method is simply unavailable.
   */
  static public void showMethodWarning(String method) {
    PGraphics.showMethodWarning(method);
  }


  /**
   * Error that a particular variation of a method is unavailable (even though
   * other variations are). For instance, if vertex(x, y, u, v) is not
   * available, but vertex(x, y) is just fine.
   */
  static public void showVariationWarning(String str) {
    PGraphics.showVariationWarning(str);
  }


  /**
   * Display a warning that the specified method is not implemented, meaning
   * that it could be either a completely missing function, although other
   * variations of it may still work properly.
   */
  static public void showMissingWarning(String method) {
    PGraphics.showMissingWarning(method);
  }


  /**
   * Check the alpha on an image, using a really primitive loop.
   */
  public void checkAlpha() {
    if (recorder != null) recorder.checkAlpha();
    g.checkAlpha();
  }


  /**
   *
   * Reads the color of any pixel or grabs a section of an image. If no
   * parameters are specified, the entire image is returned. Use the <b>x</b>
   * and <b>y</b> parameters to get the value of one pixel. Get a section of
   * the display window by specifying an additional <b>width</b> and
   * <b>height</b> parameter. When getting an image, the <b>x</b> and
   * <b>y</b> parameters define the coordinates for the upper-left corner of
   * the image, regardless of the current <b>imageMode()</b>.<br />
   * <br />
   * If the pixel requested is outside the image window, black is returned.
   * The numbers returned are scaled according to the current color
   * ranges, but only RGB values are returned by this function. For example,
   * even though you may have drawn a shape with <b>colorMode(HSB)</b>, the
   * numbers returned will be in RGB format.<br />
   * <br />
   * Getting the color of a single pixel with <b>get(x, y)</b> is easy, but
   * not as fast as grabbing the data directly from <b>pixels[]</b>. The
   * equivalent statement to <b>get(x, y)</b> using <b>pixels[]</b> is
   * <b>pixels[y*width+x]</b>. See the reference for <b>pixels[]</b> for more information.
   *
   * <h3>Advanced</h3>
   * Returns an ARGB "color" type (a packed 32-bit int) with the color.
   * If the coordinate is outside the image, zero is returned
   * (black, but completely transparent).
   * <P>
   * If the image is in RGB format (i.e. on a PVideo object),
   * the value will get its high bits set, just to avoid cases where
   * they haven't been set already.
   * <P>
   * If the image is in ALPHA format, this returns a white with its
   * alpha value set.
   * <P>
   * This function is included primarily for beginners. It is quite
   * slow because it has to check to see if the x, y that was provided
   * is inside the bounds, and then has to check to see what image
   * type it is. If you want things to be more efficient, access the
   * pixels[] array directly.
   *
   * @webref image:pixels
   * @webBrief Reads the color of any pixel or grabs a rectangle of pixels
   * @usage web_application
   * @param x x-coordinate of the pixel
   * @param y y-coordinate of the pixel
   * @see PApplet#set(int, int, int)
   * @see PApplet#pixels
   * @see PApplet#copy(PImage, int, int, int, int, int, int, int, int)
   */
  public int get(int x, int y) {
    return g.get(x, y);
  }


  /**
   * @param w width of pixel rectangle to get
   * @param h height of pixel rectangle to get
   */
  public PImage get(int x, int y, int w, int h) {
    return g.get(x, y, w, h);
  }


  /**
   * Returns a copy of this PImage. Equivalent to get(0, 0, width, height).
   * Deprecated, just use copy() instead.
   */
  public PImage get() {
    return g.get();
  }


  public PImage copy() {
    return g.copy();
  }


  /**
   *
   * Changes the color of any pixel or writes an image directly into the
   * display window.<br />
   * <br />
   * The <b>x</b> and <b>y</b> parameters specify the pixel to change and the
   * <b>color</b> parameter specifies the color value. The color parameter is
   * affected by the current color mode (the default is RGB values from 0 to
   * 255). When setting an image, the <b>x</b> and <b>y</b> parameters define
   * the coordinates for the upper-left corner of the image, regardless of
   * the current <b>imageMode()</b>.
   * <br /><br />
   * Setting the color of a single pixel with <b>set(x, y)</b> is easy, but
   * not as fast as putting the data directly into <b>pixels[]</b>. The
   * equivalent statement to <b>set(x, y, #000000)</b> using <b>pixels[]</b>
   * is <b>pixels[y*width+x] = #000000</b>. See the reference for
   * <b>pixels[]</b> for more information.
   *
   * @webref image:pixels
   * @webBrief Writes a color to any pixel or writes an image into another
   * @usage web_application
   * @param x x-coordinate of the pixel
   * @param y y-coordinate of the pixel
   * @param c any value of the color datatype
   * @see PImage#get(int, int, int, int)
   * @see PImage#pixels
   * @see PImage#copy(PImage, int, int, int, int, int, int, int, int)
   */
  public void set(int x, int y, int c) {
    if (recorder != null) recorder.set(x, y, c);
    g.set(x, y, c);
  }


  /**
   * <h3>Advanced</h3>
   * Efficient method of drawing an image's pixels directly to this surface.
   * No variations are employed, meaning that any scale, tint, or imageMode
   * settings will be ignored.
   *
   * @param img image to copy into the original image
   */
  public void set(int x, int y, PImage img) {
    if (recorder != null) recorder.set(x, y, img);
    g.set(x, y, img);
  }


  /**
   *
   * Masks part of an image from displaying by loading another image and
   * using it as an alpha channel. This mask image should only contain
   * grayscale data, but only the blue color channel is used. The mask image
   * needs to be the same size as the image to which it is applied.<br />
   * <br />
   * In addition to using a mask image, an integer array containing the alpha
   * channel data can be specified directly. This method is useful for
   * creating dynamically generated alpha masks. This array must be of the
   * same length as the target image's pixels array and should contain only
   * grayscale data of values between 0-255.
   *
   * <h3>Advanced</h3>
   *
   * Set alpha channel for an image. Black colors in the source
   * image will make the destination image completely transparent,
   * and white will make things fully opaque. Gray values will
   * be in-between steps.
   * <P>
   * Strictly speaking the "blue" value from the source image is
   * used as the alpha color. For a fully grayscale image, this
   * is correct, but for a color image it's not 100% accurate.
   * For a more accurate conversion, first use filter(GRAY)
   * which will make the image into a "correct" grayscale by
   * performing a proper luminance-based conversion.
   *
   * @webref image:pixels
   * @webBrief Masks part of an image with another image as an alpha channel
   * @usage web_application
   * @param img image to use as the mask
   */
  public void mask(PImage img) {
    if (recorder != null) recorder.mask(img);
    g.mask(img);
  }


  public void filter(int kind) {
    if (recorder != null) recorder.filter(kind);
    g.filter(kind);
  }


  /**
   * Filters the image as defined by one of the following modes:<br />
   * <br />
   * THRESHOLD<br />
   * Converts the image to black and white pixels depending on if they
   * are above or below the threshold defined by the level parameter.
   * The parameter must be between 0.0 (black) and 1.0 (white).
   * If no level is specified, 0.5 is used.<br />
   * <br />
   * GRAY<br />
   * Converts any colors in the image to grayscale equivalents. No parameter is
   * used.<br />
   * <br />
   * OPAQUE<br />
   * Sets the alpha channel to entirely opaque. No parameter is used.<br />
   * <br />
   * INVERT<br />
   * Sets each pixel to its inverse value. No parameter is used.<br />
   * <br />
   * POSTERIZE<br />
   * Limits each channel of the image to the number of colors specified as the
   * parameter. The parameter can be set to values between 2 and 255, but results
   * are most noticeable in the lower ranges.<br />
   * <br />
   * BLUR<br />
   * Executes a Gaussian blur with the level parameter specifying the extent of
   * the blurring. If no parameter is used, the blur is equivalent to Gaussian
   * blur of radius 1. Larger values increase the blur.<br />
   * <br />
   * ERODE<br />
   * Reduces the light areas. No parameter is used.<br />
   * <br />
   * DILATE<br />
   * Increases the light areas. No parameter is used.
   *
   * <h3>Advanced</h3> Method to apply a variety of basic filters to this image.
   * <P>
   * <UL>
   * <LI>filter(BLUR) provides a basic blur.
   * <LI>filter(GRAY) converts the image to grayscale based on luminance.
   * <LI>filter(INVERT) will invert the color components in the image.
   * <LI>filter(OPAQUE) set all the high bits in the image to opaque
   * <LI>filter(THRESHOLD) converts the image to black and white.
   * <LI>filter(DILATE) grow white/light areas
   * <LI>filter(ERODE) shrink white/light areas
   * </UL>
   * Luminance conversion code contributed by
   * <A HREF="http://www.toxi.co.uk">toxi</A>
   * <P/>
   * Gaussian blur code contributed by
   * <A HREF="http://incubator.quasimondo.com">Mario Klingemann</A>
   *
   * @webref image:pixels
   * @webBrief Converts the image to grayscale or black and white
   * @usage web_application
   * @param kind  Either THRESHOLD, GRAY, OPAQUE, INVERT, POSTERIZE, BLUR, ERODE,
   *              or DILATE
   * @param param unique for each, see above
   */
  public void filter(int kind, float param) {
    if (recorder != null) recorder.filter(kind, param);
    g.filter(kind, param);
  }


  /**
   * Copies a region of pixels from one image into another. If the source and
   * destination regions aren't the same size, it will automatically resize
   * source pixels to fit the specified target region. No alpha information
   * is used in the process, however if the source image has an alpha channel
   * set, it will be copied as well.
   * <br /><br />
   * As of release 0149, this function ignores <b>imageMode()</b>.
   *
   * @webref image:pixels
   * @webBrief Copies the entire image
   * @usage web_application
   * @param sx X coordinate of the source's upper left corner
   * @param sy Y coordinate of the source's upper left corner
   * @param sw source image width
   * @param sh source image height
   * @param dx X coordinate of the destination's upper left corner
   * @param dy Y coordinate of the destination's upper left corner
   * @param dw destination image width
   * @param dh destination image height
   * @see PGraphics#alpha(int)
   * @see PImage#blend(PImage, int, int, int, int, int, int, int, int, int)
   */
  public void copy(int sx, int sy, int sw, int sh,
                   int dx, int dy, int dw, int dh) {
    if (recorder != null) recorder.copy(sx, sy, sw, sh, dx, dy, dw, dh);
    g.copy(sx, sy, sw, sh, dx, dy, dw, dh);
  }


/**
 * @param src an image variable referring to the source image.
 */
  public void copy(PImage src,
                   int sx, int sy, int sw, int sh,
                   int dx, int dy, int dw, int dh) {
    if (recorder != null) recorder.copy(src, sx, sy, sw, sh, dx, dy, dw, dh);
    g.copy(src, sx, sy, sw, sh, dx, dy, dw, dh);
  }


  public void blend(int sx, int sy, int sw, int sh,
                    int dx, int dy, int dw, int dh, int mode) {
    if (recorder != null) recorder.blend(sx, sy, sw, sh, dx, dy, dw, dh, mode);
    g.blend(sx, sy, sw, sh, dx, dy, dw, dh, mode);
  }


  /**
   *
   * Blends a region of pixels into the image specified by the <b>img</b>
   * parameter. These copies utilize full alpha channel support and a choice
   * of the following modes to blend the colors of source pixels (A) with the
   * ones of pixels in the destination image (B):<br />
   * <br />
   * BLEND - linear interpolation of colours: <b>C = A*factor + B</b><br />
   * <br />
   * ADD - additive blending with white clip: <b>C = min(A*factor + B, 255)</b><br />
   * <br />
   * SUBTRACT - subtractive blending with black clip: <b>C = max(B - A*factor,
   * 0)</b><br />
   * <br />
   * DARKEST - only the darkest colour succeeds: <b>C = min(A*factor, B)</b><br />
   * <br />
   * LIGHTEST - only the lightest colour succeeds: <b>C = max(A*factor, B)</b><br />
   * <br />
   * DIFFERENCE - subtract colors from underlying image.<br />
   * <br />
   * EXCLUSION - similar to DIFFERENCE, but less extreme.<br />
   * <br />
   * MULTIPLY - Multiply the colors, result will always be darker.<br />
   * <br />
   * SCREEN - Opposite multiply, uses inverse values of the colors.<br />
   * <br />
   * OVERLAY - A mix of MULTIPLY and SCREEN. Multiplies dark values,
   * and screens light values.<br />
   * <br />
   * HARD_LIGHT - SCREEN when greater than 50% gray, MULTIPLY when lower.<br />
   * <br />
   * SOFT_LIGHT - Mix of DARKEST and LIGHTEST.
   * Works like OVERLAY, but not as harsh.<br />
   * <br />
   * DODGE - Lightens light tones and increases contrast, ignores darks.
   * Called "Color Dodge" in Illustrator and Photoshop.<br />
   * <br />
   * BURN - Darker areas are applied, increasing contrast, ignores lights.
   * Called "Color Burn" in Illustrator and Photoshop.<br />
   * <br />
   * All modes use the alpha information (the highest byte) of source image
   * pixels as the blending factor. If the source and destination regions are
   * different sizes, the image will be automatically resized to match the
   * destination size. If the <b>srcImg</b> parameter is not used, the
   * display window is used as the source image.<br />
   * <br />
   * As of release 0149, this function ignores <b>imageMode()</b>.
   *
   * @webref image:pixels
   * @webBrief Copies a pixel or rectangle of pixels using different blending modes
   * @param src an image variable referring to the source image
   * @param sx X coordinate of the source's upper left corner
   * @param sy Y coordinate of the source's upper left corner
   * @param sw source image width
   * @param sh source image height
   * @param dx X coordinate of the destination's upper left corner
   * @param dy Y coordinate of the destination's upper left corner
   * @param dw destination image width
   * @param dh destination image height
   * @param mode Either BLEND, ADD, SUBTRACT, LIGHTEST, DARKEST, DIFFERENCE, EXCLUSION, MULTIPLY, SCREEN, OVERLAY, HARD_LIGHT, SOFT_LIGHT, DODGE, BURN
   *
   * @see PApplet#alpha(int)
   * @see PImage#copy(PImage, int, int, int, int, int, int, int, int)
   * @see PImage#blendColor(int,int,int)
   */
  public void blend(PImage src,
                    int sx, int sy, int sw, int sh,
                    int dx, int dy, int dw, int dh, int mode) {
    if (recorder != null) recorder.blend(src, sx, sy, sw, sh, dx, dy, dw, dh, mode);
    g.blend(src, sx, sy, sw, sh, dx, dy, dw, dh, mode);
  }
}
