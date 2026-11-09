package org.encinet.mik.feature.community.chat.pipeline;

import org.encinet.mik.feature.community.chat.mention.PlayerMentionIndex.Target;
import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatEffect;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatMessageId;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatReferences;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.feature.community.chat.model.ChatConversation;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    void unchangedContentRetainsEmailAndMentionSemantics() {
        UUID bob = UUID.randomUUID();
        ChatMessage original = gameMessage("@Bob admin@example.org", bob);
        assertEquals(original, processor.acceptFinalContent(original, original.content()));
        assertTrue(original.content().nodes().stream().anyMatch(ChatNode.PlayerMention.class::isInstance));
        assertTrue(original.content().nodes().stream().anyMatch(node -> node instanceof ChatNode.Link link
                && link.target().getScheme().equals("mailto")));
    }

    @Test
    void finalGameEditsReplaceOldMentionsAndPreserveExplicitLinkTargets() {
        UUID bob = UUID.randomUUID();
        ChatMessage original = gameMessage("@Bob github.com/encinet/mik", bob);
        var finalBody = net.kyori.adventure.text.Component.text("redacted ")
                .append(net.kyori.adventure.text.Component.text("@Bob")
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl("https://example.org/final")));
        ChatMessage edited = processor.acceptFinalContent(original, new AdventureComponentImporter().importComponent(finalBody));
        assertTrue(edited.effects().isEmpty(), "Mention-shaped link labels must not trigger alerts");
        assertEquals("redacted @Bob", edited.content().plainText());
        assertEquals("https://example.org/final", ((ChatNode.Link) edited.content().nodes().getLast()).target().toString());
        ChatMessage mentioned = processor.acceptFinalContent(original, new AdventureComponentImporter()
                .importComponent(net.kyori.adventure.text.Component.text("edited @Bob")));
        assertEquals(Set.of(bob), ((ChatEffect.Mentions) mentioned.effects().iterator().next()).playerIds());
    }

    private ChatMessage gameMessage(String source, UUID bob) {
        UUID sender = UUID.randomUUID();
        ChatProcessingContext context = new ChatProcessingContext(Set.of(ChatCapability.URL, ChatCapability.PLAYER_MENTION),
                PlayerMentions.of(List.of(new Target(bob, "Bob"))), Optional.empty(), Map.of());
        return processor.process(new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Minecraft(sender),
                ChatSender.minecraft(sender, "Alice"), ChatConversation.minecraftPublic(), source,
                ChatReferences.empty(), Instant.now(), context));
    }

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

    @ParameterizedTest
    @CsvSource({"hello @Alice, hello @Alice", "hello @ Alice, hello @Alice",
            "@Alice你好, @Alice 你好", "你好@Alice, 你好 @Alice", "你好@Alice在吗, 你好 @Alice 在吗",
            "你好@ aLiCe在吗, 你好 @Alice 在吗", "@Aliceこんにちは, @Alice こんにちは",
            "안녕@Alice, 안녕 @Alice", "𠀀@Alice𠀀, 𠀀 @Alice 𠀀"})
    void playerMentionsProduceTheSameSemanticEffectUsedForNotifications(String source, String expected) {
        UUID mentionedId = UUID.randomUUID();
        ChatProcessingContext context = new ChatProcessingContext(
                Set.of(ChatCapability.PLAYER_MENTION),
                PlayerMentions.of(List.of(new Target(
                        mentionedId, "Alice"))), Optional.empty(), Map.of());
        UUID sender = UUID.randomUUID();
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.random(), new ChatOrigin.Minecraft(sender),
                ChatSender.minecraft(sender, "Steve"),
                ChatConversation.minecraftPublic(), source,
                ChatReferences.empty(), Instant.EPOCH, context);

        ChatMessage message = processor.process(submission);

        ChatEffect.Mentions mentions = assertInstanceOf(
                ChatEffect.Mentions.class, message.effects().iterator().next());
        assertEquals(Set.of(mentionedId), mentions.playerIds());
        assertEquals(expected, message.content().plainText());
    }

    @Test
    void playerMentionsUsePrefixSyntaxAndLeaveSuffixSyntaxAsText() {
        UUID mentionedId = UUID.randomUUID();
        ChatProcessingContext context = new ChatProcessingContext(
                Set.of(ChatCapability.PLAYER_MENTION),
                PlayerMentions.of(List.of(new Target(
                        mentionedId, "Alice"))), Optional.empty(), Map.of());

        ChatContent content = processor.process(
                "Alice Alice@ @Alice @ Alice", context);

        assertEquals("Alice Alice@ @Alice @Alice", content.plainText());
        assertEquals(2, content.nodes().stream()
                .filter(ChatNode.PlayerMention.class::isInstance).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Alice", "Alice@", "prefix@Alice", "prefix@ Alice",
            "@AliceLonger", "@ Alice_more", "@Alice1", "@Alice\u0301", "@Aliceé", "@AliceΣ"})
    void playerMentionsRequireAPrefixAndWholeName(String source) {
        ChatProcessingContext context = new ChatProcessingContext(
                Set.of(ChatCapability.PLAYER_MENTION),
                PlayerMentions.of(List.of(new Target(
                        UUID.randomUUID(), "Alice"))), Optional.empty(), Map.of());

        ChatContent content = processor.process(source, context);

        assertEquals(source, content.plainText());
        assertTrue(content.nodes().stream().allMatch(ChatNode.Text.class::isInstance));
    }

    @Test
    void playerMentionsSkipInvalidCandidatesAndIgnoreCase() {
        UUID playerId = UUID.randomUUID();
        ChatProcessingContext context = new ChatProcessingContext(
                Set.of(ChatCapability.PLAYER_MENTION),
                PlayerMentions.of(List.of(new Target(
                        playerId, "Alice"))), Optional.empty(), Map.of());

        ChatContent content = processor.process(
                "mail@Alice @AliceLonger @ aLiCe", context);

        assertEquals("mail@Alice @AliceLonger @Alice", content.plainText());
        assertEquals(List.of(playerId), content.nodes().stream()
                .filter(ChatNode.PlayerMention.class::isInstance)
                .map(ChatNode.PlayerMention.class::cast)
                .map(ChatNode.PlayerMention::playerId).toList());
    }

    @Test
    void multipleMentionsInUnspacedTextPreserveBothTargetsAndTheMessage() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        ChatProcessingContext context = new ChatProcessingContext(Set.of(ChatCapability.PLAYER_MENTION),
                PlayerMentions.of(List.of(new Target(alice, "Alice"), new Target(bob, "Bob"))),
                Optional.empty(), Map.of());

        ChatContent content = processor.process("你好@Alice请叫@Bob来一下", context);

        assertEquals("你好 @Alice 请叫 @Bob 来一下", content.plainText());
        assertEquals(List.of(alice, bob), content.nodes().stream()
                .filter(ChatNode.PlayerMention.class::isInstance)
                .map(ChatNode.PlayerMention.class::cast).map(ChatNode.PlayerMention::playerId).toList());
    }

    @Test
    void namesInsideLinksAndEmailAddressesAreNotPlayerMentions() {
        ChatProcessingContext context = new ChatProcessingContext(Set.of(ChatCapability.URL, ChatCapability.PLAYER_MENTION),
                PlayerMentions.of(List.of(new Target(UUID.randomUUID(), "Alice"))), Optional.empty(), Map.of());

        ChatContent content = processor.process("https://example.org/@Alice你好 admin@Alice.example", context);

        assertTrue(content.nodes().stream().noneMatch(ChatNode.PlayerMention.class::isInstance));
        assertEquals(2, content.nodes().stream().filter(ChatNode.Link.class::isInstance).count());
    }

    @ParameterizedTest
    @CsvSource({"@all过来, @all 过来", "大家@ALL看这里, 大家 @all 看这里", "𠀀@all𠀀, 𠀀 @all 𠀀"})
    void broadcastMentionsUseTheSameTextBoundaries(String source, String expected) {
        ChatProcessingContext context = new ChatProcessingContext(Set.of(ChatCapability.BROADCAST_MENTION),
                PlayerMentions.empty(), Optional.empty(), Map.of());
        UUID sender = UUID.randomUUID();
        ChatSubmission submission = new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Minecraft(sender),
                ChatSender.minecraft(sender, "Steve"), ChatConversation.minecraftPublic(),
                source, ChatReferences.empty(), Instant.EPOCH, context);

        ChatMessage message = processor.process(submission);

        assertEquals(expected, message.content().plainText());
        ChatEffect.Mentions mentions = assertInstanceOf(ChatEffect.Mentions.class, message.effects().iterator().next());
        assertTrue(mentions.broadcast());
        assertTrue(mentions.playerIds().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"@alliance", "mail@all", "@all_more", "@all1", "@all\u0301", "@allé"})
    void broadcastMentionsDoNotSplitLongerNamesOrWords(String source) {
        ChatProcessingContext context = new ChatProcessingContext(Set.of(ChatCapability.BROADCAST_MENTION),
                PlayerMentions.empty(), Optional.empty(), Map.of());

        ChatContent content = processor.process(source, context);

        assertEquals(source, content.plainText());
        assertTrue(content.nodes().stream().allMatch(ChatNode.Text.class::isInstance));
    }

    @Test
    void playerMentionsPreferTheLongestMatchingKnownName() {
        UUID shortNameId = UUID.randomUUID();
        UUID longNameId = UUID.randomUUID();
        ChatProcessingContext context = new ChatProcessingContext(
                Set.of(ChatCapability.PLAYER_MENTION), PlayerMentions.of(List.of(
                new Target(shortNameId, "Alex"),
                new Target(longNameId, "Alex One"))),
                Optional.empty(), Map.of());

        ChatContent content = processor.process("@ Alex One @Alex", context);

        assertEquals("@Alex One @Alex", content.plainText());
        assertEquals(List.of(longNameId, shortNameId), content.nodes().stream()
                .filter(ChatNode.PlayerMention.class::isInstance)
                .map(ChatNode.PlayerMention.class::cast)
                .map(ChatNode.PlayerMention::playerId).toList());
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
                ChatMessageId.external("matrix", "room", "$mention"),
                new ChatOrigin.Social("matrix", "Matrix", 0x55FFFF, "!room:example.org", "$mention"),
                new ChatSender("Sender", Optional.empty(), Optional.empty(),
                        "", "", "", 0xFFFFFF),
                ChatConversation.minecraftPublic(),
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
                Set.of(ChatCapability.MINI_MESSAGE), PlayerMentions.empty(),
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
        UUID sender = UUID.randomUUID();
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.random(), new ChatOrigin.Minecraft(sender),
                ChatSender.minecraft(sender, "Steve"),
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
        ChatProcessingContext context = new ChatProcessingContext(
                Set.of(ChatCapability.URL, ChatCapability.PLAYER_MENTION),
                PlayerMentions.of(List.of(new Target(
                        UUID.randomUUID(), "Alice"))), Optional.empty(), Map.of());
        ChatContent content = processor.process(
                "admin@example.org @alice:example.org matrix:u/bob:example.org",
                context);

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
