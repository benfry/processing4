package processing.core;

import org.junit.Assert;
import org.junit.Test;
import processing.data.XML;
import processing.core.PImage;

import java.awt.*;


public class PShapeSVGTest {

  private static final String[] TEST_CONTENT = {
    "<svg><g><path d=\"L 0,3.1.4.1\"/></g></svg>",
    "<svg><g><path d=\"m-13.6 4.69-1.35 0.78h-0.00034l1.33 2.27 1.33-0.77a6.48 6.47 43.14 0 1-1.31-2.27z\"/></g></svg>"
  };
  private static final int[] TEST_NVERTEX = {2, 8};
  private static final String TEST_EXPONENT =
    "<svg><g><path d=\"m-1.36E1 469e-2-1.35 0.78h-3.4e-4l1.33 2.27 1.33-0.77a6.48 6.47 43.14 0 1-1.31-2.27z\"/></g></svg>";

  @Test
  public void testDecimals() {
    try {
      for (int i = 0; i < TEST_CONTENT.length; ++i) {
        XML xml = XML.parse(TEST_CONTENT[i]);
        PShapeSVG shape = new PShapeSVG(xml);
        PShape[] children = shape.getChildren();
        Assert.assertEquals(1, children.length);
        PShape[] grandchildren = children[0].getChildren();
        Assert.assertEquals(1, grandchildren.length);
        Assert.assertEquals(0, grandchildren[0].getChildCount());
        Assert.assertEquals(TEST_NVERTEX[i], grandchildren[0].getVertexCount());
      }
    }
    catch (Exception e) {
      Assert.fail("Encountered exception " + e);
    }
  }

  @Test
  public void testExponent() {
    try {
      XML xml = XML.parse(TEST_EXPONENT);
      PShapeSVG shape = new PShapeSVG(xml);
      PShape[] children = shape.getChildren();
      Assert.assertEquals(1, children.length);
      PShape[] grandchildren = children[0].getChildren();
      Assert.assertEquals(1, grandchildren.length);
      Assert.assertEquals(0, grandchildren[0].getChildCount());
      Assert.assertEquals(8, grandchildren[0].getVertexCount());
    }
    catch (Exception e) {
      Assert.fail("Encountered exception " + e);
    }
  }
}
