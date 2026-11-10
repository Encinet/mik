package org.encinet.mik.feature.community.social.safety;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded moderation with round-robin 8B routes and a last-resort 0.6B route. */
public final class QwenGuardClient implements SocialContentModerator {
    private static final URI OVH_ENDPOINT = URI.create(
            "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions");
    private static final URI EDEN_ENDPOINT = URI.create("https://api.edenai.run/v3/universal-ai");
    private static final int MAXIMUM_RESPONSE_BYTES = 65_536;
    // Keep the complete input below the models' 32k context even for non-ASCII text.
    private static final int MAXIMUM_INPUT_BYTES = 30_000;
    private static final Duration FAILURE_COOLDOWN = Duration.ofSeconds(5);
    private static final Duration RATE_LIMIT_COOLDOWN = Duration.ofMinutes(1);
    private static final Duration MAXIMUM_COOLDOWN = Duration.ofDays(1);

    private final SocialContentSafetyConfig config;
    private final List<Route> preferredRoutes;
    private final Route fallbackRoute;
    private final AtomicInteger cursor = new AtomicInteger();
    private final HttpClient httpClient;
    private volatile boolean closed;

    public QwenGuardClient(SocialContentSafetyConfig config) {
        this(config, OVH_ENDPOINT, EDEN_ENDPOINT);
    }

    QwenGuardClient(SocialContentSafetyConfig config, URI ovhEndpoint, URI edenEndpoint) {
        this.config = Objects.requireNonNull(config, "config");
        List<Route> configured = new ArrayList<>();
        configured.add(new Route(ovhEndpoint, "Qwen3Guard-Gen-8B", config.apiKey(),
                QwenGuardProtocol.OVH));
        if (!config.edenaiApiKey().isEmpty()) {
            configured.add(new Route(edenEndpoint, "text/moderation/ovhcloud/Qwen3Guard-Gen-8B",
                    config.edenaiApiKey(), QwenGuardProtocol.EDEN));
        }
        preferredRoutes = List.copyOf(configured);
        fallbackRoute = new Route(ovhEndpoint, "Qwen3Guard-Gen-0.6B", config.apiKey(),
                QwenGuardProtocol.OVH);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public boolean allows(String text) {
        Objects.requireNonNull(text, "text");
        checkActive();
        if (text.isBlank()) return true;
        if (text.length() > MAXIMUM_INPUT_BYTES
                || text.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_INPUT_BYTES) {
            return false;
        }
        long deadline = System.nanoTime() + config.requestTimeout().toNanos();
        List<Route> candidates = candidates();
        String failure = "Qwen Guard has no available moderation route";
        if (candidates.isEmpty()) throw new IllegalStateException(failure);
        for (int index = 0; index < candidates.size(); index++) {
            checkActive();
            Route route = candidates.get(index);
            if (!route.available()) continue;
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            // Reserve time for the remaining routes so a stalled body can also fail over.
            Duration attemptTimeout = Duration.ofNanos(remaining / (candidates.size() - index));
            HttpResponse<byte[]> response;
            try {
                response = request(route, text, attemptTimeout);
            } catch (TimeoutException | ExecutionException error) {
                route.coolDown(FAILURE_COOLDOWN);
                failure = "Qwen Guard request failed or timed out";
                continue;
            }
            int status = response.statusCode();
            if (status == 200) {
                // A valid rejection is final; never seek another model's approval.
                try {
                    return route.protocol.allows(new String(response.body(), StandardCharsets.UTF_8));
                } catch (IllegalStateException error) {
                    route.coolDown(FAILURE_COOLDOWN);
                    failure = error.getMessage();
                    continue;
                }
            }
            failure = "Qwen Guard returned HTTP " + status;
            if (status == 401 || status == 402 || status == 403 || status == 404) {
                route.disabled = true; // Invalid credentials/account/model: recover on reload.
            } else if (status == 408 || status == 425 || status == 429 || (status >= 500 && status <= 599)) {
                route.coolDown(retryAfter(response.headers(), status == 429
                        ? RATE_LIMIT_COOLDOWN : FAILURE_COOLDOWN));
            } else {
                throw new IllegalStateException(failure);
            }
        }
        // Remote bodies, credentials and low-level exception details must not enter logs.
        throw new IllegalStateException(failure);
    }

    private List<Route> candidates() {
        List<Route> available = new ArrayList<>();
        for (Route route : preferredRoutes) if (route.available()) available.add(route);
        List<Route> candidates = new ArrayList<>();
        if (!available.isEmpty()) {
            int start = cursor.getAndUpdate(value -> (value + 1) % available.size()) % available.size();
            for (int index = 0; index < available.size(); index++) {
                candidates.add(available.get((start + index) % available.size()));
            }
        }
        if (fallbackRoute.available()) candidates.add(fallbackRoute);
        return candidates;
    }

    private HttpResponse<byte[]> request(Route route, String text, Duration timeout)
            throws TimeoutException, ExecutionException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(route.endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        route.protocol.requestBody(route.model, text), StandardCharsets.UTF_8));
        if (!route.apiKey.isEmpty()) builder.header("Authorization", "Bearer " + route.apiKey);
        var future = httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.limiting(
                HttpResponse.BodyHandlers.ofByteArray(), MAXIMUM_RESPONSE_BYTES));
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CancellationException("Qwen Guard request interrupted");
        } catch (TimeoutException error) {
            future.cancel(true);
            throw error;
        }
    }

    private void checkActive() {
        if (closed || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Qwen Guard client closed or request interrupted");
        }
    }

    private static Duration retryAfter(HttpHeaders headers, Duration fallback) {
        String value = headers.firstValue("Retry-After").orElse("").strip();
        Duration delay;
        try {
            delay = Duration.ofSeconds(Long.parseLong(value));
        } catch (NumberFormatException error) {
            try {
                delay = Duration.between(ZonedDateTime.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
            } catch (DateTimeParseException invalid) {
                return fallback;
            }
        }
        if (delay.isNegative() || delay.isZero()) return fallback;
        return delay.compareTo(MAXIMUM_COOLDOWN) > 0 ? MAXIMUM_COOLDOWN : delay;
    }

    @Override
    public void close() {
        closed = true;
        httpClient.shutdownNow();
    }

    private static final class Route {
        private final URI endpoint;
        private final String model;
        private final String apiKey;
        private final QwenGuardProtocol protocol;
        private final AtomicLong retryAt = new AtomicLong();
        private volatile boolean disabled;

        private Route(URI endpoint, String model, String apiKey, QwenGuardProtocol protocol) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            this.model = model;
            this.apiKey = apiKey;
            this.protocol = protocol;
        }

        private boolean available() {
            long retry = retryAt.get();
            return !disabled && (retry == 0 || System.nanoTime() - retry >= 0);
        }

        private void coolDown(Duration delay) {
            long until = System.nanoTime() + delay.toNanos();
            retryAt.updateAndGet(previous -> previous == 0 || until - previous > 0 ? until : previous);
        }
    }
}
