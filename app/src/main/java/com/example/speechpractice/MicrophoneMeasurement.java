package com.example.speechpractice;

import java.io.Serializable;

public class MicrophoneMeasurement implements Serializable {
    private static final long serialVersionUID = 1L;

    public final long recordedAtMs;
    public final long durationMs;
    public final double rmsDbfs;
    public final double peakDbfs;
    public final double noiseFloorDbfs;
    public final double snrDb;
    public final double speechActiveDurationMs;
    public final double estimatedF0Hz;
    public final String quality;
    public final String qualityFlags;
    public final boolean available;

    public MicrophoneMeasurement(long recordedAtMs, long durationMs, double rmsDbfs, double peakDbfs,
                                double noiseFloorDbfs, double snrDb, double speechActiveDurationMs,
                                double estimatedF0Hz, String quality, String qualityFlags, boolean available) {
        this.recordedAtMs = recordedAtMs;
        this.durationMs = durationMs;
        this.rmsDbfs = rmsDbfs;
        this.peakDbfs = peakDbfs;
        this.noiseFloorDbfs = noiseFloorDbfs;
        this.snrDb = snrDb;
        this.speechActiveDurationMs = speechActiveDurationMs;
        this.estimatedF0Hz = estimatedF0Hz;
        this.quality = quality;
        this.qualityFlags = qualityFlags;
        this.available = available;
    }
}
