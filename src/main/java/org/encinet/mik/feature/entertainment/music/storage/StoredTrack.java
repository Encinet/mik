package org.encinet.mik.feature.entertainment.music.storage;

import java.time.Duration;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;

/** Identity plus non-authoritative metadata used while the daemon is unavailable. */
public record StoredTrack(String id, String title, String artist, String author, String album,
        Long duration, String kind, String sound) {
    public static StoredTrack of(MusicTrack track) {
        var details = track.details();
        var duration = details.durationHint();
        return new StoredTrack(track.id(), details.title(), details.artist(), details.originalAuthor(), details.album(),
                duration == null ? null : duration.toMillis(),
                track.target() instanceof TrackTarget.NativeRecord ? "record" : "managed",
                track.target() instanceof TrackTarget.NativeRecord nativeRecord ? nativeRecord.sound() : null);
    }

    public MusicTrack track() {
        TrackTarget target = switch (kind) {
            case "record" -> new TrackTarget.NativeRecord(sound);
            case "managed" -> new TrackTarget.Managed(id);
            default -> throw new IllegalArgumentException("Unknown music track kind");
        };
        return new MusicTrack(id, new TrackDetails(title, artist, author, album,
                duration == null ? null : Duration.ofMillis(duration)), target);
    }
}
