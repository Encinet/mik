package org.encinet.mik.module.music.jukebox;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackCountPolicyTest {

    @Test
    void naturalCompletionAlwaysCounts() {
        assertTrue(PlaybackCountPolicy.qualifies(true, 0, Duration.ofMinutes(4)));
        assertTrue(PlaybackCountPolicy.qualifies(true, 0, null));
    }

    @Test
    void interruptedPlaybackRequiresSeventyPercentOfKnownDuration() {
        Duration duration = Duration.ofSeconds(100);

        assertFalse(PlaybackCountPolicy.qualifies(false, 69_999, duration));
        assertTrue(PlaybackCountPolicy.qualifies(false, 70_000, duration));
        assertEquals(70_000, PlaybackCountPolicy.minimumPlayedMillis(duration));
    }

    @Test
    void roundsFractionalThresholdUpToTheNextMillisecond() {
        Duration duration = Duration.ofMillis(101);

        assertEquals(71, PlaybackCountPolicy.minimumPlayedMillis(duration));
        assertFalse(PlaybackCountPolicy.qualifies(false, 70, duration));
        assertTrue(PlaybackCountPolicy.qualifies(false, 71, duration));
    }

    @Test
    void unknownDurationRequiresThirtySeconds() {
        long minimum = PlaybackCountPolicy.UNKNOWN_DURATION_MINIMUM.toMillis();

        assertFalse(PlaybackCountPolicy.qualifies(false, minimum - 1, null));
        assertTrue(PlaybackCountPolicy.qualifies(false, minimum, null));
    }

    @Test
    void handlesExtremelyLongDurationsWithoutOverflow() {
        Duration duration = Duration.ofMillis(Long.MAX_VALUE);

        assertEquals(6_456_360_425_798_343_065L,
                PlaybackCountPolicy.minimumPlayedMillis(duration));
        assertTrue(PlaybackCountPolicy.qualifies(false, Long.MAX_VALUE, duration));
    }
}
