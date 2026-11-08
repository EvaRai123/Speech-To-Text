package com.example.speechpractice;

public final class SemgSample {
    public final long timestampMonotonicNanos;
    public final double inputUnits;

    public SemgSample(long timestampMonotonicNanos, double inputUnits) {
        if (timestampMonotonicNanos < 0L || !Double.isFinite(inputUnits)) {
            throw new IllegalArgumentException("sEMG samples require a non-negative timestamp and finite input.");
        }
        this.timestampMonotonicNanos = timestampMonotonicNanos;
        this.inputUnits = inputUnits;
    }
}
