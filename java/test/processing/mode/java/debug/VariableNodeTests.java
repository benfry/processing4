package processing.mode.java.debug;

import com.sun.jdi.StringReference;
import com.sun.jdi.Value;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;


public class VariableNodeTests {

  @Test
  public void describeNull() {
    VariableNode node = new VariableNode("test", "null", null);
    Assert.assertEquals(node.getStringValue(), "null");
  }

  @Test
  public void describeInt() {
    Value value = buildMockValue("5");
    VariableNode node = new VariableNode("test", "int", value);
    Assert.assertEquals(node.getStringValue(), "5");
  }

  @Test
  public void describeFloat() {
    Value value = buildMockValue("5.5");
    VariableNode node = new VariableNode("test", "float", value);
    Assert.assertEquals(node.getStringValue(), "5.5");
  }

  @Test
  public void describeObject() {
    Value value = buildMockValue("5.5");
    VariableNode node = new VariableNode("test", "Other", value);
    Assert.assertEquals(node.getStringValue(), "instance of Other");
  }

  @Test
  public void describeString() {
    Value value = buildMockString("testing");
    VariableNode node = new VariableNode("test", "java.lang.String", value);
    Assert.assertEquals(node.getStringValue(), "testing");
  }

  @Test
  public void describeSimpleArray() {
    Value value = buildMockValue("instance of int[5] (id=998)");
    VariableNode node = new VariableNode("test", "int[]", value);
    Assert.assertEquals(node.getStringValue(), "instance of int[5] (id=998)");
  }

  @Test
  public void describeNestedArraySingleDimensionUnknown() {
    Value value = buildMockValue("instance of int[][5] (id=998)");
    VariableNode node = new VariableNode("test", "int[][]", value);
    Assert.assertEquals(node.getStringValue(), "instance of int[5][]");
  }

  @Test
  public void describeNestedArrayMultiDimensionUnknown() {
    Value value = buildMockValue("instance of int[][][5] (id=998)");
    VariableNode node = new VariableNode("test", "int[][][]", value);
    Assert.assertEquals(node.getStringValue(), "instance of int[5][][]");
  }

  @Test
  public void describeNestedArrayMixed() {
    Value value = buildMockValue("instance of int[][][5][7] (id=998)");
    VariableNode node = new VariableNode("test", "int[][][][]", value);
    Assert.assertEquals(node.getStringValue(), "instance of int[5][7][][]");
  }

  @Test
  public void describeArrayFailsafe() {
    Value value = buildMockValue("instance of int[x][7] (id=98)");
    VariableNode node = new VariableNode("test", "int[][][][]", value);
    Assert.assertEquals(node.getStringValue(), "instance of int[x][7] (id=98)");
  }

  @Test
  public void describeArrayUnexpectedOrder() {
    Value value = buildMockValue("instance of int[7][] (id=98)");
    VariableNode node = new VariableNode("test", "int[][][][]", value);
    Assert.assertEquals(node.getStringValue(), "instance of int[7][] (id=98)");
  }

  private Value buildMockValue(String toStringValue) {
    Value value = Mockito.mock(Value.class);
    Mockito.when(value.toString()).thenReturn(toStringValue);
    return value;
  }

  private StringReference buildMockString(String innerValue) {
    StringReference value = Mockito.mock(StringReference.class);
    Mockito.when(value.value()).thenReturn(innerValue);
    return value;
  }

}
