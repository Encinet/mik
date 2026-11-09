package org.encinet.mik.feature.community.chat.runtime;

import org.encinet.mik.feature.community.chat.bridge.*;
import org.encinet.mik.feature.community.chat.model.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {
    private static final UUID ALICE = UUID.randomUUID(), BOB = UUID.randomUUID();
    private static ChatParticipants participants(Set<UUID> recipients) {
        return (ChatParticipants) Proxy.newProxyInstance(ChatParticipants.class.getClassLoader(), new Class<?>[]{ChatParticipants.class}, (_, method, _) -> {
            if (method.getName().equals("gameRecipients")) return recipients;
            throw new AssertionError(method.getName());
        });
    }
    private static ChatSubmission social(Optional<UUID> binding) {
        return new ChatSubmission(ChatMessageId.external("matrix", "room", "event"),
                new ChatOrigin.Social("matrix", "Matrix", "room", "event"),
                new ChatSender("Guest", binding, Optional.empty(), binding.isPresent() ? "Alice" : "", "", "", 0xFFFFFF),
                ChatConversation.minecraftPublic(), "hello", ChatReferences.empty(), Instant.now(), ChatProcessingContext.external());
    }
    @Test void unboundAndOfflineBoundSocialAuthorsReachEveryOutputWithoutPlayerEnrichment() {
        List<ChatDelivery> game = new ArrayList<>(), web = new ArrayList<>();
        List<ChatMessage> social = new ArrayList<>();
        ChatService service = new ChatService(_ -> { throw new AssertionError("Social identity is already resolved"); }, Runnable::run,
                message -> { social.add(message); return SocialChatPublishReport.empty(); }, (_, error) -> fail(error));
        service.connect(participants(Set.of(BOB)),
                (delivery, recipients) -> { assertEquals(Set.of(BOB), recipients); game.add(delivery); }, web::add);
        service.accept(social(Optional.empty()));
        service.accept(social(Optional.of(ALICE)));
        assertEquals(2, game.size()); assertEquals(2, web.size()); assertEquals(2, social.size());
        assertTrue(web.getFirst().message().submission().sender().minecraftId().isEmpty());
        assertEquals(Optional.of(ALICE), web.getLast().message().submission().sender().minecraftId());
    }
    @Test void restrictedNativeAudienceNeverExpandsButACompleteAudienceRelays() {
        List<ChatDelivery> web = new ArrayList<>(); List<ChatMessage> social = new ArrayList<>();
        ChatService service = new ChatService(java.util.function.UnaryOperator.identity(), Runnable::run,
                message -> { social.add(message); return SocialChatPublishReport.empty(); }, (_, error) -> fail(error));
        service.connect(participants(Set.of(ALICE, BOB)), (_, _) -> {}, web::add);
        ChatMessage message = service.process(new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Minecraft(ALICE),
                ChatSender.minecraft(ALICE, "Alice"), ChatConversation.minecraftPublic(), "hello", ChatReferences.empty(), Instant.now(), ChatProcessingContext.external()));
        service.publish(ChatDelivery.nativeGame(message, null, Set.of(ALICE), Set.of(ALICE, BOB)));
        assertTrue(web.isEmpty()); assertTrue(social.isEmpty());
        service.publish(ChatDelivery.nativeGame(message, null, Set.of(ALICE, BOB), Set.of(ALICE, BOB)));
        assertEquals(1, web.size()); assertEquals(1, social.size());
    }
    @Test void aGameSenderLosingAdmissionCannotRelayUsingItsEarlierSnapshot() {
        List<ChatDelivery> web = new ArrayList<>(); List<ChatMessage> social = new ArrayList<>();
        ChatService service = new ChatService(java.util.function.UnaryOperator.identity(), Runnable::run,
                message -> { social.add(message); return SocialChatPublishReport.empty(); }, (_, error) -> fail(error));
        service.connect(participants(Set.of(BOB)), (_, _) -> {}, web::add);
        ChatMessage message = service.process(new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Minecraft(ALICE),
                ChatSender.minecraft(ALICE, "Alice"), ChatConversation.minecraftPublic(), "revoked", ChatReferences.empty(), Instant.now(), ChatProcessingContext.external()));
        service.publish(ChatDelivery.nativeGame(message, null, Set.of(ALICE, BOB), Set.of(ALICE, BOB)));
        assertTrue(web.isEmpty()); assertTrue(social.isEmpty());
    }

    @Test void aRecipientLeavingCannotTurnRestrictedNativeChatIntoABroadcast() {
        List<ChatDelivery> web = new ArrayList<>();
        ChatService service = new ChatService(java.util.function.UnaryOperator.identity(), Runnable::run,
                _ -> SocialChatPublishReport.empty(), (_, error) -> fail(error));
        service.connect(participants(Set.of(ALICE)), (_, _) -> {}, web::add);
        ChatMessage message = service.process(new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Minecraft(ALICE),
                ChatSender.minecraft(ALICE, "Alice"), ChatConversation.minecraftPublic(), "restricted", ChatReferences.empty(), Instant.now(), ChatProcessingContext.external()));
        service.publish(ChatDelivery.nativeGame(message, null, Set.of(ALICE), Set.of(ALICE, BOB)));
        assertTrue(web.isEmpty());
    }

    @Test void oneOutputFailureDoesNotPreventOtherOutputsOrChangeAcceptance() {
        List<String> failures = new ArrayList<>(); List<ChatMessage> social = new ArrayList<>();
        ChatService service = new ChatService(java.util.function.UnaryOperator.identity(), Runnable::run,
                message -> { social.add(message); return SocialChatPublishReport.empty(); }, (output, _) -> failures.add(output));
        service.connect(participants(Set.of()), (_, _) -> { throw new IllegalStateException("game failure"); },
                _ -> { throw new IllegalStateException("web failure"); });
        assertDoesNotThrow(() -> service.accept(social(Optional.empty())));
        assertEquals(List.of("game", "web"), failures); assertEquals(1, social.size());
    }
    @Test void shutdownDiscardsPreviouslyQueuedDeliveries() {
        List<Runnable> queued = new ArrayList<>(); List<ChatDelivery> web = new ArrayList<>();
        ChatService service = new ChatService(java.util.function.UnaryOperator.identity(), queued::add,
                _ -> SocialChatPublishReport.empty(), (_, error) -> fail(error));
        service.connect(participants(Set.of()), (_, _) -> {}, web::add);
        ChatMessage message = service.process(social(Optional.empty()));
        service.publish(ChatDelivery.managed(message, null, null));
        service.disconnect(); service.connect(participants(Set.of()), (_, _) -> {}, web::add); queued.forEach(Runnable::run); assertTrue(web.isEmpty());
    }
    @Test void invalidReconnectCannotPartiallyReplaceTheCurrentRouting() {
        List<String> outputs = new ArrayList<>();
        ChatService service = new ChatService(java.util.function.UnaryOperator.identity(), Runnable::run,
                _ -> SocialChatPublishReport.empty(), (_, error) -> fail(error));
        service.connect(participants(Set.of()), (_, _) -> outputs.add("game"), _ -> outputs.add("web"));
        assertThrows(NullPointerException.class, () -> service.connect(participants(Set.of()),
                (_, _) -> outputs.add("replacement"), null));
        service.accept(social(Optional.empty()));
        assertEquals(List.of("game", "web"), outputs);
    }
    @Test void disconnectedIngressDoesNotScheduleWorkOnAStoppedHost() {
        ChatService service = new ChatService(java.util.function.UnaryOperator.identity(),
                _ -> { throw new IllegalStateException("Host is stopped"); },
                _ -> SocialChatPublishReport.empty(), (_, error) -> fail(error));
        ChatSubmission submission = social(Optional.empty());
        ChatMessage message = service.process(submission);
        assertDoesNotThrow(() -> service.accept(submission));
        assertDoesNotThrow(() -> service.publish(ChatDelivery.managed(message, null, null)));
    }
    @Test void disablingDuringOneOutputStopsRemainingOutputsFromThatRouting() {
        List<String> outputs = new ArrayList<>();
        ChatService service = new ChatService(java.util.function.UnaryOperator.identity(), Runnable::run,
                _ -> { outputs.add("social"); return SocialChatPublishReport.empty(); }, (_, error) -> fail(error));
        service.connect(participants(Set.of()), (_, _) -> {
            outputs.add("game");
            service.disconnect();
            service.connect(participants(Set.of()), (_, _) -> outputs.add("new-game"), _ -> outputs.add("new-web"));
        }, _ -> outputs.add("old-web"));
        service.accept(social(Optional.empty()));
        assertEquals(List.of("game"), outputs);
        service.accept(social(Optional.empty()));
        assertEquals(List.of("game", "new-game", "new-web", "social"), outputs);
    }
    @Test void externalMessageIdsAreBoundedAndIncludeConversationScope() {
        ChatMessageId first = ChatMessageId.external("matrix", "room-one", "event".repeat(100));
        ChatMessageId second = ChatMessageId.external("matrix", "room-two", "event".repeat(100));
        assertNotEquals(first, second); assertTrue(first.value().length() <= 128);
        assertEquals(first, ChatMessageId.external("matrix", "room-one", "event".repeat(100)));
    }
}
