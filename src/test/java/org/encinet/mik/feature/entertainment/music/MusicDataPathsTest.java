package org.encinet.mik.feature.entertainment.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.encinet.mik.feature.entertainment.music.online.OnlineAudioCacheTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MusicDataPathsTest {
    @TempDir Path directory;

    @Test
    void cacheStartupAndClearTouchOnlyTheirOwnLeafDirectory() throws Exception {
        var paths = MusicDataPaths.in(directory);
        List<Path> retained = List.of(paths.settingsFile(), paths.databaseFile(), paths.statisticsFile(),
                paths.discSigningKey(), paths.libraryDirectory().resolve("album/song.nbs"),
                paths.sourcesDirectory().resolve("subscriptions.json"),
                paths.sourcesDirectory().resolve("managed/retained.js"),
                paths.lyricsCacheDirectory().resolve("lyrics.json"),
                paths.directory().resolve("unrelated.part"));
        for (Path file : retained) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "keep");
        }
        Files.createDirectories(paths.audioCacheDirectory());
        Path abandoned = Files.writeString(paths.audioCacheDirectory().resolve("abandoned.part"), "partial");
        Path audio =
                Files.writeString(paths.audioCacheDirectory().resolve("a".repeat(64) + ".audio"), "audio");
        try (var cache = OnlineAudioCacheTestSupport.create(paths.audioCacheDirectory(),
                     _ -> CompletableFuture.failedFuture(new AssertionError("No network expected")))) {
            assertFalse(Files.exists(abandoned));
            assertTrue(Files.exists(audio));
            cache.clearAsync().get(5, TimeUnit.SECONDS);
            assertFalse(Files.exists(audio));
        }
        for (Path file : retained) assertEquals("keep", Files.readString(file), file.toString());
        assertFalse(Files.exists(directory.resolve("state")));
        assertFalse(Files.exists(directory.resolve("lxmusic")));
        assertFalse(Files.exists(directory.resolve("cache")));
    }
}
