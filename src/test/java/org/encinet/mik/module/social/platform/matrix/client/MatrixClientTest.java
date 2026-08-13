package org.encinet.mik.module.social.platform.matrix.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.social.platform.matrix.MatrixPlatformConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void whoamiAndSyncUseBearerAuthenticationAndAFilteredCursor() throws Exception {
        List<String> paths = new ArrayList<>();
        List<String> authorizations = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().toASCIIString());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (exchange.getRequestURI().getPath().endsWith("/account/whoami")) {
                respond(exchange, 200, "{\"user_id\":\"@bot:example.org\"}");
            } else {
                respond(exchange, 200, "{\"next_batch\":\"next\",\"rooms\":{}}");
            }
        });
        server.start();
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config());

        assertEquals("@bot:example.org", client.whoAmI());
        assertEquals("next", client.sync("old cursor", 1_000)
                .get("next_batch").getAsString());

        assertEquals(List.of("Bearer secret-token", "Bearer secret-token"), authorizations);
        assertTrue(paths.get(1).contains("since=old%20cursor"));
        assertTrue(paths.get(1).contains("timeout=1000"));
        assertTrue(paths.get(1).contains("filter="));
        assertFalse(paths.get(1).contains("secret-token"));
        httpClient.shutdownNow();
    }

    @Test
    void noticeReplyPreservesReplyAndThreadRelationships() throws Exception {
        List<String> paths = new ArrayList<>();
        List<JsonObject> bodies = new ArrayList<>();
        server = messageServer(paths, bodies);
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config());

        client.replyNotice("!room:example.org", "$incoming", Optional.of("$root"),
                "Plain reply", "<h3>Reply</h3>").join();

        assertEquals(1, bodies.size());
        assertTrue(paths.getFirst().contains(
                "/rooms/%21room%3Aexample.org/send/m.room.message/"));
        JsonObject body = bodies.getFirst();
        assertEquals("m.notice", body.get("msgtype").getAsString());
        assertEquals("Plain reply", body.get("body").getAsString());
        assertEquals("<h3>Reply</h3>", body.get("formatted_body").getAsString());
        assertEquals(0, body.getAsJsonObject("m.mentions").size());
        JsonObject relation = body.getAsJsonObject("m.relates_to");
        assertEquals("$incoming", relation.getAsJsonObject("m.in_reply_to")
                .get("event_id").getAsString());
        assertEquals("m.thread", relation.get("rel_type").getAsString());
        assertEquals("$root", relation.get("event_id").getAsString());
        assertFalse(relation.get("is_falling_back").getAsBoolean());
        httpClient.shutdownNow();
    }

    @Test
    void chatTextIsSentWithoutReplyMetadataOrSyntheticMentions() throws Exception {
        List<String> paths = new ArrayList<>();
        List<JsonObject> bodies = new ArrayList<>();
        server = messageServer(paths, bodies);
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config());

        client.sendText("!room:example.org", "Steve: hello").join();

        JsonObject body = bodies.getFirst();
        assertEquals("m.text", body.get("msgtype").getAsString());
        assertEquals("Steve: hello", body.get("body").getAsString());
        assertEquals(0, body.getAsJsonObject("m.mentions").size());
        assertFalse(body.has("m.relates_to"));
        httpClient.shutdownNow();
    }

    @Test
    void formattedChatIncludesPlainFallbackWithoutReplyMetadata() throws Exception {
        List<String> paths = new ArrayList<>();
        List<JsonObject> bodies = new ArrayList<>();
        server = messageServer(paths, bodies);
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config());

        client.sendFormattedText("!room:example.org", "Steve: link",
                "<strong>Steve</strong>: <a href=\"https://example.com\">link</a>")
                .join();

        JsonObject body = bodies.getFirst();
        assertEquals("m.text", body.get("msgtype").getAsString());
        assertEquals("Steve: link", body.get("body").getAsString());
        assertEquals("org.matrix.custom.html", body.get("format").getAsString());
        assertTrue(body.get("formatted_body").getAsString()
                .contains("href=\"https://example.com\""));
        assertEquals(0, body.getAsJsonObject("m.mentions").size());
        assertFalse(body.has("m.relates_to"));
        httpClient.shutdownNow();
    }

    @Test
    void formattedChatCarriesNativeMatrixMentionMetadata() throws Exception {
        List<String> paths = new ArrayList<>();
        List<JsonObject> bodies = new ArrayList<>();
        server = messageServer(paths, bodies);
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config());

        client.sendFormattedText("!room:example.org", "Steve: @Alex",
                "<strong>Steve</strong>: <a href=\"https://matrix.to/#/@alex:example.org\">@Alex</a>",
                List.of("@alex:example.org", "@alex:example.org"))
                .join();

        JsonObject mentions = bodies.getFirst().getAsJsonObject("m.mentions");
        assertEquals(List.of("@alex:example.org"),
                mentions.getAsJsonArray("user_ids").asList().stream()
                        .map(value -> value.getAsString()).toList());
        httpClient.shutdownNow();
    }

    @Test
    void joinedMembersReturnsAuthoritativeDisplayNames() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, 200, """
                {"joined":{
                  "@alice:example.org":{"display_name":"Alice"},
                  "@unnamed:example.org":{"avatar_url":"mxc://example.org/a"}
                }}
                """));
        server.start();
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config());

        assertEquals(java.util.Map.of(
                "@alice:example.org", "Alice",
                "@unnamed:example.org", ""),
                client.joinedMembers("!room:example.org"));
        httpClient.shutdownNow();
    }

    @Test
    void imageReplyUploadsBytesAndSendsAnMxcMediaEvent() throws Exception {
        List<String> paths = new ArrayList<>();
        List<JsonObject> bodies = new ArrayList<>();
        List<byte[]> uploads = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().toASCIIString());
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            if (exchange.getRequestURI().getPath().endsWith("/media/v3/upload")) {
                uploads.add(requestBody);
                assertEquals("image/png",
                        exchange.getRequestHeaders().getFirst("Content-Type"));
                respond(exchange, 200,
                        "{\"content_uri\":\"mxc://example.org/media\"}");
            } else {
                bodies.add(JsonParser.parseString(new String(
                        requestBody, StandardCharsets.UTF_8)).getAsJsonObject());
                respond(exchange, 200, "{\"event_id\":\"$sent\"}");
            }
        });
        server.start();
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config());

        client.replyImage("!room:example.org", "$incoming", Optional.empty(),
                "image/png", new byte[]{1, 2, 3, 4}, 256, 128,
                "Avatar", "Profile", "<h3>Profile</h3>").join();

        assertEquals(1, uploads.size());
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new byte[]{1, 2, 3, 4}, uploads.getFirst());
        assertTrue(paths.getFirst().contains("filename=Avatar.png"));
        JsonObject body = bodies.getFirst();
        assertEquals("m.image", body.get("msgtype").getAsString());
        assertEquals("mxc://example.org/media", body.get("url").getAsString());
        assertEquals("Profile", body.get("body").getAsString());
        assertEquals(256, body.getAsJsonObject("info").get("w").getAsInt());
        assertEquals(128, body.getAsJsonObject("info").get("h").getAsInt());
        httpClient.shutdownNow();
    }

    @Test
    void matrixErrorsExposeProtocolCodeAndRetryHint() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, 429, """
                {"errcode":"M_LIMIT_EXCEEDED","error":"Slow down","retry_after_ms":1234}
                """));
        server.start();
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config());

        MatrixHttpException error = assertThrows(MatrixHttpException.class,
                client::whoAmI);

        assertEquals(429, error.statusCode());
        assertEquals("M_LIMIT_EXCEEDED", error.errorCode());
        assertEquals(1_234, error.retryAfterMillis());
        assertTrue(error.isRateLimited());
        httpClient.shutdownNow();
    }

    private HttpServer messageServer(List<String> paths, List<JsonObject> bodies)
            throws IOException {
        HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        created.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getRawPath());
            bodies.add(JsonParser.parseString(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject());
            respond(exchange, 200, "{\"event_id\":\"$sent\"}");
        });
        created.start();
        return created;
    }

    private MatrixPlatformConfig config() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                homeserver-url: http://127.0.0.1:%d
                access-token: secret-token
                allowed-room-ids: ['!room:example.org']
                """.formatted(server.getAddress().getPort()));
        return MatrixPlatformConfig.from(yaml);
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
