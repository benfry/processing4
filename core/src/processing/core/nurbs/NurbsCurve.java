package processing.core.nurbs;

import processing.core.PGraphics;
import processing.core.PVector;

public class NurbsCurve extends Nurbs {
    private PVector[] points;
    private float[] weights;

    private final float[] knotVector;
    private final int degree;

    private final Polynomial[][] basisFunctions;

    public NurbsCurve(PVector[] points, float[] weights, float[] knotVector) {
        if (points.length != weights.length) {
            throw new IllegalArgumentException("The points and weights vectors must be of equal length");
        }
        this.points = points;
        this.weights = weights;
        this.knotVector = knotVector;
        this.degree = knotVector.length - points.length - 1;

        if (degree <= 0) {
            throw new IllegalArgumentException("The degree of the curve must be at least 1.");
        }

        this.basisFunctions = calcBasisFunctions(degree, knotVector);
    }

    public void setPoint(int index, PVector point) {
        if (index < 0 || points.length <= index) {
            throw new IllegalArgumentException("Index out of bounds");
        }
        points[index] = point;
    }

    public void setPoints(PVector[] points) {
        if (this.points.length != points.length) {
            throw new IllegalArgumentException("The old points array is not the same length as the new one.");
        }
        this.points = points;
    }

    public void setWeight(int index, float weight) {
        if (index < 0 || weights.length <= index) {
            throw new IllegalArgumentException("Index out of bounds");
        }
        weights[index] = weight;
    }

    public void setWeights(float[] weights) {
        if (this.weights.length != weights.length) {
            throw new IllegalArgumentException("The old weights array is not the same length as the new one.");
        }
        this.weights = weights;
    }

    public void draw(PGraphics g, int steps) {
        g.beginShape();
        float stepSize = (knotVector[knotVector.length - 1] - knotVector[0]) / steps;
        for (int knot = 0; knot < points.length; knot++) {
            if (knotVector[knot + 1] <= knotVector[knot]) {
                continue;
            }
            for (float t = knotVector[knot]; t < knotVector[knot + 1]; t += stepSize) {
                g.vertex(evaluate(t, knot).array());
            }
        }
        g.vertex(evaluate(knotVector[points.length], points.length - 1).array());
        g.endShape();
    }

    public PVector evaluate(float t) {
        if (t < knotVector[0]) {
            return points[0];
        }
        if (t > knotVector[knotVector.length - 1]) {
            return points[points.length - 1];
        }

        for (int knot = 1; knot < knotVector.length; knot++) {
            if (t <= knotVector[knot]) {
                return evaluate(t, knot - 1);
            }
        }
        throw new RuntimeException("This code should never execute.");
    }

    public PVector evaluate(float t, int knot) {
        if (knot < 0 || knotVector.length <= knot + 1) {
            throw new IllegalArgumentException("Knot out of bounds.");
        }
        if (t < knotVector[knot] || knotVector[knot + 1] < t) {
            throw new IllegalArgumentException("t should be between the knot and the next knot.");
        }

        float[] basisWeights = new float[points.length];
        float basisWeightsSum = 0;
        for (int i = Math.max(degree - knot, 0); i <= degree; i++) {
            basisWeightsSum += basisWeights[i] = basisFunctions[knot][i].evaluate(t) * weights[i + knot - degree];
        }
        PVector result = new PVector();
        for (int i = Math.max(degree - knot, 0); i <= degree; i++) {
            result = PVector.add(result, PVector.mult(points[i + knot - degree], basisWeights[i] / basisWeightsSum));
        }
        return result;
    }

}
