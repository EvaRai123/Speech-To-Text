package com.example.speechpractice;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CameraMeasurementGateTest {
    @Test
    public void stopInvalidatesPendingFramesAndRepeatedStopsStayClosed() {
        CameraMeasurementGate gate = new CameraMeasurementGate();
        long session = gate.start();
        AtomicInteger emittedMeasurements = new AtomicInteger();

        assertTrue(gate.runIfActive(session, emittedMeasurements::incrementAndGet));
        gate.stop();
        gate.stop();

        assertFalse(gate.isActive(session));
        assertFalse(gate.runIfActive(session, emittedMeasurements::incrementAndGet));
        assertEquals(1, emittedMeasurements.get());
    }

    @Test
    public void restartedCameraDoesNotAcceptCallbacksFromPreviousSession() {
        CameraMeasurementGate gate = new CameraMeasurementGate();
        long firstSession = gate.start();
        gate.stop();
        long secondSession = gate.start();
        AtomicInteger emittedMeasurements = new AtomicInteger();

        assertFalse(gate.runIfActive(firstSession, emittedMeasurements::incrementAndGet));
        assertTrue(gate.runIfActive(secondSession, emittedMeasurements::incrementAndGet));
        assertEquals(1, emittedMeasurements.get());
    }
}
