package com.example.speechpractice;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MeasurementWindowContractTest {
    @Test
    public void audioMayHaveReceiptTimeWithoutInventedAcquisitionTime() {
        MeasurementRecord record = MeasurementRecord.available(MeasurementSource.AUDIO,
                MeasurementType.FUNDAMENTAL_FREQUENCY, 220.0, "Hz", null, 500L,
                MeasurementQuality.GOOD, 0.7, "Acquisition clock is unavailable.");
        assertNull(record.acquiredAtMonotonicNanos);
        assertEquals(Long.valueOf(500L), record.receivedAtWallClockMs);
    }

    @Test
    public void windowMeasurementsKeepAcquisitionBoundsSeparateFromReceiptTime() {
        MeasurementRecord record = MeasurementRecord.availableWindow(MeasurementSource.SEMG,
                MeasurementType.SEMG_ENVELOPE_RMS, 2.0, "uV",
                100L, 200L, 900L, MeasurementQuality.LIMITED, 0.6, "Configured window.");
        assertNull(record.acquiredAtMonotonicNanos);
        assertEquals(Long.valueOf(100L), record.windowStartMonotonicNanos);
        assertEquals(Long.valueOf(200L), record.windowEndMonotonicNanos);
        assertEquals(Long.valueOf(900L), record.receivedAtWallClockMs);
    }

    @Test(expected = IllegalArgumentException.class)
    public void windowStartCannotFollowWindowEnd() {
        MeasurementRecord.availableWindow(MeasurementSource.ACCELEROMETER,
                MeasurementType.ACCELERATION_RMS, 1.0, "m/s^2",
                300L, 200L, 400L, MeasurementQuality.GOOD, null, "Invalid window.");
    }

    @Test
    public void measurementUnitsAndSourcesRemainTypeBound() {
        assertTrue(MeasurementType.ACCELERATION_RMS.accepts(
                MeasurementSource.ACCELEROMETER, "m/s^2", 0.2));
        assertTrue(MeasurementType.SEMG_ENVELOPE_RMS.accepts(
                MeasurementSource.SEMG, "uV", 4.0));
        assertTrue(!MeasurementType.SEMG_ENVELOPE_RMS.accepts(
                MeasurementSource.ACCELEROMETER, "uV", 4.0));
        assertTrue(!MeasurementType.ACCELERATION_RMS.accepts(
                MeasurementSource.ACCELEROMETER, "Hz", 4.0));
        assertTrue(!MeasurementType.ACCELERATION_DOMINANT_FREQUENCY.accepts(
                MeasurementSource.ACCELEROMETER, "Hz", 0.0));
    }

    @Test
    public void unavailableRemainsNullAndIsNotEquivalentToMeasuredZero() {
        MeasurementRecord unavailable = MeasurementRecord.unavailable(
                MeasurementSource.ACCELEROMETER, MeasurementType.ACCELERATION_RMS,
                MeasurementStatus.UNAVAILABLE, "No hardware data.");
        MeasurementRecord zero = MeasurementRecord.availableWindow(
                MeasurementSource.ACCELEROMETER, MeasurementType.ACCELERATION_RMS, 0.0,
                "m/s^2", 1L, 2L, 3L, MeasurementQuality.GOOD, 1.0, "Genuine zero-valued test fixture.");
        assertNull(unavailable.value);
        assertNull(unavailable.unit);
        assertEquals(Double.valueOf(0.0), zero.value);
        assertEquals(MeasurementStatus.UNAVAILABLE, unavailable.status);
        assertEquals(MeasurementStatus.AVAILABLE, zero.status);
    }
}
