package processing.core;

import java.util.function.BiFunction;

public class NURBS {
    private final PVector[] points;
    private final float[] weights;
    private final float[] knot;
    private final int degree;

    private Polynomial[][] basisFunctions;

    public NURBS(PVector[] points, float[] weights, float[] knot) {
        if (points.length != weights.length) {
            throw new IllegalArgumentException("The points and weights vectors must be of equal length");
        }
        this.points = points;
        this.weights = weights;
        this.knot = knot;
        this.degree = knot.length - points.length - 1;

        calcBasisFunctions();
    }

    private void calcBasisFunctions() {
//        System.out.println(degree);
        Polynomial[][][] functions = new Polynomial[degree + 1][knot.length - 1][knot.length];
        Polynomial zero = new Polynomial(new float[0]);
        Polynomial one = new Polynomial(new float[]{1});

        BiFunction<Integer, Integer, Polynomial> getF = (n, i) -> knot[i + n] <= knot[i] ? one : new Polynomial(new float[]{
                -knot[i] / (knot[i + n] - knot[i]),
                1        / (knot[i + n] - knot[i])
        });

        for (int k = 0; k < knot.length - 1; k++) {
            if (knot[k + 1] <= knot[k]) {
                continue;
            }
            for (int n = 0; n <= degree; n++) {
                for (int i = 0; i + n < knot.length - 1; i++) {
                    if (k < i || i + n < k) {
                        functions[n][k][i] = zero;
//                        System.out.printf("k: %s, n: %s, i: %s -> zero%n", k, n, i);
                        continue;
                    }
                    if (n == 0) {
                        functions[n][k][i] = one;
//                        System.out.printf("k: %s, n: %s, i: %s -> one%n", k, n, i);
                        continue;
                    }
//                    System.out.printf("k: %s, n: %s, i: %s -> (%s) * (%s), (%s) * (%s)%n", k, n, i, functions[n - 1][k][i], getF.apply(n, i), functions[n - 1][k][i + 1], one.sub(getF.apply(n, i + 1)));
                    functions[n][k][i] = getF.apply(n, i)
                            .mul(functions[n - 1][k][i])
                            .add(one.sub(getF.apply(n, i + 1))
                                    .mul(functions[n - 1][k][i + 1])
                            );
//                    System.out.printf("k: %s, n: %s, i: %s -> %s%n", k, n, i, functions[n][k][i]);
//                    for (float t = knot[k]; t <= knot[k + 1]; t += .1) {
//                        System.out.printf("(%s, %s)%n", t, functions[n][k][i].eval(t));
//                    }
                }
            }
        }
        basisFunctions = functions[degree];
    }

    public void draw(PGraphics g, int steps) {
        g.beginShape();
        float stepSize = (knot[knot.length - 1] - knot[0]) / steps;
        for (int k = 0; k < points.length; k++) {
            if (knot[k + 1] <= knot[k]) {
                continue;
            }
            for (float t = knot[k]; t < knot[k + 1]; t += stepSize) {
                float[] basisWeights = new float[points.length];
                float basisWeightsSum = 0;
//                System.out.printf("Start %s, %s, %s%n", k, t, stepSize);
//                System.out.println(Arrays.toString(basisFunctions));
                for (int i = Math.max(k - degree, 0); i <= k; i++) {
//                    System.out.printf("f[i]: %s, %s%n", basisFunctions[k][i], basisFunctions[k][i].eval(t));
                    basisWeightsSum += basisWeights[i] = basisFunctions[k][i].eval(t) * weights[i];
                }
//                System.out.println(Arrays.toString(basisWeights));
//                System.out.println(basisWeightsSum);
                PVector result = new PVector();
                for (int i = Math.max(k - degree, 0); i <= k; i++) {
                    result = PVector.add(result, PVector.mult(points[i], basisWeights[i] / basisWeightsSum));
                }
//                System.out.println(result);
                g.vertex(result.array());
            }
        }
        g.endShape();
    }

    private static class Polynomial {
        private float[] c;

        public Polynomial(float[] c) {
            this.c = c;
            for (int i = c.length - 1; i >= 0; i--) {
                if (c[i] == 0) {
                    continue;
                }
                if (i == c.length - 1) {
                    break;
                }
                this.c = new float[i + 1];
                System.arraycopy(c, 0, this.c, 0, this.c.length);
                break;
            }
        }

        public Polynomial add(Polynomial p) {
            float[] newc = new float[Math.max(c.length, p.c.length)];
            for (int i = 0; i < newc.length; i++) {
                newc[i] = (i < c.length ? c[i] : 0) + (i < p.c.length ? p.c[i] : 0);
            }
            return new Polynomial(newc);
        }

        public Polynomial sub(Polynomial p) {
            float[] newc = new float[Math.max(c.length, p.c.length)];
            for (int i = 0; i < newc.length; i++) {
                newc[i] = (i < c.length ? c[i] : 0) - (i < p.c.length ? p.c[i] : 0);
            }
            return new Polynomial(newc);
        }

        public Polynomial mul(Polynomial p) {
            float[] newc = new float[c.length + p.c.length];
            for (int i = 0; i < c.length + p.c.length; i++) {
                newc[i] = 0;
                for (int j = Math.max(0, i - c.length + 1); j <= i && j < p.c.length; j++) {
                    newc[i] += c[i - j] * p.c[j];
                }
            }
            return new Polynomial(newc);
        }

        public float eval(float x) {
            float xn = 1;
            float result = 0;
            for (float v : c) {
                result += v * xn;
                xn *= x;
            }
            return result;
        }

        public String toString() {
            StringBuilder result = new StringBuilder();
            for (int i = c.length - 1; i >= 0; i--) {
                result.append(String.format("+ %s t^%s", c[i], i));
            }
            return result.toString();
        }
    }
}
