package processing.mode.java.preproc.issue.strategy;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import processing.mode.java.preproc.issue.IssueMessageSimplification;

import java.util.Optional;


public class MissingDoubleQuoteMessageSimplifierStrategyTest {

  private MissingDoubleQuoteMessageSimplifierStrategy strategy;

  @Before
  public void setup() {
    strategy = new MissingDoubleQuoteMessageSimplifierStrategy();
  }

  @Test
  public void testPresent() {
    Optional<IssueMessageSimplification> msg = strategy.simplify("String x = \" \" \"");
    Assert.assertTrue(msg.isPresent());
  }

  @Test
  public void testNotPresent() {
    Optional<IssueMessageSimplification> msg = strategy.simplify("String x = \" \\\" \"");
    Assert.assertTrue(msg.isEmpty());
  }

}