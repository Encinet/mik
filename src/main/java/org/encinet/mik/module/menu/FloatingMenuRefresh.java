package org.encinet.mik.module.menu;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Function;

/**
 * Declarative invalidation policy for live menu content. A revision supplier
 * prevents entity metadata from being resent when the observed state is equal.
 */
public record FloatingMenuRefresh(
        int intervalTicks,
        Function<Player, ?> revision,
        FloatingMenuCommand action) {

    public static final FloatingMenuRefresh NONE =
            new FloatingMenuRefresh(0, null, null);

    public FloatingMenuRefresh {
        if (intervalTicks < 0) {
            throw new IllegalArgumentException("Refresh interval must not be negative");
        }
        if (intervalTicks == 0) {
            if (revision != null || action != null) {
                throw new IllegalArgumentException("Disabled refresh cannot own callbacks");
            }
        } else {
            action = Objects.requireNonNull(action, "action");
        }
    }

    public static FloatingMenuRefresh every(int intervalTicks, FloatingMenuCommand action) {
        return new FloatingMenuRefresh(requirePositive(intervalTicks), null,
                Objects.requireNonNull(action, "action"));
    }

    public static FloatingMenuRefresh whenChanged(
            int intervalTicks, Function<Player, ?> revision,
            FloatingMenuCommand action) {
        return new FloatingMenuRefresh(requirePositive(intervalTicks),
                Objects.requireNonNull(revision, "revision"),
                Objects.requireNonNull(action, "action"));
    }

    public boolean enabled() {
        return intervalTicks > 0;
    }

    public Object revision(Player player) {
        return revision == null ? null : revision.apply(player);
    }

    private static int requirePositive(int intervalTicks) {
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("Refresh interval must be positive");
        }
        return intervalTicks;
    }
}
