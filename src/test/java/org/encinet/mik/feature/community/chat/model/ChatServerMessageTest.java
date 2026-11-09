package org.encinet.mik.feature.community.chat.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatServerMessageTest {
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), other = UUID.randomUUID();
    private final ChatServerMessage.Body content = new ChatServerMessage.RichText(ChatContent.plain("Server output"));

    @Test void privateAudienceMustExactlyMatchTheCanonicalConversation() {
        var conversation = ChatConversation.minecraftPrivate(alice, bob);
        assertThrows(IllegalArgumentException.class, () -> message(conversation, Map.of(alice, "Alice")));
        assertThrows(IllegalArgumentException.class, () -> message(conversation, Map.of(alice, "Alice", other, "Other")));
        assertThrows(IllegalArgumentException.class, () -> message(ChatConversation.minecraftPublic(), Map.of(alice, "Alice", bob, "Bob")));
        assertEquals(conversation, message(conversation, Map.of(alice, "Alice", bob, "Bob")).conversation());
    }

    @Test void participantIdentitiesAreNormalizedAndCopiedBeforePublication() {
        Map<UUID, String> participants = new HashMap<>(Map.of(alice, " Alice ", bob, "Bob"));
        var message = message(ChatConversation.minecraftPrivate(alice, bob), participants);
        participants.put(alice, "Changed");
        assertEquals("Alice", message.participants().get(alice));
        assertThrows(UnsupportedOperationException.class, () -> message.participants().put(other, "Other"));
        assertThrows(IllegalArgumentException.class, () -> new ChatServerMessage.Subject(alice, "Alice\nAdmin"));
    }

    @Test void translationsCannotCarryUnexpectedArgumentsAndRichOutputCannotBeEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new ChatServerMessage.Translation(
                ChatServerMessage.Template.WEB_JOINED, Map.of("name", "Alice", "secret", "internal")));
        assertThrows(IllegalArgumentException.class, () -> new ChatServerMessage.Translation(
                ChatServerMessage.Template.GAME_LEFT, Map.of("name", "")));
        assertThrows(IllegalArgumentException.class, () -> new ChatServerMessage.RichText(ChatContent.plain(" ")));
        var notice = ChatServerMessage.publicMessage(content, null, false);
        assertTrue(notice.subject().isEmpty());
        assertTrue(notice.participants().isEmpty());
        assertFalse(notice.countsAsUnread());
    }

    private ChatServerMessage message(ChatConversation conversation, Map<UUID, String> participants) {
        return new ChatServerMessage(ChatMessageId.random(), conversation, content, Optional.empty(), participants, false, Instant.EPOCH);
    }
}
