package org.encinet.mik.module.music.jukebox;

import java.util.Objects;

/** One coherent, read-only view of a jukebox playback clock. */
public record JukeboxPlaybackSnapshot(
        PlaybackStatus status,
        long positionMillis
) {
    public JukeboxPlaybackSnapshot {
        status = Objects.requireNonNull(status, "status");
        positionMillis = Math.max(0L, positionMillis);
    }
}
