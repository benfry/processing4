package processing.mode.java.preproc.issue;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import processing.mode.java.preproc.issue.IssueMessageSimplification;

import java.util.Optional;


public class MissingClassNameMessageSimplifierStrategyTest {

  private PreprocessIssueMessageSimplifier.PreprocIssueMessageSimplifierStrategy strategy;

  @Before
  public void setup() {
    strategy = PreprocessIssueMessageSimplifier.get().createMissingClassNameStrategy();
  }

  @Test
  public void testPresentExtends() {
    Optional<IssueMessageSimplification> msg = strategy.simplify("class extends Base\n{");
    Assert.assertTrue(msg.isPresent());
  }

  @Test
  public void testPresentNoExtends() {
    Optional<IssueMessageSimplification> msg = strategy.simplify("class \n{");
    Assert.assertTrue(msg.isPresent());
  }

  @Test
  public void testNotPresent() {
    Optional<IssueMessageSimplification> msg = strategy.simplify("int x = y");
    Assert.assertTrue(msg.isEmpty());
  }

}