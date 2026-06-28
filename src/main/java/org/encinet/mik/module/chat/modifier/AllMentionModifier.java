package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.Mik;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AllMentionModifier implements ChatModifier {

    private static final Pattern ALL_PATTERN = Pattern.compile("(?i)@all");

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        if (!context.sender().hasPermission("group." + Mik.GROUP_MANAGER)) {
            return null;
        }
        Matcher matcher = ALL_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        return new ChatReplacement(matcher.start(), matcher.end(),
                ChatRenderUtil.allMention(context.allHover()));
    }
}
