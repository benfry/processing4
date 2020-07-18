package processing.core;

import java.util.Arrays;
import java.util.Random;

/**
 * Gradient Noise generation based on
 * https://rosettacode.org/wiki/Perlin_noise
 * */
public class ImprovedNoiseGenerator implements NoiseGenerator {

  private final Random random = new Random();
  private int[] p;
  private int[] permutation;

  private float generateNoise(float x, float y, float z) {
    if(p == null) initializeNoise();

    int X = (int) Math.floor(x) & 255,                      // FIND UNIT CUBE THAT
        Y = (int) Math.floor(y) & 255,                      // CONTAINS POINT.
        Z = (int) Math.floor(z) & 255;
    x -= Math.floor(x);                                     // FIND RELATIVE X,Y,Z
    y -= Math.floor(y);                                     // OF POINT IN CUBE.
    z -= Math.floor(z);
    float  u = fade(x),                                     // COMPUTE FADE CURVES
           v = fade(y),                                     // FOR EACH OF X,Y,Z.
           w = fade(z);
    int A = p[X] + Y, AA = p[A] + Z, AB = p[A + 1] + Z,     // HASH COORDINATES OF
        B = p[X + 1] + Y, BA = p[B] + Z, BB = p[B + 1] + Z; // THE 8 CUBE CORNERS,

    return lerp(w, lerp(v, lerp(u, grad(p[AA], x, y, z),              // AND ADD
                                   grad(p[BA], x - 1, y, z)),      // BLENDED
                           lerp(u, grad(p[AB], x, y - 1, z),       // RESULTS
                                   grad(p[BB], x - 1, y - 1, z))), // FROM 8
                   lerp(v, lerp(u, grad(p[AA + 1], x, y, z - 1),  // CORNERS
                                   grad(p[BA + 1], x - 1, y, z - 1)), // OF CUBE
                           lerp(u, grad(p[AB + 1], x, y - 1, z - 1),
                                   grad(p[BB + 1], x - 1, y - 1, z - 1))));
  }

  private static float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }
  private static float lerp(float t, float a, float b) { return a + t * (b - a); }
  private static float grad(int hash, float x, float y, float z) {
    int h = hash & 15;                      // CONVERT LO 4 BITS OF HASH CODE
    float u = h<8 ? x : y,                  // INTO 12 GRADIENT DIRECTIONS.
      v = h<4 ? y : h==12||h==14 ? x : z;
    return ((h&1) == 0 ? u : -u) + ((h&2) == 0 ? v : -v);
  }

  @Override
  public float noise(float x, float y, float z) {
    return (float) generateNoise(x, y, z);
  }

  @Override
  public void noiseDetail(int lod) {

  }

  @Override
  public void noiseDetail(int lod, float falloff) {

  }

  @Override
  public void noiseSeed(long seed) {

  }

  private void initializeNoise() {
    p = new int[512];
    permutation = new int[256];
    Arrays.parallelSetAll(permutation, i -> random.nextInt(255));
    Arrays.parallelSetAll(p, i -> permutation[i % permutation.length]);
  }
}
