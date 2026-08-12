package org.encinet.mik.module.social.platform.qq.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.social.platform.qq.QqPlatformConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqGatewayDiscoveryTest {

    private HttpServer server;
    private HttpClient client;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (client != null) {
            client.shutdownNow();
        }
    }

    @Test
    void discoversGatewayWithQqBotAuthorization() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> unionAppId = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gateway/bot", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            unionAppId.set(exchange.getRequestHeaders().getFirst("X-Union-Appid"));
            respond(exchange, 200, "{\"url\":\"wss://gateway.example/socket?v=1\"}");
        });
        server.start();
        client = HttpClient.newHttpClient();

        URI endpoint = new QqGatewayDiscovery(client, config(server.getAddress().getPort()),
                () -> CompletableFuture.completedFuture("access-token"), () -> { })
                .discover().join();

        assertEquals("wss://gateway.example/socket?v=1", endpoint.toString());
        assertEquals("QQBot access-token", authorization.get());
        assertEquals("app", unionAppId.get());
    }

    @Test
    void rejectsUnsafeOrFailedDiscoveryResponses() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gateway/bot", exchange ->
                respond(exchange, 200, "{\"url\":\"https://not-a-websocket.example\"}"));
        server.start();
        client = HttpClient.newHttpClient();

        CompletionException error = org.junit.jupiter.api.Assertions.assertThrows(
                CompletionException.class,
                () -> new QqGatewayDiscovery(client, config(server.getAddress().getPort()),
                        () -> CompletableFuture.completedFuture("token"), () -> { })
                        .discover().join());

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("unsafe url"));
    }

    @Test
    void unauthorizedDiscoveryInvalidatesAndRetriesTheTokenOnce() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicInteger invalidations = new AtomicInteger();
        List<String> authorizations = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gateway/bot", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (requests.incrementAndGet() == 1) {
                respond(exchange, 401, "{\"message\":\"expired\"}");
            } else {
                respond(exchange, 200, "{\"url\":\"wss://gateway.example/socket\"}");
            }
        });
        server.start();
        client = HttpClient.newHttpClient();

        URI endpoint = new QqGatewayDiscovery(client, config(server.getAddress().getPort()),
                () -> CompletableFuture.completedFuture(
                        "token-" + tokenRequests.incrementAndGet()),
                invalidations::incrementAndGet).discover().join();

        assertEquals("wss://gateway.example/socket", endpoint.toString());
        assertEquals(2, requests.get());
        assertEquals(2, tokenRequests.get());
        assertEquals(1, invalidations.get());
        assertEquals(List.of("QQBot token-1", "QQBot token-2"), authorizations);
    }

    @Test
    void persistentDiscoveryFailureIncludesQqErrorDetails() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gateway/bot", exchange -> respond(exchange, 401,
                "{\"code\":11243,\"message\":\"invalid app access token\","
                        + "\"trace_id\":\"trace-123\"}"));
        server.start();
        client = HttpClient.newHttpClient();

        CompletionException error = org.junit.jupiter.api.Assertions.assertThrows(
                CompletionException.class,
                () -> new QqGatewayDiscovery(client, config(server.getAddress().getPort()),
                        () -> CompletableFuture.completedFuture("token"), () -> { })
                        .discover().join());

        assertTrue(error.getCause().getMessage().contains("HTTP 401"));
        assertTrue(error.getCause().getMessage().contains("code=11243"));
        assertTrue(error.getCause().getMessage().contains("invalid app access token"));
        assertTrue(error.getCause().getMessage().contains("trace_id=trace-123"));
    }

    private static QqPlatformConfig config(int port) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                app-id: app
                app-secret: secret
                api-base-url: http://127.0.0.1:%d
                """.formatted(port));
        return QqPlatformConfig.from(yaml);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
