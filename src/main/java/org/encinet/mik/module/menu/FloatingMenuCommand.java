package org.encinet.mik.module.menu;

import org.bukkit.entity.Player;

/** Action for a trigger whose interaction kind is already known by declaration. */
@FunctionalInterface
public interface FloatingMenuCommand {
    void execute(Player player, FloatingMenuHandle menu);
}
