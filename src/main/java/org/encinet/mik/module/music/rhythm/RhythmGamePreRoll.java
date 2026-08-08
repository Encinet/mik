package org.encinet.mik.module.music.rhythm;

import java.util.Objects;

/** A monotonic pre-roll that keeps transport startup behind the countdown. */
final class RhythmGamePreRoll {
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final long durationMillis;
    private final long durationNanos;
    private boolean started;
    private long startedAtNanos;
    private boolean playbackStartRequested;

    RhythmGamePreRoll(long durationMillis) {
        if (durationMillis <= 0L) {
            throw new IllegalArgumentException("durationMillis must be positive");
        }
        this.durationMillis = durationMillis;
        this.durationNanos = Math.multiplyExact(durationMillis, NANOS_PER_MILLI);
    }

    void begin(long nowNanos) {
        if (started) return;
        started = true;
        startedAtNanos = nowNanos;
    }

    boolean started() {
        return started;
    }

    boolean completeAt(long nowNanos) {
        return started && elapsedNanosAt(nowNanos) >= durationNanos;
    }

    int numberAt(long nowNanos) {
        if (!started) return secondsCeiling(durationNanos);
        long remainingNanos = Math.max(1L,
                durationNanos - elapsedNanosAt(nowNanos));
        return secondsCeiling(remainingNanos);
    }

    long elapsedMillisAt(long nowNanos) {
        if (!started) return 0L;
        return Math.min(durationMillis,
                elapsedNanosAt(nowNanos) / NANOS_PER_MILLI);
    }

    boolean requestPlaybackStart(long nowNanos, Runnable startPlayback) {
        Objects.requireNonNull(startPlayback, "startPlayback");
        if (playbackStartRequested || !completeAt(nowNanos)) return false;
        playbackStartRequested = true;
        startPlayback.run();
        return true;
    }

    private long elapsedNanosAt(long nowNanos) {
        // nanoTime is modular; subtraction remains valid for this short interval.
        return Math.max(0L, nowNanos - startedAtNanos);
    }

    private static int secondsCeiling(long nanos) {
        return (int) Math.max(1L,
                Math.min(3L, (nanos + NANOS_PER_SECOND - 1L)
                        / NANOS_PER_SECOND));
    }
}
