package com.example.speechpractice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WearablePacket {
    public final int version;
    public final int type;
    public final long sessionId;
    public final long sequence;
    public final long deviceTimestampMicros;
    public final WearableDeviceStatus deviceStatus;
    public final List<AccelerometerWireSample> accelerometerSamples;
    public final List<SemgWireSample> semgSamples;
    public final Long echoedAndroidMonotonicNanos;
    public final long firstSampleSequence;
    public final int errorCode;
    public final int errorSource;
    public final String errorMessage;

    WearablePacket(int version, int type, long sessionId, long sequence,
                   long deviceTimestampMicros, WearableDeviceStatus deviceStatus,
                   List<AccelerometerWireSample> accelerometerSamples,
                   List<SemgWireSample> semgSamples, Long echoedAndroidMonotonicNanos,
                   long firstSampleSequence, int errorCode, int errorSource, String errorMessage) {
        this.version = version;
        this.type = type;
        this.sessionId = sessionId;
        this.sequence = sequence;
        this.deviceTimestampMicros = deviceTimestampMicros;
        this.deviceStatus = deviceStatus;
        this.accelerometerSamples = Collections.unmodifiableList(new ArrayList<>(accelerometerSamples));
        this.semgSamples = Collections.unmodifiableList(new ArrayList<>(semgSamples));
        this.echoedAndroidMonotonicNanos = echoedAndroidMonotonicNanos;
        this.firstSampleSequence = firstSampleSequence;
        this.errorCode = errorCode;
        this.errorSource = errorSource;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static final class AccelerometerWireSample {
        public final long sampleSequence;
        public final long deviceTimestampMicros;
        public final double xRaw;
        public final double yRaw;
        public final double zRaw;
        public final int unitCode;
        public final double scaleToMetersPerSecondSquared;

        AccelerometerWireSample(long sampleSequence, long deviceTimestampMicros,
                                double xRaw, double yRaw, double zRaw, int unitCode,
                                double scaleToMetersPerSecondSquared) {
            this.sampleSequence = sampleSequence;
            this.deviceTimestampMicros = deviceTimestampMicros;
            this.xRaw = xRaw;
            this.yRaw = yRaw;
            this.zRaw = zRaw;
            this.unitCode = unitCode;
            this.scaleToMetersPerSecondSquared = scaleToMetersPerSecondSquared;
        }
    }

    public static final class SemgWireSample {
        public final long sampleSequence;
        public final long deviceTimestampMicros;
        public final double rawValue;
        public final double scaleToConfiguredInputUnits;

        SemgWireSample(long sampleSequence, long deviceTimestampMicros,
                       double rawValue, double scaleToConfiguredInputUnits) {
            this.sampleSequence = sampleSequence;
            this.deviceTimestampMicros = deviceTimestampMicros;
            this.rawValue = rawValue;
            this.scaleToConfiguredInputUnits = scaleToConfiguredInputUnits;
        }
    }
}
