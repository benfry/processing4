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
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;

import processing.mode.java.preproc.SourceEmitter;

import java.util.BitSet;
import java.util.Optional;


/**
 * ANTLR error listener to inform a preprocess issue listener when syntax errors are encountered.
 *
 * <p>
 *   A {BaseErrorListener} which looks for syntax errors reported by ANTLR and converts them to
 *   {PdePreprocessIssue}s that are consumable by a {PdePreprocessIssueListener}. It does this by
 *   running the {PreprocessIssueMessageSimplifierFacade} to generate a more user-friendly error message
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

    PreprocessIssueMessageSimplifierFacade facade = PreprocessIssueMessageSimplifierFacade.get();
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

}
