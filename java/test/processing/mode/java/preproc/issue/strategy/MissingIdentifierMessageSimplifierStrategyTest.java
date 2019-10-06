package processing.mode.java.preproc.issue.strategy;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import processing.mode.java.preproc.issue.IssueMessageSimplification;

import java.util.Optional;


public class MissingIdentifierMessageSimplifierStrategyTest {

  private MissingIdentifierMessageSimplifierStrategy strategy;

  @Before
  public void setup() {
    strategy = new MissingIdentifierMessageSimplifierStrategy();
  }

  @Test
  public void testPresent() {
    Optional<IssueMessageSimplification> msg = strategy.simplify("Missing identifier at ';'");
    Assert.assertTrue(msg.isPresent());
  }

  @Test
  public void testNotPresent() {
    Optional<IssueMessageSimplification> msg = strategy.simplify("String x = \" \\\" \"");
    Assert.assertTrue(msg.isEmpty());
  }

}