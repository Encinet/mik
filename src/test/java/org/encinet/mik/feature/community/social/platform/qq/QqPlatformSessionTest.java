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
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatStyle;
import org.encinet.mik.feature.community.social.api.SocialSessionStatus;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGroupMemberEvent;
import org.encinet.mik.test.ChatTestMessages;
import org.encinet.mik.feature.community.social.platform.qq.client.QqAccessTokenProvider;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewaySession;
import org.encinet.mik.feature.community.social.platform.qq.join.QqGroupJoinApproval;
import org.encinet.mik.feature.community.social.platform.qq.join.QqGroupJoinReconciler;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.encinet.mik.test.QqTestHttpClient;

import java.net.http.HttpClient;
import java.nio.file.Path;
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
    @TempDir Path temporary;

    @Test
    void sendsAnnouncementsAsProactiveDocumentsWithoutPlayerOrPassiveReplyFields() throws Exception {
        try (var fixture = sessionFixture()) {
            var session = fixture.session();
            var messages = fixture.messages();
            SocialDocument notice = SocialDocument.of(SocialDocument.Moderation.REQUIRED,
                    "Announcement", SocialDocument.Tone.INFO, SocialDocument.paragraph("First line\n<everyone> *text*"));
            SocialConversation group = new SocialConversation("group-a", SocialConversation.Type.GROUP);
            session.prepareNotice(group, notice, 2_000).send().join();
            assertEquals(1, messages.size());
            JsonObject message = messages.getFirst();
            assertFalse(message.has("msg_id"));
            assertFalse(message.has("msg_seq"));
            String markdown = message.getAsJsonObject("markdown").get("content").getAsString();
            assertTrue(markdown.startsWith("## Announcement"));
            assertEquals("## Announcement\n\nFirst line  \n&#60;everyone&#62; &#42;text&#42;", markdown);
            session.close();
            session.prepareNotice(group, notice, 2_000).send().join();
            assertEquals(1, messages.size());
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
                _ -> SocialEventSink.Acceptance.IGNORED, _ -> { }, Logger.getAnonymousLogger());
        QqGroupJoinApproval approvals = new QqGroupJoinApproval(config, api, Logger.getAnonymousLogger());
        QqMembershipDirectory monitor = new QqMembershipDirectory(config,
                temporary.resolve("memberships.json"), Logger.getAnonymousLogger());
        monitor.gateway(org.encinet.mik.feature.community.social.api.SocialSessionStatus.ready("gateway"));
        monitor.accept(new org.encinet.mik.feature.community.social.platform.qq.gateway.QqGroupMemberEvent("group-a", "current", true));
        monitor.accept(new org.encinet.mik.feature.community.social.platform.qq.gateway.QqGroupMemberEvent("group-b", "wrong-group", true));
        QqPlatformSession session = new QqPlatformSession(
                gateway, httpClient, api, config,
                approvals, new QqGroupJoinReconciler(config, api, approvals, Logger.getAnonymousLogger()), monitor);
        UUID playerId = UUID.randomUUID();
        ExternalIdentity current = identity("app", "", "current");
        ExternalIdentity wrongApp = identity("other-app", "", "current");
        ExternalIdentity wrongGroup = identity("app", "", "wrong-group");
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
            var otherGroup = new SocialConversation("group-b", SocialConversation.Type.GROUP);
            var request = List.of(new SocialChatMentionRequest(playerId, "Alex", List.of(current)));
            assertTrue(session.resolveMentions(otherGroup, request).targetsFor(playerId).isEmpty());
            monitor.accept(new org.encinet.mik.feature.community.social.platform.qq.gateway.QqGroupMemberEvent("group-b", "current", true));
            assertEquals(List.of(current), session.resolveMentions(otherGroup, request).targetsFor(playerId));
            session.close();
            assertTrue(session.resolveMentions(otherGroup, request).targetsFor(playerId).isEmpty());
        } finally {
            session.close();
        }
    }

    @Test
    void sendsLiteralChatAndNativeMentionsAndRechecksMembershipBeforeSending() throws Exception {
        try (var fixture = sessionFixture()) {
            var session = fixture.session();
            var group = new SocialConversation("group-a", SocialConversation.Type.GROUP);
            fixture.memberships().gateway(SocialSessionStatus.ready("gateway"));
            fixture.memberships().accept(new QqGroupMemberEvent("group-a", "target", true));
            String literal = "(括号) [物品] C:\\temp 20.00 *literal*";
            session.sendChat(group, ChatTestMessages.minecraft("Steve", literal)).toCompletableFuture().join();
            var plain = fixture.messages().getLast();
            assertEquals(0, plain.get("msg_type").getAsInt());
            assertEquals("Steve: " + literal, plain.get("content").getAsString());
            assertFalse(plain.has("markdown"));
            assertFalse(plain.has("msg_id"));

            UUID playerId = UUID.randomUUID();
            var mention = new ChatNode.PlayerMention(playerId, "Alex", ChatStyle.EMPTY);
            var resolution = session.resolveMentions(group, List.of(new SocialChatMentionRequest(
                    playerId, "Alex", List.of(identity("app", "", "target")))));
            var message = ChatTestMessages.minecraft("Steve", mention.visibleText(), new ChatContent(List.of(mention)));
            session.sendChat(group, message, resolution).toCompletableFuture().join();
            var nativeMention = fixture.messages().getLast();
            assertEquals(2, nativeMention.get("msg_type").getAsInt());
            assertFalse(nativeMention.has("content"));
            assertEquals("Steve: &#64;Alex(<qqbot-at-user id=\"target\" />)",
                    nativeMention.getAsJsonObject("markdown").get("content").getAsString());

            fixture.memberships().accept(new QqGroupMemberEvent("group-a", "target", false));
            session.sendChat(group, message, resolution).toCompletableFuture().join();
            var stale = fixture.messages().getLast();
            assertEquals(0, stale.get("msg_type").getAsInt());
            assertEquals("Steve: @Alex", stale.get("content").getAsString());
        }
    }

    private SessionFixture sessionFixture() throws Exception {
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
                allowed-group-openids: [group-a, group-b]
                """);
        QqPlatformConfig config = QqPlatformConfig.from(yaml);
        HttpClient client = QqTestHttpClient.create(server.getAddress().getPort());
        QqAccessTokenProvider tokens = new QqAccessTokenProvider(client, config);
        QqOpenApiClient api = new QqOpenApiClient(client, config, tokens);
        QqGroupJoinApproval approvals = new QqGroupJoinApproval(config, api, Logger.getAnonymousLogger());
        QqMembershipDirectory monitor = new QqMembershipDirectory(config,
                temporary.resolve("memberships.json"), Logger.getAnonymousLogger());
        QqPlatformSession session = new QqPlatformSession(new QqGatewaySession(client, config, tokens,
                _ -> SocialEventSink.Acceptance.IGNORED, _ -> SocialEventSink.Acceptance.IGNORED,
                _ -> SocialEventSink.Acceptance.IGNORED, _ -> { }, Logger.getAnonymousLogger()), client, api, config,
                approvals, new QqGroupJoinReconciler(config, api, approvals, Logger.getAnonymousLogger()),
                monitor);
        return new SessionFixture(session, monitor, messages, server);
    }

    private record SessionFixture(QqPlatformSession session, QqMembershipDirectory memberships,
                                  List<JsonObject> messages, HttpServer server) implements AutoCloseable {
        @Override
        public void close() {
            try { session.close(); }
            finally { server.stop(0); }
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
                allowed-group-openids: [group-a, group-b]
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
