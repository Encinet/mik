package org.encinet.mik.module.music.jukebox;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JukeboxPlaybackGroupTest {

    @Test
    void firstReadyJukeboxClaimsEpochAndLaterJukeboxesJoinItsPosition() {
        AtomicLong nanos = new AtomicLong(5_000_000_000L);
        JukeboxPlaybackGroup group = new JukeboxPlaybackGroup(nanos::get);

        assertEquals(0L, group.synchronizedPositionMillis());
        nanos.addAndGet(1_250_000_000L);
        assertEquals(1_250L, group.synchronizedPositionMillis());

        group.publishPositionMillis(900L);
        nanos.addAndGet(10_000_000_000L);
        assertEquals(900L, group.synchronizedPositionMillis(),
                "published backend progress must replace wall-clock extrapolation");
    }

    @Test
    void backwardsClockMovementCannotProduceANegativePosition() {
        AtomicLong nanos = new AtomicLong(5_000_000_000L);
        JukeboxPlaybackGroup group = new JukeboxPlaybackGroup(nanos::get);

        group.synchronizedPositionMillis();
        nanos.set(4_000_000_000L);
        assertEquals(0L, group.synchronizedPositionMillis());
    }

    @Test
    void synchronizedGroupAllowsOnlyOneStartedBroadcast() {
        JukeboxPlaybackGroup group = new JukeboxPlaybackGroup();

        assertTrue(group.claimStartedAnnouncement());
        assertFalse(group.claimStartedAnnouncement());
        assertFalse(group.claimStartedAnnouncement());
    }
}
