package org.encinet.mik.feature.community.chat.parse;

import org.encinet.mik.feature.community.chat.input.AuthorizedChatMarkup;

import org.encinet.mik.feature.community.chat.mention.PlayerMentionIndex.Target;
import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatMentions;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.encinet.mik.feature.community.chat.model.ChatProcessingContext.linksOnly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatProcessorTest {
    private final ChatProcessor processor = new ChatProcessor(new AuthorizedChatMarkup()::parse);

    @Test
    void linksOnlyContextDoesNotEnablePlayerModifiers() {
        ChatContent content = processor.process("%i @all", linksOnly());

        assertEquals("%i @all", content.plainText());
        assertTrue(content.nodes().stream().allMatch(ChatNode.Text.class::isInstance));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello @Alice", "hello @ Alice", "@Alice你好", "你好@Alice", "你好@Alice在吗",
            "你好@ aLiCe在吗", "@Aliceこんにちは", "안녕@Alice", "𠀀@Alice𠀀"})
    void playerMentionsPreserveTheSourceWhileIdentifyingTheRecipient(String source) {
        UUID mentionedId = UUID.randomUUID();
        ChatProcessingContext context = new ChatProcessingContext(
                Set.of(ChatCapability.PLAYER_MENTION),
                PlayerMentions.of(List.of(new Target(
                        mentionedId, "Alice"))), Optional.empty(), Map.of());
        ChatContent content = processor.process(source, context);

        assertEquals(List.of(mentionedId), playerTargets(content));
        assertEquals(source, content.plainText());
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

        assertEquals("Alice Alice@ @Alice @ Alice", content.plainText());
        assertEquals(2, playerTargets(content).size());
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

        assertEquals("mail@Alice @AliceLonger @ aLiCe", content.plainText());
        assertEquals(List.of(playerId), playerTargets(content));
    }

    @Test
    void multipleMentionsInUnspacedTextPreserveBothTargetsAndTheMessage() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        ChatProcessingContext context = new ChatProcessingContext(Set.of(ChatCapability.PLAYER_MENTION),
                PlayerMentions.of(List.of(new Target(alice, "Alice"), new Target(bob, "Bob"))),
                Optional.empty(), Map.of());

        ChatContent content = processor.process("你好@Alice请叫@Bob来一下", context);

        assertEquals("你好@Alice请叫@Bob来一下", content.plainText());
        assertEquals(List.of(alice, bob), playerTargets(content));
    }

    @Test
    void addressesConsumeTheirWholeReferenceBeforeMentionsAreResolved() {
        UUID alice = UUID.randomUUID();
        ChatProcessingContext context = ChatProcessingContext.playerText(
                PlayerMentions.of(List.of(new Target(alice, "Alice"))));
        String source = "https://example.org/@Alice admin@Alice.example @alice:example.org @ Alice";

        ChatContent content = processor.process(source, context);

        assertEquals(List.of(alice), playerTargets(content));
        assertEquals(List.of("https://example.org/@Alice", "mailto:admin@Alice.example",
                        "https://matrix.to/#/@alice:example.org"),
                content.nodes().stream().filter(ChatProcessorTest::isLink)
                        .map(ChatNode.Reference.class::cast)
                        .map(ref -> ((ChatNode.Reference.Link) ref.target()).uri().toString()).toList());
        assertEquals("@ Alice", content.nodes().getLast().visibleText());
        assertTrue(playerTargets(processor.process(source, linksOnly())).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"@@Alice", "@Alice@server", "path/@Alice", "x:@Alice", "#@Alice",
            "https://example.org/%GG?who=@Alice", "@\u2003Alice",
            "@@all", "@all@server", "https://example.org/%GG?who=@all"})
    void mentionSyntaxDoesNotDependOnNameResolutionOrBroadcastPrivileges(String source) {
        var known = PlayerMentions.of(List.of(new Target(UUID.randomUUID(), "Alice")));
        var capabilities = Set.of(ChatCapability.LINK, ChatCapability.PLAYER_MENTION,
                ChatCapability.BROADCAST_MENTION);
        for (var players : List.of(known, PlayerMentions.empty())) {
            var context = new ChatProcessingContext(capabilities, players, Optional.empty(), Map.of())
                    .withBoundPlayerMentions();
            ChatContent content = processor.process(source, context);
            assertEquals(source, content.plainText());
            assertTrue(content.nodes().stream().allMatch(ChatNode.Text.class::isInstance));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"@all过来", "大家@ALL看这里", "𠀀@all𠀀"})
    void broadcastMentionsUseTheSameTextBoundaries(String source) {
        ChatProcessingContext context = new ChatProcessingContext(Set.of(ChatCapability.BROADCAST_MENTION),
                PlayerMentions.empty(), Optional.empty(), Map.of());
        ChatContent content = processor.process(source, context);

        assertEquals(source, content.plainText());
        assertEquals(new ChatMentions(Set.of(), true), ChatMentions.fromContent(content));
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

        assertEquals("@ Alex One @Alex", content.plainText());
        assertEquals(List.of(longNameId, shortNameId), playerTargets(content));
    }

    @Test
    void safeMiniMessageFormattingBecomesSemanticStyles() {
        ChatProcessingContext context = new ChatProcessingContext(
                Set.of(ChatCapability.MINI_MESSAGE, ChatCapability.PLAYER_MENTION),
                PlayerMentions.of(List.of(new Target(UUID.randomUUID(), "Alice"))),
                Optional.empty(), Map.of());

        ChatContent content = processor.process(
                "<red><bold>hello</bold></red> @ <bold>aLi</bold>Ce", context);

        ChatNode.Text text = assertInstanceOf(
                ChatNode.Text.class, content.nodes().getFirst());
        assertEquals("hello", text.text());
        assertEquals(0xFF5555, text.style().color());
        assertTrue(text.style().bold());
        var mention = assertInstanceOf(ChatNode.Reference.class, content.nodes().getLast());
        assertInstanceOf(ChatNode.Reference.Person.class, mention.target());
        assertEquals("@ aLiCe", mention.visibleText());
        assertEquals(List.of("aLi"), mention.runs().stream().filter(run -> run.style().bold())
                .map(ChatNode.Text::text).toList());
    }

    @Test
    void authorisedAudienceTakesPrecedenceOverAPlayerWithTheSameName() {
        UUID player = UUID.randomUUID();
        var names = PlayerMentions.of(List.of(new Target(player, "all")));
        var publicChat = new ChatProcessingContext(Set.of(ChatCapability.PLAYER_MENTION, ChatCapability.BROADCAST_MENTION),
                names, Optional.empty(), Map.of());
        var content = processor.process("@all", publicChat);
        assertEquals(new ChatMentions(Set.of(), true), ChatMentions.fromContent(content));

        assertEquals(List.of(player), playerTargets(processor.process("@all", ChatProcessingContext.playerText(names))));
        assertTrue(playerTargets(processor.process("@all", ChatProcessingContext.playerText(PlayerMentions.empty()))).isEmpty());
    }

    private static boolean isLink(ChatNode node) {
        return node instanceof ChatNode.Reference ref && ref.target() instanceof ChatNode.Reference.Link;
    }

    private static List<UUID> playerTargets(ChatContent content) {
        return content.nodes().stream()
                .map(node -> node instanceof ChatNode.Reference ref
                        && ref.target() instanceof ChatNode.Reference.Person person ? person.playerId() : null)
                .filter(java.util.Objects::nonNull).toList();
    }
}
