package org.encinet.mik.feature.community.chat.parse;

import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;

import java.util.Set;
import java.util.function.IntFunction;

interface ChatModifier {
    default Set<ChatCapability> requiredCapabilities() {
        return Set.of();
    }

    default boolean supports(ChatProcessingContext context) {
        return context.capabilities().containsAll(requiredCapabilities());
    }

    /**
     * Opens a search tied to one input and context. For increasing offsets it returns the earliest
     * replacement starting at or after the offset, or null when there are no more matches.
     */
    IntFunction<ChatReplacement> search(String text, ChatProcessingContext context);
}
