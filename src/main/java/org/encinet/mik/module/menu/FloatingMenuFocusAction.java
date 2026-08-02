package org.encinet.mik.module.menu;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface FloatingMenuFocusAction {
    void changed(Player player, FloatingMenuHandle menu, boolean focused);
}
