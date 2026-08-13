package org.encinet.mik.module.music.online;

import com.sun.net.httpserver.HttpServer;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicPlaybackStats;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    void retriesTransientServerFailureThenFallsBackToAnotherProvider() throws Exception {
        byte[] audio = "fallback-audio".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AtomicInteger primaryRequests = new AtomicInteger();
        AtomicInteger fallbackRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/primary", exchange -> {
            primaryRequests.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.createContext("/fallback", exchange -> {
            fallbackRequests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, audio.length);
            exchange.getResponseBody().write(audio);
            exchange.close();
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        List<Set<String>> exclusions = new CopyOnWriteArrayList<>();
        List<String> failedProviders = new CopyOnWriteArrayList<>();
        List<String> successfulProviders = new CopyOnWriteArrayList<>();
        List<String> warnings = new CopyOnWriteArrayList<>();
        LxTrackResolver upstream = new LxTrackResolver() {
            @Override
            public CompletableFuture<String> resolve(TrackTarget.Lx target) {
                return resolveCandidate(target, Set.of())
                        .thenApply(LxTrackResolver.Resolution::url);
            }

            @Override
            public CompletableFuture<Resolution> resolveCandidate(
                    TrackTarget.Lx target, Set<String> excludedProviderIds) {
                exclusions.add(Set.copyOf(excludedProviderIds));
                return CompletableFuture.completedFuture(excludedProviderIds.contains("primary")
                        ? new Resolution(baseUrl + "/fallback", "fallback")
                        : new Resolution(baseUrl + "/primary", "primary"));
            }

            @Override
            public void candidateFailed(
                    TrackTarget.Lx target, Resolution resolution, Throwable error) {
                failedProviders.add(resolution.providerId());
            }

            @Override
            public void candidateSucceeded(TrackTarget.Lx target, Resolution resolution) {
                successfulProviders.add(resolution.providerId());
            }
        };
        cache = new OnlineAudioCache(directory, upstream, warnings::add,
                1024, Duration.ofSeconds(3));

        Path cached = Path.of(cache.resolve(target()).get(5, TimeUnit.SECONDS));

        assertArrayEquals(audio, Files.readAllBytes(cached));
        assertEquals(2, primaryRequests.get());
        assertEquals(1, fallbackRequests.get());
        assertEquals(List.of(Set.of(), Set.of("primary")), exclusions);
        assertEquals(List.of("primary"), failedProviders);
        assertEquals(List.of("fallback"), successfulProviders);
        assertTrue(warnings.stream().anyMatch(message -> message.contains("retrying once")));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("trying another source")));
    }

    @Test
    void streamsInitialBytesBeforeTheDownloadCompletesAndWaitsAtTemporaryEof() throws Exception {
        byte[] first = new byte[]{1, 2, 3};
        byte[] second = new byte[]{4, 5, 6};
        CountDownLatch firstChunkSent = new CountDownLatch(1);
        CountDownLatch releaseSecondChunk = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/progressive", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg; charset=binary");
            exchange.sendResponseHeaders(200, first.length + second.length);
            exchange.getResponseBody().write(first);
            exchange.getResponseBody().flush();
            firstChunkSent.countDown();
            try {
                releaseSecondChunk.await(5, TimeUnit.SECONDS);
                exchange.getResponseBody().write(second);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/progressive";
        cache = cache(ignored -> CompletableFuture.completedFuture(url), 1024);

        CompletableFuture<OnlineAudioCache.StreamingAudio> acquiring =
                cache.acquireStreaming(target());
        assertTrue(firstChunkSent.await(5, TimeUnit.SECONDS));
        OnlineAudioCache.StreamingAudio streaming = acquiring.get(1, TimeUnit.SECONDS);
        assertEquals(6, streaming.contentLength());
        assertEquals("audio/mpeg", streaming.contentType());
        assertFalse(cache.isCached(target()));

        try (streaming; OnlineAudioCache.StreamingAudio.Reader reader = streaming.openReader()) {
            byte[] initial = new byte[3];
            assertEquals(3, reader.read(initial, 0, initial.length));
            assertArrayEquals(first, initial);

            CompletableFuture<Integer> waitingRead = readAsync(reader);
            assertThrows(TimeoutException.class,
                    () -> waitingRead.get(150, TimeUnit.MILLISECONDS));
            releaseSecondChunk.countDown();
            assertEquals(4, waitingRead.get(5, TimeUnit.SECONDS));
            byte[] tail = new byte[2];
            assertEquals(2, reader.read(tail, 0, tail.length));
            assertArrayEquals(new byte[]{5, 6}, tail);
            assertEquals(-1, reader.read());
        } finally {
            releaseSecondChunk.countDown();
        }

        Path completed = Path.of(cache.resolve(target()).get(5, TimeUnit.SECONDS));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, Files.readAllBytes(completed));
        assertTrue(cache.isCached(target()));
        assertEquals(1, requests.get());
    }

    @Test
    void marksAnExposedStreamingTransportFailureForPlaybackRetry() throws Exception {
        CountDownLatch firstChunkSent = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/broken-stream", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[]{1, 2, 3});
            exchange.getResponseBody().flush();
            firstChunkSent.countDown();
            try {
                releaseServer.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/broken-stream";
        cache = new OnlineAudioCache(directory,
                ignored -> CompletableFuture.completedFuture(url), ignored -> {},
                1024, Duration.ofMillis(150));

        OnlineAudioCache.StreamingAudio streaming = cache.acquireStreaming(target())
                .get(5, TimeUnit.SECONDS);
        assertTrue(firstChunkSent.await(5, TimeUnit.SECONDS));
        try (streaming; OnlineAudioCache.StreamingAudio.Reader reader = streaming.openReader()) {
            byte[] initial = new byte[3];
            assertEquals(3, reader.read(initial, 0, initial.length));

            IOException failure = assertThrows(IOException.class, reader::read);

            assertTrue(OnlineAudioCache.isRecoverableRemoteFailure(failure));
        } finally {
            releaseServer.countDown();
        }
    }

    @Test
    void concurrentStreamingReadersShareOneDownload() throws Exception {
        byte[] audio = "shared-progressive-audio".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        String url = serve("/shared-progressive", audio, requests);
        cache = cache(ignored -> CompletableFuture.completedFuture(url), 1024);

        OnlineAudioCache.StreamingAudio first = cache.acquireStreaming(target())
                .get(5, TimeUnit.SECONDS);
        OnlineAudioCache.StreamingAudio second = cache.acquireStreaming(target())
                .get(5, TimeUnit.SECONDS);
        try (first; second;
             OnlineAudioCache.StreamingAudio.Reader firstReader = first.openReader();
             OnlineAudioCache.StreamingAudio.Reader secondReader = second.openReader()) {
            byte[] firstBytes = new byte[audio.length];
            byte[] secondBytes = new byte[audio.length];
            assertEquals(audio.length, firstReader.read(firstBytes, 0, firstBytes.length));
            assertEquals(audio.length, secondReader.read(secondBytes, 0, secondBytes.length));
            assertArrayEquals(audio, firstBytes);
            assertArrayEquals(audio, secondBytes);
        }
        assertEquals(1, requests.get());
    }

    @Test
    void reopensReaderChannelAfterLavaplayerSeekInterruptClosesIt() throws Exception {
        byte[] audio = "seekable-progressive-audio"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String url = serve("/seekable-progressive", audio, new AtomicInteger());
        cache = cache(ignored -> CompletableFuture.completedFuture(url), 1024);

        OnlineAudioCache.StreamingAudio streaming = cache.acquireStreaming(target())
                .get(5, TimeUnit.SECONDS);
        try (streaming; OnlineAudioCache.StreamingAudio.Reader reader = streaming.openReader()) {
            assertEquals(Byte.toUnsignedInt(audio[0]), reader.read());

            var channelField = reader.getClass().getDeclaredField("channel");
            channelField.setAccessible(true);
            ((FileChannel) channelField.get(reader)).close();

            reader.seek(0);
            byte[] replayed = new byte[audio.length];
            assertEquals(audio.length, reader.read(replayed, 0, replayed.length));
            assertArrayEquals(audio, replayed);
        }
    }

    @Test
    void invalidateWakesAReaderBlockedAtTemporaryEof() throws Exception {
        CountDownLatch firstChunkSent = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reader-cancel", exchange -> {
            exchange.sendResponseHeaders(200, 6);
            exchange.getResponseBody().write(new byte[]{1, 2, 3});
            exchange.getResponseBody().flush();
            firstChunkSent.countDown();
            try {
                releaseServer.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/reader-cancel";
        cache = cache(ignored -> CompletableFuture.completedFuture(url), 1024);

        OnlineAudioCache.StreamingAudio streaming = cache.acquireStreaming(target())
                .get(5, TimeUnit.SECONDS);
        assertTrue(firstChunkSent.await(5, TimeUnit.SECONDS));
        try (streaming; OnlineAudioCache.StreamingAudio.Reader reader = streaming.openReader()) {
            byte[] initial = new byte[3];
            assertEquals(3, reader.read(initial, 0, initial.length));
            CompletableFuture<Integer> waitingRead = readAsync(reader);

            cache.invalidate(target());

            assertFalse(waitingRead.handle((value, error) -> error == null)
                    .get(1, TimeUnit.SECONDS));
        } finally {
            releaseServer.countDown();
        }
    }

    @Test
    void restoresIndexedCachedTracksAfterRestartAndRemovesThemWhenCleared() throws Exception {
        byte[] audio = "indexed-cache".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String url = serve("/indexed", audio, new AtomicInteger());
        MusicTrack track = onlineTrack();
        cache = cache(ignored -> CompletableFuture.completedFuture(url), 1024);

        cache.resolve(target(track)).get(5, TimeUnit.SECONDS);
        assertTrue(cache.indexAsync(track).get(5, TimeUnit.SECONDS));
        assertEquals(List.of(track), cache.cachedTracks());

        cache.close();
        cache = cache(ignored -> CompletableFuture.failedFuture(
                new AssertionError("cached playback must not resolve the source URL")), 1024);

        assertEquals(List.of(track), cache.cachedTracks());
        assertEquals(new OnlineAudioCache.CacheStats(0, 0),
                cache.clearAsync().get(5, TimeUnit.SECONDS));
        assertEquals(List.of(), cache.cachedTracks());
    }

    @Test
    void rejectsCorruptOrMismatchedCachedTrackMetadata() throws Exception {
        byte[] audio = "cached".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String url = serve("/metadata", audio, new AtomicInteger());
        MusicTrack track = onlineTrack();
        cache = cache(ignored -> CompletableFuture.completedFuture(url), 1024);

        cache.resolve(target(track)).get(5, TimeUnit.SECONDS);
        String key = OnlineAudioCache.cacheKey(target(track));
        Files.writeString(directory.resolve(key + ".track.json"), "{not-json");

        cache.close();
        cache = cache(ignored -> CompletableFuture.failedFuture(
                new AssertionError("cache startup must not resolve music")), 1024);

        assertTrue(cache.isCached(target(track)));
        assertEquals(List.of(), cache.cachedTracks());
        assertFalse(Files.exists(directory.resolve(key + ".track.json")));
    }

    @Test
    void indexFailureDoesNotInvalidateOtherwisePlayableCachedAudio() throws Exception {
        byte[] audio = "playable".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String url = serve("/unindexable", audio, new AtomicInteger());
        MusicTrack track = new MusicTrack("lx:kw:1",
                new TrackDetails("Bad\nTitle", "Artist", null, "LX/KW", AudioProperties.EMPTY),
                target());
        cache = cache(ignored -> CompletableFuture.completedFuture(url), 1024);

        String path = cache.resolve(target()).get(5, TimeUnit.SECONDS);

        assertFalse(cache.indexAsync(track).get(5, TimeUnit.SECONDS));
        assertTrue(cache.isCached(target()));
        assertEquals(path, cache.resolve(target()).get(5, TimeUnit.SECONDS));
        assertEquals(List.of(), cache.cachedTracks());
    }

    @Test
    void quotaEvictionRemovesCachedTrackFromRandomCandidates() throws Exception {
        byte[] audio = new byte[6];
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/one", exchange -> respond(exchange, audio, requests));
        server.createContext("/two", exchange -> respond(exchange, audio, requests));
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        cache = new OnlineAudioCache(directory, target -> CompletableFuture.completedFuture(
                baseUrl + (target.songId().equals("1") ? "/one" : "/two")), ignored -> {},
                8, 10, Duration.ofSeconds(3));
        MusicTrack first = onlineTrack("lx:kw:1");
        MusicTrack second = onlineTrack("lx:kw:2");

        cache.resolve(target(first)).get(5, TimeUnit.SECONDS);
        assertTrue(cache.indexAsync(first).get(5, TimeUnit.SECONDS));
        cache.resolve(target(second)).get(5, TimeUnit.SECONDS);
        assertTrue(cache.indexAsync(second).get(5, TimeUnit.SECONDS));

        assertEquals(List.of(second), cache.cachedTracks());
    }

    @Test
    void quotaEvictionKeepsFrequentlyPlayedCacheOverColdCache() throws Exception {
        byte[] audio = new byte[4];
        String url = serve("/popular", audio, new AtomicInteger());
        MusicTrack popular = onlineTrack("lx:kw:1");
        MusicTrack cold = onlineTrack("lx:kw:2");
        MusicTrack incoming = onlineTrack("lx:kw:3");
        MusicPlaybackStats stats = trackId -> trackId.equals(popular.id())
                ? new MusicPlaybackStats.TrackStats(100, Instant.now())
                : MusicPlaybackStats.TrackStats.EMPTY;
        cache = new OnlineAudioCache(directory,
                ignored -> CompletableFuture.completedFuture(url), ignored -> {},
                4, 8, Duration.ofSeconds(3), stats);

        cache.resolve(target(popular)).get(5, TimeUnit.SECONDS);
        assertTrue(cache.indexAsync(popular).get(5, TimeUnit.SECONDS));
        cache.resolve(target(cold)).get(5, TimeUnit.SECONDS);
        assertTrue(cache.indexAsync(cold).get(5, TimeUnit.SECONDS));
        cache.resolve(target(incoming)).get(5, TimeUnit.SECONDS);
        assertTrue(cache.indexAsync(incoming).get(5, TimeUnit.SECONDS));

        assertTrue(cache.isCached(target(popular)));
        assertFalse(cache.isCached(target(cold)));
        assertTrue(cache.isCached(target(incoming)));
        assertEquals(List.of(popular, incoming), cache.cachedTracks());
    }

    @Test
    void rejectsResponsesThatEndBeforeTheirDeclaredLength() throws Exception {
        byte[] partialAudio = new byte[]{1, 2, 3, 4};
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/truncated", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, partialAudio.length + 16L);
            exchange.getResponseBody().write(partialAudio);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/truncated";
        cache = cache(ignored -> CompletableFuture.completedFuture(url), 1024);

        CompletionException failure = assertThrows(CompletionException.class,
                () -> cache.resolve(target()).join());
        assertTrue(hasCause(failure, EOFException.class));
        assertFalse(cache.isCached(target()));
        assertEquals(new OnlineAudioCache.CacheStats(0, 0),
                cache.statsAsync().get(5, TimeUnit.SECONDS));
        assertTrue(requests.get() >= 1);
        try (var files = Files.list(directory)) {
            assertTrue(files.findAny().isEmpty());
        }
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
                1024, 16 * 1024, Duration.ofSeconds(3), Duration.ofMillis(150),
                MusicPlaybackStats.EMPTY);

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
                OnlineAudioCache.class.getMethod("acquireStreaming", TrackTarget.Lx.class)
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
    void invalidatedActiveEntryIsDeletedOnReleaseAndDownloadedFresh() throws Exception {
        byte[] corrupt = "corrupt-cache".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] fresh = "fresh-audio".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/recover", exchange -> {
            byte[] body = requests.incrementAndGet() == 1 ? corrupt : fresh;
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/recover";
        cache = cache(ignored -> CompletableFuture.completedFuture(url), 1024);

        OnlineAudioCache.CachedAudio active = cache.acquire(target())
                .get(5, TimeUnit.SECONDS);
        Path corruptPath = Path.of(active.identifier());
        assertArrayEquals(corrupt, Files.readAllBytes(corruptPath));

        cache.invalidate(target());
        assertFalse(cache.isCached(target()));
        assertTrue(Files.exists(corruptPath));
        active.close();

        assertFalse(Files.exists(corruptPath));
        Path recoveredPath = Path.of(cache.resolve(target()).get(5, TimeUnit.SECONDS));
        assertArrayEquals(fresh, Files.readAllBytes(recoveredPath));
        assertEquals(2, requests.get());
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
    void clearPreventsDelayedMetadataIndexFromRepublishingAnActiveEntry() throws Exception {
        byte[] audio = "leased-index".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String url = serve("/leased-index", audio, new AtomicInteger());
        MusicTrack track = onlineTrack();
        cache = cache(ignored -> CompletableFuture.completedFuture(url), 1024);
        OnlineAudioCache.CachedAudio active = cache.acquire(target(track))
                .get(5, TimeUnit.SECONDS);

        cache.clearAsync().get(5, TimeUnit.SECONDS);

        assertFalse(cache.indexAsync(track).get(5, TimeUnit.SECONDS));
        assertEquals(List.of(), cache.cachedTracks());
        assertFalse(cache.isCached(target(track)));
        active.close();
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

    private static CompletableFuture<Integer> readAsync(
            OnlineAudioCache.StreamingAudio.Reader reader) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return reader.read();
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }
}
