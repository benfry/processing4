package processing.mode.java.preproc;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import processing.mode.java.preproc.PdeIssueEmitter;
import processing.mode.java.preproc.PreprocessIssueMessageSimplifier;

import java.util.Optional;


public class AssignmentMessageSimplifierStrategyTest {

  private PreprocessIssueMessageSimplifier.PreprocIssueMessageSimplifierStrategy strategy;

  @Before
  public void setup() {
    strategy = PreprocessIssueMessageSimplifier.get().createInvalidAssignmentStrategy();
  }

  @Test
  public void testPresent() {
    Optional<PdeIssueEmitter.IssueMessageSimplification> msg = strategy.simplify("  int x =");
    Assert.assertTrue(msg.isPresent());
  }

  @Test
  public void testPresentDiamond() {
    Optional<PdeIssueEmitter.IssueMessageSimplification> msg = strategy.simplify("  List<Integer> x =");
    Assert.assertTrue(msg.isPresent());
  }

  @Test
  public void testNotPresent() {
    Optional<PdeIssueEmitter.IssueMessageSimplification> msg = strategy.simplify("class {");
    Assert.assertTrue(msg.isEmpty());
  }

}