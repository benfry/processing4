package processing.mode.java.preproc.issue.strategy;


/**
 * Missing type in a generic.
 */
public class MissingGenericTypeMessageSimplifierStrategy
    extends RegexTemplateMessageSimplifierStrategy {

  @Override
  public String getRegexPattern() {
    return "<>'?$";
  }

  @Override
  public String getHintTemplate() {
    return MessageSimplifierUtil.getLocalStr("editor.status.bad.generic");
  }

}
