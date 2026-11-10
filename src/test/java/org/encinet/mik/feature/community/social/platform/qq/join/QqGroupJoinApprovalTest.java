package org.encinet.mik.feature.community.social.platform.qq.join;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.feature.community.social.api.SocialEventSink.Acceptance;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformConfig;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.encinet.mik.test.QqTestHttpClient;

import java.time.Instant;
import java.time.Duration;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqGroupJoinApprovalTest {
    @Test
    void anApplicationHandledElsewhereDoesNotImposeGlobalFailureBackoff() throws Exception {
        LinkedBlockingQueue<String> calls = new LinkedBlockingQueue<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            boolean token = path.equals("/app/getAppAccessToken");
            boolean alreadyHandled = path.contains("/group-a/");
            byte[] body = (token ? "{\"access_token\":\"token\",\"expires_in\":7200}" :
                    alreadyHandled ? "{\"code\":404}" : "{}").getBytes(StandardCharsets.UTF_8);
            if (!token) calls.add(path);
            exchange.sendResponseHeaders(alreadyHandled ? 404 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try (HttpClient client = QqTestHttpClient.create(server.getAddress().getPort())) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("app-id", "app");
            yaml.set("app-secret", "secret");
            QqOpenApiClient api = new QqOpenApiClient(client, QqPlatformConfig.from(yaml));
            try (QqGroupJoinApproval approval = new QqGroupJoinApproval(config(), (request, approve) ->
                    api.reviewGroupJoinRequest(request.groupOpenId(), request.memberOpenId(), request.joinRequestId(), approve),
                    Logger.getAnonymousLogger(), Duration.ZERO)) {
                assertEquals(Acceptance.ACCEPTED, approval.accept(request("group-a", "old", "359152")));
                assertNotNull(calls.poll(3, TimeUnit.SECONDS));
                assertEquals(Acceptance.ACCEPTED, approval.accept(request("group-b", "valid", "946038")));
                assertEquals("/v2/groups/group-b/approval_join_request/member-valid", calls.poll(3, TimeUnit.SECONDS));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void eventAndListDeliveriesShareInFlightAndCompletedDeduplication() throws Exception {
        LinkedBlockingQueue<CompletableFuture<Void>> reviews = new LinkedBlockingQueue<>();
        try (QqGroupJoinApproval approval = new QqGroupJoinApproval(config(), (_, approve) -> {
            assertTrue(approve);
            CompletableFuture<Void> result = new CompletableFuture<>();
            reviews.add(result);
            return result;
        }, Logger.getAnonymousLogger(), Duration.ZERO)) {
            var payload = com.google.gson.JsonParser.parseString("""
                    {"group_openid":"group-a","member_openid":"member","join_request_id":"same",
                     "apply_at":"1970-01-01T00:01:00Z",
                     "verify_info":{"method":"verify_message","verify_message":"359152"}}
                    """);
            QqGroupJoinRequest event = QqGroupJoinRequest.fromEvent(payload, Instant.now()).orElseThrow();
            QqGroupJoinRequest listed = QqGroupJoinRequest.fromListItem(payload, "group-a", Instant.now()).orElseThrow();
            assertEquals(Acceptance.ACCEPTED, approval.accept(event));
            CompletableFuture<Void> result = reviews.poll(3, TimeUnit.SECONDS);
            assertNotNull(result);
            assertEquals(Acceptance.IGNORED, approval.accept(listed));
            result.complete(null);
            for (int index = 0; index < 10; index++) assertEquals(Acceptance.IGNORED, approval.accept(listed));
            assertTrue(reviews.isEmpty());
        }
    }

    @Test
    void approvalRequestsAreSerializedAndPacedAcrossGroups() throws Exception {
        LinkedBlockingQueue<Long> starts = new LinkedBlockingQueue<>();
        try (QqGroupJoinApproval approval = new QqGroupJoinApproval(config(), (_, _) -> {
            starts.add(System.nanoTime());
            return CompletableFuture.completedFuture(null);
        }, Logger.getAnonymousLogger(), Duration.ofMillis(40))) {
            assertEquals(Acceptance.ACCEPTED, approval.accept(request("group-a", "one", "359152")));
            Long first = starts.poll(3, TimeUnit.SECONDS);
            assertNotNull(first);
            assertEquals(Acceptance.ACCEPTED, approval.accept(request("group-b", "two", "946038")));
            Long second = starts.poll(3, TimeUnit.SECONDS);
            assertNotNull(second);
            assertTrue(second - first >= Duration.ofMillis(40).toNanos());
        }
    }

    @Test
    void reviewsTheExactApplicationWithItsOwnGroupSecretAndApplicationWindow() throws Exception {
        LinkedBlockingQueue<Decision> decisions = new LinkedBlockingQueue<>();
        try (QqGroupJoinApproval approval = new QqGroupJoinApproval(config(), (request, approve) -> {
            decisions.add(new Decision(request, approve));
            return CompletableFuture.completedFuture(null);
        }, Logger.getAnonymousLogger(), Duration.ZERO)) {
            String[][] samples = {
                    {"group-a", "359152", "true"},
                    {"group-a", "287082", "true"},
                    {"group-a", "755224", "false"},
                    {"group-a", "969429", "false"},
                    {"group-a", "946038", "false"},
                    {"group-b", "946038", "true"},
                    {"group-b", "359152", "false"},
                    {"group-a", "", "false"}
            };
            for (int index = 0; index < samples.length; index++) {
                String[] sample = samples[index];
                QqGroupJoinRequest request = request(sample[0], "application-" + index, sample[1]);
                assertEquals(Acceptance.ACCEPTED, approval.accept(request));
                Decision decision = decisions.poll(3, TimeUnit.SECONDS);
                assertNotNull(decision);
                assertEquals(request, decision.request());
                assertEquals(Boolean.parseBoolean(sample[2]), decision.approve());
                assertEquals(Acceptance.IGNORED, approval.accept(request));
            }
            assertEquals(Acceptance.IGNORED,
                    approval.accept(request("unconfigured", "other", "359152")));
            assertTrue(decisions.isEmpty());
        }
    }

    @Test
    void boundsPendingApprovalsAndCancelsThemOnSessionClose() throws Exception {
        LinkedBlockingQueue<CompletableFuture<Void>> pending = new LinkedBlockingQueue<>();
        QqGroupJoinApproval approval = new QqGroupJoinApproval(config(1), (_, _) -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            pending.add(future);
            return future;
        }, Logger.getAnonymousLogger(), Duration.ZERO);
        try {
            assertEquals(Acceptance.ACCEPTED,
                    approval.accept(request("group-a", "first", "359152")));
            CompletableFuture<Void> first = pending.poll(3, TimeUnit.SECONDS);
            assertNotNull(first);
            QqGroupJoinRequest second = request("group-a", "second", "287082");
            assertEquals(Acceptance.RETRY_LATER, approval.accept(second));
            assertEquals(Acceptance.RETRY_LATER, approval.accept(second));
            approval.close();
            first.handle((_, _) -> null).get(3, TimeUnit.SECONDS);
            assertTrue(first.isCancelled());
            assertEquals(Acceptance.IGNORED, approval.accept(second));
            assertTrue(pending.isEmpty());
        } finally {
            approval.close();
        }
    }

    @Test
    void failedRemoteReviewCanBeRedeliveredWithoutExposingTheAnswerInLogs() throws Exception {
        LinkedBlockingQueue<String> warnings = new LinkedBlockingQueue<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) {
                warnings.add(record.getMessage());
            }
            @Override public void flush() { }
            @Override public void close() { }
        });
        try (QqGroupJoinApproval approval = new QqGroupJoinApproval(config(), (_, _) ->
                CompletableFuture.failedFuture(new IllegalStateException("secret 359152")), logger, Duration.ZERO)) {
            QqGroupJoinRequest request = request("group-a", "retry", "359152");
            assertEquals(Acceptance.ACCEPTED, approval.accept(request));
            String warning = warnings.poll(3, TimeUnit.SECONDS);
            assertNotNull(warning);
            assertTrue(!warning.contains("359152"));
            assertEquals(Acceptance.ACCEPTED, approval.accept(request));
        }
    }

    private static QqGroupJoinRequest request(String group, String id, String code) {
        // A historical application time deliberately differs from the worker's wall clock.
        return new QqGroupJoinRequest(group, "member-" + id, id,
                List.of(code), Instant.ofEpochSecond(60));
    }

    private static QqPlatformConfig config() throws Exception {
        return config(4);
    }

    private static QqPlatformConfig config(int workers) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                worker:
                  max-concurrent-tasks: %d
                group-join:
                  groups:
                    - group-openid: group-a
                      totp-secret: GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
                    - group-openid: group-b
                      totp-secret: JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP
                """.formatted(workers));
        return QqPlatformConfig.from(yaml);
    }

    private record Decision(QqGroupJoinRequest request, boolean approve) { }
}
