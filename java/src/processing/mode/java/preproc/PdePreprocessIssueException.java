package processing.mode.java.preproc;


import processing.mode.java.preproc.PdePreprocessIssue;

/**
 * Exception indicating that a preprocessor issue was found.
 */
public class PdePreprocessIssueException extends RuntimeException {

  private final PdePreprocessIssue preprocessIssue;

  /**
   * Create a new exception indicating that there was a preprocessing issue.
   *
   * @param newPreprocessIssue Issue encountered.
   */
  public PdePreprocessIssueException(PdePreprocessIssue newPreprocessIssue) {
    super(newPreprocessIssue.getMsg());
    preprocessIssue = newPreprocessIssue;
  }

  /**
   * Get information about the preprocessing issue found.
   *
   * @return Record of the preprocessor issue.
   */
  public PdePreprocessIssue getIssue() {
    return preprocessIssue;
  }

}
