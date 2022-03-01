package processing.core.nurbs;

import java.util.function.BiFunction;

public class Nurbs {
    protected static Polynomial[][] calcBasisFunctions(int degree, float[] knotVector) {
        Polynomial[][][] functions = new Polynomial[degree + 1][knotVector.length - 1][degree + 1];
        Polynomial zero = new Polynomial(new float[0]);
        Polynomial one = new Polynomial(new float[]{1});

        BiFunction<Integer, Integer, Polynomial> getF = (n, i) -> knotVector[i + n] <= knotVector[i] ? one : new Polynomial(new float[]{
                -knotVector[i] / (knotVector[i + n] - knotVector[i]),
                1        / (knotVector[i + n] - knotVector[i])
        });

        for (int k = 0; k < knotVector.length - 1; k++) {
            if (knotVector[k + 1] <= knotVector[k]) {
                continue;
            }
            for (int n = 0; n <= degree; n++) {
                for (int i = Math.max(0, k - n); i + n < knotVector.length - 1 && i <= k; i++) {
                    System.out.printf("(%s, %s, %s): %s\n", n, k, i, degree - k);
                    if (n == 0) {
                        functions[n][k][i - k + degree] = one;
                        continue;
                    }

                    Polynomial f1 = (i <= k - n) ? zero : functions[n - 1][k][i - k + degree];
                    Polynomial f2 = (k < i + 1) ? zero : functions[n - 1][k][i - k + degree + 1];

                    functions[n][k][i - k + degree] = getF.apply(n, i)
                            .mul(f1)
                            .add(one.sub(getF.apply(n, i + 1))
                                    .mul(f2)
                            );
                    System.out.printf("%s\n", functions[n][k][i - k + degree]);
                }
            }
        }
        return functions[degree];
    }
}
