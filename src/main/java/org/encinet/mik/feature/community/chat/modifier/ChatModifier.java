package org.encinet.mik.feature.community.chat.modifier;

import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;

import java.util.Set;

public interface ChatModifier {
    default int priority() {
        return 100;
    }

    default Set<ChatCapability> requiredCapabilities() {
        return Set.of();
    }

    default boolean supports(ChatProcessingContext context) {
        return context.capabilities().containsAll(requiredCapabilities());
    }

    ChatReplacement find(
            String text,
            int fromIndex,
            ChatProcessingContext context
    );
}
