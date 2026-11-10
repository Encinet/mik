package org.encinet.mik.feature.community.social.platform.qq.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformConfig;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class QqAccessTokenProvider {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final long EXPIRY_SAFETY_SECONDS = 60;

    private final HttpClient httpClient;
    private final QqPlatformConfig config;
    private final Gson gson = new Gson();

    private CachedToken cachedToken;
    private CompletableFuture<CachedToken> inFlight;

    public QqAccessTokenProvider(HttpClient httpClient, QqPlatformConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }

    public synchronized CompletableFuture<String> accessToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && cachedToken.validAt(now)) {
            return CompletableFuture.completedFuture(cachedToken.value());
        }
        if (inFlight != null) {
            return inFlight.thenApply(CachedToken::value);
        }

        CompletableFuture<CachedToken> request = requestToken();
        inFlight = request;
        request.whenComplete((token, error) -> {
            synchronized (this) {
                if (inFlight == request) {
                    inFlight = null;
                }
                if (error == null) {
                    cachedToken = token;
                }
            }
        });
        return request.thenApply(CachedToken::value);
    }

    public synchronized void invalidate() {
        cachedToken = null;
    }

    private CompletableFuture<CachedToken> requestToken() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", config.appId());
        requestBody.addProperty("clientSecret", config.appSecret());

        HttpRequest request = HttpRequest.newBuilder(
                        config.accessTokenUri())
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseTokenResponse);
    }

    private CachedToken parseTokenResponse(HttpResponse<String> response) {
        JsonObject body = QqApiErrors.parseObject(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || QqApiErrors.hasError(body)) {
            throw QqApiErrors.responseError("Access token request", response, body,
                    config.appSecret(), QqApiErrors.string(body, "access_token"), null);
        }

        String token = QqApiErrors.string(body, "access_token");
        long expiresIn = QqApiErrors.longValue(body, "expires_in", 0);
        if (token.isBlank() || expiresIn <= 0) {
            throw QqApiErrors.responseError("Access token request", response, body,
                    config.appSecret(), token, null);
        }
        long usableSeconds = Math.max(1, expiresIn - Math.min(EXPIRY_SAFETY_SECONDS, expiresIn / 2));
        long expiresAt = System.currentTimeMillis() + Duration.ofSeconds(usableSeconds).toMillis();
        return new CachedToken(token, expiresAt);
    }

    private record CachedToken(String value, long expiresAtMillis) {
        boolean validAt(long nowMillis) {
            return nowMillis < expiresAtMillis;
        }
    }
}
