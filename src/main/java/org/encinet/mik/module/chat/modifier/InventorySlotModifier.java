package org.encinet.mik.module.chat.modifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InventorySlotModifier implements ChatModifier {

    // Matches %1 through %36 (inventory slot indices 0-35)
    private static final Pattern SLOT_PATTERN = Pattern.compile("%(?:[1-9]|[1-2]\\d|3[0-6])(?!\\d)");

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        Matcher matcher = SLOT_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        int slotIndex = Integer.parseInt(matcher.group().substring(1)) - 1;
        return new ChatReplacement(matcher.start(), matcher.end(),
                ChatRenderUtil.inventorySlot(context.sender(), slotIndex, context.itemEmptyHover()));
    }
}
