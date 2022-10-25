/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  PdeTextAreaDefaults - grabs font/color settings for the editor
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-21 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-03 Massachusetts Institute of Technology

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details. 

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation, Inc.
  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/

package processing.app.syntax;

import processing.app.*;
import processing.app.ui.Theme;


/**
 * Defaults that are PDE (but not Mode) specific. PDE specific in this
 * case means that it's using other PDE classes like Preferences.
 */
public class PdeTextAreaDefaults extends TextAreaDefaults {

  public PdeTextAreaDefaults() {
    document = new SyntaxDocument();

    // Set to 0 for revision 0215 because it causes strange jumps
    // https://github.com/processing/processing/issues/1093
    electricScroll = 0;

    caretVisible = true;
    caretBlinks = Preferences.getBoolean("editor.caret.blink");
    blockCaret = Preferences.getBoolean("editor.caret.block");
    cols = 80;
    // Set the number of rows lower to avoid layout badness with large fonts
    // https://github.com/processing/processing/issues/1313
    rows = 5;

    styles = new SyntaxStyle[Token.ID_COUNT];
    updateTheme();
  }


  /**
   * Deprecated since 4.0 beta 5, because the Mode is no longer used;
   * simply use the default constructor instead.
   */
  @Deprecated
  public PdeTextAreaDefaults(Mode ignoredMode) {
    this();
  }


  protected void updateTheme() {
    fgcolor = Theme.getColor("editor.fgcolor");
    bgcolor = Theme.getColor("editor.bgcolor");

    styles[Token.COMMENT1] = Theme.getStyle("comment1");
    styles[Token.COMMENT2] = Theme.getStyle("comment2");

    styles[Token.KEYWORD1] = Theme.getStyle("keyword1");
    styles[Token.KEYWORD2] = Theme.getStyle("keyword2");
    styles[Token.KEYWORD3] = Theme.getStyle("keyword3");
    styles[Token.KEYWORD4] = Theme.getStyle("keyword4");
    styles[Token.KEYWORD5] = Theme.getStyle("keyword5");
    styles[Token.KEYWORD6] = Theme.getStyle("keyword6");

    styles[Token.FUNCTION1] = Theme.getStyle("function1");
    styles[Token.FUNCTION2] = Theme.getStyle("function2");
    styles[Token.FUNCTION3] = Theme.getStyle("function3");
    styles[Token.FUNCTION4] = Theme.getStyle("function4");

    styles[Token.LITERAL1] = Theme.getStyle("literal1");
    styles[Token.LITERAL2] = Theme.getStyle("literal2");

    styles[Token.LABEL] = Theme.getStyle("label");
    styles[Token.OPERATOR] = Theme.getStyle("operator");

    // area that's not in use by the text (replaced with tildes)
    styles[Token.INVALID] = Theme.getStyle("invalid");

    caretColor = Theme.getColor("editor.caret.color");
    selectionColor = Theme.getColor("editor.selection.color");
    lineHighlight = Theme.getBoolean("editor.line.highlight");
    lineHighlightColor = Theme.getColor("editor.line.highlight.color");
    bracketHighlight = Theme.getBoolean("editor.bracket.highlight");
    bracketHighlightColor = Theme.getColor("editor.bracket.highlight.color");
    eolMarkers = Theme.getBoolean("editor.eol_markers");
    eolMarkerColor = Theme.getColor("editor.eol_markers.color");
  }
}
