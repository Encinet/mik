package org.encinet.mik.feature.entertainment.music.catalog;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record MusicTrack(
        String id,
        TrackDetails details,
        TrackTarget target,
        Availability availability
) {

    static final UUID NIL_ID = new UUID(0, 0);
    public enum Availability { READY, NEEDS_PREPARATION, UNKNOWN, UNAVAILABLE }

    public MusicTrack(String id, TrackDetails details, TrackTarget target) {
        this(id, details, target, target instanceof TrackTarget.NativeRecord ? Availability.READY : Availability.UNKNOWN);
    }

    public static MusicTrack reference(TrackTarget.Managed target) {
        return new MusicTrack(target.trackId(), new TrackDetails(target.trackId(), null, null, null), target);
    }

    public MusicTrack {
        id = requireText(id, "id");
        details = Objects.requireNonNull(details, "details");
        target = Objects.requireNonNull(target, "target");
        availability = Objects.requireNonNull(availability, "availability");
        if (target instanceof TrackTarget.Managed managed && !id.equals(managed.trackId())) {
            throw new IllegalArgumentException("Track identity mismatch");
        }
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
