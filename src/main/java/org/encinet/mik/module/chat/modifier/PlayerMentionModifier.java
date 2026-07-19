package org.encinet.mik.module.chat.modifier;

import org.bukkit.entity.Player;

import java.util.Locale;

public final class PlayerMentionModifier implements ChatModifier {

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        ChatReplacement best = null;
        for (Player player : context.players()) {
            if (player.getUniqueId().equals(context.sender().getUniqueId())) {
                continue;
            }
            String lowerName = player.getName().toLowerCase(Locale.ROOT);
            int index = lowerText.indexOf(lowerName, fromIndex);
            if (index < 0) {
                continue;
            }
            int start = index > 0 && text.charAt(index - 1) == '@' ? index - 1 : index;
            int end = index + player.getName().length();
            if (best == null || start < best.start()
                    || (start == best.start() && end - start > best.end() - best.start())) {
                best = ChatReplacement.padded(start, end, ChatRenderUtil.mention(player));
            }
        }
        return best;
    }
}
