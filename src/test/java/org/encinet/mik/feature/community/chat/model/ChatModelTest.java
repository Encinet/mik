package org.encinet.mik.feature.community.chat.model;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatModelTest {

    @Test
    void playerConnectionsCannotDisagreeWithTheAuthenticatedSender() {
        UUID playerId = UUID.randomUUID();
        for (ChatOrigin.Player origin : List.of(new ChatOrigin.Minecraft(playerId), new ChatOrigin.Web(playerId))) {
            ChatSubmission accepted = new ChatSubmission(ChatMessageId.random(), origin, ChatSender.minecraft(playerId, "Alice"),
                    ChatConversation.minecraftPublic(), "hello", ChatReferences.empty(), Instant.EPOCH, ChatProcessingContext.external());
            assertEquals(playerId, accepted.sender().minecraftId().orElseThrow());
            assertThrows(IllegalArgumentException.class, () -> new ChatSubmission(ChatMessageId.random(), origin,
                    ChatSender.minecraft(UUID.randomUUID(), "Alice"), ChatConversation.minecraftPublic(), "hello",
                    ChatReferences.empty(), Instant.EPOCH, ChatProcessingContext.external()));
        }
    }

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
    void longContentPreviewsStayBoundedWithoutSplittingUnicode() {
        String complete = "x".repeat(16_000);
        assertEquals(complete, ChatContent.plainPreview(complete).plainText());
        assertThrows(IllegalArgumentException.class, () -> ChatContent.plain(complete + "x"));
        assertEquals("x".repeat(15_999) + "…", ChatContent.plainPreview(complete + "x").plainText());

        String unicode = "x".repeat(15_998) + "😀" + "more";
        assertEquals("x".repeat(15_998) + "…", ChatContent.plainPreview(unicode).plainText());
        assertEquals("Title     Body", ChatContent.plainPreview("Title\u0001\t\u007F\u0085\nBody").plainText());
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
                ChatMessageId.external("matrix", "room", "$event"),
                new ChatOrigin.Social("matrix", "Matrix", "!room:example.org", "$event"),
                new ChatSender("Steve", Optional.of(playerId), Optional.empty(),
                        "Steve", "[VIP] ", "", 0xFFFFFF),
                ChatConversation.minecraftPublic(),
                "hello", ChatReferences.empty(), Instant.EPOCH,
                ChatProcessingContext.external());

        assertEquals("hello", submission.sourceText());
        assertTrue(submission.origin().visitedPlatforms().contains("matrix"));
        assertEquals(playerId, submission.sender().minecraftId().orElseThrow());
    }

    @Test
    void transportLineBreaksBecomeSafeSingleLineText() {
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.random(), new ChatOrigin.Social("matrix", "Matrix", "!room:example.org", "$event"),
                new ChatSender("Alice", Optional.empty(), Optional.empty(),
                        "", "", "", 0xFFFFFF),
                ChatConversation.minecraftPublic(),
                "hello\nworld", ChatReferences.empty(), Instant.EPOCH,
                ChatProcessingContext.external());

        assertEquals("hello world", submission.sourceText());
        assertEquals("hello world", ChatContent.plain("hello\nworld").plainText());
    }
}
