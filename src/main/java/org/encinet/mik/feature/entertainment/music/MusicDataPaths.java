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

    public Path databaseFile() {
        return directory.resolve("data/music.sqlite");
    }

    public Path discSigningKey() {
        return directory.resolve("secrets/disc-signing.key");
    }

}
