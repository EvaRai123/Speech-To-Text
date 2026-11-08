package com.example.speechpractice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AudioMeasurementResult {
    public static final String ALGORITHM_VERSION = "audio-measurement-1";
    public final long computedAtWallClockMs;
    public final int sampleRateHz;
    public final double rmsDbfs;
    public final double peakDbfs;
    public final double noiseFloorDbfs;
    public final double snrDb;
    public final boolean clippingDetected;
    public final double speechActiveDurationMs;
    public final double voicedDurationMs;
    public final int pauseCount;
    public final double pauseDurationMs;
    public final double medianF0Hz;
    public final double f0MinHz;
    public final double f0MaxHz;
    public final double f0VariabilityHz;
    public final double pitchConfidence;
    public final String quality;
    public final List<String> qualityFlags;
    public final List<SpeechInterval> speechIntervals;

    AudioMeasurementResult(long computedAtWallClockMs, int sampleRateHz, double rmsDbfs, double peakDbfs,
                           double noiseFloorDbfs, double snrDb, boolean clippingDetected,
                           double speechActiveDurationMs, double voicedDurationMs,
                           int pauseCount, double pauseDurationMs,
                           double medianF0Hz, double f0MinHz, double f0MaxHz, double f0VariabilityHz,
                           double pitchConfidence, String quality, List<String> qualityFlags,
                           List<SpeechInterval> speechIntervals) {
        this.computedAtWallClockMs = computedAtWallClockMs;
        this.sampleRateHz = sampleRateHz;
        this.rmsDbfs = rmsDbfs;
        this.peakDbfs = peakDbfs;
        this.noiseFloorDbfs = noiseFloorDbfs;
        this.snrDb = snrDb;
        this.clippingDetected = clippingDetected;
        this.speechActiveDurationMs = speechActiveDurationMs;
        this.voicedDurationMs = voicedDurationMs;
        this.pauseCount = pauseCount;
        this.pauseDurationMs = pauseDurationMs;
        this.medianF0Hz = medianF0Hz;
        this.f0MinHz = f0MinHz;
        this.f0MaxHz = f0MaxHz;
        this.f0VariabilityHz = f0VariabilityHz;
        this.pitchConfidence = pitchConfidence;
        this.quality = quality;
        this.qualityFlags = Collections.unmodifiableList(new ArrayList<>(qualityFlags));
        this.speechIntervals = Collections.unmodifiableList(new ArrayList<>(speechIntervals));
    }

    public static final class SpeechInterval {
        public final double startMs;
        public final double endMs;

        SpeechInterval(double startMs, double endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }
}
