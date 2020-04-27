package processing.mode.java;

import org.junit.Assert;
import org.junit.Test;


public class SourceUtilTest {

  @Test
  public void getCountPresent() {
    String input = "test1,test2\n,test3";
    int count = SourceUtil.getCount(input, ",");
    Assert.assertEquals(2, count);
  }

  @Test
  public void getCountNotPresent() {
    String input = "test1 test2 test3";
    int count = SourceUtil.getCount(input, ",");
    Assert.assertEquals(0, count);
  }

}