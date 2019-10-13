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

package processing.mode.java.preproc.issue;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

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
}
