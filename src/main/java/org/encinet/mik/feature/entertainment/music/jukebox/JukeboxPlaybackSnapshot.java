package org.encinet.mik.feature.entertainment.music.jukebox;

import java.util.Objects;

/** One coherent, read-only view of a jukebox playback clock. */
public record JukeboxPlaybackSnapshot(
        PlaybackStatus status,
        long positionMillis,
        long durationMillis,
        boolean seekable
) {
    public JukeboxPlaybackSnapshot(PlaybackStatus status, long positionMillis) {
        this(status, positionMillis, 0L, false);
    }

    public JukeboxPlaybackSnapshot {
        status = Objects.requireNonNull(status, "status");
        positionMillis = Math.max(0L, positionMillis);
        durationMillis = Math.max(0L, durationMillis);
        seekable = seekable && durationMillis > 0 && status.hasLiveTransport();
    }

    JukeboxPlaybackSnapshot withStatus(PlaybackStatus next) {
        return next == status ? this : new JukeboxPlaybackSnapshot(next, positionMillis, durationMillis, seekable);
    }
}
