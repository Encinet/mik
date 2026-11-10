package org.encinet.mik.feature.entertainment.music.jukebox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JukeboxPlaybackSnapshotTest {

    @Test
    void playbackClockCannotExposeANegativePosition() {
        assertEquals(0L,
                new JukeboxPlaybackSnapshot(PlaybackStatus.LOADING, -1L).positionMillis());
    }
}
