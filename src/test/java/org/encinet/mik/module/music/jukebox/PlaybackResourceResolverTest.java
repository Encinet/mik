package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.online.LxSourceService;
import org.encinet.mik.module.music.online.OnlineAudioCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlaybackResourceResolverTest {

    @TempDir
    Path directory;

    private OnlineAudioCache cache;
    private LxSourceService sourceService;

    @AfterEach
    void close() {
        if (cache != null) {
            cache.close();
        }
        if (sourceService != null) {
            sourceService.close();
        }
    }

    @Test
    void resolvesValidatedLocalPathWithoutUsingOnlineCache() throws Exception {
        Path local = Files.writeString(directory.resolve("local.mp3"), "local");
        cache = cache();
        PlaybackResourceResolver resolver = new PlaybackResourceResolver(cache);

        try (PlaybackResourceResolver.Resource resource = resolver.acquire(localTrack(local, null))
                .get(5, TimeUnit.SECONDS)) {
            assertEquals(local.toAbsolutePath().normalize().toString(), resource.identifier());
        }
        assertEquals(new OnlineAudioCache.CacheStats(0, 0),
                cache.statsAsync().get(5, TimeUnit.SECONDS));
    }

    @Test
    void rejectsLocalFileReplacedBySymbolicLinkAfterCataloging() throws Exception {
        Path root = Files.createDirectories(directory.resolve("music"));
        Path local = Files.writeString(root.resolve("song.mp3"), "audio");
        MusicTrack track = localTrack(local, root);
        Path outside = Files.writeString(directory.resolve("outside.mp3"), "outside");
        Files.delete(local);
        try {
            Files.createSymbolicLink(local, outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links are unavailable");
        }
        cache = cache();
        PlaybackResourceResolver resolver = new PlaybackResourceResolver(cache);

        assertFalse(resolver.acquire(track).handle((resource, error) -> error == null).join());
    }

    @Test
    void rejectsNbsBecauseItUsesTheNoteBlockEngine() {
        cache = cache();
        PlaybackResourceResolver resolver = new PlaybackResourceResolver(cache);
        MusicTrack nbs = new MusicTrack("song.nbs",
                new TrackDetails("Song", null, null, "NBS", AudioProperties.EMPTY),
                new TrackTarget.NbsFile(directory.resolve("song.nbs"), directory));

        assertFalse(resolver.acquire(nbs).handle((resource, error) -> error == null).join());
    }

    private OnlineAudioCache cache() {
        sourceService = new LxSourceService(directory.resolve("lxmusic"), ignored -> {},
                ignored -> {});
        return new OnlineAudioCache(directory.resolve("cache"), sourceService, ignored -> {});
    }

    private static MusicTrack localTrack(Path path, Path root) {
        TrackTarget.LocalFile target = root == null
                ? new TrackTarget.LocalFile(path) : new TrackTarget.LocalFile(path, root);
        return new MusicTrack(path.getFileName().toString(),
                new TrackDetails("Local", null, null, "MP3", AudioProperties.EMPTY), target);
    }
}
