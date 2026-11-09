package org.encinet.mik.feature.community.social.platform.qq.join;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.feature.community.social.api.SocialEventSink.Acceptance;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformConfig;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewayJoinRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
    void reviewsTheExactApplicationWithItsOwnGroupSecretAndReceiveWindow() throws Exception {
        LinkedBlockingQueue<Decision> decisions = new LinkedBlockingQueue<>();
        try (QqGroupJoinApproval approval = new QqGroupJoinApproval(config(), (request, approve) -> {
            decisions.add(new Decision(request, approve));
            return CompletableFuture.completedFuture(null);
        }, Logger.getAnonymousLogger())) {
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
                QqGatewayJoinRequest request = request(sample[0], "application-" + index, sample[1]);
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
        }, Logger.getAnonymousLogger());
        try {
            assertEquals(Acceptance.ACCEPTED,
                    approval.accept(request("group-a", "first", "359152")));
            CompletableFuture<Void> first = pending.poll(3, TimeUnit.SECONDS);
            assertNotNull(first);
            QqGatewayJoinRequest second = request("group-a", "second", "287082");
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
                CompletableFuture.failedFuture(new IllegalStateException("secret 359152")), logger)) {
            QqGatewayJoinRequest request = request("group-a", "retry", "359152");
            assertEquals(Acceptance.ACCEPTED, approval.accept(request));
            String warning = warnings.poll(3, TimeUnit.SECONDS);
            assertNotNull(warning);
            assertTrue(!warning.contains("359152"));
            assertEquals(Acceptance.ACCEPTED, approval.accept(request));
        }
    }

    private static QqGatewayJoinRequest request(String group, String id, String code) {
        // A historical receive time deliberately differs from the worker's wall clock.
        return new QqGatewayJoinRequest(group, "member-" + id, id,
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

    private record Decision(QqGatewayJoinRequest request, boolean approve) { }
}
