package org.encinet.mik.module.music.online;

import org.encinet.mik.module.music.catalog.TrackTarget;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Cross-package construction helpers for focused music cache integration tests. */
public final class OnlineAudioCacheTestSupport {
    private OnlineAudioCacheTestSupport() {
    }

    public static OnlineAudioCache create(
            Path directory,
            Function<TrackTarget.Lx, CompletableFuture<String>> resolver) {
        return new OnlineAudioCache(directory, resolver::apply, ignored -> {},
                1024 * 1024, Duration.ofSeconds(5));
    }
}
