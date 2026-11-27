package org.encinet.mik.integration.mikd;

import com.google.gson.JsonParser;
import org.encinet.mik.feature.player.security.GameAccountFacts;
import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.protocol.*;
import org.encinet.mik.protocol.WebLoginReceipt.Outcome;
import org.encinet.mik.shell.mikd.GameQueries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WebApiInteropTest {
    @TempDir Path directory;

    @Test void refreshesTheSharedAnnouncementSnapshotWithoutPaper() throws Exception {
        var catalog = Files.createDirectory(directory.resolve("announcements")).resolve("catalog.json");
        Files.writeString(catalog, """
                [{"timestamp":"2026-11-09T12:00:00+08:00","content":"First"}]
                """);

        try (var backend = MikdTestBackend.webApi(directory);
             var client = HttpClient.newHttpClient()) {
            int port = backend.requests().status(ConnectionRole.GENERAL).get(5, TimeUnit.SECONDS).daemon().getWebApiPort();
            URI origin = URI.create("http://127.0.0.1:" + port);
            long initialDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            HttpResponse<String> notice;
            do {
                notice = request(client, origin, "GET", "/api/announcements", null);
                if (notice.statusCode() == 200) break;
                Thread.sleep(Duration.ofMillis(25));
            } while (System.nanoTime() - initialDeadline < 0);
            assertEquals(200, notice.statusCode());
            assertEquals("[{\"timestamp\":\"2026-11-09T04:00:00Z\",\"content\":\"First\"}]", notice.body());
            var staged = Files.writeString(catalog.resolveSibling("next.json"), """
                    [{"timestamp":"2026-11-09T12:00:00+08:00","content":"Edited"}]
                    """);
            Files.move(staged, catalog, StandardCopyOption.ATOMIC_MOVE);

            String updated = "[{\"timestamp\":\"2026-11-09T04:00:00Z\",\"content\":\"Edited\"}]";
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            do {
                notice = request(client, origin, "GET", "/api/announcements", null);
                if (updated.equals(notice.body())) break;
                Thread.sleep(Duration.ofMillis(100));
            } while (System.nanoTime() - deadline < 0);
            assertEquals(200, notice.statusCode());
            assertEquals(updated, notice.body());

            var snapshot = backend.requests().fetchAnnouncements().get(5, TimeUnit.SECONDS);
            assertEquals(List.of(AnnouncementEntry.newBuilder()
                    .setTimestamp(Instant.parse("2026-11-09T04:00:00Z").getEpochSecond())
                    .setContent("Edited").build()), snapshot.getEntriesList());
            assertFalse(snapshot.getStale());
            assertEquals(405, request(client, origin, "POST", "/api/announcements", null).statusCode());
            assertEquals(404, request(client, origin, "GET", "/api/announcements/extra", null).statusCode());
        }
    }

    @Test void daemonUsesTheAuthenticatedGameBridgeForGameState() throws Exception {
        var uuid = UUID.randomUUID();
        String display = "Player \"\\中文";
        var joined = Instant.parse("2026-09-27T00:00:00Z");
        String reference = UUID.randomUUID().toString();
        var localCustodian = new AtomicBoolean(true);
        var liveReference = new AtomicReference<>(reference);
        var banned = new AtomicBoolean();
        var allowedName = new AtomicReference<String>();
        var resolving = new CountDownLatch(1);
        var resumeResolve = new CountDownLatch(1);
        var ban = new BanRecord(1, uuid, "Player", "player", display, "Admin", joined, joined, null, null, BanRecord.Origin.MIK);
        var game = new GameQueries(null, () -> List.of(ban),
                (_, expected) -> new GameAccountFacts(localCustodian.get(), 1234, liveReference.get().equals(expected)),
                (_, name) -> { allowedName.set(name); return !banned.get(); }, null, (_, _) -> null);
        try (var backend = MikdTestBackend.webApi(directory);
             var client = HttpClient.newHttpClient();
             var _ = new GameQueryWorker("test-account-queries", backend.requests()::accountQuery, game::execute);
             var _ = new GameQueryWorker("test-game-queries", backend.requests()::gameQuery, job -> {
                 var reply = GameQueryReply.newBuilder().setId(job.getId());
                 if (job.getResolvePlayer().equals("SlowPlayer")) {
                     resolving.countDown();
                     resumeResolve.await();
                 }
                 return switch (job.getOperationCase()) {
                     case RESOLVE_PLAYER -> reply.setPlayer(ChatPlayer.newBuilder().setId(uuid.toString())
                             .setName(job.getResolvePlayer())).build();
                     default -> game.execute(job);
                 };
            })) {
            int port = backend.requests().status(ConnectionRole.GENERAL).get(5, TimeUnit.SECONDS).daemon().getWebApiPort();
            URI origin = URI.create("http://127.0.0.1:" + port);
            assertEquals(503, request(client, origin, "GET", "/api/players", null).statusCode());
            backend.requests().publish(PaperStateUpdate.newBuilder().setVersion(1).setServer(GameStatusView.getDefaultInstance())
                    .setPublicRoster(GameRoster.newBuilder().addPlayers(PlayerSnapshot.newBuilder()
                            .setName(display).setPlayerId(uuid.toString()).setJoinedAt(joined.toString())))
                    .setChatRoster(ChatGameRoster.getDefaultInstance())
                    .build()).get(5, TimeUnit.SECONDS);
            var snapshot = request(client, origin, "GET", "/api/players", null);
            assertEquals(200, snapshot.statusCode());
            assertEquals("no-store", snapshot.headers().firstValue("Cache-Control").orElseThrow());
            var roster = JsonParser.parseString(snapshot.body()).getAsJsonObject();
            assertEquals(1, roster.get("online").getAsInt());
            assertEquals(Set.of("online", "players"), roster.keySet());
            var row = roster.getAsJsonArray("players").get(0).getAsJsonObject();
            assertEquals(display, row.get("name").getAsString());
            assertEquals(joined.toString(), row.get("joined_at").getAsString());
            var bans = JsonParser.parseString(request(client, origin, "GET", "/api/bans", null).body()).getAsJsonArray();
            assertEquals(display, bans.get(0).getAsJsonObject().get("reason").getAsString());
            assertTrue(bans.get(0).getAsJsonObject().get("expiresAt").isJsonNull());
            var found = JsonParser.parseString(request(client, origin, "GET", "/api/players/resolve?name=%20Player%20", null).body());
            assertEquals("Player", found.getAsJsonObject().getAsJsonObject("player").get("name").getAsString());
            assertEquals(400, request(client, origin, "GET", "/api/players/resolve?name=x", null).statusCode());

            String path = "/api/auth/accounts/" + uuid + "?name=A%2BB";
            var slow = client.sendAsync(HttpRequest.newBuilder(origin.resolve("/api/players/resolve?name=SlowPlayer"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> accountResponse;
            try {
                assertTrue(resolving.await(3, TimeUnit.SECONDS));
                accountResponse = request(client, origin, "GET", path, null);
                assertEquals(200, accountResponse.statusCode());
            } finally { resumeResolve.countDown(); }
            assertEquals(200, slow.get(5, TimeUnit.SECONDS).statusCode());
            var account = JsonParser.parseString(accountResponse.body()).getAsJsonObject().getAsJsonObject("player");
            assertEquals(Set.of("uuid", "role", "credentialsChangedAt", "banned"), account.keySet());
            assertEquals("A+B", allowedName.get());
            assertEquals(1234L, account.get("credentialsChangedAt").getAsLong());
            assertEquals("custodian", account.get("role").getAsString());
            assertFalse(account.get("banned").getAsBoolean());
            assertEquals(404, request(client, origin, "GET", "/api/auth/accounts/1-1-1-1-1", null).statusCode());
            String challenge = "/api/auth/challenges/12345678";
            assertTrue(request(client, origin, "GET", challenge, null).body().contains("\"not_found\""));
            var approval = WebLoginApproval.newBuilder().setCode("12345678").setPlayerUuid(uuid.toString())
                    .setPlayerName(display).setLoginReference(reference).build();
            assertEquals(Outcome.APPROVED, backend.requests().approveWebLogin(approval, () -> true)
                    .get(5, TimeUnit.SECONDS).getOutcome());
            assertEquals(Outcome.REJECTED, backend.requests().approveWebLogin(
                    approval.toBuilder().setPlayerUuid(UUID.randomUUID().toString()).build(), () -> true)
                    .get(5, TimeUnit.SECONDS).getOutcome());
            assertTrue(request(client, origin, "GET", challenge, null).body().contains("\"confirmed\""));
            assertEquals(405, request(client, origin, "POST", challenge, null).statusCode());
            localCustodian.set(false);
            assertTrue(request(client, origin, "GET", challenge, null).body().contains("\"default\""));
            banned.set(true);
            assertTrue(request(client, origin, "GET", challenge, null).body().contains("\"banned\""));
            banned.set(false);
            liveReference.set(UUID.randomUUID().toString());
            assertTrue(request(client, origin, "GET", challenge, null).body().contains("\"not_found\""));

            long now = System.currentTimeMillis();
            String key = Base64.getUrlEncoder().withoutPadding().encodeToString(HexFormat.of().parseHex(
                    "046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"
                    + "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5"));
            String grant = """
                    {"playerUuid":"%s","playerName":"Player","connectionId":"%s","sessionRef":"%s",
                     "renewalToken":"%s","clientPublicKey":"%s","authenticatedAt":%d,"checkedAt":%d,"expiresAt":%d}
                    """.formatted(uuid, "A".repeat(32), "a".repeat(43), "b".repeat(43), key, now, now, now + 60_000);
            assertEquals(503, request(client, origin, "POST", "/api/chat/authorize",
                    grant.replace(Long.toString(now + 60_000), Long.toString(now - 1))).statusCode());
            var authorize = request(client, origin, "POST", "/api/chat/authorize", grant);
            assertEquals(409, authorize.statusCode());
            assertTrue(authorize.body().contains("connection_unavailable"));
            assertEquals(415, request(client, origin, "POST", "/api/chat/authorize", null).statusCode());
            assertEquals(413, request(client, origin, "POST", "/api/chat/authorize", " ".repeat(16_385)).statusCode());
            var revoke = request(client, origin, "POST", "/api/chat/revoke", "{\"sessionRefs\":[\"" + "a".repeat(43) + "\"]}");
            assertEquals(200, revoke.statusCode());
            assertTrue(JsonParser.parseString(revoke.body()).getAsJsonObject().get("ok").getAsBoolean());
            assertEquals(422, request(client, origin, "POST", "/api/chat/revoke", "{\"sessionRefs\":[123]}").statusCode());
        }
    }

    private static HttpResponse<String> request(HttpClient client, URI origin, String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(5));
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
