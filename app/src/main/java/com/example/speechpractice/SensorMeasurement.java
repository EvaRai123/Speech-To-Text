package com.example.speechpractice;

import java.io.Serializable;

public class SensorMeasurement implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String sourceName;
    public final long recordedAtMs;
    public final long durationMs;
    public final boolean available;
    public final String status;
    public final String details;

    public SensorMeasurement(String sourceName, long recordedAtMs, long durationMs, boolean available, String status, String details) {
        this.sourceName = sourceName;
        this.recordedAtMs = recordedAtMs;
        this.durationMs = durationMs;
        this.available = available;
        this.status = status;
        this.details = details;
    }
}
