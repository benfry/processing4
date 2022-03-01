package processing.core.nurbs;

import java.util.function.BiFunction;

public class Nurbs {
    protected static Polynomial[][] calcBasisFunctions(int degree, float[] knotVector) {
        Polynomial[][][] functions = new Polynomial[degree + 1][knotVector.length - 1][degree + 1];
        Polynomial zero = new Polynomial(new float[0]);
        Polynomial one = new Polynomial(new float[]{1});

        BiFunction<Integer, Integer, Polynomial> getF = (n, i) -> knotVector[i + n] <= knotVector[i] ? one : new Polynomial(new float[]{
                -knotVector[i] / (knotVector[i + n] - knotVector[i]),
                1              / (knotVector[i + n] - knotVector[i])
        });

        for (int k = 0; k < knotVector.length - 1; k++) {
            if (knotVector[k + 1] <= knotVector[k]) {
                continue;
            }
            for (int n = 0; n <= degree; n++) {
                for (int i = Math.max(degree - k, degree - n); i <= degree && i + k + n - degree < knotVector.length - 1; i++) {
                    if (n == 0) {
                        functions[n][k][i] = one;
                        continue;
                    }

                    Polynomial f1 = (i <= degree - n) ? zero : functions[n - 1][k][i];
                    Polynomial f2 = (degree < i + 1) ? zero : functions[n - 1][k][i + 1];

                    functions[n][k][i] = f1.mul(getF.apply(n, i + k - degree)).add(
                            f2.mul(one.sub(getF.apply(n, i + k - degree + 1)))
                    );
                }
            }
        }
        return functions[degree];
    }
}
