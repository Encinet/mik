package org.encinet.mik.feature.entertainment.music.catalog;

import java.util.Objects;
import java.util.UUID;

/** Stable identity, not a media representation or a retrieval method. */
public sealed interface TrackTarget permits TrackTarget.Managed, TrackTarget.NativeRecord {
    record Managed(String trackId) implements TrackTarget {
        public Managed {
            var uuid = UUID.fromString(Objects.requireNonNull(trackId, "trackId"));
            if (!uuid.toString().equals(trackId) || uuid.equals(MusicTrack.NIL_ID)) {
                throw new IllegalArgumentException("Invalid track ID");
            }
        }
    }

    /** A game resource key, resolved entirely by Paper. */
    record NativeRecord(String sound) implements TrackTarget {
        public NativeRecord {
            if (sound == null || sound.isBlank()) throw new IllegalArgumentException("Missing record sound key");
        }
    }
}
