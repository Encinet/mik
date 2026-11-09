package org.encinet.mik.feature.entertainment.music.online;

import java.nio.file.Path;
import java.util.Objects;

/** Separates administrator scripts, retained subscription snapshots, and the internal registry. */
record LxSourcePaths(Path directory) {
    LxSourcePaths {
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    Path localDirectory() {
        return directory.resolve("local");
    }

    Path managedDirectory() {
        return directory.resolve("managed");
    }

    Path subscriptionsFile() {
        return directory.resolve("subscriptions.json");
    }
}
