package processing.core.nurbs;

import java.util.function.BiFunction;

public class Nurbs {
    protected static float[] calcBasisFunctionValues(int k, float t, int degree, float[] knotVector) {
        if (k < 0 || knotVector.length <= k + 1) {
            throw new IllegalArgumentException("Knot out of bounds.");
        }
        if (knotVector[k + 1] <= knotVector[k]) {
            throw new IllegalArgumentException("The knot interval has nonpositive length.");
        }

        float[][] basisValues = new float[degree + 1][degree + 1];

        BiFunction<Integer, Integer, Float> getF = (n, i) ->
                (knotVector[i + n] <= knotVector[i])
                        ? 1
                        : (t - knotVector[i]) / (knotVector[i + n] - knotVector[i]);

        for (int n = 0; n <= degree; n++) {
            for (int i = Math.max(degree - k, degree - n); i <= degree && i + k + n - degree < knotVector.length - 1; i++) {
                if (n == 0) {
                    basisValues[n][i] = 1;
                    continue;
                }

                float v1 = (i <= degree - n) ? 0 : basisValues[n - 1][i];
                float v2 = (degree < i + 1) ? 0 : basisValues[n - 1][i + 1];

                basisValues[n][i] = v1 * getF.apply(n, i + k - degree) + v2 * (1 - getF.apply(n, i + k - degree + 1));
            }
        }
        return basisValues[degree];
    }
}
