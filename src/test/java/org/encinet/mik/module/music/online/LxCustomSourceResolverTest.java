package org.encinet.mik.module.music.online;

import com.sun.net.httpserver.HttpServer;
import com.google.gson.JsonParser;
import org.encinet.mik.module.music.online.LxMusicTrackMapper;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LxCustomSourceResolverTest {

    @TempDir
    Path directory;

    private LxCustomSourceResolver resolver;
    private HttpServer server;
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();

    @AfterEach
    void close() {
        if (resolver != null) resolver.close();
        if (server != null) server.stop(0);
    }

    @Test
    void loadsOfficialStyleScriptAndResolvesMusicUrl() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/url", exchange -> {
            byte[] response = "{\"code\":200,\"url\":\"https://cdn.example/song.mp3\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        Files.writeString(localDirectory().resolve("source.js"), officialStyleScript(
                "http://127.0.0.1:" + server.getAddress().getPort()));

        resolver = resolver();

        assertEquals(1, resolver.statuses().size(), String.join("\n", warnings));
        assertEquals("https://cdn.example/song.mp3",
                resolver.resolve(target()).get(5, TimeUnit.SECONDS));
        assertTrue(resolver.statuses().getFirst().available());
        assertEquals(List.of("128k", "320k"),
                resolver.statuses().getFirst().capabilities().get("kw"));
    }

    @Test
    void passesCurrentOfficialMusicInfoShapeAndLegacyAliasesToScript() throws Exception {
        Files.writeString(localDirectory().resolve("current-shape.js"), """
                /*! * @name Current Shape */
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, ({ action, info }) => {
                  if (action !== 'musicUrl') throw new Error('unsupported')
                  const music = info.musicInfo
                  if (music.id !== 'tx_42' || music.source !== 'tx'
                      || music.meta.songId !== '42' || music.meta.strMediaMid !== 'media-42'
                      || music.songmid !== '42' || music.strMediaMid !== 'media-42') {
                    throw new Error('invalid MusicInfo shape')
                  }
                  return `https://cdn.example/${music.meta.songId}.mp3`
                })
                send(EVENT_NAMES.inited, { status: true, sources: {
                  tx: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                }})
                """);
        MusicTrack track = new LxMusicTrackMapper().parse(JsonParser.parseString("""
                {
                  "id":"tx_42","name":"Current","singer":"Singer","source":"tx",
                  "meta":{"songId":"42","albumName":"Album",
                    "qualitys":[{"type":"320k"}],"_qualitys":{"320k":{}},
                    "strMediaMid":"media-42","id":4242,"albumMid":"album-mid-42"}
                }
                """));
        resolver = resolver();

        assertEquals("https://cdn.example/42.mp3",
                resolver.resolve(target(track)).get(5, TimeUnit.SECONDS));
    }

    @Test
    void invokesDeclaredLyricActionWithCompleteMusicInfo() throws Exception {
        Files.writeString(localDirectory().resolve("lyrics.js"), """
                /*! * @name Lyrics */
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, ({ action, source, info }) => {
                  if (action === 'musicUrl') return 'https://cdn.example/song.mp3'
                  if (action !== 'lyric' || source !== 'kw'
                      || info.musicInfo.songmid !== '1'
                      || info.musicInfo.name !== 'Song') throw new Error('invalid lyric request')
                  return { lyric: '[00:01.00]Original',
                    tlyric: '[00:01.00]Translated', rlyric: '[00:01.00]Romanized' }
                })
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl', 'lyric'], qualitys: ['320k'] }
                }})
                """);
        resolver = resolver();

        var result = resolver.lyrics(target()).get(5, TimeUnit.SECONDS).getAsJsonObject();

        assertEquals("[00:01.00]Original", result.get("lyric").getAsString());
        assertEquals("[00:01.00]Translated", result.get("tlyric").getAsString());
        assertTrue(resolver.statuses().getFirst().actions().get("kw").contains("lyric"));
    }

    @Test
    void fallsBackToNextLyricSourceAfterEmptyResponse() throws Exception {
        Files.writeString(localDirectory().resolve("first.js"), lyricScript("First", "{}"));
        Files.writeString(localDirectory().resolve("second.js"),
                lyricScript("Second", "{ lyric: '[00:01.00]Found' }"));
        resolver = resolver();

        var result = resolver.lyrics(target()).get(5, TimeUnit.SECONDS).getAsJsonObject();

        assertEquals("[00:01.00]Found", result.get("lyric").getAsString());
    }

    @Test
    void aggregatesSourcesInPathOrderAndFallsBackAfterFailure() throws Exception {
        Files.writeString(localDirectory().resolve("first.js"), rejectingScript("First"));
        Files.writeString(localDirectory().resolve("second.js"), resolvingScript("Second",
                "https://cdn.example/fallback.mp3"));

        resolver = resolver();

        assertEquals(2, resolver.statuses().size(), String.join("\n", warnings));
        assertEquals("https://cdn.example/fallback.mp3",
                resolver.resolve(target()).get(5, TimeUnit.SECONDS));
        assertEquals(1, resolver.statuses().getFirst().consecutiveFailures());
        assertEquals("Second", resolver.statuses().get(1).name());
    }

    @Test
    void downloadFailureTemporarilyPrefersAnotherProvider() throws Exception {
        Files.writeString(localDirectory().resolve("first.js"), resolvingScript("First",
                "https://cdn.example/first.mp3"));
        Files.writeString(localDirectory().resolve("second.js"), resolvingScript("Second",
                "https://cdn.example/second.mp3"));
        resolver = resolver();

        LxTrackResolver.Resolution first = resolver.resolveCandidate(target(), Set.of())
                .get(5, TimeUnit.SECONDS);
        LxTrackResolver.Resolution explicitlyExcluded = resolver.resolveCandidate(
                        target(), Set.of(first.providerId()))
                .get(5, TimeUnit.SECONDS);
        resolver.candidateFailed(target(), first, new java.io.IOException("CDN unavailable"));
        LxTrackResolver.Resolution fallback = resolver.resolveCandidate(target(), Set.of())
                .get(5, TimeUnit.SECONDS);

        assertEquals("local/first.js", first.providerId());
        assertEquals("https://cdn.example/first.mp3", first.url());
        assertEquals("local/second.js", explicitlyExcluded.providerId());
        assertEquals("local/second.js", fallback.providerId());
        assertEquals("https://cdn.example/second.mp3", fallback.url());
        assertEquals(1, resolver.statuses().getFirst().consecutiveFailures());

        resolver.candidateSucceeded(target(), first);

        assertEquals("local/first.js", resolver.resolveCandidate(target(), Set.of())
                .get(5, TimeUnit.SECONDS).providerId());
        assertEquals(0, resolver.statuses().getFirst().consecutiveFailures());
    }

    @Test
    void loadsLocalAndManagedRemoteScriptsButIgnoresLegacyAndUnmanagedPaths() throws Exception {
        Files.writeString(directory.resolve("legacy.js"), resolvingScript("Legacy",
                "https://cdn.example/legacy.mp3"));
        Files.writeString(localDirectory().resolve("local.js"), resolvingScript("Local",
                "https://cdn.example/local.mp3"));
        Path remote = Files.createDirectories(directory.resolve("remote"));
        Files.writeString(remote.resolve("remote-0123456789abcdef01234567.js"),
                resolvingScript("Remote", "https://cdn.example/remote.mp3"));
        Files.writeString(remote.resolve("manual.js"), resolvingScript("Unmanaged",
                "https://cdn.example/manual.mp3"));

        resolver = resolver();

        assertEquals(List.of("local/local.js", "remote/remote-0123456789abcdef01234567.js"),
                resolver.statuses().stream().map(LxCustomSourceResolver.SourceStatus::id).toList());
    }

    @Test
    void productionRemoteWhitelistExcludesUnregisteredHashNamedSnapshots() throws Exception {
        Path remote = Files.createDirectories(directory.resolve("remote"));
        String managed = "remote-0123456789abcdef01234567.js";
        String orphan = "remote-fedcba9876543210fedcba98.js";
        Files.writeString(remote.resolve(managed), resolvingScript("Managed",
                "https://cdn.example/managed.mp3"));
        Files.writeString(remote.resolve(orphan), resolvingScript("Orphan",
                "https://cdn.example/orphan.mp3"));
        resolver = new LxCustomSourceResolver(directory, ignored -> {}, warnings::add,
                () -> java.util.Set.of(managed));

        resolver.reload();

        assertEquals(List.of("remote/" + managed),
                resolver.statuses().stream().map(LxCustomSourceResolver.SourceStatus::id).toList());
    }

    @Test
    void opensCircuitAfterRepeatedSourceFailuresAndSkipsItDuringRetryWindow() throws Exception {
        Files.writeString(localDirectory().resolve("first.js"), rejectingScript("First"));
        Files.writeString(localDirectory().resolve("second.js"), resolvingScript("Second",
                "https://cdn.example/fallback.mp3"));

        resolver = resolver(Duration.ofSeconds(3), 2, Duration.ofSeconds(30));

        assertEquals("https://cdn.example/fallback.mp3", resolver.resolve(target()).get(5, TimeUnit.SECONDS));
        assertEquals("https://cdn.example/fallback.mp3", resolver.resolve(target()).get(5, TimeUnit.SECONDS));

        LxCustomSourceResolver.SourceStatus first = resolver.statuses().getFirst();
        assertFalse(first.available());
        assertEquals(2, first.consecutiveFailures());
        assertTrue(first.retryAt().isAfter(java.time.Instant.now()));

        assertEquals("https://cdn.example/fallback.mp3", resolver.resolve(target()).get(5, TimeUnit.SECONDS));
        assertEquals(2, resolver.statuses().getFirst().consecutiveFailures());
    }

    @Test
    void singleTimeoutKeepsSourceAvailableAndA_successfulRetryClearsFailure() throws Exception {
        Files.writeString(localDirectory().resolve("first.js"), retryAfterTimeoutScript("First"));
        Files.writeString(localDirectory().resolve("second.js"), resolvingScript("Second",
                "https://cdn.example/fallback.mp3"));

        resolver = resolver(Duration.ofMillis(250),
                2, Duration.ofMillis(150));

        assertEquals("https://cdn.example/fallback.mp3",
                resolver.resolve(target()).get(3, TimeUnit.SECONDS));
        LxCustomSourceResolver.SourceStatus afterTimeout = resolver.statuses().getFirst();
        assertTrue(afterTimeout.available());
        assertEquals(1, afterTimeout.consecutiveFailures());
        assertEquals(null, afterTimeout.retryAt());

        assertEquals("https://cdn.example/retry.mp3",
                resolver.resolve(target()).get(3, TimeUnit.SECONDS));
        assertEquals(0, resolver.statuses().getFirst().consecutiveFailures());
    }

    @Test
    void repeatedTimeoutsCloseSourceAtThresholdAndReloadAfterRetryDelay() throws Exception {
        Path first = localDirectory().resolve("first.js");
        Files.writeString(first, pendingScript("First"));
        Files.writeString(localDirectory().resolve("second.js"), resolvingScript("Second",
                "https://cdn.example/fallback.mp3"));
        resolver = resolver(Duration.ofMillis(150), 2, Duration.ofMillis(150));

        assertEquals("https://cdn.example/fallback.mp3",
                resolver.resolve(target()).get(3, TimeUnit.SECONDS));
        assertTrue(resolver.statuses().getFirst().available());
        assertEquals("https://cdn.example/fallback.mp3",
                resolver.resolve(target()).get(3, TimeUnit.SECONDS));

        LxCustomSourceResolver.SourceStatus isolated = resolver.statuses().getFirst();
        assertFalse(isolated.available());
        assertEquals(2, isolated.consecutiveFailures());
        assertTrue(isolated.retryAt().isAfter(java.time.Instant.now()));

        Files.writeString(first, resolvingScript("First recovered",
                "https://cdn.example/recovered.mp3"));
        waitUntilFirstSourceAvailable();

        assertEquals("https://cdn.example/recovered.mp3",
                resolver.resolve(target()).get(3, TimeUnit.SECONDS));
        assertEquals(0, resolver.statuses().getFirst().consecutiveFailures());
    }

    @Test
    void reloadFailsRequestsOwnedByTheReplacedRuntimeWithoutWaitingForTimeout() throws Exception {
        Path source = localDirectory().resolve("source.js");
        Files.writeString(source, pendingScript("Pending"));
        resolver = resolver(Duration.ofSeconds(5), 2, Duration.ofSeconds(10));
        var pending = resolver.resolve(target());

        Files.writeString(source, resolvingScript("Replacement",
                "https://cdn.example/replacement.mp3"));
        resolver.reload();

        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> pending.get(500, TimeUnit.MILLISECONDS));
        assertEquals("https://cdn.example/replacement.mp3",
                resolver.resolve(target()).get(3, TimeUnit.SECONDS));
    }

    @Test
    void unchangedReloadKeepsTheCurrentRuntimeAndPendingRequests() throws Exception {
        Files.writeString(localDirectory().resolve("source.js"), pendingScript("Pending"));
        resolver = resolver(Duration.ofSeconds(5), 2, Duration.ofSeconds(10));
        var pending = resolver.resolve(target());

        resolver.reload();

        Thread.sleep(100);
        assertFalse(pending.isDone());
    }

    @Test
    void failedDirectoryScanKeepsThePreviousSourceSnapshot() throws Exception {
        Path source = localDirectory().resolve("source.js");
        Files.writeString(source, resolvingScript("Stable",
                "https://cdn.example/stable.mp3"));
        resolver = resolver();

        Files.delete(source);
        Path localDirectory = directory.resolve("local");
        Files.delete(localDirectory);
        Files.writeString(localDirectory, "not a directory");

        assertThrows(CompletionException.class, resolver::reload);
        assertEquals("https://cdn.example/stable.mp3",
                resolver.resolve(target()).get(3, TimeUnit.SECONDS));
        assertEquals("Stable", resolver.statuses().getFirst().name());
    }

    @Test
    void closeInterruptsAStalledReloadWithoutWaitingForInitializationTimeout() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            requestStarted.countDown();
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        Files.writeString(localDirectory().resolve("stalled-init.js"), asyncInitializationScript(
                "http://127.0.0.1:" + server.getAddress().getPort()));
        resolver = new LxCustomSourceResolver(directory, ignored -> {}, warnings::add,
                Duration.ofSeconds(5), 2, Duration.ofSeconds(10));
        CompletableFuture<Void> reload = CompletableFuture.runAsync(resolver::reload);
        assertTrue(requestStarted.await(3, TimeUnit.SECONDS));

        resolver.close();

        assertTrue(reload.handle((ignored, error) -> true).get(500, TimeUnit.MILLISECONDS));
        assertTrue(reload.isCompletedExceptionally());
        releaseResponse.countDown();
    }

    @Test
    void bridgesFormAndFormDataRequestsForOfficialStyleSources() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/form", exchange -> respondWithRequestCapture(exchange,
                "https://cdn.example/form.mp3"));
        server.createContext("/multipart", exchange -> respondWithRequestCapture(exchange,
                "https://cdn.example/form-data.mp3"));
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        Files.writeString(localDirectory().resolve("request.js"), formRequestScript(baseUrl));

        resolver = resolver();

        assertEquals("https://cdn.example/form-data.mp3",
                resolver.resolve(target()).get(5, TimeUnit.SECONDS));
        assertEquals(List.of(
                "POST /form application/x-www-form-urlencoded title=Song&artist=Artist",
                "POST /multipart multipart/form-data abc123"), requests);
    }

    @Test
    void exposesOfficialScriptInfoAndPreservesBinaryRequestBodies() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/binary", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                    + " " + exchange.getRequestHeaders().getFirst("Content-Type") + " " + body);
            byte[] response = "{\"url\":\"https://cdn.example/binary.mp3\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        Files.writeString(localDirectory().resolve("metadata.js"), metadataAndBinaryScript(
                "http://127.0.0.1:" + server.getAddress().getPort()));

        resolver = resolver();

        assertEquals("Metadata Source", resolver.statuses().getFirst().name(),
                String.join("\n", warnings));
        assertEquals("https://cdn.example/binary.mp3",
                resolver.resolve(target()).get(5, TimeUnit.SECONDS));
        assertEquals(List.of("POST /binary application/octet-stream binary-payload"), requests);
    }

    @Test
    void rejectsScriptThatThrowsAfterDeclaringInitialization() throws Exception {
        Files.writeString(localDirectory().resolve("late-failure.js"), """
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, () => Promise.resolve('https://cdn.example/a.mp3'))
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                }})
                throw new Error('late initialization failure')
                """);

        resolver = resolver();

        assertEquals(1, resolver.statuses().size());
        assertFalse(resolver.statuses().getFirst().available());
        assertTrue(resolver.statuses().getFirst().lastError().contains("late initialization failure"));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("late initialization failure")));
    }

    @Test
    void waitsForAsynchronousAvailabilityCheckBeforeAcceptingSource() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        Files.writeString(localDirectory().resolve("async-init.js"), asyncInitializationScript(
                "http://127.0.0.1:" + server.getAddress().getPort()));

        resolver = resolver();

        assertEquals(1, resolver.statuses().size(), String.join("\n", warnings));
        assertEquals("https://cdn.example/async.mp3",
                resolver.resolve(target()).get(3, TimeUnit.SECONDS));
    }

    @Test
    void supportsBrowserStyleTimersDuringInitializationAndRequests() throws Exception {
        Files.writeString(localDirectory().resolve("timers.js"), """
                /*! * @name Timer Source */
                const { EVENT_NAMES, on, send } = globalThis.lx
                let intervalRuns = 0
                const discarded = window.setTimeout(
                  () => { throw new Error('cleared timer ran') }, 1)
                window.clearTimeout(discarded)
                const interval = lx.setInterval(() => {
                  intervalRuns++
                  lx.clearInterval(interval)
                }, 1)
                on(EVENT_NAMES.request, ({ action }) => new Promise((resolve, reject) => {
                  if (action !== 'musicUrl') return reject(new Error('unsupported'))
                  window.setTimeout((url) => intervalRuns > 0
                    ? resolve(url) : reject(new Error('interval did not run')),
                    10, 'https://cdn.example/timer.mp3')
                }))
                setTimeout(() => send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                }}), 10)
                """);

        resolver = resolver();

        assertEquals("https://cdn.example/timer.mp3",
                resolver.resolve(target()).get(3, TimeUnit.SECONDS));
        assertTrue(resolver.statuses().getFirst().available(), String.join("\n", warnings));
    }

    @Test
    void skipsScriptsThatFailAvailabilityCheck() throws Exception {
        Files.writeString(localDirectory().resolve("broken.js"), "throw new Error('broken source')");
        Files.writeString(localDirectory().resolve("unsupported.js"), """
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, () => Promise.resolve('https://cdn.example/a.mp3'))
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['lyric'], qualitys: ['320k'] }
                }})
                """);

        resolver = resolver();

        assertEquals(2, resolver.statuses().size());
        assertTrue(resolver.statuses().stream().noneMatch(
                LxCustomSourceResolver.SourceStatus::available));
        assertFalse(resolver.resolve(target()).handle((url, error) -> error == null).join());
    }

    @Test
    void keepsInitiallyBrokenSourceVisibleAndRecoversIt() throws Exception {
        Path source = localDirectory().resolve("recover.js");
        Files.writeString(source, "throw new Error('not ready')");
        resolver = resolver(Duration.ofMillis(250), 2, Duration.ofMillis(100));

        assertEquals(1, resolver.statuses().size());
        assertFalse(resolver.statuses().getFirst().available());
        assertTrue(resolver.statuses().getFirst().lastError().contains("not ready"));

        Files.writeString(source, resolvingScript("Recovered",
                "https://cdn.example/recovered.mp3"));
        waitUntilFirstSourceAvailable();

        assertEquals("Recovered", resolver.statuses().getFirst().name());
        assertEquals("https://cdn.example/recovered.mp3",
                resolver.resolve(target()).get(3, TimeUnit.SECONDS));
    }

    @Test
    void selectsOnlyQualitySupportedByTrackAndSource() {
        assertEquals("320k", LxCustomSourceResolver.selectQuality(
                "flac", List.of("128k", "320k"), List.of("320k", "flac")));
        assertEquals(null, LxCustomSourceResolver.selectQuality(
                "flac", List.of("128k"), List.of("320k", "flac")));
        assertEquals("flac24bit", LxCustomSourceResolver.selectQuality(
                "hires", List.of("flac24bit"), List.of("flac24bit")));
    }

    @Test
    void aggregatesOnlineMusicSearchAndKeepsPartialResults() throws Exception {
        Files.writeString(localDirectory().resolve("first.js"), searchableScript("First", """
                if (action === 'musicSearch') return Promise.resolve({ total: 2, list: [
                  { id: 'kw_1', name: 'Shared', singer: 'Alice', source: 'kw', songmid: '1', interval: '03:00', types: ['320k'] },
                  { id: 'kw_2', name: 'First only', singer: 'Bob', source: 'kw', songmid: '2', interval: '02:00', types: ['320k'] }
                ]})
                """));
        Files.writeString(localDirectory().resolve("second.js"), searchableScript("Second", """
                if (action === 'musicSearch') return { total: 2, list: [
                  { id: 'kw_1', name: 'Shared duplicate', singer: 'Alice', source: 'kw', songmid: '1', types: ['320k'] },
                  { id: 'kw_3', name: 'Second only', singer: 'Cara', source: 'kw', songmid: '3', types: ['320k'] }
                ]}
                """));
        Files.writeString(localDirectory().resolve("failing.js"), searchableScript("Failing", """
                if (action === 'musicSearch') throw new Error('search unavailable')
                """));

        resolver = resolver();

        var result = resolver.searchMusic("shared", 1, 30).get(5, TimeUnit.SECONDS);

        assertEquals(List.of("lx:kw:1", "lx:kw:2", "lx:kw:3"),
                result.items().stream().map(MusicTrack::id).toList());
        assertEquals(4, result.total());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().getFirst().contains("search unavailable"));
        assertEquals(1, resolver.statuses().stream()
                .filter(status -> status.id().equals("local/failing.js"))
                .findFirst().orElseThrow().consecutiveFailures());
    }

    @Test
    void searchesPlatformCatalogWithPlaybackOnlyOfficialSources() throws Exception {
        Files.writeString(localDirectory().resolve("playback-only.js"),
                resolvingScript("Playback only", "https://cdn.example/song.mp3"));
        LxOnlineSearchService onlineSearch = new LxOnlineSearchService(Duration.ofSeconds(10), request -> {
            assertEquals("search.kuwo.cn", request.uri().getHost());
            return JsonParser.parseString("""
                    {
                      "TOTAL": "1",
                      "abslist": [{
                        "MUSICRID": "MUSIC_123", "SONGNAME": "Online Song",
                        "ARTIST": "Artist", "ALBUM": "Album", "DURATION": "90",
                        "N_MINFO": "level:p,bitrate:320,format:mp3,size:3Mb"
                      }]
                    }
                    """);
        });
        resolver = resolver(Duration.ofSeconds(10), 2, Duration.ofSeconds(10), onlineSearch);

        var music = resolver.searchMusic("online", 1, 30).get(5, TimeUnit.SECONDS);

        assertEquals(List.of("lx:kw:123"),
                music.items().stream().map(MusicTrack::id).toList());
        TrackTarget.Lx target = assertInstanceOf(TrackTarget.Lx.class,
                music.items().getFirst().target());
        assertEquals("local/playback-only.js", target.providerId());
        assertEquals("https://cdn.example/song.mp3",
                resolver.resolve(target).get(5, TimeUnit.SECONDS));
        assertTrue(resolver.statuses().getFirst().actions().get("kw")
                .contains("musicsearch(catalog)"));
    }

    @Test
    void fallsBackToPlatformCatalogWhenCustomSearchActionFails() throws Exception {
        Files.writeString(localDirectory().resolve("failing-search.js"),
                searchableScript("Failing search", """
                        if (action === 'musicSearch') throw new Error('custom search failed')
                        """));
        LxOnlineSearchService onlineSearch = new LxOnlineSearchService(Duration.ofSeconds(10), request ->
                JsonParser.parseString("""
                        {"TOTAL":"1","abslist":[{"MUSICRID":"MUSIC_456",
                        "SONGNAME":"Fallback Song","ARTIST":"Artist","DURATION":"120",
                        "N_MINFO":"level:p,bitrate:320,format:mp3,size:4Mb"}]}
                        """));
        resolver = resolver(Duration.ofSeconds(10), 2, Duration.ofSeconds(10), onlineSearch);

        var result = resolver.searchMusic("fallback", 1, 30).get(5, TimeUnit.SECONDS);

        assertEquals(List.of("lx:kw:456"),
                result.items().stream().map(MusicTrack::id).toList());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().getFirst().contains("custom search failed"));
    }

    @Test
    void fallsBackToPlatformCatalogWhenCustomSearchReturnsNoTracks() throws Exception {
        Files.writeString(localDirectory().resolve("empty-search.js"),
                searchableScript("Empty search", """
                        if (action === 'musicSearch') return { total: 0, list: [] }
                        """));
        LxOnlineSearchService onlineSearch = new LxOnlineSearchService(Duration.ofSeconds(10), request ->
                JsonParser.parseString("""
                        {"TOTAL":"1","abslist":[{"MUSICRID":"MUSIC_789",
                        "SONGNAME":"Catalog Song","ARTIST":"Artist","DURATION":"180",
                        "N_MINFO":"level:p,bitrate:320,format:mp3,size:5Mb"}]}
                        """));
        resolver = resolver(Duration.ofSeconds(10), 2, Duration.ofSeconds(10), onlineSearch);

        var result = resolver.searchMusic("catalog", 1, 30).get(5, TimeUnit.SECONDS);

        assertEquals(List.of("lx:kw:789"),
                result.items().stream().map(MusicTrack::id).toList());
        assertEquals(List.of(), result.failures());
    }

    @Test
    void fallsBackToPlatformCatalogWhenCustomSearchTracksAreMalformed() throws Exception {
        Files.writeString(localDirectory().resolve("malformed-search.js"),
                searchableScript("Malformed search", """
                        if (action === 'musicSearch') return { total: 1, list: [{ name: 'No ID' }] }
                        """));
        LxOnlineSearchService onlineSearch = new LxOnlineSearchService(Duration.ofSeconds(10), request ->
                JsonParser.parseString("""
                        {"TOTAL":"1","abslist":[{"MUSICRID":"MUSIC_999",
                        "SONGNAME":"Valid Catalog Song","ARTIST":"Artist","DURATION":"200",
                        "N_MINFO":"level:p,bitrate:320,format:mp3,size:6Mb"}]}
                        """));
        resolver = resolver(Duration.ofSeconds(10), 2, Duration.ofSeconds(10), onlineSearch);

        var result = resolver.searchMusic("valid", 1, 30).get(5, TimeUnit.SECONDS);

        assertEquals(List.of("lx:kw:999"),
                result.items().stream().map(MusicTrack::id).toList());
    }

    @Test
    void resolvesSearchTracksWithTheirOriginatingProviderFirst() throws Exception {
        Files.writeString(localDirectory().resolve("first.js"), searchableScript("First", """
                if (action === 'musicSearch') return { list: [] }
                """).replace("Promise.resolve(`https://cdn.example/${info.musicInfo.songmid}.mp3`)",
                "Promise.resolve('https://cdn.example/wrong.mp3')"));
        Files.writeString(localDirectory().resolve("second.js"), searchableScript("Second", """
                if (action === 'musicSearch') return { list: [
                  { id: 'provider-specific', name: 'Right', source: 'kw', songmid: '99', types: ['320k'] }
                ]}
                """).replace("Promise.resolve(`https://cdn.example/${info.musicInfo.songmid}.mp3`)",
                "Promise.resolve('https://cdn.example/right.mp3')"));
        resolver = resolver();

        MusicTrack result = resolver.searchMusic("right", 1, 30)
                .get(5, TimeUnit.SECONDS).items().getFirst();

        TrackTarget.Lx target = assertInstanceOf(TrackTarget.Lx.class,
                result.target());
        assertEquals("local/second.js", target.providerId());
        assertEquals("https://cdn.example/right.mp3",
                resolver.resolve(target).get(5, TimeUnit.SECONDS));
    }

    private LxCustomSourceResolver resolver() {
        return resolver(Duration.ofSeconds(10), 2, Duration.ofSeconds(10));
    }

    private Path localDirectory() throws java.io.IOException {
        return Files.createDirectories(directory.resolve("local"));
    }

    private LxCustomSourceResolver resolver(Duration requestTimeout,
                                            int failureThreshold, Duration retryDelay) {
        return resolver(requestTimeout, failureThreshold, retryDelay, null);
    }

    private LxCustomSourceResolver resolver(Duration requestTimeout,
                                            int failureThreshold, Duration retryDelay,
                                            LxOnlineSearchService onlineSearch) {
        LxCustomSourceResolver result = new LxCustomSourceResolver(directory,
                ignored -> {}, warnings::add, requestTimeout, failureThreshold, retryDelay,
                onlineSearch);
        result.reload();
        return result;
    }

    private void waitUntilFirstSourceAvailable() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (resolver.statuses().getFirst().available()) {
                return;
            }
            Thread.sleep(25);
        }
        assertTrue(resolver.statuses().getFirst().available(), String.join("\n", warnings));
    }

    private void respondWithRequestCapture(com.sun.net.httpserver.HttpExchange exchange, String url)
            throws java.io.IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            body = multipartValue(body, "token");
            contentType = "multipart/form-data";
        }
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                + contentType + " " + body);
        byte[] response = ("{\"url\":\"" + url + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static String multipartValue(String body, String name) {
        String marker = "name=\"" + name + "\"\r\n\r\n";
        int start = body.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = body.indexOf("\r\n", start);
        return end < 0 ? body.substring(start) : body.substring(start, end);
    }

    private MusicTrack track() {
        return new MusicTrack("lx:kw:1",
                new org.encinet.mik.module.music.catalog.TrackDetails(
                        "Song", "Artist", "Album", "LX/KW",
                        new org.encinet.mik.module.music.catalog.AudioProperties(
                                null, null, Duration.ofMinutes(3))),
                new TrackTarget.Lx("kw", "1", List.of("128k", "320k"),
                        "{\"name\":\"Song\",\"singer\":\"Artist\",\"source\":\"kw\",\"songmid\":\"1\"}"));
    }

    private TrackTarget.Lx target() {
        return target(track());
    }

    private static TrackTarget.Lx target(MusicTrack track) {
        return (TrackTarget.Lx) track.target();
    }

    private String officialStyleScript(String apiUrl) {
        return """
                /*!
                 * @name Test Source
                 * @description Test
                 * @version 1.0
                 */
                const API_URL = '%s'
                const { EVENT_NAMES, request, on, send } = globalThis.lx
                const httpFetch = (url, options = { method: 'GET' }) => new Promise((resolve, reject) => {
                  request(url, options, (err, resp) => err ? reject(err) : resolve(resp))
                })
                on(EVENT_NAMES.request, ({ action, source, info }) => {
                  if (action !== 'musicUrl') return Promise.reject(new Error('unsupported'))
                  return httpFetch(`${API_URL}/url?source=${source}&songId=${info.musicInfo.songmid}&quality=${info.type}`)
                    .then(response => response.body.url)
                })
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { name: 'kw', type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k'] }
                }})
                """.formatted(apiUrl);
    }

    private String rejectingScript(String name) {
        return """
                /*! * @name %s */
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, () => Promise.reject(new Error('channel unavailable')))
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                }})
                """.formatted(name);
    }

    private String resolvingScript(String name, String url) {
        return """
                /*!
                 * @name %s
                 */
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, () => Promise.resolve('%s'))
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                }})
                """.formatted(name, url);
    }

    private String lyricScript(String name, String lyricResult) {
        return """
                /*! * @name %s */
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, ({ action }) => {
                  if (action === 'musicUrl') return 'https://cdn.example/song.mp3'
                  if (action === 'lyric') return %s
                  throw new Error('unsupported')
                })
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl', 'lyric'], qualitys: ['320k'] }
                }})
                """.formatted(name, lyricResult);
    }

    private String pendingScript(String name) {
        return """
                /*! * @name %s */
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, () => new Promise(() => {}))
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                }})
                """.formatted(name);
    }

    private String retryAfterTimeoutScript(String name) {
        return """
                /*! * @name %s */
                const { EVENT_NAMES, on, send } = globalThis.lx
                let requests = 0
                on(EVENT_NAMES.request, () => ++requests === 1
                  ? new Promise(() => {})
                  : Promise.resolve('https://cdn.example/retry.mp3'))
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                }})
                """.formatted(name);
    }

    private String formRequestScript(String baseUrl) {
        return """
                /*! * @name Form Source */
                const { EVENT_NAMES, request, on, send } = globalThis.lx
                const httpFetch = (url, options) => new Promise((resolve, reject) => {
                  request(url, options, (err, resp) => err ? reject(err) : resolve(resp))
                })
                on(EVENT_NAMES.request, async ({ action }) => {
                  if (action !== 'musicUrl') throw new Error('unsupported')
                  await httpFetch('%s/form', { method: 'POST', form: { title: 'Song', artist: 'Artist' } })
                  const response = await httpFetch('%s/multipart', { method: 'POST', formData: { token: 'abc123' } })
                  return response.body.url
                })
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                }})
                """.formatted(baseUrl, baseUrl);
    }

    private String asyncInitializationScript(String baseUrl) {
        return """
                /*! * @name Async Source */
                const { EVENT_NAMES, request, on, send } = globalThis.lx
                on(EVENT_NAMES.request, () => Promise.resolve('https://cdn.example/async.mp3'))
                request('%s/health', { method: 'GET' }, (error, response) => {
                  send(EVENT_NAMES.inited, { status: !error && response.body.ok, sources: {
                    kw: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                  }})
                })
                """.formatted(baseUrl);
    }

    private String metadataAndBinaryScript(String baseUrl) {
        return """
                /*!
                 * @name Metadata Source
                 * @description Runtime metadata test
                 * @version 3.2.1
                 * @author MIK
                 * @homepage https://example.invalid/source
                 */
                const { EVENT_NAMES, request, on, send, currentScriptInfo, utils } = globalThis.lx
                const httpFetch = (url, options) => new Promise((resolve, reject) => {
                  request(url, options, (err, resp) => err ? reject(err) : resolve(resp))
                })
                on(EVENT_NAMES.request, async ({ action }) => {
                  if (action !== 'musicUrl') throw new Error('unsupported')
                  if (currentScriptInfo.name !== 'Metadata Source' || currentScriptInfo.version !== '3.2.1'
                      || currentScriptInfo.author !== 'MIK' || !currentScriptInfo.rawScript.includes('@homepage')) {
                    throw new Error('script metadata is unavailable')
                  }
                  const response = await httpFetch('%s/binary', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/octet-stream' },
                    body: utils.buffer.from('binary-payload')
                  })
                  return response.body.url
                })
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                }})
                """.formatted(baseUrl);
    }

    private String searchableScript(String name, String searchActions) {
        return """
                /*! * @name %s */
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, ({ action, source, info }) => {
                  if (action === 'musicUrl') return Promise.resolve(`https://cdn.example/${info.musicInfo.songmid}.mp3`)
                  %s
                  throw new Error('unsupported action: ' + action)
                })
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl', 'musicSearch'], qualitys: ['320k'] }
                }})
                """.formatted(name, searchActions);
    }
}
