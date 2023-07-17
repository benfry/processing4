/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org

Copyright (c) 2012-23 The Processing Foundation
Copyright (c) 2004-12 Ben Fry and Casey Reas
Copyright (c) 2001-04 Massachusetts Institute of Technology

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

package processing.mode.java;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import processing.core.PApplet;
import processing.data.StringList;
import processing.app.*;
import processing.app.contrib.*;
import processing.app.syntax.JEditTextArea;
import processing.app.syntax.PdeTextArea;
import processing.app.syntax.PdeTextAreaDefaults;
import processing.app.ui.*;
import processing.app.ui.Toolkit;
import processing.mode.java.debug.Debugger;
import processing.mode.java.debug.LineBreakpoint;
import processing.mode.java.debug.LineHighlight;
import processing.mode.java.debug.LineID;
import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.runner.Runner;
import processing.mode.java.tweak.ColorControlBox;
import processing.mode.java.tweak.Handle;
import processing.mode.java.tweak.SketchParser;
import processing.mode.java.tweak.TweakClient;


public class JavaEditor extends Editor {
  JavaMode jmode;

  // Runner associated with this editor window
  private Runner runtime;

  private boolean runtimeLaunchRequested;
  private final Object runtimeLock = new Object[0];

  // Need to sort through the rest of these additions [fry]

  protected final List<LineHighlight> breakpointedLines = new ArrayList<>();
  protected LineHighlight currentLine; // where the debugger is suspended
  protected final String breakpointMarkerComment = " //<>//";

  JMenu modeMenu;
//  protected JMenuItem inspectorItem;
//  static final int ERROR_TAB_INDEX = 0;

  protected PreprocService preprocService;

  protected Debugger debugger;

  final private InspectMode inspect;
  final private ShowUsage usage;
  final private Rename rename;
  final private ErrorChecker errorChecker;

  // set true to show AST debugging window
  static private final boolean SHOW_AST_VIEWER = false;
  private ASTViewer astViewer;

  /** P5 in decimal; if there are complaints, move to preferences.txt */
  static final int REFERENCE_PORT = 8053;
  // weird to link to a specific location like this, but it's versioned, so:
  static final String REFERENCE_URL =
    "https://github.com/processing/processing-website/releases/download/2022-10-05-1459/reference.zip";
  Boolean useReferenceServer;
  WebServer referenceServer;


  protected JavaEditor(Base base, String path, EditorState state,
                       Mode mode) throws EditorException {
    super(base, path, state, mode);

//    long t1 = System.currentTimeMillis();

    jmode = (JavaMode) mode;

    debugger = new Debugger(this);
    debugger.populateMenu(modeMenu);

    // set breakpoints from marker comments
    for (LineID lineID : stripBreakpointComments()) {
      //System.out.println("setting: " + lineID);
      debugger.setBreakpoint(lineID);
    }
    // setting breakpoints will flag sketch as modified, so override this here
    getSketch().setModified(false);

    preprocService = new PreprocService(this.jmode, this.sketch); 

//    long t5 = System.currentTimeMillis();

    usage = new ShowUsage(this, preprocService);
    inspect = new InspectMode(this, preprocService, usage);
    rename = new Rename(this, preprocService, usage);

    if (SHOW_AST_VIEWER) {
      astViewer = new ASTViewer(this, preprocService);
    }

    errorChecker = new ErrorChecker(this::setProblemList, preprocService);

//    long t7 = System.currentTimeMillis();

    for (SketchCode code : getSketch().getCode()) {
      Document document = code.getDocument();
      addDocumentListener(document);
    }
    sketchChanged();

//    long t9 = System.currentTimeMillis();

    Toolkit.setMenuMnemonics(textarea.getRightClickPopup());

    // ensure completion is hidden when editor loses focus
    addWindowFocusListener(new WindowFocusListener() {
      public void windowLostFocus(WindowEvent e) {
        getJavaTextArea().hideSuggestion();
      }

      public void windowGainedFocus(WindowEvent e) { }
    });

//    long t10 = System.currentTimeMillis();
//    System.out.println("java editor was " + (t10-t9) + " " + (t9-t7) + " " + (t7-t5) + " " + (t5-t1));
  }


  public PdePreprocessor createPreprocessor(final String sketchName) {
    return PdePreprocessor.builderFor(sketchName).build();
  }


  protected JEditTextArea createTextArea() {
    return new JavaTextArea(new PdeTextAreaDefaults(), this);
  }


  public EditorToolbar createToolbar() {
    return new JavaToolbar(this);
  }


  private int previousTabCount = 1;

  // TODO: this is a clumsy way to get notified when tabs get added/deleted
  // Override the parent call to add hook to the rebuild() method
  public EditorHeader createHeader() {
    return new EditorHeader(this) {
      public void rebuild() {
        super.rebuild();

        // after Rename and New Tab, we may have new .java tabs

        if (preprocService != null) {
          int currentTabCount = sketch.getCodeCount();
          if (currentTabCount != previousTabCount) {
            previousTabCount = currentTabCount;
            sketchChanged();
          }
        }
      }
    };
  }


  @Override
  public EditorFooter createFooter() {
    EditorFooter footer = super.createFooter();
    addErrorTable(footer);
    return footer;
  }


  public Formatter createFormatter() {
    return new AutoFormat();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public JMenu buildFileMenu() {
    //String appTitle = JavaToolbar.getTitle(JavaToolbar.EXPORT, false);
    String appTitle = Language.text("menu.file.export_application");
    JMenuItem exportApplication = Toolkit.newJMenuItemShift(appTitle, 'E');
    exportApplication.addActionListener(e -> {
      if (sketch.isUntitled() || sketch.isReadOnly()) {
        // Exporting to application will open the sketch folder, which is
        // weird for untitled sketches (that live in a temp folder) and
        // read-only sketches (that live in the examples folder).
        // TODO Better explanation? And some localization too.
        Messages.showMessage("Save First", "Please first save the sketch.");
      } else {
        handleExportApplication();
      }
    });

    return buildFileMenu(new JMenuItem[] { exportApplication });
  }


  public JMenu buildSketchMenu() {
    JMenuItem runItem = Toolkit.newJMenuItem(Language.text("menu.sketch.run"), 'R');
    runItem.addActionListener(e -> handleRun());

    JMenuItem presentItem = Toolkit.newJMenuItemShift(Language.text("menu.sketch.present"), 'R');
    presentItem.addActionListener(e -> handlePresent());

    JMenuItem stopItem = new JMenuItem(Language.text("menu.sketch.stop"));
    stopItem.addActionListener(e -> {
      if (isDebuggerEnabled()) {
        Messages.log("Invoked 'Stop' menu item");
        debugger.stopDebug();
      } else {
        handleStop();
      }
    });

    JMenuItem tweakItem = Toolkit.newJMenuItemShift(Language.text("menu.sketch.tweak"), 'T');
    tweakItem.addActionListener(e -> handleTweak());

    return buildSketchMenu(new JMenuItem[] {
      runItem, presentItem, tweakItem, stopItem
    });
  }


  public JMenu buildHelpMenu() {
    JMenu menu = new JMenu(Language.text("menu.help"));
    JMenuItem item;

    // macOS already has its own about menu
    if (!Platform.isMacOS()) {
      item = new JMenuItem(Language.text("menu.help.about"));
      item.addActionListener(e -> new About(JavaEditor.this));
      menu.add(item);
    }

    item = new JMenuItem(Language.text("menu.help.welcome"));
    item.addActionListener(e -> {
      try {
        new Welcome(base);
      } catch (IOException ioe) {
        Messages.showWarning("Unwelcome Error",
                             "Please report this error to\n" +
                             "https://github.com/processing/processing4/issues", ioe);
      }
    });
    menu.add(item);

    item = new JMenuItem(Language.text("menu.help.environment"));
    item.addActionListener(e -> showReference("../environment/index.html"));
    menu.add(item);

    item = new JMenuItem(Language.text("menu.help.reference"));
    item.addActionListener(e -> showReference("index.html"));
    menu.add(item);

    item = Toolkit.newJMenuItemShift(Language.text("menu.help.find_in_reference"), 'F');
    item.addActionListener(e -> {
      if (textarea.isSelectionActive()) {
        handleFindReference();
      } else {
        statusNotice(Language.text("editor.status.find_reference.select_word_first"));
      }
    });
    menu.add(item);

    // Not gonna use "update" since it's more about re-downloading:
    // it doesn't make sense to "update" the reference because it's
    // specific to a version of the software anyway. [fry 221125]
//    item = new JMenuItem(isReferenceDownloaded() ?
//      "menu.help.reference.update" : "menu.help.reference.download");
    item = new JMenuItem(Language.text("menu.help.reference.download"));
    item.addActionListener(e -> new Thread(this::downloadReference).start());
    menu.add(item);

    menu.addSeparator();

    final JMenu libRefSubmenu = new JMenu(Language.text("menu.help.libraries_reference"));

    // Adding this in case references are included in a core library,
    // or other core libraries are included in the future
    boolean isCoreLibMenuItemAdded =
      addLibReferencesToSubMenu(mode.coreLibraries, libRefSubmenu);

    if (isCoreLibMenuItemAdded && !mode.contribLibraries.isEmpty()) {
      libRefSubmenu.addSeparator();
    }

    boolean isContribLibMenuItemAdded =
      addLibReferencesToSubMenu(mode.contribLibraries, libRefSubmenu);

    if (!isContribLibMenuItemAdded && !isCoreLibMenuItemAdded) {
      JMenuItem emptyMenuItem = new JMenuItem(Language.text("menu.help.empty"));
      emptyMenuItem.setEnabled(false);
      emptyMenuItem.setFocusable(false);
      emptyMenuItem.setFocusPainted(false);
      libRefSubmenu.add(emptyMenuItem);

    } else if (!isContribLibMenuItemAdded && !mode.coreLibraries.isEmpty()) {
      //re-populate the menu to get rid of terminal separator
      libRefSubmenu.removeAll();
      addLibReferencesToSubMenu(mode.coreLibraries, libRefSubmenu);
    }
    menu.add(libRefSubmenu);

    final JMenu toolRefSubmenu = new JMenu(Language.text("menu.help.tools_reference"));
    boolean coreToolMenuItemAdded;
    boolean contribToolMenuItemAdded;

    List<ToolContribution> contribTools = base.getContribTools();
    // Adding this in case a reference folder is added for MovieMaker,
    // or in case other core tools are introduced later.
    coreToolMenuItemAdded = addToolReferencesToSubMenu(base.getCoreTools(), toolRefSubmenu);

    if (coreToolMenuItemAdded && !contribTools.isEmpty())
      toolRefSubmenu.addSeparator();

    contribToolMenuItemAdded = addToolReferencesToSubMenu(contribTools, toolRefSubmenu);

    if (!contribToolMenuItemAdded && !coreToolMenuItemAdded) {
      toolRefSubmenu.removeAll(); // in case a separator was added
      final JMenuItem emptyMenuItem = new JMenuItem(Language.text("menu.help.empty"));
      emptyMenuItem.setEnabled(false);
      emptyMenuItem.setBorderPainted(false);
      emptyMenuItem.setFocusable(false);
      emptyMenuItem.setFocusPainted(false);
      toolRefSubmenu.add(emptyMenuItem);
    }
    else if (!contribToolMenuItemAdded && !contribTools.isEmpty()) {
      // re-populate the menu to get rid of terminal separator
      toolRefSubmenu.removeAll();
      addToolReferencesToSubMenu(base.getCoreTools(), toolRefSubmenu);
    }
    menu.add(toolRefSubmenu);

    menu.addSeparator();
    /*
    item = new JMenuItem(Language.text("menu.help.online"));
    item.setEnabled(false);
    menu.add(item);
    */

    item = new JMenuItem(Language.text("menu.help.getting_started"));
    item.addActionListener(e -> Platform.openURL(Language.text("menu.help.getting_started.url")));
    menu.add(item);

    item = new JMenuItem(Language.text("menu.help.troubleshooting"));
    item.addActionListener(e -> Platform.openURL(Language.text("menu.help.troubleshooting.url")));
    menu.add(item);

    item = new JMenuItem(Language.text("menu.help.faq"));
    item.addActionListener(e -> Platform.openURL(Language.text("menu.help.faq.url")));
    menu.add(item);

    item = new JMenuItem(Language.text("menu.help.foundation"));
    item.addActionListener(e -> Platform.openURL(Language.text("menu.help.foundation.url")));
    menu.add(item);

    item = new JMenuItem(Language.text("menu.help.visit"));
    item.addActionListener(e -> Platform.openURL(Language.text("menu.help.visit.url")));
    menu.add(item);

    return menu;
  }


  //. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Populates the JMenu with JMenuItems, one for each Library that has a
   * reference accompanying it. The JMenuItems open the index.htm/index.html
   * file of the reference in the user's default browser, or the readme.txt in
   * the user's default text editor.
   *
   * @param libsList
   *          A list of the Libraries to be added
   * @param subMenu
   *          The JMenu to which the JMenuItems corresponding to the Libraries
   *          are to be added
   * @return true if and only if any JMenuItems were added; false otherwise
   */
  private boolean addLibReferencesToSubMenu(List<Library> libsList, JMenu subMenu) {
    boolean isItemAdded = false;
    for (Library libContrib : libsList) {
      if (libContrib.hasReference()) {
        JMenuItem libRefItem = new JMenuItem(libContrib.getName());
        libRefItem.addActionListener(arg0 -> showReferenceFile(libContrib.getReferenceIndexFile()));
        subMenu.add(libRefItem);
        isItemAdded = true;
      }
    }
    return isItemAdded;
  }


  /**
   * Populates the JMenu with JMenuItems, one for each Tool that has a reference
   * accompanying it. The JMenuItems open the index.htm/index.html file of the
   * reference in the user's default browser, or the readme.txt in the user's
   * default text editor.
   *
   * @param toolsList
   *          A list of Tools to be added
   * @param subMenu
   *          The JMenu to which the JMenuItems corresponding to the Tools are
   *          to be added
   * @return true if and only if any JMenuItems were added; false otherwise
   */
  private boolean addToolReferencesToSubMenu(List<ToolContribution> toolsList, JMenu subMenu) {
    boolean isItemAdded = false;
    for (ToolContribution toolContrib : toolsList) {
      final File toolRef = new File(toolContrib.getFolder(), "reference/index.html");
      if (toolRef.exists()) {
        JMenuItem libRefItem = new JMenuItem(toolContrib.getName());
        libRefItem.addActionListener(arg0 -> showReferenceFile(toolRef));
        subMenu.add(libRefItem);
        isItemAdded = true;
      }
    }
    return isItemAdded;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public String getCommentPrefix() {
    return "//";
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Handler for Sketch → Export Application
   */
  public void handleExportApplication() {
    if (handleExportCheckModified()) {
      statusNotice(Language.text("export.notice.exporting"));
      ExportPrompt ep = new ExportPrompt(this, () -> {
        try {
          if (jmode.handleExportApplication(getSketch())) {
            Platform.openFolder(sketch.getFolder());
            statusNotice(Language.text("export.notice.exporting.done"));
          }
        } catch (Exception e) {
          statusNotice(Language.text("export.notice.exporting.error"));
          e.printStackTrace();
        }
      });
      ep.trigger();
    }
  }


  /**
   * Checks to see if the sketch has been modified, and if so,
   * asks the user to save the sketch or cancel the export.
   * This prevents issues where an incomplete version of the sketch
   * would be exported, and is a fix for
   * <A HREF="https://download.processing.org/bugzilla/157.html">Bug 157</A>
   */
  protected boolean handleExportCheckModified() {
    if (sketch.isReadOnly()) {
      // if the files are read-only, need to first do a "save as".
      Messages.showMessage(Language.text("export.messages.is_read_only"),
                           Language.text("export.messages.is_read_only.description"));
      return false;
    }

    // don't allow if untitled
    if (sketch.isUntitled()) {
      Messages.showMessage(Language.text("export.messages.cannot_export"),
                           Language.text("export.messages.cannot_export.description"));
      return false;
    }

    if (sketch.isModified()) {
      Object[] options = { Language.text("prompt.ok"), Language.text("prompt.cancel") };
      int result = JOptionPane.showOptionDialog(this,
                                                Language.text("export.unsaved_changes"),
                                                Language.text("menu.file.save"),
                                                JOptionPane.OK_CANCEL_OPTION,
                                                JOptionPane.QUESTION_MESSAGE,
                                                null,
                                                options,
                                                options[0]);

      if (result == JOptionPane.OK_OPTION) {
        handleSave(true);

      } else {
        // why it's not CANCEL_OPTION is beyond me (at least on the mac)
        // but f-- it... let's get this shite done...
        //} else if (result == JOptionPane.CANCEL_OPTION) {
        statusNotice(Language.text("export.notice.cancel.unsaved_changes"));
        //toolbar.clear();
        return false;
      }
    }
    return true;
  }


  public void handleRun() {
    if (isDebuggerEnabled()) {
      // Hitting Run while a sketch is running should restart the sketch
      // https://github.com/processing/processing/issues/3623
      if (debugger.isStarted()) {
        debugger.stopDebug();
      }
      // Don't start the sketch paused, continue until a breakpoint or error
      // https://github.com/processing/processing/issues/3096
      debugger.continueDebug();

    } else {
      handleLaunch(false, false);
    }
  }


  public void handlePresent() {
    handleLaunch(true, false);
  }


  public void handleTweak() {
    autoSave();

    if (sketch.isModified()) {
      Messages.showMessage(Language.text("menu.file.save"),
                           Language.text("tweak_mode.save_before_tweak"));
      return;
    }

    handleLaunch(false, true);
  }

  protected void handleLaunch(boolean present, boolean tweak) {
    prepareRun();
    toolbar.activateRun();
    synchronized (runtimeLock) {
      runtimeLaunchRequested = true;
    }
    new Thread(() -> {
      try {
        synchronized (runtimeLock) {
          if (runtimeLaunchRequested) {
            runtimeLaunchRequested = false;
            RunnerListener listener = new RunnerListenerEdtAdapter(JavaEditor.this);
            if (!tweak) {
              runtime = jmode.handleLaunch(sketch, listener, present);
            } else {
              runtime = jmode.handleTweak(sketch, listener, JavaEditor.this);
            }
          }
        }
      } catch (Exception e) {
        EventQueue.invokeLater(() -> statusError(e));
      }
    }).start();
  }


  /**
   * Event handler called when hitting the stop button. Stops a running debug
   * session or performs standard stop action if not currently debugging.
   */
  public void handleStop() {
    if (debugger.isStarted()) {
      debugger.stopDebug();

    } else {
      toolbar.activateStop();

      try {
        synchronized (runtimeLock) {
          if (runtimeLaunchRequested) {
            // Cancel the launch before the runtime was created
            runtimeLaunchRequested = false;
          }
          if (runtime != null) {
            // Cancel the launch after the runtime was created
            runtime.close();  // kills the window
            runtime = null;
          }
        }
      } catch (Exception e) {
        statusError(e);
      }

      toolbar.deactivateStop();
      toolbar.deactivateRun();

      // focus the PDE again after quitting presentation mode [toxi 030903]
      toFront();
    }
  }


  public void onRunnerExiting(Runner runner) {
    synchronized (runtimeLock) {
      if (this.runtime == runner) {
        deactivateRun();
      }
    }
  }


//  /** Toggle a breakpoint on the current line. */
//  public void toggleBreakpoint() {
//    toggleBreakpoint(getCurrentLineID().lineIdx());
//  }


  @Override
  public void toggleBreakpoint(int lineIndex) {
    debugger.toggleBreakpoint(lineIndex);
  }


  public boolean handleSaveAs() {
    //System.out.println("handleSaveAs");
    String oldName = getSketch().getCode(0).getFileName();
    //System.out.println("old name: " + oldName);
    boolean saved = super.handleSaveAs();
    if (saved) {
      // re-set breakpoints in first tab (name has changed)
      List<LineBreakpoint> bps = debugger.getBreakpoints(oldName);
      debugger.clearBreakpoints(oldName);
      String newName = getSketch().getCode(0).getFileName();
      //System.out.println("new name: " + newName);
      for (LineBreakpoint bp : bps) {
        LineID line = new LineID(newName, bp.lineID().lineIdx());
        //System.out.println("setting: " + line);
        debugger.setBreakpoint(line);
      }
      // add breakpoint marker comments to source file
      for (SketchCode code : getSketch().getCode()) {
        addBreakpointComments(code.getFileName());
      }

      // set new name of variable inspector
      //inspector.setTitle(getSketch().getName());
    }
    return saved;
  }


  /**
   * Add import statements to the current tab for all packages inside
   * the specified jar file.
   */
  public void handleImportLibrary(String libraryName) {
    // make sure the user didn't hide the sketch folder
    sketch.ensureExistence();

    // import statements into the main sketch file (code[0])
    // if the current code is a .java file, insert into current
    //if (current.flavor == PDE) {
    if (mode.isDefaultExtension(sketch.getCurrentCode())) {
      sketch.setCurrentCode(0);
    }

    Library lib = mode.findLibraryByName(libraryName);
    if (lib == null) {
      statusError("Unable to locate library: "+libraryName);
      return;
    }

    // could also scan the text in the file to see if each import
    // statement is already in there, but if the user has the import
    // commented out, then this will be a problem.
    StringList list = lib.getImports(); // ask the library for its imports
    if (list == null) {
      // Default to old behavior and load each package in the primary jar
      list = Util.packageListFromClassPath(lib.getJarPath());
    }

    StringBuilder sb = new StringBuilder();
//    for (int i = 0; i < list.length; i++) {
    for (String item : list) {
      sb.append("import ");
//      sb.append(list[i]);
      sb.append(item);
      sb.append(".*;\n");
    }
    sb.append('\n');
    sb.append(getText());
    setText(sb.toString());
    setSelection(0, 0);  // scroll to start
    sketch.setModified(true);
  }


  @Override
  public void librariesChanged() {
    preprocService.notifyLibrariesChanged();
  }


  @Override
  public void codeFolderChanged() {
    preprocService.notifyCodeFolderChanged();
  }


  @Override
  public void sketchChanged() {
    errorChecker.notifySketchChanged();
    preprocService.notifySketchChanged();
  }


  public void addDocumentListener(Document doc) {
    if (doc != null) {
      doc.addDocumentListener(new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
          sketchChanged();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
          sketchChanged();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
          sketchChanged();
        }
      });
    }
  }


  public void showReference(String name) {
    if (useReferenceServer == null) {
      // Because of this, it should be possible to create your own dist
      // that includes the reference by simply adding it to modes/java.
      File referenceZip = new File(mode.getFolder(), "reference.zip");
      if (!referenceZip.exists()) {
        // For Java Mode (the default), check for a reference.zip in the root
        // of the sketchbook folder. If other Modes subclass JavaEditor and
        // don't override this function, it may cause a little trouble.
        referenceZip = getOfflineReferenceFile();
      }
      if (referenceZip.exists()) {
        try {
          referenceServer = new WebServer(referenceZip, REFERENCE_PORT);
          useReferenceServer = true;

        } catch (IOException e) {
          Messages.showWarning("Reference Server Problem", "Error while starting the documentation server.");
        }

      } else {
        useReferenceServer = false;
      }
    }

    if (useReferenceServer) {
      String url = referenceServer.getPrefix() + "reference/" + name;
      Platform.openURL(url);

    } else {
      File file = new File(mode.getReferenceFolder(), name);
      if (file.exists()) {
        showReferenceFile(file);
      } else {
        // Offline reference (temporarily) removed in 4.0 beta 9
        // https://github.com/processing/processing4/issues/524
        Platform.openURL("https://processing.org/reference/" + name);
      }
    }
  }


  private File getOfflineReferenceFile() {
    return new File(Base.getSketchbookFolder(), "reference.zip");
  }


  /*
  private boolean isReferenceDownloaded() {
    return getOfflineReferenceFile().exists();
  }
  */


  private void downloadReference() {
    try {
      URL source = new URL(REFERENCE_URL);
      HttpURLConnection conn = (HttpURLConnection) source.openConnection();
      HttpURLConnection.setFollowRedirects(true);
      conn.setConnectTimeout(15 * 1000);
      conn.setReadTimeout(60 * 1000);
      conn.setRequestMethod("GET");
      conn.connect();

      int length = conn.getContentLength();
//      float size = (length >> 10) / 1024f;
      //float size = (length / 1000) / 1000f;
//      String msg =
//        "Downloading reference (" + PApplet.nf(size, 0, 1) + " MB)… ";
      String mb = PApplet.nf((length >> 10) / 1024f, 0, 1);
      if (mb.endsWith(".0")) {
        mb = mb.substring(0, mb.length() - 2);  // don't show .0
      }
      ProgressMonitorInputStream input =
        new ProgressMonitorInputStream(this,
          "Downloading reference (" + mb + " MB)… ", conn.getInputStream());
      input.getProgressMonitor().setMaximum(length);
//      ProgressMonitor monitor = input.getProgressMonitor();
//      monitor.setMaximum(length);
      PApplet.saveStream(getOfflineReferenceFile(), input);
      // reset the internal handling for the reference server
      useReferenceServer = null;

    } catch (InterruptedIOException iioe) {
      // download canceled

    } catch (IOException e) {
      Messages.showWarning("Error downloading reference",
        "Could not download the reference. Try again later.", e);
    }
  }


  public void statusError(String what) {
    super.statusError(what);
//    new Exception("deactivating RUN").printStackTrace();
//    toolbar.deactivate(JavaToolbar.RUN);
    toolbar.deactivateRun();
  }


  public void internalCloseRunner() {
    // Added temporarily to dump error log. TODO: Remove this later [mk29]
    //if (JavaMode.errorLogsEnabled) {
    //  writeErrorsToFile();
    //}
    handleStop();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  // Additions from PDE X, Debug Mode, Twerk Mode...


  /**
   * Used instead of the windowClosing event handler, since it's not called on
   * mode switch. Called when closing the editor window. Stops running debug
   * sessions and kills the variable inspector window.
   */
  @Override
  public void dispose() {
    //System.out.println("window dispose");
    // quit running debug session
    if (debugger.isEnabled()) {
      debugger.stopDebug();
    }
    debugger.dispose();
    preprocService.dispose();

    inspect.dispose();
    usage.dispose();
    rename.dispose();

    errorChecker.dispose();
    if (astViewer != null) {
      astViewer.dispose();
    }
    super.dispose();
  }


  @Override
  public boolean isDebuggerEnabled() {
    return debugger.isEnabled();
  }


  @Override
  public JMenu buildModeMenu() {
    return modeMenu = new JMenu(Language.text("menu.debug"));
  }


  // handleOpenInternal() only called by the Editor constructor, meaning that
  // this code is all useless. All these things will be in their default state.
//  /**
//   * Event handler called when loading another sketch in this editor.
//   * Clears breakpoints of previous sketch.
//   * @return true if a sketch was opened, false if aborted
//   */
//  @Override
//  protected void handleOpenInternal(String path) throws EditorException {
//    super.handleOpenInternal(path);
//
//    // should already been stopped (open calls handleStop)
//    if (debugger != null) {
//      debugger.clearBreakpoints();
//    }
//    clearBreakpointedLines();
//    variableInspector().reset();
//  }


  /**
   * Extract breakpointed lines from source code marker comments. This removes
   * marker comments from the editor text. Intended to be called on loading a
   * sketch, since re-setting the sketches contents after removing the markers
   * will clear all breakpoints.
   *
   * @return the list of {@link LineID}s where breakpoint marker comments were
   * removed from.
   */
  protected List<LineID> stripBreakpointComments() {
    List<LineID> bps = new ArrayList<>();
    // iterate over all tabs
    Sketch sketch = getSketch();
    for (int i = 0; i < sketch.getCodeCount(); i++) {
      SketchCode tab = sketch.getCode(i);
      String code = tab.getProgram();
      String[] lines = code.split("\\r?\\n"); // newlines not included
      //System.out.println(code);

      // scan code for breakpoint comments
      int lineIdx = 0;
      for (String line : lines) {
        //System.out.println(line);
        if (line.endsWith(breakpointMarkerComment)) {
          LineID lineID = new LineID(tab.getFileName(), lineIdx);
          bps.add(lineID);
          //System.out.println("found breakpoint: " + lineID);
          // got a breakpoint
          //dbg.setBreakpoint(lineID);
          int index = line.lastIndexOf(breakpointMarkerComment);
          lines[lineIdx] = line.substring(0, index);
        }
        lineIdx++;
      }
      //tab.setProgram(code);
      code = PApplet.join(lines, "\n");
      setTabContents(tab.getFileName(), code);
    }
    return bps;
  }


  /**
   * Add breakpoint marker comments to the source file of a specific tab. This
   * acts on the source file on disk, not the editor text. Intended to be
   * called just after saving the sketch.
   *
   * @param tabFilename the tab file name
   */
  protected void addBreakpointComments(String tabFilename) {
    SketchCode tab = getTab(tabFilename);
    if (tab == null) {
      // this method gets called twice when saving sketch for the first time
      // once with new name and another with old(causing NPE). Keep an eye out
      // for potential issues. See #2675. TODO:
      Messages.err("Illegal tab name to addBreakpointComments() " + tabFilename);
      return;
    }
    List<LineBreakpoint> bps = debugger.getBreakpoints(tab.getFileName());

    // load the source file
    ////switched to using methods provided by the SketchCode class
    // File sourceFile = new File(sketch.getFolder(), tab.getFileName());
    //System.out.println("file: " + sourceFile);
    try {
      tab.load();
      String code = tab.getProgram();
      //System.out.println("code: " + code);
      String[] lines = code.split("\\r?\\n"); // newlines not included
      for (LineBreakpoint bp : bps) {
        //System.out.println("adding bp: " + bp.lineID());
        lines[bp.lineID().lineIdx()] += breakpointMarkerComment;
      }
      code = PApplet.join(lines, "\n");
      //System.out.println("new code: " + code);
      tab.setProgram(code);
      tab.save();
    } catch (IOException ex) {
      Messages.err(null, ex);
    }
  }


  @Override
  public boolean handleSave(boolean immediately) {
    // note modified tabs
    final List<String> modified = new ArrayList<>();
    for (int i = 0; i < getSketch().getCodeCount(); i++) {
      SketchCode tab = getSketch().getCode(i);
      if (tab.isModified()) {
        modified.add(tab.getFileName());
      }
    }

    boolean saved = super.handleSave(immediately);
    if (saved) {
      if (immediately) {
        for (String tabFilename : modified) {
          addBreakpointComments(tabFilename);
        }
      } else {
        EventQueue.invokeLater(() -> {
          for (String tabFilename : modified) {
            addBreakpointComments(tabFilename);
          }
        });
      }
    }
    //  if file location has changed, update autosaver
    // autosaver.reloadAutosaveDir();
    return saved;
  }


  /**
   * Set text contents of a specific tab. Updates underlying document and text
   * area. Clears Breakpoints.
   *
   * @param tabFilename the tab file name
   * @param code the text to set
   */
  protected void setTabContents(String tabFilename, String code) {
    // remove all breakpoints of this tab
    debugger.clearBreakpoints(tabFilename);

    SketchCode currentTab = getCurrentTab();

    // set code of tab
    SketchCode tab = getTab(tabFilename);
    if (tab != null) {
      tab.setProgram(code);
      // this updates document and text area
      // TODO: does this have any negative effects? (setting the doc to null)
      tab.setDocument(null);
      setCode(tab);

      // switch back to original tab
      setCode(currentTab);
    }
  }


  public void clearConsole() {
    console.clear();
  }


  public void clearSelection() {
    setSelection(getCaretOffset(), getCaretOffset());
  }


  /**
   * Select a line in the current tab.
   * @param lineIdx 0-based line number
   */
  public void selectLine(int lineIdx) {
    setSelection(getLineStartOffset(lineIdx), getLineStopOffset(lineIdx));
  }


  /**
   * Set the cursor to the start of a line.
   * @param lineIdx 0-based line number
   */
  public void cursorToLineStart(int lineIdx) {
    setSelection(getLineStartOffset(lineIdx), getLineStartOffset(lineIdx));
  }


  /**
   * Set the cursor to the end of a line.
   * @param lineIdx 0-based line number
   */
  public void cursorToLineEnd(int lineIdx) {
    setSelection(getLineStopOffset(lineIdx), getLineStopOffset(lineIdx));
  }


  /**
   * Switch to a tab.
   * @param tabFileName the file name identifying the tab. (as in
   * {@link SketchCode#getFileName()})
   */
  public void switchToTab(String tabFileName) {
    Sketch s = getSketch();
    for (int i = 0; i < s.getCodeCount(); i++) {
      if (tabFileName.equals(s.getCode(i).getFileName())) {
        s.setCurrentCode(i);
        break;
      }
    }
  }


  public Debugger getDebugger() {
    return debugger;
  }


  /**
   * Access the custom text area object.
   * @return the text area object
   */
  public JavaTextArea getJavaTextArea() {
    return (JavaTextArea) textarea;
  }


  public PreprocService getPreprocessingService() {
    return preprocService;
  }


  /**
   * Grab current contents of the sketch window, advance the console, stop any
   * other running sketches, auto-save the user's code... not in that order.
   */
  @Override
  public void prepareRun() {
    autoSave();
    super.prepareRun();
    downloadImports();
    preprocService.cancel();
  }


  /**
   * Downloads libraries that have been imported, that aren't available as a
   * LocalContribution, but that have an AvailableContribution associated with
   * them.
   */
  protected void downloadImports() {
    for (SketchCode sc : sketch.getCode()) {
      if (sc.isExtension("pde")) {
        String tabCode = sc.getProgram();

        List<ImportStatement> imports =  SourceUtil.parseProgramImports(tabCode);

        if (!imports.isEmpty()) {
          ArrayList<String> importHeaders = new ArrayList<>();
          for (ImportStatement importStatement : imports) {
            importHeaders.add(importStatement.getFullMemberName());
          }
          List<AvailableContribution> installLibsHeaders =
            getNotInstalledAvailableLibs(importHeaders);
          if (!installLibsHeaders.isEmpty()) {
            StringBuilder libList = new StringBuilder("Would you like to install them now?");
            for (AvailableContribution ac : installLibsHeaders) {
              libList.append("\n  • ").append(ac.getName());
            }
            int option = Messages.showYesNoQuestion(this,
                Language.text("contrib.import.dialog.title"),
                Language.text("contrib.import.dialog.primary_text"),
                libList.toString());

            if (option == JOptionPane.YES_OPTION) {
              ContributionManager.downloadAndInstallOnImport(base, installLibsHeaders);
            }
          }
        }
      }
    }
  }


  /**
   * Returns a list of AvailableContributions of those libraries that the user
   * wants imported, but that are not installed.
   */
  private List<AvailableContribution> getNotInstalledAvailableLibs(List<String> importHeadersList) {
    Map<String, Contribution> importMap =
      ContributionListing.getInstance().getLibraryExports();
    List<AvailableContribution> libList = new ArrayList<>();
    for (String importHeaders : importHeadersList) {
      int dot = importHeaders.lastIndexOf('.');
      String entry = (dot == -1) ? importHeaders : importHeaders.substring(0,
          dot);

      if (entry.startsWith("java.") ||
          entry.startsWith("javax.") ||
          entry.startsWith("processing.")) {
        continue;
      }

      Library library;
      try {
        library = this.getMode().getLibrary(entry);
        if (library == null) {
          Contribution c = importMap.get(importHeaders);
          if (c instanceof AvailableContribution) {  // also checks null
            libList.add((AvailableContribution) c);// System.out.println(importHeaders
                                                   // + "not found");
          }
        }
      } catch (Exception e) {
        // Not gonna happen (hopefully)
        Contribution c = importMap.get(importHeaders);
        if (c instanceof AvailableContribution) {  // also checks null
          libList.add((AvailableContribution) c);// System.out.println(importHeaders
                                                 // + "not found");
        }
      }
    }
    return libList;
  }


  /**
   * Displays a JDialog prompting the user to save when the user hits
   * run/present/etc.
   */
  protected void autoSave() {
    if (!JavaMode.autoSaveEnabled) {
      return;
    }

    try {
      if (sketch.isModified() && !sketch.isUntitled()) {
        if (JavaMode.autoSavePromptEnabled) {
          final JDialog autoSaveDialog =
            new JDialog(base.getActiveEditor(), getSketch().getName(), true);
          Container container = autoSaveDialog.getContentPane();

          JPanel panelMain = new JPanel();
          panelMain.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 2));
          panelMain.setLayout(new BoxLayout(panelMain, BoxLayout.PAGE_AXIS));

          JPanel panelLabel = new JPanel(new FlowLayout(FlowLayout.LEFT));
          JLabel label = new JLabel("<html><body>&nbsp;There are unsaved"
                                    + " changes in your sketch.<br />"
                                    + "&nbsp;&nbsp;&nbsp; Do you want to save it before"
                                    + " running? </body></html>");
          label.setFont(new Font(
              label.getFont().getName(),
              Font.PLAIN,
              Toolkit.zoom(label.getFont().getSize() + 1)
          ));
          panelLabel.add(label);
          panelMain.add(panelLabel);
          final JCheckBox dontRedisplay = new JCheckBox("Remember this decision");
          JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));

          JButton btnRunSave = new JButton("Save and Run");
          btnRunSave.addActionListener(e -> {
            handleSave(true);
            if (dontRedisplay.isSelected()) {
              JavaMode.autoSavePromptEnabled = !dontRedisplay.isSelected();
              JavaMode.defaultAutoSaveEnabled = true;
              jmode.savePreferences();
            }
            autoSaveDialog.dispose();
          });
          panelButtons.add(btnRunSave);

          JButton btnRunNoSave = new JButton("Run, Don't Save");
          btnRunNoSave.addActionListener(e -> {
            if (dontRedisplay.isSelected()) {
              JavaMode.autoSavePromptEnabled = !dontRedisplay.isSelected();
              JavaMode.defaultAutoSaveEnabled = false;
              jmode.savePreferences();
            }
            autoSaveDialog.dispose();
          });
          panelButtons.add(btnRunNoSave);
          panelMain.add(panelButtons);

          JPanel panelCheck = new JPanel();
          panelCheck.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
          panelCheck.add(dontRedisplay);
          panelMain.add(panelCheck);

          container.add(panelMain);

          autoSaveDialog.setResizable(false);
          autoSaveDialog.pack();
          autoSaveDialog.setLocationRelativeTo(base.getActiveEditor());
          autoSaveDialog.setVisible(true);

        } else if (JavaMode.defaultAutoSaveEnabled) {
          handleSave(true);
        }
      }
    } catch (Exception e) {
      statusError(e);
    }
  }


  public void activateRun() {
    debugger.enableMenuItem(false);
    toolbar.activateRun();
  }


  /**
   * Deactivate the Run button. This is called by Runner to notify that the
   * sketch has stopped running, usually in response to an error (or maybe
   * the sketch completing and exiting?) Tools should not call this function.
   * To initiate a "stop" action, call handleStop() instead.
   */
  public void deactivateRun() {
    toolbar.deactivateRun();
    debugger.enableMenuItem(true);
  }


  /*
  protected void activateDebug() {
    activateRun();
  }


  public void deactivateDebug() {
    deactivateRun();
  }
   */


  public void activateContinue() {
    ((JavaToolbar) toolbar).activateContinue();
  }


  public void deactivateContinue() {
    ((JavaToolbar) toolbar).deactivateContinue();
  }


  public void activateStep() {
    ((JavaToolbar) toolbar).activateStep();
  }


  public void deactivateStep() {
    ((JavaToolbar) toolbar).deactivateStep();
  }


  public void toggleDebug() {
//    debugEnabled = !debugEnabled;

    debugger.toggleEnabled();
    rebuildToolbar();
    repaint();  // show/hide breakpoints in the gutter

    /*
    if (debugEnabled) {
      debugItem.setText(Language.text("menu.debug.disable"));
    } else {
      debugItem.setText(Language.text("menu.debug.enable"));
    }
    inspector.setVisible(debugEnabled);

    for (Component item : debugMenu.getMenuComponents()) {
      if (item instanceof JMenuItem && item != debugItem) {
        item.setEnabled(debugEnabled);
      }
    }
    */
  }


  /*
  public void toggleVariableInspector() {
    if (inspector.isVisible()) {
      inspectorItem.setText(Language.text("menu.debug.show_variables"));
      inspector.setVisible(false);
    } else {
//      inspector.setFocusableWindowState(false); // to not get focus when set visible
      inspectorItem.setText(Language.text("menu.debug.show_variables"));
      inspector.setVisible(true);
//      inspector.setFocusableWindowState(true); // allow to get focus again
    }
  }
  */


//  public void showVariableInspector() {
//    tray.setVisible(true);
//  }


//  /**
//   * Set visibility of the variable inspector window.
//   * @param visible true to set the variable inspector visible,
//   * false for invisible.
//   */
//  public void showVariableInspector(boolean visible) {
//    tray.setVisible(visible);
//  }
//
//
//  public void hideVariableInspector() {
//    tray.setVisible(true);
//  }
//
//
//  /** Toggle visibility of the variable inspector window. */
//  public void toggleVariableInspector() {
//    tray.setFocusableWindowState(false); // to not get focus when set visible
//    tray.setVisible(!tray.isVisible());
//    tray.setFocusableWindowState(true); // allow to get focus again
//  }


  /**
   * Set the line to highlight as currently suspended at. Will override the
   * breakpoint color, if set. Switches to the appropriate tab and scroll to
   * the line by placing the cursor there.
   * @param line the line to highlight as current suspended line
   */
  public void setCurrentLine(LineID line) {
    clearCurrentLine();
    if (line == null) {
      // safety, e.g. when no line mapping is found and the null line is used.
      return;
    }
    switchToTab(line.fileName());
    // scroll to line, by setting the cursor
    cursorToLineStart(line.lineIdx());
    // highlight line
    currentLine = new LineHighlight(line.lineIdx(), this);
    currentLine.setMarker(PdeTextArea.STEP_MARKER);
    currentLine.setPriority(10); // fixes current line being hidden by the breakpoint when moved down
  }


  /** Clear the highlight for the debuggers current line. */
  public void clearCurrentLine() {
    if (currentLine != null) {
      currentLine.clear();
      currentLine.dispose();

      // revert to breakpoint color if any is set on this line
      for (LineHighlight hl : breakpointedLines) {
        if (hl.getLineID().equals(currentLine.getLineID())) {
          hl.paint();
          break;
        }
      }
      currentLine = null;
    }
  }


  /**
   * Add highlight for a breakpointed line.
   * @param lineID the line id to highlight as breakpointed
   */
  public void addBreakpointedLine(LineID lineID) {
    LineHighlight hl = new LineHighlight(lineID, this);
    hl.setMarker(PdeTextArea.BREAK_MARKER);
    breakpointedLines.add(hl);
    // repaint current line if it's on this line
    if (currentLine != null && currentLine.getLineID().equals(lineID)) {
      currentLine.paint();
    }
  }


  /**
   * Remove a highlight for a breakpointed line. Needs to be on the current tab.
   * @param lineIdx the line index on the current tab to remove a breakpoint
   * highlight from
   */
  public void removeBreakpointedLine(int lineIdx) {
    LineID line = getLineIDInCurrentTab(lineIdx);
    //System.out.println("line id: " + line.fileName() + " " + line.lineIdx());
    LineHighlight foundLine = null;
    for (LineHighlight hl : breakpointedLines) {
      if (hl.getLineID().equals(line)) {
        foundLine = hl;
        break;
      }
    }
    if (foundLine != null) {
      foundLine.clear();
      breakpointedLines.remove(foundLine);
      foundLine.dispose();
      // repaint current line if it's on this line
      if (currentLine != null && currentLine.getLineID().equals(line)) {
        currentLine.paint();
      }
    }
  }


  /*
  // Remove all highlights for breakpoint lines.
  public void clearBreakpointedLines() {
    for (LineHighlight hl : breakpointedLines) {
      hl.clear();
      hl.dispose();
    }
    breakpointedLines.clear(); // remove all breakpoints
    // fix highlights not being removed when tab names have
    // changed due to opening a new sketch in same editor
    getJavaTextArea().clearGutterText();

    // repaint current line
    if (currentLine != null) {
      currentLine.paint();
    }
  }
  */


  /**
   * Retrieve a {@link LineID} object for a line on the current tab.
   * @param lineIdx the line index on the current tab
   * @return the {@link LineID} object representing a line index on the
   * current tab
   */
  public LineID getLineIDInCurrentTab(int lineIdx) {
    return new LineID(getSketch().getCurrentCode().getFileName(), lineIdx);
  }


  /**
   * Retrieve line of sketch where the cursor currently resides.
   * @return the current {@link LineID}
   */
  public LineID getCurrentLineID() {
    String tab = getSketch().getCurrentCode().getFileName();
    int lineNo = getTextArea().getCaretLine();
    return new LineID(tab, lineNo);
  }


  /**
   * Check whether a {@link LineID} is on the current tab.
   * @param line the {@link LineID}
   * @return true, if the {@link LineID} is on the current tab.
   */
  public boolean isInCurrentTab(LineID line) {
    return line.fileName().equals(getSketch().getCurrentCode().getFileName());
  }


  /**
   * Event handler called when switching between tabs. Loads all line
   * background colors set for the tab.
   * @param code tab to switch to
   */
  @Override
  public void setCode(SketchCode code) {
    Document oldDoc = code.getDocument();

    //System.out.println("tab switch: " + code.getFileName());
    // set the new document in the textarea, etc. need to do this first
    super.setCode(code);

    Document newDoc = code.getDocument();
    if (oldDoc != newDoc) {
      addDocumentListener(newDoc);
    }

    // set line background colors for tab
    final JavaTextArea ta = getJavaTextArea();
    // can be null when setCode is called the first time (in constructor)
    if (ta != null) {
      // clear all gutter text
      ta.clearGutterText();
      // first paint breakpoints
      if (breakpointedLines != null) {
        for (LineHighlight hl : breakpointedLines) {
          if (isInCurrentTab(hl.getLineID())) {
            hl.paint();
          }
        }
      }
      // now paint current line (if any)
      if (currentLine != null) {
        if (isInCurrentTab(currentLine.getLineID())) {
          currentLine.paint();
        }
      }
    }
    if (getDebugger() != null && getDebugger().isStarted()) {
      getDebugger().startTrackingLineChanges();
    }
    if (errorColumn != null) {
      errorColumn.repaint();
    }
  }


  /**
   * Get a tab by its file name.
   * @param filename the filename to search for.
   * @return the {@link SketchCode} object for the tab, or null if not found
   */
  public SketchCode getTab(String filename) {
    Sketch s = getSketch();
    for (SketchCode c : s.getCode()) {
      if (c.getFileName().equals(filename)) {
        return c;
      }
    }
    return null;
  }


  /**
   * Retrieve the current tab.
   * @return the {@link SketchCode} representing the current tab
   */
  public SketchCode getCurrentTab() {
    return getSketch().getCurrentCode();
  }


  /**
   * Access the currently edited document.
   * @return the document object
   */
  public Document currentDocument() {
    return getCurrentTab().getDocument();
  }


  public void statusBusy() {
    statusNotice(Language.text("editor.status.debug.busy"));
  }


  public void statusHalted() {
    statusNotice(Language.text("editor.status.debug.halt"));
  }


  /**
   * Updates the error table in the Error Window.
   * Overridden to handle the fugly import suggestions text.
   */
  @Override
  public void updateErrorTable(List<Problem> problems) {
    errorTable.clearRows();

    for (Problem p : problems) {
      String message = p.getMessage();

      if (p.getClass().equals(JavaProblem.class)) {
        JavaProblem jp = (JavaProblem) p;
        if (JavaMode.importSuggestEnabled &&
            jp.getImportSuggestions() != null &&
            jp.getImportSuggestions().length > 0) {
          message += " (double-click for suggestions)";
        }
      }

      errorTable.addRow(p, message,
                   sketch.getCode(p.getTabIndex()).getPrettyName(),
                   Integer.toString(p.getLineNumber() + 1));
      // Added +1 because lineNumbers internally are 0-indexed
    }
  }


  @Override
  public void errorTableDoubleClick(Object item) {
    if (!item.getClass().equals(JavaProblem.class)) {
      errorTableClick(item);
    }

    JavaProblem p = (JavaProblem) item;

//    MouseEvent evt = null;
    String[] suggs = p.getImportSuggestions();
    if (suggs != null && suggs.length > 0) {
//      String t = p.getMessage() + "(Import Suggestions available)";
//      FontMetrics fm = getFontMetrics(getFont());
//      int x1 = fm.stringWidth(p.getMessage());
//      int x2 = fm.stringWidth(t);
//      if (evt.getX() > x1 && evt.getX() < x2) {
      String[] list = p.getImportSuggestions();
      String className = list[0].substring(list[0].lastIndexOf('.') + 1);
      String[] temp = new String[list.length];
      for (int i = 0; i < list.length; i++) {
        temp[i] = "<html>Import '" +  className + "' <font color=#777777>(" + list[i] + ")</font></html>";
      }
      //        showImportSuggestion(temp, evt.getXOnScreen(), evt.getYOnScreen() - 3 * getFont().getSize());
      Point mouse = MouseInfo.getPointerInfo().getLocation();
      showImportSuggestion(temp, mouse.x, mouse.y);
    } else {
      errorTableClick(item);
    }
  }


  JFrame frmImportSuggest;

  private void showImportSuggestion(String[] list, int x, int y) {
    if (frmImportSuggest != null) {
//      frmImportSuggest.setVisible(false);
//      frmImportSuggest = null;
      return;
    }
    final JList<String> classList = new JList<>(list);
    classList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    frmImportSuggest = new JFrame();

    frmImportSuggest.setUndecorated(true);
    frmImportSuggest.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBackground(Color.WHITE);
    frmImportSuggest.setBackground(Color.WHITE);
    panel.add(classList);
    JLabel label = new JLabel("<html><div alight = \"left\"><font size = \"2\"><br>(Click to insert)</font></div></html>");
    label.setBackground(Color.WHITE);
    label.setHorizontalTextPosition(SwingConstants.LEFT);
    panel.add(label);
    panel.validate();
    frmImportSuggest.getContentPane().add(panel);
    frmImportSuggest.pack();

    classList.addListSelectionListener(e -> {
      if (classList.getSelectedValue() != null) {
        try {
          String t = classList.getSelectedValue().trim();
          Messages.log(t);
          int x1 = t.indexOf('(');
          String impString = "import " + t.substring(x1 + 1, t.indexOf(')')) + ";\n";
          int ct = getSketch().getCurrentCodeIndex();
          getSketch().setCurrentCode(0);
          getTextArea().getDocument().insertString(0, impString, null);
          getSketch().setCurrentCode(ct);
        } catch (BadLocationException ble) {
          Messages.log("Failed to insert import");
          ble.printStackTrace();
        }
      }
      frmImportSuggest.setVisible(false);
      frmImportSuggest.dispose();
      frmImportSuggest = null;
    });

    frmImportSuggest.addWindowFocusListener(new WindowFocusListener() {

      @Override
      public void windowLostFocus(WindowEvent e) {
        if (frmImportSuggest != null) {
          frmImportSuggest.dispose();
          frmImportSuggest = null;
        }
      }

      @Override
      public void windowGainedFocus(WindowEvent e) {

      }
    });

    frmImportSuggest.setLocation(x, y);
    frmImportSuggest.setBounds(x, y, 250, 100);
    frmImportSuggest.pack();
    frmImportSuggest.setVisible(true);
  }


  @Override
  public void applyPreferences() {
    super.applyPreferences();

    if (jmode != null) {
      jmode.loadPreferences();
      Messages.log("Applying prefs");
      // trigger it once to refresh UI
      //pdex.preferencesChanged();
      errorChecker.preferencesChanged();
      sketchChanged();
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  // TWEAK MODE

  static final String PREF_TWEAK_PORT = "tweak.port";
  static final String PREF_TWEAK_SHOW_CODE = "tweak.showcode";

  public String[] baseCode;
  TweakClient tweakClient;


  protected void startTweakMode() {
    getJavaTextArea().startTweakMode();
  }


  protected void stopTweakMode(List<List<Handle>> handles) {
    tweakClient.shutdown();
    getJavaTextArea().stopTweakMode();

    // remove space from the code (before and after)
    //removeSpacesFromCode();

    // check which tabs were modified
    boolean[] tweakedTabs = getTweakedTabs(handles);
    boolean modified = anythingTrue(tweakedTabs);

    if (modified) {
      // ask to keep the values
      if (Messages.showYesNoQuestion(this, Language.text("tweak_mode"),
                                     Language.text("tweak_mode.keep_changes.line1"),
                                     Language.text("tweak_mode.keep_changes.line2")) == JOptionPane.YES_OPTION) {
        for (int i = 0; i < sketch.getCodeCount(); i++) {
          if (tweakedTabs[i]) {
            sketch.getCode(i).setModified(true);

          } else {
            // load the saved code of tabs that didn't change
            // (there might be formatting changes that should not be saved)
            sketch.getCode(i).setProgram(sketch.getCode(i).getSavedProgram());
            /* Wild Hack: set document to null so the text editor will refresh
               the program contents when the document tab is being clicked */
            sketch.getCode(i).setDocument(null);

            if (i == sketch.getCurrentCodeIndex()) {
              // this will update the current code
              setCode(sketch.getCurrentCode());
            }
          }
        }

        // save the sketch
        try {
          sketch.save();
        } catch (IOException e) {
          Messages.showWarning("Error", "Could not save the modified sketch.", e);
        }

        // repaint the editor header (show the modified tabs)
        header.repaint();
        textarea.invalidate();

      } else {  // no or canceled = don't keep changes
        loadSavedCode();
        // update the painter to draw the saved (old) code
        textarea.invalidate();
      }
    } else {
      // number values were not modified, but we need to load
      // the saved code because of some formatting changes
      loadSavedCode();
      textarea.invalidate();
    }
  }


  static private boolean anythingTrue(boolean[] list) {
    for (boolean b : list) {
      if (b) return true;
    }
    return false;
  }


  protected void updateInterface(List<List<Handle>> handles,
                              List<List<ColorControlBox>> colorBoxes) {
    getJavaTextArea().updateInterface(handles, colorBoxes);
  }


  static private boolean[] getTweakedTabs(List<List<Handle>> handles) {
    boolean[] outgoing = new boolean[handles.size()];

    for (int i = 0; i < handles.size(); i++) {
      for (Handle h : handles.get(i)) {
        if (h.valueChanged()) {
          outgoing[i] = true;
        }
      }
    }
    return outgoing;
  }


  protected void initBaseCode() {
    SketchCode[] code = sketch.getCode();

    baseCode = new String[code.length];
    for (int i = 0; i < code.length; i++) {
      baseCode[i] = code[i].getSavedProgram();
    }
  }


  protected void initEditorCode(List<List<Handle>> handles) {
    SketchCode[] sketchCode = sketch.getCode();
    for (int tab=0; tab<baseCode.length; tab++) {
        // beautify the numbers
        int charInc = 0;
        String code = baseCode[tab];

        for (Handle n : handles.get(tab)) {
          int s = n.startChar + charInc;
          int e = n.endChar + charInc;
          String newStr = n.strNewValue;
          code = replaceString(code, s, e, newStr);
          n.newStartChar = n.startChar + charInc;
          charInc += n.strNewValue.length() - n.strValue.length();
          n.newEndChar = n.endChar + charInc;
        }

        sketchCode[tab].setProgram(code);
        /* Wild Hack: set document to null so the text editor will refresh
           the program contents when the document tab is being clicked */
        sketchCode[tab].setDocument(null);
      }

    // this will update the current code
    setCode(sketch.getCurrentCode());
  }


  private void loadSavedCode() {
    //SketchCode[] code = sketch.getCode();
    for (SketchCode code : sketch.getCode()) {
      if (!code.getProgram().equals(code.getSavedProgram())) {
        code.setProgram(code.getSavedProgram());
        /* Wild Hack: set document to null so the text editor will refresh
           the program contents when the document tab is being clicked */
        code.setDocument(null);
      }
    }
    // this will update the current code
    setCode(sketch.getCurrentCode());
  }


  /**
   * Replace all numbers with variables and add code to initialize
   * these variables and handle update messages.
   */
  protected boolean automateSketch(Sketch sketch, SketchParser parser) {
    SketchCode[] code = sketch.getCode();

    List<List<Handle>> handles = parser.allHandles;

    if (code.length < 1) {
      return false;
    }

    if (handles.size() == 0) {
      return false;
    }

    int afterSizePos = SketchParser.getAfterSizePos(baseCode[0]);
    if (afterSizePos < 0) {
      return false;
    }

    // get port number from preferences.txt
    int port;
    String portStr = Preferences.get(PREF_TWEAK_PORT);
    if (portStr == null) {
      Preferences.set(PREF_TWEAK_PORT, "auto");
      portStr = "auto";
    }

    if (portStr.equals("auto")) {
      // random port for udp (0xc000 - 0xffff)
      port = (int)(Math.random()*0x3fff) + 0xc000;
    } else {
      port = Preferences.getInteger(PREF_TWEAK_PORT);
    }

    // create the client that will send the new values to the sketch
    tweakClient = new TweakClient(port);
    // update handles with a reference to the client object
    for (int tab=0; tab<code.length; tab++) {
      for (Handle h : handles.get(tab)) {
        h.setTweakClient(tweakClient);
      }
    }

    // Copy current program to interactive program
    // modify the code below, replace all numbers with their variable names
    // loop through all tabs in the current sketch
    for (int tab=0; tab<code.length; tab++) {
      int charInc = 0;
      String c = baseCode[tab];
      for (Handle n : handles.get(tab)) {
        // replace number value with a variable
        c = replaceString(c, n.startChar + charInc, n.endChar + charInc, n.name);
        charInc += n.name.length() - n.strValue.length();
      }
      code[tab].setProgram(c);
    }

    // add the main header to the code in the first tab
    String c = code[0].getProgram();

    // header contains variable declaration, initialization,
    // and OSC listener function
    String header;
    header = """


      /*************************/
      /* MODIFIED BY TWEAKMODE */
      /*************************/


      """;

    // add needed OSC imports and the global OSC object
    header += "import java.net.*;\n";
    header += "import java.io.*;\n";
    header += "import java.nio.*;\n\n";

    // write a declaration for int and float arrays
    int numOfInts = howManyInts(handles);
    int numOfFloats = howManyFloats(handles);
    if (numOfInts > 0) {
      header += "int[] tweakmode_int = new int["+numOfInts+"];\n";
    }
    if (numOfFloats > 0) {
      header += "float[] tweakmode_float = new float["+numOfFloats+"];\n\n";
    }

    // add the server code that will receive the value change messages
//    header += TweakClient.getServerCode(port, numOfInts>0, numOfFloats>0);
    header += "TweakModeServer tweakmode_Server;\n";

    header += "void tweakmode_initAllVars() {\n";
    //for (int i=0; i<handles.length; i++) {
    for (List<Handle> list : handles) {
      //for (Handle n : handles[i]) {
      for (Handle n : list) {
        header += "  " + n.name + " = " + n.strValue + ";\n";
      }
    }
    header += "}\n\n";
    header += "void tweakmode_initCommunication() {\n";
    header += " tweakmode_Server = new TweakModeServer();\n";
    header += " tweakmode_Server.setup();\n";
    header += " tweakmode_Server.start();\n";
    header += "}\n";

    header += "\n\n\n\n\n";

    // add call to our initAllVars and initOSC functions
    // from the setup() function.
    String addToSetup = """



        /* TWEAKMODE */
          tweakmode_initAllVars();
          tweakmode_initCommunication();
        /* TWEAKMODE */

      """;

    afterSizePos = SketchParser.getAfterSizePos(c);
    c = replaceString(c, afterSizePos, afterSizePos, addToSetup);

    // Server code defines a class, so it should go later in the sketch
    String serverCode =
      TweakClient.getServerCode(port, numOfInts>0, numOfFloats>0);
    code[0].setProgram(header + c + serverCode);

    // print out modified code
    String showModCode = Preferences.get(PREF_TWEAK_SHOW_CODE);
    if (showModCode == null) {
      Preferences.setBoolean(PREF_TWEAK_SHOW_CODE, false);
    }

    if (Preferences.getBoolean(PREF_TWEAK_SHOW_CODE)) {
      System.out.println("\nTweakMode modified code:\n");
      for (int i=0; i<code.length; i++) {
        System.out.println("tab " + i + "\n");
        System.out.println("=======================================================\n");
        System.out.println(code[i].getProgram());
      }
    }
    return true;
  }


  static private String replaceString(String str, int start, int end, String put) {
    return str.substring(0, start) + put + str.substring(end);
  }


  //private int howManyInts(ArrayList<Handle> handles[])
  static private int howManyInts(List<List<Handle>> handles) {
    int count = 0;
    for (List<Handle> list : handles) {
      for (Handle n : list) {
        if ("int".equals(n.type) || "hex".equals(n.type) || "webcolor".equals(n.type)) {
          count++;
        }
      }
    }
    return count;
  }


  //private int howManyFloats(ArrayList<Handle> handles[])
  static private int howManyFloats(List<List<Handle>> handles) {
    int count = 0;
    for (List<Handle> list : handles) {
      for (Handle n : list) {
        if ("float".equals(n.type)) {
          count++;
        }
      }
    }
    return count;
  }
}
