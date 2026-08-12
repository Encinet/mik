package org.encinet.mik.module.music.jukebox;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JukeboxPlaybackGroupTest {

    @Test
    void reportsWhenAPlaybackClockHasActuallyBeenEstablished() {
        AtomicLong nanos = new AtomicLong(3_000_000_000L);
        JukeboxPlaybackGroup group = new JukeboxPlaybackGroup(nanos::get);

        assertFalse(group.playbackHasStarted());
        assertEquals(0L, group.synchronizedPositionMillis());
        assertTrue(group.playbackHasStarted());
    }

    @Test
    void followerCannotMoveTheLeaderClockAndTakesOverAfterLeaderLeaves() {
        JukeboxPlaybackGroup group = new JukeboxPlaybackGroup();
        JukeboxPlaybackGroup.Member leader = group.newMember();
        JukeboxPlaybackGroup.Member follower = group.newMember();
        leader.activate();
        follower.activate();

        leader.publishPositionMillis(1_000L);
        follower.publishPositionMillis(1_800L);
        assertEquals(1_000L, group.synchronizedPositionMillis());

        leader.close();
        follower.publishPositionMillis(1_100L);
        assertTrue(follower.isLeader());
        assertEquals(1_100L, group.synchronizedPositionMillis());
    }

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
