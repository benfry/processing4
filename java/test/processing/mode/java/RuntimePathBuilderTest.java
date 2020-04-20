/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org
Copyright (c) 2019 The Processing Foundation

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License version 2
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation, Inc.
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/

package processing.mode.java;

import org.junit.Before;
import org.junit.Test;
import processing.app.Sketch;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

import static org.junit.Assert.*;


public class RuntimePathBuilderTest {

  private RuntimePathBuilder builder;
  private JavaMode testMode;
  private List<ImportStatement> testImports;
  private Sketch testSketch;
  private PreprocSketch.Builder result;

  @Before
  public void setUp() throws Exception {
    builder = new RuntimePathBuilder();
    testMode = RuntimePathFactoryTestUtil.createTestJavaMode();
    testImports = RuntimePathFactoryTestUtil.createTestImports();
    testSketch = RuntimePathFactoryTestUtil.createTestSketch();

    result = new PreprocSketch.Builder();
    result.programImports.addAll(testImports);
    result.sketch = testSketch;

    builder.prepareClassPath(result, testMode);
  }

  @Test
  public void testClassPathLoader() {
    assertNotNull(result.classLoader);
  }

  @Test
  public void testClassPathObj() {
    assertNotNull(result.classPath);
  }

  @Test
  public void testSketchClassPathStrategiesJava() {
    checkPresent(result.classPathArray, "java.base.jmod");
  }

  @Test
  public void testSketchClassPathStrategiesLibrary() {
    checkPresent(result.classPathArray, "library3");
  }

  @Test
  public void testSketchClassPathStrategiesCore() {
    checkPresent(result.classPathArray, "library3");
  }

  @Test
  public void testSketchClassPathStrategiesMode() {
    checkPresent(result.classPathArray, "library6");
  }

  @Test
  public void testSketchClassPathStrategiesCodeFolder() {
    checkPresent(result.classPathArray, "file1.jar");
  }

  @Test
  public void testSearchClassPathStrategiesCodeJava() {
    checkPresent(result.searchClassPathArray, "java.base.jmod");
  }

  @Test
  public void testSearchClassPathStrategiesCodeMode() {
    checkPresent(result.classPathArray, "library6");
  }

  @Test
  public void testSearchClassPathStrategiesCodeLibrary() {
    checkPresent(result.classPathArray, "library3");
  }

  @Test
  public void testSearchClassPathStrategiesCodeCore() {
    checkPresent(result.classPathArray, "library1");
  }

  @Test
  public void testSearchClassPathStrategiesCodeCodeFolder() {
    checkPresent(result.classPathArray, "file3.zip");
  }

  private void checkPresent(String[] classPathArray, String target) {
    long count = Arrays.stream(classPathArray)
        .filter((x) -> x.contains(target))
        .count();

    assertTrue(count > 0);
  }

  @Test
  public void sanitizeClassPath() {
    StringJoiner testStrJoiner = new StringJoiner(File.pathSeparator);
    testStrJoiner.add("test1");
    testStrJoiner.add("");
    testStrJoiner.add("test2");

    List<String> classPath = builder.sanitizeClassPath(testStrJoiner.toString());
    assertEquals(2, classPath.size());
    assertEquals("test1", classPath.get(0));
    assertEquals("test2", classPath.get(1));
  }

  @Test
  public void sanitizeClassPathNoDuplicate() {
    StringJoiner testStrJoiner = new StringJoiner(File.pathSeparator);
    testStrJoiner.add("test1");
    testStrJoiner.add("");
    testStrJoiner.add("test2");
    testStrJoiner.add("test2");

    List<String> classPath = builder.sanitizeClassPath(testStrJoiner.toString());
    assertEquals(2, classPath.size());
    assertEquals("test1", classPath.get(0));
    assertEquals("test2", classPath.get(1));
  }

}
