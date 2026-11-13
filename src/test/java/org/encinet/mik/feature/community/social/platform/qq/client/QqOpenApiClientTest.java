package org.encinet.mik.feature.community.social.platform.qq.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformConfig;
import org.junit.jupiter.api.AfterEach;
import org.encinet.mik.test.QqTestHttpClient;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.feature.community.social.platform.qq.message.QqOutgoingMessage.markdown;
import static org.encinet.mik.feature.community.social.platform.qq.message.QqOutgoingMessage.text;

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
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqOpenApiClientTest {

    private HttpServer server;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void imageFailureTracksWhetherAUserVisibleSendWasAttempted(boolean submitted) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange, 200,
                "{\"access_token\":\"token\",\"expires_in\":7200}"));
        server.createContext("/v2/groups/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (exchange.getRequestURI().getPath().endsWith("/files") && submitted) {
                respond(exchange, 200, "{\"file_info\":\"file\"}");
            } else respond(exchange, 503, "{\"message\":\"unavailable\"}");
        });
        server.start();
        try (HttpClient client = QqTestHttpClient.create(server.getAddress().getPort())) {
            var result = new QqOpenApiClient(client, config()).replyRemoteImage("group", "message", 1,
                    URI.create("https://example.org/image"), "caption").join();
            assertEquals(submitted ? org.encinet.mik.feature.community.social.api.SocialDeliveryResult.Status.UNKNOWN
                    : org.encinet.mik.feature.community.social.api.SocialDeliveryResult.Status.NOT_SENT, result.status());
        }
    }

    @Test
    void listsApplicationsUsingAuthorizedGetAndAnEncodedCursorWithOneTokenRefresh() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        List<String> requests = new ArrayList<>();
        List<String> authorizations = new ArrayList<>();
        List<String> appIds = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange, 200,
                "{\"access_token\":\"token-" + tokenRequests.incrementAndGet() + "\",\"expires_in\":7200}"));
        server.createContext("/v2/groups/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            appIds.add(exchange.getRequestHeaders().getFirst("X-Union-Appid"));
            assertEquals(0, exchange.getRequestBody().readAllBytes().length);
            respond(exchange, requests.size() == 1 ? 401 : 200, requests.size() == 1 ? "{\"code\":11243}" : """
                    {"list":[{"member_openid":"member","join_request_id":"request",
                    "apply_at":"1970-01-01T00:01:00Z",
                    "verify_info":{"method":"verify_message","verify_message":"005924"}}],"next_cursor":"next"}
                    """);
        });
        server.start();
        try (HttpClient client = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort())) {
            QqOpenApiClient api = new QqOpenApiClient(client, config());
            var page = api.listGroupJoinRequests("group/a", "cursor+/= space&").join();
            assertEquals(2, tokenRequests.get());
            assertEquals(List.of("QQBot token-1", "QQBot token-2"), authorizations);
            assertEquals(List.of("app", "app"), appIds);
            assertTrue(requests.stream().allMatch(request -> request.equals(
                    "GET /v2/groups/group%2Fa/join_request_list?limit=50&cursor=cursor%2B%2F%3D%20space%26")));
            assertEquals("next", page.nextCursor());
            assertEquals("group/a", page.requests().getFirst().groupOpenId());
            assertEquals(Instant.ofEpochSecond(60), page.requests().getFirst().appliedAt());
        }
    }

    @Test
    void listFailuresAreNotEmptyPagesAndUnauthorizedRetriesStopAfterOneRefresh() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> body = new java.util.concurrent.atomic.AtomicReference<>("{}");
        AtomicInteger status = new AtomicInteger(200);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange, 200,
                "{\"access_token\":\"token\",\"expires_in\":7200}"));
        server.createContext("/v2/groups/", exchange -> {
            attempts.incrementAndGet();
            respond(exchange, status.get(), body.get());
        });
        server.start();
        try (HttpClient client = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort())) {
            QqOpenApiClient api = new QqOpenApiClient(client, config());
            for (String malformed : List.of("{}", "not json", "{\"code\":42,\"list\":[]}",
                    "{\"code\":42,\"err_code\":0,\"list\":[]}")) {
                body.set(malformed);
                assertThrows(java.util.concurrent.CompletionException.class,
                        () -> api.listGroupJoinRequests("group", "").join());
            }
            assertEquals(4, attempts.get());
            status.set(401);
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> api.listGroupJoinRequests("group", "").join());
            assertEquals(6, attempts.get());
        }
    }

    @Test
    void listAndApprovalErrorsPreserveRetryAfterWithoutImmediateRateLimitRetries() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> retryAfter = new java.util.concurrent.atomic.AtomicReference<>("120");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange, 200,
                "{\"access_token\":\"token\",\"expires_in\":7200}"));
        server.createContext("/v2/groups/", exchange -> {
            attempts.incrementAndGet();
            exchange.getResponseHeaders().set("Retry-After", retryAfter.get());
            respond(exchange, 429, "{\"code\":429,\"message\":\"rate limited\"}");
        });
        server.start();
        try (HttpClient client = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort())) {
            QqOpenApiClient api = new QqOpenApiClient(client, config());
            var error = assertThrows(java.util.concurrent.CompletionException.class,
                    () -> api.listGroupJoinRequests("group", "").join());
            assertEquals(120_000, ((QqHttpException) error.getCause()).retryAfterMillis());
            error = assertThrows(java.util.concurrent.CompletionException.class,
                    () -> api.reviewGroupJoinRequest("group", "member", "request", true).join());
            assertEquals(120_000, ((QqHttpException) error.getCause()).retryAfterMillis());
            retryAfter.set(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(90).format(DateTimeFormatter.RFC_1123_DATE_TIME));
            error = assertThrows(java.util.concurrent.CompletionException.class,
                    () -> api.listGroupJoinRequests("group", "").join());
            assertTrue(((QqHttpException) error.getCause()).retryAfterMillis() > 80_000);
            for (String invalid : List.of("invalid", "-10", "99999999999999999999999")) {
                retryAfter.set(invalid);
                error = assertThrows(java.util.concurrent.CompletionException.class,
                        () -> api.listGroupJoinRequests("group", "").join());
                assertEquals(0, ((QqHttpException) error.getCause()).retryAfterMillis());
            }
            assertEquals(6, attempts.get());
        }
    }

    @Test
    void allOpenApiOperationsPreserveBothCodesAndSafeDiagnosticDetails() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange, 200,
                "{\"access_token\":\"private-token\",\"expires_in\":7200}"));
        server.createContext("/v2/groups/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("X-Tps-trace-ID", "a93ab23871f9110667419f1a0001fcb1");
            respond(exchange, 400, """
                    {"code":11703,"err_code":40011030,"message":"机器人不是群管理员 secret private-token 005924"}
                    """);
        });
        server.start();
        try (HttpClient client = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort())) {
            QqOpenApiClient api = new QqOpenApiClient(client, config());
            List<CompletableFuture<?>> operations = List.of(
                    api.listGroupJoinRequests("group", ""),
                    api.reviewGroupJoinRequest("group", "member", "request", true),
                    api.sendMessage("group", markdown("private message")),
                    api.replyRemoteImage("group", "message", 1, URI.create("https://example.com/image"), "caption")
                            .thenApply(result -> { throw new java.util.concurrent.CompletionException(result.failure().orElseThrow()); }));
            for (CompletableFuture<?> operation : operations) {
                var error = assertThrows(java.util.concurrent.CompletionException.class, operation::join);
                QqHttpException httpError = org.junit.jupiter.api.Assertions.assertInstanceOf(QqHttpException.class, error.getCause());
                assertEquals(11703, httpError.code());
                assertEquals(40011030, httpError.errorCode());
                String message = httpError.getMessage();
                assertTrue(message.contains("HTTP 400, code=11703, err_code=40011030"));
                assertTrue(message.contains("机器人不是群管理员"));
                assertTrue(message.contains("trace_id=a93ab23871f9110667419f1a0001fcb1"));
                for (String sensitive : List.of("secret", "private-token", "005924")) assertFalse(message.contains(sensitive));
            }
        }
    }

    @Test
    void echoedMessageContentIsRedactedBeforeErrorsReachOrdinaryExceptionLogging() throws Exception {
        String content = "private \"payload\" / 005924";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange, 200,
                "{\"access_token\":\"secret-token\",\"expires_in\":7200}"));
        server.createContext("/v2/groups/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            JsonObject error = new JsonObject();
            error.addProperty("err_code", 42);
            error.addProperty("message", "rejected " + content + " secret-token secret\nforged-log\u2028line");
            error.addProperty("trace_id", "bad\ntrace");
            respond(exchange, 400, error.toString());
        });
        server.start();
        try (HttpClient client = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort())) {
            QqOpenApiClient api = new QqOpenApiClient(client, config());
            var error = assertThrows(java.util.concurrent.CompletionException.class,
                    () -> api.sendMessage("group", markdown(content)).join());
            java.io.StringWriter stack = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(stack));
            for (String sensitive : List.of(content, "secret-token", "005924")) assertFalse(stack.toString().contains(sensitive));
            String message = error.getCause().getMessage();
            assertFalse(message.contains("\n"));
            assertFalse(message.contains("\u2028"));
            assertFalse(message.contains("trace_id="));
            assertTrue(message.contains("[redacted]"));
        }
    }

    @Test
    void tokenFailureUsesTheSameDetailsAndNeverEchoesCredentials() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange, 400, """
                {"code":11243,"err_code":40012001,"message":"invalid secret private-token",
                 "access_token":"private-token","trace_id":"trace-token"}
                """));
        server.start();
        try (HttpClient client = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort())) {
            QqOpenApiClient api = new QqOpenApiClient(client, config());
            var error = assertThrows(java.util.concurrent.CompletionException.class,
                    () -> api.listGroupJoinRequests("group", "").join());
            String message = error.getCause().getMessage();
            assertTrue(message.contains("Access token request failed (HTTP 400, code=11243, err_code=40012001)"));
            assertTrue(message.contains("trace_id=trace-token"));
            assertFalse(message.contains("secret"));
            assertFalse(message.contains("private-token"));
        }
    }

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

        QqPlatformConfig config = config();
        HttpClient httpClient = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort());
        QqAccessTokenProvider tokens = new QqAccessTokenProvider(httpClient, config);
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config, tokens);

        api.replyMessage("group-id", "incoming-one", 1, markdown("## first")).join();
        api.replyMessage("group-id", "incoming-two", 2, markdown("**second**")).join();

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

        QqPlatformConfig config = config();
        HttpClient httpClient = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort());
        QqAccessTokenProvider tokens = new QqAccessTokenProvider(httpClient, config);
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config, tokens);

        api.replyMessage("group-id", "incoming-id", 1, markdown("## hello")).join();

        assertEquals(2, tokenRequests.get());
        assertEquals(List.of("QQBot token-1", "QQBot token-2"), authorizations);
        httpClient.shutdownNow();
    }

    @Test
    void reviewsExactJoinRequestsWithOfficialOperationsAndRefreshesExpiredToken() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        List<JsonObject> bodies = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        List<String> authorizations = new ArrayList<>();
        List<String> appIds = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange, 200,
                "{\"access_token\":\"token-" + tokenRequests.incrementAndGet()
                        + "\",\"expires_in\":\"7200\"}"));
        server.createContext("/v2/groups/", exchange -> {
            paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            appIds.add(exchange.getRequestHeaders().getFirst("X-Union-Appid"));
            bodies.add(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject());
            respond(exchange, bodies.size() == 1 ? 401 : 200,
                    bodies.size() == 1 ? "{\"code\":11243}" : "{}");
        });
        server.start();

        try (HttpClient client = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort())) {
            QqOpenApiClient api = new QqOpenApiClient(client, config());
            api.reviewGroupJoinRequest("group/a", "member a/b", "application-one", true).join();
            api.reviewGroupJoinRequest("group/a", "member a/b", "application-two", false).join();

            assertEquals(2, tokenRequests.get());
            assertEquals(List.of("QQBot token-1", "QQBot token-2", "QQBot token-2"), authorizations);
            assertEquals(List.of("app", "app", "app"), appIds);
            assertTrue(paths.stream().allMatch(path -> path.equals(
                    "POST /v2/groups/group%2Fa/approval_join_request/member%20a%2Fb")));
            assertEquals("approve", bodies.get(0).get("op").getAsString());
            assertEquals(bodies.get(0), bodies.get(1));
            assertEquals("application-one", bodies.get(1).get("join_request_id").getAsString());
            assertEquals("decline", bodies.get(2).get("op").getAsString());
            assertEquals("application-two", bodies.get(2).get("join_request_id").getAsString());
            assertTrue(bodies.stream().allMatch(body -> body.size() == 2));
        }
    }

    @Test
    void failedJoinReviewDoesNotReportSuccessOrRetryWithoutLimit() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange, 200,
                "{\"access_token\":\"token\",\"expires_in\":\"7200\"}"));
        server.createContext("/v2/groups/", exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 401, "{\"code\":11243}");
        });
        server.start();
        try (HttpClient client = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort())) {
            QqOpenApiClient api = new QqOpenApiClient(client, config());
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> api.reviewGroupJoinRequest("group", "member", "application", true).join());
            assertEquals(2, attempts.get());
        }
    }

    @Test
    void textAndMarkdownUseExclusiveFieldsAndOnlyRepliesCarryPassiveIds() throws Exception {
        List<String> bodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange, 200,
                "{\"access_token\":\"test-token\",\"expires_in\":\"7200\"}"));
        server.createContext("/v2/groups/group-id/messages", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"id\":\"sent-message\"}");
        });
        server.start();

        QqPlatformConfig config = config();
        HttpClient httpClient = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort());
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config);

        api.sendMessage("group-id", markdown("Alex: hello")).join();

        JsonObject body = JsonParser.parseString(bodies.getFirst()).getAsJsonObject();
        assertEquals(2, body.get("msg_type").getAsInt());
        assertEquals("Alex: hello", body.getAsJsonObject("markdown")
                .get("content").getAsString());
        assertFalse(body.has("msg_id"));
        assertFalse(body.has("msg_seq"));
        assertFalse(body.has("content"));

        String literal = "(hello) [item] C:\\temp 20.00";
        api.sendMessage("group-id", text(literal)).join();
        api.replyMessage("group-id", "incoming", 1, text(literal)).join();
        for (int index = 1; index <= 2; index++) {
            JsonObject plain = JsonParser.parseString(bodies.get(index)).getAsJsonObject();
            assertEquals(0, plain.get("msg_type").getAsInt());
            assertEquals(literal, plain.get("content").getAsString());
            assertFalse(plain.has("markdown"));
            assertEquals(index == 2, plain.has("msg_id"));
            assertEquals(index == 2, plain.has("msg_seq"));
            if (index == 2) {
                assertEquals("incoming", plain.get("msg_id").getAsString());
                assertEquals(1, plain.get("msg_seq").getAsInt());
            }
        }
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

        QqPlatformConfig config = config();
        HttpClient httpClient = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort());
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

        QqPlatformConfig config = config();
        HttpClient httpClient = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort());
        QqAccessTokenProvider tokens = new QqAccessTokenProvider(httpClient, config);
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config, tokens);
        List<CompletableFuture<Void>> replies = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            replies.add(api.replyMessage(
                    "group-id", "incoming-" + index, 1, markdown("## hello")));
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
        QqPlatformConfig config = config();
        HttpClient httpClient = QqTestHttpClient.create(server == null ? 1 : server.getAddress().getPort());
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config,
                new QqAccessTokenProvider(httpClient, config));

        assertThrows(IllegalArgumentException.class,
                () -> api.replyMessage("group-id", "", 1, markdown("## message")));
        assertThrows(IllegalArgumentException.class,
                () -> api.replyMessage("group-id", "incoming-id", 0, markdown("## message")));
        assertThrows(IllegalArgumentException.class,
                () -> api.replyMessage("group-id", "incoming-id", 6, markdown("## message")));
        assertThrows(IllegalArgumentException.class,
                () -> api.replyMessage("group-id", "incoming-id", 1, markdown(" ")));
        assertThrows(IllegalArgumentException.class,
                () -> api.sendMessage("", markdown("message")));
        assertThrows(IllegalArgumentException.class,
                () -> api.sendMessage("group-id", markdown(" ")));
        httpClient.shutdownNow();
    }

    private static QqPlatformConfig config() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                app-id: app
                app-secret: secret
                allowed-group-openids: [group-id, group, group-a]
                """);
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
