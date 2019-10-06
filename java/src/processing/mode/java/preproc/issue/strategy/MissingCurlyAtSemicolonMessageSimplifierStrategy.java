package processing.mode.java.preproc.issue.strategy;

import processing.mode.java.preproc.issue.IssueMessageSimplification;

import java.util.Optional;


/**
 * Strategy to catch a missing curly at a semicolon.
 */
public class MissingCurlyAtSemicolonMessageSimplifierStrategy
    implements PreprocIssueMessageSimplifierStrategy {

  @Override
  public Optional<IssueMessageSimplification> simplify(String message) {
    if (!message.equals("missing ';' at '{'")) {
      return Optional.empty();
    }

    return Optional.of(new IssueMessageSimplification(
        MessageSimplifierUtil.getLocalStr("editor.status.missing.right_curly_bracket")
    ));
  }

}
