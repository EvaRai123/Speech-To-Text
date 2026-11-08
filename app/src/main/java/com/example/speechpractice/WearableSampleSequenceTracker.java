package com.example.speechpractice;

public final class WearableSampleSequenceTracker {
    private boolean initialized;
    private long nextExpected;
    private long lastFirstSequence;
    private int lastSampleCount;

    public WearableSequenceTracker.Result observe(long firstSequence, int sampleCount) {
        if (firstSequence < 0L || firstSequence > 0xFFFFFFFFL
                || sampleCount < 1 || sampleCount > WearableProtocol.MAX_SEMG_SAMPLES_PER_PACKET) {
            throw new IllegalArgumentException("Sample sequence and batch size are invalid.");
        }
        if (!initialized) {
            initialized = true;
            lastFirstSequence = firstSequence;
            lastSampleCount = sampleCount;
            nextExpected = (firstSequence + sampleCount) & 0xFFFFFFFFL;
            return new WearableSequenceTracker.Result(WearableSequenceTracker.Disposition.FIRST, 0L);
        }
        if (firstSequence == lastFirstSequence && sampleCount == lastSampleCount) {
            return new WearableSequenceTracker.Result(WearableSequenceTracker.Disposition.DUPLICATE, 0L);
        }
        long delta = (firstSequence - nextExpected) & 0xFFFFFFFFL;
        if (delta == 0L) {
            lastFirstSequence = firstSequence;
            lastSampleCount = sampleCount;
            nextExpected = (firstSequence + sampleCount) & 0xFFFFFFFFL;
            return new WearableSequenceTracker.Result(WearableSequenceTracker.Disposition.IN_ORDER, 0L);
        }
        if (delta >= 0x80000000L) {
            return new WearableSequenceTracker.Result(delta == 0xFFFFFFFFL
                    ? WearableSequenceTracker.Disposition.DUPLICATE
                    : WearableSequenceTracker.Disposition.OUT_OF_ORDER, 0L);
        }
        lastFirstSequence = firstSequence;
        lastSampleCount = sampleCount;
        nextExpected = (firstSequence + sampleCount) & 0xFFFFFFFFL;
        return new WearableSequenceTracker.Result(WearableSequenceTracker.Disposition.GAP, delta);
    }

    public void reset() {
        initialized = false;
        nextExpected = 0L;
        lastFirstSequence = 0L;
        lastSampleCount = 0;
    }
}
