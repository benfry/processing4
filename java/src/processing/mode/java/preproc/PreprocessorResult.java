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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import processing.mode.java.pdex.ImportStatement;
import processing.mode.java.pdex.TextTransform;
import processing.mode.java.preproc.issue.PdePreprocessIssue;


/**
 * Result of sketch Preprocessing.
 */
public class PreprocessorResult {

  private final int headerOffset;
  private final String className;
  private final List<String> importStatementsStr;
  private final List<ImportStatement> importStatements;
  private final PdePreprocessor.Mode programType;
  private final List<TextTransform.Edit> edits;
  private final List<PdePreprocessIssue> preprocessIssues;
  private final String sketchWidth;
  private final String sketchHeight;

  /**
   * Create a new PreprocessorResult indicating that there were issues in preprocessing.
   *
   * @param newPreprocessIssues The list of issues encoutnered.
   * @return New preprocessor result.
   */
  public static PreprocessorResult reportPreprocessIssues(
      List<PdePreprocessIssue> newPreprocessIssues) {

    assert newPreprocessIssues.size() > 0;
    return new PreprocessorResult(newPreprocessIssues);
  }

  /**
   * Create a new preprocessing result.
   *
   * @param newProgramType The type of program that has be preprocessed.
   * @param newHeaderOffset The offset (in number of chars) from the start of the program at which
   *    the header finishes.
   * @param newClassName The name of the class containing the sketch.
   * @param newExtraImports Additional imports beyond the defaults and code folder.
   * @param newEdits The edits made during preprocessing.
   * @param newSketchWidth The width of the sketch in pixels or special value like displayWidth;
   * @param newSketchHeight The height of the sketch in pixels or special value like displayWidth;
   */
  public PreprocessorResult(PdePreprocessor.Mode newProgramType, int newHeaderOffset,
        String newClassName, List<String> newExtraImports, List<TextTransform.Edit> newEdits,
        String newSketchWidth, String newSketchHeight) {

    if (newClassName == null) {
      throw new RuntimeException("Could not find main class");
    }

    headerOffset = newHeaderOffset;
    className = newClassName;
    importStatementsStr = Collections.unmodifiableList(new ArrayList<>(newExtraImports));
    programType = newProgramType;
    edits = newEdits;
    preprocessIssues = new ArrayList<>();

    importStatements = importStatementsStr.stream()
        .map(ImportStatement::parse)
        .collect(Collectors.toList());

    sketchWidth = newSketchWidth;
    sketchHeight = newSketchHeight;
  }

  /**
   * Private constructor allowing creation of result indicating preprocess issues.
   *
   * @param newPreprocessIssues The list of preprocess issues encountered.
   */
  private PreprocessorResult(List<PdePreprocessIssue> newPreprocessIssues) {
    preprocessIssues = Collections.unmodifiableList(newPreprocessIssues);
    headerOffset = 0;
    className = "unknown";
    importStatementsStr = new ArrayList<>();
    programType = PdePreprocessor.Mode.STATIC;
    edits = new ArrayList<>();
    importStatements = new ArrayList<>();

    sketchWidth = null;
    sketchHeight = null;
  }

  /**
   * Get the list of preprocess issues encountered.
   *
   * @return List of preprocess issues encountered.
   */
  public List<PdePreprocessIssue> getPreprocessIssues() {
    return preprocessIssues;
  }

  /**
   * Get the end point of the header.
   *
   * @return The offset (in number of lines) from the start of the program at which the header
   *    finishes.
   */
  public int getHeaderOffset() {
    return headerOffset;
  }

  /**
   * Get the name of the Java class containing the sketch after preprocessing.
   *
   * @return The name of the class containing the sketch.
   */
  public String getClassName() {
    return className;
  }

  /**
   * Get the imports beyond the default set that are included in the sketch.
   *
   * @return Additional imports beyond the defaults and code folder.
   */
  public List<String> getImportStatementsStr() {
    return importStatementsStr;
  }

  /**
   * Get the type of program that was parsed.
   *
   * @return Type of program parsed like STATIC (no function) or ACTIVE.
   */
  public PdePreprocessor.Mode getProgramType() {
    return programType;
  }

  /**
   * Get the edits generated during preprocessing.
   *
   * @return List of edits generated during preprocessing.
   */
  public List<TextTransform.Edit> getEdits() {
    return edits;
  }

  /**
   * Get the found import statements as {ImportStatement}s.
   *
   * @return The import statements found for the user.
   */
  public List<ImportStatement> getImportStatements() {
    return importStatements;
  }

  /**
   * Get the user provided width of this sketch.
   *
   * @return The width of the sketch in pixels or special value like displayWidth or null if none
   *    given.
   */
  public String getSketchWidth() {
    return sketchWidth;
  }

  /**
   * Get the user provided height of this sketch.
   *
   * @return The height of the sketch in pixels or special value like displayHeight or null if none
   *    given.
   */
  public String getSketchHeight() {
    return sketchWidth;
  }
}
