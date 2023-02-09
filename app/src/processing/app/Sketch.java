/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-22 The Processing Foundation
  Copyright (c) 2004-11 Ben Fry and Casey Reas
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

package processing.app;

import processing.app.ui.Editor;
import processing.app.ui.Recent;
import processing.app.ui.Toolkit;
import processing.core.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;
import javax.swing.border.EmptyBorder;


/**
 * Stores information about files in the current sketch.
 */
public class Sketch {
  private final Editor editor;
  private final Mode mode;

  /** main pde file for this sketch. */
  private File mainFile;

  /**
   * Name of the sketch, which is the name of the folder since 4.0 beta 6.
   * Prior, it was the "pretty" name of the first tab (they were synonymous).
   */
  private String name;

  /** true if any of the files have been modified. */
  private boolean modified;

  /** folder that contains this sketch */
  private File folder;

  /** data folder location for this sketch (may not exist yet) */
  private File dataFolder;

  /** code folder location for this sketch (may not exist yet) */
  private File codeFolder;

  private SketchCode current;
  private int currentIndex;

  /**
   * Number of SketchCode objects (tabs) in the current sketch. Note
   * that this will be the same as code.length, because the getCode()
   * method returns just the code[] array, rather than a copy of it,
   * or an array that's been resized to just the relevant files.
   * (<a href="https://download.processing.org/bugzilla/940.html">Bugzilla 940</a>)
   */
  private int codeCount;
  private SketchCode[] code;

  /** Moved out of Editor and into here for cleaner access. */
  private boolean untitled;

  /** true if we've posted a "sketch disappeared" warning */
  private boolean disappearedWarning;


  /**
   * Used by the command-line version to create a sketch object.
   * @param path location of the main .pde file
   * @param mode what flavor of sketch we're dealing with.
   */
  public Sketch(String path, Mode mode) {
    this.editor = null;
    this.mode = mode;
    load(path);
  }


  /**
   * path is location of the main .pde file, because this is also
   * simplest to use when opening the file from the finder/explorer.
   */
  public Sketch(String path, Editor editor) throws IOException {
    this.editor = editor;
    this.mode = editor.getMode();
    load(path);
  }


  protected void load(String path) {
    mainFile = new File(path);
    folder = mainFile.getParentFile();
    /*
    // get the name of the sketch by chopping .pde or .java
    // off of the main file name
    String mainFilename = primaryFile.getName();
    int suffixLength = mode.getDefaultExtension().length() + 1;
    name = mainFilename.substring(0, mainFilename.length() - suffixLength);
    */
    // starting in 4.0 beta 6, use the folder name instead of the main tab
    name = folder.getName();
    disappearedWarning = false;
    load();
  }


  /**
   * Build the list of files.
   * <P>
   * Generally this is only done once, rather than
   * each time a change is made, because otherwise it gets to be
   * a nightmare to keep track of what files went where, because
   * not all the data will be saved to disk.
   * <P>
   * This also gets called when the main sketch file is renamed,
   * because the sketch has to be reloaded from a different folder.
   * <P>
   * Another exception is when an external editor is in use,
   * in which case the load happens each time "run" is hit.
   */
  protected void load() {
    codeFolder = new File(folder, "code");
    dataFolder = new File(folder, "data");

    List<String> filenames = new ArrayList<>();
    List<String> extensions = new ArrayList<>();

    getSketchCodeFiles(filenames, extensions);

    codeCount = filenames.size();
    code = new SketchCode[codeCount];

    for (int i = 0; i < codeCount; i++) {
      String filename = filenames.get(i);
      String extension = extensions.get(i);
      code[i] = new SketchCode(new File(folder, filename), extension);
    }

    // move the main class to the first tab
    // start at 1, if it's at zero, don't bother
    for (int i = 1; i < codeCount; i++) {
      //if (code[i].file.getName().equals(mainFilename)) {
      if (code[i].getFile().equals(mainFile)) {
        SketchCode temp = code[0];
        code[0] = code[i];
        code[i] = temp;
        break;
      }
    }

    // sort the entries at the top
    sortCode();

    // set the main file to be the current tab
    if (editor != null) {
      setCurrentCode(0);
    }
  }


  public void getSketchCodeFiles(List<String> outFilenames,
                                 List<String> outExtensions) {
    // get list of files in the sketch folder
    String[] list = folder.list();
    if (list != null) {
      for (String filename : list) {
        // Ignoring the dot prefix files is especially important to avoid files
        // with the ._ prefix on Mac OS X. (You'll see this with Mac files on
        // non-HFS drives, i.e. a thumb drive formatted FAT32.)
        if (filename.startsWith(".")) continue;

        // Don't let some wacko name a directory blah.pde or bling.java.
        if (new File(folder, filename).isDirectory()) continue;

        // figure out the name without any extension
        String base = filename;
        // now strip off the .pde and .java extensions
        for (String extension : mode.getExtensions()) {
          if (base.toLowerCase().endsWith("." + extension)) {
            base = base.substring(0, base.length() - (extension.length() + 1));

            // Don't allow people to use files with invalid names, since on load,
            // it would be otherwise possible to sneak in nasty filenames. [0116]
            if (isSanitaryName(base)) {
              if (outFilenames != null) outFilenames.add(filename);
              if (outExtensions != null) outExtensions.add(extension);
            }
          }
        }
      }
    }
  }


  /**
   * Reload the current sketch. Used to update the text area when
   * an external editor is in use.
   */
  public void reload() {
    // set current to null so that the tab gets updated
    // https://download.processing.org/bugzilla/515.html
    current = null;
    // nuke previous files and settings
    load();
  }


  /**
   * Load a tab that the user added to the sketch or modified with an external
   * editor.
   */
  public void loadNewTab(String filename, String ext, boolean newAddition) {
    if (newAddition) {
      insertCode(new SketchCode(new File(folder, filename), ext));
    } else {
      replaceCode(new SketchCode(new File(folder, filename), ext));
    }
    sortCode();
  }


  protected void replaceCode(SketchCode newCode) {
    for (int i = 0; i < codeCount; i++) {
      if (code[i].getFileName().equals(newCode.getFileName())) {
        code[i] = newCode;
        break;
      }
    }
  }


  protected void insertCode(SketchCode newCode) {
    // make sure the user didn't hide the sketch folder
    ensureExistence();

    // add file to the code/codeCount list, resort the list
    //if (codeCount == code.length) {
    code = (SketchCode[]) PApplet.append(code, newCode);
    codeCount++;
    //}
    //code[codeCount++] = newCode;
  }


  protected void sortCode() {
    // cheap-ass sort of the rest of the files
    // it's a dumb, slow sort, but there shouldn't be more than ~5 files
    for (int i = 1; i < codeCount; i++) {
      int who = i;
      for (int j = i + 1; j < codeCount; j++) {
        if (code[j].getFileName().compareTo(code[who].getFileName()) < 0) {
          who = j;  // this guy is earlier in the alphabet
        }
      }
      if (who != i) {  // swap with someone if changes made
        SketchCode temp = code[who];
        code[who] = code[i];
        code[i] = temp;

        // We also need to update the current tab
        if (currentIndex == i) {
          currentIndex = who;
        } else if (currentIndex == who) {
          currentIndex = i;
        }
      }
    }
  }


  boolean renamingCode;

  /**
   * Handler for the New Code menu option.
   */
  public void handleNewCode() {
    // make sure the user didn't hide the sketch folder
    ensureExistence();

    // if read-only, give an error
    if (isReadOnly()) {
      // if the files are read-only, need to first do a "save as".
      Messages.showMessage(Language.text("new.messages.is_read_only"),
                           Language.text("new.messages.is_read_only.description"));
      return;
    }

    renamingCode = false;
    // editor.status.edit("Name for new file:", "");
    promptForTabName(Language.text("editor.tab.rename.description")+":", "");
  }


  /**
   * Handler for the "Rename Code" menu option.
   */
  public void handleRenameCode() {
    // make sure the user didn't hide the sketch folder
    ensureExistence();

    if (currentIndex == 0 && isUntitled()) {
      Messages.showMessage(Language.text("rename.messages.is_untitled"),
                           Language.text("rename.messages.is_untitled.description"));
      return;
    }

    if (isModified()) {
      Messages.showMessage(Language.text("menu.file.save"),
                           Language.text("rename.messages.is_modified"));
      return;
    }

    // if read-only, give an error
    if (isReadOnly()) {
      // if the files are read-only, need to first do a "save as".
      Messages.showMessage(Language.text("rename.messages.is_read_only"),
                           Language.text("rename.messages.is_read_only.description"));
      return;
    }

    // ask for new name of file (internal to window)
    // TODO maybe just pop up a text area?
    renamingCode = true;
    String prompt = (currentIndex == 0 && Preferences.getBoolean("editor.sync_folder_and_filename")) ?
      Language.text("editor.sketch.rename.description") :
      Language.text("editor.tab.rename.description");
    String oldName = (current.isExtension(mode.getDefaultExtension())) ?
      current.getPrettyName() : current.getFileName();
    promptForTabName(prompt + ":", oldName);
  }


  /**
   * Displays a dialog for renaming or creating a new tab
   */
  protected void promptForTabName(String prompt, String oldName) {
    final JTextField field = new JTextField(oldName);

    field.addKeyListener(new KeyAdapter() {
      // Forget ESC, the JDialog should handle it.
      // Use keyTyped to catch when the feller is actually added to the text
      // field. With keyTyped, as opposed to keyPressed, the keyCode will be
      // zero, even if it's enter or backspace or whatever, so the key char
      // should be used instead. Grr.
      public void keyTyped(KeyEvent event) {
        //System.out.println("got event " + event);
        char ch = event.getKeyChar();

        //noinspection StatementWithEmptyBody
        if ((ch == '_') || (ch == '.') || // allow.pde and .java
            (('A' <= ch) && (ch <= 'Z')) || (('a' <= ch) && (ch <= 'z'))) {
          // These events are allowed straight through.

        } else if (ch == ' ') {
          String t = field.getText();
          int start = field.getSelectionStart();
          int end = field.getSelectionEnd();
          field.setText(t.substring(0, start) + "_" + t.substring(end));
          field.setCaretPosition(start + 1);
          event.consume();

        } else if ((ch >= '0') && (ch <= '9')) {
          // getCaretPosition == 0 means that it's the first char
          // and the field is empty.
          // getSelectionStart means that it *will be* the first
          // char, because the selection is about to be replaced
          // with whatever is typed.
          if (field.getCaretPosition() == 0 ||
              field.getSelectionStart() == 0) {
            // number not allowed as first digit
            event.consume();
          }
        } else if (ch == KeyEvent.VK_ENTER) {
          // Slightly ugly hack that ensures OK button of the dialog consumes
          // the Enter key event. Since the text field is the default component
          // in the dialog, OK doesn't consume Enter key event, by default.
          Container parent = field.getParent();
          while (!(parent instanceof JOptionPane pane)) {
            parent = parent.getParent();
          }
          final JPanel pnlBottom = (JPanel)
            pane.getComponent(pane.getComponentCount() - 1);
          for (int i = 0; i < pnlBottom.getComponents().length; i++) {
            Component component = pnlBottom.getComponents()[i];
            if (component instanceof final JButton okButton) {
              if (okButton.getText().equalsIgnoreCase("OK")) {
                ActionListener[] actionListeners =
                  okButton.getActionListeners();
                if (actionListeners.length > 0) {
                  actionListeners[0].actionPerformed(null);
                  event.consume();
                }
              }
            }
          }
        } else {
          event.consume();
        }
      }
    });

    int userReply = JOptionPane.showOptionDialog(editor, new Object[] {
                                                 prompt, field },
                                                 Language.text("editor.tab.new"),
                                                 JOptionPane.OK_CANCEL_OPTION,
                                                 JOptionPane.PLAIN_MESSAGE,
                                                 null, new Object[] {
                                                 Language.getPrompt("ok"),
                                                 Language.getPrompt("cancel") },
                                                 field);

    if (userReply == JOptionPane.OK_OPTION) {
      nameCode(field.getText());
    }
  }


  /**
   * This is called upon return from entering a new file name.
   * (that is, from either newCode or renameCode after the prompt)
   */
  protected void nameCode(String newName) {
    newName = newName.trim();
    if (newName.length() == 0) {
      return;
    }

    // Make sure the sketch folder is still available and exists.
    ensureExistence();

    // Add the extension here, this simplifies some logic below.
    if (newName.indexOf('.') == -1) {
      newName += "." + (renamingCode ? mode.getDefaultExtension() : mode.getModuleExtension());
    }

    // If renaming to the same thing as before, just ignore.
    // Also ignoring case here, because I don't want to write/maintain/debug
    // a bunch of platform-specific quirks: macOS is case-insensitive but
    // preserving, Windows is insensitive, *nix is sensitive and preserving,
    // and someday we're all gonna die, and I'm comfortable that writing the
    // necessary code is not essential to the story of my life on Earth.
    if (renamingCode) {
      if (newName.equalsIgnoreCase(current.getFileName())) {
        // exit quietly for the 'rename' case.
        // if it's a 'new' then an error will occur down below
        return;
      }
    }

    if (newName.startsWith(".")) {
      Messages.showWarning(Language.text("name.messages.problem_renaming"),
                           Language.text("name.messages.starts_with_dot.description"));
      return;
    }

    int dot = newName.lastIndexOf('.');
    String newExtension = newName.substring(dot+1).toLowerCase();
    if (!mode.validExtension(newExtension)) {
      Messages.showWarning(Language.text("name.messages.problem_renaming"),
                           Language.interpolate("name.messages.invalid_extension.description",
                           newExtension));
      return;
    }

    // Don't let the user create the main tab as a .java file instead of .pde
    if (!mode.isDefaultExtension(newExtension)) {
      if (renamingCode) {  // If creating a new tab, don't show this error
        if (current == code[0]) {  // If this is the main tab, disallow
          Messages.showWarning(Language.text("name.messages.problem_renaming"),
                               Language.interpolate("name.messages.main_java_extension.description",
                               newExtension));
          return;
        }
      }
    }

    // Dots are allowed for the .pde and .java, but not in the name.
    // Make sure the user didn't name the file poo.time.pde or anything
    // else with a dot inside it (nothing against poo time).
    String shortName = newName.substring(0, dot);
    String sanitaryName = Sketch.sanitizeName(shortName);
    if (!shortName.equals(sanitaryName)) {
      newName = sanitaryName + "." + newExtension;
    }

    // If changing the extension of a file from .pde to .java, then it's ok.
    // https://github.com/processing/processing/issues/814
    // (That regression was introduced years earlier by the bug report below.)
    if (!(renamingCode && sanitaryName.equals(current.getPrettyName()))) {
      // Make sure no .pde *and* no .java files with the same name already exist
      // (other than the one we are currently attempting to rename)
      // http://processing.org/bugs/bugzilla/543.html
      for (SketchCode c : code) {
        if (c != current && sanitaryName.equalsIgnoreCase(c.getPrettyName())) {
          Messages.showMessage(Language.text("name.messages.new_sketch_exists"),
                               Language.interpolate("name.messages.new_sketch_exists.description",
                               c.getFileName(), folder.getAbsolutePath()));
          return;
        }
      }
    }

    File newFile = new File(folder, newName);

    if (renamingCode) {
      if (currentIndex == 0 &&
          Preferences.getBoolean("editor.sync_folder_and_filename")) {
        if (!renameSketch(newName, newExtension)) return;

      } else {  // else if something besides code[0], or ok to decouple name
        if (!current.renameTo(newFile, newExtension)) {
          Messages.showWarning(Language.text("name.messages.error"),
                               Language.interpolate("name.messages.no_rename_file.description",
                               current.getFileName(), newFile.getName()));
          return;
        }
        if (currentIndex == 0) {
          // If the main tab was renamed, check sketch.properties
          mainFile = newFile;  //code[0].getFile();
          updateNameProperties();
        }
      }

    } else {  // not renaming, creating a new file
      try {
        if (!newFile.createNewFile()) {
          // Already checking for IOException, so make our own.
          throw new IOException("createNewFile() returned false");
        }
      } catch (IOException e) {
        Messages.showWarning(Language.text("name.messages.error"),
                             Language.interpolate("name.messages.no_create_file.description",
                             newFile, folder.getAbsolutePath()), e);
        return;
      }
      SketchCode newCode = new SketchCode(newFile, newExtension);
      insertCode(newCode);
    }

    // sort the entries
    sortCode();

    // set the new guy as current
    setCurrentCode(newName);

    // update the tabs
    editor.rebuildHeader();
  }


  /**
   * Pre-4.0b6 style rename where the sketch name must be identical
   * to the name of the first (main) tab with the extension removed.
   */
  protected boolean renameSketch(String newName, String newExtension) {
    // get the new folder name/location
    String folderName = newName.substring(0, newName.indexOf('.'));
    File newFolder = new File(folder.getParentFile(), folderName);
    if (newFolder.exists()) {
      Messages.showWarning(Language.text("name.messages.new_folder_exists"),
      Language.interpolate("name.messages.new_folder_exists.description", newName));
      return false;
    }

    // renaming the containing sketch folder
    boolean success = folder.renameTo(newFolder);
    if (!success) {
      Messages.showWarning(Language.text("name.messages.error"),
      Language.text("name.messages.no_rename_folder.description"));
      return false;
    }
    // let this guy know where he's living (at least for a split second)
    current.setFolder(newFolder);
    // folder will be set to newFolder by updateInternal()

    // unfortunately this can't be a "save as" because that
    // only copies the sketch files and the data folder
    // however this *will* first save the sketch, then rename

    // This isn't changing folders, just changes the name
    File newFile = new File(newFolder, newName);
    if (!current.renameTo(newFile, newExtension)) {
      Messages.showWarning(Language.text("name.messages.error"),
      Language.interpolate("name.messages.no_rename_file.description",
      current.getFileName(), newFile.getName()));
      return false;
    }

    // Tell each code file the good news about their new home.
    // current.renameTo() above already took care of the main tab.
    for (int i = 1; i < codeCount; i++) {
      code[i].setFolder(newFolder);
    }
    // Save the path in case we need to remove it from the Recent menu
    String oldPath = getMainPath();

    // Update internal state to reflect the new location
    updateInternal(newFolder);

    if (renamingCode) {
      // Update the Recent menu if a Rename event (but not Save As)
      // https://github.com/processing/processing/issues/5902
      Recent.rename(editor, oldPath);
    }

    return true;
  }


  /**
   * Remove a piece of code from the sketch and from the disk.
   */
  public void handleDeleteCode() {
    // make sure the user didn't hide the sketch folder
    ensureExistence();

    // if read-only, give an error
    if (isReadOnly()) {
      // if the files are read-only, need to first do a "save as".
      Messages.showMessage(Language.text("delete.messages.is_read_only"),
                           Language.text("delete.messages.is_read_only.description"));
      return;
    }

    // don't allow if untitled
    if (currentIndex == 0 && isUntitled()) {
      Messages.showMessage(Language.text("delete.messages.cannot_delete"),
                           Language.text("delete.messages.cannot_delete.description"));
      return;
    }

    // confirm deletion with user, yes/no
    Object[] options = { Language.text("prompt.ok"), Language.text("prompt.cancel") };
    String prompt = (currentIndex == 0) ?
      Language.interpolate("warn.delete.sketch_folder", getName()) :
      Language.interpolate("warn.delete.sketch_file", current.getPrettyName());
    int result = JOptionPane.showOptionDialog(editor,
                                              prompt,
                                              Language.text("warn.delete"),
                                              JOptionPane.YES_NO_OPTION,
                                              JOptionPane.QUESTION_MESSAGE,
                                              null,
                                              options,
                                              options[0]);
    if (result == JOptionPane.YES_OPTION) {
      if (currentIndex == 0) {  // delete the entire sketch
        // need to unset all the modified flags, otherwise tries
        // to do a save on the handleNew()

        // Attempt to move to the trash (falls back to removeDir)
        try {
          Platform.deleteFile(folder);
        } catch (IOException e) {
          e.printStackTrace();
        }

        // make a new sketch and rebuild the sketch menu
        editor.getBase().rebuildSketchbook();
        editor.getBase().handleClose(editor, false);

      } else {  // delete a single tab
        if (!current.deleteFile()) {
          Messages.showMessage(Language.text("delete.messages.cannot_delete.file"),
                               Language.text("delete.messages.cannot_delete.file.description")+" \"" +
                               current.getFileName() + "\".");
          return;
        }

        // remove code from the list
        removeCode(current);

        // update the tabs
        editor.rebuildHeader();

        // just set current tab to the main tab
        setCurrentCode(0);

      }
    }
  }


  /**
   * Remove a SketchCode from the list of files without deleting its file.
   * @see #handleDeleteCode()
   */
  public void removeCode(SketchCode which) {
    // remove it from the internal list of files
    // resort internal list of files
    for (int i = 0; i < codeCount; i++) {
      if (code[i] == which) {
        for (int j = i; j < codeCount-1; j++) {
          code[j] = code[j+1];
        }
        codeCount--;
        code = (SketchCode[]) PApplet.shorten(code);
        return;
      }
    }

    if (Base.DEBUG) {
      // This can happen with the change detector, but need not be reported.
      System.err.println("removeCode: could not find " + which.getFileName());
    }
  }


  /**
   * Move to the previous tab.
   */
  public void handlePrevCode() {
    int prev = currentIndex - 1;
    if (prev < 0) prev = codeCount-1;
    setCurrentCode(prev);
  }


  /**
   * Move to the next tab.
   */
  public void handleNextCode() {
    setCurrentCode((currentIndex + 1) % codeCount);
  }


  /**
   * Sets the modified value for the code in the front-most tab.
   */
  public void setModified(boolean state) {
    if (current.isModified() != state) {
      current.setModified(state);
      calcModified();
    }
  }


  protected void calcModified() {
    modified = false;
    for (int i = 0; i < codeCount; i++) {
      if (code[i].isModified()) {
        modified = true;
        break;
      }
    }
    editor.repaintHeader();

    if (Platform.isMacOS()) {
      // http://developer.apple.com/qa/qa2001/qa1146.html
      Object modifiedParam = modified ? Boolean.TRUE : Boolean.FALSE;
      // https://developer.apple.com/library/mac/technotes/tn2007/tn2196.html#WINDOW_DOCUMENTMODIFIED
      editor.getRootPane().putClientProperty("Window.documentModified", modifiedParam);
    }
  }


  public boolean isModified() {
    return modified;
  }


  /**
   * Ensure that all SketchCodes are up-to-date, so that sc.save() works.
   */
  public void updateSketchCodes() {
    current.setProgram(editor.getText());
  }


  /**
   * Save all code in the current sketch. This just forces the files to save
   * in place, so if it's an untitled (un-saved) sketch, saveAs() should be
   * called instead. (This is handled inside Editor.handleSave()).
   */
  public boolean save() throws IOException {
    // make sure the user didn't hide the sketch folder
    ensureExistence();

    // first get the contents of the editor text area
    updateSketchCodes();

    // don't do anything if not actually modified
    //if (!modified) return false;

    if (isReadOnly()) {
      // if the files are read-only, need to first do a "save as".
      Messages.showMessage(Language.text("save_file.messages.is_read_only"),
                           Language.text("save_file.messages.is_read_only.description"));
      // if the user cancels, give up on the save()
      if (!saveAs()) return false;
    }

    for (SketchCode sc : code) {
      if (sc.isModified()) {
        sc.save();
      }
    }
    calcModified();
    return true;
  }


  /**
   * Handles 'Save As' for a sketch.
   * <P>
   * This basically just duplicates the current sketch folder to
   * a new location, and then calls 'Save'. (needs to take the current
   * state of the open files and save them to the new folder,
   * but not save over the old versions for the old sketch...)
   * <P>
   * Also removes the previously-generated .class and .jar files,
   * because they can cause trouble.
   */
  public boolean saveAs() throws IOException {
    String newParentDir = null;
    String newSketchName = null;

    final String PROMPT = Language.text("save");

    // https://github.com/processing/processing4/issues/77
    boolean useNative = Preferences.getBoolean("chooser.files.native");
    if (useNative) {
      // get new name for folder
      FileDialog fd = new FileDialog(editor, PROMPT, FileDialog.SAVE);
      if (isReadOnly() || isUntitled()) {
        // default to the sketchbook folder
        fd.setDirectory(Preferences.getSketchbookPath());
      } else {
        // default to the parent folder of where this was
        fd.setDirectory(folder.getParent());
      }
      String oldFolderName = folder.getName();
      fd.setFile(oldFolderName);
      fd.setVisible(true);
      newParentDir = fd.getDirectory();
      newSketchName = fd.getFile();
    } else {
      JFileChooser fc = new JFileChooser();
      fc.setDialogTitle(PROMPT);
      if (isReadOnly() || isUntitled()) {
        // default to the sketchbook folder
        fc.setCurrentDirectory(new File(Preferences.getSketchbookPath()));
      } else {
        // default to the parent folder of where this was
        fc.setCurrentDirectory(folder.getParentFile());
      }
      // can't do this, will try to save into itself by default
      //fc.setSelectedFile(folder);
      int result = fc.showSaveDialog(editor);
      if (result == JFileChooser.APPROVE_OPTION) {
        File selection = fc.getSelectedFile();
        newParentDir = selection.getParent();
        newSketchName = selection.getName();
      }
    }

    // user canceled selection
    if (newSketchName == null) return false;

    boolean sync = Preferences.getBoolean("editor.sync_folder_and_filename");
    String newMainFileName = null;  // only set with !sync
    File newFolder;
    if (sync) {
      // before 4.0 beta 6
      //String sanitaryName = Sketch.checkName(newSketchName);
      String newMainName = sanitizeName(newSketchName);
      newFolder = new File(newParentDir, newMainName);
      if (!newMainName.equals(newSketchName) && newFolder.exists()) {
        Messages.showMessage(Language.text("save_file.messages.sketch_exists"),
          Language.interpolate("save_file.messages.sketch_exists.description",
          newMainName));
        return false;
      }
      newSketchName = newMainName;
      newMainFileName = newMainName + "." + mode.getDefaultExtension();

    } else {
      newFolder = new File(newParentDir, newSketchName);  // sketch folder name can be different
    }

    // make sure there doesn't exist a tab with that name already
    // but ignore this situation for the first tab, since it's probably being
    // re-saved (with the same name) to another location/folder.
    for (int i = 1; i < codeCount; i++) {
      if (newSketchName.equalsIgnoreCase(code[i].getPrettyName())) {
        Messages.showMessage(Language.text("save_file.messages.tab_exists"),
                             Language.interpolate("save_file.messages.tab_exists.description",
                             newSketchName));
        return false;
      }
    }

    // check if the paths are identical
    if (newFolder.equals(folder)) {
      // just use "save" here instead, because the user will have received a
      // message (from the operating system) about "do you want to replace?"
      return save();
    }

    // check to see if the user is trying to save this sketch inside itself
    try {
      // Includes the separator so that a/b/c is different from a/b/c2.
      // (a/b/c matches a/b/c2, but a/b/c/ does not match a/b/c2/)
      String newPath = newFolder.getCanonicalPath() + File.separator;
      String oldPath = folder.getCanonicalPath() + File.separator;

      if (newPath.indexOf(oldPath) == 0) {
        Messages.showWarning(Language.text("save_file.messages.recursive_save"),
                             Language.text("save_file.messages.recursive_save.description"));
        return false;
      }
    } catch (IOException ignored) { }

    // if the new folder already exists, then first remove its contents before
    // copying everything over (user will have already been warned).
    if (newFolder.exists()) {
      //Util.removeDir(newFolder);
      try {
        Platform.deleteFile(newFolder);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    // in fact, you can't do this on Windows because the file dialog
    // will instead put you inside the folder, but it happens on OS X a lot.

    // now make a fresh copy of the folder
    if (!newFolder.mkdirs()) {
      // mkdirs() returns true when the folders are created, which should
      // be the case here because we removed any existing 'newFolder' above.
      // If this fails, then it probably means the removeDir() failed,
      // or at least left things behind, which could mean badness later.
      System.err.println("Error creating path " + newFolder);
    }

    // grab the contents of the current tab before saving
    // first get the contents of the editor text area
    updateSketchCodes();

    File[] copyItems = folder.listFiles(file -> {
      String name = file.getName();
      // just in case the OS likes to return these as if they're legit
      if (name.equals(".") || name.equals("..")) {
        return false;
      }
      // list of files/folders to be ignored during "save as"
      String[] ignorable = mode.getIgnorable();
      if (ignorable != null) {
        for (String ignore : ignorable) {
          if (name.equals(ignore)) {
            return false;
          }
        }
      }
      // ignore the extensions for code, since that'll be copied below
      for (String ext : mode.getExtensions()) {
        if (name.endsWith(ext)) {
          return false;
        }
      }
      // don't do screen captures, since there might be thousands. kind of
      // a hack, but seems harmless. hm, where have i heard that before...
      //noinspection RedundantIfStatement
      if (name.startsWith("screen-")) {
        return false;
      }
      return true;
    });

    startSaveAsThread(newFolder, copyItems);

    // Save each tab to its new location
    for (int i = 0; i < codeCount; i++) {
      File newFile = new File(newFolder, code[i].getFileName());
      if (i == 0 && sync) {
        newFile = new File(newFolder, newMainFileName);
      }
      code[i].saveAs(newFile);
    }

    // We were removing the old folder from the Recent menu, but folks
    // did not like that behavior because they expected to have older
    // versions readily available, so we shut it off in 3.5.4 and 4.x.
    // https://github.com/processing/processing/issues/5902

//    if (sync) {
//      // save the main tab with its new name
//      File newFile = new File(newFolder, newMainName + "." + mode.getDefaultExtension());
//      code[0].saveAs(newFile);
//    }

    updateInternal(newFolder);

    // Make sure that it's not an untitled sketch
    setUntitled(false);

    // Add this sketch back using the new name
    Recent.append(editor);

    // let Editor know that the save was successful
    return true;
  }


  AtomicBoolean saving = new AtomicBoolean();

  public boolean isSaving() {
    return saving.get();
  }


  /**
   * Kick off a background thread to copy everything *but* the .pde files.
   * Due to the poor way (dating back to the late 90s with DBN) that our
   * save() and saveAs() methods have been implemented to return booleans,
   * there isn't a good way to return a value to the calling thread without
   * a good bit of refactoring (that should be done at some point).
   * As a result, this method will return 'true' before the full "Save As"
   * has completed, which will cause problems in weird cases.
   * <p/>
   * For instance, the threading will cause problems while saving an untitled
   * sketch that has an enormous data folder while quitting. The save thread to
   * move those data folder files won't have finished before this returns true,
   * and the PDE may quit before the SwingWorker completes its job.
   * <p/>
   * <a href="https://github.com/processing/processing/issues/3843">3843</a>
   */
  void startSaveAsThread(final File newFolder, final File[] copyItems) {
    saving.set(true);
    EventQueue.invokeLater(() -> {
      final JFrame frame =
        new JFrame("Saving “" + newFolder.getName() + "“…");
      frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

      Box box = Box.createVerticalBox();
      box.setBorder(new EmptyBorder(16, 16, 16, 16));

      if (Platform.isMacOS()) {
        frame.setBackground(Color.WHITE);
      }

      JLabel label =
        new JLabel("Saving additional files from the sketch folder...");
      box.add(label);
      box.add(Box.createVerticalStrut(8));

      final JProgressBar progressBar = new JProgressBar(0, 100);
      // no luck, stuck with ugly on OS X
      //progressBar.putClientProperty("JComponent.sizeVariant", "regular");
      progressBar.setValue(0);
      progressBar.setStringPainted(true);
      box.add(progressBar);

      frame.getContentPane().add(box);
      frame.pack();
      frame.setLocationRelativeTo(editor);
      Toolkit.setIcon(frame);
      frame.setVisible(true);

      new SwingWorker<Void, Void>() {

        @Override
        protected Void doInBackground() throws Exception {
          addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
              progressBar.setValue((Integer) evt.getNewValue());
            }
          });

          long totalSize = 0;
          for (File copyable : copyItems) {
            totalSize += Util.calcSize(copyable);
          }

          long progress = 0;
          setProgress(0);
          for (File copyable : copyItems) {
            if (copyable.isDirectory()) {
              copyDir(copyable,
                      new File(newFolder, copyable.getName()),
                      progress, totalSize);
              progress += Util.calcSize(copyable);
            } else {
              copyFile(copyable,
                       new File(newFolder, copyable.getName()),
                       progress, totalSize);
              if (Util.calcSize(copyable) < 512 * 1024) {
                // If the file length > 0.5MB, the copyFile() function has
                // been redesigned to change progress every 0.5MB so that
                // the progress bar doesn't stagnate during that time
                progress += Util.calcSize(copyable);
                setProgress((int) (progress * 100L / totalSize));
              }
            }
          }
          saving.set(false);
          return null;
        }


        /**
         * Overloaded copyFile that is called whenever a Save As is being done,
         * so that the ProgressBar is updated for very large files as well.
         */
        void copyFile(File sourceFile, File targetFile,
                      long progress, long totalSize) throws IOException {
          BufferedInputStream from =
            new BufferedInputStream(new FileInputStream(sourceFile));
          BufferedOutputStream to =
            new BufferedOutputStream(new FileOutputStream(targetFile));
          byte[] buffer = new byte[16 * 1024];
          int bytesRead;
          int progRead = 0;
          while ((bytesRead = from.read(buffer)) != -1) {
            to.write(buffer, 0, bytesRead);
            progRead += bytesRead;
            if (progRead >= 512 * 1024) {  // to update progress bar every 0.5MB
              progress += progRead;
              //progressBar.setValue((int) Math.min(Math.ceil(progress * 100.0 / totalSize), 100));
              setProgress((int) (100L * progress / totalSize));
              progRead = 0;
            }
          }
          // Final update to progress bar
          setProgress((int) (100L * progress / totalSize));

          from.close();
          to.flush();
          to.close();

          if (!targetFile.setLastModified(sourceFile.lastModified())) {
            System.err.println("Warning: Could not set modification date/time for " + targetFile);
          }
          if (!targetFile.setExecutable(sourceFile.canExecute())) {
            if (!Platform.isWindows()) {  // more of a UNIX thing
              System.err.println("Warning: Could not set permissions for " + targetFile);
            }
          }
        }


        long copyDir(File sourceDir, File targetDir,
                     long progress, long totalSize) throws IOException {
          // Overloaded copyDir so that the Save As progress bar gets updated when the
          //    files are in folders as well (like in the data folder)
          if (sourceDir.equals(targetDir)) {
            final String urDum = "source and target directories are identical";
            throw new IllegalArgumentException(urDum);
          }
          targetDir.mkdirs();
          String[] files = sourceDir.list();
          if (files != null) {
            for (String filename : files) {
              // Ignore dot files (.DS_Store), dot folders (.svn) while copying
              if (filename.charAt(0) == '.') {
                continue;
              }

              File source = new File(sourceDir, filename);
              File target = new File(targetDir, filename);
              if (source.isDirectory()) {
                progress = copyDir(source, target, progress, totalSize);
                //progressBar.setValue((int) Math.min(Math.ceil(progress * 100.0 / totalSize), 100));
                setProgress((int) (100L * progress / totalSize));
                target.setLastModified(source.lastModified());
              } else {
                copyFile(source, target, progress, totalSize);
                progress += source.length();
                //progressBar.setValue((int) Math.min(Math.ceil(progress * 100.0 / totalSize), 100));
                setProgress((int) (100L * progress / totalSize));
              }
            }
          } else {
            throw new IOException("Could not list files inside " + sourceDir);
          }
          return progress;
        }


        @Override
        public void done() {
          frame.dispose();
          editor.statusNotice(Language.text("editor.status.saving.done"));
        }
      }.execute();
    });
  }


  /**
   * Update internal state for new sketch name or folder location.
   */
  protected void updateInternal(File sketchFolder) {
    // reset all the state information for the sketch object
    mainFile = code[0].getFile();

    name = sketchFolder.getName();
    folder = sketchFolder;
    disappearedWarning = false;
    codeFolder = new File(folder, "code");
    dataFolder = new File(folder, "data");

    updateNameProperties();

    // Name changed, rebuild the sketch menus
    calcModified();
    editor.updateTitle();
    editor.getBase().rebuildSketchbook();
  }


  protected void updateModeProperties(Mode mode, Mode defaultMode) {
    updateModeProperties(folder, mode, defaultMode);
  }


  /**
   * Create or modify a sketch.properties file to specify the given Mode.
   */
  static protected void updateModeProperties(File folder, Mode mode, Mode defaultMode) {
    try {
      // Read the old sketch.properties file if it already exists
      Settings props = loadProperties(folder);

      // If changing to the default Mode,
      // remove those entries from sketch.properties
      if (mode == defaultMode) {
        props.remove("mode");
        props.remove("mode.id");
      } else {
        // Setting to something other than the default Mode,
        // write that and any other params already in the file.
        props.set("mode", mode.getTitle());
        props.set("mode.id", mode.getIdentifier());
      }
      props.reckon();

    } catch (IOException e) {
      System.err.println("Error while writing sketch.properties");
      e.printStackTrace();
    }
  }


  /*
  protected Settings loadProperties() throws IOException {
    return loadProperties(folder);
  }
  */


  /**
   * Opens and parses sketch.properties. If it does not exist, returns an
   * empty Settings object that can be written back to the same location.
   */
  static protected Settings loadProperties(File folder) throws IOException {
    /*
    File propsFile = new File(folder, "sketch.properties");
    if (propsFile.exists()) {
      return new Settings(propsFile);
    }
    return null;
    */
    return new Settings(new File(folder, "sketch.properties"));
  }


  /**
   * Check through the various modes and see if this is a legit sketch.
   * Because the default mode will be the first in the list, this will always
   * prefer that one over the others.
   */
  static protected File findMain(File folder, List<Mode> modeList) {
    try {
      Settings props = Sketch.loadProperties(folder);
      String main = props.get("main");
      if (main != null) {
        File mainFile = new File(folder, main);
        if (!mainFile.exists()) {
          System.err.println(main + " does not exist inside " + folder);
          // Fall through to the code below in case we can recover.
          // Not removing the bad entry since this is a find() method.
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    for (Mode mode : modeList) {
      // Test whether a .pde file of the same name as its parent folder exists.
      String defaultName = folder.getName() + "." + mode.getDefaultExtension();
      File entry = new File(folder, defaultName);
      if (entry.exists()) {
        return entry;
      }
    }
    return null;
  }


  private void updateNameProperties() {
    // If the main file and the sketch name are not identical,
    // update sketch.properties.
    String mainName = mainFile.getName();
    String defaultName = name + "." + mode.getDefaultExtension();
    //System.out.println("main name is " + mainName + " and default name is " + defaultName);

    try {
      // Read the old sketch.properties file if it already exists
      Settings props = loadProperties(folder);

      if (mainName.equals(defaultName)) {
        props.remove("main");
      } else {
        props.set("main", mainName);
      }
      //System.out.println("props size is now " + props.getMap().size());
      props.reckon();

    } catch (IOException e) {
      System.err.println("Error while updating sketch.properties");
      e.printStackTrace();
    }
  }


  /**
   * Prompt the user for a new file to the sketch, then call the
   * other addFile() function to actually add it.
   */
  public void handleAddFile() {
    // make sure the user didn't hide the sketch folder
    ensureExistence();

    // if read-only, give an error
    if (isReadOnly()) {
      // if the files are read-only, need to first do a "save as".
      Messages.showMessage(Language.text("add_file.messages.is_read_only"),
                           Language.text("add_file.messages.is_read_only.description"));
      return;
    }

    // get a dialog, select a file to add to the sketch
    String prompt = Language.text("file");
    //FileDialog fd = new FileDialog(new Frame(), prompt, FileDialog.LOAD);
    FileDialog fd = new FileDialog(editor, prompt, FileDialog.LOAD);
    fd.setVisible(true);

    String directory = fd.getDirectory();
    String filename = fd.getFile();
    if (filename == null) return;

    // copy the file into the folder. if people would rather
    // move instead of copy, they can do it by hand
    File sourceFile = new File(directory, filename);

    // now do the work of adding the file
    boolean result = addFile(sourceFile);

    if (result) {
//      editor.statusNotice("One file added to the sketch.");
    	//Done from within TaskAddFile inner class when copying is completed
    }
  }


  /**
   * Add a file to the sketch.
   * <p/>
   * .pde or .java files will be added to the sketch folder. <br/>
   * .jar, .class, .dll, .jnilib, and .so files will all
   * be added to the "code" folder. <br/>
   * All other files will be added to the "data" folder.
   * <p/>
   * If they don't exist already, the "code" or "data" folder
   * will be created.
   * <p/>
   * @return true if successful.
   */
  public boolean addFile(File sourceFile) {
    if (sourceFile.isDirectory()) {
      System.err.println("Skipping folder " + sourceFile);
      System.err.println("Dragging and dropping a folder is not supported.");
      return false;
    }
    String filename = sourceFile.getName();
    File destFile = null;
    String codeExtension = null;
    boolean replacement = false;

    boolean isCode = false;

    // if the file appears to be code related, drop it
    // into the code folder, instead of the data folder
    if (filename.toLowerCase().endsWith(".class") ||
        filename.toLowerCase().endsWith(".jar") ||
        filename.toLowerCase().endsWith(".dll") ||
        filename.toLowerCase().endsWith(".dylib") ||
        filename.toLowerCase().endsWith(".jnilib") ||
        filename.toLowerCase().endsWith(".so")) {

      if (!codeFolder.exists()) {
        boolean success = codeFolder.mkdirs();
        if (!success) {
          System.err.println("Could not create " + codeFolder);
          return false;
        }
      }
      destFile = new File(codeFolder, filename);
      isCode = true;
    } else {
      for (String extension : mode.getExtensions()) {
        String lower = filename.toLowerCase();
        if (lower.endsWith("." + extension)) {
          destFile = new File(this.folder, filename);
          codeExtension = extension;
        }
      }
      if (codeExtension == null) {
        prepareDataFolder();
        destFile = new File(dataFolder, filename);
      }
    }

    // check whether this file already exists
    if (destFile.exists()) {
      Object[] options = { Language.text("prompt.ok"), Language.text("prompt.cancel") };
      String prompt = Language.interpolate("add_file.messages.confirm_replace",
                                           filename);
      int result = JOptionPane.showOptionDialog(editor,
                                                prompt,
                                                "Replace",
                                                JOptionPane.YES_NO_OPTION,
                                                JOptionPane.QUESTION_MESSAGE,
                                                null,
                                                options,
                                                options[0]);
      if (result == JOptionPane.YES_OPTION) {
        replacement = true;
      } else {
        return false;
      }
    }

    // If it's a replacement, delete the old file first,
    // otherwise case changes will not be preserved.
    // https://download.processing.org/bugzilla/969.html
    if (replacement) {
      boolean muchSuccess = destFile.delete();
      if (!muchSuccess) {
        Messages.showWarning(Language.text("add_file.messages.error_adding"),
                             Language.interpolate("add_file.messages.cannot_delete.description", filename));
        return false;
      }
    }

    // make sure they aren't the same file
    if ((codeExtension == null) && sourceFile.equals(destFile)) {
      Messages.showWarning(Language.text("add_file.messages.same_file"),
                           Language.text("add_file.messages.same_file.description"));
      return false;
    }

    // Handles "Add File" when a .pde is used. For 3.0b1, this no longer runs
    // on a separate thread because it's totally unnecessary (a .pde file is
    // not going to be so large that it's ever required) and otherwise we have
    // to introduce a threading block here.
    // https://github.com/processing/processing/issues/3383
    if (!sourceFile.equals(destFile)) {
      try {
        Util.copyFile(sourceFile, destFile);

      } catch (IOException e) {
        Messages.showWarning(Language.text("add_file.messages.error_adding"),
                             Language.interpolate("add_file.messages.cannot_add.description", filename), e);
        return false;
      }
    }

    if (isCode) {
      editor.codeFolderChanged();
    }

    if (codeExtension != null) {
      SketchCode newCode = new SketchCode(destFile, codeExtension);

      if (replacement) {
        replaceCode(newCode);

      } else {
        insertCode(newCode);
        sortCode();
      }
      setCurrentCode(filename);
      editor.repaintHeader();
      if (isUntitled()) {  // TODO probably not necessary? problematic?
        // Mark the new code as modified so that the sketch is saved
        current.setModified(true);
      }

    } else {
      if (isUntitled()) {  // TODO probably not necessary? problematic?
        // If a file has been added, mark the main code as modified so
        // that the sketch is properly saved.
        code[0].setModified(true);
      }
    }
    return true;
  }


  /**
   * Change what file is currently being edited. Changes the current tab index.
   * <OL>
   * <LI> store the String for the text of the current file.
   * <LI> retrieve the String for the text of the new file.
   * <LI> change the text that's visible in the text area
   * </OL>
   */
  public void setCurrentCode(int which) {
    // if current is null, then this is the first setCurrent(0)
    if (which < 0 || which >= codeCount ||
        ((currentIndex == which) && (current == code[currentIndex]))) {
      return;
    }

    // get the text currently being edited
    if (current != null) {
      current.setState(editor.getText(),
                       editor.getSelectionStart(),
                       editor.getSelectionStop(),
                       editor.getScrollPosition());
    }

    current = code[which];
    currentIndex = which;
    current.visited = System.currentTimeMillis();

    editor.setCode(current);
    editor.repaintHeader();
  }


  /**
   * Internal helper function to set the current tab based on a name.
   * @param findName the file name (not pretty name) to be shown
   */
  public void setCurrentCode(String findName) {
    for (int i = 0; i < codeCount; i++) {
      if (findName.equals(code[i].getFileName()) ||
          findName.equals(code[i].getPrettyName())) {
        setCurrentCode(i);
        return;
      }
    }
  }


  /**
   * Create a temporary folder that includes the sketch's name in its title.
   */
  public File makeTempFolder() {
    try {
      return Util.createTempFolder(name, "temp", null);

    } catch (IOException e) {
      Messages.showWarning(Language.text("temp_dir.messages.bad_build_folder"),
                           Language.text("temp_dir.messages.bad_build_folder.description"), e);
    }
    return null;
  }


  /**
   * Make sure the sketch hasn't been moved or deleted by a nefarious user.
   * If they did, try to re-create it and save. Only checks whether the
   * main folder is still around, but not its contents.
   */
  public void ensureExistence() {
    if (!folder.exists()) {
      // Avoid an infinite loop if we've already warned about this
      // https://github.com/processing/processing/issues/4805
      if (!disappearedWarning) {
        disappearedWarning = true;

        // Disaster recovery, try to salvage what's there already.
        Messages.showWarning(Language.text("ensure_exist.messages.missing_sketch"),
                             Language.text("ensure_exist.messages.missing_sketch.description"));
        try {
          folder.mkdirs();
          modified = true;

          for (int i = 0; i < codeCount; i++) {
            code[i].save();  // this will force a save
          }
          calcModified();

        } catch (Exception e) {
          // disappearedWarning prevents infinite loop in this scenario
          Messages.showWarning(Language.text("ensure_exist.messages.unrecoverable"),
                               Language.text("ensure_exist.messages.unrecoverable.description"), e);
        }
      }
    }
  }


  /**
   * Returns true if this is a read-only sketch. Used for the
   * "examples" directory, or when sketches are loaded from read-only
   * volumes or folders without appropriate permissions.
   */
  public boolean isReadOnly() {
    String path = folder.getAbsolutePath();
    List<Mode> modes = editor.getBase().getModeList();
    // Make sure it's not read-only for another Mode besides this one
    // https://github.com/processing/processing/issues/773
    for (Mode mode : modes) {
      if (path.startsWith(mode.getExamplesFolder().getAbsolutePath()) ||
          path.startsWith(mode.getLibrariesFolder().getAbsolutePath())) {
        return true;
      }
    }

    // Check to see if each modified code file can be written.
    // Note: canWrite() does not work on directories.
    for (int i = 0; i < codeCount; i++) {
      if (code[i].isModified() &&
          code[i].fileReadOnly() &&
          code[i].fileExists()) {
        return true;
      }
    }

    return false;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  // Additional accessors added in 0136 because of package work.
  // These will also be helpful for tool developers.


  /**
   * Returns the name of this sketch. (The pretty name of the main tab.)
   */
  public String getName() {
    return name;
  }


//  /**
//   * Returns a File object for the main .pde file for this sketch.
//   */
//  public File getMainFile() {
//    return mainFile;
//  }


  /**
   * Returns the name (without extension) of the main tab.
   * (This version still has underscores and is a legit class name.)
   * Most getName() calls before 4.0 were to get the main class,
   * so this method addition allows the sketch name to be decoupled
   * from the name of the main tab.
   */
  public String getMainName() {
    return code[0].getPrettyName();
  }


  /**
   * Returns path to the main .pde file for this sketch.
   */
  public String getMainPath() {
    return mainFile.getAbsolutePath();
  }


  /**
   * Returns the sketch folder.
   */
  public File getFolder() {
    return folder;
  }


  /**
   * Returns the location of the sketch's data folder. (It may not exist yet.)
   */
  public File getDataFolder() {
    return dataFolder;
  }


  public boolean hasDataFolder() {
    return dataFolder.exists();
  }


  /**
   * Create the data folder if it does not exist already. As a convenience,
   * it also returns the data folder, since it's likely about to be used.
   */
  public File prepareDataFolder() {
    if (!dataFolder.exists()) {
      dataFolder.mkdirs();
    }
    return dataFolder;
  }


  /**
   * Returns the location of the sketch's code folder. (It may not exist yet.)
   */
  public File getCodeFolder() {
    return codeFolder;
  }


  public boolean hasCodeFolder() {
    return (codeFolder != null) && codeFolder.exists();
  }


  public SketchCode[] getCode() {
    return code;
  }


  public int getCodeCount() {
    return codeCount;
  }


  // Used by GUI Builder for Processing
  // https://github.com/processing/processing4/issues/545
  // https://github.com/processing/processing4/issues/596
  public SketchCode getCode(int index) {
    return code[index];
  }


  public SketchCode getCurrentCode() {
    return current;
  }


  public int getCurrentCodeIndex() {
    return currentIndex;
  }


  /**
   * Tried to remove in beta 6, but in use by Python Mode.
   * When it's removed there, let me know, and I'll remove it here.
   */
  @Deprecated
  public String getMainProgram() {
    return getCode(0).getProgram();
  }


  public void setUntitled(boolean untitled) {
    this.untitled = untitled;
    editor.updateTitle();
  }


  public boolean isUntitled() {
    return untitled;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


//  /**
//   * Convert to sanitized name and alert the user
//   * if changes were made.
//   */
//  static public String checkName(String origName) {
//    String newName = sanitizeName(origName);
//
//    if (!newName.equals(origName)) {
//      String msg =
//        Language.text("check_name.messages.is_name_modified");
//      System.out.println(msg);
//    }
//    return newName;
//  }


  /**
   * Return true if the name is valid for a Processing sketch.
   * Extensions of the form .foo are ignored.
   */
  public static boolean isSanitaryName(String name) {
    final int dot = name.lastIndexOf('.');
    if (dot >= 0) {
      name = name.substring(0, dot);
    }
    return sanitizeName(name).equals(name);
  }


  static boolean isAsciiLetter(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }


  /**
   * Produce a sanitized name that fits our standards for likely to work.
   * <p/>
   * Java classes have a wider range of names that are technically allowed
   * (supposedly any Unicode name) than what we support. The reason for
   * going more narrow is to avoid situations with text encodings and
   * converting during the process of moving files between operating
   * systems, i.e. uploading from a Windows machine to a Linux server,
   * or reading a FAT32 partition in OS X and using a thumb drive.
   * <p/>
   * This helper function replaces everything but A-Z, a-z, and 0-9 with
   * underscores. Also disallows starting the sketch name with a digit
   * or underscore.
   * <p/>
   * In Processing 2.0, sketches can no longer begin with an underscore,
   * because these aren't valid class names on Android.
   */
  static public String sanitizeName(String origName) {
    char[] orig = origName.toCharArray();
    StringBuilder sb = new StringBuilder();

    // Can't lead with a digit (or anything besides a letter), so prefix with
    // "sketch_". In 1.x this prefixed with an underscore, but those get shaved
    // off later, since you can't start a sketch name with underscore anymore.
    if (!isAsciiLetter(orig[0])) {
      sb.append("sketch_");
    }
//    for (int i = 0; i < orig.length; i++) {
    for (char c : orig) {
      if (isAsciiLetter(c) || (c >= '0' && c <= '9')) {
        sb.append(c);

      } else {
        // Tempting to only add if prev char is not underscore, but that
        // might be more confusing if lots of chars are converted and the
        // result is a very short string that's nothing like the original.
        sb.append('_');
      }
    }
    // Let's not be ridiculous about the length of filenames.
    // in fact, Mac OS 9 can handle 255 chars, though it can't really
    // deal with filenames longer than 31 chars in the Finder.
    // Limiting to that for sketches would mean setting the
    // upper-bound on the character limit here to 25 characters
    // (to handle the base name + ".class")
    if (sb.length() > 63) {
      sb.setLength(63);
    }
    // Remove underscores from the beginning, these seem to be a reserved
    // thing on Android, plus it sometimes causes trouble elsewhere.
    int underscore = 0;
    while (underscore < sb.length() && sb.charAt(underscore) == '_') {
      underscore++;
    }
    if (underscore == sb.length()) {
      return "bad_sketch_name_please_fix";

    } else if (underscore != 0) {
      return sb.substring(underscore);
    }
    return sb.toString();
  }


  public Mode getMode() {
    return mode;
  }


  @Override
  public boolean equals(Object another) {
    if (another instanceof Sketch) {
      return getMainPath().equals(((Sketch) another).getMainPath());
    }
    return false;
  }
}
