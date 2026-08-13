package org.encinet.mik.module.chat.model;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatModelTest {

    @Test
    void semanticContentMergesPlainRunsAndKeepsLinks() {
        ChatStyle white = ChatStyle.EMPTY.withColor(0xFFFFFF);
        ChatContent content = new ChatContent(List.of(
                new ChatNode.Text("hello ", white),
                new ChatNode.Text("world ", white),
                new ChatNode.Link("[wiki]", URI.create(
                        "https://zh.minecraft.wiki/w/钻石"), white)));

        assertEquals("hello world [wiki]", content.plainText());
        assertEquals(2, content.nodes().size());
        assertEquals("https://zh.minecraft.wiki/w/钻石",
                ((ChatNode.Link) content.nodes().getLast()).target().toString());
    }

    @Test
    void linksRejectUnsafeSchemesAndCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new ChatNode.Link(
                "bad", URI.create("javascript:alert(1)"), ChatStyle.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new ChatNode.Link(
                "bad", URI.create("https://user:secret@example.org"),
                ChatStyle.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new ChatNode.Link(
                "bad", URI.create("mailto:a@example.org?subject=secret"),
                ChatStyle.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new ChatNode.Link(
                "bad", URI.create("mailto:a@example.org,b@example.org"),
                ChatStyle.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new ChatNode.Link(
                "bad", URI.create("mailto:a%0A@example.org"),
                ChatStyle.EMPTY));

        ChatNode.Link email = new ChatNode.Link("email",
                URI.create("mailto:admin@example.org"), ChatStyle.EMPTY);
        assertEquals("mailto:admin@example.org", email.target().toString());
    }

    @Test
    void itemSnapshotsDefensivelyCopyOpaqueMinecraftData() {
        byte[] source = {1, 2, 3};
        ChatItemSnapshot item = new ChatItemSnapshot(
                "minecraft:diamond", "Diamond", 2, source);
        source[0] = 9;
        byte[] returned = item.minecraftData();
        returned[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, item.minecraftData());
        assertEquals("[Diamond x2]", item.fallbackText());
    }

    @Test
    void submissionRetainsRawFactsAndOriginTrace() {
        UUID playerId = UUID.randomUUID();
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.external("matrix", "$event"),
                new ChatOrigin.Social("matrix", "$event"),
                new ChatSender("Steve", Optional.of(playerId), Optional.empty(),
                        "Steve", "[VIP] ", ""),
                new ChatConversation(ChatConversation.Kind.SOCIAL,
                        "!room:example.org"),
                "hello", ChatReferences.empty(), Instant.EPOCH,
                ChatProcessingContext.external());

        assertEquals("hello", submission.sourceText());
        assertTrue(submission.origin().visitedPlatforms().contains("matrix"));
        assertEquals(playerId, submission.sender().minecraftId().orElseThrow());
    }

    @Test
    void transportLineBreaksBecomeSafeSingleLineText() {
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.random(), new ChatOrigin.Social("matrix", "$event"),
                new ChatSender("Alice", Optional.empty(), Optional.empty(),
                        "", "", ""),
                new ChatConversation(ChatConversation.Kind.SOCIAL, "room"),
                "hello\nworld", ChatReferences.empty(), Instant.EPOCH,
                ChatProcessingContext.external());

        assertEquals("hello world", submission.sourceText());
        assertEquals("hello world", ChatContent.plain("hello\nworld").plainText());
    }
}
