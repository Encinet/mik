package org.encinet.mik.module.menu;

import org.encinet.mik.module.menu.runtime.FloatingMenuService;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.function.Consumer;

/** Application-wide entry point used by menu modules. */
public final class FloatingMenus {
    private static FloatingMenuService service;

    private FloatingMenus() {
    }

    public static void install(FloatingMenuService menuService) {
        service = menuService;
    }

    public static void uninstall() {
        FloatingMenuService installed = service;
        service = null;
        if (installed != null) installed.closeAllImmediately();
    }

    public static FloatingMenuHandle open(Player player, FloatingMenuDefinition definition) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.open(player, definition);
    }

    /** Replaces any existing navigation hierarchy and establishes a new root screen. */
    public static FloatingMenuHandle openRoot(Player player, FloatingMenuDefinition definition) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.openRoot(player, definition);
    }

    /** Updates an equal logical screen in place, otherwise opens it as a child screen. */
    public static FloatingMenuHandle present(Player player, FloatingMenuDefinition definition) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.present(player, definition);
    }

    public static Optional<FloatingMenuHandle> current(Player player) {
        return service == null ? Optional.empty() : service.current(player);
    }

    public static boolean supportsSpatialScenes(Player player) {
        return service != null && service.supportsSpatialScenes(player);
    }

    public static FloatingMenuScale scale(Player player) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.scale(player);
    }

    public static FloatingMenuPreferences preferences(Player player) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.preferences(player);
    }

    public static void openSettings(Player player) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        service.openSettings(player);
    }

    static void execute(Runnable task) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        service.execute(task);
    }

    public static void setMainMenuOpener(Consumer<Player> opener) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        service.setMainMenuOpener(opener);
    }

    public static void clearMainMenuOpener() {
        if (service != null) service.clearMainMenuOpener();
    }
}
