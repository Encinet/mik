package org.encinet.mik.feature.community.social.platform.qq;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialEventSink;
import org.encinet.mik.feature.community.social.chat.SocialChatMentionRequest;
import org.encinet.mik.feature.community.social.platform.qq.client.QqAccessTokenProvider;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewaySession;
import org.encinet.mik.feature.community.social.platform.qq.join.QqGroupJoinApproval;
import org.encinet.mik.feature.community.social.platform.qq.join.QqGroupJoinReconciler;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqPlatformSessionTest {
    @Test
    void sendsAnnouncementsAsProactiveDocumentsWithoutPlayerOrPassiveReplyFields() throws Exception {
        List<JsonObject> messages = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/app/getAppAccessToken", exchange -> respond(exchange,
                "{\"access_token\":\"token\",\"expires_in\":7200}"));
        server.createContext("/v2/groups/group-a/messages", exchange -> {
            messages.add(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject());
            respond(exchange, "{}");
        });
        server.start();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                app-id: app
                app-secret: secret
                api-base-url: http://127.0.0.1:%d
                access-token-url: http://127.0.0.1:%d/app/getAppAccessToken
                """.formatted(server.getAddress().getPort(), server.getAddress().getPort()));
        QqPlatformConfig config = QqPlatformConfig.from(yaml);
        HttpClient client = HttpClient.newHttpClient();
        QqAccessTokenProvider tokens = new QqAccessTokenProvider(client, config);
        QqOpenApiClient api = new QqOpenApiClient(client, config, tokens);
        QqGroupJoinApproval approvals = new QqGroupJoinApproval(config, api, Logger.getAnonymousLogger());
        QqPlatformSession session = new QqPlatformSession(new QqGatewaySession(client, config, tokens,
                _ -> SocialEventSink.Acceptance.IGNORED, _ -> SocialEventSink.Acceptance.IGNORED,
                _ -> { }, Logger.getAnonymousLogger()), client, api, config,
                approvals, new QqGroupJoinReconciler(config, api, approvals, Logger.getAnonymousLogger()));
        try {
            SocialDocument notice = SocialDocument.of(SocialDocument.Moderation.REQUIRED,
                    "Announcement", SocialDocument.Tone.INFO, SocialDocument.paragraph("First line\n<everyone> *text*"));
            SocialConversation group = new SocialConversation("group-a", SocialConversation.Type.GROUP);
            session.sendNotice(group, notice).toCompletableFuture().join();
            assertEquals(1, messages.size());
            JsonObject message = messages.getFirst();
            assertFalse(message.has("msg_id"));
            assertFalse(message.has("msg_seq"));
            String markdown = message.getAsJsonObject("markdown").get("content").getAsString();
            assertTrue(markdown.startsWith("## Announcement"));
            assertEquals("## Announcement\n\nFirst line  \n\\<everyone\\> \\*text\\*", markdown);
            session.close();
            session.sendNotice(group, notice).toCompletableFuture().join();
            assertEquals(1, messages.size());
        } finally {
            session.close();
            server.stop(0);
        }
    }

    @Test
    void exposesConfiguredRoutesAndResolvesOnlyCurrentAppAndGroup() throws Exception {
        QqPlatformConfig config = config();
        HttpClient httpClient = HttpClient.newHttpClient();
        QqAccessTokenProvider tokens = new QqAccessTokenProvider(httpClient, config);
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config, tokens);
        QqGatewaySession gateway = new QqGatewaySession(
                httpClient, config, tokens,
                _ -> SocialEventSink.Acceptance.IGNORED,
                _ -> SocialEventSink.Acceptance.IGNORED,
                _ -> { }, Logger.getAnonymousLogger());
        QqGroupJoinApproval approvals = new QqGroupJoinApproval(config, api, Logger.getAnonymousLogger());
        QqPlatformSession session = new QqPlatformSession(
                gateway, httpClient, api, config,
                approvals, new QqGroupJoinReconciler(config, api, approvals, Logger.getAnonymousLogger()));
        UUID playerId = UUID.randomUUID();
        ExternalIdentity current = identity("app", "group-a", "current");
        ExternalIdentity wrongApp = identity("other-app", "group-a", "wrong-app");
        ExternalIdentity wrongGroup = identity("app", "group-b", "wrong-group");
        ExternalIdentity wrongPlatform = new ExternalIdentity(
                new ExternalIdentityKey("matrix", "server", "group-a", "wrong-platform"),
                "Wrong platform");

        try {
            assertEquals(1, session.chatRoutes().size());
            assertEquals("group-a", session.chatRoutes().getFirst().conversation().id());
            assertEquals(List.of(current), session.resolveMentions(
                    new SocialConversation("group-a", SocialConversation.Type.GROUP),
                    List.of(new SocialChatMentionRequest(playerId, "Alex",
                            List.of(current, wrongApp, wrongGroup, wrongPlatform))))
                    .targetsFor(playerId));
        } finally {
            session.close();
        }
    }

    private static ExternalIdentity identity(String appId, String groupId, String subject) {
        return new ExternalIdentity(new ExternalIdentityKey(
                "qq", appId, groupId, subject), subject);
    }

    private static QqPlatformConfig config() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: false
                app-id: app
                app-secret: secret
                chat-bridge:
                  routes:
                    - group-openid: group-a
                """);
        return QqPlatformConfig.from(yaml);
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
