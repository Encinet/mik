package org.encinet.mik.module.social.platform.qq;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.ExternalIdentityKey;
import org.encinet.mik.module.social.api.SocialConversation;
import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.chat.SocialChatMentionRequest;
import org.encinet.mik.module.social.platform.qq.client.QqAccessTokenProvider;
import org.encinet.mik.module.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.module.social.platform.qq.gateway.QqGatewaySession;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QqPlatformSessionTest {
    @Test
    void exposesConfiguredRoutesAndResolvesOnlyCurrentAppAndGroup() throws Exception {
        QqPlatformConfig config = config();
        HttpClient httpClient = HttpClient.newHttpClient();
        QqAccessTokenProvider tokens = new QqAccessTokenProvider(httpClient, config);
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config, tokens);
        QqGatewaySession gateway = new QqGatewaySession(
                httpClient, config, tokens,
                ignored -> SocialEventSink.Acceptance.IGNORED,
                ignored -> { }, Logger.getAnonymousLogger());
        QqPlatformSession session = new QqPlatformSession(
                gateway, httpClient, api, config);
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
}
