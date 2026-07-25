package org.encinet.mik.module.music.lyrics;

import com.sun.net.httpserver.HttpServer;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.online.LxSourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LyricsServiceTest {

    @TempDir
    Path directory;

    private LyricsService lyricsService;
    private LxSourceService sourceService;
    private HttpServer server;

    @AfterEach
    void close() {
        if (lyricsService != null) lyricsService.close();
        if (sourceService != null) sourceService.close();
        if (server != null) server.stop(0);
    }

    @Test
    void fallsBackToProviderUrlsAndReusesPersistentCache() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/original.lrc", exchange -> respond(exchange, requests,
                "[00:01.00]Original"));
        server.createContext("/translation.lrc", exchange -> respond(exchange, requests,
                "[00:01.00]Translated"));
        server.start();
        int port = server.getAddress().getPort();
        sourceService = new LxSourceService(directory.resolve("lxmusic"), ignored -> {}, ignored -> {});
        sourceService.reloadAsync().get(5, TimeUnit.SECONDS);
        lyricsService = new LyricsService(directory.resolve("cache/lyrics"),
                sourceService, ignored -> {});
        MusicTrack track = onlineTrack("http://127.0.0.1:" + port);

        Lyrics loaded = lyricsService.load(track).get(5, TimeUnit.SECONDS).orElseThrow();

        assertEquals("Original", loaded.lineAt(1_000).orElseThrow().text());
        assertEquals("Translated", loaded.lineAt(1_000).orElseThrow().translation());
        assertEquals(2, requests.get());
        lyricsService.close();
        lyricsService = new LyricsService(directory.resolve("cache/lyrics"),
                sourceService, ignored -> {});
        server.stop(0);
        server = null;

        Lyrics cached = lyricsService.load(track).get(5, TimeUnit.SECONDS).orElseThrow();
        assertEquals("Original", cached.lineAt(1_000).orElseThrow().text());
        try (var paths = Files.list(directory.resolve("cache/lyrics"))) {
            assertEquals(1, paths.filter(path -> path.toString().endsWith(".json")).count());
        }
    }

    private MusicTrack onlineTrack(String baseUrl) {
        return new MusicTrack("lx:kw:1",
                new TrackDetails("Song", "Artist", null, "LX/KW",
                        new AudioProperties(null, null, Duration.ofMinutes(3))),
                new TrackTarget.Lx("kw", "1", List.of("320k"), """
                        {"name":"Song","source":"kw","songmid":"1",
                         "lrcUrl":"%s/original.lrc","trcUrl":"%s/translation.lrc"}
                        """.formatted(baseUrl, baseUrl)));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
                                AtomicInteger requests, String text) throws java.io.IOException {
        requests.incrementAndGet();
        byte[] response = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
