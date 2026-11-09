package org.encinet.mik.feature.community.chat.model;

import org.encinet.mik.feature.community.chat.pipeline.ChatProcessor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatAudienceTest {
    private static final UUID ALICE = UUID.randomUUID(), BOB = UUID.randomUUID(), OTHER = UUID.randomUUID();

    @Test void channelMembershipUsesExplicitRoleFactsAndPrivateEndpoints() {
        ChatAudience publicChat = new ChatAudience(ChatChannel.PUBLIC, Set.of());
        ChatAudience staffChat = new ChatAudience(ChatChannel.STAFF, Set.of());
        ChatAudience privateChat = new ChatAudience(ChatChannel.PRIVATE, Set.of(ALICE, BOB));
        assertTrue(publicChat.includes(OTHER, false));
        assertFalse(staffChat.includes(ALICE, false));
        assertTrue(staffChat.includes(ALICE, true));
        assertTrue(privateChat.includes(ALICE, false));
        assertTrue(privateChat.includes(BOB, false));
        assertFalse(privateChat.includes(OTHER, true), "Staff status must not grant access to a private conversation");
    }

    @Test void deliveryAndAudienceSharePrivateConversationValidation() {
        ChatMessage message = new ChatProcessor().process(new ChatSubmission(ChatMessageId.random(),
                new ChatOrigin.Web(ALICE), ChatSender.minecraft(ALICE, "Alice"),
                ChatConversation.minecraftPrivate(ALICE, BOB), "private", ChatReferences.empty(),
                Instant.now(), ChatProcessingContext.external()));
        ChatSender bob = ChatSender.minecraft(BOB, "Bob"), other = ChatSender.minecraft(OTHER, "Other");
        assertEquals(new ChatAudience(ChatChannel.PRIVATE, Set.of(ALICE, BOB)),
                ChatDelivery.managed(message, bob, null).audience());
        assertThrows(IllegalArgumentException.class, () -> ChatAudience.of(message, null));
        assertThrows(IllegalArgumentException.class, () -> ChatDelivery.managed(message, other, null));
        assertThrows(IllegalArgumentException.class, () -> new ChatAudience(ChatChannel.PRIVATE, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ChatAudience(ChatChannel.PUBLIC, Set.of(ALICE)));
    }
}
