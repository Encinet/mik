package org.encinet.mik.module.music.jukebox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JukeboxPlaybackSnapshotTest {

    @Test
    void statusOnlyImplementationsRetainAZeroedPlaybackClock() {
        JukeboxPlaybackStatus status = ignored -> PlaybackStatus.PLAYING;

        assertEquals(new JukeboxPlaybackSnapshot(PlaybackStatus.PLAYING, 0L),
                status.snapshot(null));
    }

    @Test
    void playbackClockCannotExposeANegativePosition() {
        assertEquals(0L,
                new JukeboxPlaybackSnapshot(PlaybackStatus.LOADING, -1L).positionMillis());
    }
}
