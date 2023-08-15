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


/**
 * Problem identifying a syntax error found in preprocessing.
 */
public class SyntaxProblem extends JavaProblem  {

  private final int tabIndex;
  private final int lineNumber;
  private final String message;
  private final int lineStartOffset;
  private final int lineStopOffset;

  /**
   * Create a new syntax problem.
   *
   * @param newTabIndex The tab number containing the source with the syntax issue.
   * @param newLineNumber The line number within the tab at which the offending code can be found.
   * @param newMessage Human readable message describing the issue.
   * @param newStartOffset The character index at which the issue starts relative to line.
   * @param newStopOffset The character index at which the issue end relative to line.
   */
  public SyntaxProblem(int newTabIndex, int newLineNumber, String newMessage, int newStartOffset,
                       int newStopOffset) {

    super(newMessage, JavaProblem.ERROR, newLineNumber, newLineNumber);

    tabIndex = newTabIndex;
    lineNumber = newLineNumber;
    message = newMessage;
    lineStartOffset = newStartOffset;
    lineStopOffset = newStopOffset;
  }

  @Override
  public boolean isError() {
    return true;
  }

  @Override
  public boolean isWarning() {
    return false;
  }

  @Override
  public int getTabIndex() {
    return tabIndex;
  }

  @Override
  public int getLineNumber() {
    return lineNumber;
  }

  @Override
  public String getMessage() {
    return message;
  }

  public int getStartOffset() {
    return lineStartOffset;
  }

  public int getStopOffset() {
    return lineStopOffset;
  }

}
