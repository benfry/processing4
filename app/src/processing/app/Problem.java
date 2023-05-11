/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org
  Copyright (c) 2012-16 The Processing Foundation

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

package processing.app;

import java.util.Optional;


/**
 * Structure describing a problem encountered in sketch compilation.
 */
public interface Problem {

  /**
   * Strategy converting line number in tab to character offset from tab start.
   */
  public interface LineToTabOffsetGetter {

    /**
     * Convert a line number to the number of characters past tab start.
     * 
     * @param line The line number to convert.
     * @return The number of characters past tab start where that line starts.
     */
    public int get(int line);

  }

  /**
   * Get if the problem is an error that prevented compilation.
   * 
   * @return True if an error such that the sketch did not compile and false
   *    otherwise.
   */
  public boolean isError();

  /**
   * Get if the problem is an warning that did not prevent compilation.
   * 
   * @return True if a warning and the sketch compiled and false otherwise.
   */
  public boolean isWarning();

  /**
   * Get which tab (sketch file) the problem was encountered.
   * 
   * @return The index of the tab in which the problem was encountered.
   */
  public int getTabIndex();

  /**
   * Get at which line the problem was encountered.
   * 
   * @return Zero-indexed line number within the tab at getTabIndex in which
   *    this problem was encountered. Note that this is not the line in the
   *    generated Java file.
   */
  public int getLineNumber();

  /**
   * Get a human-reabable description of the problem encountered.
   * 
   * @return String describing the error or warning encountered.
   */
  public String getMessage();

  /**
   * Get the exact character on which this problem starts in code tab relative.
   * 
   * @return Number of characters past the start of the tab if known where the
   *    code associated with the Problem starts. Returns empty if not provided.
   */
  public Optional<Integer> getTabStartOffset();

  /**
   * Get the exact character on which this problem ends in code tab relative.
   * 
   * @return Number of characters past the start of the tab if known where the
   *    code associated with the Problem ends. Returns empty if not provided.
   */
  public Optional<Integer> getTabStopOffset();

  /**
   * Get the exact character on which this problem starts in code line relative.
   * 
   * @return Number of characters past the start of the line if known where the
   *    code associated with the Problem starts. Returns empty if not provided.
   */
  public Optional<Integer> getLineStartOffset();

  /**
   * Get the exact character on which this problem ends in code line relative.
   * 
   * @return Number of characters past the start of the line if known where the
   *    code associated with the Problem ends. Returns empty if not provided.
   */
  public Optional<Integer> getLineStopOffset();

  /**
   * Get the exact character on which this problem ends in code tab relative.
   * 
   * @param strategy Strategy to convert line to tab start if needed.
   * @return Number of characters past the start of the tab if known where the
   *    code associated with the Problem ends, using the provided conversion
   *    if needed. Returns line start if character position not given.
   */
  public int computeTabStartOffset(LineToTabOffsetGetter strategy);

  /**
   * Get the exact character on which this problem ends in code tab relative.
   * 
   * @param strategy Strategy to convert line to tab start if needed.
   * @return Number of characters past the start of the tab if known where the
   *    code associated with the Problem ends, using the provided conversion
   *    if needed. Returns line start if character position not given.
   */
  public int computeTabStopOffset(LineToTabOffsetGetter strategy);
}

