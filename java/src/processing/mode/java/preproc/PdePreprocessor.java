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

package processing.mode.java.preproc;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import processing.app.Preferences;
import processing.app.SketchException;
import processing.mode.java.preproc.code.ImportUtil;
import processing.mode.java.preproc.issue.PdeIssueEmitter;
import processing.mode.java.preproc.issue.PdePreprocessIssue;


/**
 * Utility to preprocess sketches prior to compilation.
 *
 * <p>
 * This preprocessor assists with
 * </p>
 */
public class PdePreprocessor {

  /**
   * The mode that the sketch uses to run.
   */
  public static enum Mode {
    /**
     * Sketch without draw, setup, or settings functions where code is run as if the body of a
     * method without any enclosing types. This code will not define its enclosing class or method.
     */
    STATIC,

    /**
     * Sketch using draw, setup, and / or settings where the code is run as if defining the body
     * of a class. This code will not define its enclosing class but it will define its enclosing
     * method.
     */
    ACTIVE,

    /**
     * Sketch written like typical Java where the code is run such that it defines the enclosing
     * classes itself.
     */
    JAVA
  }

  private final String sketchName;
  private final int tabSize;
  private final boolean isTesting;
  private final ParseTreeListenerFactory listenerFactory;

  private boolean foundMain;

  /**
   * Create a new PdePreprocessorBuilder for a sketch of the given name.
   *
   * Create a new builder to help instantiate a preprocessor for a sketch of the given name. Use
   * this builder to configure settings of the preprocessor before building.
   *
   * @param sketchName The name of the sketch for which a preprocessor will be built.
   * @return Builder to create a preprocessor for the sketch of the given name.
   */
  public static PdePreprocessorBuilder builderFor(String sketchName) {
    return new PdePreprocessorBuilder(sketchName);
  }

  /**
   * Create a new preprocessor.
   *
   * Create a new preprocessor that will use the following set of configuration values to process
   * a parse tree. This object should be instantiated by calling {builderFor}.
   *
   * @param newSketchName The name of the sketch.
   * @param newTabSize The number of spaces within a tab.
   * @param newIsTesting Flag indicating if this is running in unit tests (true) or in production
   *    (false).
   * @param newFactory The factory to use for building the ANTLR tree traversal listener where
   *    preprocessing edits should be made.
   */
  private PdePreprocessor(final String newSketchName, final int newTabSize, boolean newIsTesting,
        final ParseTreeListenerFactory newFactory) {

    sketchName = newSketchName;
    tabSize = newTabSize;
    isTesting = newIsTesting;
    listenerFactory = newFactory;
  }

  /**
   * Create the preprocessed sketch code without any code folder packages.
   *
   * @param out The writer into which the preprocessed code should be written. This is
   *    preferred over returning the full string result as this string may be large.
   * @param program The sketch ("PDE") code.
   * @return Information about the preprocessing operation.
   */
  public PreprocessorResult write(final Writer out, String program) throws SketchException {
    return write(out, program, null);
  }

  /**
   * Create the preprocessed sketch code.
   *
   * @param outWriter The writer into which the preprocessed code should be written. This is
   *    preferred over returning the full string result as this string may be large.
   * @param inProgram The sketch ("PDE") code.
   * @param codeFolderPackages The packages included by default for the user by virtue of those
   *    packages being in the code folder.
   * @return Information about the preprocessing operation.
   */
  public PreprocessorResult write(Writer outWriter, String inProgram,
                                  Iterable<String> codeFolderPackages)
                                    throws SketchException {

    // Determine inports
    ArrayList<String> codeFolderImports = new ArrayList<>();
    if (codeFolderPackages != null) {
      for (String item : codeFolderPackages) {
        String fullItem;

        if (item.endsWith(".*")) {
          fullItem = item;
        } else {
          fullItem = item + ".*";
        }

        codeFolderImports.add(fullItem);
      }
    }

    if (Preferences.getBoolean("preproc.substitute_unicode")) {
      inProgram = substituteUnicode(inProgram);
    }

    // Ensure ends with single newline
    while (inProgram.endsWith("\n")) {
      inProgram = inProgram.substring(0, inProgram.length() - 1);
    }

    inProgram = inProgram + "\n";

    // Lexer
    CommonTokenStream tokens;
    {
      CharStream antlrInStream = CharStreams.fromString(inProgram);
      ProcessingLexer lexer = new ProcessingLexer(antlrInStream);
      lexer.removeErrorListeners();
      tokens = new CommonTokenStream(lexer);
    }

    // Parser
    final List<PdePreprocessIssue> preprocessIssues = new ArrayList<>();
    final List<PdePreprocessIssue> treeIssues = new ArrayList<>();
    PdeParseTreeListener listener = listenerFactory.build(tokens, sketchName, tabSize);
    listener.setTesting(isTesting);
    listener.setCoreImports(ImportUtil.getCoreImports());
    listener.setDefaultImports(ImportUtil.getDefaultImports());
    listener.setCodeFolderImports(codeFolderImports);
    listener.setTreeErrorListener((x) -> { treeIssues.add(x); });

    final String finalInProgram = inProgram;
    ParseTree tree;
    {
      ProcessingParser parser = new ProcessingParser(tokens);
      parser.removeErrorListeners();
      parser.addErrorListener(new PdeIssueEmitter(
          (x) -> { preprocessIssues.add(x); },
          () -> finalInProgram
      ));
      parser.setBuildParseTree(true);
      tree = parser.processingSketch();

      if (preprocessIssues.size() > 0) {
        return PreprocessorResult.reportPreprocessIssues(preprocessIssues);
      }
    }

    ParseTreeWalker treeWalker = new ParseTreeWalker();
    treeWalker.walk(listener, tree);

    // Check for issues encountered in walk
    if (treeIssues.size() > 0) {
      return PreprocessorResult.reportPreprocessIssues(treeIssues);
    }

    // Return resulting program
    String outputProgram = listener.getOutputProgram();
    PrintWriter outPrintWriter = new PrintWriter(outWriter);
    outPrintWriter.print(outputProgram);

    foundMain = listener.foundMain();

    return listener.getResult();
  }

  /**
   * Determine if the main method was found during preprocessing.
   *
   * @return True if a main method was found. False otherwise.
   */
  public boolean hasMain() {
    return foundMain;
  }

  /* ========================
   * === Type Definitions ===
   * ========================
   *
   * The preprocessor has a sizable number of parameters, including those that can modify its
   * internal behavior. These supporting types help initialize this object and provide hooks for
   * behavior modifications.
   */

  /**
   * Builder to help instantiate a PdePreprocessor.
   *
   * The PdePreprocessor includes a number of parameters, including some behavioral parameters that
   * change how the parse tree is processed. This builder helps instantiate this object by providing
   * reasonable defaults for most values but allowing client to (especially modes) to override those
   * defaults.
   */
  public static class PdePreprocessorBuilder {

    private final String sketchName;
    private Optional<Integer> tabSize;
    private Optional<Boolean> isTesting;
    private Optional<ParseTreeListenerFactory> parseTreeFactory;

    private PdePreprocessorBuilder(String newSketchName) {
      sketchName = newSketchName;
      tabSize = Optional.empty();
      isTesting = Optional.empty();
      parseTreeFactory = Optional.empty();
    }

    /**
     * Set how large the tabs should be.
     *
     * @param newTabSize The number of spaces in a tab.
     * @return This builder for method chaining.
     */
    public PdePreprocessorBuilder setTabSize(int newTabSize) {
      tabSize = Optional.of(newTabSize);
      return this;
    }

    /**
     * Indicate if this preprocessor is running within unittests.
     *
     * @param newIsTesting Flag that, if true, will configure the preprocessor to run safely within
     *    a unit testing environment.
     * @return This builder for method chaining.
     */
    public PdePreprocessorBuilder setIsTesting(boolean newIsTesting) {
      isTesting = Optional.of(newIsTesting);
      return this;
    }

    /**
     * Specify how the parse tree listener should be built.
     *
     * The ANTLR parse tree listener is where the preprocessing edits are generated and some client
     * code (like modes) may need to override some of the preprocessing edit behavior. Specifying
     * this factory allows client code to replace the default PdeParseTreeListener that is used
     * during preprocessing.
     *
     * @param newFactory The factory to use in building a parse tree listener.
     * @return This builder for method chaining.
     */
    public PdePreprocessorBuilder setParseTreeFactory(ParseTreeListenerFactory newFactory) {
      parseTreeFactory = Optional.of(newFactory);
      return this;
    }

    /**
     * Build the preprocessor.
     *
     * @return Newly built preproceesor.
     */
    public PdePreprocessor build() {
      final int effectiveTabSize = tabSize.orElseGet(
          () -> Preferences.getInteger("editor.tabs.size")
      );

      final boolean effectiveIsTesting = isTesting.orElse(false);

      ParseTreeListenerFactory effectiveFactory = parseTreeFactory.orElse(
          PdeParseTreeListener::new
      );

      return new PdePreprocessor(
          sketchName,
          effectiveTabSize,
          effectiveIsTesting,
          effectiveFactory
      );
    }

  }

  /**
   * Factory which creates parse tree traversal listeners.
   *
   * The ANTLR parse tree listener is where the preprocessing edits are generated and some client
   * code (like modes) may need to override some of the preprocessing edit behavior. Specifying
   * this factory allows client code to replace the default PdeParseTreeListener that is used
   * during preprocessing.
   */
  public static interface ParseTreeListenerFactory {

    /**
     * Create a new processing listener.
     *
     * @param tokens The token stream with sketch code contents.
     * @param sketchName The name of the sketch that will be preprocessed.
     * @param tabSize The size (number of spaces) of the tabs.
     * @return The newly created listener.
     */
    PdeParseTreeListener build(CommonTokenStream tokens, String sketchName, int tabSize);

  }


  /* ==================================
   * === Internal Utility Functions ===
   * ==================================
   */

  /**
   * Factory function to create a {PdeParseTreeListener} for use in preprocessing
   *
   * @param tokens The token stream for which the listener needs to be created.
   * @param sketchName The name of the sketch being preprocessed.
   * @return Newly created listener suitable for use in this {PdePreprocessor}.
   */
  private PdeParseTreeListener createListener(CommonTokenStream tokens, String sketchName) {
    return new PdeParseTreeListener(tokens, sketchName, tabSize);
  }

  /**
   * Utility function to substitute non ascii characters for escaped unicode character sequences.
   *
   * @param program The program source in which to execute the replace.
   * @return The program source after replacement.
   */
  private static String substituteUnicode(String program) {
    // check for non-ascii chars (these will be/must be in unicode format)
    char p[] = program.toCharArray();
    int unicodeCount = 0;
    for (int i = 0; i < p.length; i++) {
      if (p[i] > 127)
        unicodeCount++;
    }
    if (unicodeCount == 0)
      return program;
    // if non-ascii chars are in there, convert to unicode escapes
    // add unicodeCount * 5.. replacing each unicode char
    // with six digit uXXXX sequence (xxxx is in hex)
    // (except for nbsp chars which will be a replaced with a space)
    int index = 0;
    char p2[] = new char[p.length + unicodeCount * 5];
    for (int i = 0; i < p.length; i++) {
      if (p[i] < 128) {
        p2[index++] = p[i];
      } else if (p[i] == 160) { // unicode for non-breaking space
        p2[index++] = ' ';
      } else {
        int c = p[i];
        p2[index++] = '\\';
        p2[index++] = 'u';
        char str[] = Integer.toHexString(c).toCharArray();
        // add leading zeros, so that the length is 4
        //for (int i = 0; i < 4 - str.length; i++) p2[index++] = '0';
        for (int m = 0; m < 4 - str.length; m++)
          p2[index++] = '0';
        System.arraycopy(str, 0, p2, index, str.length);
        index += str.length;
      }
    }
    return new String(p2, 0, index);
  }

}
