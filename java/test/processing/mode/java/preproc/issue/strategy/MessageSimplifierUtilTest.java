package processing.mode.java.preproc.issue.strategy;

import org.junit.Assert;
import org.junit.Test;

public class MessageSimplifierUtilTest {

  @Test
  public void getOffendingAreaMatch() {
    String input = "no viable alternative at input 'ellipse(\n\nellipse();'";
    String output = MessageSimplifierUtil.getOffendingArea(input);
    Assert.assertEquals("ellipse();", output);
  }

  @Test
  public void getOffendingAreaNoMatch() {
    String input = "ambig at input 'ellipse(\n\nellipse();'";
    String output = MessageSimplifierUtil.getOffendingArea(input);
    Assert.assertEquals("ambig at input 'ellipse(\n\nellipse();'", output);
  }

}