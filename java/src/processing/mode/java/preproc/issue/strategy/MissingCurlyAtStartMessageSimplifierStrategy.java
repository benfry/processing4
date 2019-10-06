package processing.mode.java.preproc.issue.strategy;

import processing.mode.java.preproc.issue.IssueMessageSimplification;

import java.util.Optional;

public class MissingCurlyAtStartMessageSimplifierStrategy
    implements PreprocIssueMessageSimplifierStrategy {

  @Override
  public Optional<IssueMessageSimplification> simplify(String message) {
    boolean matches = message.endsWith("expecting {'throws', '{'}");
    matches = matches || message.endsWith("expecting {'throws', '{', '[', ';'}");

    if (!matches) {
      return Optional.empty();
    }

    return Optional.of(new IssueMessageSimplification(
        MessageSimplifierUtil.getLocalStr("editor.status.missing.left_curly_bracket")
    ));
  }

}
