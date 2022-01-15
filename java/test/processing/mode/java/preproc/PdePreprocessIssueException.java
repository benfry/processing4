package processing.mode.java.preproc;


/**
 * Exception indicating that a preprocessor issue was found.
 * This is only used by classes in the test package, and needs to be moved.
 * https://github.com/processing/processing4/issues/130
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
