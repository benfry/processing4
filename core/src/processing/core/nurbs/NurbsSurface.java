package processing.core.nurbs;

import processing.core.PGraphics;
import processing.core.PVector;

import java.util.LinkedList;
import java.util.Queue;

public class NurbsSurface extends Nurbs {
    private PVector[][] points;
    private float[][] weights;

    private final float[] sKnotVector;
    private final float[] tKnotVector;
    private final int sDegree;
    private final int tDegree;

    public NurbsSurface(PVector[][] points, float[][] weights, float[] sKnotVector, float[] tKnotVector) {
        if (points.length != weights.length) {
            throw new IllegalArgumentException("The points and weights arrays must be of equal dimension");
        }
        for (int i = 0; i < points.length; i++) {
            if (points[i].length != weights[i].length) {
                throw new IllegalArgumentException("The points and weights arrays must be of equal dimension");
            }
            if (points[i].length != points[0].length) {
                throw new IllegalArgumentException("The points and weights arrays must be square");
            }
        }
        this.points = points;
        this.weights = weights;

        this.sKnotVector = sKnotVector;
        this.sDegree = sKnotVector.length - points.length - 1;
        if (sDegree <= 0) {
            throw new IllegalArgumentException("The degree for the first parameter of the surface must be at least 1.");
        }

        this.tKnotVector = tKnotVector;
        this.tDegree = tKnotVector.length - points[0].length - 1;

        if (tDegree <= 0) {
            throw new IllegalArgumentException("The degree for the second parameter of the surface must be at least 1.");
        }
    }

    public void setPoint(int sIndex, int tIndex, PVector point) {
        if (sIndex < 0 || points.length <= sIndex) {
            throw new IllegalArgumentException("First index out of bounds");
        }
        if (tIndex < 0 || points[0].length <= tIndex) {
            throw new IllegalArgumentException("Second index out of bounds");
        }
        points[sIndex][tIndex] = point;
    }

    public void setPoints(PVector[][] points) {
        if (this.points.length != points.length) {
            throw new IllegalArgumentException("The old points array does not have the same dimensions as the new one.");
        }
        for (int i = 0; i < points.length; i++) {
            if (this.points[i].length != points[i].length) {
                throw new IllegalArgumentException("The old points array does not have the same dimensions as the new one.");
            }
        }
        this.points = points;
    }

    public void setWeight(int sIndex, int tIndex, float weight) {
        if (sIndex < 0 || weights.length <= sIndex) {
            throw new IllegalArgumentException("First index out of bounds");
        }
        if (tIndex < 0 || weights[0].length <= tIndex) {
            throw new IllegalArgumentException("Second index out of bounds");
        }
        weights[sIndex][tIndex] = weight;
    }

    public void setWeights(float[][] weights) {
        if (this.weights.length != weights.length) {
            throw new IllegalArgumentException("The old weights array does not have the same dimensions as the new one.");
        }
        for (int i = 0; i < weights.length; i++) {
            if (this.weights[i].length != weights[i].length) {
                throw new IllegalArgumentException("The old weights array does not have the same dimensions as the new one.");
            }
        }
        this.weights = weights;
    }

    public void draw(PGraphics g, int steps) {
        draw(g, steps, steps);
    }

    public void draw(PGraphics g, int sSteps, int tSteps) {
        float sStepSize = (sKnotVector[sKnotVector.length - 1] - sKnotVector[0]) / sSteps;
        float tStepSize = (tKnotVector[sKnotVector.length - 1] - tKnotVector[0]) / tSteps;

        Queue<PVector> lastRow, currentRow = null;

        int sKnot = 0;
        for (float s = sKnotVector[0]; s <= sKnotVector[sKnotVector.length - 1]; s = Math.min(s + sStepSize, sKnotVector[sKnotVector.length - 1])) {
            while (sKnotVector[sKnot + 1] <= s && s < sKnotVector[sKnotVector.length - 1]) {
                sKnot++;
            }

            g.beginShape(g.TRIANGLE_STRIP);
            lastRow = currentRow;
            currentRow = new LinkedList<>();

            int tKnot = 0;
            for (float t = tKnotVector[0]; t <= tKnotVector[tKnotVector.length - 1]; t = Math.min(t + tStepSize, tKnotVector[tKnotVector.length - 1])) {
                while (tKnotVector[tKnot + 1] <= t && t < tKnotVector[tKnotVector.length - 1]) {
                    tKnot++;
                }

                PVector p = evaluate(s, sKnot, t, tKnot);
                if (lastRow != null) {
                    PVector q = lastRow.remove();
                    g.vertex(q.x, q.y, q.z);
                    g.vertex(p.x, p.y, p.z);
                }
                currentRow.add(p);

                if (t == tKnotVector[points[0].length]) {
                    break;
                }
            }

            g.endShape();

            if (s == tKnotVector[points.length]) {
                break;
            }
        }
    }

    public PVector evaluate(float s, float t) {
        s = Math.max(sKnotVector[0], Math.min(sKnotVector[sKnotVector.length - 1], s));
        t = Math.max(tKnotVector[0], Math.min(tKnotVector[tKnotVector.length - 1], t));

        for (int sKnot = 1; sKnot < sKnotVector.length; sKnot++) {
            if (s <= sKnotVector[sKnot]) {
                for (int tKnot = 1; tKnot < tKnotVector.length; tKnot++) {
                    if (t <= tKnotVector[tKnot]) {
                        return evaluate(s, sKnot - 1, t, tKnot - 1);
                    }
                }
            }
        }

        throw new RuntimeException("This code should never execute.");
    }

    public PVector evaluate(float s, int sKnot, float t, int tKnot) {
        if (sKnot < 0 || sKnotVector.length <= sKnot + 1) {
            throw new IllegalArgumentException("sKnot out of bounds.");
        }
        if (s < sKnotVector[sKnot] || sKnotVector[sKnot + 1] < s) {
            throw new IllegalArgumentException("s should be between knot `sKnot` and knot `sKnot + 1`.");
        }
        if (tKnot < 0 || tKnotVector.length <= tKnot + 1) {
            throw new IllegalArgumentException("tKnot out of bounds.");
        }
        if (t < tKnotVector[tKnot] || tKnotVector[tKnot + 1] < t) {
            throw new IllegalArgumentException("t should be between knot `tKnot` and knot `tKnot + 1`.");
        }

        float[] sBasisFunctionValues = calcBasisFunctionValues(sKnot, s, sDegree, sKnotVector);
        float[] tBasisFunctionValues = calcBasisFunctionValues(tKnot, t, tDegree, tKnotVector);

        float[][] basisWeights = new float[sDegree + 1][tDegree + 1];
        float basisWeightsSum = 0;
        for (int sI = Math.max(sDegree - sKnot, 0); sI <= sDegree; sI++) {
            for (int tI = Math.max(tDegree - tKnot, 0); tI <= tDegree; tI++) {
                basisWeightsSum += basisWeights[sI][tI] = sBasisFunctionValues[sI] * tBasisFunctionValues[tI] * weights[sI + sKnot - sDegree][tI + tKnot - tDegree];
            }
        }

        PVector result = new PVector();
        for (int sI = Math.max(sDegree - sKnot, 0); sI <= sDegree; sI++) {
            for (int tI = Math.max(tDegree - tKnot, 0); tI <= tDegree; tI++) {
                result = PVector.add(result, PVector.mult(points[sI + sKnot - sDegree][tI + tKnot - tDegree], basisWeights[sI][tI] / basisWeightsSum));
            }
        }

        return result;
    }

}
