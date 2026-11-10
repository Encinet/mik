package org.encinet.mik.feature.community.social.platform.qq.join;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.feature.community.social.api.SocialEventSink.Acceptance;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformConfig;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.junit.jupiter.api.Test;
import org.encinet.mik.test.QqTestHttpClient;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqGroupJoinReconcilerTest {
    private final LinkedBlockingQueue<Fetch> fetches = new LinkedBlockingQueue<>();

    @Test
    void startsImmediatelyAndPaginatesFairlyAcrossGroupsWithPacedRequests() throws Exception {
        LinkedBlockingQueue<QqGroupJoinRequest> applications = new LinkedBlockingQueue<>();
        try (QqGroupJoinReconciler reconciler = reconciler(List.of("a", "b"), Duration.ofHours(1),
                Duration.ofMillis(40), request -> {
                    applications.add(request);
                    return Acceptance.ACCEPTED;
                }, Logger.getAnonymousLogger())) {
            reconciler.start();
            reconciler.start();
            Fetch first = next();
            assertEquals("a", first.group());
            assertEquals("", first.cursor());
            QqGroupJoinRequest historical = request("a");
            first.response().complete(new QqGroupJoinPage(List.of(historical), "a-page-2", 0));
            assertEquals(historical, applications.poll(3, TimeUnit.SECONDS));
            Fetch second = next();
            assertEquals("b", second.group());
            assertTrue(second.atNanos() - first.atNanos() >= Duration.ofMillis(40).toNanos());
            second.response().complete(emptyPage());
            Fetch third = next();
            assertEquals("a", third.group());
            assertEquals("a-page-2", third.cursor());
            third.response().complete(emptyPage());
            assertNull(fetches.poll(80, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void completedScansRepeatOnTheConfiguredInterval() throws Exception {
        try (QqGroupJoinReconciler reconciler = reconciler(List.of("a"), Duration.ofMillis(40),
                Duration.ZERO, _ -> Acceptance.IGNORED, Logger.getAnonymousLogger())) {
            reconciler.start();
            Fetch first = next();
            long completedAt = System.nanoTime();
            first.response().complete(emptyPage());
            Fetch second = next();
            assertEquals("", second.cursor());
            assertTrue(second.atNanos() - completedAt >= Duration.ofMillis(40).toNanos());
        }
    }

    @Test
    void reconnectDuringARequestCoalescesAnImmediateRescanAndCloseCancelsIt() throws Exception {
        QqGroupJoinReconciler reconciler = reconciler(List.of("a"), Duration.ofHours(1),
                Duration.ZERO, _ -> Acceptance.IGNORED, Logger.getAnonymousLogger());
        try {
            reconciler.start();
            Fetch first = next();
            for (int index = 0; index < 10; index++) reconciler.requestScan();
            first.response().complete(emptyPage());
            Fetch recovery = next();
            assertEquals("", recovery.cursor());
            assertNull(fetches.poll(40, TimeUnit.MILLISECONDS));
            reconciler.close();
            assertTrue(recovery.response().isCancelled());
            reconciler.requestScan();
            reconciler.start();
            assertNull(fetches.poll(40, TimeUnit.MILLISECONDS));
        } finally {
            reconciler.close();
        }
    }

    @Test
    void backpressureRetriesTheSamePageBeforeAdvancingItsCursor() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (QqGroupJoinReconciler reconciler = reconciler(List.of("a"), Duration.ofMillis(30),
                Duration.ZERO, _ -> attempts.incrementAndGet() == 1 ? Acceptance.RETRY_LATER : Acceptance.ACCEPTED,
                Logger.getAnonymousLogger())) {
            reconciler.start();
            QqGroupJoinPage page = new QqGroupJoinPage(List.of(request("a")), "next-page", 0);
            next().response().complete(page);
            Fetch retry = next();
            assertEquals("", retry.cursor());
            retry.response().complete(page);
            Fetch continuation = next();
            assertEquals("next-page", continuation.cursor());
            assertEquals(2, attempts.get());
        }
    }

    @Test
    void oneGroupsFailureDoesNotBlockOthersOrLoseItsCooldownOnReconnect() throws Exception {
        try (QqGroupJoinReconciler reconciler = reconciler(List.of("a", "b"), Duration.ofMillis(300),
                Duration.ZERO, _ -> Acceptance.IGNORED, Logger.getAnonymousLogger())) {
            reconciler.start();
            Fetch failed = next();
            failed.response().completeExceptionally(new IllegalStateException("remote failure"));
            Fetch healthy = next();
            assertEquals("b", healthy.group());
            reconciler.requestScan();
            healthy.response().complete(emptyPage());
            Fetch healthyRescan = next();
            assertEquals("b", healthyRescan.group());
            healthyRescan.response().complete(emptyPage());
            Fetch retry = next();
            assertEquals("a", retry.group());
            assertEquals("", retry.cursor());
            assertTrue(retry.atNanos() - failed.atNanos() >= Duration.ofMillis(300).toNanos());
        }
    }

    @Test
    void httpFailureLogsCodesAndHonorsRetryAfterWithoutExposingRemoteText() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        LinkedBlockingQueue<Long> requests = new LinkedBlockingQueue<>();
        server.createContext("/", exchange -> {
            boolean token = exchange.getRequestURI().getPath().equals("/app/getAppAccessToken");
            if (!token) requests.add(System.nanoTime());
            byte[] body = (token ? "{\"access_token\":\"private-token\",\"expires_in\":7200}" : """
                    {"err_code":40012019,"message":"verification-answer 005924 private-token",
                    "trace_id":"untrusted-trace private-token"}
                    """).getBytes(StandardCharsets.UTF_8);
            if (!token) exchange.getResponseHeaders().set("Retry-After", "1");
            exchange.sendResponseHeaders(token ? 200 : 429, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            exchange.close();
        });
        server.start();
        LinkedBlockingQueue<LogRecord> warnings = new LinkedBlockingQueue<>();
        Logger logger = capturingLogger(warnings);
        try (HttpClient client = QqTestHttpClient.create(server.getAddress().getPort())) {
            YamlConfiguration yaml = new YamlConfiguration();
            QqOpenApiClient api = new QqOpenApiClient(client, QqPlatformConfig.from(yaml));
            try (QqGroupJoinReconciler reconciler = new QqGroupJoinReconciler(List.of("a"),
                    Duration.ofMillis(20), Duration.ZERO, api::listGroupJoinRequests,
                    _ -> Acceptance.IGNORED, logger)) {
                reconciler.start();
                LogRecord warning = warnings.poll(3, TimeUnit.SECONDS);
                assertNotNull(warning);
                assertTrue(warning.getMessage().contains("List QQ group join requests failed (HTTP 429, err_code=40012019)"));
                assertTrue(warning.getMessage().contains("retrying in 1000 ms"));
                assertFalse(warning.getMessage().contains("005924"));
                assertFalse(warning.getMessage().contains("private-token"));
                assertNull(warning.getThrown());
                assertNull(warnings.poll(100, TimeUnit.MILLISECONDS));
                // A reconnect must retain the server's cooldown as well.
                reconciler.requestScan();
                assertNull(warnings.poll(100, TimeUnit.MILLISECONDS));
                LogRecord retry = warnings.poll(3, TimeUnit.SECONDS);
                assertNotNull(retry);
                long firstRequest = requests.remove();
                assertTrue(requests.remove() - firstRequest >= Duration.ofSeconds(1).toNanos());
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void wrappedNonHttpFailuresLogOnlyTheirTypeAndRetryDelay() throws Exception {
        LinkedBlockingQueue<LogRecord> warnings = new LinkedBlockingQueue<>();
        try (QqGroupJoinReconciler reconciler = reconciler(List.of("a"), Duration.ofHours(1),
                Duration.ZERO, _ -> Acceptance.IGNORED, capturingLogger(warnings))) {
            reconciler.start();
            next().response().completeExceptionally(new CompletionException(
                    new IllegalArgumentException("verification-answer 005924 private-token")));
            LogRecord warning = warnings.poll(3, TimeUnit.SECONDS);
            assertNotNull(warning);
            assertTrue(warning.getMessage().contains("group a; IllegalArgumentException"));
            assertTrue(warning.getMessage().contains("retrying in 300000 ms"));
            assertFalse(warning.getMessage().contains("005924"));
            assertFalse(warning.getMessage().contains("private-token"));
            assertNull(warning.getThrown());
        }
    }

    @Test
    void repeatedCursorStopsTheCycleAndTheNextScanRestartsFromTheHead() throws Exception {
        LinkedBlockingQueue<LogRecord> warnings = new LinkedBlockingQueue<>();
        Logger logger = capturingLogger(warnings);
        try (QqGroupJoinReconciler reconciler = reconciler(List.of("a"), Duration.ofHours(1),
                Duration.ZERO, _ -> Acceptance.IGNORED, logger)) {
            reconciler.start();
            next().response().complete(new QqGroupJoinPage(List.of(), "same", 0));
            Fetch second = next();
            assertEquals("same", second.cursor());
            second.response().complete(new QqGroupJoinPage(List.of(), "same", 0));
            assertNotNull(warnings.poll(3, TimeUnit.SECONDS));
            assertNull(fetches.poll(30, TimeUnit.MILLISECONDS));
            reconciler.requestScan();
            assertEquals("", next().cursor());
        }
    }

    @Test
    void emptyConfigurationStartsNoNetworkWork() throws Exception {
        try (QqGroupJoinReconciler reconciler = reconciler(List.of(), Duration.ofSeconds(30),
                Duration.ZERO, _ -> Acceptance.IGNORED, Logger.getAnonymousLogger())) {
            reconciler.start();
            reconciler.requestScan();
            assertNull(fetches.poll(30, TimeUnit.MILLISECONDS));
        }
    }

    private QqGroupJoinReconciler reconciler(List<String> groups, Duration interval, Duration gap,
                                             Function<QqGroupJoinRequest, Acceptance> applications, Logger logger) {
        return new QqGroupJoinReconciler(groups, interval, gap, (group, cursor) -> {
            CompletableFuture<QqGroupJoinPage> response = new CompletableFuture<>();
            fetches.add(new Fetch(group, cursor, System.nanoTime(), response));
            return response;
        }, applications, logger);
    }

    private Fetch next() throws InterruptedException {
        Fetch fetch = fetches.poll(3, TimeUnit.SECONDS);
        assertNotNull(fetch, "Expected a list request");
        return fetch;
    }

    private static QqGroupJoinPage emptyPage() { return new QqGroupJoinPage(List.of(), "", 0); }

    private static Logger capturingLogger(LinkedBlockingQueue<LogRecord> warnings) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new java.util.logging.Handler() {
            @Override public void publish(LogRecord record) { warnings.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        return logger;
    }

    private static QqGroupJoinRequest request(String group) {
        return new QqGroupJoinRequest(group, "member", "request", List.of("359152"), Instant.ofEpochSecond(60));
    }

    private record Fetch(String group, String cursor, long atNanos, CompletableFuture<QqGroupJoinPage> response) { }
}
