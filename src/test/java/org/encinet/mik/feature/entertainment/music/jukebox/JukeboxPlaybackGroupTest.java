package org.encinet.mik.feature.entertainment.music.jukebox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JukeboxPlaybackGroupTest {

    @Test
    void onlyTheLatestSeekAndPauseIntentAreVisible() {
        var group = new JukeboxPlaybackGroup();
        group.seekTo(4_000L);
        group.seekTo(900L);
        group.setPaused(true);

        assertEquals(new JukeboxPlaybackGroup.Seek(2L, 900L), group.currentSeek());
        assertTrue(group.paused());
    }

    @Test
    void synchronizedGroupAllowsOnlyOneStartedBroadcast() {
        JukeboxPlaybackGroup group = new JukeboxPlaybackGroup();

        assertTrue(group.claimStartedAnnouncement());
        assertFalse(group.claimStartedAnnouncement());
        assertFalse(group.claimStartedAnnouncement());
    }

    @Test
    void lastOutputKeepsThePinnedVersionAndTheActualClockPositionWhileRejoining() {
        var group = new JukeboxPlaybackGroup();
        group.pinVersion("c".repeat(32));
        group.seekTo(2_000L);
        group.follow(() -> group.present(new JukeboxPlaybackSnapshot(PlaybackStatus.PLAYING, 8_000L, 12_000L, true)));
        group.setPaused(true);
        group.releaseClock();

        assertEquals(PlaybackStatus.RESUMABLE, group.snapshot().status());
        assertEquals(8_000L, group.recoveryPosition());
        group.preparing();
        assertEquals(PlaybackStatus.LOADING, group.snapshot().status());
        assertEquals(8_000L, group.currentSeek().positionMillis());
        assertEquals("c".repeat(32), group.resumeVersion());
        assertTrue(group.paused());
    }
}
