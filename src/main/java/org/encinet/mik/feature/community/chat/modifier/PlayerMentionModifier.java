package org.encinet.mik.feature.community.chat.modifier;

import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;

import java.util.Set;

public final class PlayerMentionModifier implements ChatModifier {

    @Override
    public int priority() {
        return 60;
    }

    @Override
    public Set<ChatCapability> requiredCapabilities() {
        return Set.of(ChatCapability.PLAYER_MENTION);
    }

    @Override
    public ChatReplacement find(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        var match = context.mentions().find(text, fromIndex);
        return match == null ? null : ChatReplacement.padded(match.start(), match.end(),
                new ChatNode.PlayerMention(match.target().id(), match.target().name(),
                        ChatSemanticStyles.MENTION));
    }
}
