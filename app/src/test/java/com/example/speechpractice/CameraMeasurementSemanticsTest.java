package com.example.speechpractice;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CameraMeasurementSemanticsTest {
    @Test
    public void onlyExplicitlyNamedGeometricProxiesAreSupported() {
        for (MeasurementType type : CameraMeasurementSemantics.supportedTypes()) {
            assertTrue(CameraMeasurementSemantics.isSupported(type));
        }
        assertFalse(CameraMeasurementSemantics.isSupported(MeasurementType.RESTING_LIP_POSITION));
        assertFalse(CameraMeasurementSemantics.isSupported(MeasurementType.LIP_JAW_TIMING));
        assertFalse(CameraMeasurementSemantics.isSupported(MeasurementType.LIP_MOVEMENT_TRANSITION));
        assertFalse(CameraMeasurementSemantics.isSupported(MeasurementType.VISIBLE_LIP_TEETH_POSITION));
        assertFalse(CameraMeasurementSemantics.isSupported(MeasurementType.JAW_MOVEMENT));
    }

    @Test
    public void unsupportedMeasurementsAreRepresentedWithoutFabricatedValues() {
        MeasurementRecord measurement = MeasurementRecord.unavailable(
                MeasurementSource.CAMERA, MeasurementType.VISIBLE_LIP_TEETH_POSITION,
                MeasurementStatus.UNSUPPORTED,
                CameraMeasurementSemantics.unsupportedDetail(MeasurementType.VISIBLE_LIP_TEETH_POSITION));

        assertTrue(measurement.detail.contains("cannot establish lip-to-teeth contact"));
        assertNull(measurement.value);
        assertNull(measurement.unit);
        assertNull(measurement.acquiredAtMonotonicNanos);
        assertNull(measurement.receivedAtWallClockMs);
    }

    @Test
    public void oldMovementAndChangeLabelsAreExplicitlyUnsupported() {
        assertTrue(CameraMeasurementSemantics.unsupportedDetail(MeasurementType.LIP_MOVEMENT_TRANSITION)
                .contains("temporal movement"));
        assertTrue(CameraMeasurementSemantics.unsupportedDetail(MeasurementType.JAW_MOVEMENT)
                .contains("cannot isolate jaw movement"));
        assertTrue(CameraMeasurementSemantics.unsupportedDetail(MeasurementType.LIP_JAW_TIMING)
                .contains("does not calculate"));
    }
}
