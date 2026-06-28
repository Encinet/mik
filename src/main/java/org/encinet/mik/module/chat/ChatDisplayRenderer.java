package org.encinet.mik.module.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.Mik;
import org.encinet.mik.util.PlayerDisplay;

public final class ChatDisplayRenderer {

    private ChatDisplayRenderer() {
    }

    public static Component playerName(Player player) {
        return PlayerDisplay.name(player, usernameColor(player));
    }

    public static TextColor usernameColor(Player player) {
        return player.hasPermission("group." + Mik.GROUP_MEMBER) ? NamedTextColor.WHITE : NamedTextColor.YELLOW;
    }
}
