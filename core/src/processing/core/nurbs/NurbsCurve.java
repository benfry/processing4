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
        for (int k = 0; k < points.length; k++) {
            if (knotVector[k + 1] <= knotVector[k]) {
                continue;
            }
            for (float t = knotVector[k]; t < knotVector[k + 1]; t += stepSize) {
                float[] basisWeights = new float[points.length];
                float basisWeightsSum = 0;
                for (int i = Math.max(degree - k, 0); i <= degree; i++) {
                    basisWeightsSum += basisWeights[i] = basisFunctions[k][i].evaluate(t) * weights[i + k - degree];
                }
                PVector result = new PVector();
                for (int i = Math.max(degree - k, 0); i <= degree; i++) {
                    result = PVector.add(result, PVector.mult(points[i + k - degree], basisWeights[i] / basisWeightsSum));
                }
                g.vertex(result.array());
            }
        }
        g.endShape();
    }

}
