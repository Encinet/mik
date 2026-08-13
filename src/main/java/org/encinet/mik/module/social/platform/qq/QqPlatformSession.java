package org.encinet.mik.module.social.platform.qq;

import org.encinet.mik.module.social.api.SocialPlatformSession;
import org.encinet.mik.module.social.platform.qq.gateway.QqGatewaySession;

import java.net.http.HttpClient;
import java.util.Objects;

/** Closes the QQ transport and its dedicated HTTP client together. */
final class QqPlatformSession implements SocialPlatformSession {
    private final QqGatewaySession gateway;
    private final HttpClient httpClient;

    QqPlatformSession(QqGatewaySession gateway, HttpClient httpClient) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public void start() {
        gateway.start();
    }

    @Override
    public void close() {
        gateway.close();
        httpClient.shutdownNow();
    }
}
