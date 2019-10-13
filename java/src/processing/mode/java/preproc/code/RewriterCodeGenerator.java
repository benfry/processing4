package processing.mode.java.preproc.code;

import org.antlr.v4.runtime.TokenStreamRewriter;
import processing.app.Preferences;
import processing.core.PApplet;
import processing.mode.java.preproc.PdePreprocessor;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;


/**
 * Utility to rewrite code as part of preprocessing.
 */
public class RewriterCodeGenerator {

  private final String indent1;
  private final String indent2;
  private final String indent3;

  /**
   * Create a new rewriter.
   *
   * @param indentSize Number of spaces in the indent.
   */
  public RewriterCodeGenerator(int indentSize) {
    final char[] indentChars = new char[indentSize];
    Arrays.fill(indentChars, ' ');
    indent1 = new String(indentChars);
    indent2 = indent1 + indent1;
    indent3 = indent2 + indent1;
  }

}
