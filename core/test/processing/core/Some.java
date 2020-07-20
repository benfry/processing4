package processing.core;

import java.util.Random;

/**
 *
 * */
public class Some {

  private static Random randomProvider = new Random();

  /**
   * represents an arbitrary float to use in testing
   *
   * @return an arbitrary float
   */
  public static float someFloat() {
    return randomProvider.nextFloat();
  }

  /**
   * represents an arbitrary long to use in testing
   *
   * @return an arbitrary long
   * */
  public static long someLong() {
    return randomProvider.nextLong();
  }

}
