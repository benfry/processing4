package processing.mode.java;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import processing.app.Problem;
import processing.app.ui.Editor;
import processing.mode.java.preproc.PdePreprocessIssue;

import java.util.ArrayList;
import java.util.List;

public class ProblemFactoryTest {

  private PdePreprocessIssue pdePreprocessIssue;
  private List<Integer> tabStarts;

  private List<Integer> starts;

  @Before
  public void setUp() {
    pdePreprocessIssue = new PdePreprocessIssue(8, 2, "test");

    tabStarts = new ArrayList<>();
    tabStarts.add(5);

    starts = new ArrayList<>();
    starts.add(0);
    starts.add(5);
    starts.add(10);
  }

  @Test
  public void buildWithoutEditor() {
    Problem problem = ProblemFactory.build(pdePreprocessIssue, tabStarts);

    Assert.assertEquals(3, problem.getLineNumber());
    Assert.assertEquals("test", problem.getMessage());
  }

  @Test
  public void getTabStart() {
    Assert.assertEquals(0, ProblemFactory.getTab(starts, 0).getTab());
  }

  @Test
  public void getTabMiddleFrontEdge() {
    Assert.assertEquals(1, ProblemFactory.getTab(starts, 5).getTab());
  }

  @Test
  public void getTabMiddle() {
    TabLine tabLine = ProblemFactory.getTab(starts, 7);
    Assert.assertEquals(1, tabLine.getTab());
    Assert.assertEquals(2, tabLine.getLineInTab());
  }

  @Test
  public void getTabMiddleBackEdge() {
    Assert.assertEquals(2, ProblemFactory.getTab(starts, 10).getTab());
  }

  @Test
  public void getTabEnd() {
    Assert.assertEquals(2, ProblemFactory.getTab(starts, 15).getTab());
  }

}
