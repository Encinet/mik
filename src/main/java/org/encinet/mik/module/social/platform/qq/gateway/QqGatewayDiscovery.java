package org.encinet.mik.module.social.platform.qq.gateway;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.module.social.platform.qq.QqPlatformConfig;

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
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        }).thenCompose(response -> {
            if (!retriedAuthentication && response.statusCode() == 401) {
                invalidateToken.run();
                return discover(true);
            }
            try {
                return CompletableFuture.completedFuture(parseResponse(response));
            } catch (RuntimeException error) {
                return CompletableFuture.failedFuture(error);
            }
        });
    }

    private static URI parseResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw discoveryError(response);
        }
        final JsonObject body;
        try {
            JsonElement parsed = JsonParser.parseString(response.body());
            body = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException error) {
            throw new IllegalStateException("QQ Gateway discovery returned invalid JSON", error);
        }
        JsonElement urlValue = body.get("url");
        if (urlValue == null || !urlValue.isJsonPrimitive()) {
            throw new IllegalStateException("QQ Gateway discovery response omitted url");
        }
        final URI endpoint;
        try {
            endpoint = URI.create(urlValue.getAsString().strip());
        } catch (RuntimeException error) {
            throw new IllegalStateException("QQ Gateway discovery returned an invalid url", error);
        }
        if (endpoint.getHost() == null
                || (!"wss".equalsIgnoreCase(endpoint.getScheme())
                && !"ws".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalStateException("QQ Gateway discovery returned an unsafe url");
        }
        return endpoint;
    }

    private static IllegalStateException discoveryError(HttpResponse<String> response) {
        StringBuilder message = new StringBuilder("QQ Gateway discovery returned HTTP ")
                .append(response.statusCode());
        try {
            JsonElement parsed = JsonParser.parseString(response.body());
            if (parsed.isJsonObject()) {
                JsonObject body = parsed.getAsJsonObject();
                appendErrorField(message, body, "err_code", "err_code");
                if (!body.has("err_code")) {
                    appendErrorField(message, body, "code", "code");
                }
                appendErrorField(message, body, "message", null);
                appendErrorField(message, body, "trace_id", "trace_id");
            }
        } catch (RuntimeException ignored) {
            // The status code remains actionable even if QQ returned a non-JSON proxy page.
        }
        return new IllegalStateException(message.toString());
    }

    private static void appendErrorField(
            StringBuilder target,
            JsonObject body,
            String field,
            String label
    ) {
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return;
        }
        String text = value.getAsString().strip();
        if (text.isEmpty()) {
            return;
        }
        String limited = text.length() <= 200 ? text : text.substring(0, 200) + "…";
        target.append(label == null ? ": " : " [" + label + "=")
                .append(limited);
        if (label != null) {
            target.append(']');
        }
    }
}
