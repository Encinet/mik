package org.encinet.mik.feature.community.social.platform.qq.join;

import org.encinet.mik.feature.community.social.api.SocialEventSink.Acceptance;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformConfig;
import org.encinet.mik.feature.community.social.platform.qq.client.QqHttpException;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

/** Fair, paced catch-up across groups; one page per turn prevents backlog starvation. */
public final class QqGroupJoinReconciler implements AutoCloseable {
    private static final int MAXIMUM_CURSOR_HISTORY = 64;
    private final List<Group> groups;
    private final long intervalNanos;
    private final long minimumRequestGapNanos;
    private final BiFunction<String, String, CompletableFuture<QqGroupJoinPage>> pages;
    private final Function<QqGroupJoinRequest, Acceptance> applications;
    private final Logger logger;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("mik-qq-join-reconcile-", 0).factory());
    private ScheduledFuture<?> scheduled;
    private CompletableFuture<QqGroupJoinPage> pending;
    private Group running;
    private long scheduleGeneration;
    private long nextRequestNanos = System.nanoTime();
    private boolean started;
    private boolean closed;

    public QqGroupJoinReconciler(QqPlatformConfig config, QqOpenApiClient api,
                                QqGroupJoinApproval approvals, Logger logger) {
        this(config.groupJoinRules().stream().map(QqGroupJoinRule::groupOpenId).toList(),
                Duration.ofSeconds(config.groupJoinReconcileIntervalSeconds()), Duration.ofMillis(2_100),
                api::listGroupJoinRequests, approvals::accept, logger);
    }

    QqGroupJoinReconciler(List<String> groups, Duration interval, Duration minimumRequestGap,
                          BiFunction<String, String, CompletableFuture<QqGroupJoinPage>> pages,
                          Function<QqGroupJoinRequest, Acceptance> applications, Logger logger) {
        this.groups = groups.stream().map(Group::new).toList();
        if (interval.isNegative() || interval.isZero() || minimumRequestGap.isNegative()) {
            throw new IllegalArgumentException("Scan interval must be positive and request gap must not be negative");
        }
        intervalNanos = interval.toNanos();
        minimumRequestGapNanos = minimumRequestGap.toNanos();
        this.pages = Objects.requireNonNull(pages, "pages");
        this.applications = Objects.requireNonNull(applications, "applications");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized void start() {
        if (started || closed || groups.isEmpty()) return;
        started = true;
        requestScan();
    }

    /** Coalesces READY/RESUMED signals and retains API failure cooldowns. */
    public synchronized void requestScan() {
        if (!started || closed) return;
        long now = System.nanoTime();
        for (Group group : groups) {
            group.due = now;
            if (group == running) group.rescan = true;
        }
        scheduleNext();
    }

    private void scheduleNext() {
        if (closed || !started || running != null || groups.isEmpty()) return;
        if (scheduled != null) scheduled.cancel(false);
        long due = groups.stream().mapToLong(Group::nextDue).min().orElseThrow();
        long delay = Math.max(0, Math.max(due, nextRequestNanos) - System.nanoTime());
        long generation = ++scheduleGeneration;
        scheduled = scheduler.schedule(() -> reconcileNext(generation), delay, TimeUnit.NANOSECONDS);
    }

    private void reconcileNext(long generation) {
        final Group group;
        synchronized (this) {
            if (closed || generation != scheduleGeneration) return;
            group = groups.stream().min(java.util.Comparator.comparingLong(Group::nextDue)).orElseThrow();
            if (Math.max(group.nextDue(), nextRequestNanos) > System.nanoTime()) {
                scheduleNext();
                return;
            }
            scheduled = null;
            running = group;
            nextRequestNanos = System.nanoTime() + minimumRequestGapNanos;
        }
        try {
            final CompletableFuture<QqGroupJoinPage> request;
            synchronized (this) {
                if (closed) return;
                request = pages.apply(group.id, group.cursor);
                pending = request;
            }
            QqGroupJoinPage page = request.get();
            boolean backpressured = false;
            for (QqGroupJoinRequest application : page.requests()) {
                synchronized (this) {
                    if (closed) return;
                }
                if (applications.apply(application) == Acceptance.RETRY_LATER) backpressured = true;
            }
            synchronized (this) {
                if (closed) return;
                group.failures = 0;
                group.blockedUntil = Long.MIN_VALUE;
                if (page.skippedEntries() > 0 && page.skippedEntries() != group.skippedEntries) {
                    logger.warning("QQ group join list contains applications needing manual review (group " + group.id + ")");
                }
                group.skippedEntries = page.skippedEntries();
                if (backpressured) {
                    // Re-fetch this page; accepted applications share the event deduplication ledger.
                    group.due = System.nanoTime() + Math.min(intervalNanos, Duration.ofSeconds(5).toNanos());
                } else if (page.nextCursor().isEmpty()) {
                    group.resetCursor();
                    group.due = System.nanoTime() + intervalNanos;
                } else if (page.nextCursor().equals(group.cursor)
                        || !group.cursors.add(page.nextCursor())
                        || group.cursors.size() > MAXIMUM_CURSOR_HISTORY) {
                    group.resetCursor();
                    group.due = System.nanoTime() + intervalNanos;
                    logger.warning("QQ group join pagination did not finish; restarting on the next scan (group " + group.id + ")");
                } else {
                    group.cursor = page.nextCursor();
                    group.due = System.nanoTime();
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | RuntimeException error) {
            synchronized (this) {
                if (!closed) {
                    group.failures = Math.min(group.failures + 1, 5);
                    long delay = Math.min(Duration.ofMinutes(5).toNanos(), intervalNanos << (group.failures - 1));
                    Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
                    if (cause instanceof QqHttpException httpError) {
                        delay = Math.max(delay, Duration.ofMillis(httpError.retryAfterMillis()).toNanos());
                    }
                    group.resetCursor();
                    group.blockedUntil = System.nanoTime() + delay;
                    group.due = group.blockedUntil;
                    // Neither verification text nor remote error bodies belong in logs.
                    logger.warning("Could not reconcile QQ group join applications; retrying later (group " + group.id + ")");
                }
            }
        } finally {
            synchronized (this) {
                pending = null;
                running = null;
                if (group.rescan) {
                    group.rescan = false;
                    group.due = Math.min(group.due, System.nanoTime());
                }
                scheduleNext();
            }
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (scheduled != null) scheduled.cancel(false);
        if (pending != null) pending.cancel(true);
        scheduler.shutdownNow();
    }

    private static final class Group {
        private final String id;
        private final Set<String> cursors = new HashSet<>();
        private String cursor = "";
        private long due;
        private long blockedUntil = Long.MIN_VALUE;
        private int failures;
        private int skippedEntries;
        private boolean rescan;

        private Group(String id) { this.id = id; }
        private long nextDue() { return Math.max(due, blockedUntil); }
        private void resetCursor() { cursor = ""; cursors.clear(); }
    }
}
