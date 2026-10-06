package org.encinet.mik.module.api;

import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.module.ban.BanRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalApiServerTest {
    @TempDir Path directory;

    @Test
    void serializesPlayerLoginAndBanTextAsValidJson() {
        UUID id = UUID.randomUUID();
        String text = "A\u0001\b\"\\中文";
        assertEquals(text, JsonParser.parseString(string(LocalApiServer.playerJson(id, text)))
                .getAsJsonObject().getAsJsonObject("player").get("name").getAsString());

        WebLoginChallengeStore.Confirmation confirmation =
                new WebLoginChallengeStore.Confirmation(id, text, "member",
                        "2026-09-27T00:00:00Z", 1_000L, false);
        assertEquals(text, JsonParser.parseString(string(LocalApiServer.challengeJson(confirmation)))
                .getAsJsonObject().getAsJsonObject("player").get("name").getAsString());

        BanRecord ban = new BanRecord(1, id, "Player", "player", text, "Admin",
                Instant.parse("2026-09-27T00:00:00Z"),
                Instant.parse("2026-09-27T00:00:00Z"), null, null, BanRecord.Origin.MIK);
        var banJson = JsonParser.parseString(string(LocalApiServer.bansJson(List.of(ban))))
                .getAsJsonArray().get(0).getAsJsonObject();
        assertEquals(text, banJson.get("reason").getAsString());
        assertEquals(id.toString(), banJson.get("playerUuid").getAsString());
        assertEquals("2026-09-27T00:00:00Z", banJson.get("bannedAt").getAsString());
        assertTrue(banJson.get("expiresAt").isJsonNull());
        assertTrue(banJson.get("isPermanent").getAsBoolean());
    }

    @Test
    void loopbackRoutesKeepMethodsAndChallengeConsumption() throws Exception {
        Logger logger = Logger.getLogger("LocalApiServerTest");
        ApiPlayerSnapshot players = new ApiPlayerSnapshot(directory, logger);
        players.load();
        players.bootstrap(List.of(), Instant.parse("2026-09-27T00:00:00Z"));
        WebLoginChallengeStore challenges = new WebLoginChallengeStore();
        challenges.confirm("123456", UUID.randomUUID(), "Player", "member");
        CommunityBoardView board = new CommunityBoardView() {
            @Override public String listJson() { return "{\"notices\":[]}"; }
            @Override public String detailJson(String id) { return null; }
        };
        LocalApiServer server = new LocalApiServer(plugin(logger), players, challenges,
                List::of, () -> "[]".getBytes(StandardCharsets.UTF_8), board);

        server.start(0);
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertTrue(server.running());
            assertEquals(200, request(client, server, "GET", "/api/players").statusCode());
            assertEquals("no-store", request(client, server, "GET", "/api/players")
                    .headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(200, request(client, server, "GET", "/api/announcements").statusCode());
            assertEquals(405, request(client, server, "POST", "/api/announcements").statusCode());
            assertEquals(404, request(client, server, "GET", "/api/announcements/extra").statusCode());
            assertEquals(200, request(client, server, "GET", "/api/community/notices").statusCode());
            assertEquals("confirmed", JsonParser.parseString(request(client, server, "GET",
                    "/api/auth/challenges/123456").body()).getAsJsonObject()
                    .get("status").getAsString());
            assertEquals("confirmed", JsonParser.parseString(request(client, server, "POST",
                    "/api/auth/challenges/123456/consume").body()).getAsJsonObject()
                    .get("status").getAsString());
            assertEquals("consumed", JsonParser.parseString(request(client, server, "GET",
                    "/api/auth/challenges/123456").body()).getAsJsonObject()
                    .get("status").getAsString());
        } finally {
            server.stop();
        }
        assertFalse(server.running());
    }

    @Test
    void bindFailureDoesNotPublishAStoppedServerAsRunning() throws Exception {
        Logger logger = Logger.getLogger("LocalApiServerTest");
        ApiPlayerSnapshot players = new ApiPlayerSnapshot(directory, logger);
        players.load();
        players.bootstrap(List.of(), Instant.parse("2026-09-27T00:00:00Z"));
        CommunityBoardView board = new CommunityBoardView() {
            @Override public String listJson() { return "{}"; }
            @Override public String detailJson(String id) { return null; }
        };
        LocalApiServer first = new LocalApiServer(plugin(logger), players,
                new WebLoginChallengeStore(), List::of, () -> new byte[]{'[', ']'}, board);
        LocalApiServer second = new LocalApiServer(plugin(logger), players,
                new WebLoginChallengeStore(), List::of, () -> new byte[]{'[', ']'}, board);
        first.start(0);
        try {
            assertThrows(IOException.class, () -> second.start(first.port()));
            assertFalse(second.running());
            assertTrue(first.running());
        } finally {
            second.stop();
            first.stop();
        }
    }

    private static HttpResponse<String> request(HttpClient client, LocalApiServer server,
                                                String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                        + server.port() + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Plugin plugin(Logger logger) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getLogger")) return logger;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static String string(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
