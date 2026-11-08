package com.example.speechpractice;

public final class WearableSequenceTracker {
    public enum Disposition {
        FIRST,
        IN_ORDER,
        GAP,
        DUPLICATE,
        OUT_OF_ORDER,
        NEW_SESSION
    }

    public static final class Result {
        public final Disposition disposition;
        public final long missingPackets;

        Result(Disposition disposition, long missingPackets) {
            this.disposition = disposition;
            this.missingPackets = missingPackets;
        }

        public boolean shouldAccept() {
            return disposition == Disposition.FIRST || disposition == Disposition.IN_ORDER
                    || disposition == Disposition.GAP || disposition == Disposition.NEW_SESSION;
        }
    }

    private boolean initialized;
    private long sessionId;
    private long lastSequence;

    public Result observe(long candidateSessionId, long candidateSequence) {
        if (candidateSessionId < 0L || candidateSessionId > 0xFFFFFFFFL
                || candidateSequence < 0L || candidateSequence > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Session and packet sequence must be unsigned 32-bit values.");
        }
        if (!initialized) {
            initialized = true;
            sessionId = candidateSessionId;
            lastSequence = candidateSequence;
            return new Result(Disposition.FIRST, 0L);
        }
        if (candidateSessionId != sessionId) {
            sessionId = candidateSessionId;
            lastSequence = candidateSequence;
            return new Result(Disposition.NEW_SESSION, 0L);
        }
        long delta = (candidateSequence - lastSequence) & 0xFFFFFFFFL;
        if (delta == 0L) return new Result(Disposition.DUPLICATE, 0L);
        if (delta >= 0x80000000L) return new Result(Disposition.OUT_OF_ORDER, 0L);
        lastSequence = candidateSequence;
        if (delta == 1L) return new Result(Disposition.IN_ORDER, 0L);
        return new Result(Disposition.GAP, delta - 1L);
    }

    public void reset() {
        initialized = false;
        sessionId = 0L;
        lastSequence = 0L;
    }
}
