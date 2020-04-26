package processing.mode.java.preproc;

import org.junit.Assert;
import org.junit.Test;
import processing.mode.java.preproc.PreprocessIssueMessageSimplifier;

public class MessageSimplifierUtilTest {

  @Test
  public void getOffendingAreaMatch() {
    String input = "no viable alternative at input 'ellipse(\n\nellipse();'";
    String output = PreprocessIssueMessageSimplifier.getOffendingArea(input);
    Assert.assertEquals("ellipse();", output);
  }

  @Test
  public void getOffendingAreaNoMatch() {
    String input = "ambig at input 'ellipse(\n\nellipse();'";
    String output = PreprocessIssueMessageSimplifier.getOffendingArea(input);
    Assert.assertEquals("ambig at input 'ellipse(\n\nellipse();'", output);
  }

}