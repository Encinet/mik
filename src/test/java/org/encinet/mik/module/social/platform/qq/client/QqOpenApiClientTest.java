package org.encinet.mik.module.social.platform.qq.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.social.platform.qq.QqPlatformConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqOpenApiClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void tokenIsCachedAndGroupReplyUsesTheOfficialMarkdownFields() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        List<String> authorizations = new ArrayList<>();
        List<String> unionAppIds = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> {
            tokenRequests.incrementAndGet();
            respond(exchange, 200, "{\"access_token\":\"test-token\",\"expires_in\":\"7200\"}");
        });
        server.createContext("/v2/groups/group-id/messages", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            unionAppIds.add(exchange.getRequestHeaders().getFirst("X-Union-Appid"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"id\":\"sent-message\"}");
        });
        server.start();

        QqPlatformConfig config = config(server.getAddress().getPort());
        HttpClient httpClient = HttpClient.newHttpClient();
        QqAccessTokenProvider tokens = new QqAccessTokenProvider(httpClient, config);
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config, tokens);

        api.replyMarkdown("group-id", "incoming-one", 1, "## first").join();
        api.replyMarkdown("group-id", "incoming-two", 2, "**second**").join();

        assertEquals(1, tokenRequests.get());
        assertEquals(List.of("QQBot test-token", "QQBot test-token"), authorizations);
        assertEquals(List.of("app", "app"), unionAppIds);
        JsonObject first = JsonParser.parseString(bodies.get(0)).getAsJsonObject();
        JsonObject second = JsonParser.parseString(bodies.get(1)).getAsJsonObject();
        assertEquals(2, first.get("msg_type").getAsInt());
        assertFalse(first.has("content"));
        assertFalse(first.has("keyboard"));
        assertEquals("## first", first.getAsJsonObject("markdown")
                .get("content").getAsString());
        assertEquals("incoming-one", first.get("msg_id").getAsString());
        assertEquals(1, first.get("msg_seq").getAsInt());
        assertEquals("**second**", second.getAsJsonObject("markdown")
                .get("content").getAsString());
        assertEquals("incoming-two", second.get("msg_id").getAsString());
        assertEquals(2, second.get("msg_seq").getAsInt());
        httpClient.shutdownNow();
    }

    @Test
    void unauthorizedSendRefreshesTheAccessTokenOnce() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        List<String> authorizations = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> {
            int number = tokenRequests.incrementAndGet();
            respond(exchange, 200, "{\"access_token\":\"token-" + number
                    + "\",\"expires_in\":\"7200\"}");
        });
        server.createContext("/v2/groups/group-id/messages", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            authorizations.add(authorization);
            if (authorization.endsWith("token-1")) {
                respond(exchange, 401,
                        "{\"err_code\":11243,\"message\":\"expired\"}");
            } else {
                respond(exchange, 200, "{\"id\":\"sent-message\"}");
            }
        });
        server.start();

        QqPlatformConfig config = config(server.getAddress().getPort());
        HttpClient httpClient = HttpClient.newHttpClient();
        QqAccessTokenProvider tokens = new QqAccessTokenProvider(httpClient, config);
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config, tokens);

        api.replyMarkdown("group-id", "incoming-id", 1, "## hello").join();

        assertEquals(2, tokenRequests.get());
        assertEquals(List.of("QQBot token-1", "QQBot token-2"), authorizations);
        httpClient.shutdownNow();
    }

    @Test
    void remoteImageIsUploadedThenSentAsNativeMediaWithText() throws Exception {
        List<String> uploadBodies = new ArrayList<>();
        List<String> messageBodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange, 200,
                "{\"access_token\":\"test-token\",\"expires_in\":\"7200\"}"));
        server.createContext("/v2/groups/group-id/files", exchange -> {
            uploadBodies.add(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, 200,
                    "{\"file_uuid\":\"file-id\",\"file_info\":\"file-token\",\"ttl\":300}");
        });
        server.createContext("/v2/groups/group-id/messages", exchange -> {
            messageBodies.add(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"id\":\"sent-message\"}");
        });
        server.start();

        QqPlatformConfig config = config(server.getAddress().getPort());
        HttpClient httpClient = HttpClient.newHttpClient();
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config);

        api.replyRemoteImage("group-id", "incoming-id", 1,
                URI.create("https://textures.minecraft.net/texture/abc"),
                "Minecraft profile").join();
        api.replyBase64Image("group-id", "incoming-id", 2,
                Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4}),
                "Minecraft face").join();

        assertEquals(2, uploadBodies.size());
        JsonObject upload = JsonParser.parseString(uploadBodies.getFirst()).getAsJsonObject();
        assertEquals(1, upload.get("file_type").getAsInt());
        assertEquals("https://textures.minecraft.net/texture/abc",
                upload.get("url").getAsString());
        assertFalse(upload.get("srv_send_msg").getAsBoolean());
        assertEquals("group-id", upload.get("group_openid").getAsString());
        JsonObject embeddedUpload = JsonParser.parseString(uploadBodies.get(1))
                .getAsJsonObject();
        assertEquals("AQIDBA==", embeddedUpload.get("file_data").getAsString());
        assertFalse(embeddedUpload.has("url"));

        assertEquals(2, messageBodies.size());
        JsonObject message = JsonParser.parseString(messageBodies.getFirst()).getAsJsonObject();
        assertEquals(7, message.get("msg_type").getAsInt());
        assertEquals("Minecraft profile", message.get("content").getAsString());
        assertEquals("file-token",
                message.getAsJsonObject("media").get("file_info").getAsString());
        assertEquals("incoming-id", message.get("msg_id").getAsString());
        assertEquals(1, message.get("msg_seq").getAsInt());
        assertFalse(message.has("markdown"));
        JsonObject embeddedMessage = JsonParser.parseString(messageBodies.get(1))
                .getAsJsonObject();
        assertEquals("Minecraft face", embeddedMessage.get("content").getAsString());
        assertEquals(2, embeddedMessage.get("msg_seq").getAsInt());
        httpClient.shutdownNow();
    }

    @Test
    void concurrentRepliesShareOneInFlightAccessTokenRequest() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicInteger messageRequests = new AtomicInteger();
        CountDownLatch tokenStarted = new CountDownLatch(1);
        CountDownLatch releaseToken = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> {
            tokenRequests.incrementAndGet();
            tokenStarted.countDown();
            try {
                releaseToken.await();
                respond(exchange, 200,
                        "{\"access_token\":\"shared-token\",\"expires_in\":\"7200\"}");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        server.createContext("/v2/groups/group-id/messages", exchange -> {
            messageRequests.incrementAndGet();
            respond(exchange, 200, "{\"id\":\"sent-message\"}");
        });
        server.start();

        QqPlatformConfig config = config(server.getAddress().getPort());
        HttpClient httpClient = HttpClient.newHttpClient();
        QqAccessTokenProvider tokens = new QqAccessTokenProvider(httpClient, config);
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config, tokens);
        List<CompletableFuture<Void>> replies = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            replies.add(api.replyMarkdown(
                    "group-id", "incoming-" + index, 1, "## hello"));
        }

        try {
            assertTrue(tokenStarted.await(2, TimeUnit.SECONDS));
        } finally {
            releaseToken.countDown();
        }
        CompletableFuture.allOf(replies.toArray(CompletableFuture[]::new)).join();

        assertEquals(1, tokenRequests.get());
        assertEquals(20, messageRequests.get());
        httpClient.shutdownNow();
    }

    @Test
    void aReplyCannotBeSentWithoutAnInboundMessageOrValidSequence() throws Exception {
        QqPlatformConfig config = config(1);
        HttpClient httpClient = HttpClient.newHttpClient();
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config,
                new QqAccessTokenProvider(httpClient, config));

        assertThrows(IllegalArgumentException.class,
                () -> api.replyMarkdown("group-id", "", 1, "## message"));
        assertThrows(IllegalArgumentException.class,
                () -> api.replyMarkdown("group-id", "incoming-id", 0, "## message"));
        assertThrows(IllegalArgumentException.class,
                () -> api.replyMarkdown("group-id", "incoming-id", 6, "## message"));
        assertThrows(IllegalArgumentException.class,
                () -> api.replyMarkdown("group-id", "incoming-id", 1, " "));
        httpClient.shutdownNow();
    }

    private static QqPlatformConfig config(int port) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                app-id: app
                app-secret: secret
                api-base-url: http://127.0.0.1:%d
                access-token-url: http://127.0.0.1:%d/app/getAppAccessToken
                """.formatted(port, port));
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
