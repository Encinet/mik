package org.encinet.mik.feature.community.social.platform.qq.join;

import org.encinet.mik.feature.community.social.api.SocialEventSink.Acceptance;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformConfig;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewayJoinRequest;
import org.encinet.mik.feature.community.social.runtime.SocialEventDeduplicator;
import org.encinet.mik.feature.community.social.runtime.SocialTaskExecutor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Bounded, deduplicated group approvals owned by one QQ platform session. */
public final class QqGroupJoinApproval implements AutoCloseable {
    private final Map<String, QqTotp> groups;
    private final BiFunction<QqGatewayJoinRequest, Boolean, CompletableFuture<Void>> review;
    private final SocialTaskExecutor workers;
    private final SocialEventDeduplicator requests = new SocialEventDeduplicator(
            4_096, Duration.ofMinutes(10));
    private final Logger logger;
    private boolean closed;

    public QqGroupJoinApproval(QqPlatformConfig config, QqOpenApiClient api, Logger logger) {
        this(config, (request, approve) -> api.reviewGroupJoinRequest(
                request.groupOpenId(), request.memberOpenId(), request.joinRequestId(),
                approve), logger);
    }

    QqGroupJoinApproval(
            QqPlatformConfig config,
            BiFunction<QqGatewayJoinRequest, Boolean, CompletableFuture<Void>> review,
            Logger logger
    ) {
        groups = config.groupJoinRules().stream().collect(Collectors.toUnmodifiableMap(
                QqGroupJoinRule::groupOpenId, QqGroupJoinRule::totp));
        workers = new SocialTaskExecutor(config.workerMaxConcurrentTasks(), "mik-qq-join-");
        this.review = review;
        this.logger = logger;
    }

    public synchronized Acceptance accept(QqGatewayJoinRequest request) {
        QqTotp totp = groups.get(request.groupOpenId());
        if (closed || totp == null) {
            return Acceptance.IGNORED;
        }
        String key = request.groupOpenId() + '\0' + request.joinRequestId();
        if (!requests.accept(key)) {
            return Acceptance.IGNORED;
        }
        if (!workers.submit(() -> process(request, totp, key))) {
            requests.forget(key);
            return Acceptance.RETRY_LATER;
        }
        return Acceptance.ACCEPTED;
    }

    private void process(QqGatewayJoinRequest request, QqTotp totp, String key) {
        CompletableFuture<Void> pending = null;
        try {
            boolean approve = request.answers().stream()
                    .anyMatch(answer -> totp.matches(answer, request.receivedAt()));
            synchronized (this) {
                if (closed) {
                    return;
                }
                pending = review.apply(request, approve);
            }
            pending.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (pending != null) {
                pending.cancel(true);
            }
            requests.forget(key);
        } catch (ExecutionException | RuntimeException error) {
            requests.forget(key);
            // Remote errors may echo verification answers; do not log their bodies.
            logger.warning("QQ group join approval failed; the application may need manual review");
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            workers.close();
            requests.clear();
        }
    }
}
