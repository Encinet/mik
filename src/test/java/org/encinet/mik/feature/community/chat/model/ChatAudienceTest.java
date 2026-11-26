package org.encinet.mik.feature.community.chat.model;

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

    @Test void aPrivateMessageCarriesItsSingleDestinationAndCannotTargetItsAuthor() {
        var alice = ChatSender.minecraft(ALICE, "Alice");
        var bob = ChatSender.minecraft(BOB, "Bob");
        var id = ChatMessageId.random();
        var content = ChatContent.plain("private");
        var message = new ChatMessage(id, ChatOrigin.Player.WEB, alice, ChatChannel.PRIVATE, bob, Instant.EPOCH, content);
        assertEquals(Set.of(ALICE, BOB), message.audience().participants());
        assertEquals(BOB, message.targetId());
        assertThrows(IllegalArgumentException.class,
                () -> new ChatMessage(id, ChatOrigin.Player.WEB, alice, ChatChannel.PRIVATE, alice, Instant.EPOCH, content));
        assertThrows(IllegalArgumentException.class,
                () -> new ChatMessage(id, ChatOrigin.Player.WEB, alice, ChatChannel.PUBLIC, bob, Instant.EPOCH, content));
    }
}
