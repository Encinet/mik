package org.encinet.mik.module.social.platform.qq.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.module.social.platform.qq.QqPlatformConfig;

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
        JsonObject body = parseObject(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw responseError("Access token request", response.statusCode(), body);
        }

        String token = string(body, "access_token");
        long expiresIn = longValue(body, "expires_in", 0);
        if (token.isBlank() || expiresIn <= 0) {
            throw new QqHttpException("Access token request", response.statusCode(),
                    longValue(body, "err_code", longValue(body, "code", 0)),
                    firstNonBlank(string(body, "message"), "response omitted access_token/expires_in"),
                    string(body, "trace_id"));
        }
        long usableSeconds = Math.max(1, expiresIn - Math.min(EXPIRY_SAFETY_SECONDS, expiresIn / 2));
        long expiresAt = System.currentTimeMillis() + Duration.ofSeconds(usableSeconds).toMillis();
        return new CachedToken(token, expiresAt);
    }

    static QqHttpException responseError(String operation, int statusCode, JsonObject body) {
        return new QqHttpException(operation, statusCode,
                longValue(body, "err_code", longValue(body, "code", 0)),
                firstNonBlank(string(body, "message"), "QQ API rejected the request"),
                string(body, "trace_id"));
    }

    static JsonObject parseObject(String value) {
        try {
            JsonElement element = JsonParser.parseString(value == null || value.isBlank() ? "{}" : value);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return "";
        }
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    static long longValue(JsonObject object, String name, long fallback) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private record CachedToken(String value, long expiresAtMillis) {
        boolean validAt(long nowMillis) {
            return nowMillis < expiresAtMillis;
        }
    }
}
