package org.encinet.mik.shared.menu;

import org.encinet.mik.shared.menu.runtime.FloatingMenuService;

import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;

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

    public static FloatingMenuHandle open(Player player, MenuScene definition) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.open(player, definition);
    }

    /** Replaces any existing navigation hierarchy and establishes a new root screen. */
    public static FloatingMenuHandle openRoot(Player player, MenuScene definition) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.openRoot(player, definition);
    }

    /** Updates an equal logical screen in place, otherwise opens it as a child screen. */
    public static FloatingMenuHandle present(Player player, MenuScene definition) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.present(player, definition);
    }

    public static Optional<FloatingMenuHandle> current(Player player) {
        return service == null ? Optional.empty() : service.current(player);
    }

    /** Main-thread snapshot for small world effects: panels, popovers and tooltips keep their reading space. */
    public static Predicate<Location> worldOverlayVisibility(Player player) {
        return service == null ? _ -> true : service.worldOverlayVisibility(player);
    }

    public static MenuInputLease beginConfirmation(Player player) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.beginConfirmation(player);
    }

    /** Owns a native dialog until its action, cancellation or originating page ends. */
    public static MenuInputLease beginConfirmation(Player player, Runnable ended) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.beginConfirmation(player, ended);
    }

    /** Receives chat while its view remains active, without a timer; preview delivery stays on the server thread. */
    public static MenuInputLease beginChatInput(Player player, Consumer<String> text, Runnable ended) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.beginChatInput(player, text, ended);
    }

    public static FloatingMenuScale scale(Player player) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.scale(player);
    }

    /** Opens a spatial form whose fields are edited in native dialogs. */
    public static void input(Player player, MenuInputForm form) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        service.input(player, form);
    }

    /** Shares form drafts and native field editing while the host keeps its navigation and content visible. */
    public static MenuFormSession inlineInput(Player player, MenuInputForm form, Runnable changed, Runnable finished) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.inlineInput(player, form, changed, finished);
    }

    /** A compact query submitted by one chat message. Update applies and redraws; redraw reflects focus or errors. */
    public static MenuQuerySession queryInput(Player player, Component placeholder, int maximumLength,
                                              Consumer<String> update, Runnable redraw) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.queryInput(player, placeholder, maximumLength, update, redraw);
    }

    /** Searches in place; a result must be clicked before its selection callback is delivered. */
    public static <T> void search(Player player, MenuSearch<T> search) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        service.search(player, search);
    }

    public static FloatingMenuPreferences preferences(Player player) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.preferences(player);
    }

    public static void openSettings(Player player) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        service.openSettings(player);
    }

    /** Embeds interface preferences while retaining the host's navigation and inline form drafts. */
    public static MenuPanel settingsPanel(Player player, Runnable redraw) {
        if (service == null) throw new IllegalStateException("Floating menu service is not installed");
        return service.settingsPanel(player, redraw);
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
