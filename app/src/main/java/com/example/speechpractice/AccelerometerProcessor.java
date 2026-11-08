package com.example.speechpractice;

import java.util.ArrayList;
import java.util.List;

public final class AccelerometerProcessor {
    public ProcessedSignalWindow process(List<AccelerometerSample> samples, Config config) {
        if (samples == null || samples.size() < 4 || config == null || !config.isValid()) {
            MeasurementStatus status = config == null || !config.isValid()
                    ? MeasurementStatus.FAILURE : MeasurementStatus.UNAVAILABLE;
            return ProcessedSignalWindow.unavailable(MeasurementSource.ACCELEROMETER,
                    MeasurementType.ACCELERATION_RMS, status,
                    "A valid configuration and at least four timestamped accelerometer samples are required.");
        }
        for (int i = 1; i < samples.size(); i++) {
            if (samples.get(i).timestampMonotonicNanos <= samples.get(i - 1).timestampMonotonicNanos) {
                return ProcessedSignalWindow.unavailable(MeasurementSource.ACCELEROMETER,
                        MeasurementType.ACCELERATION_RMS, MeasurementStatus.FAILURE,
                        "Sample timestamps must be strictly increasing.");
            }
        }

        double[] intervals = intervalsSeconds(samples);
        double medianInterval = median(intervals);
        if (!(medianInterval > 0.0)) {
            return ProcessedSignalWindow.unavailable(MeasurementSource.ACCELEROMETER,
                    MeasurementType.ACCELERATION_RMS, MeasurementStatus.FAILURE,
                    "Sampling interval could not be estimated.");
        }
        double estimatedRate = 1.0 / medianInterval;
        if (config.expectedSampleRateHz > 0.0
                && Math.abs(1.0 / medianInterval - config.expectedSampleRateHz)
                / config.expectedSampleRateHz > 0.20) {
            return ProcessedSignalWindow.unavailable(MeasurementSource.ACCELEROMETER,
                    MeasurementType.ACCELERATION_RMS, MeasurementStatus.FAILURE,
                    "Observed sample rate differs materially from the configured acquisition rate.");
        }
        if (config.highPassHz >= estimatedRate / 2.0) {
            return ProcessedSignalWindow.unavailable(MeasurementSource.ACCELEROMETER,
                    MeasurementType.ACCELERATION_RMS, MeasurementStatus.FAILURE,
                    "Configured high-pass cutoff is not below the observed Nyquist frequency.");
        }
        int missing = 0;
        int irregular = 0;
        for (double interval : intervals) {
            double ratio = interval / medianInterval;
            if (ratio > config.maxGapFactor) {
                missing += Math.max(1, (int) Math.round(ratio) - 1);
                irregular++;
            } else if (Math.abs(ratio - 1.0) > config.irregularToleranceFraction) {
                irregular++;
            }
        }
        double missingFraction = missing / (double) Math.max(samples.size() + missing, 1);
        double irregularFraction = irregular / (double) intervals.length;
        boolean saturated = false;
        double meanX = 0.0, meanY = 0.0, meanZ = 0.0;
        for (AccelerometerSample sample : samples) {
            saturated |= Math.abs(sample.xMetersPerSecondSquared) >= config.saturationLimit
                    || Math.abs(sample.yMetersPerSecondSquared) >= config.saturationLimit
                    || Math.abs(sample.zMetersPerSecondSquared) >= config.saturationLimit;
            meanX += sample.xMetersPerSecondSquared;
            meanY += sample.yMetersPerSecondSquared;
            meanZ += sample.zMetersPerSecondSquared;
        }
        meanX /= samples.size();
        meanY /= samples.size();
        meanZ /= samples.size();

        double[] magnitude = new double[samples.size()];
        double squareSum = 0.0;
        double peak = Double.NEGATIVE_INFINITY;
        double energy = 0.0;
        for (int i = 0; i < samples.size(); i++) {
            AccelerometerSample sample = samples.get(i);
            double dx = sample.xMetersPerSecondSquared - meanX;
            double dy = sample.yMetersPerSecondSquared - meanY;
            double dz = sample.zMetersPerSecondSquared - meanZ;
            magnitude[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        if (config.highPassHz > 0.0) {
            double previousInput = magnitude[0];
            double previousOutput = magnitude[0];
            for (int i = 1; i < magnitude.length; i++) {
                double dt = intervals[i - 1];
                double rc = 1.0 / (2.0 * Math.PI * config.highPassHz);
                double alpha = rc / (rc + dt);
                double filtered = alpha * (previousOutput + magnitude[i] - previousInput);
                previousInput = magnitude[i];
                previousOutput = filtered;
                magnitude[i] = filtered;
            }
        }
        for (int i = 0; i < samples.size(); i++) {
            squareSum += magnitude[i] * magnitude[i];
            peak = Math.max(peak, magnitude[i]);
            if (i > 0) energy += magnitude[i] * magnitude[i] * intervals[i - 1];
        }
        double rms = Math.sqrt(squareSum / magnitude.length);
        double peakToPeak = peak - min(magnitude);
        SignalQualityAssessment quality = SignalQualityAssessment.forSampleWindow(
                MeasurementSource.ACCELEROMETER, missingFraction, irregularFraction,
                saturated, false, false, rms <= config.noiseFloor);
        long start = samples.get(0).timestampMonotonicNanos;
        long end = samples.get(samples.size() - 1).timestampMonotonicNanos;
        long received = System.currentTimeMillis();
        List<MeasurementRecord> records = new ArrayList<>();
        String detail = "Neck-mounted mechanical vibration proxy after mean removal"
                + (config.highPassHz > 0.0 ? " and configured high-pass filtering" : "")
                + "; not a direct measure of vocal-fold motion, voice quality, or phonation success.";
        records.add(windowRecord(MeasurementType.ACCELERATION_RMS, rms, start, end, received, quality, detail));
        records.add(windowRecord(MeasurementType.ACCELERATION_PEAK_TO_PEAK, peakToPeak, start, end, received, quality, detail));
        records.add(windowRecord(MeasurementType.ACCELERATION_ENERGY, energy, start, end, received, quality,
                "Integrated squared mean-removed acceleration over this sample window; depends on window duration and mounting."));
        if (samples.size() >= 32 && (end - start) >= 500_000_000L && irregularFraction <= 0.10) {
            double dominant = dominantFrequency(magnitude, estimatedRate);
            if (Double.isFinite(dominant) && dominant > 0.0) {
                records.add(windowRecord(MeasurementType.ACCELERATION_DOMINANT_FREQUENCY,
                        dominant, start, end, received, quality,
                        "Largest non-DC DFT component within this window; only reported when duration and interval regularity support an estimate."));
            } else {
                records.add(MeasurementRecord.unavailable(MeasurementSource.ACCELEROMETER,
                        MeasurementType.ACCELERATION_DOMINANT_FREQUENCY, MeasurementStatus.POOR_SIGNAL,
                        "No supported non-DC frequency peak was found."));
            }
        } else {
            records.add(MeasurementRecord.unavailable(MeasurementSource.ACCELEROMETER,
                    MeasurementType.ACCELERATION_DOMINANT_FREQUENCY, MeasurementStatus.NOT_APPLICABLE,
                    "Window duration, sample count, or interval regularity is insufficient for a frequency estimate."));
        }
        return new ProcessedSignalWindow(MeasurementSource.ACCELEROMETER, MeasurementStatus.AVAILABLE,
                estimatedRate, samples.size(), missing, quality, records,
                "Sampling interval estimated from monotonic timestamps. " + quality.explanation);
    }

    private MeasurementRecord windowRecord(MeasurementType type, double value, long start, long end,
                                           long received, SignalQualityAssessment quality, String detail) {
        return MeasurementRecord.availableWindow(MeasurementSource.ACCELEROMETER, type, value,
                type.canonicalUnit, start, end, received, quality.quality, quality.confidence, detail);
    }

    private double dominantFrequency(double[] values, double sampleRateHz) {
        int n = values.length;
        double mean = 0.0;
        for (double value : values) mean += value;
        mean /= n;
        double bestPower = 0.0;
        int bestBin = 0;
        for (int bin = 1; bin < n / 2; bin++) {
            double real = 0.0, imaginary = 0.0;
            for (int i = 0; i < n; i++) {
                double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (n - 1));
                double angle = 2.0 * Math.PI * bin * i / n;
                double sample = (values[i] - mean) * window;
                real += sample * Math.cos(angle);
                imaginary -= sample * Math.sin(angle);
            }
            double power = real * real + imaginary * imaginary;
            if (power > bestPower) { bestPower = power; bestBin = bin; }
        }
        return bestBin == 0 ? Double.NaN : bestBin * sampleRateHz / n;
    }

    private double[] intervalsSeconds(List<AccelerometerSample> samples) {
        double[] result = new double[samples.size() - 1];
        for (int i = 1; i < samples.size(); i++) {
            result[i - 1] = (samples.get(i).timestampMonotonicNanos
                    - samples.get(i - 1).timestampMonotonicNanos) / 1_000_000_000.0;
        }
        return result;
    }

    private double median(double[] values) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0 : sorted[middle];
    }

    private double min(double[] values) {
        double result = Double.POSITIVE_INFINITY;
        for (double value : values) result = Math.min(result, value);
        return result;
    }

    public static final class Config {
        public final double expectedSampleRateHz;
        public final double highPassHz;
        public final double saturationLimit;
        public final double noiseFloor;
        public final double maxGapFactor;
        public final double irregularToleranceFraction;

        public Config(double expectedSampleRateHz, double highPassHz, double saturationLimit,
                      double noiseFloor, double maxGapFactor, double irregularToleranceFraction) {
            this.expectedSampleRateHz = expectedSampleRateHz;
            this.highPassHz = highPassHz;
            this.saturationLimit = saturationLimit;
            this.noiseFloor = noiseFloor;
            this.maxGapFactor = maxGapFactor;
            this.irregularToleranceFraction = irregularToleranceFraction;
        }

        boolean isValid() {
            return Double.isFinite(expectedSampleRateHz) && expectedSampleRateHz >= 0.0
                    && Double.isFinite(highPassHz) && highPassHz >= 0.0
                    && (expectedSampleRateHz == 0.0 || highPassHz < expectedSampleRateHz / 2.0)
                    && Double.isFinite(saturationLimit) && saturationLimit > 0.0
                    && Double.isFinite(noiseFloor) && noiseFloor >= 0.0
                    && Double.isFinite(maxGapFactor) && maxGapFactor > 1.0
                    && Double.isFinite(irregularToleranceFraction) && irregularToleranceFraction >= 0.0;
        }
    }
}
