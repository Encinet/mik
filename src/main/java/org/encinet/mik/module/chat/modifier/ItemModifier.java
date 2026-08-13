package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatItemSnapshot;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatProcessingContext;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemModifier implements ChatModifier {

    private static final Pattern ITEM_PATTERN = Pattern.compile("(?i)(?:\\[(?:item|i)]|%i)");

    @Override
    public int priority() {
        return 70;
    }

    @Override
    public Set<ChatCapability> requiredCapabilities() {
        return Set.of(ChatCapability.ITEM);
    }

    @Override
    public ChatReplacement find(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        Matcher matcher = ITEM_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        return new ChatReplacement(matcher.start(), matcher.end(), item(
                context.mainHand()));
    }

    private ChatNode.Item item(Optional<ChatItemSnapshot> snapshot) {
        return snapshot.map(item -> ChatNode.Item.snapshot(
                        item, ChatSemanticStyles.ITEM))
                .orElseGet(() -> new ChatNode.Item("[empty]", Optional.empty(),
                        ChatSemanticStyles.EMPTY_ITEM));
    }
}
