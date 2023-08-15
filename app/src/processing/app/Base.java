/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-23 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License
  version 2, as published by the Free Software Foundation.

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
import java.awt.event.ActionListener;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import processing.app.contrib.*;
import processing.app.tools.Tool;
import processing.app.ui.*;
import processing.app.ui.Toolkit;
import processing.core.*;
import processing.data.StringList;


/**
 * The base class for the main processing application.
 * Primary role of this class is for platform identification and
 * general interaction with the system (launching URLs, loading
 * files and images, etc.) that comes from that.
 */
public class Base {
  // Added accessors for 0218 because the UpdateCheck class was not properly
  // updating the values, due to javac inlining the static final values.
  static private final int REVISION = 1294;
  /** This might be replaced by main() if there's a lib/version.txt file. */
  static private String VERSION_NAME = "1294"; //$NON-NLS-1$

  static final public String SKETCH_BUNDLE_EXT = ".pdez";
  static final public String CONTRIB_BUNDLE_EXT = ".pdex";

  /**
   * True if heavy debugging error/log messages are enabled. Set to true
   * if an empty file named 'debug' is found in the settings folder.
   * See implementation in createAndShowGUI().
   */
  static public boolean DEBUG;

  /** True if running via Commander. */
  static private boolean commandLine;

  /**
   * If settings.txt is present inside lib, it will be used to override
   * the location of the settings folder so that "portable" versions
   * of the software are possible.
   */
  static private File settingsOverride;

  // A single instance of the preferences window
  PreferencesFrame preferencesFrame;

  // Location for untitled items
  static File untitledFolder;

  /** List of currently active editors. */
  final protected List<Editor> editors =
    Collections.synchronizedList(new ArrayList<>());
  protected Editor activeEditor;

  /** A lone file menu to be used when all sketch windows are closed. */
  protected JMenu defaultFileMenu;

  /**
   * The next Mode to be used with handleNew() or handleOpen()
   * (unless it's overridden by something else). Starts with the last
   * Mode used with the environment, or the default mode if not used.
   */
  private Mode nextMode;

  /** Only one built-in Mode these days, removing the extra fluff. */
  private Mode coreMode;

  // TODO can these be Set objects, or are they expected to be in order?
  private List<ModeContribution> contribModes;
  private List<ExamplesContribution> contribExamples;

  /** These aren't even dynamically loaded, they're hard-wired here. */
  private List<Tool> internalTools;

  // TODO can these be Set objects, or are they expected to be in order?
  private List<ToolContribution> coreTools;
  private List<ToolContribution> contribTools;

  /** Current tally of available updates (used for new Editor windows). */
  private int updatesAvailable = 0;

  // Used by handleOpen(), this saves the chooser to remember the directory.
  // Doesn't appear to be necessary with the AWT native file dialog.
  // https://github.com/processing/processing/pull/2366
  private JFileChooser openChooser;

  static protected File sketchbookFolder;


  static public void main(final String[] args) {
    EventQueue.invokeLater(() -> {
      try {
        createAndShowGUI(args);

      } catch (Throwable t) {
        // Windows Defender has been insisting on destroying each new
        // release by removing core.jar and other files. Yay!
        // https://github.com/processing/processing/issues/5537
        if (Platform.isWindows()) {
          String mess = t.getMessage();
          String missing = null;
          if (mess.contains("Could not initialize class com.sun.jna.Native")) {
            //noinspection SpellCheckingInspection
            missing = "jnidispatch.dll";
          } else if (t instanceof NoClassDefFoundError &&
                     mess.contains("processing/core/PApplet")) {
            // Had to change how this was called
            // https://github.com/processing/processing4/issues/154
            missing = "core.jar";
          }
          if (missing != null) {
            Messages.showError("Necessary files are missing",
                               "A file required by Processing (" + missing + ") is missing.\n\n" +
                               "Make sure that you're not trying to run Processing from inside\n" +
                               "the .zip file you downloaded, and check that Windows Defender\n" +
                               "has not removed files from the Processing folder.\n\n" +
                               "(Defender sometimes flags parts of Processing as malware.\n" +
                               "It is not, but Microsoft has ignored our pleas for help.)", t);
          }
        }
        Messages.showTrace("Unknown Problem",
                           "A serious error happened during startup. Please report:\n" +
                           "http://github.com/processing/processing/issues/new", t, true);
      }
    });
  }


  static private void createAndShowGUI(String[] args) {
    // these times are fairly negligible relative to Base.<init>
//    long t1 = System.currentTimeMillis();

    File versionFile = Platform.getContentFile("lib/version.txt");
    if (versionFile != null && versionFile.exists()) {
      String[] lines = PApplet.loadStrings(versionFile);
      if (lines != null && lines.length > 0) {
        if (!VERSION_NAME.equals(lines[0])) {
          VERSION_NAME = lines[0];
        }
      }
    }

    // Detect settings.txt in the lib folder for portable versions
    File settingsFile = Platform.getContentFile("lib/settings.txt");
    if (settingsFile != null && settingsFile.exists()) {
      try {
        Settings portable = new Settings(settingsFile);
        String path = portable.get("settings.path");
        File folder = new File(path);
        boolean success = true;
        if (!folder.exists()) {
          success = folder.mkdirs();
          if (!success) {
            Messages.err("Could not create " + folder + " to store settings.");
          }
        }
        if (success) {
          if (!folder.canRead()) {
            Messages.err("Cannot read from " + folder);
          } else if (!folder.canWrite()) {
            Messages.err("Cannot write to " + folder);
          } else {
            settingsOverride = folder.getAbsoluteFile();
          }
        }
      } catch (IOException e) {
        Messages.err("Error while reading the settings.txt file", e);
      }
    }

    Platform.init();
    // call after Platform.init() because we need the settings folder
    Console.startup();

    // Set the debug flag based on a file being present in the settings folder
    File debugFile = getSettingsFile("debug");

    // If it's a directory, it's a leftover from much older releases
    // (2.x? 3.x?) that wrote DebugMode.log files into this directory.
    // Could remove the directory, but it's harmless enough that it's
    // not worth deleting files in case something could go wrong.
    if (debugFile.exists() && debugFile.isFile()) {
      DEBUG = true;
    }

    // Use native popups to avoid looking crappy on macOS
    JPopupMenu.setDefaultLightWeightPopupEnabled(false);

    // Don't put anything above this line that might make GUI,
    // because the platform has to be inited properly first.

    // Make sure a full JDK is installed
    //initRequirements();

    // Load the languages
    Language.init();

    // run static initialization that grabs all the prefs
    Preferences.init();

//    long t2 = System.currentTimeMillis();

    if (!SingleInstance.alreadyRunning(args)) {
      // Set the look and feel before opening the window
      try {
        Platform.setLookAndFeel();
        Platform.setInterfaceZoom();
      } catch (Exception e) {
        Messages.err("Error while setting up the interface", e); //$NON-NLS-1$
      }

//      long t3 = System.currentTimeMillis();

      // Get the sketchbook path, and make sure it's set properly
      locateSketchbookFolder();

//      long t4 = System.currentTimeMillis();

      // Load colors for UI elements. This must happen after Preferences.init()
      // (so that fonts are set) and locateSketchbookFolder() so that a
      // theme.txt file in the user's sketchbook folder is picked up.
      Theme.init();

      // Create a location for untitled sketches
      try {
        // Users on a shared machine may also share a TEMP folder,
        // which can cause naming collisions; use a UUID as the name
        // for the subfolder to introduce another layer of indirection.
        // https://github.com/processing/processing4/issues/549
        // The UUID also prevents collisions when restarting the
        // software. Otherwise, after using up the a-z naming options
        // it was not possible for users to restart (without manually
        // finding and deleting the TEMP files).
        // https://github.com/processing/processing4/issues/582
        String uuid = UUID.randomUUID().toString();
        untitledFolder = new File(Util.getProcessingTemp(), uuid);

      } catch (IOException e) {
        Messages.showError("Trouble without a name",
                           "Could not create a place to store untitled sketches.\n" +
                           "That's gonna prevent us from continuing.", e);
      }

//      long t5 = System.currentTimeMillis();
//      long t6 = 0;  // replaced below, just needs decl outside try { }

      Messages.log("About to create Base..."); //$NON-NLS-1$
      try {
        final Base base = new Base(args);
        base.updateTheme();
        Messages.log("Base() constructor succeeded");
//        t6 = System.currentTimeMillis();

        // Prevent more than one copy of the PDE from running.
        SingleInstance.startServer(base);

        handleWelcomeScreen(base);
        handleCrustyDisplay();
        handleTempCleaning();

      } catch (Throwable t) {
        // Catch-all to pick up badness during startup.
        Throwable err = t;
        if (t.getCause() != null) {
          // Usually this is the more important piece of information. We'll
          // show this one so that it's not truncated in the error window.
          err = t.getCause();
        }
        Messages.showTrace("We're off on the wrong foot",
                           "An error occurred during startup.", err, true);
      }
      Messages.log("Done creating Base..."); //$NON-NLS-1$

//      long t10 = System.currentTimeMillis();
//      System.out.println("startup took " + (t2-t1) + " " + (t3-t2) + " " + (t4-t3) + " " + (t5-t4) + " " + (t6-t5) + " " + (t10-t6) + " ms");
    }
  }


  public void updateTheme() {
    try {
      //System.out.println("updating theme");
      FlatLaf laf = "dark".equals(Theme.get("laf.mode")) ?
        new FlatDarkLaf() : new FlatLightLaf();
      laf.setExtraDefaults(Collections.singletonMap("@accentColor",
        Theme.get("laf.accent.color")));
      //System.out.println(laf.getExtraDefaults());
      //UIManager.setLookAndFeel(laf);
      FlatLaf.setup(laf);
      // updateUI() will wipe out our custom components
      // even if we do a setUI() call and invalidate/revalidate/repaint
//      FlatLaf.updateUI();
//      System.out.println("FlatLaf.updateUI() should be done");
//      FlatLaf.updateUILater();

    } catch (Exception e) {
      e.printStackTrace();
    }

    if (preferencesFrame != null) {
      preferencesFrame.updateTheme();
    }

    ContributionManager.updateTheme();
    for (Editor editor : getEditors()) {
      editor.updateTheme();

//      Component sb = editor.getPdeTextArea();
//      sb.invalidate();
//      sb.revalidate();
//      sb.repaint();
    }

    /*
    Window[] windows = Window.getWindows();
    for (Window w : windows) {
      SwingUtilities.updateComponentTreeUI(w);
    }
    */
  }


  static private void handleWelcomeScreen(Base base) {
    // Needs to be shown after the first editor window opens, so that it
    // shows up on top, and doesn't prevent an editor window from opening.
    if (Preferences.getBoolean("welcome.four.show")) {
      try {
        new Welcome(base);
      } catch (IOException e) {
        Messages.showTrace("Unwelcoming",
          "Please report this error to\n" +
            "https://github.com/processing/processing4/issues", e, false);
      }
    }
  }


  /**
   * Temporary workaround as we try to sort out
   * <a href="https://github.com/processing/processing4/issues/231">231</a>
   * and <a href="https://github.com/processing/processing4/issues/226">226</a>.
   */
  static private void handleCrustyDisplay() {
    /*
    System.out.println("retina is " + Toolkit.isRetina());
    System.out.println("system zoom " + Platform.getSystemZoom());
    System.out.println("java2d param is " + System.getProperty("sun.java2d.uiScale.enabled"));
    System.out.println("toolkit res is " + java.awt.Toolkit.getDefaultToolkit().getScreenResolution());
    */
    if (Platform.isWindows()) {  // only an issue on Windows
      if (!Toolkit.isRetina() && !Splash.getDisableHiDPI()) {
        int res = java.awt.Toolkit.getDefaultToolkit().getScreenResolution();
        if (res % 96 != 0) {
          // fractional dpi scaling on a low-res screen
          System.out.println("If the editor cursor is in the wrong place or the interface is blocky or fuzzy,");
          System.out.println("open Preferences and select the “Disable HiDPI Scaling” option to fix it.");
        }
      }
    }
  }


  static private void handleTempCleaning() {
    new Thread(() -> {
      Console.cleanTempFiles();
      cleanTempFolders();
    }).start();
  }


  /**
   * Clean folders and files from the Processing subdirectory
   * of the user's temp folder (java.io.tmpdir).
   */
  static public void cleanTempFolders() {
    try {
      final File tempDir = Util.getProcessingTemp();
      final int days = Preferences.getInteger("temp.days");

      if (days > 0) {
        final long now = new Date().getTime();
        final long diff = days * 24 * 60 * 60 * 1000L;
        File[] expiredFiles =
          tempDir.listFiles(file -> (now - file.lastModified()) > diff);
        if (expiredFiles != null) {
          // Remove the files approved for deletion
          for (File file : expiredFiles) {
            try {
              // move to trash or delete if unavailable
              Platform.deleteFile(file);
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * @return the current revision number, safe to be used for update checks
   */
  static public int getRevision() {
    return REVISION;
  }


  /**
   * @return something like 2.2.1 or 3.0b4 (or 0213 if it's not a release)
   */
  static public String getVersionName() {
    return VERSION_NAME;
  }



  public static void setCommandLine() {
    commandLine = true;
  }


  static public boolean isCommandLine() {
    return commandLine;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public Base(String[] args) throws Exception {
    long t1 = System.currentTimeMillis();
    ContributionManager.init(this);

    long t2 = System.currentTimeMillis();
    buildCoreModes();
    long t2b = System.currentTimeMillis();
    rebuildContribModes();
    long t2c = System.currentTimeMillis();
    rebuildContribExamples();

    long t3 = System.currentTimeMillis();
    // Needs to happen after the sketchbook folder has been located.
    // Also relies on the modes to be loaded, so it knows what can be
    // marked as an example.
    Recent.init(this);

    long t4 = System.currentTimeMillis();
    String lastModeIdentifier = Preferences.get("mode.last"); //$NON-NLS-1$
    if (lastModeIdentifier == null) {
      nextMode = getDefaultMode();
      Messages.log("Nothing set for last.sketch.mode, using default."); //$NON-NLS-1$
    } else {
      for (Mode m : getModeList()) {
        if (m.getIdentifier().equals(lastModeIdentifier)) {
          Messages.logf("Setting next mode to %s.", lastModeIdentifier); //$NON-NLS-1$
          nextMode = m;
        }
      }
      if (nextMode == null) {
        nextMode = getDefaultMode();
        Messages.logf("Could not find mode %s, using default.", lastModeIdentifier); //$NON-NLS-1$
      }
    }

    //contributionManagerFrame = new ContributionManagerDialog();

    long t5 = System.currentTimeMillis();

    // Make sure ThinkDifferent has library examples too
    nextMode.rebuildLibraryList();

    // Put this after loading the examples, so that building the default file
    // menu works on Mac OS X (since it needs examplesFolder to be set).
    Platform.initBase(this);

    long t6 = System.currentTimeMillis();

//    // Check if there were previously opened sketches to be restored
//    boolean opened = restoreSketches();
    boolean opened = false;

    // Check if any files were passed in on the command line
    for (int i = 0; i < args.length; i++) {
      Messages.logf("Parsing command line... args[%d] = '%s'", i, args[i]);

      String path = args[i];
      // Fix a problem with systems that use a non-ASCII languages. Paths are
      // being passed in with 8.3 syntax, which makes the sketch loader code
      // unhappy, since the sketch folder naming doesn't match up correctly.
      // https://download.processing.org/bugzilla/1089.html
      if (Platform.isWindows()) {
        try {
          File file = new File(args[i]);
          path = file.getCanonicalPath();
          Messages.logf("Changing %s to canonical %s", i, args[i], path);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (handleOpen(path) != null) {
        opened = true;
      }
    }

    long t7 = System.currentTimeMillis();

    // Create a new empty window (will be replaced with any files to be opened)
    if (!opened) {
      Messages.log("Calling handleNew() to open a new window");
      handleNew();
    } else {
      Messages.log("No handleNew(), something passed on the command line");
    }

    long t8 = System.currentTimeMillis();

    // check for updates
    new UpdateCheck(this);

    ContributionListing cl = ContributionListing.getInstance();
    cl.downloadAvailableList(this, new ContribProgress(null));
    long t9 = System.currentTimeMillis();

    if (DEBUG) {
      System.out.println("core modes: " + (t2b-t2) +
                         ", contrib modes: " + (t2c-t2b) +
                         ", contrib ex: " + (t2c-t2b));
      System.out.println("base took " + (t2-t1) + " " + (t3-t2) + " " + (t4-t3) +
                         " " + (t5-t4) + " t6-t5=" + (t6-t5) + " " + (t7-t6) +
                         " handleNew=" + (t8-t7) + " " + (t9-t8) + " ms");
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Limited file menu to be used on OS X when no sketches are open.
   */
  public JMenu initDefaultFileMenu() {
    defaultFileMenu = new JMenu(Language.text("menu.file"));

    JMenuItem item = Toolkit.newJMenuItem(Language.text("menu.file.new"), 'N');
    item.addActionListener(e -> handleNew());
    defaultFileMenu.add(item);

    item = Toolkit.newJMenuItem(Language.text("menu.file.open"), 'O');
    item.addActionListener(e -> handleOpenPrompt());
    defaultFileMenu.add(item);

    item = Toolkit.newJMenuItemShift(Language.text("menu.file.sketchbook"), 'K');
    item.addActionListener(e -> showSketchbookFrame());
    defaultFileMenu.add(item);

    item = Toolkit.newJMenuItemShift(Language.text("menu.file.examples"), 'O');
    item.addActionListener(e -> thinkDifferentExamples());
    defaultFileMenu.add(item);

    return defaultFileMenu;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Tools require an 'Editor' object when they're instantiated, but
   * the activeEditor will be null when the first Editor that opens is
   * creating its Tools menu. This will temporarily set the activeEditor
   * to the one that's opening so that we don't go all NPE on startup.
   * If there's already an active editor, then this does nothing.
   */
  public void checkFirstEditor(Editor editor) {
    if (activeEditor == null) {
      activeEditor = editor;
    }
  }


  /** Returns the front most, active editor window. */
  public Editor getActiveEditor() {
    return activeEditor;
  }


  /** Get the list of currently active editor windows. */
  public List<Editor> getEditors() {
    return editors;
  }


  /**
   * Called when a window is activated. Because of variations in native
   * windowing systems, no guarantees about changes to the focused and active
   * Windows can be made. Never assume that this Window is the focused or
   * active Window until this Window actually receives a WINDOW_GAINED_FOCUS
   * or WINDOW_ACTIVATED event.
   */
  public void handleActivated(Editor whichEditor) {
    activeEditor = whichEditor;

    // set the current window to be the console that's getting output
    EditorConsole.setEditor(activeEditor);

    // make this the next mode to be loaded
    nextMode = whichEditor.getMode();
    Preferences.set("mode.last", nextMode.getIdentifier()); //$NON-NLS-1$
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public void refreshContribs(ContributionType ct) {
    if (ct == ContributionType.LIBRARY) {
      for (Mode m : getModeList()) {
        m.rebuildImportMenu();
      }

    } else if (ct == ContributionType.MODE) {
      rebuildContribModes();
      for (Editor editor : editors) {
        editor.rebuildModePopup();
      }

    } else if (ct == ContributionType.TOOL) {
      rebuildToolList();
      for (Editor editor : editors) {
        populateToolsMenu(editor.getToolMenu());
      }

    } else if (ct == ContributionType.EXAMPLES) {
      rebuildContribExamples();
      for (Mode m : getModeList()) {
        m.rebuildExamplesFrame();
      }
    }
  }


  /**
   * Get all the contributed Modes, Libraries, Tools, and Examples.
   * Used by the Contribution Manager to report what's installed while
   * checking for updates and available contributions.
   */
  public Set<Contribution> getInstalledContribs() {
    List<ModeContribution> modeContribs = getContribModes();
    Set<Contribution> contributions = new HashSet<>(modeContribs);

    for (ModeContribution modeContrib : modeContribs) {
      Mode mode = modeContrib.getMode();
      contributions.addAll(mode.contribLibraries);
      contributions.addAll(mode.foundationLibraries);
    }

    contributions.addAll(getContribTools());
    contributions.addAll(getContribExamples());
    return contributions;
  }


  public void tallyUpdatesAvailable() {
    // Significant rewrite from previous version seen in
    // https://github.com/processing/processing4/commit/a2e8cd7
    // Also counting all updates, not just those for the current Mode.
    // (Too confusing otherwise, having different counts.)

    Set<Contribution> installed = getInstalledContribs();
    ContributionListing listing = ContributionListing.getInstance();

    int newCount = 0;
    for (Contribution contrib : installed) {
      if (listing.hasUpdates(contrib)) {
        newCount++;
      }
    }
    updatesAvailable = newCount;

    synchronized (editors) {
      for (Editor editor : editors) {
        editor.setUpdatesAvailable(updatesAvailable);
      }
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public List<Mode> getModeList() {
    List<Mode> outgoing = new ArrayList<>();
    outgoing.add(coreMode);
    if (contribModes != null) {
      for (ModeContribution contrib : contribModes) {
        outgoing.add(contrib.getMode());
      }
    }
    return outgoing;
  }


  void buildCoreModes() {
    ModeContribution javaModeContrib =
      ModeContribution.load(this, Platform.getContentFile("modes/java"),
                            getDefaultModeIdentifier());
    if (javaModeContrib == null) {
      Messages.showError("Startup Error",
                "Could not load Java Mode, please reinstall Processing.",
                         new Exception("ModeContribution.load() was null"));

    } else {
      // PDE X calls getModeList() while it's loading, so coreModes must be set
      //coreModes = new Mode[] { javaModeContrib.getMode() };
      coreMode = javaModeContrib.getMode();
    }
  }


  public List<ModeContribution> getContribModes() {
    return contribModes;
  }


  /**
   * Instantiates and adds new contributed modes to the contribModes list.
   * Checks for duplicates so the same mode isn't instantiated twice. Does not
   * remove modes because modes can't be removed once they are instantiated.
   */
  void rebuildContribModes() {
    if (contribModes == null) {
      contribModes = new ArrayList<>();
    }
    File modesFolder = getSketchbookModesFolder();
    Map<File, ModeContribution> known = new HashMap<>();
    for (ModeContribution contrib : getContribModes()) {
      known.put(contrib.getFolder(), contrib);
    }
    File[] potential = ContributionType.MODE.listCandidates(modesFolder);
    // If modesFolder does not exist or is inaccessible (folks might like to
    // mess with folders then report it as a bug) 'potential' will be null.
    if (potential != null) {
      for (File folder : potential) {
        if (!known.containsKey(folder)) {
          try {
            contribModes.add(new ModeContribution(this, folder, null));
          } catch (NoSuchMethodError | NoClassDefFoundError ne) {
            System.err.println(folder.getName() + " is not compatible with this version of Processing");
            if (DEBUG) ne.printStackTrace();
          } catch (InvocationTargetException ite) {
            System.err.println(folder.getName() + " could not be loaded and may not compatible with this version of Processing");
            if (DEBUG) ite.printStackTrace();
          } catch (IgnorableException ig) {
            Messages.log(ig.getMessage());
            if (DEBUG) ig.printStackTrace();
          } catch (Throwable e) {
            System.err.println("Could not load Mode from " + folder);
            e.printStackTrace();
          }
        } else {
          known.remove(folder);  // remove this item as already been seen
        }
      }
    }

    // This allows you to build and test a Mode from Eclipse
    // -Dusemode=com.foo.FrobMode:/path/to/FrobMode
    final String useMode = System.getProperty("usemode");
    if (useMode != null) {
      final String[] modeInfo = useMode.split(":", 2);
      final String modeClass = modeInfo[0];
      final String modeResourcePath = modeInfo[1];
      System.out.println("Attempting to load " + modeClass + " with resources at " + modeResourcePath);
      ModeContribution mc = ModeContribution.load(this, new File(modeResourcePath), modeClass);
      contribModes.add(mc);
      File key = getModeContribFile(mc, known);
      if (key != null) {
        known.remove(key);
      }
    }
    if (known.size() != 0) {
      for (ModeContribution mc : known.values()) {
        System.out.println("Extraneous Mode entry: " + mc.getName());
      }
    }
  }


  static private File getModeContribFile(ModeContribution contrib,
                                         Map<File, ModeContribution> known) {
    for (Entry<File, ModeContribution> entry : known.entrySet()) {
      if (entry.getValue() == contrib) {
        return entry.getKey();
      }
    }
    return null;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public List<ToolContribution> getCoreTools() {
    return coreTools;
  }


  public List<ToolContribution> getContribTools() {
    return contribTools;
  }


  public void rebuildToolList() {
    // Only do these once because the list of internal tools will never change
    if (internalTools == null) {
      internalTools = new ArrayList<>();

      initInternalTool(processing.app.tools.Archiver.class);
      initInternalTool(processing.app.tools.ColorSelector.class);
      initInternalTool(processing.app.tools.CreateFont.class);

      if (Platform.isMacOS()) {
        initInternalTool(processing.app.tools.InstallCommander.class);
      }

      initInternalTool(processing.app.tools.ThemeSelector.class);
    }

    // Only init() these the first time they're loaded
    if (coreTools == null) {
      coreTools = ToolContribution.loadAll(Base.getToolsFolder());
      for (Tool tool : coreTools) {
        tool.init(this);
      }
    }

    // Reset the contributed tools and re-init() all of them.
    contribTools = ToolContribution.loadAll(Base.getSketchbookToolsFolder());
    for (Tool tool : contribTools) {
      try {
        tool.init(this);

        // With the exceptions, we can't call statusError because the window
        // isn't completely set up yet. Also not gonna pop up a warning because
        // people may still be running different versions of Processing.

      } catch (VerifyError | AbstractMethodError ve) {
        System.err.println("\"" + tool.getMenuTitle() + "\" is not " +
                           "compatible with this version of Processing");
        Messages.err("Incompatible Tool found during tool.init()", ve);

      } catch (NoSuchMethodError nsme) {
        System.err.println("\"" + tool.getMenuTitle() + "\" is not " +
                           "compatible with this version of Processing");
        System.err.println("The " + nsme.getMessage() + " method no longer exists.");
        Messages.err("Incompatible Tool found during tool.init()", nsme);

      } catch (NoClassDefFoundError ncdfe) {
        System.err.println("\"" + tool.getMenuTitle() + "\" is not " +
                           "compatible with this version of Processing");
        System.err.println("The " + ncdfe.getMessage() + " class is no longer available.");
        Messages.err("Incompatible Tool found during tool.init()", ncdfe);

      } catch (Error | Exception e) {
        System.err.println("An error occurred inside \"" + tool.getMenuTitle() + "\"");
        e.printStackTrace();
      }
    }
  }


  protected void initInternalTool(Class<?> toolClass) {
    try {
      final Tool tool = (Tool)
        toolClass.getDeclaredConstructor().newInstance();

      tool.init(this);
      internalTools.add(tool);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public void clearToolMenus() {
    for (Editor ed : editors) {
      ed.clearToolMenu();
    }
  }


  public void populateToolsMenu(JMenu toolsMenu) {
    // If this is the first run, need to build out the lists
    if (internalTools == null) {
      rebuildToolList();
    }

    toolsMenu.removeAll();
    for (Tool tool : internalTools) {
      toolsMenu.add(createToolItem(tool));
    }
    toolsMenu.addSeparator();

    if (!coreTools.isEmpty()) {
      for (Tool tool : coreTools) {
        toolsMenu.add(createToolItem(tool));
      }
      toolsMenu.addSeparator();
    }

    if (!contribTools.isEmpty()) {
      for (Tool tool : contribTools) {
        toolsMenu.add(createToolItem(tool));
      }
      toolsMenu.addSeparator();
    }

    JMenuItem manageTools =
      new JMenuItem(Language.text("menu.tools.manage_tools"));
    manageTools.addActionListener(e -> ContributionManager.openTools());
    toolsMenu.add(manageTools);
  }


  JMenuItem createToolItem(final Tool tool) { //, Map<String, JMenuItem> toolItems) {
    String title = tool.getMenuTitle();
    final JMenuItem item = new JMenuItem(title);
    item.addActionListener(e -> {
      try {
        tool.run();

      } catch (NoSuchMethodError | NoClassDefFoundError ne) {
        Messages.showWarning("Tool out of date",
                             tool.getMenuTitle() + " is not compatible with this version of Processing.\n" +
                             "Try updating the Mode or contact its author for a new version.", ne);
        Messages.err("Incompatible tool found during tool.run()", ne);
        item.setEnabled(false);

      } catch (Exception ex) {
        activeEditor.statusError("An error occurred inside \"" + tool.getMenuTitle() + "\"");
        ex.printStackTrace();
        item.setEnabled(false);
      }
    });
    //toolItems.put(title, item);
    return item;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  void rebuildContribExamples() {
    contribExamples =
      ExamplesContribution.loadAll(getSketchbookExamplesFolder());
  }


  public List<ExamplesContribution> getContribExamples() {
    return contribExamples;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  String getDefaultModeIdentifier() {
    // Used to initialize coreModes, so cannot use coreModes[0].getIdentifier()
    return "processing.mode.java.JavaMode";
  }


  public Mode getDefaultMode() {
    //return coreModes[0];
    return coreMode;
  }


  /**
   * @return true if mode is changed within this window (false if new window)
   */
  public boolean changeMode(Mode mode) {
    Mode oldMode = activeEditor.getMode();
    if (oldMode != mode) {
      Sketch sketch = activeEditor.getSketch();
      nextMode = mode;

      if (sketch.isModified()) {
        // If sketch is modified, simpler to just open a new window.
        // https://github.com/processing/processing4/issues/189
        handleNew();
        return false;

      } else if (sketch.isUntitled()) {
        // The current sketch is empty, just close and start fresh.
        // (Otherwise the editor would lose its 'untitled' status.)
        // Safe to do here because of the 'modified' check above.
        handleClose(activeEditor, true);
        handleNew();

      } else {
        // If the current sketch contains file extensions that the new Mode
        // can handle, then write a sketch.properties file with that Mode
        // specified, and reopen. Currently, only used for Java <-> Android.
        if (mode.canEdit(sketch)) {
          //final File props = new File(sketch.getFolder(), "sketch.properties");
          //saveModeSettings(props, nextMode);
          sketch.updateModeProperties(nextMode, getDefaultMode());
          handleClose(activeEditor, true);
          Editor editor = handleOpen(sketch.getMainPath());
          if (editor == null) {
            // the Mode change failed (probably code that's out of date)
            // re-open the sketch using the mode we were in before
            //saveModeSettings(props, oldMode);
            sketch.updateModeProperties(oldMode, getDefaultMode());
            handleOpen(sketch.getMainPath());
            return false;
          }
        } else {
          handleNew();  // create a new window with the new Mode
          return false;
        }
      }
    }
    // Against all (or at least most) odds, we were able to reassign the Mode
    return true;
  }


  protected Mode findMode(String id) {
    for (Mode mode : getModeList()) {
      if (mode.getIdentifier().equals(id)) {
        return mode;
      }
    }
    return null;
  }


  /**
   * Called when a Mode is uninstalled, in case it's the current Mode.
   */
  public void modeRemoved(Mode mode) {
    if (nextMode == mode) {
      nextMode = getDefaultMode();
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Create a new untitled document in a new sketch window.
   */
  public void handleNew() {
//    long t1 = System.currentTimeMillis();
    try {
      // In 0126, untitled sketches will begin in the temp folder,
      // and then moved to a new location because Save will default to Save As.
      //File sketchbookDir = getSketchbookFolder();
      File newbieDir = SketchName.nextFolder(untitledFolder);

      // User was told to go outside or other problem happened inside naming.
      if (newbieDir == null) return;

      // Make the directory for the new sketch
      if (!newbieDir.mkdirs()) {
        throw new IOException("Could not create directory " + newbieDir);
      }

      // Retrieve the sketch name from the folder name (not a great
      // assumption for the future, but overkill to do otherwise for now.)
      String newbieName = newbieDir.getName();

      // Add any template files from the Mode itself
      File newbieFile = nextMode.addTemplateFiles(newbieDir, newbieName);

      // Create sketch properties file if it's not the default mode.
      if (!nextMode.equals(getDefaultMode())) {
        Sketch.updateModeProperties(newbieDir, nextMode, getDefaultMode());
      }

      String path = newbieFile.getAbsolutePath();
      handleOpenUntitled(path);

    } catch (IOException e) {
      Messages.showTrace("That's new to me",
                         "A strange and unexplainable error occurred\n" +
                         "while trying to create a new sketch.", e, false);
    }
  }


  /**
   * Prompt for a sketch to open, and open it in a new window.
   */
  public void handleOpenPrompt() {
    final StringList extensions = new StringList();

    // Add support for pdez files
    extensions.append(SKETCH_BUNDLE_EXT);

    // Add the extensions for each installed Mode
    for (Mode mode : getModeList()) {
      extensions.append(mode.getDefaultExtension());
      // not adding aux extensions b/c we're looking for the main
    }

    final String prompt = Language.text("open");

    if (Preferences.getBoolean("chooser.files.native")) {  //$NON-NLS-1$
      // use the front-most window frame for placing file dialog
      FileDialog openDialog =
        new FileDialog(activeEditor, prompt, FileDialog.LOAD);

      // Only show .pde files as eligible bachelors
      openDialog.setFilenameFilter((dir, name) -> {
        // confirmed to be working properly [fry 110128]
        for (String ext : extensions) {
          if (name.toLowerCase().endsWith("." + ext)) { //$NON-NLS-1$
            return true;
          }
        }
        return false;
      });

      openDialog.setVisible(true);

      String directory = openDialog.getDirectory();
      String filename = openDialog.getFile();
      if (filename != null) {
        File inputFile = new File(directory, filename);
        handleOpen(inputFile.getAbsolutePath());
      }

    } else {
      if (openChooser == null) {
        openChooser = new JFileChooser();
        openChooser.setDialogTitle(prompt);
      }

      openChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
        public boolean accept(File file) {
          // JFileChooser requires you to explicitly say yes to directories
          // as well (unlike the AWT chooser). Useful, but... different.
          // https://github.com/processing/processing/issues/1189
          if (file.isDirectory()) {
            return true;
          }
          for (String ext : extensions) {
            if (file.getName().toLowerCase().endsWith("." + ext)) { //$NON-NLS-1$
              return true;
            }
          }
          return false;
        }

        public String getDescription() {
          return "Processing Sketch";
        }
      });
      if (openChooser.showOpenDialog(activeEditor) == JFileChooser.APPROVE_OPTION) {
        handleOpen(openChooser.getSelectedFile().getAbsolutePath());
      }
    }
  }


  private Editor openSketchBundle(String path) {
    File zipFile = new File(path);
    try {
      File destFolder = File.createTempFile("zip", "tmp", untitledFolder);
      if (!destFolder.delete() || !destFolder.mkdirs()) {
        // Hard to imagine why this would happen, but...
        System.err.println("Could not create temporary folder " + destFolder);
        return null;
      }
      Util.unzip(zipFile, destFolder);
      File[] fileList = destFolder.listFiles(File::isDirectory);
      if (fileList != null) {
        if (fileList.length == 1) {
          File sketchFile = Sketch.findMain(fileList[0], getModeList());
          if (sketchFile != null) {
            return handleOpenUntitled(sketchFile.getAbsolutePath());
          }
        } else {
          System.err.println("Expecting one folder inside " +
            SKETCH_BUNDLE_EXT + " file, found " + fileList.length + ".");
        }
      } else {
        System.err.println("Could not read " + destFolder);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;  // no luck
  }


  private void openContribBundle(String path) {
    EventQueue.invokeLater(() -> {
      Editor editor = getActiveEditor();
      if (editor == null) {
        // Shouldn't really happen, but if it's still null, it's a no-go
        Messages.showWarning("Failure is the only option",
            "Please open an Editor window before installing an extension.");
      } else {
        File contribFile = new File(path);
        String baseName = contribFile.getName();
        baseName = baseName.substring(0, baseName.length() - CONTRIB_BUNDLE_EXT.length());
        int result =
          Messages.showYesNoQuestion(editor, "How to Handle " + CONTRIB_BUNDLE_EXT,
          "Install " + baseName + "?",
          "Libraries, Modes, and Tools should<br>" +
          "only be installed from trusted sources.");

        if (result == JOptionPane.YES_OPTION) {
          editor.statusNotice("Installing " + baseName + "...");
          editor.startIndeterminate();

          new Thread(() -> {
            try {
              // do the work of the actual install
              LocalContribution contrib =
                AvailableContribution.install(this, new File(path));

              EventQueue.invokeLater(() -> {
                editor.stopIndeterminate();

                if (contrib != null) {
                  editor.statusEmpty();
                } else {
                  editor.statusError("Could not install " + path);
                }
              });
            } catch (IOException e) {
              EventQueue.invokeLater(() ->
                  Messages.showWarning("Exception During Installation",
                      "Could not install contrib from " + path, e));
            }
          }).start();
        }
      }
    });
  }


  /**
   * Return true if it's an obvious sketch folder: only .pde files,
   * and maybe a data folder. Dot files (.DS_Store, ._blah) are ignored.
   */
  private boolean smellsLikeSketchFolder(File folder) {
    File[] files = folder.listFiles();
    if (files == null) {  // unreadable, assume badness
      return false;
    }
    for (File file : files) {
      String name = file.getName();
      if (!(name.startsWith(".") ||
            name.toLowerCase().endsWith(".pde")) ||
            (file.isDirectory() && name.equals("data"))) {
        return false;
      }
    }
    return true;
  }


  private File moveLikeSketchFolder(File pdeFile, String baseName) throws IOException {
    Object[] options = {
      "Keep", "Move", "Cancel"
    };
    String prompt =
      "Would you like to keep “" + pdeFile.getParentFile().getName() + "” as the sketch folder,\n" +
      "or move “" + pdeFile.getName() + "” to its own folder?\n" +
      "(Usually, “" + pdeFile.getName() + "” would be stored inside a\n" +
      "sketch folder named “" + baseName + "”.)";

    int result = JOptionPane.showOptionDialog(null,
      prompt,
      "Keep it? Move it?",
      JOptionPane.YES_NO_CANCEL_OPTION,
      JOptionPane.QUESTION_MESSAGE,
      null,
      options,
      options[0]);

    if (result == JOptionPane.YES_OPTION) {  // keep
      return pdeFile;

    } else if (result == JOptionPane.NO_OPTION) {  // move
      // create properly named folder
      File properFolder = new File(pdeFile.getParent(), baseName);
      if (properFolder.exists()) {
        throw new IOException("A folder named \"" + baseName + "\" " +
          "already exists. Cannot open sketch.");
      }
      if (!properFolder.mkdirs()) {
        throw new IOException("Could not create the sketch folder.");
      }
      // copy the sketch inside
      File properPdeFile = new File(properFolder, pdeFile.getName());
      Util.copyFile(pdeFile, properPdeFile);

      // remove the original file, so user doesn't get confused
      if (!pdeFile.delete()) {
        Messages.err("Could not delete " + pdeFile);
      }

      // update with the new path
      return properPdeFile;
    }

    // Catch all other cases, including Cancel or ESC
    return null;
  }


  /**
   * Handler for pde:// protocol URIs
   * @param schemeUri the full URI, including pde://
   */
  public Editor handleScheme(String schemeUri) {
    String location = schemeUri.substring(6);
    if (location.length() > 0) {
      // if it leads with a slash, then it's a file url
      if (location.charAt(0) == '/') {
        File file = new File(location);
        if (file.exists()) {
          handleOpen(location);  // it's a full path to a file
        } else {
          System.err.println(file + " does not exist.");
        }
      } else {
        // turn it into an https url
        final String url = "https://" + location;
        if (location.toLowerCase().endsWith(".pdez") ||
          location.toLowerCase().endsWith(".pdex")) {
          String extension = location.substring(location.length() - 5);
          try {
            File tempFile = File.createTempFile("scheme", extension);
            if (PApplet.saveStream(tempFile, Util.createInput(url))) {
              return handleOpen(tempFile.getAbsolutePath());
            } else {
              System.err.println("Could not open " + tempFile);
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
    return null;
  }


  /**
   * Open a sketch from the path specified. Do not use for untitled sketches.
   * Note that the user may have selected/double-clicked any .pde in a sketch.
   */
  public Editor handleOpen(String path) {
    if (path.startsWith("pde://")) {
      return handleScheme(path);
    }

    if (path.endsWith(SKETCH_BUNDLE_EXT)) {
      return openSketchBundle(path);

    } else if (path.endsWith(CONTRIB_BUNDLE_EXT)) {
      openContribBundle(path);
      return null;  // never returning an Editor for a contrib
    }

    File pdeFile = new File(path);
    if (!pdeFile.exists()) {
      System.err.println(path + " does not exist");
      return null;
    }

    // Cycle through open windows to make sure that it's not already open.
    for (Editor editor : editors) {
      // User may have double-clicked any PDE in the sketch folder,
      // so we have to check each open tab (not just the main one).
      // https://github.com/processing/processing/issues/2506
      for (SketchCode tab : editor.getSketch().getCode()) {
        if (tab.getFile().equals(pdeFile)) {
          editor.toFront();
          // move back to the top of the recent list
          Recent.append(editor);
          return editor;
        }
      }
    }

    File parentFolder = pdeFile.getParentFile();

    try {
      // read the sketch.properties file or get an empty Settings object
      Settings props = Sketch.loadProperties(parentFolder);
      if (!props.isEmpty()) {
        // First check for the Mode, because it may not even be available
        String modeIdentifier = props.get("mode.id");
        if (modeIdentifier != null) {
          if (modeIdentifier.equals("galsasson.mode.tweak.TweakMode")) {
            // Tweak Mode has been built into Processing since 2015,
            // but there are some old sketch.properties files out there.
            // https://github.com/processing/processing4/issues/415
            nextMode = getDefaultMode();

            // Clean up sketch.properties and re-save or remove if necessary
            props.remove("mode");
            props.remove("mode.id");
            props.reckon();

          } else {
            // sketch.properties specifies a Mode, see if it's available
            Mode mode = findMode(modeIdentifier);
            if (mode != null) {
              nextMode = mode;
            } else {
              ContributionManager.openModes();
              Messages.showWarning("Missing Mode",
                "You must first install " + props.get("mode") + " Mode to use this sketch.");
              return null;
            }
          }
        }

        String main = props.get("main");
        if (main != null) {
          // this may be exactly what was passed, but override anyway
          String mainPath = new File(parentFolder, main).getAbsolutePath();
          if (!path.equals(mainPath)) {
            // for now, post a warning if the main was different
            System.out.println(path + " selected, but main is " + mainPath);
          }
          return handleOpenInternal(mainPath, false);
        }
      } else {
        // Switch back to defaultMode, because a sketch.properties
        // file is required whenever not using the default Mode.
        // (Unless being called from, say, the Examples frame, which
        // uses a version of this function that takes a Mode object.)
        nextMode = getDefaultMode();
      }

      // Do some checks to make sure the file can be opened, and identify the
      // Mode that it's using. (In 4.0b8, this became the fall-through case.)

      if (!Sketch.isSanitaryName(pdeFile.getName())) {
        Messages.showWarning("You're tricky, but not tricky enough",
          pdeFile.getName() + " is not a valid name for sketch code.\n" +
          "Better to stick to ASCII, no spaces, and make sure\n" +
          "it doesn't start with a number.", null);
        return null;
      }

      // Check if the name of the file matches the parent folder name.
      String baseName = pdeFile.getName();
      int dot = baseName.lastIndexOf('.');
      if (dot == -1) {
        // Shouldn't really be possible, right?
        System.err.println(pdeFile + " does not have an extension.");
        return null;
      }
      baseName = baseName.substring(0, dot);
      if (!baseName.equals(parentFolder.getName())) {
        // Parent folder name does not match, and because sketch.properties
        // did not exist or did not specify it above, need to determine main.

        // Check whether another .pde file has a matching name, and if so,
        // switch to using that instead. Handles when a user selects a .pde
        // file in the open dialog box that isn't the main tab.
        // (Also important to use nextMode here, because the Mode
        // may be set by sketch.properties when it's loaded above.)
        String filename =
          parentFolder.getName() + "." + nextMode.getDefaultExtension();
        File mainFile = new File(parentFolder, filename);
        if (mainFile.exists()) {
          // User was opening the wrong file in a legit sketch folder.
          pdeFile = mainFile;

        } else if (smellsLikeSketchFolder(parentFolder)) {
          // Looks like a sketch folder, set this as the main.
          props.set("main", pdeFile.getName());
          // Save for later use, then fall through.
          props.save();

        } else {
          // If it's not an obvious sketch folder (just .pde files,
          // maybe a data folder) prompt the user whether to
          // 1) move sketch into its own folder, or
          // 2) call this the main, and write sketch.properties.
          File newFile = moveLikeSketchFolder(pdeFile, baseName);

          if (newFile == pdeFile) {
            // User wanted to keep this sketch folder, so update the
            // property for the main tab and write sketch.properties.
            props.set("main", newFile.getName());
            props.save();

          } else if (newFile == null) {
            // User canceled, so exit handleOpen()
            return null;

          } else {
            // User asked to move the sketch file
            pdeFile = newFile;
          }
        }
      }

      // TODO Remove this selector? Seems too messy/precious. [fry 220205]
      //      Opting to remove in beta 7, because sketches that use another
      //      Mode should have a working sketch.properties. [fry 220302]
      /*
      // If the current Mode cannot open this file, try to find another.
      if (!nextMode.canEdit(pdeFile)) {

        final Mode mode = promptForMode(pdeFile);
        if (mode == null) {
          return null;
        }
        nextMode = mode;
      }
      */
      return handleOpenInternal(pdeFile.getAbsolutePath(), false);

    } catch (IOException e) {
      Messages.showWarning("sketch.properties",
        "Error while reading sketch.properties from\n" + parentFolder, e);
      return null;
    }
  }


  /**
   * Open a (vetted) sketch location using a particular Mode. Used by the
   * Examples window, because Modes like Python and Android do not have
   * "sketch.properties" files in each example folder.
   */
  public Editor handleOpenExample(String path, Mode mode) {
    nextMode = mode;
    return handleOpenInternal(path, true);
  }


  /**
   * Open the sketch associated with this .pde file in a new window
   * as an "Untitled" sketch.
   * @param path Path to the pde file for the sketch in question
   * @return the Editor object, so that properties (like 'untitled')
   *         can be set by the caller
   */
  protected Editor handleOpenUntitled(String path) {
    return handleOpenInternal(path, true);
  }


  /**
   * Internal function to actually open the sketch. At this point, the
   * sketch file/folder must have been vetted, and nextMode set properly.
   */
  protected Editor handleOpenInternal(String path, boolean untitled) {
    try {
      try {
        EditorState state = EditorState.nextEditor(editors);
        Editor editor = nextMode.createEditor(this, path, state);

        // opened successfully, let's go to work
        editor.setUpdatesAvailable(updatesAvailable);
        editor.getSketch().setUntitled(untitled);
        editors.add(editor);
        Recent.append(editor);

        // now that we're ready, show the window
        // (don't do earlier, cuz we might move it based on a window being closed)
        editor.setVisible(true);

        return editor;

      } catch (EditorException ee) {
        if (ee.getMessage() != null) {  // null if the user canceled
          Messages.showWarning("Error opening sketch", ee.getMessage(), ee);
        }
      } catch (NoSuchMethodError me) {
        Messages.showWarning("Mode out of date",
                             nextMode.getTitle() + " is not compatible with this version of Processing.\n" +
                             "Try updating the Mode or contact its author for a new version.", me);
      } catch (Throwable t) {
        if (nextMode.equals(getDefaultMode())) {
          Messages.showTrace("Serious Problem",
                             "An unexpected, unknown, and unrecoverable error occurred\n" +
                             "while opening a new editor window. Please report this.", t, true);
        } else {
          Messages.showTrace("Mode Problems",
                             "A nasty error occurred while trying to use “" + nextMode.getTitle() + "”.\n" +
                             "It may not be compatible with this version of Processing.\n" +
                             "Try updating the Mode or contact its author for a new version.", t, false);
        }
      }
      if (editors.isEmpty()) {
        Mode defaultMode = getDefaultMode();
        if (nextMode == defaultMode) {
          // unreachable? hopefully?
          Messages.showError("Editor Problems", """
              An error occurred while trying to change modes.
              We'll have to quit for now because it's an
              unfortunate bit of indigestion with the default Mode.
              """, null);
        } else {
          // Don't leave the user hanging or the PDE locked up
          // https://github.com/processing/processing/issues/4467
          if (untitled) {
            nextMode = defaultMode;
            handleNew();
            return null;  // ignored by any caller

          } else {
            // This null response will be kicked back to changeMode(),
            // signaling it to re-open the sketch in the default Mode.
            return null;
          }
        }
      }
    } catch (Throwable t) {
      Messages.showTrace("Terrible News",
                         "A serious error occurred while " +
                         "trying to create a new editor window.", t,
                         nextMode == getDefaultMode());  // quit if default
      nextMode = getDefaultMode();
    }
    return null;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Close a sketch as specified by its editor window.
   * @param editor Editor object of the sketch to be closed.
   * @param preventQuit For platforms that must have a window open,
   *                    prevent a quit because a new window will be opened
   *                    (i.e. when upgrading or changing the Mode)
   * @return true if succeeded in closing, false if canceled.
   */
  public boolean handleClose(Editor editor, boolean preventQuit) {
    if (!editor.checkModified()) {
      return false;
    }

    // Close the running window, avoid window boogers with multiple sketches
    editor.internalCloseRunner();

    if (editors.size() == 1) {
      if (Platform.isMacOS()) {
        // If the central menu bar isn't supported on this macOS JVM,
        // we have to do the old behavior. Yuck!
        if (defaultFileMenu == null) {
          Object[] options = { Language.text("prompt.ok"), Language.text("prompt.cancel") };

          int result = JOptionPane.showOptionDialog(editor,
            Toolkit.formatMessage("Are you sure you want to Quit?",
                "Closing the last open sketch will quit Processing."),
            "Quit",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
          if (result == JOptionPane.NO_OPTION ||
              result == JOptionPane.CLOSED_OPTION) {
            return false;
          }
        }
      }

      if (defaultFileMenu == null) {
        if (preventQuit) {
          // need to close this editor, ever so temporarily
          editor.setVisible(false);
          editor.dispose();
          activeEditor = null;
          editors.remove(editor);
        } else {
          // Since this wasn't an actual Quit event, call System.exit()
          System.exit(0);
        }
      } else {  // on OS X, update the default file menu
        editor.setVisible(false);
        editor.dispose();
        defaultFileMenu.insert(Recent.getMenu(), 2);
        activeEditor = null;
        editors.remove(editor);
      }

    } else {
      // More than one editor window open,
      // proceed with closing the current window.
      editor.setVisible(false);
      editor.dispose();
      editors.remove(editor);
    }
    return true;
  }


  /**
   * Handler for File &rarr; Quit. Note that this is *only* for the
   * File menu. On macOS, it will not call System.exit() because the
   * application will handle that. If calling this from elsewhere,
   * you'll need a System.exit() call on macOS.
   * @return false if canceled, true otherwise.
   */
  public boolean handleQuit() {
    // If quit is canceled, this will be replaced anyway
    // by a later handleQuit() that is not canceled.
//    storeSketches();

    if (handleQuitEach()) {
      // make sure running sketches close before quitting
      for (Editor editor : editors) {
        editor.internalCloseRunner();
      }
      // Save out the current prefs state
      Preferences.save();

      // Finished with this guy
      Console.shutdown();

      if (!Platform.isMacOS()) {
        // If this was fired from the menu or an AppleEvent (the Finder),
        // then Mac OS X will send the terminate signal itself.
        System.exit(0);
      }
      return true;
    }
    return false;
  }


  /**
   * Attempt to close each open sketch in preparation for quitting.
   * @return false if canceled along the way
   */
  protected boolean handleQuitEach() {
//    int index = 0;
    for (Editor editor : editors) {
//      if (editor.checkModified()) {
//        // Update to the new/final sketch path for this fella
//        storeSketchPath(editor, index);
//        index++;
//
//      } else {
//        return false;
//      }
      if (!editor.checkModified()) {
        return false;
      }
    }
    return true;
  }


  public void handleRestart() {
    File app = Platform.getProcessingApp();
    System.out.println(app);
    if (app.exists()) {
      if (handleQuitEach()) {  // only if everything saved
        SingleInstance.clearRunning();

        // Launch on quit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          try {
            //Runtime.getRuntime().exec(app.getAbsolutePath());
            System.out.println("launching");
            Process p;
            if (Platform.isMacOS()) {
              p = Runtime.getRuntime().exec(new String[] {
                // -n allows more than one instance to be opened at a time
                "open", "-n", "-a", app.getAbsolutePath()
              });
            } else if (Platform.isLinux()) {
              p = Runtime.getRuntime().exec(new String[] {
                app.getAbsolutePath()
              });
            } else {
              p = Runtime.getRuntime().exec(new String[] {
                "cmd", "/c", app.getAbsolutePath()
              });
            }
            System.out.println("launched with result " + p.waitFor());
            System.out.flush();
          } catch (Exception e) {
            e.printStackTrace();
          }
        }));
        handleQuit();
        // handleQuit() does not call System.exit() on macOS
        if (Platform.isMacOS()) {
          System.exit(0);
        }
      }
    } else {
      Messages.showWarning("Cannot Restart",
        "Cannot automatically restart because the Processing\n" +
        "application has been renamed. Please quit and then restart manually.");
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


//  /**
//   * Asynchronous version of menu rebuild to be used on save and rename
//   * to prevent the interface from locking up until the menus are done.
//   */
//  protected void rebuildSketchbookMenusAsync() {
//    EventQueue.invokeLater(this::rebuildSketchbookMenus);
//  }


  public void thinkDifferentExamples() {
    nextMode.showExamplesFrame();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  protected SketchbookFrame sketchbookFrame;

  public DefaultMutableTreeNode buildSketchbookTree() {
    DefaultMutableTreeNode sbNode =
      new DefaultMutableTreeNode(Language.text("sketchbook.tree"));
    try {
      addSketches(sbNode, Base.getSketchbookFolder(), false);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return sbNode;
  }


  /** Sketchbook has changed, update it on next viewing. */
  public void rebuildSketchbookFrame() {
    if (sketchbookFrame != null) {
      sketchbookFrame.rebuild();
      /*
      boolean visible = sketchbookFrame.isVisible();
      Rectangle bounds = null;
      if (visible) {
        bounds = sketchbookFrame.getBounds();
        sketchbookFrame.setVisible(false);
        sketchbookFrame.dispose();
      }
      sketchbookFrame = null;
      if (visible) {
        showSketchbookFrame();
        sketchbookFrame.setBounds(bounds);
      }
      */
    }
  }


  public void showSketchbookFrame() {
    if (sketchbookFrame == null) {
      sketchbookFrame = new SketchbookFrame(this);
    }
    sketchbookFrame.setVisible();
  }


  /**
   * Synchronous version of rebuild, used when the sketchbook folder has
   * changed, so that the libraries are properly re-scanned before those menus
   * (and the examples window) are rebuilt.
   */
  public void rebuildSketchbook() {
    for (Mode mode : getModeList()) {
      mode.rebuildImportMenu();  // calls rebuildLibraryList
      mode.rebuildToolbarMenu();
      mode.rebuildExamplesFrame();
    }
    // Unlike libraries, examples, etc. the sketchbook is global
    // (because you need to be able to open sketches from the Mode
    // that you're not currently using).
    rebuildSketchbookFrame();
  }


  public void populateSketchbookMenu(JMenu menu) {
    boolean found = false;
    try {
      found = addSketches(menu, sketchbookFolder);
    } catch (Exception e) {
      Messages.showWarning("Sketchbook Menu Error",
                           "An error occurred while trying to list the sketchbook.", e);
    }
    if (!found) {
      JMenuItem empty = new JMenuItem(Language.text("menu.file.sketchbook.empty"));
      empty.setEnabled(false);
      menu.add(empty);
    }
  }


  /**
   * Scan a folder recursively, and add any sketches found to the menu
   * specified. Set the openReplaces parameter to true when opening the sketch
   * should replace the sketch in the current window, or false when the
   * sketch should open in a new window.
   */
  protected boolean addSketches(JMenu menu, File folder) {
    // skip .DS_Store files, etc. (this shouldn't actually be necessary)
    if (!folder.isDirectory()) {
      return false;
    }

    if (folder.getName().equals("libraries")) {
      return false;  // let's not go there
    }

    if (folder.getName().equals("sdk")) {
      // This could be Android's SDK folder. Let's double-check:
      File suspectSDKPath = new File(folder.getParent(), folder.getName());
      File expectedSDKPath = new File(sketchbookFolder, "android" + File.separator + "sdk");
      if (expectedSDKPath.getAbsolutePath().equals(suspectSDKPath.getAbsolutePath())) {
        return false;  // Most likely the SDK folder, skip it
      }
    }

    String[] list = folder.list();
    // If a bad folder or unreadable or whatever, this will come back null
    if (list == null) {
      return false;
    }

    // Alphabetize the list, since it's not always alpha order
    Arrays.sort(list, String.CASE_INSENSITIVE_ORDER);

    ActionListener listener = e -> {
      String path = e.getActionCommand();
      if (new File(path).exists()) {
        handleOpen(path);
      } else {
        Messages.showWarning("Sketch Disappeared","""
            The selected sketch no longer exists.
            You may need to restart Processing to update
            the sketchbook menu.""", null);
      }
    };
    // offers no speed improvement
    //menu.addActionListener(listener);

    boolean found = false;

    for (String name : list) {
      if (name.charAt(0) == '.') {
        continue;
      }

      // TODO Is this necessary any longer? This seems gross [fry 210804]
      if (name.equals("old")) {  // Don't add old contributions
        continue;
      }

      File entry = new File(folder, name);
      File sketchFile = null;
      if (entry.isDirectory()) {
        sketchFile = Sketch.findMain(entry, getModeList());
      } else if (name.toLowerCase().endsWith(SKETCH_BUNDLE_EXT)) {
        name = name.substring(0, name.length() - SKETCH_BUNDLE_EXT.length());
        sketchFile = entry;
      }

      if (sketchFile != null) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(listener);
        item.setActionCommand(sketchFile.getAbsolutePath());
        menu.add(item);
        found = true;

      } else if (entry.isDirectory()) {
        // not a sketch folder, but may be a subfolder containing sketches
        JMenu submenu = new JMenu(name);
        // needs to be separate var otherwise would set found to false
        boolean anything = addSketches(submenu, entry);
        if (anything) {
          menu.add(submenu);
          found = true;
        }
      }
    }
    return found;
  }


  /**
   * Mostly identical to the JMenu version above, however the rules are
   * slightly different for how examples are handled, etc.
   */
  public boolean addSketches(DefaultMutableTreeNode node, File folder,
                             boolean examples) throws IOException {
    // skip .DS_Store files, etc. (this shouldn't actually be necessary)
    if (!folder.isDirectory()) {
      return false;
    }

    final String folderName = folder.getName();

    // Don't look inside the 'libraries' folders in the sketchbook
    if (folderName.equals("libraries")) {
      return false;
    }

    // When building the sketchbook, don't show the contributed 'examples'
    // like it's a subfolder. But when loading examples, allow the folder
    // to be named 'examples'.
    if (!examples && folderName.equals("examples")) {
      return false;
    }

//    // Conversely, when looking for examples, ignore the other folders
//    // (to avoid going through hoops with the tree node setup).
//    if (examples && !folderName.equals("examples")) {
//      return false;
//    }
//    // Doesn't quite work because the parent will be 'examples', and we want
//    // to walk inside that, but the folder itself will have a different name

    String[] fileList = folder.list();
    // If a bad folder or unreadable or whatever, this will come back null
    if (fileList == null) {
      return false;
    }

    // Alphabetize the list, since it's not always alpha order
    Arrays.sort(fileList, String.CASE_INSENSITIVE_ORDER);

    boolean found = false;
    for (String name : fileList) {
      if (name.charAt(0) == '.') {  // Skip hidden files
        continue;
      }

      File entry = new File(folder, name);
      File sketchFile = null;
      if (entry.isDirectory()) {
        sketchFile = Sketch.findMain(entry, getModeList());
      } else if (name.toLowerCase().endsWith(SKETCH_BUNDLE_EXT)) {
        name = name.substring(0, name.length() - SKETCH_BUNDLE_EXT.length());
        sketchFile = entry;
      }

      if (sketchFile != null) {
        DefaultMutableTreeNode item =
          new DefaultMutableTreeNode(new SketchReference(name, sketchFile));

        node.add(item);
        found = true;

      } else if (entry.isDirectory()) {
        // not a sketch folder, but maybe a subfolder containing sketches
        DefaultMutableTreeNode subNode = new DefaultMutableTreeNode(name);
        // needs to be separate var otherwise would set found to false
        boolean anything = addSketches(subNode, entry, examples);
        if (anything) {
          node.add(subNode);
          found = true;
        }
      }
    }
    return found;
  }


  /*
  static private Mode findSketchMode(File folder, List<Mode> modeList) {
    try {
      Settings props = Sketch.loadProperties(folder);
      if (props != null) {
        String id = props.get("mode.id");
        if (id != null) {
          Mode mode = findMode(id);
          if (mode != null) {
            return mode;
          }
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
        return mode;
      }
    }
    return null;
  }
  */


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Show the Preferences window.
   */
  public void handlePrefs() {
    if (preferencesFrame == null) {
      preferencesFrame = new PreferencesFrame(this);
    }
    preferencesFrame.showFrame();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Return a File from inside the Processing 'lib' folder.
   */
  @SuppressWarnings("RedundantThrows")
  static public File getLibFile(String filename) throws IOException {
    return new File(Platform.getContentFile("lib"), filename);
  }


  /**
   * Return an InputStream for a file inside the Processing lib folder.
   */
  static public InputStream getLibStream(String filename) throws IOException {
    return new FileInputStream(getLibFile(filename));
  }


  /**
   * Get the directory that can store settings. (Library on OS X, App Data or
   * something similar on Windows, a dot folder on Linux.) Removed this as a
   * preference for 3.0a3 because we need this to be stable, but adding back
   * for 4.0 beta 4 so that folks can do 'portable' versions again.
   */
  static public File getSettingsFolder() {
    File settingsFolder = null;

    try {
      settingsFolder = Platform.getSettingsFolder();

      // create the folder if it doesn't exist already
      if (!settingsFolder.exists()) {
        if (!settingsFolder.mkdirs()) {
          Messages.showError("Settings issues",
                             "Processing cannot run because it could not\n" +
                             "create a folder to store your settings at\n" +
                             settingsFolder, null);
        }
      }
    } catch (Exception e) {
      Messages.showTrace("An rare and unknowable thing happened",
                         "Could not get the settings folder. Please report:\n" +
                         "http://github.com/processing/processing/issues/new",
                         e, true);
    }
    return settingsFolder;
  }


  static public File getSettingsOverride() {
    return settingsOverride;
  }


  /**
   * Convenience method to get a File object for the specified filename inside
   * the settings folder. Used to get preferences and recent sketch files.
   * @param filename A file inside the settings folder.
   * @return filename wrapped as a File object inside the settings folder
   */
  static public File getSettingsFile(String filename) {
    return new File(getSettingsFolder(), filename);
  }


  static public File getToolsFolder() {
    return Platform.getContentFile("tools");
  }


  static public void locateSketchbookFolder() {
    // If a value is at least set, first check to see if the folder exists.
    // If it doesn't, warn the user that the sketchbook folder is being reset.
    String sketchbookPath = Preferences.getSketchbookPath();
    if (sketchbookPath != null) {
      sketchbookFolder = new File(sketchbookPath);
      if (!sketchbookFolder.exists()) {
        Messages.showWarning("Sketchbook folder disappeared","""
            The sketchbook folder no longer exists.
            Processing will switch to the default sketchbook
            location, and create a new sketchbook folder if
            necessary. Processing will then stop talking
            about itself in the third person.""", null);
        sketchbookFolder = null;
      }
    }

    // If no path is set, get the default sketchbook folder for this platform
    if (sketchbookFolder == null) {
      sketchbookFolder = getDefaultSketchbookFolder();
      Preferences.setSketchbookPath(sketchbookFolder.getAbsolutePath());
      if (!sketchbookFolder.exists()) {
        if (!sketchbookFolder.mkdirs()) {
          Messages.showError("Could not create sketchbook",
                             "Unable to create a sketchbook folder at\n" +
                             sketchbookFolder + "\n" +
                             "Try creating a folder at that path and restart Processing.", null);
        }
      }
    }

    // make sure the libraries/modes/tools directories exist
    makeSketchbookSubfolders();
  }


  public void setSketchbookFolder(File folder) {
    sketchbookFolder = folder;
    Preferences.setSketchbookPath(folder.getAbsolutePath());
    rebuildSketchbook();
    makeSketchbookSubfolders();
  }


  /**
   * Create the libraries, modes, tools, examples folders in the sketchbook.
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  static protected void makeSketchbookSubfolders() {
    // ignore result; mkdirs() will return false if the folder already exists
    getSketchbookLibrariesFolder().mkdirs();
    getSketchbookToolsFolder().mkdirs();
    getSketchbookModesFolder().mkdirs();
    getSketchbookExamplesFolder().mkdirs();
    getSketchbookTemplatesFolder().mkdirs();

    /*
      Messages.showWarning("Could not create folder",
                           "Could not create the libraries, tools, modes, examples, and templates\n" +
                           "folders inside " + sketchbookFolder + "\n" +
                           "Try creating them manually to determine the problem.", null);
    */
  }


  static public File getSketchbookFolder() {
    return sketchbookFolder;
  }


  static public File getSketchbookLibrariesFolder() {
    return new File(sketchbookFolder, "libraries");
  }


  static public File getSketchbookToolsFolder() {
    return new File(sketchbookFolder, "tools");
  }


  static public File getSketchbookModesFolder() {
    return new File(sketchbookFolder, "modes");
  }


  static public File getSketchbookExamplesFolder() {
    return new File(sketchbookFolder, "examples");
  }


  static public File getSketchbookTemplatesFolder() {
    return new File(sketchbookFolder, "templates");
  }


  static protected File getDefaultSketchbookFolder() {
    File sketchbookFolder = null;
    try {
      sketchbookFolder = Platform.getDefaultSketchbookFolder();
    } catch (Exception ignored) { }

    if (sketchbookFolder == null) {
      Messages.showError("No sketchbook",
                         "Problem while trying to get the sketchbook", null);

    } else {
      // create the folder if it doesn't exist already
      boolean result = true;
      if (!sketchbookFolder.exists()) {
        result = sketchbookFolder.mkdirs();
      }

      if (!result) {
        Messages.showError("You forgot your sketchbook",
                           "Processing cannot run because it could not\n" +
                           "create a folder to store your sketchbook.", null);
      }
    }
    return sketchbookFolder;
  }
}
