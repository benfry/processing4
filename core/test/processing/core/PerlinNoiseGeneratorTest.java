package processing.core;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

import java.util.stream.IntStream;

public class PerlinNoiseGeneratorTest {

  private PerlinNoiseGenerator sut;
  private final long TEST_SEED = 1L;

  @Before
  public void setup(){
    sut = new PerlinNoiseGenerator();
  }

  @Test
  public void noiseGenerator_usingSeed_returnsExpectedValues() {
    sut.noiseSeed(TEST_SEED);

    double[] result = IntStream.range(0, 10).mapToDouble(i -> sut.noise(i, i, i)).toArray();

    Assert.assertArrayEquals(result, new double[]{
      // results collected by repeating above with original implementation
       0.6851983070373535,
       0.4338264763355255,
       0.7655746936798096,
       0.42400380969047546,
       0.7723499536514282,
       0.6922339200973511,
       0.46340879797935486,
       0.6506533622741699,
       0.6501635909080505,
       0.4172065556049347
    }, 0.0);
  }

  @Test
  public void noiseGenerator_sameParameters_returnsSameResult() {
    float x = anyFloat();
    float y = anyFloat();
    float z = anyFloat();

    float value = sut.noise(x, y, z);
    Assert.assertEquals(value, sut.noise(x, y, z), 0.0);
  }

  @Test
  public void noiseGenerator_sameSeed_returnsSameResult() {

    float x = anyFloat();
    float y = anyFloat();
    float z = anyFloat();

    sut.noiseSeed(TEST_SEED);
    float firstValue = sut.noise(x, y, z);

    sut.noiseSeed(TEST_SEED);
    float secondValue = sut.noise(x, y, z);

    Assert.assertEquals(firstValue, secondValue, 0.0);
  }

  @Test
  public void noiseGenerator_differentSeed_returnsDifferentResult() {

    float x = anyFloat();
    float y = anyFloat();
    float z = anyFloat();

    sut.noiseSeed(TEST_SEED);
    float firstValue = sut.noise(x, y, z);

    sut.noiseSeed(TEST_SEED + anyLong());
    float secondValue = sut.noise(x, y, z);

    Assert.assertFalse(firstValue == secondValue);
  }

}