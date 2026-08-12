package org.encinet.mik.module.social.platform.qq.gateway;

import org.encinet.mik.module.social.platform.qq.QqPlatformConfig;

import java.util.Objects;

/** Encapsulates QQ Gateway reconnect backoff and close-code semantics. */
final class QqGatewayReconnectPolicy {

    private final long baseDelayMillis;
    private final long maximumDelayMillis;
    private final int maximumAttempts;

    QqGatewayReconnectPolicy(QqPlatformConfig config) {
        Objects.requireNonNull(config, "config");
        baseDelayMillis = config.gatewayReconnectBaseDelayMillis();
        maximumDelayMillis = config.gatewayReconnectMaxDelayMillis();
        maximumAttempts = config.gatewayMaxReconnectAttempts();
    }

    int maximumAttempts() {
        return maximumAttempts;
    }

    boolean exhausted(int attempts) {
        return attempts >= maximumAttempts;
    }

    long delayMillis(int attempt) {
        long delay = baseDelayMillis;
        for (int index = 1; index < attempt; index++) {
            if (delay >= maximumDelayMillis / 2) {
                return maximumDelayMillis;
            }
            delay *= 2;
        }
        return Math.min(delay, maximumDelayMillis);
    }

    CloseAction closeAction(int statusCode) {
        if (isFatal(statusCode)) {
            return CloseAction.FAIL;
        }
        if (statusCode == 4004) {
            return CloseAction.REFRESH_TOKEN;
        }
        if (statusCode < 4000 || statusCode == 4008 || statusCode == 4009) {
            return CloseAction.RESUME;
        }
        return CloseAction.REIDENTIFY;
    }

    private static boolean isFatal(int statusCode) {
        return statusCode == 4001 || statusCode == 4002
                || statusCode >= 4010 && statusCode <= 4014
                || statusCode == 4914 || statusCode == 4915;
    }

    enum CloseAction {
        RESUME,
        REIDENTIFY,
        REFRESH_TOKEN,
        FAIL
    }
}
