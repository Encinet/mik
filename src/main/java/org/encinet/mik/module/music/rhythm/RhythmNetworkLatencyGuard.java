package org.encinet.mik.module.music.rhythm;

/**
 * Fair-play admission and sustained-RTT policy shared by calibration and games.
 *
 * <p>Entry uses the current server RTT immediately. Once a session is active,
 * one isolated spike only raises a warning; three consecutive one-second
 * samples above the limit end the session before clamped compensation can make
 * timing misleading.</p>
 */
final class RhythmNetworkLatencyGuard {
    static final int MAXIMUM_PLAYABLE_RTT_MILLIS = 350;
    static final int REQUIRED_CONSECUTIVE_HIGH_SAMPLES = 3;
    static final long SAMPLE_INTERVAL_NANOS = 1_000_000_000L;

    private long lastSampleAtNanos;
    private int consecutiveHighSamples;
    private int lastObservedRttMillis;
    private State state = State.HEALTHY;

    RhythmNetworkLatencyGuard(long startedAtNanos) {
        this.lastSampleAtNanos = startedAtNanos;
    }

    static boolean allowsEntry(int pingMillis) {
        return normalized(pingMillis) <= MAXIMUM_PLAYABLE_RTT_MILLIS;
    }

    Update sample(int pingMillis, long sampledAtNanos) {
        long elapsed = sampledAtNanos - lastSampleAtNanos;
        if (elapsed < SAMPLE_INTERVAL_NANOS) {
            return new Update(false, state, normalized(pingMillis),
                    consecutiveHighSamples);
        }
        lastSampleAtNanos = sampledAtNanos;
        int rttMillis = normalized(pingMillis);
        lastObservedRttMillis = rttMillis;
        if (rttMillis > MAXIMUM_PLAYABLE_RTT_MILLIS) {
            consecutiveHighSamples++;
        } else {
            consecutiveHighSamples = 0;
        }
        state = consecutiveHighSamples >= REQUIRED_CONSECUTIVE_HIGH_SAMPLES
                ? State.EXCESSIVE
                : consecutiveHighSamples > 0 ? State.WARNING : State.HEALTHY;
        return new Update(true, state, rttMillis, consecutiveHighSamples);
    }

    boolean warningActive() {
        return state == State.WARNING;
    }

    int lastObservedRttMillis() {
        return lastObservedRttMillis;
    }

    private static int normalized(int pingMillis) {
        return Math.max(0, pingMillis);
    }

    enum State {
        HEALTHY,
        WARNING,
        EXCESSIVE
    }

    record Update(boolean sampled, State state, int rttMillis,
                  int consecutiveHighSamples) {
        boolean warning() {
            return sampled && state == State.WARNING;
        }

        boolean excessive() {
            return sampled && state == State.EXCESSIVE;
        }
    }
}
