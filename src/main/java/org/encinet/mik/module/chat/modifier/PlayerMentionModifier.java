package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatProcessingContext;

import java.util.Locale;
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
        String lowerText = text.toLowerCase(Locale.ROOT);
        ChatReplacement best = null;
        for (ChatProcessingContext.PlayerReference player
                : context.mentionablePlayers()) {
            String lowerName = player.name().toLowerCase(Locale.ROOT);
            int index = lowerText.indexOf(lowerName + '@', fromIndex);
            if (index < 0) {
                continue;
            }
            int start = index;
            int end = index + player.name().length() + 1;
            if (!hasBoundaryBefore(text, start)
                    || !hasBoundaryAfter(text, end)) {
                continue;
            }
            if (best == null || start < best.start()
                    || (start == best.start() && end - start > best.end() - best.start())) {
                best = ChatReplacement.padded(start, end,
                        new ChatNode.PlayerMention(player.id(), player.name(),
                                ChatSemanticStyles.MENTION));
            }
        }
        return best;
    }

    private boolean hasBoundaryBefore(String text, int index) {
        if (index <= 0) {
            return true;
        }
        return !isNameCharacter(text.codePointBefore(index));
    }

    private boolean hasBoundaryAfter(String text, int index) {
        if (index >= text.length()) {
            return true;
        }
        return !isNameCharacter(text.codePointAt(index));
    }

    private boolean isNameCharacter(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetterOrDigit(codePoint) || codePoint == '_'
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }
}
