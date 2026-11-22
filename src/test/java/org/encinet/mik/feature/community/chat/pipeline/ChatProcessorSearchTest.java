package org.encinet.mik.feature.community.chat.pipeline;

import org.encinet.mik.feature.community.chat.input.AuthorizedChatMarkup;

import org.encinet.mik.feature.community.chat.mention.PlayerMentionIndex.Target;
import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.modifier.ChatModifier;
import org.encinet.mik.feature.community.chat.modifier.ChatReplacement;
import org.encinet.mik.feature.community.chat.modifier.PlayerMentionModifier;
import org.encinet.mik.feature.community.chat.modifier.UrlModifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ChatProcessorSearchTest {
    private final UUID aliceId = UUID.randomUUID();
    private final ChatProcessingContext context = new ChatProcessingContext(
            Set.of(ChatCapability.URL, ChatCapability.PLAYER_MENTION),
            PlayerMentions.of(List.of(new Target(aliceId, "Alice"))),
            Optional.empty(), Map.of());
    private final CountingModifier mentions = new CountingModifier(new PlayerMentionModifier());
    private final ChatProcessor processor = new ChatProcessor(
            List.of(mentions, new UrlModifier()), new AuthorizedChatMarkup()::parse);

    @Test
    void exhaustedMentionSearchIsReusedAcrossLinks() {
        ChatContent content = processor.process(
                "https://one.example/ https://two.example/ @Nobody", context);

        assertEquals("[https://one.example/] [https://two.example/] @Nobody",
                content.plainText());
        assertEquals(2, content.nodes().stream().filter(ChatNode.Link.class::isInstance).count());
        assertEquals(1, mentions.searches);
    }

    @Test
    void futureMentionIsReusedUntilCursorReachesIt() {
        ChatContent content = processor.process(
                "https://one.example/ https://two.example/ @Alice", context);

        assertEquals("[https://one.example/] [https://two.example/] @Alice",
                content.plainText());
        assertEquals(aliceId, assertInstanceOf(
                ChatNode.PlayerMention.class, content.nodes().getLast()).playerId());
        assertEquals(1, mentions.searches);
    }

    @Test
    void overlappingMentionCandidateIsDiscardedAndSearchContinues() {
        ChatContent content = processor.process(
                "https://example.org/@Alice @ Alice", context);

        assertEquals("[https://example.org/@Alice] @Alice", content.plainText());
        assertEquals(1, content.nodes().stream()
                .filter(ChatNode.PlayerMention.class::isInstance).count());
        assertEquals(aliceId, assertInstanceOf(
                ChatNode.PlayerMention.class, content.nodes().getLast()).playerId());
        assertEquals(2, mentions.searches);
    }

    @Test
    void searchResultsAreLocalToEachMessageAndContext() {
        ChatProcessingContext emptyContext = new ChatProcessingContext(
                context.capabilities(), PlayerMentions.empty(), Optional.empty(), Map.of());

        ChatContent first = processor.process("@Alice", emptyContext);
        ChatContent second = processor.process("@Alice", context);
        ChatContent third = processor.process("@Alice", emptyContext);

        assertInstanceOf(ChatNode.Text.class, first.nodes().getFirst());
        assertEquals(aliceId, assertInstanceOf(
                ChatNode.PlayerMention.class, second.nodes().getFirst()).playerId());
        assertInstanceOf(ChatNode.Text.class, third.nodes().getFirst());
        assertEquals(3, mentions.searches);
    }

    private static final class CountingModifier implements ChatModifier {
        private final ChatModifier delegate;
        private int searches;

        private CountingModifier(ChatModifier delegate) {
            this.delegate = delegate;
        }

        @Override
        public int priority() {
            return delegate.priority();
        }

        @Override
        public Set<ChatCapability> requiredCapabilities() {
            return delegate.requiredCapabilities();
        }

        @Override
        public ChatReplacement find(String text, int fromIndex, ChatProcessingContext context) {
            searches++;
            return delegate.find(text, fromIndex, context);
        }
    }
}
