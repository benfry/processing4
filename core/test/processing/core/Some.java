package processing.core;

import java.util.Random;

public class Some {

  private static Random randomProvider = new Random();

  public static float floatValue() {
    return randomProvider.nextFloat();
  }

  public static long longValue() {
    return randomProvider.nextLong();
  }

}
