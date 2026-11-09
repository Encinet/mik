package org.encinet.mik.feature.world.vehicle;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuContext;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuFeedbackKind;
import org.encinet.mik.shared.menu.FloatingMenuScreen;
import org.encinet.mik.shared.menu.FloatingMenuState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class VehicleDashboard {
    private record State(UUID vehicle, UUID session, VehicleDashboardMenu.Page page) { }
    private final LanguageService language;
    private final VehicleModule module;
    private final Map<UUID, UUID> sessions = new HashMap<>();
    private final FloatingMenuScreen<State> screen;

    VehicleDashboard(LanguageService language, VehicleModule module) {
        this.language = language;
        this.module = module;
        screen = new FloatingMenuScreen<>("vehicle-dashboard", this::render);
    }

    void open(Player player, UUID vehicle, VehicleDashboardMenu.Page page) {
        module.dashboardView(player, vehicle);
        UUID session = UUID.randomUUID();
        sessions.put(player.getUniqueId(), session);
        try { screen.open(player, new State(vehicle, session, page)); }
        catch (RuntimeException exception) { sessions.remove(player.getUniqueId(), session); screen.forget(player); throw exception; }
    }

    private MenuScene render(FloatingMenuContext<State> context) {
        Player player = context.player();
        State state = context.state();
        VehicleDashboardMenu.View view;
        try { requireCurrent(player, state); view = module.dashboardView(player, state.vehicle()); }
        catch (IllegalArgumentException exception) {
            var menu = MenuScene.screen("vehicle-dashboard")
                    .content(org.encinet.mik.shared.menu.ui.UiTemplates.information("status"));
            menu.information("unavailable", language.text(player, Message.VEHICLE_DASH_UNAVAILABLE, NamedTextColor.RED)).region("status");
            return menu.refreshEvery(1, (actor, _) -> forget(actor.getUniqueId())).build();
        }
        var menu = VehicleDashboardMenu.create(view, state.page(), (message, arguments) -> language.text(player, message, NamedTextColor.AQUA, arguments),
                (actor, command) -> {
                    try {
                        requireCurrent(actor, state);
                        module.drivingControl(actor, state.vehicle(), command);
                        context.redraw();
                    } catch (IllegalArgumentException exception) {
                        Message message = switch (exception.getMessage()) {
                            case "STOP_FIRST", "SHIFTING", "OVERREV", "MANUAL_ONLY", "GEAR_LIMIT", "NO_FUEL", "DISABLED" -> VehicleHud.statusMessage(exception.getMessage(), false);
                            default -> Message.VEHICLE_DASH_UNAVAILABLE;
                        };
                        context.feedback(language.text(actor, message, NamedTextColor.RED), FloatingMenuFeedbackKind.ERROR);
                        context.redraw();
                    }
                }, page -> {
                    try { requireCurrent(player, state); module.dashboardView(player, state.vehicle()); context.setState(new State(state.vehicle(), state.session(), page)); }
                    catch (IllegalArgumentException exception) { forget(player.getUniqueId()); }
                })
                .refreshEvery(4, (actor, _) -> {
                    try { requireCurrent(actor, state); module.dashboardView(actor, state.vehicle()); context.redraw(); }
                    catch (IllegalArgumentException exception) { forget(actor.getUniqueId()); }
                })
                .lifecycle((actor, _, _, current, _) -> {
                    if (current == FloatingMenuState.CLOSED) sessions.remove(actor.getUniqueId(), state.session());
                });
        menu.navigation("workspace", language.text(player, Message.VEHICLE_MENU_LABEL, NamedTextColor.AQUA, "home")).region("navigation")
                .primary((actor, _) -> {
                    try { requireCurrent(actor, state); module.dashboardView(actor, state.vehicle()); module.openMenu(actor); }
                    catch (IllegalArgumentException exception) { forget(actor.getUniqueId()); }
                });
        return menu.build();
    }

    private void requireCurrent(Player player, State state) {
        if (!state.session().equals(sessions.get(player.getUniqueId()))) throw new IllegalArgumentException("Dashboard session changed");
    }
    void forget(UUID player) { sessions.remove(player); screen.forget(player); }
    void close() { for (UUID player : List.copyOf(sessions.keySet())) forget(player); }
}
