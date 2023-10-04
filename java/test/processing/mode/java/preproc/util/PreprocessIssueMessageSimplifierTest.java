package processing.mode.java.preproc.util;

import org.junit.Assert;
import org.junit.Test;
import processing.mode.java.preproc.PreprocessIssueMessageSimplifier;


public class PreprocessIssueMessageSimplifierTest {

  @Test
  public void testAssignment() {
    String input = "List<ColoredCircle> =";
    String output = PreprocessIssueMessageSimplifier.get().simplify(input, 123).getMessage();
    Assert.assertTrue(output.contains("assignment")&&!(output.contains("$statement$") || output.contains("$character$") || output.contains("$linenumber$")));
  }

  @Test
  public void testBadIdentifier() {
    String input = "List<ColoredCircle> 9";
    String output = PreprocessIssueMessageSimplifier.get().simplify(input, 123).getMessage();
    System.out.println("output:"+output);
    Assert.assertTrue(output.contains("digit")&&!(output.contains("$statement$") || output.contains("$character$") || output.contains("$linenumber$")));
  }

  @Test
  public void testBadParamLead() {
    String input = "x,";
    String output = PreprocessIssueMessageSimplifier.get().simplify(input, 123).getMessage();
    Assert.assertTrue(output.contains("parameter")&&!(output.contains("$statement$") || output.contains("$character$") || output.contains("$linenumber$")));
  }

  @Test
  public void testBadParamEnd() {
    String input = "colorGen),";
    String output = PreprocessIssueMessageSimplifier.get().simplify(input, 123).getMessage();
    Assert.assertTrue(output.contains("parameter"));
  }

  @Test
  public void testCaret() {
    String input = "List<ColoredCircle circles";
    String output = PreprocessIssueMessageSimplifier.get().simplify(input, 123).getMessage();
    System.out.println("testCaret output:" + output);
    Assert.assertTrue(output.contains(">")&&!(output.contains("$statement$") || output.contains("$character$") || output.contains("$linenumber$")));
  }

  @Test
  public void testMissingIdentifier() {
    String input = "missing Identifier at '{'";
    String output = PreprocessIssueMessageSimplifier.get().simplify(input, 123).getMessage();
    System.out.println("testMissingIdentifieroutput:" + output);
    Assert.assertTrue(output.contains("{")&&!(output.contains("$statement$") || output.contains("$character$") || output.contains("$linenumber$")));
  }

  @Test
  public void simplifyParen() {
    String input = "no viable alternative at input 'ellipse(\n\nellipse();'";
    String output = PreprocessIssueMessageSimplifier.get().simplify(input, 123).getMessage();
    Assert.assertNotNull(output);
  }

  @Test
  public void simplifySemicolon() {
    String input = "no viable alternative at input 'ellipse(\n\nellipse())'";
    String output = PreprocessIssueMessageSimplifier.get().simplify(input, 123).getMessage();
    Assert.assertNotNull(output);
  }

}