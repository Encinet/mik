package org.encinet.mik.module.music.catalog;

import java.util.Locale;
import java.util.Objects;

public record MusicTrack(
        String id,
        TrackDetails details,
        TrackTarget target
) {

    public MusicTrack {
        id = requireText(id, "id");
        details = Objects.requireNonNull(details, "details");
        target = Objects.requireNonNull(target, "target");
    }

    public boolean matches(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return id.toLowerCase(Locale.ROOT).contains(normalized)
                || details.title().toLowerCase(Locale.ROOT).contains(normalized)
                || (details.artist() != null
                && details.artist().toLowerCase(Locale.ROOT).contains(normalized))
                || (details.originalAuthor() != null
                && details.originalAuthor().toLowerCase(Locale.ROOT).contains(normalized))
                || (details.album() != null
                && details.album().toLowerCase(Locale.ROOT).contains(normalized));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
