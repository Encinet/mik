package org.encinet.mik.feature.community.social.platform.qq.join;

import org.encinet.mik.feature.community.social.api.SocialEventSink.Acceptance;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void repeatedCursorStopsTheCycleAndTheNextScanRestartsFromTheHead() throws Exception {
        LinkedBlockingQueue<String> warnings = new LinkedBlockingQueue<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { warnings.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
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

    private static QqGroupJoinRequest request(String group) {
        return new QqGroupJoinRequest(group, "member", "request", List.of("359152"), Instant.ofEpochSecond(60));
    }

    private record Fetch(String group, String cursor, long atNanos, CompletableFuture<QqGroupJoinPage> response) { }
}
