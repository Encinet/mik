package org.encinet.mik.module.music.rhythm;

import org.encinet.mik.module.music.catalog.MusicTrack;
import java.util.Objects;
import java.util.UUID;

/** Immutable view of one active jukebox playback, with a shared growing timeline. */
public record RhythmPlaybackSnapshot(
        UUID playbackId,
        MusicTrack track,
        long positionMillis,
        RhythmPlaybackState status,
        RhythmTimeline timeline
) {
    public RhythmPlaybackSnapshot {
        playbackId = Objects.requireNonNull(playbackId, "playbackId");
        track = Objects.requireNonNull(track, "track");
        positionMillis = Math.max(0L, positionMillis);
        status = Objects.requireNonNull(status, "status");
        timeline = Objects.requireNonNull(timeline, "timeline");
    }
}
