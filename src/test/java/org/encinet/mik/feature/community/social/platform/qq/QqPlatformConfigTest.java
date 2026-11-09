package org.encinet.mik.feature.community.social.platform.qq;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.feature.community.social.chat.SocialChatPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqPlatformConfigTest {

    @Test
    void joinReconciliationHasABoundedConfigurableInterval() throws Exception {
        assertEquals(30, QqPlatformConfig.from(yaml("")).groupJoinReconcileIntervalSeconds());
        for (long seconds : new long[]{10, 60, 3_600}) {
            assertEquals(seconds, QqPlatformConfig.from(yaml("""
                    group-join:
                      reconcile-interval-seconds: %d
                    """.formatted(seconds))).groupJoinReconcileIntervalSeconds());
        }
        for (long seconds : new long[]{-1, 0, 9, 3_601}) {
            assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                    group-join:
                      reconcile-interval-seconds: %d
                    """.formatted(seconds))));
        }
    }

    @Test
    void gatewayAndCredentialsComeFromTheNewYaml() throws Exception {
        QqPlatformConfig config = QqPlatformConfig.from(yaml("""
                enabled: true
                app-id: yaml-id
                app-secret: yaml-secret
                default-language: ja_jp
                allowed-group-openids: [group-a]
                gateway:
                  debug: true
                  reconnect-base-delay-millis: 250
                  reconnect-max-delay-millis: 5000
                  max-reconnect-attempts: 7
                  max-frame-bytes: 8192
                  handshake-timeout-millis: 45000
                worker:
                  max-concurrent-tasks: 32
                reply:
                  max-message-length: 12
                """));

        assertEquals("yaml-id", config.appId());
        assertEquals("yaml-secret", config.appSecret());
        assertEquals(Language.JA_JP, config.defaultLanguage());
        assertTrue(config.acceptsGroup("group-a"));
        assertFalse(config.acceptsGroup("group-b"));
        assertTrue(config.gatewayDebugEnabled());
        assertEquals(250, config.gatewayReconnectBaseDelayMillis());
        assertEquals(5_000, config.gatewayReconnectMaxDelayMillis());
        assertEquals(7, config.gatewayMaxReconnectAttempts());
        assertEquals(8_192, config.gatewayMaxFrameBytes());
        assertEquals(45_000, config.gatewayHandshakeTimeoutMillis());
        assertEquals(32, config.workerMaxConcurrentTasks());
        assertEquals(100, config.maxOutboundLength());
        assertTrue(config.contentSafetyEnabled());
    }

    @Test
    void defaultsUseGatewayDiscoveryAndAcceptEveryGroup() throws Exception {
        QqPlatformConfig config = QqPlatformConfig.from(yaml("""
                enabled: false
                api-base-url: ""
                access-token-url: ""
                """));

        assertTrue(config.acceptsGroup("any-group"));
        assertEquals("https://api.sgroup.qq.com/gateway/bot",
                config.apiUri("/gateway/bot").toString());
        assertEquals("https://bots.qq.com/app/getAppAccessToken",
                config.accessTokenUri().toString());
        assertEquals(1_000, config.gatewayReconnectBaseDelayMillis());
        assertEquals(30_000, config.gatewayReconnectMaxDelayMillis());
        assertEquals(10, config.gatewayMaxReconnectAttempts());
        assertEquals(1_048_576, config.gatewayMaxFrameBytes());
        assertEquals(30_000, config.gatewayHandshakeTimeoutMillis());
        assertEquals(Language.ZH_CN, config.defaultLanguage());
        assertTrue(config.chatRoutes().isEmpty());
        assertEquals("social/qq.yml", QqPlatformConfig.FILE_NAME);
    }

    @Test
    void chatBridgeRoutesSupportAlwaysAndPrefixPolicies() throws Exception {
        QqPlatformConfig config = QqPlatformConfig.from(yaml("""
                enabled: false
                allowed-group-openids: [group-a, group-b]
                chat-bridge:
                  routes:
                    - group-openid: group-a
                      inbound:
                        mode: prefix
                        prefix: ">"
                      outbound:
                        mode: always
                    - group-openid: group-b
                      outbound:
                        mode: prefix
                        prefix: "#"
                        strip-prefix: false
                """));

        assertEquals(2, config.chatRoutes().size());
        assertEquals("group-a", config.chatRoutes().get(0).conversation().id());
        assertEquals(SocialChatPolicy.prefixed(">", true), config.chatRoutes().get(0).inbound());
        assertEquals(SocialChatPolicy.Mode.ALWAYS,
                config.chatRoutes().get(0).outbound().mode());
        assertEquals("group-b", config.chatRoutes().get(1).conversation().id());
        assertEquals(SocialChatPolicy.always(), config.chatRoutes().get(1).inbound());
        assertEquals(SocialChatPolicy.Mode.PREFIX,
                config.chatRoutes().get(1).outbound().mode());
        assertEquals("#", config.chatRoutes().get(1).outbound().prefix());
        assertFalse(config.chatRoutes().get(1).outbound().stripPrefix());
        assertTrue(config.chatRoute("group-b").isPresent());
    }

    @Test
    void chatBridgeRoutesMustBeAllowedUniqueAndValid() {
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                allowed-group-openids: [group-a]
                chat-bridge:
                  routes:
                    - group-openid: group-b
                """)));
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                chat-bridge:
                  routes:
                    - group-openid: group-a
                    - group-openid: group-a
                """)));
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                chat-bridge:
                  routes:
                    - group-openid: group-a
                      outbound:
                        mode: sometimes
                """)));
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                chat-bridge:
                  routes:
                    - group-openid: ""
                """)));
    }

    @Test
    void removedCredentialAndTransportKeysAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: true
                app-id: app
                client-secret: legacy-yaml-secret
                """)));
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                event-transport: gateway
                gateway-url: wss://example.invalid/socket
                """)));
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                webhook:
                  port: 35354
                """)));
    }

    @Test
    void enabledConfigurationRequiresBothCredentials() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: true
                app-id: only-an-id
                """)));
    }

    @Test
    void endpointSchemesAreValidated() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                api-base-url: file:///tmp/not-an-api
                """)));
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                access-token-url: file:///tmp/not-a-token-endpoint
                """)));
    }

    @Test
    void unifiedDomainsFallBackToThirdPartyCompatibleEndpoints() throws Exception {
        QqPlatformConfig config = QqPlatformConfig.from(yaml("""
                enabled: false
                api-base-url: https://api.bot.qq.com
                access-token-url: https://api.bot.qq.com/app/getAppAccessToken
                """));

        assertEquals("https://api.sgroup.qq.com/gateway/bot",
                config.apiUri("/gateway/bot").toString());
        assertEquals("https://bots.qq.com/app/getAppAccessToken",
                config.accessTokenUri().toString());
    }

    @Test
    void gatewayBoundsAndWorkerBoundsAreValidated() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                gateway:
                  reconnect-base-delay-millis: 99
                """)));
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                gateway:
                  reconnect-base-delay-millis: 5000
                  reconnect-max-delay-millis: 4999
                """)));
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                gateway:
                  max-frame-bytes: 4095
                """)));
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                gateway:
                  handshake-timeout-millis: 999
                """)));
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                worker:
                  max-concurrent-tasks: 513
                """)));
    }

    @Test
    void contentSafetyCanBeExplicitlyDisabled() throws Exception {
        QqPlatformConfig config = QqPlatformConfig.from(yaml("""
                enabled: false
                content-safety:
                  enabled: false
                """));

        assertFalse(config.contentSafetyEnabled());
    }

    @Test
    void unsupportedDefaultLanguageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> QqPlatformConfig.from(yaml("""
                enabled: false
                default-language: klingon
                """)));
    }

    @Test
    void joinApprovalLoadsTwoIndependentGroupSecretsAndDefaultsToDisabled() throws Exception {
        assertTrue(QqPlatformConfig.from(yaml("")).groupJoinRules().isEmpty());
        QqPlatformConfig config = QqPlatformConfig.from(yaml("""
                allowed-group-openids: [group-a, group-b]
                group-join:
                  groups:
                    - group-openid: group-a
                      totp-secret: GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
                    - group-openid: group-b
                      totp-secret: JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP
                """));
        assertEquals(java.util.List.of("group-a", "group-b"), config.groupJoinRules().stream()
                .map(org.encinet.mik.feature.community.social.platform.qq.join.QqGroupJoinRule::groupOpenId)
                .toList());
        assertFalse(config.groupJoinRules().get(0).totp()
                .hasSameSecret(config.groupJoinRules().get(1).totp()));
    }

    @Test
    void rejectsAmbiguousIncompleteOrDisallowedJoinRules() {
        String first = """
                group-join:
                  groups:
                    - group-openid: group-a
                      totp-secret: GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
                """;
        for (String invalid : java.util.List.of(
                "allowed-group-openids: [other]\n" + first,
                first + """
                    - group-openid: group-a
                      totp-secret: JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP
                """,
                first + """
                    - group-openid: group-b
                      totp-secret: gezdgnbvgy3tqojqgezdgnbvgy3tqojq
                """,
                "group-join: {groups: [{group-openid: group-a}]}",
                "group-join: {groups: [invalid]}",
                "group-join: {groups: invalid}")) {
            assertThrows(IllegalArgumentException.class,
                    () -> QqPlatformConfig.from(yaml(invalid)));
        }
    }

    private static YamlConfiguration yaml(String value) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(value);
        return yaml;
    }
}
