/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2010-23 Ben Fry and Casey Reas
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

package processing.mode.java;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import processing.app.*;
import processing.app.ui.Editor;
import processing.app.ui.EditorException;
import processing.app.ui.EditorState;

import processing.mode.java.runner.Runner;
import processing.mode.java.tweak.SketchParser;


public class JavaMode extends Mode {

  public Editor createEditor(Base base, String path,
                             EditorState state) throws EditorException {
    return new JavaEditor(base, path, state, this);
  }


  public JavaMode(Base base, File folder) {
    super(base, folder);

    loadPreferences();
    loadSuggestionsMap();
  }


  public String getTitle() {
    return "Java";
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public File[] getExampleCategoryFolders() {
    return new File[] {
      new File(examplesFolder, "Basics"),
      new File(examplesFolder, "Topics"),
      new File(examplesFolder, "Demos"),
      new File(examplesFolder, "Books")
    };
  }


  public String getDefaultExtension() {
    return "pde";
  }


  public String[] getExtensions() {
    return new String[] { "pde", "java" };
  }


  public String[] getIgnorable() {
    // folder names for exported applications
    return Platform.getSupportedVariants().keyArray();
  }


  public Library getCoreLibrary() {
    if (coreLibrary == null) {
      File coreFolder = Platform.getContentFile("core");
      coreLibrary = new Library(coreFolder);
    }
    return coreLibrary;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .



  /** Handles the standard Java "Run" or "Present" */
  public Runner handleLaunch(Sketch sketch, RunnerListener listener,
                             final boolean present) throws SketchException {
    JavaBuild build = new JavaBuild(sketch);
//    String appletClassName = build.build(false);
    String appletClassName = build.build(true);
    if (appletClassName != null) {
      final Runner runtime = new Runner(build, listener);
      new Thread(() -> {
        // these block until finished
        if (present) {
          runtime.present(null);
        } else {
          runtime.launch(null);
        }
      }).start();
      return runtime;
    }
    return null;
  }


  /** Start a sketch in tweak mode */
  public Runner handleTweak(Sketch sketch,
                            RunnerListener listener,
                            JavaEditor editor) throws SketchException {
    // first try to build the unmodified code
    JavaBuild build = new JavaBuild(sketch);
//    String appletClassName = build.build(false);
    String appletClassName = build.build(true);
    if (appletClassName == null) {
      // unmodified build failed, so fail
      return null;
    }

    // if compilation passed, modify the code and build again
    // save the original sketch code of the user
    editor.initBaseCode();
    // check for "// tweak" comment in the sketch
    boolean requiresTweak = SketchParser.containsTweakComment(editor.baseCode);
    // parse the saved sketch to get all (or only with "//tweak" comment) numbers
    final SketchParser parser = new SketchParser(editor.baseCode, requiresTweak);

    // add our code to the sketch
    final boolean launchInteractive = editor.automateSketch(sketch, parser);

    build = new JavaBuild(sketch);
    appletClassName = build.build(false);

    if (appletClassName != null) {
      final Runner runtime = new Runner(build, listener);
      new Thread(() -> {
        runtime.launch(null);
        // next lines are executed when the sketch quits
        if (launchInteractive) {
          // fix swing deadlock issue: https://github.com/processing/processing/issues/3928
          EventQueue.invokeLater(() -> {
            editor.initEditorCode(parser.allHandles);
            editor.stopTweakMode(parser.allHandles);
          });
        }
      }).start();

      if (launchInteractive) {
        // fix swing deadlock issue: https://github.com/processing/processing/issues/3928
        EventQueue.invokeLater(() -> {
          // replace editor code with baseCode
          editor.initEditorCode(parser.allHandles);
          editor.updateInterface(parser.allHandles, parser.colorBoxes);
          editor.startTweakMode();
        });
      }
      return runtime;
    }
    return null;
  }


  public boolean handleExportApplication(Sketch sketch) throws SketchException, IOException {
    JavaBuild build = new JavaBuild(sketch);
    return build.exportApplication();
  }


  /**
   * Any modes that extend JavaMode can override this method to add additional
   * JARs to be included in the classpath for code completion and error checking
   * @return searchPath: file-paths separated by File.pathSeparatorChar
   */
  public String getSearchPath() {
    return getCoreLibrary().getJarPath();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  // Merged from ExperimentalMode


  static volatile boolean errorCheckEnabled = true;
  static volatile boolean warningsEnabled = true;
  static volatile boolean errorLogsEnabled = false;

  static volatile boolean autoSaveEnabled = true;
  static volatile boolean autoSavePromptEnabled = true;
  static volatile boolean defaultAutoSaveEnabled = true;

  static volatile boolean codeCompletionsEnabled = true;
  static volatile boolean ccTriggerEnabled = false;
  static volatile boolean importSuggestEnabled = true;
  static volatile boolean inspectModeHotkeyEnabled = true;

  static public int autoSaveInterval = 3; //in minutes

  static public final String prefErrorCheck = "pdex.errorCheckEnabled";
  static public final String prefWarnings = "pdex.warningsEnabled";
  static public final String prefErrorLogs = "pdex.writeErrorLogs";

  static public final String prefAutoSave = "pdex.autoSave.autoSaveEnabled";
  static public final String prefAutoSaveInterval = "pdex.autoSaveInterval";
  static public final String prefAutoSavePrompt = "pdex.autoSave.promptDisplay";
  static public final String prefDefaultAutoSave = "pdex.autoSave.autoSaveByDefault";

  static public final String COMPLETION_PREF = "pdex.completion";
  static public final String COMPLETION_TRIGGER_PREF = "pdex.completion.trigger";
  static public final String SUGGEST_IMPORTS_PREF = "pdex.suggest.imports";
  static public final String INSPECT_MODE_HOTKEY_PREF = "pdex.inspectMode.hotkey";

  /**
   * Stores the white list/black list of allowed/blacklisted imports.
   * These are defined in suggestions.txt in java mode folder.
   */
  private final Set<String> includedSuggestions = ConcurrentHashMap.newKeySet();
  private final Set<String> excludedSuggestions = ConcurrentHashMap.newKeySet();


  boolean includeSuggestion(String impName) {
    return includedSuggestions.contains(impName);
  }


  boolean excludeSuggestion(String impName) {
    return excludedSuggestions.contains(impName);
  }


  /*
  static boolean checkSuggestion(String mapName, String impName) {
    return suggestionsMap.containsKey(mapName) &&
      suggestionsMap.get(mapName).contains(impName);
  }
  */


  public void loadPreferences() {
    Messages.log("Load PDEX prefs");

    errorCheckEnabled = Preferences.getBoolean(prefErrorCheck);
    warningsEnabled = Preferences.getBoolean(prefWarnings);
    errorLogsEnabled = Preferences.getBoolean(prefErrorLogs);

    autoSaveEnabled = Preferences.getBoolean(prefAutoSave);
    autoSaveInterval = Preferences.getInteger(prefAutoSaveInterval);
    autoSavePromptEnabled = Preferences.getBoolean(prefAutoSavePrompt);
    defaultAutoSaveEnabled = Preferences.getBoolean(prefDefaultAutoSave);

    codeCompletionsEnabled = Preferences.getBoolean(COMPLETION_PREF);
    ccTriggerEnabled = Preferences.getBoolean(COMPLETION_TRIGGER_PREF);
    importSuggestEnabled = Preferences.getBoolean(SUGGEST_IMPORTS_PREF);
    inspectModeHotkeyEnabled = Preferences.getBoolean(INSPECT_MODE_HOTKEY_PREF);
  }


  public void savePreferences() {
    Messages.log("Saving PDEX prefs");

    Preferences.setBoolean(prefErrorCheck, errorCheckEnabled);
    Preferences.setBoolean(prefWarnings, warningsEnabled);
    Preferences.setBoolean(prefErrorLogs, errorLogsEnabled);

    Preferences.setBoolean(prefAutoSave, autoSaveEnabled);
    Preferences.setInteger(prefAutoSaveInterval, autoSaveInterval);
    Preferences.setBoolean(prefAutoSavePrompt, autoSavePromptEnabled);
    Preferences.setBoolean(prefDefaultAutoSave, defaultAutoSaveEnabled);

    Preferences.setBoolean(COMPLETION_PREF, codeCompletionsEnabled);
    Preferences.setBoolean(COMPLETION_TRIGGER_PREF, ccTriggerEnabled);
    Preferences.setBoolean(SUGGEST_IMPORTS_PREF, importSuggestEnabled);
    Preferences.setBoolean(INSPECT_MODE_HOTKEY_PREF, inspectModeHotkeyEnabled);
  }


  private void loadSuggestionsMap() {
    Collections.addAll(includedSuggestions, getSuggestionIncludeList());
    Collections.addAll(excludedSuggestions, getSuggestionExcludeList());
  }


  // broken out so that it can be overridden by Android, etc
  protected String[] getSuggestionIncludeList() {
    return new String[] {
      "processing.core.PApplet",
      "processing.core.PFont",
      "processing.core.PGraphics",
      "processing.core.PImage",
      "processing.core.PMatrix2D",
      "processing.core.PMatrix3D",
      "processing.core.PStyle",
      "processing.core.PVector",
      "processing.core.PShape",
      "processing.core.PGraphicsJava2D",
      "processing.core.PGraphics2D",
      "processing.core.PGraphics3D",
      "processing.data.FloatDict",
      "processing.data.FloatList",
      "processing.data.IntDict",
      "processing.data.IntList",
      "processing.data.JSONArray",
      "processing.data.JSONObject",
      "processing.data.StringDict",
      "processing.data.StringList",
      "processing.data.Table",
      "processing.data.XML",
      "processing.event.Event",
      "processing.event.KeyEvent",
      "processing.event.MouseEvent",
      "processing.event.TouchEvent",
      "processing.opengl.PShader",
      "processing.opengl.PGL",

      "java.util.ArrayList",
      "java.io.BufferedReader",
      "java.util.HashMap",
      "java.io.PrintWriter",
      "java.lang.String"
    };
  }


  // broken out so that it can be overridden by Android, etc
  protected String[] getSuggestionExcludeList() {
    return new String[] {
      "processing.core.PGraphicsRetina2D",
      "processing.core.PShapeOBJ",
      "processing.core.PShapeSVG",
      "processing.data.Sort",
      "processing.opengl.FrameBuffer",
      "processing.opengl.LinePath",
      "processing.opengl.LinePath.PathIterator",
      "processing.opengl.LineStroker",
      "processing.opengl.PGraphicsOpenGL"
    };
  }


  /*
  private void loadSuggestionsMap() {
    File suggestionsFile = new File(getFolder(), "suggestions.txt");
    if (suggestionsFile.exists()) {
      String[] lines = PApplet.loadStrings(suggestionsFile);
      if (lines != null) {
        for (String line : lines) {
          line = line.trim();
          if (line.length() > 0 && !line.startsWith("#")) {
            int equals = line.indexOf('=');
            if (equals != -1) {
              String key = line.substring(0, equals).trim();
              String value = line.substring(equals + 1).trim();

              if (key.equals("include")) {
                includedSuggestions.add(value);
              } else if (key.equals("exclude")) {
                excludedSuggestions.add(value);
              } else {
                Messages.loge("Should be include or exclude: " + key);
              }
            } else {
              Messages.loge("Bad line found in suggestions file: " + line);
            }
          }
        }
      }
    } else {
      Messages.loge("Suggestions file not found at " + suggestionsFile);
    }
  }
  */
}
