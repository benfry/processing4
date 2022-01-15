/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  ThemeSelector - easy selection of alternate color systems
  Part of the Processing project - https://processing.org

  Copyright (c) 2022 The Processing Foundation

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app.tools;

import processing.app.*;
import processing.app.ui.Editor;
import processing.awt.ShimAWT;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.zip.*;


public class ThemeSelector implements Tool {
  static final int COUNT = 16;
  static final int SIZE = 445;

  static final String[] themeOrder = {
    "kyanite", "calcite", "olivine", "beryl",
    "galena", "jasper", "malachite", "pyrite",
    "gabbro", "fluorite", "orpiment", "feldspar",
    "antimony", "serandite", "bauxite", "garnet"
  };

  String[] themeContents;

  Base base;
  Editor editor;


  public String getMenuTitle() {
    return Language.text("Theme Selector");
  }


  public void init(Base base) {
    this.base = base;

    themeContents = new String[COUNT];
    for (int i = 0; i < COUNT; i++) {
      try {
        File file = Base.getLibFile("themes/" + themeOrder[i] + ".txt");

      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }


  public void run() {
    editor = base.getActiveEditor();
  }
}