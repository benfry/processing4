package processing.mode.java;

/**
 * Problem identifying a syntax error found in preprocessing.
 */
public class SyntaxProblem extends JavaProblem  {

  private final int tabIndex;
  private final int lineNumber;
  private final String message;
  private final int startOffset;
  private final int stopOffset;
  private final boolean lineFlag;

  /**
   * Create a new syntax problem.
   *
   * @param newTabIndex The tab number containing the source with the syntax issue.
   * @param newLineNumber The line number within the tab at which the offending code can be found.
   * @param newMessage Human readable message describing the issue.
   * @param newStartOffset The character index at which the issue starts. This is relative to start
   *    of tab / file not relative to start of line if newIsLineOffset is true else it is line
   *    offset.
   * @param newStopOffset The character index at which the issue ends. This is relative to start
   *    of tab / file not relative to start of line if newIsLineOffset is true else it is line
   *    offset.
   */
  public SyntaxProblem(int newTabIndex, int newLineNumber, String newMessage, int newStartOffset,
                       int newStopOffset, boolean newIsLineOffset) {

    super(newMessage, JavaProblem.ERROR, newLineNumber, newLineNumber);

    tabIndex = newTabIndex;
    lineNumber = newLineNumber;
    message = newMessage;
    startOffset = newStartOffset;
    stopOffset = newStopOffset;
    lineFlag = newIsLineOffset;
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

  @Override
  public int getStartOffset() {
    return startOffset;
  }

  @Override
  public int getStopOffset() {
    return stopOffset;
  }

  public boolean isLineOffset() {
    return lineFlag;
  }

}
