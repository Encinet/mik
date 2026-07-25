package org.encinet.mik.module.music.catalog;

import java.util.Objects;

/** Descriptive metadata for a playable track. */
public record TrackDetails(
        String title,
        String artist,
        String album,
        String format,
        AudioProperties audio
) {

    public TrackDetails {
        title = requireText(title, "title");
        artist = normalize(artist);
        album = normalize(album);
        format = requireText(format, "format");
        audio = Objects.requireNonNullElse(audio, AudioProperties.EMPTY);
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
