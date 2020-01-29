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

/**
 * Convenience functions useful for working on syntax checking for source.
 */
public class SyntaxUtil {

  /**
   * Determine how many times a string appears in another.
   *
   * @param body The string in which occurrences should be counted.
   * @param search The string to look for.
   * @return The number of times search appears in body.
   */
  public static int getCount(String body, String search) {
    int count = 0;
    if (search.length() == 1) {
      // single character version
      final char sought = search.charAt(0);
      for (int i = 0; i < body.length(); i++) {
        if (body.charAt(i) == sought) {
          count++;
        }
      }
    } else {
      for (int i = 0; i < body.length(); i++) {
        if (body.substring(i).startsWith(search)) {
          count++;
        }
      }
    }
    return count;
  }
}
