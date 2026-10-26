package org.encinet.mik.feature.community.chat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.player.guidance.Tip;
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.shared.i18n.Message;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Chat owns eligibility and usage state; the shared Tip owns delivery policy. */
final class ChatTips {
    private ChatTips() {}

    static Consumer<Player> privateChannel(Plugin plugin, TipScope tips, Predicate<Player> publicChannel) {
        Tip<Void> tip = tips.<Void>define("chat-private-channel", Message.CHAT_PRIVATE_TIP_CHANNEL_MM)
                .when(publicChannel)
                .build();
        NamespacedKey usesKey = new NamespacedKey(plugin, "chat_private_command_uses");
        return player -> {
            if (!publicChannel.test(player)) return;
            var data = player.getPersistentDataContainer();
            int uses = Math.clamp(data.getOrDefault(usesKey, PersistentDataType.BYTE, (byte) 0), 0, 2);
            if (uses < 2) data.set(usesKey, PersistentDataType.BYTE, (byte) ++uses);
            if (uses == 2) tip.offer(player);
        };
    }
}
