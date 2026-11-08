package com.example.speechpractice;

import java.util.ArrayList;
import java.util.List;

public final class SemgProcessor {
    public ProcessedSignalWindow process(List<SemgSample> samples, Config config) {
        if (samples == null || samples.size() < 4 || config == null || !config.isValid()) {
            MeasurementStatus status = config == null || !config.isValid()
                    ? MeasurementStatus.FAILURE : MeasurementStatus.UNAVAILABLE;
            return ProcessedSignalWindow.unavailable(MeasurementSource.SEMG,
                    MeasurementType.SEMG_ENVELOPE_RMS, status,
                    "sEMG processing requires configured front-end scaling and filter settings plus at least four timestamped samples.");
        }
        for (int i = 1; i < samples.size(); i++) {
            if (samples.get(i).timestampMonotonicNanos <= samples.get(i - 1).timestampMonotonicNanos) {
                return ProcessedSignalWindow.unavailable(MeasurementSource.SEMG,
                        MeasurementType.SEMG_ENVELOPE_RMS, MeasurementStatus.FAILURE,
                        "sEMG sample timestamps must be strictly increasing.");
            }
        }
        double[] intervals = intervalsSeconds(samples);
        double medianInterval = median(intervals);
        double rate = 1.0 / medianInterval;
        if (Math.abs(rate - config.samplingRateHz) / config.samplingRateHz > config.rateToleranceFraction) {
            return ProcessedSignalWindow.unavailable(MeasurementSource.SEMG,
                    MeasurementType.SEMG_ENVELOPE_RMS, MeasurementStatus.FAILURE,
                    "Observed sEMG sample rate is incompatible with the configured acquisition rate.");
        }
        if (config.highPassHz >= rate / 2.0 || config.lowPassHz >= rate / 2.0) {
            return ProcessedSignalWindow.unavailable(MeasurementSource.SEMG,
                    MeasurementType.SEMG_ENVELOPE_RMS, MeasurementStatus.FAILURE,
                    "Configured filter cutoffs are not below the observed Nyquist frequency.");
        }
        boolean saturated = false;
        for (SemgSample sample : samples) {
            saturated |= config.saturationLimitInputUnits > 0.0
                    && Math.abs(sample.inputUnits) >= config.saturationLimitInputUnits;
        }
        int baselineCount = Math.max(1, samples.size() / 10);
        double baseline = 0.0;
        for (int i = 0; i < baselineCount; i++) baseline += samples.get(i).inputUnits;
        baseline = baseline / baselineCount * config.microvoltsPerInputUnit;
        double baselineVariance = 0.0;
        for (int i = 0; i < baselineCount; i++) {
            double difference = (samples.get(i).inputUnits * config.microvoltsPerInputUnit) - baseline;
            baselineVariance += difference * difference;
        }
        double baselineRms = Math.sqrt(baselineVariance / baselineCount);
        boolean unstableBaseline = baselineRms > config.maximumBaselineVariationMicrovolts;
        double[] filtered = new double[samples.size()];
        double previousInput = 0.0, previousHigh = 0.0, previousLow = 0.0;
        for (int i = 0; i < samples.size(); i++) {
            double input = (samples.get(i).inputUnits * config.microvoltsPerInputUnit) - baseline;
            double high = input;
            if (config.highPassHz > 0.0 && i > 0) {
                double rc = 1.0 / (2.0 * Math.PI * config.highPassHz);
                double alpha = rc / (rc + intervals[i - 1]);
                high = alpha * (previousHigh + input - previousInput);
            }
            double low = high;
            if (i > 0) {
                double rc = 1.0 / (2.0 * Math.PI * config.lowPassHz);
                double alpha = intervals[i - 1] / (rc + intervals[i - 1]);
                low = previousLow + alpha * (high - previousLow);
            }
            filtered[i] = Math.abs(low);
            previousInput = input;
            previousHigh = high;
            previousLow = low;
        }
        int envelopeWindow = Math.max(1, (int) Math.round(config.envelopeWindowMs * rate / 1000.0));
        double envelopeSquares = 0.0;
        for (int i = 0; i < filtered.length; i++) {
            int start = Math.max(0, i - envelopeWindow + 1);
            double sum = 0.0;
            for (int j = start; j <= i; j++) sum += filtered[j] * filtered[j];
            double envelope = Math.sqrt(sum / (i - start + 1));
            envelopeSquares += envelope * envelope;
        }
        double envelopeRms = Math.sqrt(envelopeSquares / filtered.length);
        int missingSamples = estimateMissing(intervals, medianInterval, config.maxGapFactor);
        double missingFraction = missingSamples / (double) Math.max(samples.size() + missingSamples, 1);
        double irregular = irregularFraction(intervals, medianInterval, config.irregularToleranceFraction);
        SignalQualityAssessment quality = SignalQualityAssessment.forSampleWindow(MeasurementSource.SEMG,
                missingFraction, irregular, saturated, unstableBaseline, false, false);
        long startNanos = samples.get(0).timestampMonotonicNanos;
        long endNanos = samples.get(samples.size() - 1).timestampMonotonicNanos;
        long received = System.currentTimeMillis();
        List<MeasurementRecord> records = new ArrayList<>();
        records.add(MeasurementRecord.availableWindow(MeasurementSource.SEMG,
                MeasurementType.SEMG_ENVELOPE_RMS, envelopeRms, "uV",
                startNanos, endNanos, received, quality.quality, quality.confidence,
                "Configured front-end scale, configured filtering, full-wave rectification, and moving RMS envelope; electrical surface activity only, not muscle strength or intrinsic vocal-fold activity."));
        records.add(MeasurementRecord.availableWindow(MeasurementSource.SEMG,
                MeasurementType.SEMG_DC_OFFSET, baseline, "uV",
                startNanos, endNanos, received, quality.quality, quality.confidence,
                "Mean configured input offset before baseline subtraction; depends on the configured analog front-end and is not a physiological baseline."));
        return new ProcessedSignalWindow(MeasurementSource.SEMG, MeasurementStatus.AVAILABLE,
                rate, samples.size(), missingSamples, quality,
                records, "sEMG values are produced only when explicitly configured timestamped samples are supplied. " + quality.explanation);
    }

    private int estimateMissing(double[] intervals, double medianInterval, double gapFactor) {
        int result = 0;
        for (double interval : intervals) {
            if (interval / medianInterval > gapFactor) result += Math.max(1, (int) Math.round(interval / medianInterval) - 1);
        }
        return result;
    }

    private double irregularFraction(double[] intervals, double medianInterval, double tolerance) {
        int irregular = 0;
        for (double interval : intervals) {
            if (Math.abs(interval / medianInterval - 1.0) > tolerance) irregular++;
        }
        return irregular / (double) intervals.length;
    }

    private double[] intervalsSeconds(List<SemgSample> samples) {
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

    public static final class Config {
        public final double samplingRateHz;
        public final double microvoltsPerInputUnit;
        public final double highPassHz;
        public final double lowPassHz;
        public final double envelopeWindowMs;
        public final double saturationLimitInputUnits;
        public final double maximumBaselineVariationMicrovolts;
        public final double maxGapFactor;
        public final double irregularToleranceFraction;
        public final double rateToleranceFraction;
        public final String inputUnit;

        public Config(double samplingRateHz, double microvoltsPerInputUnit, double highPassHz,
                      double lowPassHz, double envelopeWindowMs, double saturationLimitInputUnits,
                      double maximumBaselineVariationMicrovolts, double maxGapFactor,
                      double irregularToleranceFraction, double rateToleranceFraction) {
            this(samplingRateHz, microvoltsPerInputUnit, highPassHz, lowPassHz,
                    envelopeWindowMs, saturationLimitInputUnits,
                    maximumBaselineVariationMicrovolts, maxGapFactor,
                    irregularToleranceFraction, rateToleranceFraction, "");
        }

        public Config(double samplingRateHz, double microvoltsPerInputUnit, double highPassHz,
                      double lowPassHz, double envelopeWindowMs, double saturationLimitInputUnits,
                      double maximumBaselineVariationMicrovolts, double maxGapFactor,
                      double irregularToleranceFraction, double rateToleranceFraction,
                      String inputUnit) {
            this.samplingRateHz = samplingRateHz;
            this.microvoltsPerInputUnit = microvoltsPerInputUnit;
            this.highPassHz = highPassHz;
            this.lowPassHz = lowPassHz;
            this.envelopeWindowMs = envelopeWindowMs;
            this.saturationLimitInputUnits = saturationLimitInputUnits;
            this.maximumBaselineVariationMicrovolts = maximumBaselineVariationMicrovolts;
            this.maxGapFactor = maxGapFactor;
            this.irregularToleranceFraction = irregularToleranceFraction;
            this.rateToleranceFraction = rateToleranceFraction;
            this.inputUnit = inputUnit == null ? "" : inputUnit;
        }

        private boolean isValid() {
            return Double.isFinite(samplingRateHz) && samplingRateHz > 0.0
                    && Double.isFinite(microvoltsPerInputUnit) && microvoltsPerInputUnit > 0.0
                    && Double.isFinite(highPassHz) && highPassHz >= 0.0 && highPassHz < samplingRateHz / 2.0
                    && Double.isFinite(lowPassHz) && lowPassHz > highPassHz && lowPassHz < samplingRateHz / 2.0
                    && Double.isFinite(envelopeWindowMs) && envelopeWindowMs > 0.0
                    && Double.isFinite(saturationLimitInputUnits) && saturationLimitInputUnits >= 0.0
                    && Double.isFinite(maximumBaselineVariationMicrovolts) && maximumBaselineVariationMicrovolts >= 0.0
                    && Double.isFinite(maxGapFactor) && maxGapFactor > 1.0
                    && Double.isFinite(irregularToleranceFraction) && irregularToleranceFraction >= 0.0
                    && Double.isFinite(rateToleranceFraction) && rateToleranceFraction >= 0.0;
        }
    }
}
