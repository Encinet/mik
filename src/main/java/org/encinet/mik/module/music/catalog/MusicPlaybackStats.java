package org.encinet.mik.module.music.catalog;

import java.time.Instant;

/** Read-only server-wide playback statistics used by ranking and cache policy. */
public interface MusicPlaybackStats {

    MusicPlaybackStats EMPTY = trackId -> TrackStats.EMPTY;

    TrackStats stats(String trackId);

    default TrackStats stats(MusicTrack track) {
        return track == null ? TrackStats.EMPTY : stats(track.id());
    }

    record TrackStats(long playCount, Instant lastPlayedAt) {
        public static final TrackStats EMPTY = new TrackStats(0, null);

        public TrackStats {
            if (playCount < 0) {
                throw new IllegalArgumentException("playCount must not be negative");
            }
        }

        public boolean hasBeenPlayed() {
            return playCount > 0 && lastPlayedAt != null;
        }
    }
}
