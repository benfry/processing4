package processing.mode.java.preproc.issue.strategy;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import processing.mode.java.preproc.issue.IssueMessageSimplification;

import java.util.Optional;

public class MissingSingleQuoteMessageSimplifierStrategyTest {

  private MissingSingleQuoteMessageSimplifierStrategy strategy;

  @Before
  public void setup() {
    strategy = new MissingSingleQuoteMessageSimplifierStrategy();
  }

  @Test
  public void testPresent() {
    Optional<IssueMessageSimplification> msg = strategy.simplify("char x = '");
    Assert.assertTrue(msg.isPresent());
  }

  @Test
  public void testNotPresent() {
    Optional<IssueMessageSimplification> msg = strategy.simplify("char x = '\\''");
    Assert.assertTrue(msg.isEmpty());
  }

}