/*
 * SyntaxStyle.java - A simple text style class
 * Copyright (C) 1999 Slava Pestov
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */

package processing.app.syntax;

import java.awt.Color;
import java.util.StringTokenizer;


/**
 * A simple text style class that specifies the color and bold flag of a run of text.
 * @author Slava Pestov
 * @version $Id$
 */
public class SyntaxStyle {
  final private Color color;
  final private boolean bold;

  
  /**
   * Creates a new SyntaxStyle.
   * @param color The text color
   * @param bold True if the text should be bold
   */
  public SyntaxStyle(Color color, boolean bold) {
    this.color = color;
    this.bold = bold;
  }


  static public SyntaxStyle fromString(String str) {
    StringTokenizer st = new StringTokenizer(str, ",");

    String s = st.nextToken();
    if (s.indexOf("#") == 0) s = s.substring(1);
    Color color = new Color(Integer.parseInt(s, 16));

    s = st.nextToken();
    boolean bold = s.contains("bold");
    //boolean italic = s.contains("italic");

    return new SyntaxStyle(color, bold);
  }

  
  /** Returns the color specified in this style. */
  public Color getColor() {
    return color;
  }

  
  /** Returns true if boldface is enabled for this style. */
  public boolean isBold() {
    return bold;
  }

  
  /** Returns a string representation of this object. */
  public String toString() {
    return getClass().getName() + "[color=" + color + (bold ? ",bold" : "") + "]";
  }
}
