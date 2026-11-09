package org.encinet.mik.feature.community.chat.modifier;

import org.encinet.mik.feature.community.chat.mention.MentionBoundaries;
import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AllMentionModifier implements ChatModifier {

    private static final Pattern ALL_PATTERN = Pattern.compile("(?iu)@all");

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
        Matcher matcher = ALL_PATTERN.matcher(text).region(fromIndex, text.length());
        while (matcher.find()) {
            if (MentionBoundaries.isStart(text, matcher.start())
                    && MentionBoundaries.isEnd(text, matcher.end())) {
                return ChatReplacement.padded(matcher.start(), matcher.end(),
                        new ChatNode.BroadcastMention(
                                "@all", ChatSemanticStyles.BROADCAST_MENTION));
            }
        }
        return null;
    }
}
