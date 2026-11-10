package org.encinet.mik.feature.community.social.platform.qq.gateway;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.encinet.mik.feature.community.social.platform.qq.client.QqApiErrors;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Discovers the current QQ bot Gateway using the authenticated OpenAPI endpoint. */
final class QqGatewayDiscovery {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final QqPlatformConfig config;
    private final Supplier<CompletableFuture<String>> accessTokens;
    private final Runnable invalidateToken;

    QqGatewayDiscovery(
            HttpClient httpClient,
            QqPlatformConfig config,
            Supplier<CompletableFuture<String>> accessTokens,
            Runnable invalidateToken
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.config = Objects.requireNonNull(config, "config");
        this.accessTokens = Objects.requireNonNull(accessTokens, "accessTokens");
        this.invalidateToken = Objects.requireNonNull(invalidateToken, "invalidateToken");
    }

    CompletableFuture<URI> discover() {
        return discover(false);
    }

    private CompletableFuture<URI> discover(boolean retriedAuthentication) {
        return accessTokens.get().thenCompose(token -> {
            HttpRequest request = HttpRequest.newBuilder(config.apiUri("/gateway/bot"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "QQBot " + token)
                    .header("X-Union-Appid", config.appId())
                    .GET()
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
                if (!retriedAuthentication && response.statusCode() == 401) {
                    invalidateToken.run();
                    return discover(true);
                }
                try {
                    return CompletableFuture.completedFuture(parseResponse(response, token));
                } catch (RuntimeException error) {
                    return CompletableFuture.failedFuture(error);
                }
            });
        });
    }

    private URI parseResponse(HttpResponse<String> response, String token) {
        JsonObject body = QqApiErrors.parseObject(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || QqApiErrors.hasError(body)) {
            throw QqApiErrors.responseError("QQ Gateway discovery", response, body,
                    config.appSecret(), token, null);
        }
        JsonElement urlValue = body.get("url");
        if (urlValue == null || !urlValue.isJsonPrimitive()) {
            throw new IllegalStateException("QQ Gateway discovery response omitted url");
        }
        final URI endpoint;
        try {
            endpoint = URI.create(urlValue.getAsString().strip());
        } catch (RuntimeException error) {
            throw new IllegalStateException("QQ Gateway discovery returned an invalid url");
        }
        if (endpoint.getHost() == null
                || (!"wss".equalsIgnoreCase(endpoint.getScheme())
                && !"ws".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalStateException("QQ Gateway discovery returned an unsafe url");
        }
        return endpoint;
    }

}
