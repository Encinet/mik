package org.encinet.mik.feature.community.social.platform.matrix;

import static org.encinet.mik.feature.community.social.platform.matrix.MatrixTestSupport.plaintextDevice;
import static org.encinet.mik.feature.community.social.platform.matrix.MatrixTestSupport.joinedRoom;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.feature.community.social.api.SocialEventSink;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.api.SocialSessionStatus;
import org.encinet.mik.test.ChatTestMessages;
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.feature.community.social.chat.SocialChatMentionRequest;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
    void encryptedCommandsUseDecryptedBodiesAndKeyWaitsDoNotBlockMemberExits() throws Exception {
        var requests = new AtomicInteger();
        var idle = new CountDownLatch(1);
        server = server(exchange -> {
            int request = requests.incrementAndGet();
            if (request == 1) respond(exchange, 200, """
                    {"next_batch":"initial","rooms":{"join":{"!room:example.org":{
                      "state":{"events":[
                        {"type":"m.room.member","state_key":"@bot:example.org","content":{"membership":"join"}},
                        {"type":"m.room.encryption","state_key":"","content":{"algorithm":"m.megolm.v1.aes-sha2"}},
                        {"type":"m.room.member","state_key":"@alice:example.org","content":{"membership":"join"}}]},
                      "timeline":{"prev_batch":"start","events":[{"event_id":"$history","type":"m.room.encrypted","sender":"@alice:example.org","content":{}}]}
                    }}}}
                    """);
            else if (request == 2) respond(exchange, 200, """
                    {"next_batch":"live","rooms":{"join":{"!room:example.org":{
                      "timeline":{"events":[
                        {"event_id":"$live","type":"m.room.encrypted","sender":"@alice:example.org","content":{}},
                        {"event_id":"$plaintext","type":"m.room.message","sender":"@alice:example.org","content":{"msgtype":"m.text","body":"!绑定 spoofed"}}]}
                    }}}}
                    """);
            else if (request == 3) respond(exchange, 200, """
                    {"next_batch":"left","rooms":{"join":{"!room:example.org":{
                      "timeline":{"events":[{"type":"m.room.member","state_key":"@alice:example.org","content":{"membership":"leave"}}]}
                    }}}}
                    """);
            else {
                awaitRelease(idle);
                respond(exchange, 200, "{\"next_batch\":\"idle\",\"rooms\":{}}");
            }
        });
        var device = org.mockito.Mockito.spy(plaintextDevice());
        var decrypting = new java.util.concurrent.CompletableFuture<JsonObject>();
        org.mockito.Mockito.doReturn(decrypting).when(device).decrypt("!room:example.org", "$live");
        var config = config();
        var rooms = new MatrixRoomDirectory();
        var http = HttpClient.newHttpClient();
        var client = new MatrixClient(http, config, rooms::access, device);
        var admitted = new CopyOnWriteArrayList<SocialInboundMessage>();
        var mapper = new MatrixInboundMapper(config, client, message -> {
            admitted.add(message);
            return SocialEventSink.Acceptance.ACCEPTED;
        });
        try (var session = new MatrixPlatformSession(http, client, config, mapper, rooms, device, () -> { },
                _ -> { }, Logger.getAnonymousLogger())) {
            session.start();
            var alice = new ExternalIdentityKey("matrix", "example.org", "", "@alice:example.org");
            await(() -> session.memberships().orElseThrow().state(alice)
                    == org.encinet.mik.feature.community.identity.IdentityMemberships.State.ABSENT);
            assertTrue(admitted.isEmpty());
            decrypting.complete(JsonParser.parseString("""
                    {"event_id":"$live","sender":"@alice:example.org","type":"m.room.message",
                     "content":{"msgtype":"m.text","body":"!绑定 000123"}}
                    """).getAsJsonObject());
            await(() -> admitted.size() == 1);
            assertEquals("!绑定 000123", ((SocialInboundMessage.Text) admitted.getFirst().content()).body());
            org.mockito.Mockito.verify(device, org.mockito.Mockito.never()).decrypt("!room:example.org", "$history");
        } finally {
            idle.countDown();
        }
    }

    @Test
    void membershipUsesAllConfiguredRoomsWithoutChatRoutesAndTracksLeaveAndBan() throws Exception {
        var firstLeave = new CountDownLatch(1);
        var lastLeave = new CountDownLatch(1);
        var idle = new CountDownLatch(1);
        var requests = new AtomicInteger();
        var loadedRooms = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/account/whoami")) {
                respond(exchange, 200, "{\"user_id\":\"@bot:example.org\"}");
            } else if (path.endsWith("/members")) {
                loadedRooms.incrementAndGet();
                respond(exchange, 200, "{\"chunk\":[{\"type\":\"m.room.member\",\"state_key\":\"@alice:example.org\",\"content\":{\"membership\":\"join\",\"displayname\":\"Alice\"}}]}");
            } else {
                int request = requests.incrementAndGet();
                if (request == 1) respond(exchange, 200, joinedBatch("one", Map.of(
                        "!one:example.org", joinedRoom(Map.of("@bot:example.org", "MIK", "@alice:example.org", "Alice")),
                        "!two:example.org", joinedRoom(Map.of("@bot:example.org", "MIK", "@alice:example.org", "Alice")))));
                else if (request == 2 || request == 3) {
                    awaitRelease(request == 2 ? firstLeave : lastLeave);
                    String room = request == 2 ? "!one:example.org" : "!two:example.org";
                    String state = request == 2 ? "leave" : "ban";
                    respond(exchange, 200, """
                            {"next_batch":"%s","rooms":{"join":{"%s":{"timeline":{"events":[
                            {"type":"m.room.member","state_key":"@alice:example.org","content":{"membership":"%s"}}
                            ]}}}}}
                            """.formatted(request, room, state));
                } else {
                    awaitRelease(idle);
                    respond(exchange, 200, "{\"next_batch\":\"idle\",\"rooms\":{}}");
                }
            }
        });
        server.start();
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                homeserver-url: http://127.0.0.1:%d
                access-token: token
                membership:
                  room-ids: ['!one:example.org', '!two:example.org']
                commands:
                  room-ids: ['!one:example.org', '!two:example.org']
                """.formatted(server.getAddress().getPort()));
        var config = MatrixPlatformConfig.from(yaml);
        var http = HttpClient.newHttpClient();
        var roomDirectory = new MatrixRoomDirectory();
        var client = new MatrixClient(http, config, roomDirectory::access, plaintextDevice());
        try (var session = new MatrixPlatformSession(http, client, config,
                new MatrixInboundMapper(config, client, _ -> SocialEventSink.Acceptance.IGNORED),
                roomDirectory, plaintextDevice(), () -> { }, _ -> { }, Logger.getAnonymousLogger())) {
            var memberships = session.memberships().orElseThrow();
            var alice = new ExternalIdentityKey("matrix", "example.org", "", "@alice:example.org");
            session.start();
            await(() -> requests.get() >= 2);
            assertEquals(0, loadedRooms.get());
            assertEquals(org.encinet.mik.feature.community.identity.IdentityMemberships.State.JOINED, memberships.state(alice));
            firstLeave.countDown();
            await(() -> requests.get() >= 3);
            assertEquals(org.encinet.mik.feature.community.identity.IdentityMemberships.State.JOINED, memberships.state(alice));
            lastLeave.countDown();
            await(() -> memberships.state(alice) == org.encinet.mik.feature.community.identity.IdentityMemberships.State.ABSENT);
            idle.countDown();
        } finally {
            firstLeave.countDown(); lastLeave.countDown(); idle.countDown();
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
        MatrixRoomDirectory roomDirectory = new MatrixRoomDirectory();
        MatrixClient client = new MatrixClient(httpClient, config, roomDirectory::access, plaintextDevice());
        List<SocialInboundMessage> admitted = new CopyOnWriteArrayList<>();
        List<SocialSessionStatus> statuses = new CopyOnWriteArrayList<>();
        MatrixInboundMapper mapper = new MatrixInboundMapper(config, client, message -> {
            admitted.add(message);
            return SocialEventSink.Acceptance.ACCEPTED;
        });
        MatrixPlatformSession session = new MatrixPlatformSession(httpClient, client,
                config, mapper, roomDirectory, plaintextDevice(), () -> { }, statuses::add,
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
                respond(exchange, 200, batch("one", "$history", "!status"));
            } else if (request == 2 || request == 3) {
                respond(exchange, 200, batch("two", "$retry", "!status"));
            } else {
                awaitRelease(holdLastSync);
                respond(exchange, 200, "{\"next_batch\":\"three\",\"rooms\":{}}");
            }
        });
        MatrixPlatformConfig config = config();
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixRoomDirectory roomDirectory = new MatrixRoomDirectory();
        MatrixClient client = new MatrixClient(httpClient, config, roomDirectory::access, plaintextDevice());
        MatrixInboundMapper mapper = new MatrixInboundMapper(config, client, _ ->
                admissions.incrementAndGet() == 1
                        ? SocialEventSink.Acceptance.RETRY_LATER
                        : SocialEventSink.Acceptance.ACCEPTED);
        MatrixPlatformSession session = new MatrixPlatformSession(httpClient, client,
                config, mapper, roomDirectory, plaintextDevice(), () -> { }, _ -> { },
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
                respond(exchange, 200, batch("one", "$history", "!status"));
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
        MatrixRoomDirectory roomDirectory = new MatrixRoomDirectory();
        MatrixClient client = new MatrixClient(httpClient, config, roomDirectory::access, plaintextDevice());
        MatrixInboundMapper mapper = new MatrixInboundMapper(config, client, _ -> {
            admissions.incrementAndGet();
            return statuses.getLast().state() == SocialSessionStatus.State.READY
                    ? SocialEventSink.Acceptance.ACCEPTED
                    : SocialEventSink.Acceptance.RETRY_LATER;
        });
        MatrixPlatformSession session = new MatrixPlatformSession(httpClient, client,
                config, mapper, roomDirectory, plaintextDevice(), () -> { }, statuses::add,
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
    void aNewRoomCompletesItsRosterThroughSyncAndOnlyAdmitsLiveMessages() throws Exception {
        var syncRequests = new AtomicInteger();
        var memberRequests = new AtomicInteger();
        var queries = new CopyOnWriteArrayList<String>();
        var idle = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/account/whoami")) {
                respond(exchange, 200, "{\"user_id\":\"@bot:example.org\"}");
            } else if (path.endsWith("/members")) {
                memberRequests.incrementAndGet();
                respond(exchange, 403, "{\"errcode\":\"M_FORBIDDEN\"}");
            } else {
                queries.add(exchange.getRequestURI().getRawQuery());
                int request = syncRequests.incrementAndGet();
                if (request == 1) {
                    respond(exchange, 200, joinedBatch("initial", Map.of()));
                } else if (request == 2) {
                    respond(exchange, 200, batch("discovered", "$history", "!status"));
                } else if (request == 3) {
                    respond(exchange, 200, batch("confirmed", "$live", "!status"));
                } else {
                    awaitRelease(idle);
                    respond(exchange, 200, "{\"next_batch\":\"idle\",\"rooms\":{}}");
                }
            }
        });
        server.start();
        var config = config();
        var http = HttpClient.newHttpClient();
        var rooms = new MatrixRoomDirectory();
        var client = new MatrixClient(http, config, rooms::access, plaintextDevice());
        var statuses = new CopyOnWriteArrayList<SocialSessionStatus>();
        var admitted = new CopyOnWriteArrayList<SocialInboundMessage>();
        try (var session = new MatrixPlatformSession(http, client, config,
                new MatrixInboundMapper(config, client, message -> {
                    admitted.add(message);
                    return SocialEventSink.Acceptance.ACCEPTED;
                }), rooms, plaintextDevice(), () -> { }, statuses::add, Logger.getAnonymousLogger())) {
            var alice = new ExternalIdentityKey("matrix", "example.org", "", "@alice:example.org");
            session.start();
            await(() -> !admitted.isEmpty());
            assertEquals(org.encinet.mik.feature.community.identity.IdentityMemberships.State.JOINED,
                    session.memberships().orElseThrow().state(alice));
            assertEquals(List.of("$live"), admitted.stream().map(SocialInboundMessage::eventId).toList());
            assertTrue(queries.get(2).contains("full_state=true"));
            assertTrue(queries.get(2).contains("since=discovered"));
            assertEquals(0, memberRequests.get());
            assertTrue(statuses.stream().noneMatch(status -> !status.lastError().isEmpty()));
        } finally {
            idle.countDown();
        }
    }

    @Test
    void closingDuringSyncCancelsIoAndCannotPublishLateReadiness() throws Exception {
        var syncStarted = new CountDownLatch(1);
        var released = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/account/whoami")) {
                respond(exchange, 200, "{\"user_id\":\"@bot:example.org\"}");
            } else {
                exchange.sendResponseHeaders(200, 100);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                syncStarted.countDown();
                awaitRelease(released);
                exchange.close();
            }
        });
        server.start();
        var config = config();
        var http = HttpClient.newHttpClient();
        var rooms = new MatrixRoomDirectory();
        var client = new MatrixClient(http, config, rooms::access, plaintextDevice());
        var statuses = new CopyOnWriteArrayList<SocialSessionStatus>();
        var session = new MatrixPlatformSession(http, client, config,
                new MatrixInboundMapper(config, client, _ -> SocialEventSink.Acceptance.IGNORED),
                rooms, plaintextDevice(), () -> { }, statuses::add, Logger.getAnonymousLogger());
        try {
            session.start();
            assertTrue(syncStarted.await(3, TimeUnit.SECONDS));
            session.close();
            assertTrue(http.awaitTermination(java.time.Duration.ofSeconds(2)));
            assertEquals(SocialSessionStatus.State.STOPPED, statuses.getLast().state());
            assertTrue(statuses.stream().noneMatch(status -> status.state() == SocialSessionStatus.State.READY));
            assertEquals(org.encinet.mik.feature.community.identity.IdentityMemberships.State.UNKNOWN,
                    session.memberships().orElseThrow().state(new ExternalIdentityKey(
                            "matrix", "example.org", "", "@alice:example.org")));
        } finally {
            released.countDown();
            session.close();
        }
    }

    @Test
    void doesNotAdmitPlaintextCommandsFromEncryptedRooms() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch holdSync = new CountDownLatch(1);
        server = server(exchange -> {
            int request = requests.incrementAndGet();
            if (request == 1) {
                respond(exchange, 200, """
                        {"next_batch":"initial","rooms":{"join":{"!room:example.org":{
                          "timeline":{"prev_batch":"room-start","events":[]},
                          "state":{"events":[
                            {"type":"m.room.member","state_key":"@bot:example.org","content":{"membership":"join"}},
                            {"type":"m.room.encryption","state_key":"",
                             "content":{"algorithm":"m.megolm.v1.aes-sha2"}}]}
                        }}}}
                        """);
            } else if (request == 2) {
                respond(exchange, 200, batch("live", "$command", "!绑定 secret"));
            } else {
                awaitRelease(holdSync);
                respond(exchange, 200, "{\"next_batch\":\"idle\",\"rooms\":{}}");
            }
        });
        MatrixPlatformConfig config = config();
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixRoomDirectory rooms = new MatrixRoomDirectory();
        MatrixClient client = new MatrixClient(httpClient, config, rooms::access, plaintextDevice());
        List<SocialInboundMessage> admitted = new CopyOnWriteArrayList<>();
        MatrixInboundMapper mapper = new MatrixInboundMapper(config, client, message -> {
            admitted.add(message);
            return SocialEventSink.Acceptance.ACCEPTED;
        });
        MatrixPlatformSession session = new MatrixPlatformSession(httpClient, client,
                config, mapper, rooms, plaintextDevice(), () -> { }, _ -> { }, Logger.getLogger("MatrixPlatformSessionTest"));
        try {
            session.start();
            await(() -> requests.get() >= 3);
            assertTrue(admitted.isEmpty());
        } finally {
            holdSync.countDown();
            session.close();
        }
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
            } else if (path.endsWith("/sync")) {
                if (syncRequests.incrementAndGet() == 1) {
                    respond(exchange, 200, joinedBatch("one", Map.of(
                            "!always:example.org", joinedRoom(Map.of("@bot:example.org", "MIK", "@alex:one.example", "Room Alex")),
                            "!prefix:example.org", joinedRoom(Map.of("@bot:example.org", "MIK", "@alex:two.example", "Room Alex")))));
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
                membership:
                  room-ids: ['!always:example.org', '!prefix:example.org']
                commands:
                  room-ids: ['!always:example.org', '!prefix:example.org']
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
        MatrixRoomDirectory roomDirectory = new MatrixRoomDirectory();
        MatrixClient client = new MatrixClient(httpClient, config, roomDirectory::access, plaintextDevice());
        List<SocialSessionStatus> statuses = new CopyOnWriteArrayList<>();
        MatrixInboundMapper mapper = new MatrixInboundMapper(config, client,
                _ -> SocialEventSink.Acceptance.IGNORED);
        MatrixPlatformSession session = new MatrixPlatformSession(httpClient, client,
                config, mapper, roomDirectory, plaintextDevice(), () -> { }, statuses::add,
                Logger.getLogger("MatrixPlatformSessionTest"));

        session.start();
        await(() -> statuses.stream().anyMatch(status ->
                status.state() == SocialSessionStatus.State.READY));
        UUID targetPlayer = UUID.randomUUID();
        List<ExternalIdentity> candidates = List.of(
                new ExternalIdentity(new ExternalIdentityKey("matrix",
                        "one.example", "", "@alex:one.example"), "Old One"),
                new ExternalIdentity(new ExternalIdentityKey("matrix",
                        "two.example", "", "@alex:two.example"), "Old Two"),
                new ExternalIdentity(new ExternalIdentityKey("matrix",
                        "wrong-issuer.example", "", "@alex:one.example"), "Invalid namespace"));
        var alwaysMentions = session.resolveMentions(
                config.rooms().chatRoutes().get(0).conversation(),
                List.of(new SocialChatMentionRequest(
                        targetPlayer, "Alex", candidates)));
        var prefixMentions = session.resolveMentions(
                config.rooms().chatRoutes().get(1).conversation(),
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
        session.sendChat(config.rooms().chatRoutes().get(0).conversation(),
                ChatTestMessages.minecraft("Steve", "hello"))
                .toCompletableFuture().join();
        session.sendChat(config.rooms().chatRoutes().get(1).conversation(),
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
        SocialDocument notice = SocialDocument.of(SocialDocument.Moderation.REQUIRED,
                "Announcement", SocialDocument.Tone.INFO, SocialDocument.paragraph("First line\n<everyone> & text"));
        session.prepareNotice(config.rooms().chatRoutes().get(1).conversation(), notice, 2_000).send().join();
        assertEquals(3, bodies.size());
        assertEquals("Announcement\n\nFirst line\n<everyone> & text", bodies.getLast());
        assertEquals("<h3>Announcement</h3><p>First line<br>&lt;everyone&gt; &amp; text</p>", formattedBodies.getLast());
        holdSync.countDown();
        session.close();
        session.prepareNotice(config.rooms().chatRoutes().get(1).conversation(), notice, 2_000).send().join();
        assertEquals(3, bodies.size());
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
                membership:
                  room-ids: ['!room:example.org']
                commands:
                  room-ids: ['!room:example.org']
                sync:
                  timeout-millis: 1000
                  reconnect-base-delay-millis: 100
                  reconnect-max-delay-millis: 100
                  max-reconnect-attempts: 2
                """.formatted(server.getAddress().getPort()));
        return MatrixPlatformConfig.from(yaml);
    }

    private static String batch(String nextBatch, String eventId, String body) {
        var room = joinedRoom(Map.of("@bot:example.org", "MIK", "@alice:example.org", "Alice"));
        var event = new JsonObject();
        event.addProperty("type", "m.room.message");
        event.addProperty("event_id", eventId);
        event.addProperty("sender", "@alice:example.org");
        var content = new JsonObject();
        content.addProperty("msgtype", "m.text");
        content.addProperty("body", body);
        event.add("content", content);
        room.getAsJsonObject("timeline").getAsJsonArray("events").add(event);
        return joinedBatch(nextBatch, Map.of("!room:example.org", room));
    }

    private static String joinedBatch(String nextBatch, Map<String, JsonObject> joined) {
        var response = new JsonObject();
        response.addProperty("next_batch", nextBatch);
        var groups = new JsonObject();
        var rooms = new JsonObject();
        joined.forEach(rooms::add);
        groups.add("join", rooms);
        response.add("rooms", groups);
        return response.toString();
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
