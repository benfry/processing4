/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013-15 The Processing Foundation
  Copyright (c) 2010-13 Ben Fry and Casey Reas

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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

import processing.app.contrib.ContributionManager;
import processing.app.syntax.*;
import processing.app.ui.Editor;
import processing.app.ui.EditorException;
import processing.app.ui.EditorState;
import processing.app.ui.ExamplesFrame;
import processing.app.ui.Recent;
import processing.app.ui.Toolkit;
import processing.core.PApplet;


public abstract class Mode {
  protected Base base;

  protected File folder;

  protected TokenMarker tokenMarker;
  protected Map<String, String> keywordToReference = new HashMap<>();

  protected Settings theme;

  // maps imported packages to their library folder
  protected Map<String, List<Library>> importToLibraryTable;

  // these menus are shared so that they needn't be rebuilt for all windows
  // each time a sketch is created, renamed, or moved.
//  protected JMenu examplesMenu;  // this is for the menubar, not the toolbar
  protected JMenu importMenu;

  protected ExamplesFrame examplesFrame;

  // popup menu used for the toolbar
  protected JMenu toolbarMenu;

  protected File examplesFolder;
  protected File librariesFolder;
  protected File referenceFolder;

//  protected File examplesContribFolder;

  public List<Library> coreLibraries;
  public List<Library> contribLibraries;

  // Initialize to empty b/c these may not exist for contributed Mode classes.
  public List<Library> foundationLibraries = new ArrayList<>();

  /** Library folder for core. (Used for OpenGL in particular.) */
  protected Library coreLibrary;

  /**
   * ClassLoader used to retrieve classes for this mode. Useful if you want
   * to grab any additional classes that subclass what's in the mode folder.
   */
  protected ClassLoader classLoader;


  public Mode(Base base, File folder) {
    this.base = base;
    this.folder = folder;
    tokenMarker = createTokenMarker();

    // Get paths for the libraries and examples in the mode folder
    examplesFolder = new File(folder, "examples");
    librariesFolder = new File(folder, "libraries");
    referenceFolder = new File(folder, "reference");

//    rebuildToolbarMenu();
    rebuildLibraryList();
//    rebuildExamplesMenu();

    try {
      for (File file : getKeywordFiles()) {
        loadKeywords(file);
      }
    } catch (IOException e) {
      Messages.showWarning("Problem loading keywords",
                           "Could not load keywords file for " + getTitle() + " mode.", e);
    }
  }


  /**
   * To add additional keywords, or to grab them from another mode, override
   * this function. If your mode has no keywords, return a zero length array.
   */
  public File[] getKeywordFiles() {
    return new File[] { new File(folder, "keywords.txt") };
  }


  protected void loadKeywords(File keywordFile) throws IOException {
    // overridden for Python, where # is an actual keyword
    loadKeywords(keywordFile, "#");
  }


  @SuppressWarnings("SameParameterValue")
  protected void loadKeywords(File keywordFile,
                              String commentPrefix) throws IOException {
    String[] lines = PApplet.loadStrings(keywordFile);
    if (lines != null) {
      for (String line : lines) {
        if (!line.trim().startsWith(commentPrefix)) {
          // Was difficult to make sure that mode authors were properly doing
          // tab-separated values. By definition, there can't be additional
          // spaces inside a keyword (or filename), so just splitting on tokens.
          String[] pieces = PApplet.splitTokens(line);
          if (pieces.length >= 2) {
            String keyword = pieces[0];
            String coloring = pieces[1];

            if (coloring.length() > 0) {
              tokenMarker.addColoring(keyword, coloring);
            }
            if (pieces.length == 3) {
              String htmlFilename = pieces[2];
              if (htmlFilename.length() > 0) {
                // if the file is for the version with parens,
                // add a paren to the keyword
                if (htmlFilename.endsWith("_")) {
                  keyword += "_";
                }
                // Allow the bare size() command to override the lookup
                // for StringList.size() and others, but not vice-versa.
                // https://github.com/processing/processing/issues/4224
                boolean seen = keywordToReference.containsKey(keyword);
                if (!seen || keyword.equals(htmlFilename)) {
                  keywordToReference.put(keyword, htmlFilename);
                }
              }
            }
          }
        }
      }
    } else {
      System.err.println("Could not read " + keywordFile);
    }
  }


  public void setClassLoader(ClassLoader loader) {
    this.classLoader = loader;
  }


  public ClassLoader getClassLoader() {
    return classLoader;
  }


  public File getContentFile(String path) {
    return new File(folder, path);
  }


  @SuppressWarnings("unused")
  public InputStream getContentStream(String path) throws FileNotFoundException {
    return new FileInputStream(getContentFile(path));
  }


  /**
   * Add files to a folder to create an empty sketch. This can be overridden
   * to add template files to a sketch for Modes that need them.
   *
   * @param sketchFolder the directory where the new sketch should live
   * @param sketchName the name of the new sketch
   * @return the main file for the sketch to be opened via handleOpen()
   * @throws IOException if the file somehow already exists
   */
  public File addTemplateFiles(File sketchFolder,
                               String sketchName) throws IOException {
    // Make an empty .pde file
    File newbieFile =
      new File(sketchFolder, sketchName + "." + getDefaultExtension());

    try {
      // First see if the user has overridden the default template
      File templateFolder = checkSketchbookTemplate();

      // Next see if the Mode has its own template
      if (templateFolder == null) {
        templateFolder = getTemplateFolder();
      }
      if (templateFolder.exists()) {
        Util.copyDir(templateFolder, sketchFolder);
        File templateFile =
          new File(sketchFolder, "sketch." + getDefaultExtension());
        if (!templateFile.renameTo(newbieFile)) {
          System.err.println("Error while assigning the sketch template.");
        }
      } else {
        if (!newbieFile.createNewFile()) {
          System.err.println(newbieFile + " already exists.");
        }
      }
    } catch (Exception e) {
      // just spew out this error and try to recover below
      e.printStackTrace();
    }
    return newbieFile;
  }


  /**
   * See if the user has their own template for this Mode. If the default
   * extension is "pde", this will look for a file called sketch.pde to use
   * as the template for all sketches.
   */
  protected File checkSketchbookTemplate() {
    File user = new File(Base.getSketchbookTemplatesFolder(), getTitle());
    if (user.exists()) {
      File template = new File(user, "sketch." + getDefaultExtension());
      if (template.exists() && template.canRead()) {
        return user;
      }
    }
    return null;
  }


  public File getTemplateFolder() {
    return getContentFile("template");
  }


  /**
   * Return the pretty/printable/menu name for this mode. This is separate from
   * the single word name of the folder that contains this mode. It could even
   * have spaces, though that might result in sheer madness or total mayhem.
   */
  abstract public String getTitle();


  /**
   * Get an identifier that can be used to resurrect this mode and connect it
   * to a sketch. Using this instead of getTitle() because there might be name
   * clashes with the titles, but there should not be once the actual package,
   * et al. is included.
   * @return full name (package + class name) for this mode.
   */
  public String getIdentifier() {
    return getClass().getCanonicalName();
  }


  /**
   * Create a new editor associated with this mode.
   */
  abstract public Editor createEditor(Base base, String path,
                                      EditorState state) throws EditorException;


  /**
   * Get the folder where this mode is stored.
   * @since 3.0a3
   */
  public File getFolder() {
    return folder;
  }


  public File getExamplesFolder() {
    return examplesFolder;
  }


  public File getLibrariesFolder() {
    return librariesFolder;
  }


  public File getReferenceFolder() {
    return referenceFolder;
  }


  public void rebuildLibraryList() {
    // reset the table mapping imports to libraries
    Map<String, List<Library>> newTable = new HashMap<>();

    Library core = getCoreLibrary();
    if (core != null) {
      core.addPackageList(newTable);
    }

    coreLibraries = Library.list(librariesFolder);
    File contribLibrariesFolder = Base.getSketchbookLibrariesFolder();
    contribLibraries = Library.list(contribLibrariesFolder);

    // Check to see if video and sound are installed and move them
    // from the contributed list to the core list.
    foundationLibraries = new ArrayList<>();
    for (Library lib : contribLibraries) {
      if (lib.isFoundation()) {
        foundationLibraries.add(lib);
      }
    }
    coreLibraries.addAll(foundationLibraries);
    contribLibraries.removeAll(foundationLibraries);

    for (Library lib : coreLibraries) {
      lib.addPackageList(newTable);
    }

    for (Library lib : contribLibraries) {
      lib.addPackageList(newTable);
    }

    // Make this Map thread-safe
    importToLibraryTable = Collections.unmodifiableMap(newTable);

    if (base != null) {
      base.getEditors().forEach(Editor::librariesChanged);
    }
  }


  public Library getCoreLibrary() {
    return null;
  }


  public Library getLibrary(String pkgName) throws SketchException {
    List<Library> libraries = importToLibraryTable.get(pkgName);
    if (libraries == null) {
      return null;

    } else if (libraries.size() > 1) {
      String primary = "More than one library is competing for this sketch.";
      String secondary = "The import " + pkgName + " points to multiple libraries:<br>";
      for (Library library : libraries) {
        String location = library.getPath();
        if (location.startsWith(getLibrariesFolder().getAbsolutePath())) {
          location = "part of Processing";
        }
        secondary += "<b>" + library.getName() + "</b> (" + location + ")<br>";
      }
      secondary += "Extra libraries need to be removed before this sketch can be used.";
      Messages.showWarningTiered("Duplicate Library Problem", primary, secondary, null);
      throw new SketchException("Duplicate libraries found for " + pkgName + ".");

    } else {
      return libraries.get(0);
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


//  abstract public EditorToolbar createToolbar(Editor editor);


  @SuppressWarnings("unused")
  public JMenu getToolbarMenu() {
    if (toolbarMenu == null) {
      rebuildToolbarMenu();
    }
    return toolbarMenu;
  }


  public void insertToolbarRecentMenu() {
    if (toolbarMenu == null) {
      rebuildToolbarMenu();
    } else {
      toolbarMenu.insert(Recent.getToolbarMenu(), 1);
    }
  }


  public void removeToolbarRecentMenu() {
    toolbarMenu.remove(Recent.getToolbarMenu());
  }


  protected void rebuildToolbarMenu() {  //JMenu menu) {
    JMenuItem item;
    if (toolbarMenu == null) {
      toolbarMenu = new JMenu();
    } else {
      toolbarMenu.removeAll();
    }

    //System.out.println("rebuilding toolbar menu");
    // Add the single "Open" item
    item = Toolkit.newJMenuItem("Open...", 'O');
    item.addActionListener(e -> base.handleOpenPrompt());
    toolbarMenu.add(item);

    insertToolbarRecentMenu();

    item = Toolkit.newJMenuItemShift("Examples...", 'O');
    item.addActionListener(e -> showExamplesFrame());
    toolbarMenu.add(item);

    item = new JMenuItem(Language.text("examples.add_examples"));
    item.addActionListener(e -> ContributionManager.openExamples());
    toolbarMenu.add(item);

    // Add a list of all sketches and subfolders
    toolbarMenu.addSeparator();
    base.populateSketchbookMenu(toolbarMenu);
//    boolean found = false;
//    try {
//      found = base.addSketches(toolbarMenu, base.getSketchbookFolder(), true);
//    } catch (IOException e) {
//      Base.showWarning("Sketchbook Toolbar Error",
//                       "An error occurred while trying to list the sketchbook.", e);
//    }
//    if (!found) {
//      JMenuItem empty = new JMenuItem("(empty)");
//      empty.setEnabled(false);
//      toolbarMenu.add(empty);
//    }
  }


  protected int importMenuIndex = -1;

  /**
   * Rather than re-building the library menu for every open sketch (very slow
   * and prone to bugs when updating libs, particularly with the contribs mgr),
   * share a single instance across all windows.
   * @since 3.0a6
   * @param sketchMenu the Sketch menu that's currently active
   */
  public void removeImportMenu(JMenu sketchMenu) {
    JMenu importMenu = getImportMenu();
    //importMenuIndex = sketchMenu.getComponentZOrder(importMenu);
    importMenuIndex = Toolkit.getMenuItemIndex(sketchMenu, importMenu);
    sketchMenu.remove(importMenu);
  }


  /**
   * Re-insert the Import Library menu. Added function so that other modes
   * need not have an 'import' menu.
   * @since 3.0a6
   * @param sketchMenu the Sketch menu that's currently active
   */
  public void insertImportMenu(JMenu sketchMenu) {
    // hard-coded as 4 in 3.0a5, change to 5 for 3.0a6, but... yuck
    //sketchMenu.insert(mode.getImportMenu(), 4);
    // This is -1 on when the editor window is first shown, but that's fine
    // because the import menu has just been added in the Editor constructor.
    if (importMenuIndex != -1) {
      sketchMenu.insert(getImportMenu(), importMenuIndex);
    }
  }


  public JMenu getImportMenu() {
    if (importMenu == null) {
      rebuildImportMenu();
    }
    return importMenu;
  }


  public void rebuildImportMenu() {  //JMenu importMenu) {
    if (importMenu == null) {
      importMenu = new JMenu(Language.text("menu.library"));
    } else {
      //System.out.println("rebuilding import menu");
      importMenu.removeAll();
    }

    JMenuItem manageLibs = new JMenuItem(Language.text("menu.library.manage_libraries"));
    manageLibs.addActionListener(e -> ContributionManager.openLibraries());
    importMenu.add(manageLibs);
    importMenu.addSeparator();

    rebuildLibraryList();

    ActionListener listener = e -> base.activeEditor.handleImportLibrary(e.getActionCommand());

//    try {
//      pw = new PrintWriter(new FileWriter(System.getProperty("user.home") + "/Desktop/libs.csv"));
//    } catch (IOException e1) {
//      e1.printStackTrace();
//    }

    if (coreLibraries.size() == 0) {
      JMenuItem item = new JMenuItem(getTitle() + " " + Language.text("menu.library.no_core_libraries"));
      item.setEnabled(false);
      importMenu.add(item);

    } else {
      for (Library library : coreLibraries) {
        JMenuItem item = new JMenuItem(library.getName());
        item.addActionListener(listener);

        // changed to library-name to facilitate specification of imports from properties file
        item.setActionCommand(library.getName());

        importMenu.add(item);
      }
    }

    if (contribLibraries.size() != 0) {
      importMenu.addSeparator();
      JMenuItem contrib = new JMenuItem(Language.text("menu.library.contributed"));
      contrib.setEnabled(false);
      importMenu.add(contrib);

      Map<String, JMenu> subfolders = new HashMap<>();

      for (Library library : contribLibraries) {
        JMenuItem item = new JMenuItem(library.getName());
        item.addActionListener(listener);

        // changed to library-name to facilitate specification if imports from properties file
        item.setActionCommand(library.getName());

        String group = library.getGroup();
        if (group != null) {
          JMenu subMenu = subfolders.get(group);
          if (subMenu == null) {
            subMenu = new JMenu(group);
            importMenu.add(subMenu);
            subfolders.put(group, subMenu);
          }
          subMenu.add(item);
        } else {
          importMenu.add(item);
        }
      }
    }
  }


  /**
   * Require examples to explicitly state that they're compatible with this
   * Mode before they're included. Helpful for Modes like p5js or Python
   * where the .java examples cannot be used.
   * @since 3.2
   * @return true if an examples package must list this Mode's identifier
   */
  public boolean requireExampleCompatibility() {
    return false;
  }


  /**
   * Override this to control the order of the first set of example folders
   * and how they appear in the examples window.
   */
  public File[] getExampleCategoryFolders() {
    return examplesFolder.listFiles((dir, name) -> dir.isDirectory() && name.charAt(0) != '.');
  }


  public void rebuildExamplesFrame() {
    if (examplesFrame != null) {
      boolean visible = examplesFrame.isVisible();
      Rectangle bounds = null;
      if (visible) {
        bounds = examplesFrame.getBounds();
        examplesFrame.setVisible(false);
        examplesFrame.dispose();
      }
      examplesFrame = null;
      if (visible) {
        showExamplesFrame();
        examplesFrame.setBounds(bounds);
      }
    }
  }


  public void showExamplesFrame() {
    if (examplesFrame == null) {
      examplesFrame = new ExamplesFrame(base, this);
    }
    examplesFrame.setVisible();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Get an ImageIcon object from the Mode folder.
   * Or when prefixed with /lib, load it from the main /lib folder.
   * @since 3.0a6
   */
  public ImageIcon loadIcon(String filename) {
    if (filename.startsWith("/lib/")) {
      return Toolkit.getLibIcon(filename.substring(5));
    }
    File file = new File(folder, filename);
    if (!file.exists()) {
      return null;
    }
    return new ImageIcon(file.getAbsolutePath());
  }


  /**
   * Get an image object from the mode folder.
   * Or when prefixed with /lib, load it from the main /lib folder.
   */
  public Image loadImage(String filename) {
    ImageIcon icon = loadIcon(filename);
    if (icon != null) {
      return icon.getImage();
    }
    return null;
  }


  public Image loadImageX(String filename) {
    return loadImage(filename + "-" + Toolkit.highResMultiplier() +  "x.png");
  }


  public String loadString(String filename) {
    File file;
    if (filename.startsWith("/lib/")) {
      // remove the slash from the front
      file = Platform.getContentFile(filename.substring(1));
    } else {
      file = new File(folder, filename);
    }
    return Util.loadFile(file);
  }


  /**
   * Returns the HTML filename (including path prefix if necessary)
   * for this keyword, or null if it doesn't exist.
   */
  public String lookupReference(String keyword) {
    return keywordToReference.get(keyword);
  }


  /**
   * Specialized version of getTokenMarker() that can be overridden to
   * provide different TokenMarker objects for different file types.
   * @since 3.2
   * @param code the code for which we need a TokenMarker
   */
  @SuppressWarnings("unused")
  public TokenMarker getTokenMarker(SketchCode code) {
    return getTokenMarker();
  }


  public TokenMarker getTokenMarker() {
    return tokenMarker;
  }


  protected TokenMarker createTokenMarker() {
    return new PdeTokenMarker();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  // Breaking out extension types in order to clean up the code, and make it
  // easier for other environments (like Arduino) to incorporate changes.


  /**
   * True if the specified extension should be hidden when shown on a tab.
   * For Processing, this is true for .pde files. (Broken out for subclasses.)
   * You can override this in your Mode subclass to handle it differently.
   */
  public boolean hideExtension(String ext) {
    return ext.equals(getDefaultExtension());
  }


  /**
   * True if the specified code has the default file extension.
   */
  public boolean isDefaultExtension(SketchCode code) {
    return code.getExtension().equals(getDefaultExtension());
  }


  /**
   * True if the specified extension is the default file extension.
   */
  public boolean isDefaultExtension(String ext) {
    return ext.equals(getDefaultExtension());
  }


  /**
   * True if this Mode can edit this file (usually meaning that
   * its extension matches one that is supported by the Mode).
   */
  public boolean canEdit(final File file) {
    final int dot = file.getName().lastIndexOf('.');
    if (dot < 0) {
      return false;
    }
    return validExtension(file.getName().substring(dot + 1));
  }


  public boolean canEdit(Sketch sketch) {
    for (final SketchCode code : sketch.getCode()) {
      if (!validExtension(code.getExtension())) {
        return false;
      }
    }
    return true;
  }


  /**
   * Check this extension (no dots, please) against the list of valid
   * extensions.
   */
  public boolean validExtension(String what) {
    String[] ext = getExtensions();
    for (String s : ext) {
      if (s.equals(what)) return true;
    }
    return false;
  }


  /**
   * Returns the default extension for this editor setup.
   */
  abstract public String getDefaultExtension();


  /**
   * Returns the appropriate file extension to use for auxiliary source
   * files in a sketch. For example, in a Java-mode sketch, auxiliary files
   * can be named "Foo.java"; in Python mode, they should be named "foo.py".
   * <p/>
   * Modes that do not override this function will get the
   * default behavior of returning the default extension.
   */
  public String getModuleExtension() {
    return getDefaultExtension();
  }


  /**
   * Returns a String[] array of proper extensions.
   */
  abstract public String[] getExtensions();


  /**
   * Get array of file/directory names that needn't be copied during "Save As".
   */
  abstract public String[] getIgnorable();


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Checks coreLibraries and contribLibraries for a library with the specified name
   * @param libName the name of the library to find
   * @return the Library or null if not found
   */
  public Library findLibraryByName(String libName) {
    for (Library lib : this.coreLibraries) {
      if (libName.equals(lib.getName()))
        return lib;
    }
    for (Library lib : this.contribLibraries) {
      if (libName.equals(lib.getName()))
        return lib;
    }
    return null;
  }


  /**
   * Create a fresh application folder if the 'delete target folder'
   * pref has been set in the preferences.
   */
  public void prepareExportFolder(File targetFolder) {
    if (targetFolder != null) {
      // Nuke the old application folder because it can cause trouble
      if (Preferences.getBoolean("export.delete_target_folder")) {
        if (targetFolder.exists()) {
          try {
            Platform.deleteFile(targetFolder);
          } catch (IOException e) {
            // ignore errors/continue; likely to be ok
            e.printStackTrace();
          }
        }
      }
      // Create a fresh output folder (needed before preproc is run next)
      if (!targetFolder.exists()) {
        if (!targetFolder.mkdirs()) {
          Messages.err("Could not create " + targetFolder);
        }
      }
    }
  }


  @Override
  public String toString() {
    return getTitle();
  }
}
