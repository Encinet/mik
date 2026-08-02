package org.encinet.mik.module.menu;

import org.bukkit.entity.Player;

import java.util.Objects;

/** Receives centralized screen lifecycle transitions. */
@FunctionalInterface
public interface FloatingMenuLifecycle {
    FloatingMenuLifecycle NONE = (player, handle, previous, current, reason) -> { };

    /** {@code reason} is populated on the terminal {@link FloatingMenuState#CLOSED} transition. */
    void changed(Player player, FloatingMenuHandle handle,
                 FloatingMenuState previous, FloatingMenuState current,
                 FloatingMenuCloseReason reason);

    static FloatingMenuLifecycle combine(FloatingMenuLifecycle first,
                                         FloatingMenuLifecycle second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first == NONE) return second;
        if (second == NONE) return first;
        return (player, handle, previous, current, reason) -> {
            first.changed(player, handle, previous, current, reason);
            second.changed(player, handle, previous, current, reason);
        };
    }
}
