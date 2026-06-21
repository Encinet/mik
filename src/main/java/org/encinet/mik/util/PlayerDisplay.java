package org.encinet.mik.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

public final class PlayerDisplay {

    private static final Key PLAYER_ENTITY_TYPE = Key.key("minecraft", "player");

    private PlayerDisplay() {
    }

    public static Component name(Player player) {
        return name(player, null);
    }

    public static Component name(Player player, TextColor fallbackColor) {
        Component displayName = player.displayName();
        Component visibleName = fallbackColor != null ? displayName.colorIfAbsent(fallbackColor) : displayName;
        return visibleName
                .hoverEvent(HoverEvent.showEntity(
                        PLAYER_ENTITY_TYPE,
                        player.getUniqueId(),
                        displayName));
    }
}
