/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - https://processing.org

  Copyright (c) 2014-23 The Processing Foundation

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along
  with this program; if not, write to the Free Software Foundation, Inc.
  59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package processing.app.contrib;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import processing.app.Base;
import processing.app.Mode;
import processing.data.StringDict;
import processing.data.StringList;

import static processing.app.contrib.ContributionType.EXAMPLES;


public class ExamplesContribution extends LocalContribution {

  private ExamplesContribution(File folder) {
    super(folder);
  }


  static public ExamplesContribution load(File folder) {
    return new ExamplesContribution(folder);
  }


  static public List<ExamplesContribution> loadAll(File examplesFolder) {
    List<ExamplesContribution> outgoing = new ArrayList<>();
    File[] potential = EXAMPLES.listCandidates(examplesFolder);
    // If examplesFolder does not exist or is inaccessible (stranger things
    // have happened, and are reported as bugs) the list will come back null.
    if (potential != null) {
      for (File folder : potential) {
        outgoing.add(load(folder));
      }
    }
    outgoing.sort(Comparator.comparing(Contribution::getName));
    return outgoing;
  }


  static public boolean isModeCompatible(Base base, StringDict props) {
    return isModeCompatible(base.getActiveEditor().getMode(), props);
  }


  /**
   * Determine whether the example is compatible with the current Mode.
   * @return true if compatible with the Mode of the currently active editor
   */
  static public boolean isModeCompatible(Mode mode, StringDict props) {
    String currentIdentifier = mode.getIdentifier();
    StringList compatibleList = parseModeList(props);
    if (compatibleList.size() == 0) {
      if (mode.requireExampleCompatibility()) {
        // for p5js (and maybe Python), examples must specify that they work
        return false;
      }
      // if no Mode specified, assume compatible everywhere
      return true;
    }
    return compatibleList.hasValue(currentIdentifier);
  }


  @Override
  public ContributionType getType() {
    return EXAMPLES;
  }
}
