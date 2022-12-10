package processing.mode.java;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static processing.mode.java.ProcessingTestUtil.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Optional;

import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

import processing.app.Preferences;
import processing.app.SketchException;
import processing.mode.java.preproc.PreprocessorResult;
import processing.mode.java.preproc.PdePreprocessIssueException;


public class ParserTests {

  @BeforeClass
  public static void init() {
    ProcessingTestUtil.init();
  }

  @Before
  public void before() {
    Preferences.setBoolean("export.application.fullscreen", false);
  }

  static void expectRecognitionException(final String id,
                                         final int expectedLine) {

    PreprocessorResult result;
    try {
      preprocess(id, res(id + ".pde"));
      fail("Expected to fail with on line " + expectedLine);
    } catch (PdePreprocessIssueException e) {
      assertNotNull(e.getIssue().getMsg());
      assertEquals(expectedLine, e.getIssue().getLine());
    } catch (Exception e) {
      if (!e.equals(e.getCause()) && e.getCause() != null)
        fail(e.getCause().toString());
      else
        fail(e.toString());
    }
  }

  static void expectRunnerException(final String id) {
    try {
      preprocess(id, res(id + ".pde"));
      fail("Expected to fail");
    } catch (SketchException e) {
      assertNotNull(e);
    } catch (PdePreprocessIssueException e) {
      assertNotNull(e.getIssue().getMsg());
    } catch (Exception e) {
      if (!e.equals(e.getCause()) && e.getCause() != null)
        fail(e.getCause().toString());
      else
        fail(e.toString());
    }
  }

  static void expectRunnerException(final String id,
                                    final int expectedLine) {

    try {
      preprocess(id, res(id + ".pde"));
      fail("Expected to fail with on line " + expectedLine);
    } catch (SketchException e) {
      assertEquals(expectedLine, e.getCodeLine());
    } catch (PdePreprocessIssueException e) {
      assertNotNull(e.getIssue().getMsg());
      assertEquals(expectedLine, e.getIssue().getLine());
    } catch (Exception e) {
      if (!e.equals(e.getCause()) && e.getCause() != null)
        fail(e.getCause().toString());
      else
        fail(e.toString());
    }
  }

  static void expectGood(final String id) {
    expectGood(id, true);
  }

  static void expectGood(final String id, boolean ignoreWhitespace) {
    expectGood(id, ignoreWhitespace, Optional.empty());
  }

  static void expectGood(final String id, boolean ignoreWhitespace, Optional<String> packageName) {
    try {
      final String program = preprocess(id, res(id + ".pde"), packageName);

      final File expectedFile = res(id + ".expected");
      if (expectedFile.exists()) {
        final String expected = ProcessingTestUtil.read(expectedFile);
        if (ignoreWhitespace) {
          String expectedStrip = expected.replace("\t", "")
              .replace(" ", "")
              .replace("\n", "")
              .replace("\r", "");

          String actualStrip = program.replace("\t", "")
              .replace(" ", "")
              .replace("\n", "")
              .replace("\r", "");

          if (!expectedStrip.equals(actualStrip)) {
            System.err.println("Expected >>>>>>>");
            System.err.println(expected);
            System.err.println("<<<<<<< Got >>>>>>>");
            System.err.println(program);
            System.err.println("<<<<<<<");
            assertEquals(expectedStrip, actualStrip);
          }
        } else {
          assertEquals(expected, program);
        }
      } else {
        System.err.println("WARN: " + id
            + " does not have an expected output file. Generating.");
        final FileWriter sug = new FileWriter(res(id + ".expected"));
        sug.write(ProcessingTestUtil.normalize(program));
        sug.close();
      }

    } catch (SketchException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void bug4() {
    expectGood("bug4");
  }

  @Test
  public void bug5a() {
    expectGood("bug5a");
  }

  @Test
  public void bug5b() {
    expectGood("bug5b");
  }

  @Test
  public void bug6() {
    expectRecognitionException("bug6", 1);
  }

  @Test
  public void bug16() {
    expectRunnerException("bug16", 3);
  }

  @Test
  public void bug136() {
    expectGood("bug136", true);
  }

  @Test
  public void bug196() {
    expectRecognitionException("bug196", 5);
  }

  @Test
  public void bug281() {
    expectGood("bug281");
  }

  @Test
  public void bug481() {
    expectGood("bug481");
  }

  @Test
  public void bug507() {
    expectRecognitionException("bug507", 5);
  }

  @Test
  public void bug598() {
    expectGood("bug598");
  }

  @Test
  public void bug631() {
    expectGood("bug631");
  }

  @Test
  public void bug763() {
    expectRunnerException("bug763", 8);
  }

  // The JDT doesn't seem to mind this now. Commenting out.
  /*@Test
  public void bug820() {
    expectGood("bug820");
  }*/

  @Test
  public void bug1064() {
    expectGood("bug1064");
  }

  @Test
  public void bug1362() {
    expectGood("bug1362");
  }

  @Test
  public void bug1390() {
    expectGood("bug1390");
  }

  @Test
  public void bug1442() {
    expectGood("bug1442");
  }

  @Test
  public void bug1511() {
    expectGood("bug1511");
  }

  @Test
  public void bug1512() {
    expectGood("bug1512");
  }

  @Test
  public void bug1514a() {
    expectGood("bug1514a");
  }

  @Test
  public void bug1514b() {
    expectGood("bug1514b");
  }

  @Test
  public void bug1515() {
    expectGood("bug1515");
  }

  @Test
  public void bug1516() {
    expectGood("bug1516");
  }

  @Test
  public void bug1517() {
    expectGood("bug1517");
  }

  @Test
  public void bug1518a() {
    expectGood("bug1518a");
  }

  @Test
  public void bug1518b() {
    expectGood("bug1518b");
  }

  @Test
  public void bug1525() {
    expectGood("bug1525");
  }

  @Test
  public void bug1532() {
    expectRecognitionException("bug1532", 50);
  }

  @Test
  public void bug1534() {
    expectGood("bug1534");
  }

  @Test
  public void bug1936() {
    expectGood("bug1936");
  }

  @Test
  public void bug315g() {
    expectGood("bug315g");
  }

  @Test
  public void bug400g() {
    expectGood("bug400g", true);
  }

  @Test
  public void bug427g() {
    expectGood("bug427g");
  }

  @Test
  public void color() {
    expectGood("color", true);
  }

  @Test
  public void annotations() {
    expectGood("annotations", true);
  }

  @Test
  public void staticannotations() {
    expectGood("staticannotations", true);
  }

  @Test
  public void generics() {
    expectGood("generics", true);
  }

  @Test
  public void lambda() {
    expectGood("lambdaexample", true);
  }

  @Test
  public void specialMethods() {
    expectGood("speicalmethods", true);
  }

  @Test
  public void specialMethodsPrivate() {
    expectGood("specialmethodsprivate", true);
  }

  @Test
  public void classInStatic() {
    expectGood("classinstatic", true);
  }

  @Test
  public void fullscreen() {
    expectGood("fullscreen", true);
  }

  @Test
  public void fullscreenArg() {
    expectGood("fullscreen_arg", true);
  }

  @Test
  public void customMain() {
    expectGood("custommain", true);
  }

  @Test
  public void charSpecial() {
    expectGood("charspecial", true);
  }

  @Test
  public void typeInference() {
    expectGood("typeinference");
  }

  @Test
  public void testPackage() {
    expectGood("packageTest", true, Optional.of("test.subtest"));
  }

  @Test
  public void testStaticPixelDensity() {
    expectGood("staticpixeldensity");
  }

  @Test
  public void testParamPixelDensity() {
    expectGood("parampixeldensity");
  }

  @Test
  public void testPdfWrite() {
    expectGood("pdfwrite");
  }

  @Test
  public void testColorReturn() {
    expectGood("colorreturn");
  }

  @Test
  public void testNoSmooth() {
    expectGood("nosmooth");
  }

  @Test
  public void testSmooth() {
    expectGood("smoothnoparam");
  }

  @Test
  public void testSmoothThis() {
    expectGood("smoothnoparamthis");
  }

  @Test
  public void testSmoothWithParam() {
    expectGood("smoothparam");
  }

  @Test
  public void testSmoothWithParamStatic() {
    expectGood("smoothparamstatic");
  }

  @Test
  public void testColorInImport() {
    expectGood("colorimport");
  }

  @Test
  public void testPGraphicsStandalone() {
    expectGood("pgraphics");
  }

  @Test
  public void testSizeThis() {
    expectGood("sizethis");
  }

  @Test
  public void testMixing() {
    expectRunnerException("mixing", 6);
  }

  @Test
  public void testSizeClass() {
    expectGood("sizeclass");
  }

  @Test
  public void testMultilineString() {
    expectGood("multilinestr");
  }

  @Test
  public void testMultilineStringClass() {
    expectGood("multilinestrclass");
  }

  @Test
  public void testMultiMultilineString() {
    Preferences.setBoolean("export.application.fullscreen", true);
    expectGood("fullscreen_export");
  }

  @Test
  public void testStaticClass() {
    expectGood("staticclass");
  }

  @Test
  public void testCustomRootClass() {
    expectGood("customrootclass");
  }

  @Test
  public void testExpessionSize() {
    expectGood("expressionsize");
  }

  @Test
  public void testExpessionSizeMethod() {
    expectGood("expressionsizemethod");
  }

  @Test
  public void testExpessionSizeVar() {
    expectGood("expressionsizevar");
  }

  @Test
  public void testWhitespace() {
    expectGood("whitespace", false);
  }

}
