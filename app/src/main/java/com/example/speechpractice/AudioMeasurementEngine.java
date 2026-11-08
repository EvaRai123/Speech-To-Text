package com.example.speechpractice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure PCM16 analysis. It does not amplify, store, or upload audio. */
public final class AudioMeasurementEngine {
    private static final double MIN_F0_HZ = 70.0;
    private static final double MAX_F0_HZ = 400.0;
    private static final double EPSILON = 1.0e-12;

    public AudioMeasurementResult analyze(short[] samples, int sampleRateHz, short[] quietCalibration) {
        if (samples == null || samples.length == 0 || sampleRateHz <= 0) {
            return invalidResult(sampleRateHz);
        }
        double noiseDbfs = rmsDbfs(quietCalibration == null ? new short[0] : quietCalibration);
        if (quietCalibration == null || quietCalibration.length == 0) noiseDbfs = -60.0;
        double rmsDbfs = rmsDbfs(samples);
        double peakDbfs = peakDbfs(samples);
        boolean clipping = hasClipping(samples);
        double snr = rmsDbfs - noiseDbfs;
        List<String> flags = new ArrayList<>();
        if (clipping) flags.add("CLIPPING");
        if (snr < 6.0) flags.add("LOW_SNR");
        if (rmsDbfs < -55.0) flags.add("VERY_QUIET");
        SpeechDetection detection = detectSpeech(samples, sampleRateHz, noiseDbfs);
        if (detection.intervals.isEmpty()) flags.add("NO_SPEECH_ACTIVITY_DETECTED");
        PitchSummary pitch = estimatePitch(samples, sampleRateHz, detection.frameSpeech);
        if (pitch.validCount == 0) flags.add("PITCH_UNAVAILABLE");
        String quality = flags.contains("CLIPPING") || flags.contains("LOW_SNR")
                || flags.contains("VERY_QUIET") || flags.contains("NO_SPEECH_ACTIVITY_DETECTED")
                ? "LIMITED" : "USABLE";
        return new AudioMeasurementResult(System.currentTimeMillis(), sampleRateHz, rmsDbfs, peakDbfs,
                noiseDbfs, snr, clipping, detection.speechDurationMs,
                pitch.validCount * (sampleRateHz / 100.0), detection.pauseCount,
                detection.pauseDurationMs, pitch.median, pitch.min, pitch.max, pitch.variability,
                pitch.confidence, quality, flags, detection.intervals);
    }

    private AudioMeasurementResult invalidResult(int sampleRateHz) {
        return new AudioMeasurementResult(System.currentTimeMillis(), sampleRateHz, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, false, 0, 0, 0, 0, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, 0, "INVALID", Collections.singletonList("EMPTY_OR_INVALID_INPUT"), Collections.emptyList());
    }

    private SpeechDetection detectSpeech(short[] samples, int sampleRateHz, double noiseDbfs) {
        int frame = Math.max(1, sampleRateHz / 50); // 20 ms
        int hop = Math.max(1, sampleRateHz / 100); // 10 ms
        double onset = Math.max(-60.0, noiseDbfs + 10.0);
        double offset = Math.max(-60.0, noiseDbfs + 6.0);
        boolean[] speech = new boolean[Math.max(1, (samples.length - frame) / hop + 1)];
        for (int i = 0; i < speech.length; i++) {
            int start = i * hop;
            speech[i] = rmsDbfs(samples, start, Math.min(frame, samples.length - start)) >= onset;
        }
        List<AudioMeasurementResult.SpeechInterval> intervals = new ArrayList<>();
        boolean active = false;
        int startFrame = 0;
        int quietFrames = 0;
        for (int i = 0; i < speech.length; i++) {
            if (!active && speech[i]) {
                int next = Math.min(speech.length, i + 3);
                int activeFrames = 0;
                for (int j = i; j < next; j++) if (speech[j]) activeFrames++;
                if (activeFrames >= 2) { active = true; startFrame = i; }
            } else if (active) {
                double level = frameLevelDbfs(samples, i, hop, frame);
                if (!speech[i] && level < offset) quietFrames++; else quietFrames = 0;
                if (quietFrames >= 5) {
                    int endFrame = i - quietFrames + 1;
                    intervals.add(new AudioMeasurementResult.SpeechInterval(startFrame * hop * 1000.0 / sampleRateHz,
                            Math.min(samples.length, (endFrame + 1) * hop + frame) * 1000.0 / sampleRateHz));
                    active = false;
                    quietFrames = 0;
                }
            }
        }
        if (active) intervals.add(new AudioMeasurementResult.SpeechInterval(startFrame * hop * 1000.0 / sampleRateHz, samples.length * 1000.0 / sampleRateHz));
        double speechDuration = 0;
        for (AudioMeasurementResult.SpeechInterval interval : intervals) speechDuration += interval.endMs - interval.startMs;
        double pauseDuration = 0;
        for (int i = 1; i < intervals.size(); i++) pauseDuration += intervals.get(i).startMs - intervals.get(i - 1).endMs;
        return new SpeechDetection(intervals, speech, speechDuration, Math.max(0, intervals.size() - 1), pauseDuration);
    }

    private double frameLevelDbfs(short[] samples, int frameIndex, int hop, int frame) {
        int start = frameIndex * hop;
        return rmsDbfs(samples, start, Math.min(frame, samples.length - start));
    }

    private PitchSummary estimatePitch(short[] samples, int sampleRateHz, boolean[] speechFrames) {
        int frame = Math.max(1, sampleRateHz / 50);
        int hop = Math.max(1, sampleRateHz / 100);
        List<Double> values = new ArrayList<>();
        double confidenceSum = 0;
        for (int i = 0; i < speechFrames.length; i++) {
            if (!speechFrames[i]) continue;
            int start = i * hop;
            if (start + frame > samples.length) continue;
            PitchEstimate estimate = estimateFramePitch(samples, start, frame, sampleRateHz);
            if (estimate.frequencyHz > 0 && estimate.confidence >= 0.30) {
                values.add(estimate.frequencyHz);
                confidenceSum += estimate.confidence;
            }
        }
        if (values.isEmpty()) return new PitchSummary(0, Double.NaN, Double.NaN, Double.NaN, 0);
        Collections.sort(values);
        double median = values.get(values.size() / 2);
        double mean = 0;
        for (double value : values) mean += value;
        mean /= values.size();
        double variance = 0;
        for (double value : values) variance += (value - mean) * (value - mean);
        variance = Math.sqrt(variance / values.size());
        return new PitchSummary(values.size(), median, values.get(0), values.get(values.size() - 1), variance, confidenceSum / values.size());
    }

    private PitchEstimate estimateFramePitch(short[] samples, int start, int length, int sampleRateHz) {
        int minLag = (int) (sampleRateHz / MAX_F0_HZ);
        int maxLag = Math.min(length - 2, (int) (sampleRateHz / MIN_F0_HZ));
        double best = 0;
        int bestLag = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double correlation = 0;
            double left = 0;
            double right = 0;
            for (int i = 0; i < length - lag; i++) {
                double a = samples[start + i] / 32768.0;
                double b = samples[start + i + lag] / 32768.0;
                correlation += a * b;
                left += a * a;
                right += b * b;
            }
            double normalized = correlation / Math.sqrt(left * right + EPSILON);
            if (normalized > best) { best = normalized; bestLag = lag; }
        }
        return new PitchEstimate(bestLag == 0 ? 0 : sampleRateHz / (double) bestLag, best);
    }

    static double rmsDbfs(short[] samples) { return rmsDbfs(samples, 0, samples == null ? 0 : samples.length); }

    private static double rmsDbfs(short[] samples, int start, int length) {
        if (samples == null || length <= 0) return -120.0;
        double sum = 0;
        int end = Math.min(samples.length, start + length);
        for (int i = Math.max(0, start); i < end; i++) {
            double normalized = samples[i] / 32768.0;
            sum += normalized * normalized;
        }
        return 20.0 * Math.log10(Math.sqrt(sum / Math.max(1, end - Math.max(0, start))) + EPSILON);
    }

    private static double peakDbfs(short[] samples) {
        if (samples == null || samples.length == 0) return -120.0;
        int peak = 0;
        for (short sample : samples) peak = Math.max(peak, Math.abs((int) sample));
        return 20.0 * Math.log10(peak / 32768.0 + EPSILON);
    }

    private static boolean hasClipping(short[] samples) {
        if (samples == null) return false;
        for (short sample : samples) if (sample == Short.MAX_VALUE || sample == Short.MIN_VALUE) return true;
        return false;
    }

    private static final class SpeechDetection {
        final List<AudioMeasurementResult.SpeechInterval> intervals;
        final boolean[] frameSpeech;
        final double speechDurationMs;
        final int pauseCount;
        final double pauseDurationMs;
        SpeechDetection(List<AudioMeasurementResult.SpeechInterval> intervals, boolean[] frameSpeech, double speechDurationMs, int pauseCount, double pauseDurationMs) {
            this.intervals = intervals; this.frameSpeech = frameSpeech; this.speechDurationMs = speechDurationMs; this.pauseCount = pauseCount; this.pauseDurationMs = pauseDurationMs;
        }
    }

    private static final class PitchEstimate {
        final double frequencyHz; final double confidence;
        PitchEstimate(double frequencyHz, double confidence) { this.frequencyHz = frequencyHz; this.confidence = confidence; }
    }

    private static final class PitchSummary {
        final int validCount; final double median; final double min; final double max; final double variability; final double confidence;
        PitchSummary(int validCount, double median, double min, double max, double variability) { this(validCount, median, min, max, variability, 0); }
        PitchSummary(int validCount, double median, double min, double max, double variability, double confidence) { this.validCount = validCount; this.median = median; this.min = min; this.max = max; this.variability = variability; this.confidence = confidence; }
    }
}
