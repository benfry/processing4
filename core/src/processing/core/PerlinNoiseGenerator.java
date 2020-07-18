package processing.core;

import java.util.Random;
import java.util.stream.IntStream;

public class PerlinNoiseGenerator implements NoiseGenerator {
  private static final int PERLIN_YWRAPB = 4;
  private static final int PERLIN_YWRAP = 1 << PERLIN_YWRAPB;
  private static final int PERLIN_ZWRAPB = 8;
  private static final int PERLIN_ZWRAP = 1 << PERLIN_ZWRAPB;
  private static final int PERLIN_SIZE = 4095;
  private static final int PERLIN_TWOPI = PGraphics.SINCOS_LENGTH;
  private static final int PERLIN_PI = PGraphics.SINCOS_LENGTH >> 1;

  private float perlin_amp_falloff;
  private int perlin_octaves = 4;
  private float[] perlin_cosTable = PGraphics.cosLUT;
  private float[] perlin;
  private Random perlinRandom;

  @Override public float noise(float x) {
    // is this legit? it's a dumb way to do it (but repair it later)
    return noise(x, 0f, 0f);
  }

  @Override public float noise(float x, float y) {
    return noise(x, y, 0f);
  }

  @Override public float noise(float x, float y, float z) {
    if (x < 0) x = -x;
    if (y < 0) y = -y;
    if (z < 0) z = -z;

    int xi = (int) x,
        yi = (int) y,
        zi = (int) z;

    float xf = x - xi;
    float yf = y - yi;
    float zf = z - zi;
    float rxf, ryf;

    float r = 0;
    float ampl = 0.5f;

    float n1, n2, n3;

      for (int i = 0; i < perlin_octaves; i++) {
        int of = xi + (yi << PERLIN_YWRAPB) + (zi << PERLIN_ZWRAPB);

        rxf = noise_fsc(xf);
        ryf = noise_fsc(yf);

        n1 = getPerlin()[of & PERLIN_SIZE];
        n1 += rxf * (getPerlin()[(of + 1) & PERLIN_SIZE] - n1);
        n2 = getPerlin()[(of + PERLIN_YWRAP) & PERLIN_SIZE];
        n2 += rxf * (getPerlin()[(of + PERLIN_YWRAP + 1) & PERLIN_SIZE] - n2);
        n1 += ryf * (n2 - n1);

        of += PERLIN_ZWRAP;
        n2 = getPerlin()[of & PERLIN_SIZE];
        n2 += rxf * (getPerlin()[(of + 1) & PERLIN_SIZE] - n2);
        n3 = getPerlin()[(of + PERLIN_YWRAP) & PERLIN_SIZE];
        n3 += rxf * (getPerlin()[(of + PERLIN_YWRAP + 1) & PERLIN_SIZE] - n3);
        n2 += ryf * (n3 - n2);

        n1 += noise_fsc(zf) * (n2 - n1);

        r += n1 * ampl;
        ampl *= perlin_amp_falloff;
        xi <<= 1;
        xf *= 2;
        yi <<= 1;
        yf *= 2;
        zi <<= 1;
        zf *= 2;

        if (xf >= 1.0f) {
          xi++;
          xf--;
        }
        if (yf >= 1.0f) {
          yi++;
          yf--;
        }
        if (zf >= 1.0f) {
          zi++;
          zf--;
        }
      }
      return r;
  }

  // now adjusts to the size of the cosLUT used via
  // the new variables, defined above
  private float noise_fsc(float i) {
    // using bagel's cosine table instead
    return 0.5f * (1.0f - perlin_cosTable[(int) (i * PERLIN_PI) % PERLIN_TWOPI]);
  }

  @Override
  public void noiseDetail(int lod) {
    if (lod>0)
      perlin_octaves = lod;
  }

  @Override
  public void noiseDetail(int lod, float falloff) {
    if (lod>0)
      perlin_octaves = lod;
    if (falloff>0)
      perlin_amp_falloff = falloff;
  }

  @Override
  public void noiseSeed(long seed) {
    if (perlinRandom == null) perlinRandom = new Random();

    perlinRandom.setSeed(seed);
    perlin = null;
  }

  private float[] getPerlin() {
    if (perlin == null) {
      perlin = new float[PERLIN_SIZE + 1];
      IntStream.range(0, perlin.length).parallel()
        .forEach(this::updatePerlinAtIndex);
    }

    return perlin;
  }

  private void updatePerlinAtIndex(int i){
    perlin[i] = getPerlinRandom().nextFloat();
  }

  private Random getPerlinRandom() {
    if(perlinRandom == null) perlinRandom = new Random();

    return perlinRandom;
  }
}