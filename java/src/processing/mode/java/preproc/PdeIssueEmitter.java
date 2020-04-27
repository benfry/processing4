/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2019 The Processing Foundation

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.mode.java.preproc;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import processing.mode.java.SourceUtil;

import java.util.Optional;


/**
 * ANTLR error listener to inform a preprocess issue listener when syntax errors are encountered.
 *
 * <p>
 *   A {BaseErrorListener} which looks for syntax errors reported by ANTLR and converts them to
 *   {PdePreprocessIssue}s that are consumable by a {PdePreprocessIssueListener}. It does this by
 *   running the {PreprocessIssueMessageSimplifier} to generate a more user-friendly error message
 *   before informing the provided listener.
 * </p>
 */
public class PdeIssueEmitter extends BaseErrorListener {

  private final PdePreprocessIssueListener listener;
  private final Optional<SourceEmitter> sourceMaybe;

  /**
   * Create a new issue emitter.
   *
   * <p>
   *    Create a new issue emitter when access to the processing sketch source is not available.
   *    Note that this will not allow some error beautification and, if sketch source is available,
   *    use other constructor.
   * </p>
   *
   * @param newListener The listener to inform when encountering a syntax error.
   */
  public PdeIssueEmitter(PdePreprocessIssueListener newListener) {
    listener = newListener;
    sourceMaybe = Optional.empty();
  }

  /**
   * Create a new issue emitter.
   *
   * @param newListener The listener to inform when encountering a syntax error.
   * @param newSourceEmitter The sketch source to use when helping beautify certain syntax error
   *    messages.
   */
  public PdeIssueEmitter(PdePreprocessIssueListener newListener, SourceEmitter newSourceEmitter) {
    listener = newListener;
    sourceMaybe = Optional.of(newSourceEmitter);
  }

  @Override
  public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                          int charPositionInLine, String msg, RecognitionException e) {

    PreprocessIssueMessageSimplifier facade = PreprocessIssueMessageSimplifier.get();
    IssueMessageSimplification simplification = facade.simplify(msg);

    IssueLocation issueLocation;

    if (sourceMaybe.isPresent()) {
      issueLocation = IssueLocationFactory.getLineWithOffset(
          simplification,
          line,
          charPositionInLine,
          sourceMaybe.get().getSource()
      );
    } else {
      issueLocation = new IssueLocation(line, charPositionInLine);
    }

    listener.onIssue(new PdePreprocessIssue(
        issueLocation.getLine(),
        issueLocation.getCharPosition(),
        simplification.getMessage()
    ));
  }

  /**
   * Simple interface for strategy which can emit the full body of a processing sketch.
   */
  public static interface SourceEmitter {

    /**
     * Get the full body of the processing sketch.
     *
     * @return String processing sketch source code across all tabs.
     */
    String getSource();

  }

  /**
   * Interface for listener that responds to issues reported by the preprocessor.
   */
  public static interface PdePreprocessIssueListener {

    /**
     * Callback to invoke when an issue is encountered in preprocesing.
     *
     * @param issue Description of the issue.
     */
    void onIssue(PdePreprocessIssue issue);

  }

  /**
   * ================================
   * == Supporting data structures ==
   * ================================
   */

  /**
   * Data structure describing an issue simplification or explanation.
   *
   * <p>
   *   Data structure describing an edit that was made to an error message or warning to be shown to
   *   the user based on a series of rules that attempts to make error messages easier to understand
   *   for the user.
   * </p>
   */
  public static class IssueMessageSimplification {

    private final String message;
    private final boolean attributeToPriorToken;

    /**
     * Create a new issue message simplification.
     *
     * <p>
     *   Create a new issue message simplification that leaves the token attribution alone (the token
     *   on which the error was reported will be the same before error message simplification).
     * </p>
     *
     * @param newMessage The message to show to the user.
     */
    public IssueMessageSimplification(String newMessage) {
      message = newMessage;
      attributeToPriorToken = false;
    }

    /**
     * Create a new issue message simplification.
     *
     * <p>
     *   Create a new issue message simplification. Note that there is an option to have the error
     *   attributed to the "prior token". This is helpful, for example, when a semicolon is missing.
     *   The error is generated on the token after the line on which the semicolon was omitted so,
     *   while the error technically emerges on the next line, it is better for the user for it to
     *   appear earlier. Specifically, it is most sensible for it to appear on the "prior token".
     * </p>
     *
     * @param newMessage The message to show to the user.
     * @param newAttributeToPriorToken Boolean flag indicating if the error should be shown on the
     *    token prior to the one on which the error was originally generated. True if the error should
     *    be attributed to the prior token. False otherwise.
     */
    public IssueMessageSimplification(String newMessage, boolean newAttributeToPriorToken) {
      message = newMessage;
      attributeToPriorToken = newAttributeToPriorToken;
    }

    /**
     * Get the error message text that should be shown to the user.
     *
     * @return The error message text that should be shown to the user.
     */
    public String getMessage() {
      return message;
    }

    /**
     * Flag indicating if the error should be attributed to the prior token.
     *
     * @return True if the error should be attributed to the prior non-skip token (not whitepsace or
     *    comment). This is useful when a mistake on a prior line like omitted semicolon causes an
     *    error on a later line but one wants error highlighting closer to the mistake itself. False
     *    if the error should be attributed to the original offending token.
     */
    public boolean getAttributeToPriorToken() {
      return attributeToPriorToken;
    }

  }

  /**
   * Data structure describing where an issue occurred.
   */
  public static class IssueLocation {

    private final int line;
    private final int charPosition;

    /**
     * Create a new issue location structure.
     *
     * @param newLine The line (1-indexed) where the issue occurred. This should be in the global file
     *    generated by the preprocessor and not relative to the start of the tab.
     * @param newCharPosition The position on the line.
     */
    public IssueLocation(int newLine, int newCharPosition) {
      line = newLine;
      charPosition = newCharPosition;
    }

    /**
     * Get the 1-indexed line on which this error occurred.
     *
     * @return The line on which this error occurred. Note that this will be relative to the global
     *    file generated by the preprocessor and not relative to the start of the tab.
     */
    public int getLine() {
      return line;
    }

    /**
     * The the position of the error within the line.
     *
     * @return The number of characters including whitespace from the start of the line at which the
     *    error occurred.
     */
    public int getCharPosition() {
      return charPosition;
    }

  }

  /**
   * =====================
   * == Utility classes ==
   * =====================
   */

  /**
   * Utility that can help clean up where in source an issue should be reported.
   *
   * <p>
   *    For some errors, the location of the "mistake" does not appear close to where the actual error
   *    is generated. For example, consider omitting a semicolon. Though the "mistake" is arguably on
   *    the line on which a semicolon is forgotten, the grammatical error appears in the first
   *    non-skip token after the omitted character. This means that the issue shown to the user may
   *    be far away from the line they would want to edit. This utility helps determine if an issue
   *    requires a new location and, if so, where the location should be.
   * </p>
   */
  public static class IssueLocationFactory {

    /**
     * Determine where an issue should be reported.
     *
     * @param simplification The issue simplification generated from {PreprocessIssueMessageSimplifier}.
     * @param originalLine The original line (1 indexed) on which the issue was reported.
     * @param originalOffset The original number of characters from the start of the line where the
     *    the issue was reported.
     * @param source The full concatenated source of the sketch being built.
     * @param lineCount The total
     * @return The new location where the issue should be reported. This may be identical to the
     *    original location if the issue was not moved.
     */
    public static IssueLocation getLineWithOffset(IssueMessageSimplification simplification,
          int originalLine, int originalOffset, String source) {

      // Determine if the issue should be relocated
      boolean shouldAttributeToPrior = simplification.getAttributeToPriorToken();
      shouldAttributeToPrior = shouldAttributeToPrior && originalLine != 0;

      if (!shouldAttributeToPrior) {
        return new IssueLocation(originalLine, originalOffset);
      }

      // Find the code prior the issue
      String priorCode = getContentsUpToLine(source, originalLine);

      // Find the token immediately prior to the issue
      PreprocessIssueMessageSimplifier.PriorTokenFinder finder = new PreprocessIssueMessageSimplifier.PriorTokenFinder();
      int charPos = priorCode.length();
      while (!finder.isDone() && charPos > 0) {
        charPos--;
        finder.step(priorCode.charAt(charPos));
      }

      // Find the location offset depending on if the prior token could be found
      Optional<Integer> foundStartOfMatchMaybe = finder.getTokenPositionMaybe();
      int startOfMatch;
      int linesOffset;

      if (foundStartOfMatchMaybe.isPresent()) {
        startOfMatch = priorCode.length() - foundStartOfMatchMaybe.get();
        String contentsOfMatch = priorCode.substring(startOfMatch);
        linesOffset = SourceUtil.getCount(contentsOfMatch, "\n");
      } else {
        startOfMatch = priorCode.length();
        linesOffset = 0;
      }

      // Apply the location offset and highlight to the end of the line
      String contentsPriorToMatch = priorCode.substring(0, startOfMatch);
      int newLine = originalLine - linesOffset;
      int lengthIncludingLine = contentsPriorToMatch.length();
      int lengthExcludingLine = contentsPriorToMatch.lastIndexOf('\n');
      int lineLength = lengthIncludingLine - lengthExcludingLine;
      int col = lineLength - 1; // highlight from start of line to end

      // Build the new issue location
      return new IssueLocation(newLine, col);
    }

    /**
     * Get all of the contents of source leading up to a line.
     *
     * @param source The full concatenated sketch source.
     * @param endLineExclusive The line up to which code should be returned. Note that this is an
     *    "exclusive" boundary. Code from this line itself will not be included.
     * @return All of the sketch code leading up to but not including the line given.
     */
    private static String getContentsUpToLine(String source, int endLineExclusive) {
      int line = 0;
      int stringCursor = 0;
      int strLength = source.length();

      while (line < endLineExclusive-1 && stringCursor < strLength) {
        if (source.charAt(stringCursor) == '\n') {
          line++;
        }

        stringCursor++;
      }

      return source.substring(0, stringCursor);
    }

  }

}
