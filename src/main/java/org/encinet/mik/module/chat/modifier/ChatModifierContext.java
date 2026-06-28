package org.encinet.mik.module.chat.modifier;

import org.bukkit.entity.Player;

import java.util.List;

public record ChatModifierContext(
        Player sender,
        List<Player> players,
        String itemEmptyHover,
        String allHover,
        String bilibiliHover
) {
}
