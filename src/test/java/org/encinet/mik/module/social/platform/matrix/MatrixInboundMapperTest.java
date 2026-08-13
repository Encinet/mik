package org.encinet.mik.module.social.platform.matrix;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.module.social.platform.matrix.sync.MatrixRoomMessage;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixInboundMapperTest {

    @Test
    void mapsGlobalMxidsAndOnlyStructuredReferences() throws Exception {
        MatrixPlatformConfig config = config();
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixClient client = new MatrixClient(httpClient, config);
        AtomicReference<SocialInboundMessage> received = new AtomicReference<>();
        MatrixInboundMapper mapper = new MatrixInboundMapper(config, client, message -> {
            received.set(message);
            return SocialEventSink.Acceptance.ACCEPTED;
        });
        MatrixRoomMessage event = new MatrixRoomMessage("$event", "!room:example.org",
                new MatrixRoomMessage.MatrixMember("@alice:remote.example", "Alice"),
                "@bot:example.org !profile @forged:example.org",
                List.of(
                        new MatrixRoomMessage.MatrixMention(
                                new MatrixRoomMessage.MatrixMember(
                                        "@bot:example.org", "Bot"), 0, 16),
                        new MatrixRoomMessage.MatrixMention(
                                new MatrixRoomMessage.MatrixMember(
                                        "@bob:example.org", "Bob"), 26, 45)),
                Optional.of(new MatrixRoomMessage.MatrixMember(
                        "@carol:elsewhere.org", "Carol")), Optional.empty());

        assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                mapper.accept(event, "@bot:example.org"));

        SocialInboundMessage message = received.get();
        assertEquals("!profile @forged:example.org",
                ((SocialInboundMessage.Text) message.content()).body());
        assertEquals("matrix", message.authenticatedIdentity().orElseThrow()
                .key().platform());
        assertEquals("remote.example", message.authenticatedIdentity().orElseThrow()
                .key().issuer());
        assertEquals("", message.authenticatedIdentity().orElseThrow().key().scope());
        assertEquals("@alice:remote.example", message.authenticatedIdentity().orElseThrow()
                .key().subject());
        assertEquals(List.of("@carol:elsewhere.org", "@bob:example.org"),
                message.references().targetIdentities().stream()
                        .map(identity -> identity.key().subject()).toList());
        assertEquals(1, message.references().mentionSpans().size());
        assertEquals(9, message.references().mentionSpans().getFirst().start());
        assertTrue(message.replyChannel() instanceof MatrixReplyChannel);
        httpClient.shutdownNow();
    }

    @Test
    void ignoresSelfMessagesAndRoomsOutsideTheAllowlist() throws Exception {
        MatrixPlatformConfig config = config();
        HttpClient httpClient = HttpClient.newHttpClient();
        MatrixInboundMapper mapper = new MatrixInboundMapper(config,
                new MatrixClient(httpClient, config), message -> {
                    throw new AssertionError("ignored event reached the host");
                });

        assertEquals(SocialEventSink.Acceptance.IGNORED,
                mapper.accept(message("!room:example.org", "@bot:example.org"),
                        "@bot:example.org"));
        assertEquals(SocialEventSink.Acceptance.IGNORED,
                mapper.accept(message("!other:example.org", "@alice:example.org"),
                        "@bot:example.org"));
        httpClient.shutdownNow();
    }

    @Test
    void submitsEachTextEventOnceForHostClassification()
            throws Exception {
        MatrixPlatformConfig config = bridgeConfig();
        HttpClient httpClient = HttpClient.newHttpClient();
        AtomicReference<SocialInboundMessage> received = new AtomicReference<>();
        MatrixInboundMapper mapper = new MatrixInboundMapper(config,
                new MatrixClient(httpClient, config),
                message -> {
                    received.set(message);
                    return SocialEventSink.Acceptance.ACCEPTED;
                });

        MatrixRoomMessage chat = new MatrixRoomMessage("$chat", "!room:example.org",
                new MatrixRoomMessage.MatrixMember("@alice:example.org", "Alice"),
                "hello Minecraft", List.of(), Optional.empty(), Optional.empty());
        assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                mapper.accept(chat, "@bot:example.org"));
        assertEquals("Alice", received.get().senderDisplayName());
        assertEquals("hello Minecraft",
                ((SocialInboundMessage.Text) received.get().content()).body());

        MatrixRoomMessage shortChat = new MatrixRoomMessage("$js", "!room:example.org",
                new MatrixRoomMessage.MatrixMember("@alice:example.org", "Alice"),
                "js", List.of(), Optional.empty(), Optional.empty());
        assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                mapper.accept(shortChat, "@bot:example.org"));
        assertEquals("js",
                ((SocialInboundMessage.Text) received.get().content()).body());

        received.set(null);
        MatrixRoomMessage outside = new MatrixRoomMessage("$outside", "!commands:example.org",
                new MatrixRoomMessage.MatrixMember("@alice:example.org", "Alice"),
                "ordinary text", List.of(), Optional.empty(), Optional.empty());
        assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                mapper.accept(outside, "@bot:example.org"));
        assertEquals("ordinary text",
                ((SocialInboundMessage.Text) received.get().content()).body());
        httpClient.shutdownNow();
    }

    private static MatrixRoomMessage message(String room, String sender) {
        return new MatrixRoomMessage("$event", room,
                new MatrixRoomMessage.MatrixMember(sender, sender),
                "!status", List.of(), Optional.empty(), Optional.empty());
    }

    private static MatrixPlatformConfig config() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                homeserver-url: https://matrix.example.org
                access-token: token
                allowed-room-ids: ['!room:example.org']
                """);
        return MatrixPlatformConfig.from(yaml);
    }

    private static MatrixPlatformConfig bridgeConfig() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                homeserver-url: https://matrix.example.org
                access-token: token
                allowed-room-ids:
                  - '!room:example.org'
                  - '!commands:example.org'
                chat-bridge:
                  routes:
                    - room-id: '!room:example.org'
                      outbound:
                        mode: always
                """);
        return MatrixPlatformConfig.from(yaml);
    }
}
