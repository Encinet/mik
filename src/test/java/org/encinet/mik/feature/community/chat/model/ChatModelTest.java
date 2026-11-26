package org.encinet.mik.feature.community.chat.model;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatModelTest {

    @Test
    void originDoesNotReplaceOrReinterpretTheSenderIdentity() {
        var player = ChatSender.minecraft(UUID.randomUUID(), "Alice");
        var id = ChatMessageId.random();
        var content = ChatContent.plain("hello");
        for (var origin : ChatOrigin.Player.values()) {
            var message = new ChatMessage(id, origin, player, ChatChannel.PUBLIC, null, Instant.EPOCH, content);
            assertEquals(player, message.sender());
            assertEquals(origin, message.origin());
        }
        var account = new ExternalIdentity(new ExternalIdentityKey("matrix", "example.org", "", "alice"), "Alice");
        var external = new ChatSender("Alice", player.minecraftId(), Optional.of(account), "Alice");
        assertThrows(IllegalArgumentException.class,
                () -> new ChatMessage(id, ChatOrigin.Player.WEB, external, ChatChannel.PUBLIC, null, Instant.EPOCH, content));
        assertThrows(IllegalArgumentException.class,
                () -> new ChatMessage(id, new ChatOrigin.Social("qq"), external, ChatChannel.PUBLIC, null, Instant.EPOCH, content));
    }

    @Test
    void semanticContentMergesPlainRunsAndKeepsLinks() {
        ChatStyle white = ChatStyle.EMPTY.withColor(0xFFFFFF);
        ChatContent content = new ChatContent(List.of(
                new ChatNode.Text("hello ", white),
                new ChatNode.Text("world ", white),
                new ChatNode.Reference("[wiki]", white, new ChatNode.Reference.Link(
                        URI.create("https://zh.minecraft.wiki/w/钻石"), false))));

        assertEquals("hello world [wiki]", content.plainText());
        assertEquals(2, content.nodes().size());
        assertEquals("https://zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3",
                ((ChatNode.Reference.Link) ((ChatNode.Reference) content.nodes().getLast()).target()).uri().toString());
    }

    @Test
    void semanticContentEnforcesItsLengthLimit() {
        String complete = "x".repeat(16_000);
        assertEquals(complete, ChatContent.plain(complete).plainText());
        assertThrows(IllegalArgumentException.class, () -> ChatContent.plain(complete + "x"));
    }

    @Test
    void linksRejectUnsafeSchemesAndCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new ChatNode.Reference.Link(URI.create("javascript:alert(1)"), false));
        assertThrows(IllegalArgumentException.class, () -> new ChatNode.Reference.Link(URI.create("https://user:secret@example.org"), false));
        assertThrows(IllegalArgumentException.class, () -> new ChatNode.Reference.Link(URI.create("mailto:a@example.org?subject=secret"), false));
        assertThrows(IllegalArgumentException.class, () -> new ChatNode.Reference.Link(URI.create("mailto:a@example.org,b@example.org"), false));
        assertThrows(IllegalArgumentException.class, () -> new ChatNode.Reference.Link(URI.create("mailto:a%0A@example.org"), false));

        var email = new ChatNode.Reference.Link(URI.create("mailto:admin@example.org"), false);
        assertEquals("mailto:admin@example.org", email.uri().toString());
    }

    @Test
    void gradientStylesRequireTwoValidColorsAndSolidOverridesClearThem() {
        ChatStyle gradient = new ChatStyle(
                0x55D6FF, 0xC084FC, false, false, false, false);

        assertEquals(0x55D6FF, gradient.color());
        assertEquals(0xC084FC, gradient.gradientEndColor());
        assertEquals(new ChatStyle(
                        0xFFFFFF, null, false, false, false, false),
                gradient.withColor(0xFFFFFF));
        assertThrows(IllegalArgumentException.class, () -> new ChatStyle(
                null, 0xC084FC, false, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new ChatStyle(
                0x55D6FF, 0x1000000, false, false, false, false));
    }

    @Test
    void displayedNodesNormalizeLineBreaks() {
        assertEquals("hello world", ChatContent.plain("hello\nworld").plainText());
    }
}
