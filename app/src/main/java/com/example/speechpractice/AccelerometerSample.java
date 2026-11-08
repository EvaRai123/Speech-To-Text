package com.example.speechpractice;

public final class AccelerometerSample {
    public final long timestampMonotonicNanos;
    public final double xMetersPerSecondSquared;
    public final double yMetersPerSecondSquared;
    public final double zMetersPerSecondSquared;

    public AccelerometerSample(long timestampMonotonicNanos, double xMetersPerSecondSquared,
                               double yMetersPerSecondSquared, double zMetersPerSecondSquared) {
        if (timestampMonotonicNanos < 0L
                || !Double.isFinite(xMetersPerSecondSquared)
                || !Double.isFinite(yMetersPerSecondSquared)
                || !Double.isFinite(zMetersPerSecondSquared)) {
            throw new IllegalArgumentException("Accelerometer samples require a non-negative timestamp and finite axes.");
        }
        this.timestampMonotonicNanos = timestampMonotonicNanos;
        this.xMetersPerSecondSquared = xMetersPerSecondSquared;
        this.yMetersPerSecondSquared = yMetersPerSecondSquared;
        this.zMetersPerSecondSquared = zMetersPerSecondSquared;
    }
}
