package com.example.speechpractice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PracticeMeasurementState implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MeasurementSource {
        MICROPHONE,
        ACCELEROMETER,
        SURFACE_EMG,
        UNKNOWN
    }

    public enum Availability {
        AVAILABLE,
        UNAVAILABLE,
        ERROR,
        UNKNOWN
    }

    public final String sourceName;
    public final MeasurementSource source;
    public final Availability availability;
    public final String statusMessage;
    public final long lastUpdateMs;
    public final String details;
    public final List<String> warnings;

    public PracticeMeasurementState(String sourceName, MeasurementSource source, Availability availability,
                                   String statusMessage, long lastUpdateMs, String details, List<String> warnings) {
        this.sourceName = sourceName;
        this.source = source;
        this.availability = availability;
        this.statusMessage = statusMessage;
        this.lastUpdateMs = lastUpdateMs;
        this.details = details;
        this.warnings = warnings == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public static PracticeMeasurementState unavailable(String sourceName, MeasurementSource source, String message) {
        return new PracticeMeasurementState(sourceName, source, Availability.UNAVAILABLE, message, 0L,
                "No genuine wearable measurement is currently available for this source.", Collections.emptyList());
    }

    public static PracticeMeasurementState available(String sourceName, MeasurementSource source, String message, long lastUpdateMs, String details) {
        return new PracticeMeasurementState(sourceName, source, Availability.AVAILABLE, message, lastUpdateMs,
                details == null ? "Measurement data is available." : details, Collections.emptyList());
    }

    public static PracticeMeasurementState error(String sourceName, MeasurementSource source, String message) {
        return new PracticeMeasurementState(sourceName, source, Availability.ERROR, message, 0L,
                "The source is unavailable because of an error or missing dependency.", Collections.emptyList());
    }
}
