/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org
Copyright (c) 2012-15 The Processing Foundation

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License version 2
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation, Inc.
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/

package processing.mode.java;

import java.util.Optional;

import org.eclipse.jdt.core.compiler.IProblem;

import processing.app.Problem;


/**
 * Wrapper class for IProblem that stores the tabIndex and line number
 * according to its tab, including the original IProblem object
 */
public class JavaProblem implements Problem {
  /** Error Message. Processed form of IProblem.getMessage() */
  private final String message;

  /** The type of error - WARNING or ERROR. */
  private final int type;

  /** The tab number the error belongs to. */
  private final int tabIndex;

  /** Line number (pde code) of the error */
  private final int lineNumber;

  private Optional<Integer> startOffset;

  private Optional<Integer> stopOffset;

  /**
   * If the error is a 'cannot find type' contains the list of suggested imports
   */
  private String[] importSuggestions;

  static final int ERROR = 1;
  static final int WARNING = 2;


  public JavaProblem(String message, int type, int tabIndex, int lineNumber) {
    this.message = message;
    this.type = type;
    this.tabIndex = tabIndex;
    this.lineNumber = lineNumber;
    this.startOffset = Optional.empty();
    this.stopOffset = Optional.empty();
  }


  /**
   * @param iProblem - The IProblem which is being wrapped
   * @param tabIndex - The tab number to which the error belongs to
   * @param lineNumber - Line number(pde code) of the error
   * @param badCode - The code iProblem refers to.
   */
  static public JavaProblem fromIProblem(IProblem iProblem, int tabIndex,
                                         int lineNumber, String badCode) {
    int type = 0;
    if (iProblem.isError()) {
      type = ERROR;
    } else if (iProblem.isWarning()) {
      type = WARNING;
    }
    String message = CompileErrorMessageSimplifier.getSimplifiedErrorMessage(iProblem, badCode);
    return new JavaProblem(message, type, tabIndex, lineNumber);
  }


  public void setPDEOffsets(int startOffset, int stopOffset){
    this.startOffset = Optional.of(startOffset);
    this.stopOffset = Optional.of(stopOffset);
  }


  @Override
  public Optional<Integer> getTabStartOffset() {
    return startOffset;
  }


  @Override
  public Optional<Integer> getTabStopOffset() {
    return stopOffset;
  }

  @Override
  public Optional<Integer> getLineStartOffset() {
    return Optional.empty();
  }

  @Override
  public Optional<Integer> getLineStopOffset() {
    return Optional.empty();
  }

  @Override
  public boolean isError() {
    return type == ERROR;
  }


  @Override
  public boolean isWarning() {
    return type == WARNING;
  }


  @Override
  public String getMessage() {
    return message;
  }


  @Override
  public int getTabIndex() {
    return tabIndex;
  }


  @Override
  public int getLineNumber() {
    return lineNumber;
  }


  public String[] getImportSuggestions() {
    return importSuggestions;
  }


  public void setImportSuggestions(String[] a) {
    importSuggestions = a;
  }

  public boolean usesLineOffset() {
    return false;
  }

  @Override
  public String toString() {
    return "TAB " + tabIndex + ",LN " + lineNumber + "LN START OFF: "
        + startOffset + ",LN STOP OFF: " + stopOffset + ",PROB: "
        + message;
  }

  @Override
  public int computeTabStartOffset(LineToTabOffsetGetter strategy) {
    Optional<Integer> nativeTabStartOffset = getTabStartOffset();
    if (nativeTabStartOffset.isPresent()) {
      return nativeTabStartOffset.get();
    }

    Optional<Integer> lineStartOffset = getLineStartOffset();
    int lineOffset = strategy.get(getLineNumber());
    if (lineStartOffset.isPresent()) {
      return lineOffset + lineStartOffset.get();
    } else {
      return lineOffset;
    }
  }

  @Override
  public int computeTabStopOffset(LineToTabOffsetGetter strategy) {
    Optional<Integer> nativeTabStopOffset = getTabStopOffset();
    if (nativeTabStopOffset.isPresent()) {
      return nativeTabStopOffset.get();
    }

    Optional<Integer> lineStopOffset = getLineStopOffset();
    int lineOffset = strategy.get(getLineNumber());
    if (lineStopOffset.isPresent()) {
      return lineOffset + lineStopOffset.get();
    } else {
      return lineOffset;
    }
  }

}
