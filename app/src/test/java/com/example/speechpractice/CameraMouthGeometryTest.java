package com.example.speechpractice;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CameraMouthGeometryTest {
    @Test
    public void derivesRelativeLipAndMouthRatiosFromContours() {
        CameraMouthGeometry geometry = CameraMouthGeometry.derive(
                points(40, 50, 50, 50, 60, 50),
                points(40, 60, 50, 60, 60, 60),
                points(30, 48, 70, 48, 30, 62, 70, 62),
                points(0, 0, 100, 0, 100, 100, 0, 100),
                points(45, 35, 55, 35));

        assertNotNull(geometry);
        assertEquals(0.75, geometry.ratios.get(MeasurementType.LIP_CLOSURE_RATIO), 0.0001);
        assertEquals(0.25, geometry.ratios.get(MeasurementType.LIP_SEPARATION_RATIO), 0.0001);
        assertEquals(0.4, geometry.ratios.get(MeasurementType.LIP_SPREAD_WIDTH_RATIO), 0.0001);
        assertEquals(0.35, geometry.ratios.get(MeasurementType.LIP_ROUNDING_PROXY), 0.0001);
        assertEquals(0.1, geometry.ratios.get(MeasurementType.MOUTH_OPENING_RATIO), 0.0001);
        assertEquals(0.2, geometry.mouthCenterYFromNoseRatio, 0.0001);
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(
                        MeasurementType.LIP_CLOSURE_RATIO,
                        MeasurementType.LIP_SEPARATION_RATIO,
                        MeasurementType.LIP_SPREAD_WIDTH_RATIO,
                        MeasurementType.LIP_ROUNDING_PROXY,
                        MeasurementType.MOUTH_OPENING_RATIO)),
                geometry.ratios.keySet());
    }

    @Test
    public void rejectsMissingNonFiniteOrDegenerateContours() {
        double[][] validFace = points(0, 0, 100, 0, 100, 100, 0, 100);
        assertNull(CameraMouthGeometry.derive(null, points(1, 1, 2, 2),
                points(0, 0, 10, 10), validFace, points(1, 1, 2, 2)));
        assertNull(CameraMouthGeometry.derive(points(1, 1, Double.NaN, 2), points(2, 3),
                points(0, 0, 10, 10), validFace, points(1, 1, 2, 2)));
        assertNull(CameraMouthGeometry.derive(points(1, 1, 2, 2), points(2, 3),
                points(0, 0, 10, 10), points(1, 1, 1, 1), points(1, 1, 2, 2)));
    }

    private static double[][] points(double... coordinates) {
        double[][] result = new double[coordinates.length / 2][2];
        for (int i = 0; i < coordinates.length; i += 2) {
            result[i / 2][0] = coordinates[i];
            result[i / 2][1] = coordinates[i + 1];
        }
        return result;
    }
}
