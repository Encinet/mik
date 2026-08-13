package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatItemSnapshot;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatProcessingContext;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InventorySlotModifier implements ChatModifier {

    // Matches %1 through %36 (inventory slot indices 0-35)
    private static final Pattern SLOT_PATTERN = Pattern.compile("%(?:[1-9]|[1-2]\\d|3[0-6])(?!\\d)");

    @Override
    public int priority() {
        return 80;
    }

    @Override
    public Set<ChatCapability> requiredCapabilities() {
        return Set.of(ChatCapability.INVENTORY);
    }

    @Override
    public ChatReplacement find(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        Matcher matcher = SLOT_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        int slotIndex = Integer.parseInt(matcher.group().substring(1)) - 1;
        Optional<ChatItemSnapshot> snapshot = Optional.ofNullable(
                context.inventory().get(slotIndex));
        ChatNode.Item item = snapshot.map(value -> ChatNode.Item.snapshot(
                        value, ChatSemanticStyles.ITEM))
                .orElseGet(() -> new ChatNode.Item("[empty]", Optional.empty(),
                        ChatSemanticStyles.EMPTY_ITEM));
        return new ChatReplacement(matcher.start(), matcher.end(), item);
    }
}
