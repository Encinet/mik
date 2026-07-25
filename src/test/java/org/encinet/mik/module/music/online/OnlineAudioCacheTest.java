package org.encinet.mik.module.music.online;

import com.sun.net.httpserver.HttpServer;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineAudioCacheTest {

    @TempDir
    Path directory;

    private HttpServer server;
    private OnlineAudioCache cache;

    @AfterEach
    void close() {
        if (cache != null) {
            cache.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void coalescesConcurrentDownloadsAndReusesCacheAfterRestart() throws Exception {
        byte[] audio = "cached-audio-data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        String url = serve("/song", audio, requests);
        AtomicInteger resolutions = new AtomicInteger();
        LxTrackResolver upstream = track -> {
            resolutions.incrementAndGet();
            return CompletableFuture.completedFuture(url);
        };

        cache = cache(upstream, 1024);
        assertFalse(cache.isCached(target()));
        CompletableFuture<String> first = cache.resolve(target());
        CompletableFuture<String> second = cache.resolve(target());
        String firstPath = first.get(5, TimeUnit.SECONDS);
        String secondPath = second.get(5, TimeUnit.SECONDS);

        assertEquals(firstPath, secondPath);
        assertArrayEquals(audio, Files.readAllBytes(Path.of(firstPath)));
        assertEquals(1, resolutions.get());
        assertEquals(1, requests.get());
        assertTrue(cache.isCached(target()));
        assertEquals(new OnlineAudioCache.CacheStats(1, audio.length),
                cache.statsAsync().get(5, TimeUnit.SECONDS));

        cache.close();
        cache = cache(track -> CompletableFuture.failedFuture(
                new AssertionError("cached playback must not resolve the source URL")), 1024);

        assertTrue(cache.isCached(target()));
        assertEquals(firstPath, cache.resolve(target()).get(5, TimeUnit.SECONDS));
        assertEquals(1, requests.get());
    }

    @Test
    void rejectsOversizedDownloadsWithoutLeavingPartialFiles() throws Exception {
        byte[] audio = new byte[128];
        AtomicInteger requests = new AtomicInteger();
        String url = serve("/large", audio, requests);
        AtomicInteger resolutions = new AtomicInteger();
        cache = cache(track -> {
            resolutions.incrementAndGet();
            return CompletableFuture.completedFuture(url);
        }, 32);

        assertFalse(cache.resolve(target()).handle((path, error) -> error == null)
                .get(5, TimeUnit.SECONDS));
        assertEquals(new OnlineAudioCache.CacheStats(0, 0),
                cache.statsAsync().get(5, TimeUnit.SECONDS));
        try (var files = Files.list(directory)) {
            assertTrue(files.findAny().isEmpty());
        }

        assertFalse(cache.resolve(target()).handle((path, error) -> error == null)
                .get(5, TimeUnit.SECONDS));
        assertEquals(2, resolutions.get());
        assertEquals(2, requests.get());
    }

    @Test
    void clearRemovesCachedAudioAndForcesAnotherDownload() throws Exception {
        byte[] audio = "audio".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        String url = serve("/clear", audio, requests);
        cache = cache(track -> CompletableFuture.completedFuture(url), 1024);

        String firstPath = cache.resolve(target()).get(5, TimeUnit.SECONDS);
        assertTrue(Files.isRegularFile(Path.of(firstPath)));

        assertEquals(new OnlineAudioCache.CacheStats(0, 0),
                cache.clearAsync().get(5, TimeUnit.SECONDS));
        assertFalse(cache.isCached(target()));
        assertFalse(Files.exists(Path.of(firstPath)));
        String secondPath = cache.resolve(target()).get(5, TimeUnit.SECONDS);

        assertEquals(firstPath, secondPath);
        assertEquals(2, requests.get());
    }

    @Test
    void timesOutStalledResponseBodiesWithoutLeavingPartialFiles() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stalled", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[]{1, 2, 3});
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/stalled";
        cache = new OnlineAudioCache(directory,
                ignored -> CompletableFuture.completedFuture(url), ignored -> {},
                1024, Duration.ofMillis(150));

        assertFalse(cache.resolve(target()).handle((path, error) -> error == null)
                .get(5, TimeUnit.SECONDS));
        assertEquals(1, requests.get());
        assertEquals(new OnlineAudioCache.CacheStats(0, 0),
                cache.statsAsync().get(5, TimeUnit.SECONDS));
        try (var files = Files.list(directory)) {
            assertTrue(files.findAny().isEmpty());
        }
    }

    @Test
    void publicMediaOperationsOnlyAcceptLxTargets() throws Exception {
        assertEquals(TrackTarget.Lx.class,
                OnlineAudioCache.class.getMethod("isCached", TrackTarget.Lx.class)
                        .getParameterTypes()[0]);
        assertEquals(TrackTarget.Lx.class,
                OnlineAudioCache.class.getMethod("resolve", TrackTarget.Lx.class)
                        .getParameterTypes()[0]);
        assertEquals(TrackTarget.Lx.class,
                OnlineAudioCache.class.getMethod("acquire", TrackTarget.Lx.class)
                        .getParameterTypes()[0]);
        assertEquals(TrackTarget.Lx.class,
                OnlineAudioCache.class.getMethod("invalidate", TrackTarget.Lx.class)
                        .getParameterTypes()[0]);
    }

    @Test
    void evictsLeastRecentlyUsedFilesWhenTotalQuotaIsReached() throws Exception {
        byte[] audio = new byte[6];
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/first", exchange -> respond(exchange, audio, requests));
        server.createContext("/second", exchange -> respond(exchange, audio, requests));
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        cache = new OnlineAudioCache(directory, target -> CompletableFuture.completedFuture(
                baseUrl + (target.songId().equals("1") ? "/first" : "/second")), ignored -> {},
                8, 10, Duration.ofSeconds(3));

        Path first = Path.of(cache.resolve(target("lx:kw:1"))
                .get(5, TimeUnit.SECONDS));
        Files.setLastModifiedTime(first, java.nio.file.attribute.FileTime.fromMillis(1));
        Path second = Path.of(cache.resolve(target("lx:kw:2"))
                .get(5, TimeUnit.SECONDS));

        assertFalse(Files.exists(first));
        assertTrue(Files.exists(second));
        assertFalse(cache.isCached(target("lx:kw:1")));
        assertTrue(cache.isCached(target("lx:kw:2")));
        assertEquals(new OnlineAudioCache.CacheStats(1, audio.length),
                cache.statsAsync().get(5, TimeUnit.SECONDS));

        cache.resolve(target("lx:kw:1")).get(5, TimeUnit.SECONDS);
        assertEquals(3, requests.get());
    }

    @Test
    void invalidatePreventsAnOlderDownloadFromPublishingItsFile() throws Exception {
        byte[] audio = "stale-audio".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/invalidate", exchange -> {
            requestStarted.countDown();
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
                exchange.sendResponseHeaders(200, audio.length);
                exchange.getResponseBody().write(audio);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/invalidate";
        cache = cache(track -> CompletableFuture.completedFuture(url), 1024);

        MusicTrack track = onlineTrack();
        CompletableFuture<String> stale = cache.resolve(target(track));
        assertTrue(requestStarted.await(5, TimeUnit.SECONDS));
        cache.invalidate(target(track));
        assertFalse(cache.isCached(target(track)));
        releaseResponse.countDown();

        assertFalse(stale.handle((path, error) -> error == null).get(5, TimeUnit.SECONDS));
        for (int attempt = 0; attempt < 50
                && !cache.statsAsync().get(5, TimeUnit.SECONDS)
                .equals(new OnlineAudioCache.CacheStats(0, 0)); attempt++) {
            Thread.sleep(20);
        }
        assertEquals(new OnlineAudioCache.CacheStats(0, 0),
                cache.statsAsync().get(5, TimeUnit.SECONDS));
    }

    @Test
    void invalidateAbortsAStalledHttpBodyAndRemovesItsPartFile() throws Exception {
        CountDownLatch bodyStarted = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stalled-invalidate", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[]{1, 2, 3});
            exchange.getResponseBody().flush();
            bodyStarted.countDown();
            try {
                releaseServer.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/stalled-invalidate";
        cache = cache(track -> CompletableFuture.completedFuture(url), 1024);
        MusicTrack track = onlineTrack();
        CompletableFuture<String> resolving = cache.resolve(target(track));
        assertTrue(bodyStarted.await(5, TimeUnit.SECONDS));

        cache.invalidate(target(track));

        assertFalse(resolving.handle((path, error) -> error == null).get(1, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (hasPartFile() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(hasPartFile());
        releaseServer.countDown();
    }

    @Test
    void clearWaitsForStalledDownloadTerminationBeforeDeletingCache() throws Exception {
        CountDownLatch bodyStarted = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stalled-clear", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[]{1, 2, 3});
            exchange.getResponseBody().flush();
            bodyStarted.countDown();
            try {
                releaseServer.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/stalled-clear";
        cache = cache(track -> CompletableFuture.completedFuture(url), 1024);

        CompletableFuture<String> resolving = cache.resolve(target());
        assertTrue(bodyStarted.await(5, TimeUnit.SECONDS));

        assertEquals(new OnlineAudioCache.CacheStats(0, 0),
                cache.clearAsync().get(5, TimeUnit.SECONDS));
        assertFalse(resolving.handle((path, error) -> error == null).get(1, TimeUnit.SECONDS));
        assertFalse(hasPartFile());
        assertFalse(cache.isCached(target()));
        releaseServer.countDown();
    }

    @Test
    void clearAlsoWaitsForARecentlyInvalidatedDownload() throws Exception {
        CountDownLatch bodyStarted = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/invalidate-then-clear", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[]{1, 2, 3});
            exchange.getResponseBody().flush();
            bodyStarted.countDown();
            try {
                releaseServer.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/invalidate-then-clear";
        cache = cache(track -> CompletableFuture.completedFuture(url), 1024);
        MusicTrack track = onlineTrack();

        CompletableFuture<String> resolving = cache.resolve(target(track));
        assertTrue(bodyStarted.await(5, TimeUnit.SECONDS));
        cache.invalidate(target(track));

        assertEquals(new OnlineAudioCache.CacheStats(0, 0),
                cache.clearAsync().get(5, TimeUnit.SECONDS));
        assertFalse(resolving.handle((path, error) -> error == null).get(1, TimeUnit.SECONDS));
        assertFalse(hasPartFile());
        releaseServer.countDown();
    }

    @Test
    void activePlaybackLeasePreventsQuotaEvictionUntilReleased() throws Exception {
        byte[] audio = new byte[6];
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio", exchange -> respond(exchange, audio, requests));
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/audio";
        cache = new OnlineAudioCache(directory,
                track -> CompletableFuture.completedFuture(url), ignored -> {},
                8, 10, Duration.ofSeconds(3));

        OnlineAudioCache.CachedAudio active = cache.acquire(target("lx:kw:1"))
                .get(5, TimeUnit.SECONDS);

        assertFalse(cache.resolve(target("lx:kw:2"))
                .handle((path, error) -> error == null).get(5, TimeUnit.SECONDS));
        assertTrue(Files.exists(Path.of(active.identifier())));

        active.close();
        Path second = Path.of(cache.resolve(target("lx:kw:2"))
                .get(5, TimeUnit.SECONDS));
        assertTrue(Files.exists(second));
        assertFalse(Files.exists(Path.of(active.identifier())));
    }

    @Test
    void clearDefersActiveEntryDeletionUntilLeaseCloses() throws Exception {
        byte[] audio = "leased".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String url = serve("/leased", audio, new AtomicInteger());
        cache = cache(track -> CompletableFuture.completedFuture(url), 1024);
        OnlineAudioCache.CachedAudio active = cache.acquire(target())
                .get(5, TimeUnit.SECONDS);
        Path path = Path.of(active.identifier());

        assertEquals(new OnlineAudioCache.CacheStats(1, audio.length),
                cache.clearAsync().get(5, TimeUnit.SECONDS));
        assertTrue(Files.exists(path));
        assertFalse(cache.isCached(target()));

        active.close();
        assertFalse(Files.exists(path));
    }

    @Test
    void sameTrackIdWithChangedPlaybackTargetUsesFreshAudio() throws Exception {
        byte[] firstAudio = "first-version".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] secondAudio = "second-version".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/first", exchange -> respond(exchange, firstAudio, requests));
        server.createContext("/second", exchange -> respond(exchange, secondAudio, requests));
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        cache = cache(track -> CompletableFuture.completedFuture(
                baseUrl + (track.musicInfoJson().contains("first")
                        ? "/first" : "/second")), 1024);

        MusicTrack first = onlineTrack("lx:kw:1", "1", "first");
        MusicTrack changed = onlineTrack("lx:kw:1", "1", "second");
        Path firstPath = Path.of(cache.resolve(target(first)).get(5, TimeUnit.SECONDS));
        Path changedPath = Path.of(cache.resolve(target(changed)).get(5, TimeUnit.SECONDS));

        assertNotEquals(firstPath, changedPath);
        assertArrayEquals(firstAudio, Files.readAllBytes(firstPath));
        assertArrayEquals(secondAudio, Files.readAllBytes(changedPath));
        assertEquals(2, requests.get());
    }

    @Test
    void closeAlwaysCompletesAnOutstandingClearOperation() throws Exception {
        cache = cache(track -> CompletableFuture.failedFuture(
                new AssertionError("clear does not resolve music")), 1024);

        CompletableFuture<OnlineAudioCache.CacheStats> clearing = cache.clearAsync();
        cache.close();

        assertTrue(clearing.handle((stats, error) -> true).get(5, TimeUnit.SECONDS));
    }

    private OnlineAudioCache cache(LxTrackResolver upstream, long maximumBytes) {
        return new OnlineAudioCache(directory, upstream, ignored -> {}, maximumBytes,
                Duration.ofSeconds(3));
    }

    private String serve(String path, byte[] body, AtomicInteger requests) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static MusicTrack onlineTrack() {
        return onlineTrack("lx:kw:1");
    }

    private static MusicTrack onlineTrack(String id) {
        String songId = id.substring(id.lastIndexOf(':') + 1);
        return onlineTrack(id, songId, "kw_" + songId);
    }

    private static MusicTrack onlineTrack(String id, String songId, String metadata) {
        return new MusicTrack(id,
                new TrackDetails("Online", "Artist", null, "MP3",
                        new AudioProperties(null, null, Duration.ofMinutes(3))),
                new TrackTarget.Lx("kw", songId, List.of("320k"),
                        "{\"id\":\"" + metadata + "\",\"source\":\"kw\",\"meta\":{\"songId\":\""
                                + songId + "\"}}"));
    }

    private static TrackTarget.Lx target(MusicTrack track) {
        return (TrackTarget.Lx) track.target();
    }

    private static TrackTarget.Lx target() {
        return target(onlineTrack());
    }

    private static TrackTarget.Lx target(String id) {
        return target(onlineTrack(id));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body,
                                AtomicInteger requests) throws java.io.IOException {
        requests.incrementAndGet();
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private boolean hasPartFile() throws java.io.IOException {
        try (var paths = Files.list(directory)) {
            return paths.anyMatch(path -> path.getFileName().toString().endsWith(".part"));
        }
    }
}
