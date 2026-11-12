package org.encinet.mik.feature.community.social.platform.matrix.client;

import org.encinet.mik.feature.community.social.api.SocialFutures;

import static org.encinet.mik.feature.community.social.platform.matrix.MatrixTestSupport.plaintextDevice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.feature.community.social.platform.matrix.MatrixPlatformConfig;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void imageFailureTracksWhetherAUserVisibleSendWasAttempted(boolean submitted) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (exchange.getRequestURI().getPath().endsWith("/upload") && submitted) {
                respond(exchange, 200, "{\"content_uri\":\"mxc://example.org/image\"}");
            } else respond(exchange, 503, "{\"errcode\":\"M_UNKNOWN\",\"error\":\"unavailable\"}");
        });
        server.start();
        try (HttpClient http = HttpClient.newHttpClient()) {
            var client = new MatrixClient(http, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(
                    org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());
            var result = client.replyImage("!room:example.org", "$reply", Optional.empty(), "image/png", new byte[]{1},
                    1, 1, "image", "caption", "caption").join();
            assertEquals(submitted ? org.encinet.mik.feature.community.social.api.SocialDeliveryResult.Status.UNKNOWN
                    : org.encinet.mik.feature.community.social.api.SocialDeliveryResult.Status.NOT_SENT, result.status());
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
        MatrixClient client = new MatrixClient(httpClient, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());

        assertEquals("@bot:example.org", client.whoAmI());
        assertEquals("next", client.sync(new MatrixSyncRequest(Optional.of("old cursor"), 1_000, false))
                .get("next_batch").getAsString());

        assertEquals(List.of("Bearer secret-token", "Bearer secret-token"), authorizations);
        assertTrue(paths.get(1).contains("since=old%20cursor"));
        assertTrue(paths.get(1).contains("timeout=1000"));
        assertTrue(paths.get(1).contains("use_state_after=true"));
        assertTrue(paths.get(1).contains("filter="));
        assertFalse(paths.get(1).contains("secret-token"));
        var query = java.util.Arrays.stream(java.net.URI.create(paths.get(1)).getRawQuery().split("&"))
                .map(part -> part.split("=", 2)).collect(java.util.stream.Collectors.toMap(
                        part -> part[0], part -> java.net.URLDecoder.decode(part[1], StandardCharsets.UTF_8)));
        JsonObject roomFilter = JsonParser.parseString(query.get("filter")).getAsJsonObject().getAsJsonObject("room");
        assertEquals(java.util.Set.of("!space:example.org", "!room:example.org", "!relay:example.org"),
                roomFilter.getAsJsonArray("rooms").asList().stream().map(value -> value.getAsString())
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(roomFilter.get("include_leave").getAsBoolean());
        assertFalse(roomFilter.getAsJsonObject("state").get("lazy_load_members").getAsBoolean());
        assertFalse(roomFilter.getAsJsonObject("state").has("types"), "crypto requires the complete room state");
        assertTrue(roomFilter.getAsJsonObject("timeline").getAsJsonArray("types").asList().stream()
                .anyMatch(value -> value.getAsString().equals("m.room.encrypted")));
        client.sync(new MatrixSyncRequest(Optional.of("next"), 0, true));
        assertTrue(paths.getLast().contains("full_state=true"));
        httpClient.shutdownNow();
    }

    @Test
    void textReplyPreservesReplyAndThreadRelationshipsWithoutNoticeStyling() throws Exception {
        List<String> paths = new ArrayList<>();
        List<JsonObject> bodies = new ArrayList<>();
        server = messageServer(paths, bodies);
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());

        client.replyText("!room:example.org", "$incoming", Optional.of("$root"),
                "Plain reply", "<h3>Reply</h3>").join();

        assertEquals(1, bodies.size());
        assertTrue(paths.getFirst().contains(
                "/rooms/%21room%3Aexample.org/send/m.room.message/"));
        JsonObject body = bodies.getFirst();
        assertEquals("m.text", body.get("msgtype").getAsString());
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
        MatrixClient client = new MatrixClient(httpClient, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());

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
        MatrixClient client = new MatrixClient(httpClient, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());

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
        MatrixClient client = new MatrixClient(httpClient, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());

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
        MatrixClient client = new MatrixClient(httpClient, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());

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
        MatrixClient client = new MatrixClient(httpClient, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());

        MatrixHttpException error = assertThrows(MatrixHttpException.class,
                client::whoAmI);

        assertEquals(429, error.statusCode());
        assertEquals("M_LIMIT_EXCEEDED", error.errorCode());
        assertEquals(1_234, error.retryAfterMillis());
        assertTrue(error.isRateLimited());
        httpClient.shutdownNow();
    }

    @Test
    void retryAfterHeaderTakesPrecedenceAndSupportsHttpDates() throws Exception {
        var retryAfter = new java.util.concurrent.atomic.AtomicReference<>("7");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Retry-After", retryAfter.get());
            respond(exchange, 429, "{\"errcode\":\"M_LIMIT_EXCEEDED\",\"retry_after_ms\":1234}");
        });
        server.start();
        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            MatrixClient client = new MatrixClient(httpClient, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());
            assertEquals(7_000, assertThrows(MatrixHttpException.class, client::whoAmI).retryAfterMillis());
            retryAfter.set("0");
            assertEquals(0, assertThrows(MatrixHttpException.class, client::whoAmI).retryAfterMillis());
            retryAfter.set(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(60)
                    .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME));
            long delay = assertThrows(MatrixHttpException.class, client::whoAmI).retryAfterMillis();
            assertTrue(delay > 50_000 && delay <= 60_000);
            retryAfter.set("invalid");
            assertEquals(1_234, assertThrows(MatrixHttpException.class, client::whoAmI).retryAfterMillis());
        }
    }

    @Test
    void sendingWaitsForCryptoSynchronizationAndCancellingOneSenderDoesNotCancelTheBarrier() throws Exception {
        List<String> paths = new ArrayList<>();
        List<JsonObject> bodies = new ArrayList<>();
        server = messageServer(paths, bodies);
        var rooms = new org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory();
        rooms.apply(syncBatch("{\"join\":{\"!room:example.org\":{}}}"));
        try (HttpClient http = HttpClient.newHttpClient()) {
            var client = new MatrixClient(http, config(), rooms::access, plaintextDevice());
            rooms.beginCryptoSync();
            var cancelled = client.sendText("!room:example.org", "cancelled");
            var pending = client.sendText("!room:example.org", "retained");
            assertFalse(cancelled.isDone());
            assertFalse(pending.isDone());
            cancelled.cancel(true);
            rooms.endCryptoSync();
            pending.get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, bodies.size());
            assertEquals("retained", bodies.getFirst().get("body").getAsString());
            rooms.beginCryptoSync();
            var closed = client.sendText("!room:example.org", "closed");
            rooms.close();
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> closed.get(3, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, paths.size());
        }
    }

    @Test
    void enablingEncryptionDuringSyncCannotReleaseAPendingMessageAsPlaintext() throws Exception {
        List<String> paths = new ArrayList<>();
        List<JsonObject> bodies = new ArrayList<>();
        server = messageServer(paths, bodies);
        try (var rooms = new org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory();
             HttpClient http = HttpClient.newHttpClient()) {
            rooms.apply(syncBatch("{\"join\":{\"!room:example.org\":{}}}"));
            var client = new MatrixClient(http, config(), rooms::access, plaintextDevice());
            rooms.beginCryptoSync();
            var pending = client.sendText("!room:example.org", "private");
            rooms.apply(syncBatch("""
                    {"join":{"!room:example.org":{"state":{"events":[
                      {"type":"m.room.encryption","state_key":"","content":{"algorithm":"m.megolm.v1.aes-sha2"}}
                    ]}}}}
                    """));
            rooms.endCryptoSync();
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> pending.get(3, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(paths.isEmpty());
        }
    }

    @Test
    void unavailableRoomsAndMissingEncryptionNeverReceivePlaintext() throws Exception {
        List<String> paths = new ArrayList<>();
        List<JsonObject> bodies = new ArrayList<>();
        server = messageServer(paths, bodies);
        try (var rooms = new org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory();
             HttpClient httpClient = HttpClient.newHttpClient()) {
            MatrixClient client = new MatrixClient(httpClient, config(), rooms::access, plaintextDevice());
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> client.sendText("!room:example.org", "unknown").join());
            rooms.apply(syncBatch("{\"join\":{\"!room:example.org\":{}}}"));
            client.sendText("!room:example.org", "joined").join();
            assertEquals(1, bodies.size());
            rooms.apply(syncBatch("{\"leave\":{\"!room:example.org\":{}}}"));
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> client.sendText("!room:example.org", "left").join());
            rooms.apply(syncBatch("""
                    {"join":{"!room:example.org":{"state":{"events":[
                      {"type":"m.room.encryption","state_key":"",
                       "content":{"algorithm":"m.megolm.v1.aes-sha2"}}
                    ]}}}}
                    """));
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> client.sendText("!room:example.org", "cannot encrypt").join());
            rooms.apply(syncBatch("{\"join\":{\"!room:example.org\":{}}}"));
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> client.sendText("!room:example.org", "still encrypted").join());
            rooms.clear();
            rooms.apply(syncBatch("""
                    {"join":{"!room:example.org":{"state":{"events":[
                      {"type":"m.room.create","state_key":"","content":{"type":"m.space"}}
                    ]}}}}
                    """));
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> client.sendText("!room:example.org", "space").join());
            assertEquals(1, paths.size(), "blocked rooms must not receive a message or media upload");
        }
    }

    @Test
    void requestDeadlineCoversAStalledResponseBody() throws Exception {
        var released = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            hold(released);
            exchange.close();
        });
        server.start();
        try (HttpClient http = HttpClient.newHttpClient()) {
            MatrixClient client = new MatrixClient(http, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice(), Duration.ofMillis(500));
            assertThrows(java.net.http.HttpTimeoutException.class, client::whoAmI);
            http.shutdown();
            assertTrue(http.awaitTermination(Duration.ofSeconds(2)), "a timed-out exchange must be cancelled");
        } finally {
            released.countDown();
        }
    }

    @Test
    void cancellingAnOutboundRequestCancelsTheExchangeAndLetsTheClientStop() throws Exception {
        var started = new CountDownLatch(1);
        var released = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            started.countDown();
            hold(released);
            exchange.close();
        });
        server.start();
        HttpClient http = HttpClient.newHttpClient();
        try {
            MatrixClient client = new MatrixClient(http, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());
            var pending = client.sendText("!room:example.org", "message");
            assertTrue(started.await(3, TimeUnit.SECONDS));
            assertTrue(pending.cancel(true));
            http.shutdown();
            assertTrue(http.awaitTermination(Duration.ofSeconds(2)), "cancelling the result must stop the body reader");
        } finally {
            released.countDown();
            http.shutdownNow();
        }
    }

    @Test
    void cancellingAnImageAfterUploadCancelsItsSendWithoutStartingTextRecovery() throws Exception {
        var sending = new CountDownLatch(1);
        var released = new CountDownLatch(1);
        var textRecovery = new java.util.concurrent.atomic.AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/upload")) {
                exchange.getRequestBody().readAllBytes();
                respond(exchange, 200, "{\"content_uri\":\"mxc://example.org/image\"}");
            } else {
                exchange.sendResponseHeaders(200, 100);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                sending.countDown();
                hold(released);
                exchange.close();
            }
        });
        server.start();
        HttpClient http = HttpClient.newHttpClient();
        try {
            MatrixClient client = new MatrixClient(http, config(), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());
            var pending = SocialFutures.recover(client.replyImage("!room:example.org", "$reply", Optional.empty(),
                    "image/png", new byte[]{1}, 1, 1, "image", "caption", "caption"), _ -> {
                textRecovery.incrementAndGet();
                return org.encinet.mik.feature.community.social.api.SocialDeliveryResult.observe(client.sendText("!room:example.org", "caption"),
                        _ -> org.encinet.mik.feature.community.social.api.SocialDeliveryResult.Status.UNKNOWN);
            });
            assertTrue(sending.await(3, TimeUnit.SECONDS));
            assertTrue(pending.cancel(true));
            http.shutdown();
            assertTrue(http.awaitTermination(Duration.ofSeconds(2)), "the active send must be cancelled after upload ends");
            assertEquals(0, textRecovery.get());
        } finally {
            released.countDown();
            http.shutdownNow();
        }
    }

    @Test
    void responseLimitAlsoAppliesToChunkedBodies() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write(new byte[65_537]);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (HttpClient http = HttpClient.newHttpClient()) {
            MatrixClient client = new MatrixClient(http, config(65_536), _ -> java.util.concurrent.CompletableFuture.completedFuture(org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security.PLAINTEXT), plaintextDevice());
            assertThrows(IOException.class, client::whoAmI);
        }
    }

    private static void hold(CountDownLatch released) {
        try {
            released.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncProtocol.Batch
            syncBatch(String rooms) {
        return org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncProtocol.parse(
                JsonParser.parseString("{\"next_batch\":\"cursor\",\"rooms\":" + rooms + "}")
                        .getAsJsonObject());
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
        return config(2_097_152);
    }

    private MatrixPlatformConfig config(int maxResponseBytes) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                homeserver-url: http://127.0.0.1:%d
                access-token: secret-token
                membership:
                  room-ids: ['!space:example.org']
                commands:
                  room-ids: ['!room:example.org']
                chat-bridge:
                  routes:
                    - room-id: '!relay:example.org'
                """.formatted(server.getAddress().getPort()));
        yaml.set("sync.max-response-bytes", maxResponseBytes);
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
