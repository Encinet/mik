package org.encinet.mik.module.social.platform.matrix;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialSessionStatus;
import org.encinet.mik.test.ChatTestMessages;
import org.encinet.mik.module.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.module.social.chat.SocialChatMentionRequest;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.ExternalIdentityKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixPlatformSessionTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void initialSyncIsDiscardedAndLaterMessagesAreAdmitted() throws Exception {
        AtomicInteger syncRequests = new AtomicInteger();
        CountDownLatch holdLastSync = new CountDownLatch(1);
        List<String> queries = new CopyOnWriteArrayList<>();
        server = server(exchange -> {
            int request = syncRequests.incrementAndGet();
            queries.add(exchange.getRequestURI().getRawQuery());
            if (request == 1) {
                respond(exchange, 200, batch("initial", "$old", "!status"));
            } else if (request == 2) {
                respond(exchange, 200, batch("live", "$live", "!status"));
            } else {
                awaitRelease(holdLastSync);
                respond(exchange, 200, "{\"next_batch\":\"idle\",\"rooms\":{}}");
            }
        });
        MatrixPlatformConfig config = config();
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config);
        List<SocialInboundMessage> admitted = new CopyOnWriteArrayList<>();
        List<SocialSessionStatus> statuses = new CopyOnWriteArrayList<>();
        MatrixInboundMapper mapper = new MatrixInboundMapper(config, client, message -> {
            admitted.add(message);
            return SocialEventSink.Acceptance.ACCEPTED;
        });
        MatrixPlatformSession session = new MatrixPlatformSession(httpClient, client,
                config, mapper, statuses::add,
                Logger.getLogger("MatrixPlatformSessionTest"));

        session.start();
        await(() -> admitted.size() == 1);

        assertEquals("$live", admitted.getFirst().eventId());
        assertEquals("!status",
                ((SocialInboundMessage.Text) admitted.getFirst().content()).body());
        assertTrue(queries.get(1).contains("since=initial"));
        assertTrue(statuses.stream().anyMatch(status ->
                status.state() == SocialSessionStatus.State.READY));

        holdLastSync.countDown();
        session.close();
        assertEquals(SocialSessionStatus.State.STOPPED,
                statuses.getLast().state());
    }

    @Test
    void backpressureRetriesTheSameSyncCursor() throws Exception {
        AtomicInteger syncRequests = new AtomicInteger();
        AtomicInteger admissions = new AtomicInteger();
        CountDownLatch holdLastSync = new CountDownLatch(1);
        List<String> queries = new CopyOnWriteArrayList<>();
        server = server(exchange -> {
            int request = syncRequests.incrementAndGet();
            queries.add(exchange.getRequestURI().getRawQuery());
            if (request == 1) {
                respond(exchange, 200, "{\"next_batch\":\"one\",\"rooms\":{}}");
            } else if (request == 2 || request == 3) {
                respond(exchange, 200, batch("two", "$retry", "!status"));
            } else {
                awaitRelease(holdLastSync);
                respond(exchange, 200, "{\"next_batch\":\"three\",\"rooms\":{}}");
            }
        });
        MatrixPlatformConfig config = config();
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config);
        MatrixInboundMapper mapper = new MatrixInboundMapper(config, client, message ->
                admissions.incrementAndGet() == 1
                        ? SocialEventSink.Acceptance.RETRY_LATER
                        : SocialEventSink.Acceptance.ACCEPTED);
        MatrixPlatformSession session = new MatrixPlatformSession(httpClient, client,
                config, mapper, ignored -> { },
                Logger.getLogger("MatrixPlatformSessionTest"));

        session.start();
        await(() -> admissions.get() == 2);

        assertTrue(queries.get(1).contains("since=one"));
        assertTrue(queries.get(2).contains("since=one"));
        holdLastSync.countDown();
        session.close();
    }

    @Test
    void recoveredSyncBecomesReadyBeforeAdmittingItsEvents() throws Exception {
        AtomicInteger syncRequests = new AtomicInteger();
        CountDownLatch holdLastSync = new CountDownLatch(1);
        List<SocialSessionStatus> statuses = new CopyOnWriteArrayList<>();
        AtomicInteger admissions = new AtomicInteger();
        server = server(exchange -> {
            int request = syncRequests.incrementAndGet();
            if (request == 1) {
                respond(exchange, 200, "{\"next_batch\":\"one\",\"rooms\":{}}");
            } else if (request == 2) {
                respond(exchange, 500,
                        "{\"errcode\":\"M_UNKNOWN\",\"error\":\"temporary\"}");
            } else if (request == 3) {
                respond(exchange, 200, batch("two", "$after-reconnect", "!status"));
            } else {
                awaitRelease(holdLastSync);
                respond(exchange, 200, "{\"next_batch\":\"three\",\"rooms\":{}}");
            }
        });
        MatrixPlatformConfig config = config();
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config);
        MatrixInboundMapper mapper = new MatrixInboundMapper(config, client, message -> {
            admissions.incrementAndGet();
            return statuses.getLast().state() == SocialSessionStatus.State.READY
                    ? SocialEventSink.Acceptance.ACCEPTED
                    : SocialEventSink.Acceptance.RETRY_LATER;
        });
        MatrixPlatformSession session = new MatrixPlatformSession(httpClient, client,
                config, mapper, statuses::add,
                Logger.getLogger("MatrixPlatformSessionTest"));

        session.start();
        await(() -> admissions.get() == 1);

        assertEquals(SocialSessionStatus.State.READY, statuses.getLast().state());
        assertTrue(statuses.stream().anyMatch(status ->
                status.state() == SocialSessionStatus.State.STARTING
                        && status.lastError().contains("HTTP 500")));
        holdLastSync.countDown();
        session.close();
    }

    @Test
    void outboundChatSendsTheSelectedConversationAndBody() throws Exception {
        AtomicInteger syncRequests = new AtomicInteger();
        CountDownLatch holdSync = new CountDownLatch(1);
        List<String> rooms = new CopyOnWriteArrayList<>();
        List<String> bodies = new CopyOnWriteArrayList<>();
        List<String> formattedBodies = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.endsWith("/account/whoami")) {
                respond(exchange, 200, "{\"user_id\":\"@bot:example.org\"}");
            } else if (path.endsWith("/joined_members")) {
                String account = path.contains("%21always%3Aexample.org")
                        ? "@alex:one.example" : "@alex:two.example";
                respond(exchange, 200, "{\"joined\":{"
                        + "\"@bot:example.org\":{\"display_name\":\"MIK\"},"
                        + "\"" + account + "\":{\"display_name\":\"Room Alex\"}}}");
            } else if (path.endsWith("/sync")) {
                if (syncRequests.incrementAndGet() == 1) {
                    respond(exchange, 200, "{\"next_batch\":\"one\",\"rooms\":{}}");
                } else {
                    awaitRelease(holdSync);
                    respond(exchange, 200, "{\"next_batch\":\"idle\",\"rooms\":{}}");
                }
            } else {
                JsonObject body = JsonParser.parseString(new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                rooms.add(path);
                bodies.add(body.get("body").getAsString());
                formattedBodies.add(body.get("formatted_body").getAsString());
                respond(exchange, 200, "{\"event_id\":\"$sent\"}");
            }
        });
        server.start();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                homeserver-url: http://127.0.0.1:%d
                access-token: token
                allowed-room-ids:
                  - '!always:example.org'
                  - '!prefix:example.org'
                chat-bridge:
                  routes:
                    - room-id: '!always:example.org'
                      outbound:
                        mode: always
                    - room-id: '!prefix:example.org'
                      outbound:
                        mode: prefix
                        prefix: '#'
                        strip-prefix: true
                sync:
                  timeout-millis: 1000
                """.formatted(server.getAddress().getPort()));
        MatrixPlatformConfig config = MatrixPlatformConfig.from(yaml);
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config);
        List<SocialSessionStatus> statuses = new CopyOnWriteArrayList<>();
        MatrixInboundMapper mapper = new MatrixInboundMapper(config, client,
                ignored -> SocialEventSink.Acceptance.IGNORED);
        MatrixPlatformSession session = new MatrixPlatformSession(httpClient, client,
                config, mapper, statuses::add,
                Logger.getLogger("MatrixPlatformSessionTest"));

        session.start();
        await(() -> statuses.stream().anyMatch(status ->
                status.state() == SocialSessionStatus.State.READY));
        UUID targetPlayer = UUID.randomUUID();
        List<ExternalIdentity> candidates = List.of(
                new ExternalIdentity(new ExternalIdentityKey("matrix",
                        "one.example", "", "@alex:one.example"), "Old One"),
                new ExternalIdentity(new ExternalIdentityKey("matrix",
                        "two.example", "", "@alex:two.example"), "Old Two"));
        var alwaysMentions = session.resolveMentions(
                config.chatRoutes().get(0).conversation(),
                List.of(new SocialChatMentionRequest(
                        targetPlayer, "Alex", candidates)));
        var prefixMentions = session.resolveMentions(
                config.chatRoutes().get(1).conversation(),
                List.of(new SocialChatMentionRequest(
                        targetPlayer, "Alex", candidates)));
        assertEquals(List.of("@alex:one.example"), alwaysMentions
                .targetsFor(targetPlayer).stream()
                .map(identity -> identity.key().subject()).toList());
        assertEquals(List.of("@alex:two.example"), prefixMentions
                .targetsFor(targetPlayer).stream()
                .map(identity -> identity.key().subject()).toList());
        assertEquals("Room Alex", alwaysMentions.targetsFor(
                targetPlayer).getFirst().displayName());
        session.sendChat(config.chatRoutes().get(0).conversation(),
                ChatTestMessages.minecraft("Steve", "hello"))
                .toCompletableFuture().join();
        session.sendChat(config.chatRoutes().get(1).conversation(),
                ChatTestMessages.minecraft("Steve", "world"))
                .toCompletableFuture().join();

        assertEquals(2, bodies.size());
        assertTrue(bodies.containsAll(List.of("Steve: hello", "Steve: world")));
        assertTrue(formattedBodies.containsAll(List.of(
                "<strong>Steve</strong>: hello",
                "<strong>Steve</strong>: world")));
        assertTrue(rooms.stream().filter(path ->
                path.contains("%21always%3Aexample.org")).count() == 1);
        assertTrue(rooms.stream().filter(path ->
                path.contains("%21prefix%3Aexample.org")).count() == 1);
        holdSync.countDown();
        session.close();
    }

    private HttpServer server(SyncHandler syncHandler) throws IOException {
        HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        created.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/account/whoami")) {
                respond(exchange, 200, "{\"user_id\":\"@bot:example.org\"}");
            } else {
                syncHandler.handle(exchange);
            }
        });
        created.start();
        return created;
    }

    private MatrixPlatformConfig config() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                homeserver-url: http://127.0.0.1:%d
                access-token: token
                allowed-room-ids: ['!room:example.org']
                sync:
                  timeout-millis: 1000
                  reconnect-base-delay-millis: 100
                  reconnect-max-delay-millis: 100
                  max-reconnect-attempts: 2
                """.formatted(server.getAddress().getPort()));
        return MatrixPlatformConfig.from(yaml);
    }

    private static String batch(String nextBatch, String eventId, String body) {
        return """
                {"next_batch":"%s","rooms":{"join":{"!room:example.org":{
                  "timeline":{"events":[{"type":"m.room.message",
                    "event_id":"%s","sender":"@alice:example.org",
                    "content":{"msgtype":"m.text","body":"%s"}}]}
                }}}}
                """.formatted(nextBatch, eventId, body);
    }

    private static void await(java.util.function.BooleanSupplier condition)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertTrue(condition.getAsBoolean(),
                "condition did not become true before timeout");
    }

    private static void awaitRelease(CountDownLatch latch) {
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface SyncHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
