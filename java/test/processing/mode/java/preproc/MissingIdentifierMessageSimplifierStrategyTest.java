package processing.mode.java.preproc;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import processing.mode.java.preproc.PdeIssueEmitter;
import processing.mode.java.preproc.PreprocessIssueMessageSimplifier;

import java.util.Optional;


public class MissingIdentifierMessageSimplifierStrategyTest {

  private PreprocessIssueMessageSimplifier.PreprocIssueMessageSimplifierStrategy strategy;

  @Before
  public void setup() {
    strategy = PreprocessIssueMessageSimplifier.get()
        .createMissingIdentifierSimplifierStrategy();
  }

  @Test
  public void testPresent() {
    Optional<PdeIssueEmitter.IssueMessageSimplification> msg = strategy.simplify("Missing identifier at ';'", 123);
    Assert.assertTrue(msg.isPresent());
  }

  @Test
  public void testNotPresent() {
    Optional<PdeIssueEmitter.IssueMessageSimplification> msg = strategy.simplify("String x = \" \\\" \"", 123);
    Assert.assertTrue(msg.isEmpty());
  }

}