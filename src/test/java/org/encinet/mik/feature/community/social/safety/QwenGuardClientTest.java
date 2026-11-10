package org.encinet.mik.feature.community.social.safety;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QwenGuardClientTest {
    private static final String OVH_8B = "Qwen3Guard-Gen-8B";
    private static final String EDEN_8B = "text/moderation/ovhcloud/Qwen3Guard-Gen-8B";
    private static final String FALLBACK = "Qwen3Guard-Gen-0.6B";
    private static final Reply OVH_SAFE = new Reply(200, """
            {"choices":[{"finish_reason":"stop","message":{"content":"Safety: Safe\\nCategories: None"}}]}
            """);
    private static final Reply EDEN_SAFE = new Reply(200, """
            {"status":"success","provider":"ovhcloud","feature":"text","subfeature":"moderation",
             "output":{"nsfw_likelihood":1,"nsfw_likelihood_score":0}}
            """);

    private final List<String> models = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void healthyOvh8bAlwaysWinsWithoutEden() throws Exception {
        startServer(_ -> OVH_SAFE);
        try (var client = client(false)) {
            for (int i = 0; i < 6; i++) assertTrue(client.allows("hello"));
        }
        assertEquals(java.util.Collections.nCopies(6, OVH_8B), models);
    }

    @Test
    void healthy8bRoutesShareTrafficWithoutUsingFallback() throws Exception {
        startServer(QwenGuardClientTest::safe);
        try (var client = client(true)) {
            for (int i = 0; i < 6; i++) assertTrue(client.allows("hello"));
        }
        assertEquals(List.of(OVH_8B, EDEN_8B, OVH_8B, EDEN_8B, OVH_8B, EDEN_8B), models);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 503, 200})
    void another8bIsTriedBeforeFallbackAndFailedRoutesAreSkipped(int status) throws Exception {
        startServer(model -> OVH_8B.equals(model) ? new Reply(status, "invalid result") : safe(model));
        try (var client = client(true)) {
            assertTrue(client.allows("hello"));
            assertTrue(client.allows("hello again"));
        }
        assertEquals(List.of(OVH_8B, EDEN_8B, EDEN_8B), models);
    }

    @Test
    void fallbackIsUsedOnlyAfterBoth8bRoutesFail() throws Exception {
        startServer(model -> FALLBACK.equals(model) ? OVH_SAFE : new Reply(503, "unavailable"));
        try (var client = client(true)) {
            assertTrue(client.allows("hello"));
            assertTrue(client.allows("hello again"));
        }
        assertEquals(List.of(OVH_8B, EDEN_8B, FALLBACK, FALLBACK), models);
    }

    @Test
    void recovered8bTakesPriorityAgainAfterUsingFallback() throws Exception {
        AtomicBoolean limited = new AtomicBoolean(true);
        startServer(model -> OVH_8B.equals(model) && limited.get()
                ? new Reply(429, "limited") : OVH_SAFE);
        try (var client = client(false)) {
            assertTrue(client.allows("hello"));
            assertTrue(client.allows("during cooldown"));
            limited.set(false);
            // The test server supplies a one-second Retry-After for rate limits.
            Thread.sleep(1_100);
            assertTrue(client.allows("after cooldown"));
            assertTrue(client.allows("still healthy"));
        }
        assertEquals(List.of(OVH_8B, FALLBACK, FALLBACK, OVH_8B, OVH_8B), models);
    }

    @Test
    void riskVerdictNeverFallsThroughToAnotherRoute() throws Exception {
        startServer(_ -> new Reply(200, """
                {"choices":[{"finish_reason":"stop",
                 "message":{"content":"Safety: Unsafe\\nCategories: Violent"}}]}
                """));
        try (var client = client(true)) {
            assertFalse(client.allows("unsafe"));
        }
        assertEquals(List.of(OVH_8B), models);
    }

    @Test
    void allRoutesUnavailableStopsSendingRequests() throws Exception {
        startServer(_ -> new Reply(429, "limited"));
        try (var client = client(true)) {
            assertThrows(IllegalStateException.class, () -> client.allows("hello"));
            assertThrows(IllegalStateException.class, () -> client.allows("during cooldown"));
        }
        assertEquals(List.of(OVH_8B, EDEN_8B, FALLBACK), models);
    }

    private static Reply safe(String model) {
        return EDEN_8B.equals(model) ? EDEN_SAFE : OVH_SAFE;
    }

    private void startServer(Function<String, Reply> replies) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                var body = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8)).getAsJsonObject();
                String model = body.get("model").getAsString();
                models.add(model);
                Reply reply = replies.apply(model);
                if (reply.status() == 429) exchange.getResponseHeaders().set("Retry-After", "1");
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
    }

    private QwenGuardClient client(boolean eden) {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        return new QwenGuardClient(new SocialContentSafetyConfig("", eden ? "eden-token" : "",
                Duration.ofSeconds(1), Duration.ofSeconds(5)), endpoint, endpoint);
    }

    private record Reply(int status, String body) {}
}
