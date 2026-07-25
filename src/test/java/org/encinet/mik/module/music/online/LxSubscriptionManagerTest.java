package org.encinet.mik.module.music.online;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LxSubscriptionManagerTest {

    @TempDir
    Path directory;

    private HttpServer server;

    @AfterEach
    void close() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void importsHttpSourceAndUsesConditionalRequestsForUpdates() throws Exception {
        AtomicReference<String> etag = new AtomicReference<>("\"v1\"");
        AtomicReference<String> script = new AtomicReference<>(sourceScript("HTTP v1"));
        AtomicReference<String> receivedEtag = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/source.js", exchange -> respondWithEtag(
                exchange, script.get(), etag.get(), receivedEtag));
        server.start();

        LxSubscriptionManager manager = manager();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/source.js";
        LxSubscriptionManager.ImportResult imported = manager.importSource(url);
        Path snapshot = manager.remoteDirectory().resolve(imported.id() + ".js");

        assertTrue(imported.added());
        assertTrue(imported.changed());
        assertEquals(sourceScript("HTTP v1"), Files.readString(snapshot));
        assertTrue(Files.readString(manager.remoteDirectory().resolve("sources.json"))
                .contains(imported.id()));

        LxSubscriptionManager.RefreshResult unchanged = manager.refreshAll();

        assertEquals(1, unchanged.total());
        assertEquals(0, unchanged.changed());
        assertEquals(0, unchanged.failed());
        assertEquals("\"v1\"", receivedEtag.get());

        etag.set("\"v2\"");
        script.set(sourceScript("HTTP v2"));
        LxSubscriptionManager.RefreshResult updated = manager.refreshAll();

        assertEquals(1, updated.changed());
        assertEquals(sourceScript("HTTP v2"), Files.readString(snapshot));
        assertEquals(1, manager.statuses().size());
        assertEquals(null, manager.statuses().getFirst().lastError());
    }

    @Test
    void importsAndRefreshesFileUrlAsAnIndependentSnapshot() throws Exception {
        Path external = directory.resolve("external.js");
        Files.writeString(external, sourceScript("File v1"));
        LxSubscriptionManager manager = manager();

        LxSubscriptionManager.ImportResult imported = manager.importSource(external.toUri().toString());
        Path snapshot = manager.remoteDirectory().resolve(imported.id() + ".js");

        assertTrue(imported.added());
        assertEquals(sourceScript("File v1"), Files.readString(snapshot));
        Files.writeString(external, sourceScript("File v2"));

        LxSubscriptionManager.RefreshResult refreshed = manager.refreshAll();

        assertEquals(1, refreshed.changed());
        assertEquals(sourceScript("File v2"), Files.readString(snapshot));
        assertFalse(Files.isSameFile(external, snapshot));
    }

    @Test
    void keepsLastWorkingSnapshotWhenAnUpdateFailsValidation() throws Exception {
        Path external = directory.resolve("external.js");
        Files.writeString(external, sourceScript("Stable"));
        LxSubscriptionManager manager = manager();
        LxSubscriptionManager.ImportResult imported = manager.importSource(external.toUri().toString());
        Path snapshot = manager.remoteDirectory().resolve(imported.id() + ".js");

        Files.writeString(external, "throw new Error('broken update')");
        LxSubscriptionManager.RefreshResult refreshed = manager.refreshAll();

        assertEquals(0, refreshed.changed());
        assertEquals(1, refreshed.failed());
        assertEquals(sourceScript("Stable"), Files.readString(snapshot));
        assertTrue(manager.statuses().getFirst().lastError().contains("broken update"));
    }

    @Test
    void removeOnlyDeletesItsManagedSnapshot() throws Exception {
        Path external = directory.resolve("external.js");
        Files.writeString(external, sourceScript("Managed"));
        LxSubscriptionManager manager = manager();
        LxSubscriptionManager.ImportResult imported = manager.importSource(external.toUri().toString());
        Path snapshot = manager.remoteDirectory().resolve(imported.id() + ".js");
        Path unmanaged = manager.remoteDirectory().resolve("manual.js");
        Files.writeString(unmanaged, sourceScript("Unmanaged"));

        assertTrue(manager.remove(imported.id()));
        assertFalse(Files.exists(snapshot));
        assertTrue(Files.exists(unmanaged));
        assertFalse(manager.remove("remote-000000000000000000000000"));
        assertEquals(0, manager.statuses().size());
    }

    @Test
    void rejectsUnsupportedOrAmbiguousUrls() {
        LxSubscriptionManager manager = manager();

        assertThrows(IOException.class, () -> manager.importSource("ftp://example.com/source.js"));
        assertThrows(IOException.class, () -> manager.importSource("https:///source.js"));
        assertThrows(IOException.class,
                () -> manager.importSource("https://user:secret@example.com/source.js"));
        assertThrows(IOException.class, () -> manager.importSource("file:///tmp/source.js?version=1"));
    }

    @Test
    void validatesAnOrphanedMatchingSnapshotBeforeRegisteringIt() throws Exception {
        Path external = directory.resolve("broken.js");
        Files.writeString(external, "throw new Error('orphan is broken')");
        LxSubscriptionManager manager = manager();
        String id = remoteId(external.toUri().toString());
        Files.writeString(manager.remoteDirectory().resolve(id + ".js"),
                Files.readString(external));

        assertThrows(IOException.class,
                () -> manager.importSource(external.toUri().toString()));
        assertTrue(manager.statuses().isEmpty());
    }

    @Test
    void rejectsUnconditionalNotModifiedResponseForANewSubscription() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/source.js", exchange -> {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/source.js";
        LxSubscriptionManager manager = manager();
        String id = remoteId(url);
        Files.writeString(manager.remoteDirectory().resolve(id + ".js"),
                "throw new Error('unvalidated orphan')");

        IOException failure = assertThrows(IOException.class,
                () -> manager.importSource(url));

        assertTrue(failure.getMessage().contains("304"));
        assertTrue(manager.statuses().isEmpty());
        assertFalse(Files.exists(manager.remoteDirectory().resolve("sources.json")));
    }

    private LxSubscriptionManager manager() {
        return new LxSubscriptionManager(directory.resolve("lxmusic"), ignored -> {},
                Duration.ofSeconds(5));
    }

    private static void respondWithEtag(HttpExchange exchange, String script, String etag,
                                        AtomicReference<String> receivedEtag) throws IOException {
        receivedEtag.set(exchange.getRequestHeaders().getFirst("If-None-Match"));
        exchange.getResponseHeaders().set("ETag", etag);
        if (etag.equals(receivedEtag.get())) {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }
        byte[] body = script.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String sourceScript(String name) {
        return """
                /*! * @name %s */
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, () => Promise.resolve('https://cdn.example/song.mp3'))
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                }})
                """.formatted(name);
    }

    private static String remoteId(String url) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(url.getBytes(StandardCharsets.UTF_8));
        return "remote-" + java.util.HexFormat.of().formatHex(digest, 0, 12);
    }
}
