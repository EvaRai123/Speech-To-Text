package com.example.speechpractice;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CameraMeasurementBufferTest {
    @Test
    public void repeatedDrainsCannotPersistTheSameLatestMeasurementTwice() {
        CameraMeasurementBuffer buffer = new CameraMeasurementBuffer();
        MeasurementRecord measurement = MeasurementRecord.unavailable(
                MeasurementSource.CAMERA, MeasurementType.VISIBLE_LIP_TEETH_POSITION,
                MeasurementStatus.UNSUPPORTED, "Not measured.");
        buffer.put(measurement);

        List<MeasurementRecord> firstDrain = buffer.drain();
        List<MeasurementRecord> secondDrain = buffer.drain();

        assertEquals(1, firstDrain.size());
        assertEquals(measurement, firstDrain.get(0));
        assertTrue(secondDrain.isEmpty());
        assertFalse(buffer.hasMeasurements());
    }

    @Test
    public void aNewerRecordForTheSameTypeReplacesTheBufferedObservation() {
        CameraMeasurementBuffer buffer = new CameraMeasurementBuffer();
        MeasurementRecord unavailable = MeasurementRecord.unavailable(
                MeasurementSource.CAMERA, MeasurementType.LIP_CLOSURE_RATIO,
                MeasurementStatus.POOR_SIGNAL, "First frame unusable.");
        MeasurementRecord available = MeasurementRecord.available(
                MeasurementSource.CAMERA, MeasurementType.LIP_CLOSURE_RATIO,
                0.8, "ratio", 10L, 20L, MeasurementQuality.LIMITED, null, "Observed.");
        buffer.put(unavailable);
        buffer.put(available);

        assertEquals(1, buffer.size());
        assertEquals(available, buffer.drain().get(0));
    }
}
