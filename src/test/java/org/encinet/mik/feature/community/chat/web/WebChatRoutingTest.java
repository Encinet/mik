package org.encinet.mik.feature.community.chat.web;

import com.google.gson.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.encinet.mik.feature.community.chat.game.GameChatPresenceListener;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.community.chat.model.*;
import org.encinet.mik.feature.community.chat.runtime.ChatService;
import org.encinet.mik.feature.community.chat.runtime.ChatSendLedger;
import org.encinet.mik.feature.community.chat.bridge.SocialChatPublishReport;
import org.encinet.mik.feature.community.chat.pipeline.ChatProcessor;
import org.encinet.mik.feature.player.security.WebAccountPolicy;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class WebChatRoutingTest {
    @Test void socialAuthorsReachAuthorizedBrowsersWithoutAConnectedMinecraftSender() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID observer = f.account("Observer", "member"), offline = f.account("Offline", "member");
            Client browser = f.join(observer, "o"); f.clearFrames();
            for (boolean bound : List.of(false, true)) {
                ChatSender sender = new ChatSender("Guest", bound ? Optional.of(offline) : Optional.empty(),
                        Optional.empty(), bound ? "Offline" : "", "", "", 0xFFFFFF);
                f.chat.accept(new ChatSubmission(ChatMessageId.external("matrix", "room", "event-" + bound),
                        new ChatOrigin.Social("matrix", "Matrix", 0x55FFFF, "room", "event-" + bound), sender,
                        ChatConversation.minecraftPublic(), "from social", ChatReferences.empty(), Instant.now(), ChatProcessingContext.external()));
                List<JsonObject> entries = browser.messages(); assertEquals(1, entries.size());
                assertEquals("social", text(entries.getFirst().getAsJsonObject("source"), "kind"));
                assertEquals("Matrix", text(entries.getFirst().getAsJsonObject("source"), "platformName"));
                assertEquals(bound, entries.getFirst().getAsJsonObject("sender").has("playerUuid"));
                assertNull(f.module.peer(observer, offline));
            }
        }
    }

    @Test void repeatedAcceptedWebRequestsDoNotRepublishOrConsumeAdditionalSendAllowance() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member");
            Client a = f.join(alice, "a"), b = f.join(bob, "b"); f.clearFrames();
            String request = UUID.randomUUID().toString();
            f.send(a, "public", null, request, "once"); assertEquals(1, b.messages().size());
            for (int i = 0; i < 5; i++) {
                a.events(); f.send(a, "public", null, request, "once");
                assertTrue(b.messages().isEmpty());
                assertTrue(a.events().stream().anyMatch(event -> text(event, "type").equals("accepted")));
            }
        }
    }

    @Test void defaultWebAccountsAppearInPresenceAndUsePublicAndPrivateChat() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID newcomer = f.account("Newcomer", "default"), member = f.account("Member", "member");
            Client observer = f.join(member, "m"); f.clearFrames();
            Client n = f.join(newcomer, "n");
            List<JsonObject> initial = n.events();
            JsonObject authorized = initial.stream().filter(event -> text(event, "type").equals("authorized")).findFirst().orElseThrow();
            assertFalse(authorized.get("staff").getAsBoolean());
            JsonObject presence = initial.stream().filter(event -> text(event, "type").equals("presence")).findFirst().orElseThrow();
            assertTrue(presence.getAsJsonArray("players").asList().stream()
                    .anyMatch(peer -> text(peer.getAsJsonObject(), "playerUuid").equals(newcomer.toString())));
            assertEquals(1, f.module.peer(member, newcomer).webConnections());
            f.send(n, "public", null, UUID.randomUUID().toString(), "hello");
            assertEquals(newcomer.toString(), text(observer.messages().getFirst().getAsJsonObject("sender"), "playerUuid"));
            n.events();
            f.send(observer, "private", newcomer, UUID.randomUUID().toString(), "welcome");
            assertEquals("private", text(n.messages().getFirst(), "channel"));
            observer.events();
            f.send(n, "private", member, UUID.randomUUID().toString(), "thanks");
            assertEquals("private", text(observer.messages().getFirst(), "channel"));
        }
    }

    @Test void defaultGamePlayersAppearInWebPresenceAndReceiveCrossPlatformMessages() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID newcomer = f.account("GameNewcomer", "default"), member = f.account("Member", "member");
            f.game(newcomer);
            assertFalse(f.gamePlayers.get(newcomer).hasPermission("group.member"));
            Client web = f.join(member, "m");
            JsonObject presence = web.events().stream().filter(event -> text(event, "type").equals("presence")).findFirst().orElseThrow();
            JsonObject peer = presence.getAsJsonArray("players").asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(player -> text(player, "playerUuid").equals(newcomer.toString())).findFirst().orElseThrow();
            assertTrue(peer.get("game").getAsBoolean());
            assertEquals(0, peer.get("webConnections").getAsInt());
            f.clearFrames();
            f.send(web, "public", null, UUID.randomUUID().toString(), "welcome");
            assertEquals(List.of(newcomer), f.gameDeliveries.stream().map(Map.Entry::getKey).toList());
            f.clearFrames();
            f.send(web, "private", newcomer, UUID.randomUUID().toString(), "private hello");
            assertEquals(List.of(newcomer), f.gameDeliveries.stream().map(Map.Entry::getKey).toList());
            f.clearFrames();
            ChatMessage message = new ChatProcessor().process(new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Minecraft(newcomer),
                    ChatSender.minecraft(newcomer, "GameNewcomer"), ChatConversation.minecraftPublic(), "from game", ChatReferences.empty(),
                    Instant.now(), ChatProcessingContext.playerText(PlayerMentions.empty())));
            f.relay(message, null, Set.of(newcomer));
            assertEquals(newcomer.toString(), text(web.messages().getFirst().getAsJsonObject("sender"), "playerUuid"));
        }
    }

    @Test void defaultChatAccessRetainsStaffPrivacyAndVisibilityRules() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID newcomer = f.account("Newcomer", "default"), moderator = f.account("Staff", "moderator"), hidden = f.account("Hidden", "default");
            f.game(hidden); f.concealed.add(hidden);
            Client n = f.join(newcomer, "n"), staff = f.join(moderator, "s"); f.clearFrames();
            assertNull(f.module.peer(newcomer, hidden));
            assertTrue(f.module.channelPeers(newcomer, ChatChannel.STAFF, null).isEmpty());
            f.send(staff, "staff", null, UUID.randomUUID().toString(), "staff only");
            assertTrue(n.messages().isEmpty()); assertEquals(1, staff.messages().size());
            f.send(n, "staff", null, UUID.randomUUID().toString(), "attempt");
            assertTrue(staff.messages().isEmpty());
            assertTrue(n.events().stream().anyMatch(event -> text(event, "error").equals("staff_required")));
            f.banned.add(newcomer);
            f.send(staff, "private", newcomer, UUID.randomUUID().toString(), "blocked");
            assertTrue(n.messages().isEmpty());
            assertTrue(staff.events().stream().anyMatch(event -> text(event, "error").equals("recipient_unavailable")));
        }
    }

    @Test void allWebChannelsAccept256UnicodeCodePointsAndReject257() throws Exception {
        for (String channel : List.of("public", "staff", "private")) {
            for (String character : List.of("a", "中", "😀")) {
                try (Fixture f = new Fixture()) {
                    UUID alice = f.account("Alice", "moderator"), bob = f.account("Bob", "moderator");
                    Client a = f.join(alice, "a"), b = f.join(bob, "b"); f.clearFrames();
                    UUID target = channel.equals("private") ? bob : null;
                    f.send(a, channel, target, UUID.randomUUID().toString(), character.repeat(256));
                    assertEquals(1, b.messages().size(), channel + " accepts 256 " + character);
                    assertTrue(a.events().stream().anyMatch(event -> text(event, "type").equals("accepted")));
                    f.send(a, channel, target, UUID.randomUUID().toString(), character.repeat(257));
                    assertTrue(b.messages().isEmpty(), channel + " rejects 257 " + character);
                    assertTrue(a.events().stream().anyMatch(event -> text(event, "error").equals("invalid_message")));
                }
            }
        }
    }

    @Test void staffCompletionIncludesOnlyVisibleStaffAndIsNotSharedWithMembers() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID staff = f.account("Staff", "moderator"), hidden = f.account("Hidden", "moderator");
            UUID member = f.account("Member", "member"), viewer = f.account("Viewer", "moderator");
            f.game(hidden); f.game(member); f.concealed.add(hidden);
            f.join(staff, "s");
            Client m = f.join(member, "m"), v = f.join(viewer, "v");
            JsonObject memberPresence = m.events().stream().filter(event -> text(event, "type").equals("presence")).findFirst().orElseThrow();
            JsonObject staffPresence = v.events().stream().filter(event -> text(event, "type").equals("presence")).findFirst().orElseThrow();
            assertTrue(memberPresence.getAsJsonArray("staffPlayers").isEmpty());
            assertEquals(Set.of(staff.toString(), viewer.toString()), staffPresence.getAsJsonArray("staffPlayers").asList().stream().map(JsonElement::getAsString).collect(java.util.stream.Collectors.toSet()));
            assertTrue(f.module.channelPeers(member, ChatChannel.STAFF, null).isEmpty());
            assertTrue(staffPresence.getAsJsonArray("players").asList().stream()
                    .noneMatch(peer -> text(peer.getAsJsonObject(), "playerUuid").equals(hidden.toString())));
        }
    }

    @Test void firstJoinIsAnnouncedBeforeImmediateMessagesOnWebAndInGame() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member");
            f.game(bob);
            Client b = f.join(bob, "b"); f.clearFrames();
            Client a = f.join(alice, "a");
            f.send(a, "public", null, UUID.randomUUID().toString(), "hello");
            for (Client client : List.of(a, b)) {
                List<JsonObject> entries = client.entries();
                assertEquals(List.of("server", "user"), entries.stream().map(entry -> text(entry, "kind")).toList());
                assertEquals("webJoined", text(entries.getFirst().getAsJsonObject("content"), "template"));
                assertEquals(alice.toString(), text(entries.getFirst().getAsJsonObject("subject"), "playerUuid"));
                assertEquals("Alice", text(entries.getFirst().getAsJsonObject("content").getAsJsonObject("values"), "name"));
                assertFalse(entries.getFirst().get("countsAsUnread").getAsBoolean());
                assertEquals(text(entries.getFirst(), "streamId"), text(entries.getLast(), "streamId"));
                assertTrue(entries.getFirst().get("sequence").getAsLong() < entries.getLast().get("sequence").getAsLong());
            }
            assertEquals(List.of("WEBCHAT_JOINED:Alice", "message:hello"), f.gameEvents.get(bob));
        }
    }

    @Test void presenceMessagesFollowFirstAndLastConnectionsAcrossTabsAndLogins() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member");
            Client observer = f.join(bob, "b"); f.clearFrames();
            Client first = f.join(alice, "a"), second = f.join(alice, "a"), otherLogin = f.join(alice, "c");
            var renewed = f.module.authorize(f.grant(first, alice, "a")); f.flush();
            assertEquals(200, renewed.join().status());
            List<JsonObject> joined = observer.serverMessages();
            assertEquals(1, joined.size());
            assertEquals("webJoined", text(joined.getFirst().getAsJsonObject("content"), "template"));
            f.disconnect(first); f.disconnect(second);
            assertTrue(observer.serverMessages().isEmpty());
            f.disconnect(otherLogin);
            Client reconnected = f.join(alice, "d");
            f.send(reconnected, "public", null, UUID.randomUUID().toString(), "back");
            List<JsonObject> entries = observer.entries();
            assertEquals(List.of("server", "server", "user"), entries.stream().map(entry -> text(entry, "kind")).toList());
            assertEquals("webLeft", text(entries.get(0).getAsJsonObject("content"), "template"));
            assertEquals("webJoined", text(entries.get(1).getAsJsonObject("content"), "template"));
        }
    }

    @Test void immediateServerMessagesRespectCurrentGameVisibility() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID hidden = f.account("Hidden", "member"), gameViewer = f.account("Game", "member"), webViewer = f.account("Web", "member");
            f.game(hidden); f.game(gameViewer);
            Client observer = f.join(webViewer, "b"); f.clearFrames();
            f.concealed.add(hidden);
            Client subject = f.join(hidden, "a");
            assertTrue(subject.serverMessages().stream().anyMatch(entry -> text(entry.getAsJsonObject("subject"), "name").equals("Hidden")));
            f.disconnect(subject);
            assertTrue(observer.serverMessages().isEmpty());
            assertNull(f.gameEvents.get(gameViewer));
        }
    }

    @Test void perViewerVisibilityAlsoProtectsWebPeersWhenTheSubjectIsVisibleByDefault() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID hidden = f.account("Hidden", "member"), gameViewer = f.account("Game", "member"), webViewer = f.account("Web", "member");
            f.game(hidden); f.game(gameViewer);
            f.hiddenFromViewers.add(hidden);
            assertTrue(f.gamePlayers.get(hidden).isVisibleByDefault());
            f.join(webViewer, "w");
            assertFalse(f.module.canSee(webViewer, f.gamePlayers.get(hidden)));
            f.maintenance();
            assertNull(f.module.peer(webViewer, hidden));
            f.hiddenFromViewers.remove(hidden);
            f.maintenance();
            assertNotNull(f.module.peer(webViewer, hidden));
        }
    }

    @Test void genericServerOutputUsesPublicStaffAndPrivateConversationRules() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member"), staff = f.account("Staff", "moderator");
            Client a = f.join(alice, "a"), b = f.join(bob, "b"), moderator = f.join(staff, "s"); f.clearFrames();
            var body = new ChatServerMessage.RichText(ChatContent.plain("Maintenance at 18:00"));
            f.module.publish(ChatServerMessage.publicMessage(body, null, true));
            for (Client client : List.of(a, b, moderator)) {
                var entry = client.serverMessages().getFirst();
                assertTrue(entry.get("countsAsUnread").getAsBoolean());
                assertFalse(entry.has("subject"));
                assertEquals("Maintenance at 18:00", text(entry.getAsJsonArray("content").get(0).getAsJsonObject(), "text"));
            }
            f.module.publish(new ChatServerMessage(ChatMessageId.random(), ChatConversation.minecraftStaff(), body,
                    Optional.empty(), Map.of(), true, Instant.now()));
            assertTrue(a.serverMessages().isEmpty()); assertTrue(b.serverMessages().isEmpty());
            assertEquals("staff", text(moderator.serverMessages().getFirst(), "channel"));

            f.module.publish(new ChatServerMessage(ChatMessageId.random(), ChatConversation.minecraftPrivate(alice, bob), body,
                    Optional.empty(), Map.of(alice, "Alice", bob, "Bob"), false, Instant.now()));
            var forAlice = a.serverMessages().getFirst();
            var forBob = b.serverMessages().getFirst();
            assertEquals(alice.toString(), text(forAlice, "recipientUuid"));
            assertEquals(bob.toString(), text(forAlice, "targetUuid"));
            assertEquals(bob.toString(), text(forBob, "recipientUuid"));
            assertEquals(alice.toString(), text(forBob, "targetUuid"));
            assertTrue(moderator.serverMessages().isEmpty());

            f.module.publish(ChatServerMessage.publicMessage(body, null, false), viewer -> viewer.equals(alice));
            assertEquals(1, a.serverMessages().size());
            assertTrue(b.serverMessages().isEmpty()); assertTrue(moderator.serverMessages().isEmpty());

            f.game(alice); f.concealed.add(alice);
            f.module.publish(new ChatServerMessage(ChatMessageId.random(), ChatConversation.minecraftPrivate(alice, bob), body,
                    Optional.empty(), Map.of(alice, "Alice", bob, "Bob"), false, Instant.now()));
            assertTrue(b.serverMessages().isEmpty(), "A private server message must not reveal a hidden peer");
            assertEquals(1, a.serverMessages().size());
        }
    }

    @Test void aServerSubjectIsIdentityMetadataRatherThanAnOnlineRequirement() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID viewer = f.account("Viewer", "member"), offline = f.account("Offline", "member");
            Client authorized = f.join(viewer, "v"), pending = f.open(); f.clearFrames();
            var body = new ChatServerMessage.RichText(ChatContent.plain("Updated the server rules"));
            f.module.publish(ChatServerMessage.publicMessage(body, new ChatServerMessage.Subject(offline, "Offline"), true));
            assertEquals(offline.toString(), text(authorized.serverMessages().getFirst().getAsJsonObject("subject"), "playerUuid"));
            assertTrue(pending.entries().isEmpty());
        }
    }

    @Test void gameConnectionEventsAreIndependentOfWebConnectionsAndRetainQuitVisibility() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member");
            Client a = f.join(alice, "a"), b = f.join(bob, "b"); f.clearFrames();
            f.game(alice);
            Player subject = f.gamePlayers.get(alice);
            var listener = f.gamePresence();
            listener.onJoin(new PlayerJoinEvent(subject, Component.empty()));
            assertEquals("gameJoined", text(b.serverMessages().getFirst().getAsJsonObject("content"), "template"));
            f.gamePlayers.remove(alice);
            listener.onQuit(new PlayerQuitEvent(subject, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
            assertEquals("gameLeft", text(b.serverMessages().getFirst().getAsJsonObject("content"), "template"));
            assertEquals(1, f.module.peer(bob, alice).webConnections());
            a.events();

            f.game(alice); subject = f.gamePlayers.get(alice);
            listener.onJoin(new PlayerJoinEvent(subject, Component.empty()));
            b.events(); a.events();
            f.concealed.add(alice); f.gamePlayers.remove(alice);
            listener.onQuit(new PlayerQuitEvent(subject, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
            assertTrue(b.serverMessages().isEmpty(), "A departed hidden subject must not reappear after its Bukkit lookup is gone");
        }
    }

    @Test void browsersCannotPublishOrSpoofServerOutput() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member");
            Client a = f.join(alice, "a"), b = f.join(bob, "b"); f.clearFrames();
            JsonObject body = new JsonObject();
            body.addProperty("type", "server"); body.addProperty("requestId", UUID.randomUUID().toString());
            body.addProperty("channel", "public"); body.addProperty("text", "fake");
            f.module.message(a.channel, body.toString()); f.flush();
            assertEquals("invalid_message", text(a.events().getFirst(), "error"));
            body.addProperty("type", "send"); body.addProperty("kind", "server");
            f.module.message(a.channel, body.toString()); f.flush();
            assertEquals("invalid_message", text(a.events().getFirst(), "error"));
            assertTrue(b.entries().isEmpty());
            assertTrue(f.gameDeliveries.isEmpty());
        }
    }

    @Test void rejectedParsingDoesNotExtendLoginActivity() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member");
            Client a = f.join(alice, "a"), b = f.join(bob, "b"); f.clearFrames();
            f.send(a, "public", null, UUID.randomUUID().toString(), "https://github.com/encinet/%zz");
            assertTrue(b.messages().isEmpty());
            assertTrue(a.events().stream().anyMatch(event -> text(event, "error").equals("invalid_message")));
            var renewal = f.module.authorize(f.grant(a, alice, "a")); f.flush();
            assertEquals(0, renewal.join().lastActivityAt());
        }
    }

    @Test void webMentionsUseVisibleChannelParticipantsAndDoNotGrantGameOnlyCapabilities() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member"), other = f.account("Other", "member");
            f.game(alice);
            Client a = f.join(alice, "a"), b = f.join(bob, "b"), o = f.join(other, "c"); f.clearFrames();
            f.send(a, "public", null, UUID.randomUUID().toString(), "<red>@Bob @all %i github.com/encinet/mik");
            JsonObject sent = a.messages().getFirst(), received = b.messages().getFirst();
            assertFalse(sent.get("mentioned").getAsBoolean());
            assertTrue(received.get("mentioned").getAsBoolean());
            assertFalse(o.messages().getFirst().get("mentioned").getAsBoolean());
            assertTrue(received.getAsJsonArray("content").asList().stream().anyMatch(part -> part.getAsJsonObject().has("href")));
            ChatMessage accepted = f.gameDeliveries.getFirst().getValue().message();
            assertEquals(new ChatOrigin.Web(alice), accepted.submission().origin());
            assertEquals(alice, accepted.submission().sender().minecraftId().orElseThrow());
            assertEquals(ChatConversation.minecraftPublic(), accepted.submission().conversation());
            assertEquals(Set.of(ChatCapability.URL, ChatCapability.PLAYER_MENTION), accepted.submission().processingContext().capabilities());
            ChatContent game = accepted.content();
            assertTrue(game.plainText().contains("<red>")); assertTrue(game.plainText().contains("@all %i"));
            assertEquals(1, game.nodes().stream().filter(ChatNode.PlayerMention.class::isInstance).count());
            assertEquals(1, game.nodes().stream().filter(ChatNode.Link.class::isInstance).count());
        }
    }

    @Test void privateAndStaffMentionsCannotTargetOtherChannelParticipants() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "moderator"), bob = f.account("Bob", "moderator"), outsider = f.account("Other", "member");
            Client a = f.join(alice, "a"), b = f.join(bob, "b"), o = f.join(outsider, "c"); f.clearFrames();
            f.send(a, "staff", null, UUID.randomUUID().toString(), "@Bob @Other");
            assertTrue(b.messages().getFirst().get("mentioned").getAsBoolean());
            assertEquals(1, a.messages().getFirst().getAsJsonArray("content").asList().stream()
                    .filter(part -> part.getAsJsonObject().has("mention")).count());
            assertTrue(o.messages().isEmpty());
            f.send(a, "private", bob, UUID.randomUUID().toString(), "@Bob @Other");
            assertTrue(b.messages().getFirst().get("mentioned").getAsBoolean());
            assertTrue(o.messages().isEmpty());
            assertEquals(Set.of(alice, bob), f.module.channelPeers(alice, ChatChannel.PRIVATE, bob).stream()
                    .map(ChatParticipant::playerUuid).collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test void browsersCannotSupplyIdentityPermissionsOrParsedContent() throws Exception {
        for (String field : List.of("senderUuid", "role", "staff", "sessionRef", "expiresAt", "content", "mentioned", "origin", "processingContext", "prefix", "suffix")) {
            try (Fixture f = new Fixture()) {
                UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member");
                Client a = f.join(alice, "a"), b = f.join(bob, "b"); f.clearFrames();
                JsonObject body = new JsonObject(); body.addProperty("type", "send");
                body.addProperty("requestId", UUID.randomUUID().toString()); body.addProperty("channel", "public");
                body.addProperty("text", "ordinary text"); body.addProperty(field, "custodian");
                f.module.message(a.channel, body.toString()); f.flush();
                assertTrue(b.messages().isEmpty(), field);
                assertTrue(a.events().stream().anyMatch(event -> text(event, "error").equals("invalid_message")), field);
            }
        }
    }

    @Test void directSocketRequestsCannotSkipLoginOrEnterStaffAsAMember() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID member = f.account("Member", "member"), moderator = f.account("Staff", "moderator");
            Client m = f.join(member, "m"), s = f.join(moderator, "s"), anonymous = f.open(); f.clearFrames();
            f.send(anonymous, "public", null, UUID.randomUUID().toString(), "no login");
            assertFalse(anonymous.channel.isActive());
            assertTrue(m.messages().isEmpty()); assertTrue(s.messages().isEmpty());
            f.send(m, "staff", null, UUID.randomUUID().toString(), "bypass channel controls");
            assertTrue(s.messages().isEmpty());
            assertTrue(m.events().stream().anyMatch(event -> text(event, "error").equals("staff_required")));
        }
    }

    @Test void banChangesStopDistributionWithoutWaitingForThePolicyPoll() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member");
            Client a = f.join(alice, "a"), b = f.join(bob, "b"); f.clearFrames();
            f.banned.add(bob);
            f.send(a, "private", bob, UUID.randomUUID().toString(), "blocked");
            assertTrue(b.messages().isEmpty());
            assertTrue(a.events().stream().anyMatch(event -> text(event, "error").equals("recipient_unavailable")));
        }
    }

    @Test void privateMessagesFanOutToAllOwnTabsAndThePeerWithoutLeakingToOtherStaff() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member"), staff = f.account("Staff", "moderator");
            Client a1 = f.join(alice, "a"), a2 = f.join(alice, "a"), a3 = f.join(alice, "b");
            Client b = f.join(bob, "c"), outsider = f.join(staff, "d");
            f.clearFrames();
            String request = UUID.randomUUID().toString();
            f.send(a1, "private", bob, request, "hello");
            List<JsonObject> first = a1.messages(), second = a2.messages(), third = a3.messages(), target = b.messages();
            assertEquals(1, first.size()); assertEquals(1, second.size()); assertEquals(1, third.size()); assertEquals(1, target.size());
            assertEquals(first.getFirst().get("id"), target.getFirst().get("id"));
            assertEquals(first.getFirst().get("id"), third.getFirst().get("id"));
            assertFalse(target.getFirst().has("requestId"));
            assertTrue(outsider.messages().isEmpty());
            assertEquals(3, f.module.peers(bob).stream().filter(peer -> peer.playerUuid().equals(alice)).findFirst().orElseThrow().webConnections());
            f.send(a2, "private", bob, request, "hello");
            assertTrue(b.messages().isEmpty(), "replaying a request from another tab must not redistribute it");
            f.module.closed(b.channel); b.channel.close(); f.flush();
            f.send(a1, "private", bob, UUID.randomUUID().toString(), "offline");
            assertTrue(a1.events().stream().anyMatch(event -> "recipient_unavailable".equals(text(event, "error"))));
        }
    }

    @Test void revocationClosesOnlyItsLoginAndALateAuthorizationCannotRestoreIt() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member");
            Client a1 = f.join(alice, "a"), a2 = f.join(alice, "a"), otherLogin = f.join(alice, "b");
            Client pending = f.open();
            f.module.revoke(List.of("a".repeat(43))); f.flush();
            assertFalse(a1.channel.isActive()); assertFalse(a2.channel.isActive()); assertTrue(otherLogin.channel.isActive());
            CompletableFuture<WebChatModule.ControlResult> late = f.module.authorize(f.grant(pending, alice, "a")); f.flush();
            assertEquals(403, late.join().status());
            assertTrue(pending.events().stream().noneMatch(event -> text(event, "type").equals("authorized")));
            assertEquals(1, f.module.peer(alice, alice).webConnections());
        }
    }

    @Test void permissionChangesSuspendDistributionBeforeTheQueuedPolicyRefresh() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "moderator"), sender = f.account("Staff", "moderator"), member = f.account("Member", "member");
            Client a = f.join(alice, "a"), s = f.join(sender, "b"), m = f.join(member, "c");
            f.clearFrames();
            f.module.invalidatePolicy(alice);
            f.send(s, "staff", null, UUID.randomUUID().toString(), "secret");
            assertTrue(a.messages().isEmpty()); assertTrue(m.messages().isEmpty()); assertEquals(1, s.messages().size());
            f.roles.put(alice, "member");
            var renewed = f.module.authorize(f.grant(a, alice, "a")); f.flush();
            assertEquals(200, renewed.join().status());
            f.clearFrames();
            f.send(s, "staff", null, UUID.randomUUID().toString(), "still secret");
            assertTrue(a.messages().isEmpty());
        }
    }

    @Test void gameAudienceSnapshotsUseGameRolesAndNeverInheritAWebRole() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "moderator"), bob = f.account("Bob", "moderator");
            f.game(alice); f.game(bob);
            f.gameStaffDenied.add(alice);
            f.join(alice, "a");
            f.maintenance();
            ChatAudience staff = new ChatAudience(ChatChannel.STAFF, Set.of());
            ChatAudience privateChat = new ChatAudience(ChatChannel.PRIVATE, Set.of(alice));
            assertEquals(Set.of(bob), f.module.gameRecipients(staff));
            assertEquals(Set.of(alice), f.module.gameRecipients(privateChat));
            f.primaryThread = false;
            assertEquals(Set.of(bob), f.module.gameRecipients(staff));
            assertEquals(Set.of(alice), f.module.gameRecipients(privateChat));
            assertEquals(Set.of(alice, bob), f.module.gameRecipients(new ChatAudience(ChatChannel.PUBLIC, Set.of())));
        }
    }

    @Test void gameAndWebRecipientsShareOneAccountAndRestrictedGamePublicChatIsNotBroadcast() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member");
            f.game(alice); f.game(bob);
            Client a = f.join(alice, "a"), b = f.join(bob, "b"); f.clearFrames();
            f.send(a, "private", bob, UUID.randomUUID().toString(), "both");
            assertEquals(2, f.gameDeliveries.size());
            assertEquals(Set.of(alice, bob), f.gameDeliveries.stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
            assertEquals(1, b.messages().size());
            f.clearFrames();
            ChatMessage message = new ChatProcessor().process(new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Minecraft(alice),
                    ChatSender.minecraft(alice, "Alice"), ChatConversation.minecraftPublic(), "restricted", ChatReferences.empty(),
                    Instant.now(), ChatProcessingContext.playerText(PlayerMentions.empty())));
            assertFalse(Set.of(alice).containsAll(f.module.gameRecipients(ChatAudience.of(message, null))));
            assertTrue(Set.of(alice, bob).containsAll(f.module.gameRecipients(ChatAudience.of(message, null))));
            f.relay(message, null, Set.of(alice));
            assertTrue(a.messages().isEmpty()); assertTrue(b.messages().isEmpty());
        }
    }

    @Test void gameViewerRestrictionsAlsoApplyToStaffAndPrivateRelays() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "moderator"), bob = f.account("Bob", "moderator"), other = f.account("Other", "member");
            f.game(alice); f.game(bob);
            Client a = f.join(alice, "a"), b = f.join(bob, "b"), o = f.join(other, "c");
            for (ChatChannel channel : List.of(ChatChannel.STAFF, ChatChannel.PRIVATE)) {
                f.clearFrames();
                ChatConversation conversation = channel == ChatChannel.STAFF ? ChatConversation.minecraftStaff()
                        : ChatConversation.minecraftPrivate(alice, bob);
                ChatMessage message = new ChatProcessor().process(new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Minecraft(alice),
                        ChatSender.minecraft(alice, "Alice"), conversation, "secret", ChatReferences.empty(), Instant.now(),
                        ChatProcessingContext.playerText(PlayerMentions.empty())));
                UUID target = channel == ChatChannel.PRIVATE ? bob : null;
                f.relay(message, target, Set.of(alice));
                assertTrue(a.messages().isEmpty()); assertTrue(b.messages().isEmpty()); assertTrue(o.messages().isEmpty());
                f.relay(message, target, Set.of(alice, bob));
                assertEquals(1, a.messages().size()); assertEquals(1, b.messages().size()); assertTrue(o.messages().isEmpty());
            }
        }
    }

    @Test void privateGameMessagesReachWebOnlyTargetsWithoutInventingGameViewers() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member");
            f.game(alice);
            Client a = f.join(alice, "a"), b = f.join(bob, "b"); f.clearFrames();
            ChatMessage message = new ChatProcessor().process(new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Minecraft(alice),
                    ChatSender.minecraft(alice, "Alice"), ChatConversation.minecraftPrivate(alice, bob), "private", ChatReferences.empty(),
                    Instant.now(), ChatProcessingContext.playerText(PlayerMentions.empty())));
            f.relay(message, bob, Set.of(alice));
            assertEquals(1, a.messages().size()); assertEquals(1, b.messages().size());
        }
    }

    @Test void newConnectionsCannotEvictExistingTabsWhenTheAccountOrServerIsFull() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice", "member"), bob = f.account("Bob", "member");
            set(f.module, "maxPerPlayer", 2);
            Client first = f.join(alice, "a"), second = f.join(alice, "a"), excess = f.open();
            var result = f.module.authorize(f.grant(excess, alice, "a")); f.flush();
            assertEquals(429, result.join().status()); assertTrue(first.channel.isActive()); assertTrue(second.channel.isActive());
            set(f.module, "maxConnections", 2);
            Client full = f.open(); var global = f.module.authorize(f.grant(full, bob, "b")); f.flush();
            assertEquals("chat_capacity", global.join().error());
        }
    }

    private static String text(JsonObject object, String field) { return object.has(field) ? object.get(field).getAsString() : ""; }
    private static void set(Object object, String field, Object value) throws Exception {
        Field target = object.getClass().getDeclaredField(field); target.setAccessible(true); target.set(object, value);
    }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class Client {
        final EmbeddedChannel channel = new EmbeddedChannel();
        final String publicKey = WebChatEncryption.generate().publicKey();
        String id;
        List<JsonObject> events() {
            List<JsonObject> output = new ArrayList<>(); Object frame;
            while ((frame = channel.readOutbound()) != null) {
                if (frame instanceof TextWebSocketFrame text) output.add(JsonParser.parseString(text.text()).getAsJsonObject());
                ReferenceCountUtil.release(frame);
            }
            return output;
        }
        List<JsonObject> entries() { return events().stream().filter(event -> text(event, "type").equals("message")).map(event -> event.getAsJsonObject("message")).toList(); }
        List<JsonObject> messages() { return entries().stream().filter(entry -> text(entry, "kind").equals("user")).toList(); }
        List<JsonObject> serverMessages() { return entries().stream().filter(entry -> text(entry, "kind").equals("server")).toList(); }
    }

    private static final class Fixture implements AutoCloseable {
        final Map<UUID, String> names = new HashMap<>(), roles = new HashMap<>();
        final Map<UUID, Player> gamePlayers = new HashMap<>();
        final Set<UUID> banned = new HashSet<>();
        final Set<UUID> gameStaffDenied = new HashSet<>();
        final Set<UUID> concealed = new HashSet<>();
        final Set<UUID> hiddenFromViewers = new HashSet<>();
        final List<Client> clients = new ArrayList<>();
        final List<Map.Entry<UUID, WebChatMessage>> gameDeliveries = new ArrayList<>();
        final Map<UUID, List<String>> gameEvents = new HashMap<>();
        final Field serverField;
        final Object previousServer;
        final WebChatModule module;
        final ChatService chat;
        final Plugin plugin;
        final long authenticatedAt = System.currentTimeMillis() - 1000;
        boolean primaryThread = true;
        Fixture() throws Exception {
            Server server = proxy(Server.class, (_, method, args) -> switch (method.getName()) {
                case "isPrimaryThread" -> primaryThread;
                case "getOnlinePlayers" -> gamePlayers.values();
                case "getPlayer" -> gamePlayers.get(args[0]);
                case "getOfflinePlayer" -> proxy(OfflinePlayer.class, (_, call, _) -> switch (call.getName()) {
                    case "getName" -> names.get(args[0]);
                    default -> throw new AssertionError(call.getName());
                });
                default -> throw new AssertionError(method.getName());
            });
            serverField = Bukkit.class.getDeclaredField("server"); serverField.setAccessible(true);
            previousServer = serverField.get(null); serverField.set(null, server);
            plugin = proxy(Plugin.class, (_, method, _) -> {
                if (method.getName().equals("getLogger")) return Logger.getAnonymousLogger();
                throw new AssertionError(method.getName());
            });
            LanguageService language = new LanguageService(null) {
                @Override public String t(Player player, Message message, Object... args) { return message.name() + ":" + args[0]; }
            };
            java.util.function.BiConsumer<ChatDelivery, Set<UUID>> gameOutput = (delivery, ids) -> {
                        if (delivery.game() instanceof ChatDelivery.Native) return;
                        WebChatMessage message = new WebChatMessage(delivery.message(), delivery.requestId(), delivery.target());
                        ids.forEach(id -> {
                            gameDeliveries.add(Map.entry(id, message));
                            gameEvents.computeIfAbsent(id, _ -> new ArrayList<>()).add("message:" + message.content().plainText());
                        });
                    };
            chat = new ChatService(java.util.function.UnaryOperator.identity(), Runnable::run,
                    _ -> SocialChatPublishReport.empty(), (_, error) -> { throw new AssertionError(error); });
            module = new WebChatModule(plugin, language, _ -> () -> true,
                    id -> new WebAccountPolicy(roles.getOrDefault(id, "default"), 0), _ -> {}, (id, _) -> !banned.contains(id), _ -> {},
                    chat::process, chat::publish, new ChatSendLedger());
            chat.connect(module, gameOutput, module);
            set(module, "running", true); set(module, "maxPerPlayer", 8); set(module, "maxConnections", 32);
        }
        UUID account(String name, String role) { UUID id = UUID.randomUUID(); names.put(id, name); roles.put(id, role); return id; }
        void game(UUID id) {
            gamePlayers.put(id, proxy(Player.class, (object, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> names.get(id);
                case "isOnline" -> true;
                case "canSee" -> !concealed.contains(((Player) args[0]).getUniqueId())
                        && !hiddenFromViewers.contains(((Player) args[0]).getUniqueId());
                case "isVisibleByDefault" -> !concealed.contains(id);
                case "sendMessage" -> {
                    gameEvents.computeIfAbsent(id, _ -> new ArrayList<>()).add(PlainTextComponentSerializer.plainText().serialize((Component) args[0]));
                    yield null;
                }
                case "hasPermission" -> !gameStaffDenied.contains(id) && args[0].equals("group." + roles.get(id));
                case "equals" -> object == args[0];
                case "hashCode" -> System.identityHashCode(object);
                default -> throw new AssertionError(method.getName());
            }));
        }
        GameChatPresenceListener gamePresence() {
            return new GameChatPresenceListener(plugin, module, player -> {
                var peer = module.peer(player.getUniqueId(), player.getUniqueId());
                return peer != null && peer.game();
            }, module::canSee);
        }
        Client open() throws Exception {
            Client client = new Client(); clients.add(client); module.opened(client.channel, "127.0.0.1"); flush();
            client.id = client.events().getFirst().get("connectionId").getAsString(); return client;
        }
        JsonObject grant(Client client, UUID player, String ref) {
            JsonObject grant = new JsonObject(); grant.addProperty("connectionId", client.id); grant.addProperty("playerUuid", player.toString());
            grant.addProperty("clientPublicKey", client.publicKey);
            grant.addProperty("playerName", names.get(player)); grant.addProperty("sessionRef", ref.repeat(43));
            grant.addProperty("authenticatedAt", authenticatedAt); long now = System.currentTimeMillis();
            grant.addProperty("checkedAt", now); grant.addProperty("expiresAt", now + 60_000); return grant;
        }
        Client join(UUID player, String ref) throws Exception {
            Client client = open(); var result = module.authorize(grant(client, player, ref)); flush();
            assertEquals(200, result.join().status());
            client.channel.runPendingTasks(); module.ready(client.channel); flush(); return client;
        }
        void disconnect(Client client) throws Exception { client.channel.close(); module.closed(client.channel); flush(); }
        void send(Client client, String channel, UUID target, String request, String text) throws Exception {
            JsonObject body = new JsonObject(); body.addProperty("type", "send"); body.addProperty("requestId", request); body.addProperty("channel", channel); body.addProperty("text", text);
            if (target != null) body.addProperty("targetUuid", target.toString()); module.message(client.channel, body.toString()); flush();
        }
        void flush() throws Exception {
            set(module, "lastMaintenance", System.currentTimeMillis());
            tick();
        }
        void maintenance() throws Exception {
            set(module, "lastMaintenance", 0L);
            tick();
        }
        private void tick() throws Exception {
            Method tick = WebChatModule.class.getDeclaredMethod("tick"); tick.setAccessible(true); tick.invoke(module);
        }
        void relay(ChatMessage message, UUID target, Set<UUID> viewers) {
            chat.publish(ChatDelivery.nativeGame(message, target == null ? null : ChatSender.minecraft(target, names.get(target)), viewers,
                    module.gameRecipients(ChatAudience.of(message, target == null ? null : ChatSender.minecraft(target, names.get(target))))));
        }
        void clearFrames() { clients.forEach(Client::events); gameDeliveries.clear(); gameEvents.clear(); }
        @Override public void close() throws IllegalAccessException {
            try { module.disable(); clients.forEach(client -> client.channel.finishAndReleaseAll()); }
            finally { serverField.set(null, previousServer); }
        }
    }
}
