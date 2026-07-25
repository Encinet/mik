package org.encinet.mik.module.music.jukebox;

import java.time.Duration;

/** Defines when a jukebox playback is substantial enough for server statistics. */
final class PlaybackCountPolicy {

    static final int COMPLETION_PERCENT = 70;
    static final Duration UNKNOWN_DURATION_MINIMUM = Duration.ofSeconds(30);
    private static final long PERCENT_DENOMINATOR = 100;

    private PlaybackCountPolicy() {
    }

    static boolean qualifies(boolean finishedNaturally, long playedMillis, Duration duration) {
        if (finishedNaturally) {
            return true;
        }
        long minimumMillis = minimumPlayedMillis(duration);
        return playedMillis >= minimumMillis;
    }

    static long minimumPlayedMillis(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return UNKNOWN_DURATION_MINIMUM.toMillis();
        }
        long durationMillis;
        try {
            durationMillis = duration.toMillis();
        } catch (ArithmeticException ignored) {
            durationMillis = Long.MAX_VALUE;
        }
        long quotient = durationMillis / PERCENT_DENOMINATOR;
        long remainder = durationMillis % PERCENT_DENOMINATOR;
        long threshold = quotient * COMPLETION_PERCENT
                + (remainder * COMPLETION_PERCENT + PERCENT_DENOMINATOR - 1)
                / PERCENT_DENOMINATOR;
        return Math.max(1, threshold);
    }
}
