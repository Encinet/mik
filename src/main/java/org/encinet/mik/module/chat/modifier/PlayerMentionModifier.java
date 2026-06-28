package org.encinet.mik.module.chat.modifier;

import org.bukkit.entity.Player;

import java.util.Locale;

public final class PlayerMentionModifier implements ChatModifier {

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        ChatReplacement best = null;
        for (Player player : context.players()) {
            String lowerName = player.getName().toLowerCase(Locale.ROOT);
            int index = lowerText.indexOf(lowerName, fromIndex);
            if (index < 0) {
                continue;
            }
            if (best == null || index < best.start()
                    || (index == best.start() && player.getName().length() > best.end() - best.start())) {
                best = new ChatReplacement(index, index + player.getName().length(), ChatRenderUtil.mention(player));
            }
        }
        return best;
    }
}
