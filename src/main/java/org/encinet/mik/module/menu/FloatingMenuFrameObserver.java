package org.encinet.mik.module.menu;

import org.bukkit.entity.Player;

/** Observes the exact server instant at which a decoration teleport is sent. */
@FunctionalInterface
public interface FloatingMenuFrameObserver {
    FloatingMenuFrameObserver NONE = (player, decorationId, sentAtNanos) -> { };

    void presented(Player player, String decorationId, long sentAtNanos);
}
