package org.encinet.mik.module.chat.pipeline;

import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatEffect;
import org.encinet.mik.module.chat.model.ChatMessage;
import org.encinet.mik.module.chat.model.ChatMessageId;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatOrigin;
import org.encinet.mik.module.chat.model.ChatProcessingContext;
import org.encinet.mik.module.chat.model.ChatReferences;
import org.encinet.mik.module.chat.model.ChatSender;
import org.encinet.mik.module.chat.model.ChatSubmission;
import org.encinet.mik.module.chat.model.ChatConversation;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.ExternalIdentityKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatProcessorTest {
    private final ChatProcessor processor = new ChatProcessor();

    @Test
    void siteModifiersWinOverGenericUrls() {
        ChatContent mojira = processor.process(
                "https://bugs.mojang.com/browse/MC-4", external());
        ChatContent wiki = processor.process(
                "https://zh.minecraft.wiki/w/钻石", external());
        ChatContent github = processor.process(
                "github.com/encinet/mik", external());
        ChatContent pages = processor.process(
                "encinet.github.io/mik", external());
        ChatContent gist = processor.process(
                "gist.github.com/encinet/0123456789abcdef", external());
        ChatContent bilibili = processor.process(
                "https://m.bilibili.com/video/BV1GJ411x7h7?share_source=copy",
                external());

        assertEquals("[Mojira: MC-4]", mojira.plainText());
        assertEquals("[Minecraft Wiki: 钻石]", wiki.plainText());
        assertEquals("[GitHub: encinet/mik]", github.plainText());
        assertEquals("[GitHub Pages: encinet/mik]", pages.plainText());
        assertEquals("[GitHub Gist: encinet/01234567]", gist.plainText());
        assertEquals("[Bilibili: BV1GJ411x7h7]", bilibili.plainText());
        assertEquals("https://github.com/encinet/mik",
                ((ChatNode.Link) github.nodes().getFirst()).target().toString());
    }

    @Test
    void capabilitiesPreventMinecraftOnlyModifiersOnSocialInput() {
        ChatContent external = processor.process("%i @all", external());

        assertEquals("%i @all", external.plainText());
        assertTrue(external.nodes().stream().allMatch(ChatNode.Text.class::isInstance));
    }

    @Test
    void playerMentionsProduceTheSameSemanticEffectUsedForNotifications() {
        UUID mentionedId = UUID.randomUUID();
        ChatProcessingContext context = new ChatProcessingContext(
                Set.of(ChatCapability.PLAYER_MENTION),
                List.of(new ChatProcessingContext.PlayerReference(
                        mentionedId, "Alice")), Optional.empty(), Map.of());
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.random(), new ChatOrigin.Minecraft(UUID.randomUUID()),
                ChatSender.minecraft(UUID.randomUUID(), "Steve"),
                ChatConversation.minecraftPublic(), "hello Alice@",
                ChatReferences.empty(), Instant.EPOCH, context);

        ChatMessage message = processor.process(submission);

        ChatEffect.Mentions mentions = assertInstanceOf(
                ChatEffect.Mentions.class, message.effects().iterator().next());
        assertEquals(Set.of(mentionedId), mentions.playerIds());
        assertEquals("hello @Alice", message.content().plainText());
    }

    @Test
    void playerMentionsRequireTheSuffixSyntaxButRenderWithAPrefix() {
        UUID mentionedId = UUID.randomUUID();
        ChatProcessingContext context = new ChatProcessingContext(
                Set.of(ChatCapability.PLAYER_MENTION),
                List.of(new ChatProcessingContext.PlayerReference(
                        mentionedId, "Alice")), Optional.empty(), Map.of());

        ChatContent content = processor.process(
                "Alice Alice@ @Alice", context);

        assertEquals("Alice @Alice @Alice", content.plainText());
        assertEquals(1, content.nodes().stream()
                .filter(ChatNode.PlayerMention.class::isInstance).count());
    }

    @Test
    void authenticatedSocialMentionRangesBecomeBoundOrExternalNodes() {
        UUID playerId = UUID.randomUUID();
        ExternalIdentity bound = new ExternalIdentity(new ExternalIdentityKey(
                "matrix", "example.org", "", "@alice:example.org"),
                "Alice Social");
        ExternalIdentity unbound = new ExternalIdentity(new ExternalIdentityKey(
                "matrix", "example.org", "", "@ghost:example.org"),
                "Ghost");
        ChatReferences references = new ChatReferences(Optional.empty(),
                Optional.empty(), List.of(
                new ChatReferences.Mention(3, 9, bound,
                        Optional.of(new ChatReferences.MinecraftTarget(
                                playerId, "Alice"))),
                new ChatReferences.Mention(14, 20, unbound)));
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.external("matrix", "$mention"),
                new ChatOrigin.Social("matrix", "$mention"),
                new ChatSender("Sender", Optional.empty(), Optional.empty(),
                        "", "", ""),
                new ChatConversation(ChatConversation.Kind.SOCIAL,
                        "matrix:room"),
                "hi @Alice and @Ghost", references, Instant.EPOCH, external());

        ChatMessage message = processor.process(submission);

        assertEquals("hi @Alice Social(Alice) and @Ghost",
                message.content().plainText());
        assertEquals(Set.of(playerId), assertInstanceOf(
                ChatEffect.Mentions.class,
                message.effects().iterator().next()).playerIds());
        assertEquals(1, message.content().nodes().stream()
                .filter(ChatNode.ExternalMention.class::isInstance).count());
    }

    @Test
    void safeMiniMessageFormattingBecomesSemanticStyles() {
        ChatProcessingContext context = new ChatProcessingContext(
                Set.of(ChatCapability.MINI_MESSAGE), List.of(),
                Optional.empty(), Map.of());

        ChatContent content = processor.process(
                "<red><bold>hello</bold></red>", context);

        ChatNode.Text text = assertInstanceOf(
                ChatNode.Text.class, content.nodes().getFirst());
        assertEquals("hello", text.text());
        assertEquals(0xFF5555, text.style().color());
        assertTrue(text.style().bold());
    }

    @Test
    void urlEnrichmentSurvivesIntoTheSharedMessageModel() {
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.random(), new ChatOrigin.Minecraft(UUID.randomUUID()),
                ChatSender.minecraft(UUID.randomUUID(), "Steve"),
                ChatConversation.minecraftPublic(),
                "看看 https://example.com/path", ChatReferences.empty(),
                Instant.EPOCH, external());

        ChatMessage message = processor.process(submission);

        assertTrue(message.content().nodes().stream()
                .anyMatch(ChatNode.Link.class::isInstance));
        assertEquals("看看 [https://example.com/path]",
                message.content().plainText());
    }

    @Test
    void sharedPipelinePrefersEmailAndMatrixSemanticsOverGenericParsing() {
        ChatContent content = processor.process(
                "admin@example.org @alice:example.org matrix:u/bob:example.org",
                external());

        assertEquals("[Email: admin@example.org] "
                        + "[Matrix: @alice:example.org] "
                        + "[Matrix: @bob:example.org]",
                content.plainText());
        assertEquals(List.of(
                        "mailto:admin@example.org",
                        "https://matrix.to/#/%40alice%3Aexample.org",
                        "https://matrix.to/#/%40bob%3Aexample.org"),
                content.nodes().stream()
                        .filter(ChatNode.Link.class::isInstance)
                        .map(ChatNode.Link.class::cast)
                        .map(link -> link.target().toString())
                        .toList());
    }

    private ChatProcessingContext external() {
        return ChatProcessingContext.external();
    }
}
