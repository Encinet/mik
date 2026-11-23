package org.encinet.mik.feature.entertainment.music.catalog;

import java.time.Duration;

/** Catalog display metadata. Duration is a hint, not a playback clock. */
public record TrackDetails(
        String title,
        String artist,
        String originalAuthor,
        String album,
        Duration durationHint
) {

    public TrackDetails(String title, String artist, String album, Duration durationHint) {
        this(title, artist, null, album, durationHint);
    }

    public TrackDetails {
        title = requireText(title, "title");
        artist = normalize(artist);
        originalAuthor = normalize(originalAuthor);
        album = normalize(album);
        if (durationHint != null && (durationHint.isNegative() || durationHint.isZero())) {
            throw new IllegalArgumentException("Duration hint must be positive");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
