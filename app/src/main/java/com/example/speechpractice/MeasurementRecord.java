package com.example.speechpractice;

import java.io.Serializable;

public final class MeasurementRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    public final MeasurementSource source;
    public final MeasurementType type;
    public final MeasurementStatus status;
    public final Double value;
    public final String unit;
    /** Producer-reported monotonic acquisition time; null means the source did not provide it. */
    public final Long acquiredAtMonotonicNanos;
    /** Optional monotonic bounds for an aggregate computed from a sample window. */
    public final Long windowStartMonotonicNanos;
    public final Long windowEndMonotonicNanos;
    /** Wall-clock time this app received the record; never a substitute for acquisition time. */
    public final Long receivedAtWallClockMs;
    public final MeasurementQuality quality;
    public final Double confidence;
    public final String detail;

    private MeasurementRecord(MeasurementSource source, MeasurementType type, MeasurementStatus status,
                              Double value, String unit, Long acquiredAtMonotonicNanos,
                              Long windowStartMonotonicNanos, Long windowEndMonotonicNanos,
                              Long receivedAtWallClockMs, MeasurementQuality quality,
                              Double confidence, String detail) {
        if (source == null || type == null || status == null || quality == null) {
            throw new IllegalArgumentException("Measurement source, type, status, and quality are required.");
        }
        if (type.source != source) {
            throw new IllegalArgumentException("Measurement source is incompatible with its type.");
        }
        if (status == MeasurementStatus.AVAILABLE) {
            if (value == null || !Double.isFinite(value) || unit == null || unit.trim().isEmpty()
                    || receivedAtWallClockMs == null) {
                throw new IllegalArgumentException("Available measurements require a finite value, unit, and receive timestamp.");
            }
            if (!type.accepts(source, unit, value)) {
                throw new IllegalArgumentException("Measurement source, type, unit, or value is incompatible.");
            }
        } else if (value != null || unit != null) {
            throw new IllegalArgumentException("Unavailable measurements must not contain a numeric value or unit.");
        }
        if (confidence != null && (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0)) {
            throw new IllegalArgumentException("Confidence must be between zero and one.");
        }
        if (acquiredAtMonotonicNanos != null && acquiredAtMonotonicNanos < 0L
                || receivedAtWallClockMs != null && receivedAtWallClockMs < 0L
                || windowStartMonotonicNanos != null && windowStartMonotonicNanos < 0L
                || windowEndMonotonicNanos != null && windowEndMonotonicNanos < 0L) {
            throw new IllegalArgumentException("Measurement timestamps cannot be negative.");
        }
        if ((windowStartMonotonicNanos == null) != (windowEndMonotonicNanos == null)
                || windowStartMonotonicNanos != null
                && windowEndMonotonicNanos < windowStartMonotonicNanos) {
            throw new IllegalArgumentException("Measurement window timestamps must be a complete ordered pair.");
        }
        if (acquiredAtMonotonicNanos != null && windowStartMonotonicNanos != null) {
            throw new IllegalArgumentException("A record cannot be both a point sample and a window aggregate.");
        }
        this.source = source;
        this.type = type;
        this.status = status;
        this.value = value;
        this.unit = unit;
        this.acquiredAtMonotonicNanos = acquiredAtMonotonicNanos;
        this.windowStartMonotonicNanos = windowStartMonotonicNanos;
        this.windowEndMonotonicNanos = windowEndMonotonicNanos;
        this.receivedAtWallClockMs = receivedAtWallClockMs;
        this.quality = quality;
        this.confidence = confidence;
        this.detail = detail == null ? "" : detail;
    }

    public static MeasurementRecord available(MeasurementSource source, MeasurementType type,
                                              double value, String unit, Long acquiredAtMonotonicNanos,
                                              long receivedAtWallClockMs, MeasurementQuality quality,
                                              Double confidence, String detail) {
        return new MeasurementRecord(source, type, MeasurementStatus.AVAILABLE, value, unit,
                acquiredAtMonotonicNanos, null, null, receivedAtWallClockMs, quality, confidence, detail);
    }

    public static MeasurementRecord availableWindow(MeasurementSource source, MeasurementType type,
                                                    double value, String unit, long windowStartMonotonicNanos,
                                                    long windowEndMonotonicNanos, long receivedAtWallClockMs,
                                                    MeasurementQuality quality, Double confidence, String detail) {
        return new MeasurementRecord(source, type, MeasurementStatus.AVAILABLE, value, unit, null,
                windowStartMonotonicNanos, windowEndMonotonicNanos, receivedAtWallClockMs,
                quality, confidence, detail);
    }

    public static MeasurementRecord unavailable(MeasurementSource source, MeasurementType type,
                                                MeasurementStatus status, String detail) {
        if (status == MeasurementStatus.AVAILABLE) {
            throw new IllegalArgumentException("Use available() for an available measurement.");
        }
        return new MeasurementRecord(source, type, status, null, null, null, null,
                null, null, MeasurementQuality.UNKNOWN, null, detail);
    }
}
