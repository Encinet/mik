package org.encinet.mik.shared.menu;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface FloatingMenuAction {
    void execute(Player player, FloatingMenuHandle menu, FloatingMenuInteraction interaction);
}
