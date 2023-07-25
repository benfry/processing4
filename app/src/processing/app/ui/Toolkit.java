/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-19 The Processing Foundation

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

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.ImageObserver;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import processing.app.Language;
import processing.app.Messages;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.Util;
import processing.awt.PGraphicsJava2D;
import processing.awt.PShapeJava2D;
import processing.core.PApplet;
import processing.core.PShape;
import processing.data.StringDict;
import processing.data.StringList;
import processing.data.XML;


/**
 * Utility functions for base that require a java.awt.Toolkit object. These
 * are broken out from Base as we start moving toward the possibility of the
 * code running in headless mode.
 */
public class Toolkit {
  static final java.awt.Toolkit awtToolkit =
    java.awt.Toolkit.getDefaultToolkit();

  /** Command on Mac OS X, Ctrl on Windows and Linux */
  static final int SHORTCUT_KEY_MASK =
    awtToolkit.getMenuShortcutKeyMaskEx();
  /** Command-Option on Mac OS X, Ctrl-Alt on Windows and Linux */
  static final int SHORTCUT_ALT_KEY_MASK =
    ActionEvent.ALT_MASK | SHORTCUT_KEY_MASK;
  /** Command-Shift on Mac OS X, Ctrl-Shift on Windows and Linux */
  static final int SHORTCUT_SHIFT_KEY_MASK =
    ActionEvent.SHIFT_MASK | SHORTCUT_KEY_MASK;

  /** Command-W on Mac OS X, Ctrl-W on Windows and Linux */
  static public final KeyStroke WINDOW_CLOSE_KEYSTROKE =
    KeyStroke.getKeyStroke('W', SHORTCUT_KEY_MASK);

  static final String BAD_KEYSTROKE =
    "'%s' is not understood, please re-read the Java reference for KeyStroke";

  /**
   * Standardized width for buttons. Mac OS X 10.3 wants 70 as its default,
   * Windows XP needs 66, and my Ubuntu machine needs 80+, so 80 seems proper.
   * This is now stored in the languages file since this may need to be larger
   * for languages that are consistently wider than English.
   */
  static public int getButtonWidth() {
    // Made into a method so that calling Toolkit methods doesn't require
    // the languages to be loaded, and with that, Base initialized completely
    return zoom(Integer.parseInt(Language.text("preferences.button.width")));
  }


  /**
   * Return the correct KeyStroke per locale and platform.
   * Also checks for any additional overrides in preferences.txt.
   * @param base the localization key for the menu item
   *             (.keystroke and .platform will be added to the end)
   * @return KeyStroke for base + .keystroke + .platform
   *         (or the value from preferences) or null if none found
   */
  static public KeyStroke getKeyStrokeExt(String base) {
    String key = base + ".keystroke";

    // see if there's an override in preferences.txt
    String sequence = Preferences.get(key);
    if (sequence != null) {
      KeyStroke ks = KeyStroke.getKeyStroke(sequence);
      if (ks != null) {
        return ks;  // user did good, we're all set

      } else {
        System.err.format(BAD_KEYSTROKE, sequence);
      }
    }

    sequence = Language.text(key + "." + Platform.getName());
    KeyStroke ks = KeyStroke.getKeyStroke(sequence);
    if (ks == null) {
      // this can only happen if user has screwed up their language files
      System.err.format(BAD_KEYSTROKE, sequence);
      //return KeyStroke.getKeyStroke(0, 0);  // badness
    }
    return ks;
  }


  /**
   * Create a menu item and set its KeyStroke by name (so it can be stored
   * in the language settings or the preferences). Syntax is .
   * <a href="https://docs.oracle.com/javase/8/docs/api/javax/swing/KeyStroke.html#getKeyStroke-java.lang.String-">here</a>.
   */
  static public JMenuItem newJMenuItemExt(String base) {
    JMenuItem menuItem = new JMenuItem(Language.text(base));
    KeyStroke ks = getKeyStrokeExt(base);  // will print error if necessary
    if (ks != null) {
      menuItem.setAccelerator(ks);
    }
    return menuItem;
  }


  /**
   * A software engineer, somewhere, needs to have their abstraction
   * taken away. Who crafts the sort of API that would require a
   * five-line helper function just to set the shortcut key for a
   * menu item?
   */
  static public JMenuItem newJMenuItem(String title, int what) {
    JMenuItem menuItem = new JMenuItem(title);
    menuItem.setAccelerator(KeyStroke.getKeyStroke(what, SHORTCUT_KEY_MASK));
    return menuItem;
  }


  /**
   * @param action: use an Action, which sets the title, reaction
   *                and enabled-ness all by itself.
   */
  static public JMenuItem newJMenuItem(Action action, int what) {
    JMenuItem menuItem = new JMenuItem(action);
    // Use putValue() instead of setAccelerator() to work with applyAction()
//    menuItem.setAccelerator(KeyStroke.getKeyStroke(what, SHORTCUT_KEY_MASK));
    action.putValue(Action.ACCELERATOR_KEY,
                    KeyStroke.getKeyStroke(what, SHORTCUT_KEY_MASK));
    return menuItem;
  }


  /**
   * Like newJMenuItem() but adds shift as a modifier for the shortcut.
   */
  static public JMenuItem newJMenuItemShift(String title, int what) {
    JMenuItem menuItem = new JMenuItem(title);
    menuItem.setAccelerator(KeyStroke.getKeyStroke(what, SHORTCUT_SHIFT_KEY_MASK));
    return menuItem;
  }


  /**
   * Like newJMenuItem() but adds shift as a modifier for the shortcut.
   */
  static public JMenuItem newJMenuItemShift(Action action, int what) {
    JMenuItem menuItem = new JMenuItem(action);
    // Use putValue() instead of setAccelerator() to work with applyAction()
//    menuItem.setAccelerator(KeyStroke.getKeyStroke(what, SHORTCUT_SHIFT_KEY_MASK));
    action.putValue(Action.ACCELERATOR_KEY,
                    KeyStroke.getKeyStroke(what, SHORTCUT_SHIFT_KEY_MASK));
    return menuItem;
  }


  /**
   * Same as newJMenuItem(), but adds the ALT (on Linux and Windows)
   * or OPTION (on Mac OS X) key as a modifier. This function should almost
   * never be used, because it's bad for non-US keyboards that use ALT in
   * strange and wondrous ways.
   */
  static public JMenuItem newJMenuItemAlt(String title, int what) {
    JMenuItem menuItem = new JMenuItem(title);
    menuItem.setAccelerator(KeyStroke.getKeyStroke(what, SHORTCUT_ALT_KEY_MASK));
    return menuItem;
  }


  static public JCheckBoxMenuItem newJCheckBoxMenuItem(String title, int what) {
    JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(title);
    menuItem.setAccelerator(KeyStroke.getKeyStroke(what, SHORTCUT_KEY_MASK));
    return menuItem;
  }


  static public void addDisabledItem(JMenu menu, String title) {
    JMenuItem item = new JMenuItem(title);
    item.setEnabled(false);
    menu.add(item);
  }


  /**
   * Apply an Action from something else (i.e. a JMenuItem) to a JButton.
   * Swing is so absof*ckinglutely convoluted sometimes. Do we really need
   * half a dozen lines of boilerplate to apply a key shortcut to a button?
   */
  static public void applyAction(Action action, JButton button) {
    button.setAction(action);
    // use an arbitrary but unique name
    String name = String.valueOf(action.hashCode());
    button.getActionMap().put(name, action);
    button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
      .put((KeyStroke) action.getValue(Action.ACCELERATOR_KEY), name);
  }


  /**
   * Removes all mnemonics, then sets a mnemonic for each menu and menu
   * item recursively by these rules:
   * <ol>
   * <li> It tries to assign one of <a href="http://techbase.kde.org/Projects/Usability/HIG/Keyboard_Accelerators">
   * KDE's defaults</a>.</li>
   * <li>
   *   Failing that, it loops through the first letter of each word,
   *   where a word is a block of Unicode "alphabetical" chars, looking
   *   for an upper-case ASCII mnemonic that is not taken.
   *   This is to try to be relevant, by using a letter well-associated
   *   with the command. (MS guidelines)
   * </li>
   * <li>
   *   Ditto, but with lowercase.
   * </li>
   * <li>
   *   Next, it tries the second ASCII character, if its width
   *   &gt;= half the width of 'A'.
   * </li>
   * <li>
   *   If the first letters are all taken/non-ASCII, then it loops
   *   through the ASCII letters in the item, widest to narrowest,
   *   seeing if any of them is not taken. To improve readability,
   *   it discriminates against descenders (qypgj), imagining they
   *   have 2/3 their actual width. (MS guidelines: avoid descenders).
   *   It also discriminates against vowels, imagining they have 2/3
   *   their actual width. (MS and Gnome guidelines: avoid vowels.)
   * </li>
   * <li>
   *   Failing that, it will loop left-to-right for an available digit.
   *   This is a last resort because the normal setMnemonic dislikes them.
   * </li>
   * <li>
   *   If that doesn't work, it doesn't assign a mnemonic.
   * </li>
   * </ol>
   *
   * Additional rules:
   * <ul>
   * <li>
   *   As a special case, strings starting "sketchbook → " have that
   *   bit ignored because otherwise the Recent menu looks awful.
   * </li>
   * <li>
   *   However, the name <tt>"sketchbook → Sketch"</tt>,
   *   for example, will have the 'S' of "Sketch" chosen,
   *   but the 's' of 'sketchbook' will get underlined.
   * </li>
   * <li>
   *   No letter by an underscore will be assigned.
   * </li>
   * <li>
   *   Disabled on Mac, per Apple guidelines.
   * </li>
   * </ul>
   *
   * Written by George Bateman, with Initial work Myer Nore.
   * @param menu Menu items to set mnemonics for (null entries are ok)
   */
  static public void setMenuMnemonics(JMenuItem... menu) {
    if (Platform.isMacOS()) return;
    if (menu.length == 0) return;

    // The English is http://techbase.kde.org/Projects/Usability/HIG/Keyboard_Accelerators,
    // made lowercase.
    // Nothing but [a-z] except for '&' before mnemonics and regexes for changeable text.
    final String[] kdePreDefStrs = {
      "&file", "&new", "&open", "open&recent",
      "&save", "save&as", "saveacop&y", "saveas&template", "savea&ll", "reloa&d",
      "&print", "printpre&view", "&import", "e&xport", "&closefile",
      "clos&eallfiles", "&quit", "&edit", "&undo", "re&do", "cu&t", "&copy",
      "&paste", "&delete", "select&all", "dese&lect", "&find", "find&next",
      "findpre&vious", "&replace", "&gotoline", "&view", "&newview",
      "close&allviews", "&splitview", "&removeview", "splitter&orientation",
      "&horizontal", "&vertical", "view&mode", "&fullscreenmode", "&zoom",
      "zoom&in", "zoom&out", "zoomtopage&width", "zoomwhole&page", "zoom&factor",
      "&insert", "&format", "&go", "&up", "&back", "&forward", "&home", "&go",
      "&previouspage", "&nextpage", "&firstpage", "&lastpage", "read&updocument",
      "read&downdocument", "&back", "&forward", "&gotopage", "&bookmarks",
      "&addbookmark", "bookmark&tabsasfolder", "&editbookmarks",
      "&newbookmarksfolder", "&tools", "&settings", "&toolbars",
      "configure&shortcuts", "configuretool&bars", "&configure.*", "&help",
      ".+&handbook", "&whatsthis", "report&bug", "&aboutprocessing", "about&kde",
      "&beenden", "&suchen",  // de
      "&preferncias", "&sair",  // Preferências; pt
      "&rechercher"  // fr
    };
    Pattern[] kdePreDefPats = new Pattern[kdePreDefStrs.length];
    for (int i = 0; i < kdePreDefStrs.length; i++) {
      kdePreDefPats[i] = Pattern.compile(kdePreDefStrs[i].replace("&",""));
    }

    final Pattern nonAAlpha = Pattern.compile("[^A-Za-z]");
    FontMetrics fmTmp = null;
    for (JMenuItem m : menu) {
      if (m != null) {
        fmTmp = m.getFontMetrics(m.getFont());
        break;
      }
    }
    if (fmTmp == null) return; // All null menuitems; would fail.
    final FontMetrics fm = fmTmp; // Hack for accessing variable in comparator.

    final Comparator<Character> charComparator = new Comparator<>() {
      final char[] baddies = "qypgjaeiouQAEIOU".toCharArray();
      public int compare(Character ch1, Character ch2) {
        // Discriminates against descenders for readability, per MS
        // Human Interface Guide, and vowels per MS and Gnome.
        float w1 = fm.charWidth(ch1), w2 = fm.charWidth(ch2);
        for (char bad : baddies) {
          if (bad == ch1) w1 *= 0.66f;
          if (bad == ch2) w2 *= 0.66f;
        }
        return (int)Math.signum(w2 - w1);
      }
    };

    // Holds only [0-9a-z], not uppercase.
    // Prevents X != x, so "Save" and "Save As" aren't both given 'a'.
    final List<Character> taken = new ArrayList<>(menu.length);
    char firstChar;
    char[] cleanChars;
    Character[] cleanCharas;

    // METHOD 1: attempt to assign KDE defaults.
    for (JMenuItem jmi : menu) {
      if (jmi == null) continue;
      if (jmi.getText() == null) continue;
      jmi.setMnemonic(0); // Reset all mnemonics.
      String asciiName = nonAAlpha.matcher(jmi.getText()).replaceAll("");
      String lAsciiName = asciiName.toLowerCase();
      for (int i = 0; i < kdePreDefStrs.length; i++) {
        if (kdePreDefPats[i].matcher(lAsciiName).matches()) {
          char mnem = asciiName.charAt(kdePreDefStrs[i].indexOf("&"));
          jmi.setMnemonic(mnem);
          jmi.setDisplayedMnemonicIndex(jmi.getText().indexOf(mnem));
          taken.add((char)(mnem | 32)); // to lowercase
          break;
        }
      }
    }

    // Where KDE defaults fail, use an algorithm.
    algorithmicAssignment:
    for (JMenuItem jmi : menu) {
      if (jmi == null) continue;
      if (jmi.getText() == null) continue;
      if (jmi.getMnemonic() != 0) continue; // Already assigned.

      // The string can't be made lower-case as that would spoil
      // the width comparison.
      String cleanString = jmi.getText();
      if (cleanString.startsWith("sketchbook → "))
        cleanString = cleanString.substring(13);

      if (cleanString.length() == 0) continue;

      // First, ban letters by underscores.
      final List<Character> banned = new ArrayList<>();
      for (int i = 0; i < cleanString.length(); i++) {
        if (cleanString.charAt(i) == '_') {
          if (i > 0)
            banned.add(Character.toLowerCase(cleanString.charAt(i - 1)));
          if (i + 1 < cleanString.length())
            banned.add(Character.toLowerCase(cleanString.charAt(i + 1)));
        }
      }

      // METHOD 2: Uppercase starts of words.
      // Splitting into blocks of ASCII letters wouldn't work
      // because there could be non-ASCII letters in a word.
      for (String wd : cleanString.split("[^\\p{IsAlphabetic}]")) {
        if (wd.length() == 0) continue;
        firstChar = wd.charAt(0);
        if (taken.contains(Character.toLowerCase(firstChar))) continue;
        if (banned.contains(Character.toLowerCase(firstChar))) continue;
        if ('A' <= firstChar && firstChar <= 'Z') {
          jmi.setMnemonic(firstChar);
          jmi.setDisplayedMnemonicIndex(jmi.getText().indexOf(firstChar));
          taken.add((char)(firstChar | 32)); // tolowercase
          continue algorithmicAssignment;
        }
      }

      // METHOD 3: Lowercase starts of words.
      for (String wd : cleanString.split("[^\\p{IsAlphabetic}]")) {
        if (wd.length() == 0) continue;
        firstChar = wd.charAt(0);
        if (taken.contains(Character.toLowerCase(firstChar))) continue;
        if (banned.contains(Character.toLowerCase(firstChar))) continue;
        if ('a' <= firstChar && firstChar <= 'z') {
          jmi.setMnemonic(firstChar);
          jmi.setDisplayedMnemonicIndex(jmi.getText().indexOf(firstChar));
          taken.add(firstChar); // is lowercase
          continue algorithmicAssignment;
        }
      }

      // METHOD 4: Second wide-enough ASCII letter.
      cleanString = nonAAlpha.matcher(jmi.getText()).replaceAll("");
      if (cleanString.length() >= 2) {
        char ascii2nd = cleanString.charAt(1);
        if (!taken.contains((char)(ascii2nd|32)) &&
            !banned.contains((char)(ascii2nd|32)) &&
            fm.charWidth('A') <= 2*fm.charWidth(ascii2nd)) {
          jmi.setMnemonic(ascii2nd);
          jmi.setDisplayedMnemonicIndex(jmi.getText().indexOf(ascii2nd));
          taken.add((char)(ascii2nd|32));
          continue algorithmicAssignment;
        }
      }

      // METHOD 5: charComparator over all ASCII letters.
      cleanChars  = cleanString.toCharArray();
      cleanCharas = new Character[cleanChars.length];
      for (int i = 0; i < cleanChars.length; i++) {
        cleanCharas[i] = cleanChars[i];
      }
      Arrays.sort(cleanCharas, charComparator); // sorts in increasing order
      for (char mnem : cleanCharas) {
        if (taken.contains(Character.toLowerCase(mnem))) continue;
        if (banned.contains(Character.toLowerCase(mnem))) continue;

        // NB: setMnemonic(char) doesn't want [^A-Za-z]
        jmi.setMnemonic(mnem);
        jmi.setDisplayedMnemonicIndex(jmi.getText().indexOf(mnem));
        taken.add(Character.toLowerCase(mnem));
        continue algorithmicAssignment;
      }

      // METHOD 6: Digits as last resort.
      for (char digit : jmi.getText().replaceAll("[^0-9]", "").toCharArray()) {
        if (taken.contains(digit)) continue;
        if (banned.contains(digit)) continue;
        jmi.setMnemonic(KeyEvent.VK_0 + digit - '0');
        // setDisplayedMnemonicIndex() unneeded: no case issues.
        taken.add(digit);
        continue algorithmicAssignment;
      }
    }

    // Finally, RECURSION.
    for (JMenuItem jmi : menu) {
      if (jmi instanceof JMenu) setMenuMnemsInside((JMenu) jmi);
    }
  }


  /**
   * As setMenuMnemonics(JMenuItem...).
   */
  static public void setMenuMnemonics(JMenuBar menubar) {
    JMenuItem[] items = new JMenuItem[menubar.getMenuCount()];
    for (int i = 0; i < items.length; i++) {
      items[i] = menubar.getMenu(i);
    }
    setMenuMnemonics(items);
  }


  /**
   * As setMenuMnemonics(JMenuItem...).
   */
  static public void setMenuMnemonics(JPopupMenu menu) {
    ArrayList<JMenuItem> items = new ArrayList<>();

    for (Component c : menu.getComponents()) {
      if (c instanceof JMenuItem) items.add((JMenuItem)c);
    }
    setMenuMnemonics(items.toArray(new JMenuItem[0]));
  }


  /**
   * Calls setMenuMnemonics(JMenuItem...) on the sub-elements only.
   */
  static public void setMenuMnemsInside(JMenu menu) {
    JMenuItem[] items = new JMenuItem[menu.getItemCount()];
    for (int i = 0; i < items.length; i++) {
      items[i] = menu.getItem(i);
    }
    setMenuMnemonics(items);
  }


  static public int getMenuItemIndex(JMenu menu, JMenuItem item) {
    int index = 0;
    for (Component comp : menu.getMenuComponents()) {
      if (comp == item) {
        return index;
      }
      index++;
    }
    return -1;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static public Dimension getScreenSize() {
    return awtToolkit.getScreenSize();
  }


  /**
   * Return an Image object from inside the Processing 'lib' folder.
   * Moved here so that Base can stay headless.
   */
  static public Image getLibImage(String filename) {
    ImageIcon icon = getLibIcon(filename);
    return (icon == null) ? null : icon.getImage();
  }


  /**
   * Get an ImageIcon from the Processing 'lib' folder.
   * @since 3.0a6
   */
  static public ImageIcon getLibIcon(String filename) {
    File file = Platform.getContentFile("lib/" + filename);
    if (file == null || !file.exists()) {
      Messages.err("does not exist: " + file);
      return null;
    }
    return new ImageIcon(file.getAbsolutePath());
  }


  static public ImageIcon getIconX(File dir, String base) {
    return getIconX(dir, base, 0);
  }


  /*
  static public String getLibString(String filename) {
    File file = Platform.getContentFile("lib/" + filename);
    if (file == null || !file.exists()) {
      Messages.err("does not exist: " + file);
      return null;
    }
    return PApplet.join(PApplet.loadStrings(file), "\n");
  }
  */


  /**
   * Get an icon of the format base-NN.png where NN is the size, but if it's
   * a hidpi display, get the NN*2 version automatically, sized at NN
   */
  static public ImageIcon getIconX(File dir, String base, int size) {
    final int scale = Toolkit.highResImages() ? 2 : 1;
    String filename = (size == 0) ?
      (base + "-" + scale + "x.png") :
      (base + "-" + (size*scale) + ".png");
    File file = new File(dir, filename);
    if (!file.exists()) {
      return null;
    }

    // Not broken into separate class because it requires (and is
    // optimized for) an image file, and the SVG version does not.
    // Also moving away from images from files anyway. [fry 220501]
    return new ImageIcon(file.getAbsolutePath()) {
      @Override
      public int getIconWidth() {
        return Toolkit.zoom(super.getIconWidth()) / scale;
      }

      @Override
      public int getIconHeight() {
        return Toolkit.zoom(super.getIconHeight()) / scale;
      }

      @Override
      public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
        ImageObserver imageObserver = getImageObserver();
        if (imageObserver == null) {
          imageObserver = c;
        }
        g.drawImage(getImage(), x, y, getIconWidth(), getIconHeight(), imageObserver);
      }
    };
  }


  /**
   * Get an image icon with hi-dpi support. Pulls 1x or 2x versions of the
   * file depending on the display type, but sizes them based on 1x.
   */
  static public ImageIcon getLibIconX(String base) {
    return getLibIconX(base, 0);
  }


  static public ImageIcon getLibIconX(String base, int size) {
    return getIconX(Platform.getContentFile("lib"), base, size);
  }


//  /**
//   * Create a JButton with an icon, and set its disabled and pressed images
//   * to be the same image, so that 2x versions of the icon work properly.
//   */
//  static public JButton createIconButton(String title, String base) {
//    ImageIcon icon = Toolkit.getLibIconX(base);
//    return createIconButton(title, icon);
//  }
//
//
//  /** Same as above, but with no text title (follows JButton constructor) */
//  static public JButton createIconButton(String base) {
//    return createIconButton(null, base);
//  }
//
//
//  static public JButton createIconButton(String title, Icon icon) {
//    JButton button = new JButton(title, icon);
//    button.setDisabledIcon(icon);
//    button.setPressedIcon(icon);
//    return button;
//  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static List<Image> iconImages;


  /**
   * Unnecessary version of the function, but can't get rid of it
   * without breaking tools and modes (they'd only require a recompile,
   * but they would no longer be backwards compatible).
   */
  static public void setIcon(Frame frame) {
    setIcon((Window) frame);
  }


  /**
   * Give this Frame the Processing icon set. Ignored on OS X, because they
   * thought different and made this function set the minified image of the
   * window, not the window icon for the dock or cmd-tab.
   */
  static public void setIcon(Window window) {
    if (!Platform.isMacOS()) {
      if (iconImages == null) {
        iconImages = new ArrayList<>();
        final int[] sizes = { 16, 32, 48, 64, 128, 256, 512 };
        for (int sz : sizes) {
          iconImages.add(Toolkit.getLibImage("icons/app-" + sz + ".png"));
        }
      }
      window.setIconImages(iconImages);
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Create an Icon object from an SVG path name.
   * @param name subpath relative to lib/
   * @param color color to apply to the monochrome icon
   * @param size size in pixels (handling for 2x done automatically)
   */
  static public ImageIcon renderIcon(String name, String color, int size) {
    return renderIcon(Platform.getContentFile("lib/" + name + ".svg"), color, size);
  }


  /**
   * Create an Icon object from an SVG path name.
   * @param file full path to icon
   * @param color color to apply to the monochrome icon
   * @param size size in pixels (handling for 2x done automatically)
   */
  static public ImageIcon renderIcon(File file, String color, int size) {
    Image image = renderMonoImage(file, color, size);
    return (image != null) ? wrapIcon(image) : null;
  }


  static public ImageIcon wrapIcon(Image image) {
    final int scale = Toolkit.highResImages() ? 2 : 1;

    return new ImageIcon(image) {
      @Override
      public int getIconWidth() {
        return Toolkit.zoom(super.getIconWidth()) / scale;
      }

      @Override
      public int getIconHeight() {
        return Toolkit.zoom(super.getIconHeight()) / scale;
      }

      @Override
      public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
        ImageObserver imageObserver = getImageObserver();
        if (imageObserver == null) {
          imageObserver = c;
        }
        g.drawImage(getImage(), x, y, getIconWidth(), getIconHeight(), imageObserver);
      }
    };
  }


  static protected Image renderMonoImage(File file, String color, int size) {
    String xmlOrig = Util.loadFile(file);

    if (xmlOrig != null) {
      StringDict replace = new StringDict(new String[][] {
        { "#9B9B9B", color }
      });
      return Toolkit.svgToImageMult(xmlOrig, size, size, replace);
    }
    return null;
  }


  /*
  static private Image svgToImageMult(String xmlStr, int wide, int high) {
    return svgToImage(xmlStr, highResMultiply(wide), highResMultiply(high));
  }
  */


  static public Image svgToImageMult(String xmlStr, int wide, int high, StringDict replacements) {
    /*
    for (StringDict.Entry entry : replacements.entries()) {
      xmlStr = xmlStr.replace(entry.key, entry.value);
    }
    */
    // 2-pass version to avoid re-assigning identical colors
    // (Otherwise, if a color is set to #666666 before #666666 is
    // re-assigned to its new color, the swap will happen twice.)
    for (StringDict.Entry entry : replacements.entries()) {
      xmlStr = xmlStr.replace(entry.key, "$" + entry.key.hashCode() + "$");
    }
    for (StringDict.Entry entry : replacements.entries()) {
      xmlStr = xmlStr.replace("$" + entry.key.hashCode() + "$", entry.value);
    }
    return svgToImage(xmlStr, highResMultiply(wide), highResMultiply(high));
  }


  /**
   * Render an SVG, passed in as a String, into an AWT Image at
   * the specified width and height. Used for interface buttons.
   */
  static private Image svgToImage(String xmlStr, int wide, int high) {
    PGraphicsJava2D pg = new PGraphicsJava2D();
    pg.setPrimary(false);
    pg.setSize(wide, high);
    pg.smooth();

    pg.beginDraw();

    try {
      XML xml = XML.parse(xmlStr);
      PShape shape = new PShapeJava2D(xml);
      pg.shape(shape, 0, 0, wide, high);

    } catch (Exception e) {
      e.printStackTrace();
    }

    pg.endDraw();
    return pg.image;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static public Shape createRoundRect(float x1, float y1, float x2, float y2,
                                      float tl, float tr, float br, float bl) {
    GeneralPath path = new GeneralPath();
//    vertex(x1+tl, y1);

    if (tr != 0) {
      path.moveTo(x2-tr, y1);
      path.quadTo(x2, y1, x2, y1+tr);
    } else {
      path.moveTo(x2, y1);
    }
    if (br != 0) {
      path.lineTo(x2, y2-br);
      path.quadTo(x2, y2, x2-br, y2);
    } else {
      path.lineTo(x2, y2);
    }
    if (bl != 0) {
      path.lineTo(x1+bl, y2);
      path.quadTo(x1, y2, x1, y2-bl);
    } else {
      path.lineTo(x1, y2);
    }
    if (tl != 0) {
      path.lineTo(x1, y1+tl);
      path.quadTo(x1, y1, x1+tl, y1);
    } else {
      path.lineTo(x1, y1);
    }
    path.closePath();
    return path;
  }


  /**
   * Registers key events for a Ctrl-W and ESC with an ActionListener
   * that will take care of disposing the window.
   */
  static public void registerWindowCloseKeys(JRootPane root,
                                             ActionListener disposer) {
    KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    root.registerKeyboardAction(disposer, stroke,
                                JComponent.WHEN_IN_FOCUSED_WINDOW);

    stroke = KeyStroke.getKeyStroke('W', SHORTCUT_KEY_MASK);
    root.registerKeyboardAction(disposer, stroke,
                                JComponent.WHEN_IN_FOCUSED_WINDOW);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static public void beep() {
    awtToolkit.beep();
  }


  static public Clipboard getSystemClipboard() {
    return awtToolkit.getSystemClipboard();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /*
  static final boolean ISSUE_342 = false;

  //static private float dpiScale(Component comp) {
  static private float dpiScale() {
    if (Platform.isWindows()) {
      return awtToolkit.getScreenResolution() / 96f;
//      return comp.getToolkit().getScreenResolution() / 96f;
    }
    return Toolkit.isRetina() ? 2 : 1;
  }


  static private int dpiScale(int what) {
    return (int) Math.floor(what * dpiScale());
  }
  */


//  /**
//   * Create an Image to be used as an offscreen drawing context,
//   * automatically doubling the size if running on a retina display.
//   */
//  static public Image offscreenGraphics(Component comp, int width, int height) {
////    if (ISSUE_342) {
////      return comp.createImage(dpiScale(width), dpiScale(height));
////    }
//    int m = Toolkit.isRetina() ? 2 : 1;
//    return comp.createImage(m * width, m * height);
//  }


  static public Graphics2D prepareGraphics(Image image) {
    return prepareGraphics(image.getGraphics(), true);
  }


  static public Graphics2D prepareGraphics(Graphics g) {
    return prepareGraphics(g, false);
  }


  /**
   * Handles scaling for high-res displays, also sets text anti-aliasing
   * options to be far less ugly than the defaults.
   * Moved to a utility function because it's used in several classes.
   * @return a Graphics2D object, as a bit o sugar
   */
  static public Graphics2D prepareGraphics(Graphics g, boolean scale) {
    Graphics2D g2 = (Graphics2D) g;

    if (scale && Toolkit.isRetina()) {
      // scale everything 2x, will be scaled down when drawn to the screen
      g2.scale(2, 2);
    }

//    float s = dpiScale();
//    if (s != 1) {
//      if (ISSUE_342) {
//        System.out.println("Toolkit.prepareGraphics() with dpi scale " + s);
//      }
//      g2.scale(s, s);
//    }

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    if (Toolkit.isRetina()) {
      // Looks great on retina, not so great (with our font) on 1x
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                          RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
    }
    if (scale) {
      zoomStroke(g2);
    }
    return g2;
  }


//  /**
//   * Prepare and offscreen image that's sized for this Component, 1x or 2x
//   * depending on whether this is a retina display or not.
//   * @param comp
//   * @param image
//   * @return
//   */
//  static public Image prepareOffscreen(Component comp, Image image) {
//    Dimension size = comp.getSize();
//    Image offscreen = image;
//    if (image == null ||
//        image.getWidth(null) != size.width ||
//        image.getHeight(null) != size.height) {
//      if (Toolkit.highResDisplay()) {
//        offscreen = comp.createImage(size.width*2, size.height*2);
//      } else {
//        offscreen = comp.createImage(size.width, size.height);
//      }
//    }
//    return offscreen;
//  }


//  static final Color CLEAR_COLOR = new Color(0, true);
//
//  static public void clearGraphics(Graphics g, int width, int height) {
//    g.setColor(CLEAR_COLOR);
//    g.fillRect(0, 0, width, height);
//  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static float zoom = 0;


  /*
  // http://stackoverflow.com/a/35029265
  static public void zoomSwingFonts() {
    Set<Object> keySet = UIManager.getLookAndFeelDefaults().keySet();
    Object[] keys = keySet.toArray(new Object[keySet.size()]);

    for (Object key : keys) {
      if (key != null && key.toString().toLowerCase().contains("font")) {
        System.out.println(key);
        Font font = UIManager.getDefaults().getFont(key);
        if (font != null) {
          font = font.deriveFont(font.getSize() * zoom);
          UIManager.put(key, font);
        }
      }
    }
  }
  */


  static final StringList zoomOptions =
    new StringList("100%", "150%", "200%", "300%");


  /**
   * Calculate the desired size in pixels of an element using preferences or
   * system zoom if preferences set to auto.
   *
   * @param pixels The size in pixels to scale.
   * @return The scaled size.
   */
  static public int zoom(int pixels) {
    if (zoom == 0) {
      zoom = parseZoom();
    }
    // Deal with 125% scaling badness
    // https://github.com/processing/processing/issues/4902
    return (int) Math.ceil(zoom * pixels);
  }


  static public Dimension zoom(int w, int h) {
    return new Dimension(zoom(w), zoom(h));
  }


  /*
  static public final int BORDER = Platform.isMacOS() ? 20 : 13;


  static public void setBorder(JComponent comp) {
    setBorder(comp, BORDER, BORDER, BORDER, BORDER);
  }


  static public void setBorder(JComponent comp,
                               int top, int left, int bottom, int right) {
    comp.setBorder(new EmptyBorder(Toolkit.zoom(top), Toolkit.zoom(left),
                                   Toolkit.zoom(bottom), Toolkit.zoom(right)));
  }
  */


  static private float parseZoom() {
    if (Preferences.getBoolean("editor.zoom.auto")) {
      float newZoom = Platform.getSystemZoom();
      String percentSel = ((int) (newZoom*100)) + "%";
      Preferences.set("editor.zoom", percentSel);
      return newZoom;

    } else {
      String zoomSel = Preferences.get("editor.zoom");
      if (zoomOptions.hasValue(zoomSel)) {
        // shave off the % symbol at the end
        zoomSel = zoomSel.substring(0, zoomSel.length() - 1);
        return PApplet.parseInt(zoomSel, 100) / 100f;

      } else {
        Preferences.set("editor.zoom", "100%");
        return 1;
      }
    }
  }


  static BasicStroke zoomStroke;

  static private void zoomStroke(Graphics2D g2) {
    if (zoom != 1) {
      if (zoomStroke == null || zoomStroke.getLineWidth() != zoom) {
        zoomStroke = new BasicStroke(zoom);
      }
      g2.setStroke(zoomStroke);
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  // Changed to retinaProp instead of highResProp because only Mac
  // "retina" displays use this mechanism for high-resolution scaling.
  static Boolean retinaProp;


  static public boolean highResImages() {
    return isRetina() || (Platform.getSystemZoom() > 1);
  }


  static public int highResMultiplier() {
    return highResImages() ? 2 : 1;
  }


  static public int highResMultiply(int amount) {
    return highResImages() ? 2*amount : amount;
  }


  static public boolean isRetina() {
    if (retinaProp == null) {
      retinaProp = checkRetina();
    }
    return retinaProp;
  }


  // This should probably be reset each time there's a display change.
  // A 5-minute search didn't turn up any such event in the Java API.
  // Also, should we use the Toolkit associated with the editor window?
  static private boolean checkRetina() {
    AffineTransform tx = GraphicsEnvironment
      .getLocalGraphicsEnvironment()
      .getDefaultScreenDevice()
      .getDefaultConfiguration()
      .getDefaultTransform();

    return Math.round(tx.getScaleX()) == 2;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  // Gets the plain (not bold, not italic) version of each
  static private List<Font> getMonoFontList() {
    GraphicsEnvironment ge =
      GraphicsEnvironment.getLocalGraphicsEnvironment();
    Font[] fonts = ge.getAllFonts();
    List<Font> outgoing = new ArrayList<>();
    // Using AffineTransform.getScaleInstance(100, 100) doesn't change sizes
    FontRenderContext frc =
      new FontRenderContext(new AffineTransform(),
                            Preferences.getBoolean("editor.antialias"),
                            true);  // use fractional metrics
    for (Font font : fonts) {
      if (font.getStyle() == Font.PLAIN &&
          font.canDisplay('i') && font.canDisplay('M') &&
          font.canDisplay(' ') && font.canDisplay('.')) {

        // The old method just returns 1 or 0, and using deriveFont(size)
        // is overkill. It also causes deprecation warnings
//        @SuppressWarnings("deprecation")
//        FontMetrics fm = awtToolkit.getFontMetrics(font);
        //FontMetrics fm = awtToolkit.getFontMetrics(font.deriveFont(24));
//        System.out.println(fm.charWidth('i') + " " + fm.charWidth('M'));
//        if (fm.charWidth('i') == fm.charWidth('M') &&
//            fm.charWidth('M') == fm.charWidth(' ') &&
//            fm.charWidth(' ') == fm.charWidth('.')) {
        double w = font.getStringBounds(" ", frc).getWidth();
        if (w == font.getStringBounds("i", frc).getWidth() &&
            w == font.getStringBounds("M", frc).getWidth() &&
            w == font.getStringBounds(".", frc).getWidth()) {

//          //PApplet.printArray(font.getAvailableAttributes());
//          Map<TextAttribute,?> attr = font.getAttributes();
//          System.out.println(font.getFamily() + " > " + font.getName());
//          System.out.println(font.getAttributes());
//          System.out.println("  " + attr.get(TextAttribute.WEIGHT));
//          System.out.println("  " + attr.get(TextAttribute.POSTURE));

          outgoing.add(font);
//          System.out.println("  good " + w);
        }
      }
    }
    return outgoing;
  }


  static public String[] getMonoFontFamilies() {
    StringList families = new StringList();
    for (Font font : getMonoFontList()) {
      families.appendUnique(font.getFamily());
    }
    families.sort();
    return families.toArray();
  }


  static Font monoFont;
  static Font monoBoldFont;
  static Font sansFont;
  static Font sansBoldFont;


  /** Get the name of the default (built-in) monospaced font. */
  static public String getMonoFontName() {
    return getMonoFont(12, Font.PLAIN).getName();
  }


  /**
   * Get the Font object of the default (built-in) monospaced font.
   * As of 4.x, this is Source Code Pro and ships in lib/fonts because
   * it looks like JDK 11+ <a href="https://www.oracle.com/java/technologies/javase/11-relnote-issues.html#JDK-8191522">
   * no longer supports</a> a "fonts" subfolder (or at least,
   * its cross-platform implementation is inconsistent).
   */
  static public Font getMonoFont(int size, int style) {
    // Prior to 4.0 beta 9, we had a manual override for
    // individual languages to use SansSerif instead.
    // In beta 9, that was moved to the language translation file.
    // https://github.com/processing/processing/issues/2886
    // https://github.com/processing/processing/issues/4944
    String fontFamilyMono = Language.text("font.family.mono");

    if (monoFont == null || monoBoldFont == null) {
      try {
        if ("Source Code Pro".equals(fontFamilyMono)) {
          monoFont = initFont("SourceCodePro-Regular.ttf", size);
          monoBoldFont = initFont("SourceCodePro-Bold.ttf", size);
        }
      } catch (Exception e) {
        Messages.err("Could not load mono font", e);
      }
    }

    // If not using Source Code Pro above, or an Exception was thrown
    if (monoFont == null || monoBoldFont == null) {
      monoFont = new Font(fontFamilyMono, Font.PLAIN, size);
      monoBoldFont = new Font(fontFamilyMono, Font.BOLD, size);
    }

    if (style == Font.BOLD) {
      if (size == monoBoldFont.getSize()) {
        return monoBoldFont;
      } else {
        return monoBoldFont.deriveFont((float) size);
      }
    } else {
      if (size == monoFont.getSize()) {
        return monoFont;
      } else {
        return monoFont.deriveFont((float) size);
      }
    }
  }


  static public String getSansFontName() {
    return getSansFont(12, Font.PLAIN).getName();
  }


  static public Font getSansFont(int size, int style) {
    // Prior to 4.0 beta 9, we had a manual override for
    // individual languages to use SansSerif instead.
    // In beta 9, that was moved to the language translation file.
    // https://github.com/processing/processing/issues/2886
    // https://github.com/processing/processing/issues/4944
    String fontFamilySans = Language.text("font.family.sans");

    if (sansFont == null || sansBoldFont == null) {
      try {
        if ("Processing Sans".equals(fontFamilySans)) {
          sansFont = initFont("ProcessingSans-Regular.ttf", size);
          sansBoldFont = initFont("ProcessingSans-Bold.ttf", size);
        }
      } catch (Exception e) {
        Messages.err("Could not load sans font", e);
      }
    }

    // If not using "Processing Sans" above, or an Exception was thrown
    if (sansFont == null || sansBoldFont == null) {
      sansFont = new Font(fontFamilySans, Font.PLAIN, size);
      sansBoldFont = new Font(fontFamilySans, Font.BOLD, size);
    }

    if (style == Font.BOLD) {
      if (size == sansBoldFont.getSize() || size == 0) {
        return sansBoldFont;
      } else {
        return sansBoldFont.deriveFont((float) size);
      }
    } else {
      if (size == sansFont.getSize() || size == 0) {
        return sansFont;
      } else {
        return sansFont.deriveFont((float) size);
      }
    }
  }


  /**
   * Load a built-in font from the Processing lib/fonts folder and register
   * it with the GraphicsEnvironment so that it's broadly available.
   * (i.e. shows up in getFontList() works, so it appears in the list of fonts
   * in the Preferences window, and can be used by HTMLEditorKit for WebFrame.)
   */
  static private Font initFont(String filename, int size) throws IOException, FontFormatException {
    File fontFile = Platform.getContentFile("lib/fonts/" + filename);

    if (fontFile == null || !fontFile.exists()) {
      String msg = "Could not find required fonts. ";
      // This gets the JAVA_HOME for the *local* copy of the JRE installed with
      // Processing. If it's not using the local JRE, it may be because of this
      // launch4j bug: https://github.com/processing/processing/issues/3543
      if (Util.containsNonASCII(Platform.getJavaHome().getAbsolutePath())) {
        msg += "Trying moving Processing\n" +
          "to a location with only ASCII characters in the path.";
      } else {
        msg += "Please reinstall Processing.";
      }
      Messages.showError("Font Sadness", msg, null);
    }

    BufferedInputStream input = new BufferedInputStream(new FileInputStream(fontFile));
    Font font = Font.createFont(Font.TRUETYPE_FONT, input);
    input.close();

    // Register the font to be available for other function calls
    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);

    return font.deriveFont((float) size);
  }


  /**
   * Synthesized replacement for FontMetrics.getAscent(), which is dreadfully
   * inaccurate and inconsistent across platforms.
   */
  static public double getAscent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g;
    FontRenderContext frc = g2.getFontRenderContext();
    //return new TextLayout("H", font, frc).getBounds().getHeight();
    return new TextLayout("H", g.getFont(), frc).getBounds().getHeight();
  }


  static public String formatMessage(String message) {
    String monoName = "Monospaced";
    try {
      monoName = Toolkit.getMonoFontName();
    } catch (Exception ignored) { }

    // Necessary to replace \n with <br/> (even if pre) otherwise Java
    // treats it as a closed tag and reverts to plain formatting.
    return "<html> " +
      "<head> <style type=\"text/css\">" +
      // if smaller than 12 pt, Source Code Sans doesn't get hinted
      // (not clear if that's a font or Java issue) [fry 220803]
      "tt { font: 12pt \"" + monoName + "\"; color: #888; }" +
      "</style> </head>" +
      message.replaceAll("\n", "<br/>");
  }


  static public String formatMessage(String primary, String secondary) {
    // Pane formatting originally adapted from the Quaqua guide
    // http://www.randelshofer.ch/quaqua/guide/joptionpane.html

    // This code originally disabled unless Java 1.5 is in use on OS X
    // because of a Java bug that prevents the initial value of the
    // dialog from being set properly (at least on my MacBook Pro).
    // The bug causes the "Don't Save" option to be the highlighted,
    // blinking, default. This sucks. But I'll tell you what doesn't
    // suck--workarounds for the Mac and Apple's snobby attitude about it!
    // I think it's nifty that they treat their developers like dirt.

//    String monoName = "Monospaced";
//    try {
//      monoName = Toolkit.getMonoFontName();
//    } catch (Exception ignored) { }

    // Necessary to replace \n with <br/> (even if pre) otherwise Java
    // treats it as a closed tag and reverts to plain formatting.
    return ("<html> " +
      "<head> <style type=\"text/css\">"+
      //"b { font: 13pt \"Lucida Grande\" }"+
      //"b { font: 13pt \"Processing Sans\" }"+
      //"p { font: 11pt \"Lucida Grande\"; margin-top: 8px }"+
      //"p { font: 11pt \"Processing Sans\"; margin-top: 8px }"+
      // sometimes with "width: 300px" but that might also be problematic
      // <tt> never used with the two tier dialog
      //"tt { font: 11pt \"" + monoName + "\"; }" +  // mono not used here
      "</style> </head>" +
      // Extra &nbsp; because the right-hand side of the text is cutting off.
      "<b>" + primary + "</b>&nbsp;" +
      "<p>" + secondary + "</p>").replaceAll("\n", "<br/>");
  }


  static public HTMLEditorKit createHtmlEditorKit() {
    return new HTMLEditorKit() {
      private StyleSheet style;

      @Override
      public StyleSheet getStyleSheet() {
        return style == null ? super.getStyleSheet() : style;
      }

      @Override
      public void setStyleSheet(StyleSheet s) {
        this.style = s;
      }

      public StyleSheet getDefaultStyleSheet() {
        return super.getStyleSheet();
      }

      public void setDefaultStyleSheet(StyleSheet s) {
        super.setStyleSheet(s);
      }
    };
  }
}
