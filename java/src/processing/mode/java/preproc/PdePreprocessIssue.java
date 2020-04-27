package processing.mode.java.preproc;

/**
 * Issue emitted from the preprocessor.
 */
public class PdePreprocessIssue {

  private final int line;
  private final int charPositionInLine;
  private final String msg;

  /**
   * Create a new record of an issue emitted from the preprocessor.
   *
   * @param newLine The line in the generated java file.
   * @param newCharPositionInLine The character position in the source line.
   * @param newMsg Description of the issue.
   */
  public PdePreprocessIssue(int newLine, int newCharPositionInLine, String newMsg) {
    line = newLine;
    charPositionInLine = newCharPositionInLine;
    msg = newMsg;
  }

  /**
   * Get the unified source line where the issue was found.
   *
   * @return The line in the output java source where the issue was found.
   */
  public int getLine() {
    return line;
  }

  /**
   * Get the character position in the source line of the issue.
   *
   * @return The source column where the issue was found.
   */
  public int getCharPositionInLine() {
    return charPositionInLine;
  }

  /**
   * Get a description of the issue found.
   *
   * @return Human-readable localized message describing the issue.
   */
  public String getMsg() {
    return msg;
  }

}
