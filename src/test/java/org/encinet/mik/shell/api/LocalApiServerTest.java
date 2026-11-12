package org.encinet.mik.shell.api;

import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.server.ban.BanRecord;
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
                        "2026-09-27T00:00:00Z", 1_000L);
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
    void loopbackRoutesExposeRetryableApprovalsAndAccountPolicy() throws Exception {
        Logger logger = Logger.getLogger("LocalApiServerTest");
        ApiPlayerSnapshot players = new ApiPlayerSnapshot(directory, logger);
        players.load();
        players.bootstrap(List.of(), Instant.parse("2026-09-27T00:00:00Z"));
        WebLoginChallengeStore challenges = new WebLoginChallengeStore();
        challenges.confirm("123456", UUID.randomUUID(), "Player", "member", () -> true);
        var currentPolicy = new java.util.concurrent.atomic.AtomicReference<>(new org.encinet.mik.feature.player.security.WebAccountPolicy("member", 1234L));
        var banned = new java.util.concurrent.atomic.AtomicBoolean(false);
        LocalApiServer server = new LocalApiServer(plugin(logger), players, challenges,
                List::of, () -> "[]".getBytes(StandardCharsets.UTF_8), _ -> currentPolicy.get(), (_, _) -> !banned.get());

        server.start(0);
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertTrue(server.running());
            assertEquals(200, request(client, server, "GET", "/api/players").statusCode());
            assertEquals("no-store", request(client, server, "GET", "/api/players")
                    .headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(200, request(client, server, "GET", "/api/announcements").statusCode());
            assertEquals(405, request(client, server, "POST", "/api/announcements").statusCode());
            assertEquals(404, request(client, server, "GET", "/api/announcements/extra").statusCode());
            assertEquals("confirmed", JsonParser.parseString(request(client, server, "GET",
                    "/api/auth/challenges/123456").body()).getAsJsonObject()
                    .get("status").getAsString());
            assertEquals(404, request(client, server, "POST", "/api/auth/challenges/123456/consume").statusCode());
            assertEquals(405, request(client, server, "POST", "/api/auth/challenges/123456").statusCode());
            assertEquals("confirmed", JsonParser.parseString(request(client, server, "GET",
                    "/api/auth/challenges/123456").body()).getAsJsonObject().get("status").getAsString());
            var policy = JsonParser.parseString(request(client, server, "GET",
                    "/api/auth/accounts/00000000-0000-4000-8000-000000000001").body()).getAsJsonObject().getAsJsonObject("player");
            assertEquals("member", policy.get("role").getAsString()); assertEquals(1234L, policy.get("credentialsChangedAt").getAsLong());
            assertFalse(policy.get("banned").getAsBoolean());
            assertEquals(404, request(client, server, "GET", "/api/auth/accounts/1-1-1-1-1").statusCode());
            currentPolicy.set(new org.encinet.mik.feature.player.security.WebAccountPolicy("default", 1234L));
            var downgraded = JsonParser.parseString(request(client, server, "GET", "/api/auth/challenges/123456").body()).getAsJsonObject();
            assertEquals("default", downgraded.getAsJsonObject("player").get("role").getAsString());
            banned.set(true);
            var denied = JsonParser.parseString(request(client, server, "GET", "/api/auth/accounts/00000000-0000-4000-8000-000000000001?name=Player").body()).getAsJsonObject().getAsJsonObject("player");
            assertTrue(denied.get("banned").getAsBoolean());
            assertEquals("banned", JsonParser.parseString(request(client, server, "GET", "/api/auth/challenges/123456").body()).getAsJsonObject().get("status").getAsString());
            banned.set(false);
            assertEquals("confirmed", JsonParser.parseString(request(client, server, "GET", "/api/auth/challenges/123456").body()).getAsJsonObject().get("status").getAsString());
            currentPolicy.set(new org.encinet.mik.feature.player.security.WebAccountPolicy("member", System.currentTimeMillis() + 1000L));
            assertEquals("not_found", JsonParser.parseString(request(client, server, "GET", "/api/auth/challenges/123456").body()).getAsJsonObject().get("status").getAsString());
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
        LocalApiServer first = new LocalApiServer(plugin(logger), players,
                new WebLoginChallengeStore(), List::of, () -> new byte[]{'[', ']'}, _ -> new org.encinet.mik.feature.player.security.WebAccountPolicy("member", 1234L), (_, _) -> true);
        LocalApiServer second = new LocalApiServer(plugin(logger), players,
                new WebLoginChallengeStore(), List::of, () -> new byte[]{'[', ']'}, _ -> new org.encinet.mik.feature.player.security.WebAccountPolicy("member", 1234L), (_, _) -> true);
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
                new Class<?>[]{Plugin.class}, (_, method, _) -> {
                    if (method.getName().equals("getLogger")) return logger;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static String string(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
