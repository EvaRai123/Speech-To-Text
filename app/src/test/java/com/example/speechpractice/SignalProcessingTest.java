package com.example.speechpractice;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SignalProcessingTest {
    @Test
    public void accelerometerAnalyzesWindowAndReportsFrequencyOnlyWithSufficientData() {
        List<AccelerometerSample> samples = accelerometerSamples(100, false);
        AccelerometerProcessor.Config config = new AccelerometerProcessor.Config(
                100.0, 0.0, 30.0, 0.01, 1.8, 0.10);

        ProcessedSignalWindow result = new AccelerometerProcessor().process(samples, config);

        assertEquals(MeasurementStatus.AVAILABLE, result.status);
        assertEquals(100.0, result.estimatedSamplingRateHz, 0.01);
        assertTrue(result.measurements.size() >= 4);
        assertTrue(valueFor(result, MeasurementType.ACCELERATION_RMS) > 0.1);
        assertTrue(valueFor(result, MeasurementType.ACCELERATION_ENERGY) > 0.0);
        assertTrue(valueFor(result, MeasurementType.ACCELERATION_DOMINANT_FREQUENCY) > 0.0);
        assertNotNull(result.measurements.get(0).windowStartMonotonicNanos);
        assertNotNull(result.measurements.get(0).windowEndMonotonicNanos);
    }

    @Test
    public void accelerometerDetectsMissingSamplesAndRejectsOutOfOrderTimestamps() {
        List<AccelerometerSample> samples = accelerometerSamples(100, true);
        AccelerometerProcessor.Config config = new AccelerometerProcessor.Config(
                100.0, 4.0, 30.0, 0.01, 1.5, 0.10);
        ProcessedSignalWindow result = new AccelerometerProcessor().process(samples, config);

        assertTrue(result.estimatedMissingSamples > 0);
        assertTrue(result.quality.flags.contains("MISSING_SAMPLES"));

        List<AccelerometerSample> unordered = new ArrayList<>(samples);
        unordered.set(2, new AccelerometerSample(unordered.get(1).timestampMonotonicNanos - 1L, 0.0, 0.0, 0.0));
        assertEquals(MeasurementStatus.FAILURE,
                new AccelerometerProcessor().process(unordered, config).status);
    }

    @Test
    public void semgUsesExplicitConfigurationAndReturnsUnavailableForInvalidRate() {
        List<SemgSample> samples = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            double sample = Math.sin(2.0 * Math.PI * 50.0 * i / 1000.0) * 1000.0 + 100.0;
            samples.add(new SemgSample(3_000_000_000L + i * 1_000_000L, sample));
        }
        SemgProcessor.Config config = new SemgProcessor.Config(
                1000.0, 0.1, 20.0, 400.0, 50.0, 32767.0, 10.0, 1.8, 0.1, 0.05);

        ProcessedSignalWindow result = new SemgProcessor().process(samples, config);
        assertEquals(MeasurementStatus.AVAILABLE, result.status);
        assertTrue(valueFor(result, MeasurementType.SEMG_ENVELOPE_RMS) > 0.0);
        assertTrue(result.measurements.get(0).detail.contains("not muscle strength"));
        List<SemgSample> missingSample = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            if (i != 500) missingSample.add(samples.get(i));
        }
        ProcessedSignalWindow discontinuous = new SemgProcessor().process(missingSample, config);
        assertTrue(discontinuous.estimatedMissingSamples > 0);
        assertTrue(discontinuous.quality.flags.contains("MISSING_SAMPLES"));
        assertEquals(MeasurementStatus.FAILURE, new SemgProcessor().process(samples,
                new SemgProcessor.Config(500.0, 0.1, 20.0, 200.0, 50.0,
                        32767.0, 10.0, 1.8, 0.1, 0.05)).status);
    }

    @Test
    public void sampleQualityIncludesSourceSpecificFlagsAndBoundedReliability() {
        SignalQualityAssessment quality = SignalQualityAssessment.forSampleWindow(
                MeasurementSource.ACCELEROMETER, 0.1, 0.2, true, false, false, false);
        assertEquals(MeasurementQuality.POOR, quality.quality);
        assertTrue(quality.flags.contains("SATURATION"));
        assertTrue(quality.confidence >= 0.0 && quality.confidence <= 1.0);
        assertTrue(quality.explanation.contains("not clinical accuracy"));
    }

    private List<AccelerometerSample> accelerometerSamples(int sampleCount, boolean omitOne) {
        List<AccelerometerSample> samples = new ArrayList<>();
        for (int i = 0; i < sampleCount; i++) {
            if (omitOne && i == 30) continue;
            double vibration = 0.4 * Math.sin(2.0 * Math.PI * 20.0 * i / 100.0);
            samples.add(new AccelerometerSample(1_000_000_000L + i * 10_000_000L,
                    9.81 + vibration, 0.0, 0.0));
        }
        return samples;
    }

    private double valueFor(ProcessedSignalWindow result, MeasurementType type) {
        for (MeasurementRecord record : result.measurements) {
            if (record.type == type && record.value != null) return record.value;
        }
        return Double.NaN;
    }
}
