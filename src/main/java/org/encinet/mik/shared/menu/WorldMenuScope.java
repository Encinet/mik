package org.encinet.mik.shared.menu;

import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.ui.UiKey;

/** Main-thread ownership of private world panels; closing the feature releases all its viewers and displays. */
public interface WorldMenuScope extends AutoCloseable {
    /** Reconciles a stable panel slot. The predicate revalidates its domain source before input is delivered. */
    FloatingMenuHandle show(Player viewer, UiKey key, WorldMenuSurface surface, MenuScene scene, Predicate<Player> available);
    void clear(Player viewer, UiKey key);
    void clear(UUID viewer);
    @Override void close();
}
