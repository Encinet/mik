package org.encinet.mik.feature.entertainment.music;

import java.nio.file.Path;
import java.util.Objects;

/** Music-owned files. Only the composition root distributes paths to their owners. */
public record MusicDataPaths(Path directory) {
    public MusicDataPaths {
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    public static MusicDataPaths in(Path pluginDirectory) {
        return new MusicDataPaths(pluginDirectory.resolve("music"));
    }

    public Path settingsFile() {
        return directory.resolve("settings.yml");
    }

    public Path libraryDirectory() {
        return directory.resolve("library");
    }

    public Path sourcesDirectory() {
        return directory.resolve("sources");
    }

    public Path databaseFile() {
        return directory.resolve("data/music.sqlite");
    }

    public Path statisticsFile() {
        return directory.resolve("data/playback-statistics.json");
    }

    public Path discSigningKey() {
        return directory.resolve("secrets/disc-signing.key");
    }

    public Path audioCacheDirectory() {
        return directory.resolve("cache/audio");
    }

    public Path lyricsCacheDirectory() {
        return directory.resolve("cache/lyrics");
    }
}
