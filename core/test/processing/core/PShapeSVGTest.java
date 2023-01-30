package processing.core;

import org.junit.Assert;
import org.junit.Test;
import processing.data.XML;
import processing.core.PImage;

import java.awt.*;


public class PShapeSVGTest {

  private static final String TEST_CONTENT = "<svg><g><path d=\"L 0,3.1.4.1\"/></g></svg>";

  @Test
  public void testDecimals() {
    try {
      XML xml = XML.parse(TEST_CONTENT);
      PShapeSVG shape = new PShapeSVG(xml);
      PShape[] children = shape.getChildren();
      Assert.assertEquals(1, children.length);
      PShape[] grandchildren = children[0].getChildren();
      Assert.assertEquals(1, grandchildren.length);
      Assert.assertEquals(0, grandchildren[0].getChildCount());
      Assert.assertEquals(2, grandchildren[0].getVertexCount());
    }
    catch (Exception e) {
      Assert.fail("Encountered exception " + e);
    }
  }

}
