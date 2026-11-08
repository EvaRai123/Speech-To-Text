package com.example.speechpractice;

import java.util.EnumMap;
import java.util.Map;

public final class CameraMouthGeometry {
    public final Map<MeasurementType, Double> ratios;
    public final double mouthCenterYFromNoseRatio;

    private CameraMouthGeometry(Map<MeasurementType, Double> ratios, double mouthCenterYFromNoseRatio) {
        this.ratios = ratios;
        this.mouthCenterYFromNoseRatio = mouthCenterYFromNoseRatio;
    }

    public static CameraMouthGeometry derive(double[][] upperLipInner, double[][] lowerLipInner,
                                             double[][] allLipContours, double[][] faceContour,
                                             double[][] noseBase) {
        Bounds face = bounds(faceContour);
        Bounds lips = bounds(allLipContours);
        if (face == null || lips == null || bounds(upperLipInner) == null
                || bounds(lowerLipInner) == null || bounds(noseBase) == null
                || face.width() <= 0.0 || face.height() <= 0.0) return null;

        double gap = Math.abs(meanY(upperLipInner) - meanY(lowerLipInner));
        double faceWidth = face.width();
        double mouthWidth = lips.width();
        if (mouthWidth <= 0.0 || mouthWidth > faceWidth) return null;

        double separation = clamp(gap / mouthWidth, 0.0, 1.0);
        double spread = clamp(mouthWidth / faceWidth, 0.0, 1.0);
        double rounding = clamp((lips.bottom - lips.top) / mouthWidth, 0.0, 1.0);
        double mouthOpening = clamp(gap / face.height(), 0.0, 1.0);
        Map<MeasurementType, Double> ratios = new EnumMap<>(MeasurementType.class);
        ratios.put(MeasurementType.LIP_CLOSURE_RATIO, 1.0 - separation);
        ratios.put(MeasurementType.LIP_SEPARATION_RATIO, separation);
        ratios.put(MeasurementType.LIP_SPREAD_WIDTH_RATIO, spread);
        ratios.put(MeasurementType.LIP_ROUNDING_PROXY, rounding);
        ratios.put(MeasurementType.MOUTH_OPENING_RATIO, mouthOpening);

        double mouthCenterY = (lips.top + lips.bottom) / 2.0;
        double noseY = meanY(noseBase);
        double relativeMouthNose = Math.abs(mouthCenterY - noseY) / face.height();
        return new CameraMouthGeometry(ratios, clamp(relativeMouthNose, 0.0, 1.0));
    }

    private static Bounds bounds(double[][] points) {
        if (points == null || points.length < 2) return null;
        double left = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (double[] point : points) {
            if (point == null || point.length < 2 || !Double.isFinite(point[0]) || !Double.isFinite(point[1])) {
                return null;
            }
            left = Math.min(left, point[0]);
            right = Math.max(right, point[0]);
            top = Math.min(top, point[1]);
            bottom = Math.max(bottom, point[1]);
        }
        return new Bounds(left, right, top, bottom);
    }

    private static double meanY(double[][] points) {
        double sum = 0.0;
        for (double[] point : points) sum += point[1];
        return sum / points.length;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Bounds {
        final double left;
        final double right;
        final double top;
        final double bottom;

        Bounds(double left, double right, double top, double bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }

        double width() { return right - left; }
        double height() { return bottom - top; }
    }
}
