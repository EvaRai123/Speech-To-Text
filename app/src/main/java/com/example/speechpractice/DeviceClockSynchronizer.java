package com.example.speechpractice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class DeviceClockSynchronizer {
    private static final int MAX_OBSERVATIONS = 32;
    private final ArrayDeque<Observation> observations = new ArrayDeque<>();
    private double slope = 1.0;
    private double interceptNanos;
    private long estimatedRoundTripNanos = -1L;
    private boolean available;

    public void reset() {
        observations.clear();
        slope = 1.0;
        interceptNanos = 0.0;
        estimatedRoundTripNanos = -1L;
        available = false;
    }

    public void addExchange(long deviceMicros, long androidSentNanos,
                            long androidReceivedNanos) {
        if (deviceMicros < 0L || androidSentNanos < 0L
                || androidReceivedNanos < androidSentNanos
                || deviceMicros > Long.MAX_VALUE / 1000L) {
            throw new IllegalArgumentException("Clock synchronization exchange is invalid.");
        }
        long roundTrip = androidReceivedNanos - androidSentNanos;
        long midpoint = androidSentNanos + roundTrip / 2L;
        observations.addLast(new Observation(deviceMicros * 1000.0, midpoint));
        while (observations.size() > MAX_OBSERVATIONS) observations.removeFirst();
        estimatedRoundTripNanos = roundTrip;
        fit();
    }

    public boolean isAvailable() {
        return available;
    }

    public long getEstimatedRoundTripNanos() {
        return estimatedRoundTripNanos;
    }

    public long toAndroidMonotonicNanos(long deviceTimestampMicros) {
        if (!available || deviceTimestampMicros < 0L
                || deviceTimestampMicros > Long.MAX_VALUE / 1000L) {
            throw new IllegalStateException("A clock estimate is not available for this device session.");
        }
        double converted = slope * (deviceTimestampMicros * 1000.0) + interceptNanos;
        if (!Double.isFinite(converted) || converted < 0.0 || converted > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Converted timestamp is outside the supported range.");
        }
        return Math.round(converted);
    }

    private void fit() {
        if (observations.size() < 2) {
            available = false;
            return;
        }
        List<Observation> values = new ArrayList<>(observations);
        double meanDevice = 0.0;
        double meanAndroid = 0.0;
        for (Observation observation : values) {
            meanDevice += observation.deviceNanos / values.size();
            meanAndroid += observation.androidMidpointNanos / (double) values.size();
        }
        double covariance = 0.0;
        double variance = 0.0;
        for (Observation observation : values) {
            double dx = observation.deviceNanos - meanDevice;
            covariance += dx * (observation.androidMidpointNanos - meanAndroid);
            variance += dx * dx;
        }
        if (!(variance > 0.0)) return;
        double estimatedSlope = covariance / variance;
        if (!Double.isFinite(estimatedSlope) || estimatedSlope < 0.95 || estimatedSlope > 1.05) {
            return;
        }
        slope = estimatedSlope;
        interceptNanos = meanAndroid - slope * meanDevice;
        available = true;
    }

    private static final class Observation {
        final double deviceNanos;
        final double androidMidpointNanos;
        Observation(double deviceNanos, double androidMidpointNanos) {
            this.deviceNanos = deviceNanos;
            this.androidMidpointNanos = androidMidpointNanos;
        }
    }
}
