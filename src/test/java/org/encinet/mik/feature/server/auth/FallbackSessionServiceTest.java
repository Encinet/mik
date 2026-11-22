package org.encinet.mik.feature.server.auth;

import com.destroystokyo.paper.profile.PaperServicesDiscoveryService;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.services.ProfileResult;
import com.mojang.authlib.services.response.discovery.Discovery;
import com.mojang.authlib.services.response.discovery.DiscoveryResponse;
import com.mojang.authlib.services.response.discovery.Endpoint;
import com.mojang.authlib.services.response.discovery.Endpoints;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.encinet.mik.feature.server.auth.AuthenticationTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class FallbackSessionServiceTest {
    @TempDir Path directory;
    private final MutableClock clock = new MutableClock();
    private final ProfileResult alice = profile(UUID.randomUUID(), "Alice");
    private final List<Exception> errors = new ArrayList<>();
    private final List<UUID> fallbacks = new ArrayList<>();
    private final Upstream upstream = new Upstream();

    private AuthenticationFallbackStore open() throws Exception {
        var store = new AuthenticationFallbackStore(directory.resolve("cache.db"), clock, Duration.ofHours(72), 10);
        store.open();
        return store;
    }

    private FallbackSessionService service(AuthenticationFallbackStore store, boolean checkAddress) {
        var delegate = upstream.proxy();
        return new FallbackSessionService(delegate, delegate::hasJoinedServer, store, checkAddress, errors::add, fallbacks::add);
    }

    @Test
    void realVerificationRateLimitsUseTheMatchingCacheButDenialsNeverDo() throws Exception {
        var reply = new AtomicReference<>(new VerificationReply(200, """
                {"id":"%s","properties":[{"name":"textures","value":"skin","signature":"signature"}],
                 "profileActions":[{"updateType":"USING_BANNED_SKIN"}]}
                """.formatted(alice.profile().id().toString().replace("-", ""))));
        var query = new AtomicReference<String>();
        var requests = new AtomicInteger();
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/verify", exchange -> {
            try (exchange) {
                query.set(exchange.getRequestURI().getRawQuery());
                requests.incrementAndGet();
                var next = reply.get();
                byte[] bytes = next.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type",
                        next.body().startsWith("{") ? "application/json" : "text/plain");
                exchange.sendResponseHeaders(next.status(), bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length != 0) exchange.getResponseBody().write(bytes);
            }
        });
        http.start();
        try (var cache = open()) {
            var discovery = discoveryAt("http://127.0.0.1:" + http.getAddress().getPort() + "/verify");
            var delegate = discovery.createMinecraftSessionService();
            var verifier = new OfficialSessionVerifier(PaperAuthenticationHook.sessionDiscovery(delegate));
            try (var service = new FallbackSessionService(delegate, verifier, cache, true, errors::add, fallbacks::add)) {
                assertEquals(alice, service.hasJoinedServer("Alice", "verified-digest", ADDRESS));
                assertEquals(Set.of("username=Alice", "serverId=verified-digest", "ip=192.0.2.10"),
                        Set.of(query.get().split("&")));

                reply.set(new VerificationReply(429, "Too Many Requests"));
                assertEquals(alice, service.hasJoinedServer("Alice", "limited-digest", ADDRESS));
                assertEquals(2, requests.get()); // No retry or second request to distinguish a denial.
                assertThrows(AuthenticationUnavailableException.class,
                        () -> service.hasJoinedServer("Alice", "limited-other-address", OTHER_ADDRESS));

                reply.set(new VerificationReply(503, ""));
                assertEquals(alice, service.hasJoinedServer("Alice", "outage", ADDRESS));

                for (var rejected : List.of(new VerificationReply(204, ""), new VerificationReply(401, ""),
                        new VerificationReply(403, "not authorized"), new VerificationReply(200, "{broken"),
                        new VerificationReply(429, "{\"error\":\"multiplayer.access.banned\"}"))) {
                    reply.set(rejected);
                    assertNull(service.hasJoinedServer("Alice", "rejected", ADDRESS), "HTTP " + rejected.status());
                }
                assertEquals(List.of(alice.profile().id(), alice.profile().id()), fallbacks);
                assertTrue(errors.isEmpty());
            }
        } finally {
            http.stop(0);
        }
    }

    @Test
    void unavailableUpstreamUsesOnlyFreshMatchingIdentityWithoutRenewingItsAge() throws Exception {
        try (var cache = open(); var service = service(cache, false)) {
            upstream.result = alice;
            assertSame(alice, service.hasJoinedServer("Alice", "first-digest", ADDRESS));
            upstream.failure = new AuthenticationUnavailableException("offline");
            clock.advance(Duration.ofHours(71).toMillis());
            assertEquals(alice.profile().id(), service.hasJoinedServer("Alice", "new-digest", ADDRESS).profile().id());
            assertThrows(AuthenticationUnavailableException.class, () -> service.hasJoinedServer("Unknown", "digest", ADDRESS));
            assertThrows(AuthenticationUnavailableException.class, () -> service.hasJoinedServer("Alice", "digest", OTHER_ADDRESS));
            assertThrows(AuthenticationUnavailableException.class, () -> service.hasJoinedServer("Alice", "digest", null));
            clock.advance(Duration.ofHours(1).toMillis());
            assertThrows(AuthenticationUnavailableException.class, () -> service.hasJoinedServer("Alice", "digest", ADDRESS));
            assertEquals(List.of(alice.profile().id()), fallbacks);
            assertTrue(errors.isEmpty());
        }
    }

    @Test
    void normalRejectionAndUnexpectedErrorsNeverUseFallback() throws Exception {
        try (var cache = open(); var service = service(cache, true)) {
            cache.remember("Alice", ADDRESS, alice);
            assertNull(service.hasJoinedServer("Alice", "invalid-session", ADDRESS));
            upstream.failure = new IllegalStateException("unexpected error");
            assertSame(upstream.failure, assertThrows(IllegalStateException.class,
                    () -> service.hasJoinedServer("Alice", "digest", ADDRESS)));
            assertTrue(fallbacks.isEmpty());
        }
    }

    @Test
    void recoveringUpstreamAlwaysWinsAndRefreshesTheAddressBinding() throws Exception {
        try (var cache = open(); var service = service(cache, true)) {
            cache.remember("Alice", ADDRESS, alice);
            upstream.failure = new AuthenticationUnavailableException("offline");
            service.hasJoinedServer("Alice", "outage", ADDRESS);
            upstream.failure = null;
            upstream.result = alice;
            assertSame(alice, service.hasJoinedServer("Alice", "recovered", OTHER_ADDRESS));
            upstream.failure = new AuthenticationUnavailableException("offline again");
            assertThrows(AuthenticationUnavailableException.class, () -> service.hasJoinedServer("Alice", "digest", ADDRESS));
            assertNotNull(service.hasJoinedServer("Alice", "digest", OTHER_ADDRESS));
        }
    }

    @Test
    void officialUuidSurvivesRestartFallbackRenamingAndUpstreamRecovery() throws Exception {
        UUID officialId = alice.profile().id();
        try (var cache = open(); var service = service(cache, true)) {
            upstream.result = alice;
            assertEquals(officialId, service.hasJoinedServer("Alice", "online", ADDRESS).profile().id());
        }
        try (var cache = open(); var service = service(cache, true)) {
            upstream.failure = new AuthenticationUnavailableException("offline after restart");
            assertEquals(officialId, service.hasJoinedServer("Alice", "fallback", ADDRESS).profile().id());
            upstream.failure = null;
            upstream.result = profile(officialId, "RenamedAlice");
            assertEquals(officialId, service.hasJoinedServer("RenamedAlice", "recovered", OTHER_ADDRESS).profile().id());
            upstream.failure = new AuthenticationUnavailableException("offline again");
            assertEquals(officialId, service.hasJoinedServer("RenamedAlice", "fallback2", OTHER_ADDRESS).profile().id());
            assertThrows(AuthenticationUnavailableException.class,
                    () -> service.hasJoinedServer("Alice", "stale-name", ADDRESS));
        }
    }

    @Test
    void preservesTheUpstreamIpPolicyAndForwardsTheCurrentDigest() throws Exception {
        try (var cache = open(); var service = service(cache, false)) {
            upstream.result = alice;
            service.hasJoinedServer("Alice", "random-digest", ADDRESS);
            assertNull(upstream.address);
            assertEquals("random-digest", upstream.digest);
            assertNotNull(cache.find("Alice", ADDRESS));
        }
        try (var cache = open(); var service = service(cache, true)) {
            service.hasJoinedServer("Alice", "next-digest", ADDRESS);
            assertEquals(ADDRESS, upstream.address);
        }
    }

    @Test
    void storageFailureDisablesFallbackWhileKeepingGenuineUpstreamSuccess() throws Exception {
        var cache = open();
        cache.close();
        try (var service = service(cache, false)) {
            upstream.result = alice;
            assertSame(alice, service.hasJoinedServer("Alice", "digest", ADDRESS));
            assertSame(alice, service.hasJoinedServer("Alice", "digest2", ADDRESS));
            assertEquals(1, errors.size());
            upstream.failure = new AuthenticationUnavailableException("offline");
            assertThrows(AuthenticationUnavailableException.class, () -> service.hasJoinedServer("Alice", "digest", ADDRESS));
        }
    }

    @Test
    void failedCacheReadPreservesTheOriginalAuthenticationFailure() throws Exception {
        var cache = open();
        cache.remember("Alice", ADDRESS, alice);
        cache.close();
        try (var service = service(cache, true)) {
            upstream.failure = new AuthenticationUnavailableException("offline");
            assertSame(upstream.failure, assertThrows(AuthenticationUnavailableException.class,
                    () -> service.hasJoinedServer("Alice", "digest", ADDRESS)));
            assertEquals(1, errors.size());
            assertTrue(fallbacks.isEmpty());
        }
    }

    @Test
    void shutdownPreventsAnInFlightUpstreamFailureFromUsingCache() throws Exception {
        try (var cache = open()) {
            var service = service(cache, true);
            try {
                cache.remember("Alice", ADDRESS, alice);
                upstream.entered = new CountDownLatch(1);
                upstream.resume = new CountDownLatch(1);
                upstream.failure = new AuthenticationUnavailableException("offline");
                var attempt = CompletableFuture.runAsync(() -> assertThrows(AuthenticationUnavailableException.class,
                        () -> service.hasJoinedServer("Alice", "digest", ADDRESS)));
                try {
                    assertTrue(upstream.entered.await(5, TimeUnit.SECONDS));
                    service.close();
                } finally { upstream.resume.countDown(); }
                attempt.get(5, TimeUnit.SECONDS);
                assertTrue(fallbacks.isEmpty());
            } finally {
                service.close();
            }
        }
    }

    private static PaperServicesDiscoveryService discoveryAt(String verifyUrl) {
        var empty = Endpoints.empty();
        var session = new Endpoints(Map.of("verify", new Endpoint(verifyUrl)));
        var discovery = new Discovery("minecraft", empty, session, empty, empty, empty);
        return new PaperServicesDiscoveryService(Proxy.NO_PROXY, false,
                () -> new DiscoveryResponse("test", "minecraft", discovery));
    }

    private record VerificationReply(int status, String body) {}

    private static final class Upstream {
        ProfileResult result;
        Exception failure;
        InetAddress address;
        String digest;
        CountDownLatch entered;
        CountDownLatch resume;

        SessionService proxy() {
            return (SessionService) java.lang.reflect.Proxy.newProxyInstance(SessionService.class.getClassLoader(), new Class<?>[]{SessionService.class},
                    (_, method, args) -> {
                        if (!method.getName().equals("hasJoinedServer")) throw new AssertionError(method);
                        digest = (String) args[1];
                        address = (InetAddress) args[2];
                        if (entered != null) {
                            entered.countDown();
                            if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("Upstream was not resumed");
                        }
                        if (failure != null) throw failure;
                        return result;
                    });
        }
    }
}
