/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013-20 The Processing Foundation
  Copyright (c) 2011-12 Ben Fry and Casey Reas

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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import processing.app.Base;
import processing.app.Library;
import processing.app.Messages;
import processing.app.Util;
import processing.app.ui.Editor;
import processing.core.PApplet;
import processing.data.StringDict;


public enum ContributionType {
  LIBRARY, MODE, TOOL, EXAMPLES;


  public String toString() {
    return switch (this) {
      case LIBRARY -> "library";
      case MODE -> "mode";
      case TOOL -> "tool";
      case EXAMPLES -> "examples";
    };
  }


  /**
   * Get this type name as a purtied up, capitalized version.
   * @return Mode for mode, Tool for tool, etc.
   */
  public String getTitle() {
    String lower = toString();
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }


  /** Get the name of the properties file for this type of contribution. */
  public String getPropertiesName() {
    return this + ".properties";
  }


  public StringDict loadProperties(File contribFolder) {
    File propertiesFile = new File(contribFolder, getPropertiesName());
    if (propertiesFile.exists()) {
      return Util.readSettings(propertiesFile, false);
    } else {
      System.err.println("Not found: " + propertiesFile);
    }
    return null;
  }


  public File createTempFolder() throws IOException {
    return Util.createTempFolder(toString(), "tmp", getSketchbookFolder());
  }


  public boolean isTempFolderName(String name) {
    return name.startsWith(toString()) && name.endsWith("tmp");
  }


  static public ContributionType fromName(String s) {
    if (s != null) {
      if ("library".equalsIgnoreCase(s)) {
        return LIBRARY;
      }
      if ("mode".equalsIgnoreCase(s)) {
        return MODE;
      }
      if ("tool".equalsIgnoreCase(s)) {
        return TOOL;
      }
      if ("examples".equalsIgnoreCase(s)) {
        return EXAMPLES;
      }
    }
    return null;
  }


  /**
   * Identify a ContributionType from a .properties file name or path.
   * If passed 'blah/potato/library.properties' returns LIBRARY, etc.
   */
  static public ContributionType fromFilePath(String path) {
    String filename = path.substring(path.lastIndexOf('/') + 1);
    for (ContributionType type : ContributionType.values()) {
      if (filename.equals(type.getPropertiesName())) {
        return type;
      }
    }
    return null;
  }


  public File getSketchbookFolder() {
    return switch (this) {
      case LIBRARY -> Base.getSketchbookLibrariesFolder();
      case TOOL -> Base.getSketchbookToolsFolder();
      case MODE -> Base.getSketchbookModesFolder();
      case EXAMPLES -> Base.getSketchbookExamplesFolder();
    };
  }


  public boolean isCandidate(File potential) {
    return (potential.isDirectory() &&
            new File(potential, toString()).exists() &&
            !isTempFolderName(potential.getName()) &&
            isCompatible(potential));
  }


  /**
   * Whether this contrib is compatible with this revision of Processing.
   * Unfortunately, this requires the author to properly set this value.
   * For instance, with “Python Mode for Processing 3” the max revision
   * is set to 0 in mode.properties (meaning all newer Processing versions),
   * even though it properly maxes out with 3.x in contribs.txt.
   */
  private boolean isCompatible(File contribFolder) {
    StringDict properties = loadProperties(contribFolder);
    if (properties != null) {
      final int revisionNum = Base.getRevision();

      int minRevision = 0;
      String minRev = properties.get("minRevision");
      if (minRev != null) {
        minRevision = PApplet.parseInt(minRev, 0);
      }

      int maxRevision = 0;
      String maxRev = properties.get("maxRevision");
      if (maxRev != null) {
        maxRevision = PApplet.parseInt(maxRev, 0);
      }

      return ((maxRevision == 0 || revisionNum <= maxRevision) && revisionNum >= minRevision);
    }
    // Maybe it's ok, maybe it's not. Don't know him; can't vouch for him.
    return true;
  }


  /**
   * Return a list of directories that have the necessary subfolder for this
   * contribution type. For instance, a list of folders that have a 'mode'
   * subfolder if this is a ModeContribution.
   */
  public File[] listCandidates(File folder) {
    return folder.listFiles(this::isCandidate);
  }


  /**
   * Return the first directory that has the necessary subfolder for this
   * contribution type. For instance, the first folder that has a 'mode'
   * subfolder if this is a ModeContribution.
   */
  File findCandidate(File folder) {
    File[] folders = listCandidates(folder);

    if (folders.length == 0) {
      return null;

    } else if (folders.length > 1) {
      Messages.log("More than one " + this + " found inside " + folder.getAbsolutePath());
    }
    return folders[0];
  }


  /**
   * Returns true if the type of contribution requires the PDE to restart
   * when being added or removed.
   */
  boolean requiresRestart() {
    return this == ContributionType.TOOL || this == ContributionType.MODE;
  }


  LocalContribution load(Base base, File folder) {
    return switch (this) {
      case LIBRARY -> Library.load(folder);
      case TOOL -> ToolContribution.load(folder);
      case MODE -> ModeContribution.load(base, folder);
      case EXAMPLES -> ExamplesContribution.load(folder);
    };
  }


  List<LocalContribution> listContributions(Base base, Editor editor) {
    List<LocalContribution> contribs = new ArrayList<>();
    switch (this) {
      case LIBRARY -> {
        if (editor != null) {
          contribs.addAll(editor.getMode().contribLibraries);
        }
      }
      case TOOL -> contribs.addAll(base.getContribTools());
      case MODE -> contribs.addAll(base.getContribModes());
      case EXAMPLES -> contribs.addAll(base.getContribExamples());
    }
    return contribs;
  }


  File getBackupFolder() {
    return new File(getSketchbookFolder(), "old");
  }


  File createBackupFolder(StatusPanel status) {
    File backupFolder = getBackupFolder();
    if (!backupFolder.exists() && !backupFolder.mkdirs()) {
      status.setErrorMessage("Could not create a backup folder in the " +
      		                   "sketchbook " + this + " folder.");
      return null;
    }
    return backupFolder;
  }
}