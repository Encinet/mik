package org.encinet.mik.module.music.rhythm.playback;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTrack;

import java.util.Objects;
import java.util.UUID;

/** Immutable view of one rhythm transport and its shared extracted timeline. */
public record RhythmPlaybackSnapshot(
        UUID playbackId,
        MusicTrack track,
        RhythmAudioChannel audioChannel,
        long positionMillis,
        RhythmPlaybackState status,
        RhythmTrack timeline
) {
    public RhythmPlaybackSnapshot {
        playbackId = Objects.requireNonNull(playbackId, "playbackId");
        track = Objects.requireNonNull(track, "track");
        audioChannel = Objects.requireNonNull(audioChannel, "audioChannel");
        positionMillis = Math.max(0L, positionMillis);
        status = Objects.requireNonNull(status, "status");
        timeline = Objects.requireNonNull(timeline, "timeline");
    }
}
