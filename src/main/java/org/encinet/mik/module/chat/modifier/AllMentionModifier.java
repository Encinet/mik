package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatProcessingContext;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AllMentionModifier implements ChatModifier {

    private static final Pattern ALL_PATTERN = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_])@all(?![\\p{L}\\p{N}_])");

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public Set<ChatCapability> requiredCapabilities() {
        return Set.of(ChatCapability.BROADCAST_MENTION);
    }

    @Override
    public ChatReplacement find(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        Matcher matcher = ALL_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        return ChatReplacement.padded(matcher.start(), matcher.end(),
                new ChatNode.BroadcastMention(
                        "@all", ChatSemanticStyles.BROADCAST_MENTION));
    }
}
