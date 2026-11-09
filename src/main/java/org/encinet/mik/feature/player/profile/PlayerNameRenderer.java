package org.encinet.mik.feature.player.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/** Builds the interactive player name shared by chat and identity previews. */
public final class PlayerNameRenderer {

    private PlayerNameRenderer() {
    }

    public static Component playerName(Player player) {
        return clickablePlayerName(
                player.displayName(),
                player.getName());
    }

    public static Component clickablePlayerName(Component name, String username) {
        return name.clickEvent(ClickEvent.suggestCommand("/tell " + username + " "));
    }

    /** Apply the effective account color without changing name interactions or decorations. */
    public static Component withColor(Component name, TextColor color) {
        return name.color(color).children(name.children().stream()
                .map(child -> withColor(child, color)).toList());
    }
}
