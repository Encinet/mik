package org.encinet.mik.module.music.catalog;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Describes where a track's media can be obtained without preparing or playing it. */
public sealed interface TrackTarget
        permits TrackTarget.LocalFile, TrackTarget.NbsFile, TrackTarget.Lx {

    record LocalFile(Path path, Path root) implements TrackTarget {
        public LocalFile(Path path) {
            this(path, parentOf(path));
        }

        public LocalFile {
            path = normalized(path, "path");
            root = normalized(root, "root");
            if (!path.startsWith(root)) {
                throw new IllegalArgumentException("Local music path is outside its root");
            }
        }
    }

    record NbsFile(Path path, Path root) implements TrackTarget {
        public NbsFile(Path path) {
            this(path, parentOf(path));
        }

        public NbsFile {
            path = normalized(path, "path");
            root = normalized(root, "root");
            if (!path.startsWith(root)) {
                throw new IllegalArgumentException("NBS music path is outside its root");
            }
        }
    }

    record Lx(String source, String songId, List<String> qualities, String musicInfoJson,
              String providerId) implements TrackTarget {
        public Lx(String source, String songId, List<String> qualities, String musicInfoJson) {
            this(source, songId, qualities, musicInfoJson, null);
        }

        public Lx {
            source = requireText(source, "source").toLowerCase(Locale.ROOT);
            songId = requireText(songId, "songId");
            qualities = qualities == null ? List.of() : List.copyOf(qualities);
            musicInfoJson = requireText(musicInfoJson, "musicInfoJson");
            providerId = providerId == null || providerId.isBlank()
                    ? null : requireText(providerId, "providerId");
        }
    }

    private static Path parentOf(Path path) {
        Path normalized = normalized(path, "path");
        Path parent = normalized.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Media path must have a parent directory");
        }
        return parent;
    }

    private static Path normalized(Path path, String field) {
        return Objects.requireNonNull(path, field).toAbsolutePath().normalize();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
