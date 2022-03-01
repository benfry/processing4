package processing.core.nurbs;

class Polynomial {
    private float[] coefficients;

    /**
     * Creates a new polynomial with terms formed like: coefficients[i] x^i. Removes trailing zeroes from the coefficients array if necessary.
     *
     * @param coefficients The coefficients.
     */
    public Polynomial(float[] coefficients) {
        this.coefficients = coefficients;
        for (int i = coefficients.length - 1; i >= 0; i--) {
            if (coefficients[i] == 0) {
                continue;
            }
            if (i == coefficients.length - 1) {
                break;
            }
            this.coefficients = new float[i + 1];
            System.arraycopy(coefficients, 0, this.coefficients, 0, this.coefficients.length);
            break;
        }
    }

    /**
     * Adds another polynomial.
     *
     * @param other The polynomial to add
     * @return The sum of the polynomials.
     */
    public Polynomial add(Polynomial other) {
        float[] newCoefficients = new float[Math.max(coefficients.length, other.coefficients.length)];
        for (int i = 0; i < newCoefficients.length; i++) {
            newCoefficients[i] = (i < coefficients.length ? coefficients[i] : 0) + (i < other.coefficients.length ? other.coefficients[i] : 0);
        }
        return new Polynomial(newCoefficients);
    }

    /**
     * Subtracts another polynomial.
     *
     * @param other The polynomial to subtract
     * @return The difference of the polynomials.
     */
    public Polynomial sub(Polynomial other) {
        float[] newCoefficients = new float[Math.max(coefficients.length, other.coefficients.length)];
        for (int i = 0; i < newCoefficients.length; i++) {
            newCoefficients[i] = (i < coefficients.length ? coefficients[i] : 0) - (i < other.coefficients.length ? other.coefficients[i] : 0);
        }
        return new Polynomial(newCoefficients);
    }

    /**
     * Multiplies by another polynomial.
     *
     * @param other The polynomial to multiply by
     * @return The product of the polynomials.
     */
    public Polynomial mul(Polynomial other) {
        float[] newCoefficients = new float[coefficients.length + other.coefficients.length];
        for (int i = 0; i < coefficients.length + other.coefficients.length; i++) {
            newCoefficients[i] = 0;
            for (int j = Math.max(0, i - coefficients.length + 1); j <= i && j < other.coefficients.length; j++) {
                newCoefficients[i] += coefficients[i - j] * other.coefficients[j];
            }
        }
        return new Polynomial(newCoefficients);
    }

    /**
     * Evaluates the polynomial in a point.
     *
     * @param t The point to evaluate in
     * @return The value of the polynomial at t
     */
    public float evaluate(float t) {
        float xn = 1; // t^n
        float result = 0;
        for (float v : coefficients) {
            result += v * xn;
            xn *= t;
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (int i = coefficients.length - 1; i >= 0; i--) {
            if (i < coefficients.length - 1) {
                result.append(" + ");
            }
            if (i == 0) {
                result.append(coefficients[i]);
                continue;
            }
            if (i == 1) {
                result.append(String.format("%s t", coefficients[i]));
                continue;
            }
            result.append(String.format("%s t^%s", coefficients[i], i));
        }
        return result.toString();
    }
}
