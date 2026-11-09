package org.encinet.mik.feature.entertainment.music.playlist;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A saved, author-edited list. Runtime programs always take their own snapshot. */
public record Playlist(UUID id, UUID author, String name, String description, String icon, boolean published,
        long version, List<Entry> entries) {
    public static final int MAX_ENTRIES = 5000;
    public Playlist {
        Objects.requireNonNull(id);
        Objects.requireNonNull(author);
        name = text(name, 64);
        description = text(description == null ? "" : description, 512, true);
        icon = icon == null ? "MUSIC_DISC_CAT" : icon;
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES
                || entries.stream().map(Entry::trackId).distinct().count() != entries.size())
            throw new IllegalArgumentException("Invalid playlist entries");
    }
    public record Entry(UUID id, String trackId, long addedAt) {
        public Entry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(trackId);
        }
    }
    public boolean visibleTo(UUID actor) {
        return published || author.equals(actor);
    }
    public Playlist revise(
            String title, String details, String image, boolean visibility, List<Entry> items) {
        return new Playlist(id, author, title, details, image, visibility, version + 1, items);
    }
    public static String text(String value, int maximum) {
        return text(value, maximum, false);
    }
    private static String text(String value, int maximum, boolean empty) {
        String clean = Objects.requireNonNull(value).strip();
        if ((!empty && clean.isEmpty()) || clean.codePointCount(0, clean.length()) > maximum
                || clean.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n'))
            throw new IllegalArgumentException("Invalid text");
        return clean;
    }
}
