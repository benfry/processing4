package processing.core.nurbs;

import java.util.function.BiFunction;

public class Nurbs {
    protected static Polynomial[][] calcBasisFunctions(int degree, float[] knotVector) {
        Polynomial[][][] functions = new Polynomial[degree + 1][knotVector.length - 1][knotVector.length];
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
                for (int i = 0; i + n < knotVector.length - 1; i++) {
                    if (k < i || i + n < k) {
                        functions[n][k][i] = zero;
                        continue;
                    }
                    if (n == 0) {
                        functions[n][k][i] = one;
                        continue;
                    }
                    functions[n][k][i] = getF.apply(n, i)
                            .mul(functions[n - 1][k][i])
                            .add(one.sub(getF.apply(n, i + 1))
                                    .mul(functions[n - 1][k][i + 1])
                            );
                }
            }
        }
        return functions[degree];
    }
}
