package org.encinet.mik.module.music.rhythm;

/**
 * Maps PacketEvents' monotonic input timestamps back onto the server playback
 * timeline without waiting for the Bukkit event's processing tick.
 */
final class RhythmMonotonicPlaybackClock {
    private static final long MAXIMUM_EXTRAPOLATION_NANOS = 1_000_000_000L;

    private long positionMillis;
    private long sampledAtNanos;
    private boolean advancing;
    private boolean initialized;

    RhythmMonotonicPlaybackClock(long positionMillis, boolean advancing,
                                 long sampledAtNanos) {
        observe(positionMillis, advancing, sampledAtNanos);
    }

    void observe(long positionMillis, boolean advancing, long sampledAtNanos) {
        if (initialized && sampledAtNanos < this.sampledAtNanos) return;
        this.positionMillis = Math.max(0L, positionMillis);
        this.sampledAtNanos = sampledAtNanos;
        this.advancing = advancing;
        initialized = true;
    }

    long positionAt(long timestampNanos) {
        if (!initialized || !advancing) return positionMillis;
        long deltaNanos;
        if (timestampNanos >= sampledAtNanos) {
            deltaNanos = Math.min(timestampNanos - sampledAtNanos,
                    MAXIMUM_EXTRAPOLATION_NANOS);
            return saturatedAdd(positionMillis, deltaNanos / 1_000_000L);
        }
        deltaNanos = Math.min(sampledAtNanos - timestampNanos,
                MAXIMUM_EXTRAPOLATION_NANOS);
        return Math.max(0L, positionMillis - deltaNanos / 1_000_000L);
    }

    private static long saturatedAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }
}
