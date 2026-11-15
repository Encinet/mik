package org.encinet.mik.feature.community.chat.pipeline;

import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatStyle;
import org.encinet.mik.feature.community.chat.modifier.ChatModifier;
import org.encinet.mik.feature.community.chat.modifier.ChatReplacement;
import org.encinet.mik.feature.community.chat.modifier.UrlModifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatProcessorFailureTest {
    private final ChatProcessingContext context =
            ChatProcessingContext.playerText(PlayerMentions.empty());

    @Test
    void failedSearchPreservesTextAndOtherModifiersAndRetriesOnTheNextMessage() {
        AtomicInteger searches = new AtomicInteger();
        ChatModifier broken = new ChatModifier() {
            @Override
            public ChatReplacement find(String text, int fromIndex, ChatProcessingContext context) {
                searches.incrementAndGet();
                throw new IllegalArgumentException("Broken modifier");
            }
        };
        ChatProcessor processor = processor(broken);
        String source = "plain https://example.com/ https://example.org/";

        ChatContent first = processor.process(source, context);
        assertEquals("plain [https://example.com/] [https://example.org/]", first.plainText());
        assertEquals(1, searches.get());

        ChatContent second = processor.process(source, context);
        assertEquals(first, second);
        assertEquals(2, searches.get());
    }

    @Test
    void failedCapabilityCheckDoesNotAbortOtherModifiers() {
        ChatModifier broken = new ChatModifier() {
            @Override
            public boolean supports(ChatProcessingContext context) {
                throw new IllegalStateException("Broken capability check");
            }

            @Override
            public ChatReplacement find(String text, int fromIndex, ChatProcessingContext context) {
                throw new AssertionError("Unsupported modifier must not run");
            }
        };

        ChatContent content = processor(broken).process("plain https://example.com/", context);

        assertEquals("plain [https://example.com/]", content.plainText());
    }

    @Test
    void failedRescanKeepsPreviousReplacementsAndStopsRetryingWithinTheMessage() {
        AtomicInteger searches = new AtomicInteger();
        ChatModifier broken = new ChatModifier() {
            @Override
            public ChatReplacement find(String text, int fromIndex, ChatProcessingContext context) {
                searches.incrementAndGet();
                if (fromIndex == 0) {
                    return new ChatReplacement(0, 4, new ChatNode.Text("done", ChatStyle.EMPTY));
                }
                throw new IllegalStateException("Broken rescan");
            }
        };

        ChatContent content = processor(broken).process(
                "test plain https://example.com/ https://example.org/", context);

        assertEquals("done plain [https://example.com/] [https://example.org/]", content.plainText());
        assertEquals(2, searches.get());
    }

    @Test
    void outOfBoundsReplacementFallsBackToText() {
        ChatModifier broken = new ChatModifier() {
            @Override
            public ChatReplacement find(String text, int fromIndex, ChatProcessingContext context) {
                return new ChatReplacement(0, text.length() + 1,
                        new ChatNode.Text("invalid", ChatStyle.EMPTY));
            }
        };

        ChatContent content = processor(broken).process("original", context);

        assertEquals("original", content.plainText());
    }

    @Test
    void staleReplacementCannotMoveTheCursorBackwards() {
        AtomicInteger searches = new AtomicInteger();
        ChatModifier broken = new ChatModifier() {
            @Override
            public ChatReplacement find(String text, int fromIndex, ChatProcessingContext context) {
                searches.incrementAndGet();
                return new ChatReplacement(0, 4, new ChatNode.Text("done", ChatStyle.EMPTY));
            }
        };

        ChatContent content = processor(broken).process("test original", context);

        assertEquals("done original", content.plainText());
        assertEquals(2, searches.get());
    }

    private ChatProcessor processor(ChatModifier modifier) {
        return new ChatProcessor(List.of(modifier, new UrlModifier()), new AdventureComponentImporter());
    }
}
