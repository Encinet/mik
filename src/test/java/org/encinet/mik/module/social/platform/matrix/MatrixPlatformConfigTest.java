package org.encinet.mik.module.social.platform.matrix;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.i18n.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixPlatformConfigTest {

    @Test
    void credentialsRoomsAndRuntimePolicyComeFromYaml() throws Exception {
        MatrixPlatformConfig config = MatrixPlatformConfig.from(yaml("""
                enabled: true
                homeserver-url: https://matrix.example.org/client/
                access-token: secret-token
                default-language: ja_jp
                allowed-room-ids: ['!allowed:example.org', '!prefixed:example.org']
                chat-bridge:
                  routes:
                    - room-id: '!allowed:example.org'
                      outbound:
                        mode: always
                    - room-id: '!prefixed:example.org'
                      outbound:
                        mode: prefix
                        prefix: '#'
                        strip-prefix: true
                sync:
                  timeout-millis: 45000
                  max-response-bytes: 1048576
                  reconnect-base-delay-millis: 250
                  reconnect-max-delay-millis: 5000
                  max-reconnect-attempts: 7
                worker:
                  max-concurrent-tasks: 32
                content-safety:
                  enabled: false
                reply:
                  max-message-length: 12
                """));

        assertTrue(config.enabled());
        assertEquals("https://matrix.example.org/client", config.homeserverUri().toString());
        assertEquals("https://matrix.example.org/client/_matrix/client/v3/sync",
                config.apiUri("/_matrix/client/v3/sync").toString());
        assertEquals("secret-token", config.accessToken());
        assertEquals(Language.JA_JP, config.defaultLanguage());
        assertTrue(config.acceptsRoom("!allowed:example.org"));
        assertFalse(config.acceptsRoom("!other:example.org"));
        assertEquals("hello", config.chatRoute("!allowed:example.org")
                .orElseThrow().outbound().select("hello").orElseThrow());
        assertTrue(config.chatRoute("!prefixed:example.org")
                .orElseThrow().outbound().select("hello").isEmpty());
        assertEquals("hello", config.chatRoute("!prefixed:example.org")
                .orElseThrow().outbound().select("#hello").orElseThrow());
        assertEquals(45_000, config.syncTimeoutMillis());
        assertEquals(1_048_576, config.syncMaxResponseBytes());
        assertEquals(250, config.reconnectBaseDelayMillis());
        assertEquals(5_000, config.reconnectMaxDelayMillis());
        assertEquals(7, config.maxReconnectAttempts());
        assertEquals(32, config.workerMaxConcurrentTasks());
        assertFalse(config.contentSafetyEnabled());
        assertEquals(100, config.maxOutboundLength());
    }

    @Test
    void disabledDefaultsAcceptEveryJoinedRoom() throws Exception {
        MatrixPlatformConfig config = MatrixPlatformConfig.from(yaml("enabled: false"));

        assertEquals("https://matrix-client.matrix.org", config.homeserverUri().toString());
        assertTrue(config.acceptsRoom("!anywhere:example.org"));
        assertEquals(30_000, config.syncTimeoutMillis());
        assertEquals(2_097_152, config.syncMaxResponseBytes());
        assertEquals(Language.ZH_CN, config.defaultLanguage());
        assertEquals("social/matrix.yml", MatrixPlatformConfig.FILE_NAME);
        assertTrue(config.chatRoutes().isEmpty());
    }

    @Test
    void enabledConfigurationRequiresAToken() {
        assertThrows(IllegalArgumentException.class, () -> MatrixPlatformConfig.from(yaml("""
                enabled: true
                homeserver-url: https://matrix.example.org
                """)));
    }

    @Test
    void endpointsRoomsAndBoundsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> MatrixPlatformConfig.from(yaml("""
                homeserver-url: file:///tmp/not-a-homeserver
                """)));
        assertThrows(IllegalArgumentException.class, () -> MatrixPlatformConfig.from(yaml("""
                allowed-room-ids: ['#alias:example.org']
                """)));
        assertThrows(IllegalArgumentException.class, () -> MatrixPlatformConfig.from(yaml("""
                sync:
                  timeout-millis: 999
                """)));
        assertThrows(IllegalArgumentException.class, () -> MatrixPlatformConfig.from(yaml("""
                sync:
                  max-response-bytes: 65535
                """)));
        assertThrows(IllegalArgumentException.class, () -> MatrixPlatformConfig.from(yaml("""
                sync:
                  reconnect-base-delay-millis: 5000
                  reconnect-max-delay-millis: 4999
                """)));
        assertThrows(IllegalArgumentException.class, () -> MatrixPlatformConfig.from(yaml("""
                allowed-room-ids: ['!allowed:example.org']
                chat-bridge:
                  routes:
                    - room-id: '!other:example.org'
                      outbound:
                        mode: always
                """)));
        assertThrows(IllegalArgumentException.class, () -> MatrixPlatformConfig.from(yaml("""
                chat-bridge:
                  routes:
                    - room-id: '!room:example.org'
                      outbound:
                        mode: prefix
                        prefix: ''
                """)));
    }

    private static YamlConfiguration yaml(String value) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(value);
        return yaml;
    }
}
