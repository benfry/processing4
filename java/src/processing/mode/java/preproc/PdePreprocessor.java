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

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import processing.app.Preferences;
import processing.app.SketchException;
import processing.mode.java.preproc.code.ImportUtil;
import processing.mode.java.preproc.issue.PdeIssueEmitter;
import processing.mode.java.preproc.issue.PdePreprocessIssue;


/**
 * Utility to preprocess sketches prior to comilation.
 */
public class PdePreprocessor {

  public static enum Mode {
    STATIC, ACTIVE, JAVA
  }

  private String sketchName;
  private int tabSize;

  private boolean hasMain;

  private final boolean isTesting;

  /**
   * Create a new preprocessor.
   *
   * @param sketchName The name of the sketch.
   */
  public PdePreprocessor(final String sketchName) {
    this(sketchName, Preferences.getInteger("editor.tabs.size"), false);
  }

  /**
   * Create a new preprocessor.
   *
   * @param sketchName The name of the sketch.
   * @param tabSize The number of tabs.
   */
  public PdePreprocessor(final String sketchName, final int tabSize) {
    this(sketchName, tabSize, false);
  }

  /**
   * Create a new preprocessor.
   *
   * @param sketchName The name of the sketch.
   * @param tabSize The number of tabs.
   * @param isTesting Flag indicating if this is running in unit tests (true) or in production
   *    (false).
   */
  public PdePreprocessor(final String sketchName, final int tabSize, boolean isTesting) {
    this.sketchName = sketchName;
    this.tabSize = tabSize;
    this.isTesting = isTesting;
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
    PdeParseTreeListener listener = createListener(tokens, sketchName);
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

    hasMain = listener.foundMain();

    return listener.getResult();
  }

  /**
   * Determine if the main method was found during preprocessing.
   *
   * @return True if a main method was found. False otherwise.
   */
  public boolean hasMain() {
    return hasMain;
  }

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
