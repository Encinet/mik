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
            for (int i = 0; i < 6; i++) assertTrue(client.review("hello").allowed());
        }
        assertEquals(java.util.Collections.nCopies(6, OVH_8B), models);
    }

    @Test
    void healthy8bRoutesShareTrafficWithoutUsingFallback() throws Exception {
        startServer(QwenGuardClientTest::safe);
        try (var client = client(true)) {
            for (int i = 0; i < 6; i++) assertTrue(client.review("hello").allowed());
        }
        assertEquals(List.of(OVH_8B, EDEN_8B, OVH_8B, EDEN_8B, OVH_8B, EDEN_8B), models);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 503, 200})
    void another8bIsTriedBeforeFallbackAndFailedRoutesAreSkipped(int status) throws Exception {
        startServer(model -> OVH_8B.equals(model) ? new Reply(status, "invalid result") : safe(model));
        try (var client = client(true)) {
            assertTrue(client.review("hello").allowed());
            assertTrue(client.review("hello again").allowed());
        }
        assertEquals(List.of(OVH_8B, EDEN_8B, EDEN_8B), models);
    }

    @Test
    void fallbackIsUsedOnlyAfterBoth8bRoutesFail() throws Exception {
        startServer(model -> FALLBACK.equals(model) ? OVH_SAFE : new Reply(503, "unavailable"));
        try (var client = client(true)) {
            assertTrue(client.review("hello").allowed());
            assertTrue(client.review("hello again").allowed());
        }
        assertEquals(List.of(OVH_8B, EDEN_8B, FALLBACK, FALLBACK), models);
    }

    @Test
    void recovered8bTakesPriorityAgainAfterUsingFallback() throws Exception {
        AtomicBoolean limited = new AtomicBoolean(true);
        startServer(model -> OVH_8B.equals(model) && limited.get()
                ? new Reply(429, "limited") : OVH_SAFE);
        try (var client = client(false)) {
            assertTrue(client.review("hello").allowed());
            assertTrue(client.review("during cooldown").allowed());
            limited.set(false);
            // The test server supplies a one-second Retry-After for rate limits.
            Thread.sleep(1_100);
            assertTrue(client.review("after cooldown").allowed());
            assertTrue(client.review("still healthy").allowed());
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
            var verdict = client.review("unsafe");
            assertFalse(verdict.allowed());
            assertEquals("Unsafe: Violent", verdict.reason());
        }
        assertEquals(List.of(OVH_8B), models);
    }

    @Test
    void edenRejectionRetainsEveryRiskLabel() throws Exception {
        startServer(model -> EDEN_8B.equals(model) ? new Reply(200, """
                {"status":"success","provider":"ovhcloud","feature":"text","subfeature":"moderation",
                 "output":{"nsfw_likelihood":3,"nsfw_likelihood_score":0.5,"items":[
                   {"label":"violence","likelihood":4,"likelihood_score":0.8},
                   {"label":"hate","likelihood":2,"likelihood_score":0.2},
                   {"label":"safe category","likelihood":1,"likelihood_score":0}]}}
                """) : OVH_SAFE);
        try (var client = client(true)) {
            assertTrue(client.review("first route").allowed());
            assertEquals(new SocialContentModerator.Verdict(false, "violence, hate"), client.review("risk"));
        }
        assertEquals(List.of(OVH_8B, EDEN_8B), models);
    }

    @Test
    void overBudgetContentExplainsTheLimitWithoutCallingAModel() throws Exception {
        startServer(_ -> OVH_SAFE);
        try (var client = client(false)) {
            var verdict = client.review("字".repeat(10_001));
            assertFalse(verdict.allowed());
            assertTrue(verdict.reason().contains("context limit"));
        }
        assertTrue(models.isEmpty());
    }

    @Test
    void allRoutesUnavailableStopsSendingRequests() throws Exception {
        startServer(_ -> new Reply(429, "limited"));
        try (var client = client(true)) {
            assertThrows(IllegalStateException.class, () -> client.review("hello").allowed());
            assertThrows(IllegalStateException.class, () -> client.review("during cooldown").allowed());
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
