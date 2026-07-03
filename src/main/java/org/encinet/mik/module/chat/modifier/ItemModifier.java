package org.encinet.mik.module.chat.modifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemModifier implements ChatModifier {

    private static final Pattern ITEM_PATTERN = Pattern.compile("(?i)(?:\\[(?:item|i)]|%i)");

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        Matcher matcher = ITEM_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        return new ChatReplacement(matcher.start(), matcher.end(),
                ChatRenderUtil.item(context.sender(), context.itemEmptyHover()));
    }
}
