package org.encinet.mik.feature.community.social.platform.qq.join;

import org.encinet.mik.feature.community.social.api.SocialEventSink.Acceptance;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformConfig;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.feature.community.social.platform.qq.client.QqHttpException;
import org.encinet.mik.feature.community.social.runtime.SocialEventDeduplicator;
import org.encinet.mik.feature.community.social.runtime.SocialTaskExecutor;

import java.time.Duration;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Bounded, deduplicated group approvals owned by one QQ platform session. */
public final class QqGroupJoinApproval implements AutoCloseable {
    private final Map<String, QqTotp> groups;
    private final BiFunction<QqGroupJoinRequest, Boolean, CompletableFuture<Void>> review;
    private final SocialTaskExecutor workers;
    private final SocialEventDeduplicator requests = new SocialEventDeduplicator(
            4_096, Duration.ofMinutes(10));
    private final Set<String> inFlight = new HashSet<>();
    private final Semaphore reviewPermit = new Semaphore(1, true);
    private final long minimumReviewGapNanos;
    private long nextReviewNanos;
    private int consecutiveFailures;
    private final Logger logger;
    private boolean closed;

    public QqGroupJoinApproval(QqPlatformConfig config, QqOpenApiClient api, Logger logger) {
        this(config, (request, approve) -> api.reviewGroupJoinRequest(
                request.groupOpenId(), request.memberOpenId(), request.joinRequestId(),
                approve), logger, Duration.ofMillis(1_100));
    }

    QqGroupJoinApproval(
            QqPlatformConfig config,
            BiFunction<QqGroupJoinRequest, Boolean, CompletableFuture<Void>> review,
            Logger logger,
            Duration minimumReviewGap
    ) {
        groups = config.groupJoinRules().stream().collect(Collectors.toUnmodifiableMap(
                QqGroupJoinRule::groupOpenId, QqGroupJoinRule::totp));
        workers = new SocialTaskExecutor(config.workerMaxConcurrentTasks(), "mik-qq-join-");
        this.review = Objects.requireNonNull(review, "review");
        this.logger = Objects.requireNonNull(logger, "logger");
        if (minimumReviewGap.isNegative()) throw new IllegalArgumentException("Review gap must not be negative");
        minimumReviewGapNanos = minimumReviewGap.toNanos();
        nextReviewNanos = System.nanoTime();
    }

    public synchronized Acceptance accept(QqGroupJoinRequest request) {
        QqTotp totp = groups.get(request.groupOpenId());
        if (closed || totp == null) {
            return Acceptance.IGNORED;
        }
        String key = request.groupOpenId() + '\0' + request.joinRequestId();
        if (inFlight.contains(key) || !requests.accept(key)) {
            return Acceptance.IGNORED;
        }
        inFlight.add(key);
        if (!workers.submit(() -> process(request, totp, key))) {
            requests.forget(key);
            inFlight.remove(key);
            return Acceptance.RETRY_LATER;
        }
        return Acceptance.ACCEPTED;
    }

    private void process(QqGroupJoinRequest request, QqTotp totp, String key) {
        CompletableFuture<Void> pending = null;
        boolean acquired = false;
        boolean completed = false;
        boolean failed = false;
        try {
            boolean approve = request.answers().stream()
                    .anyMatch(answer -> totp.matches(answer, request.appliedAt()));
            reviewPermit.acquire();
            acquired = true;
            long delay = nextReviewNanos - System.nanoTime();
            if (delay > 0) Thread.sleep(Duration.ofNanos(delay));
            synchronized (this) {
                if (closed) {
                    return;
                }
                pending = review.apply(request, approve);
                nextReviewNanos = System.nanoTime() + minimumReviewGapNanos;
            }
            pending.get();
            consecutiveFailures = 0;
            completed = true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (pending != null) {
                pending.cancel(true);
            }
        } catch (ExecutionException | RuntimeException error) {
            failed = true;
            Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
            boolean transientFailure = !(cause instanceof QqHttpException httpError)
                    || httpError.statusCode() < 400 || httpError.statusCode() == 401 || httpError.statusCode() == 429
                    || httpError.statusCode() >= 500 || httpError.retryAfterMillis() > 0;
            // An application already handled elsewhere must not delay valid applications in either group.
            if (acquired && transientFailure) {
                consecutiveFailures = Math.min(consecutiveFailures + 1, 5);
                long backoffMillis = Math.min(300_000, 30_000L << (consecutiveFailures - 1));
                if (cause instanceof QqHttpException httpError) {
                    backoffMillis = Math.max(backoffMillis, httpError.retryAfterMillis());
                }
                nextReviewNanos = System.nanoTime() + Duration.ofMillis(backoffMillis).toNanos();
            }
        } finally {
            synchronized (this) {
                inFlight.remove(key);
                requests.forget(key);
                if (completed && !closed) requests.accept(key);
            }
            if (acquired) reviewPermit.release();
            // Remote errors may echo verification answers; do not log their bodies.
            if (failed) logger.warning("QQ group join approval failed; retrying through the pending application list");
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            workers.close();
            requests.clear();
            inFlight.clear();
        }
    }
}
