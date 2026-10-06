package org.encinet.mik.module.vehicle;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuContext;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuFeedbackKind;
import org.encinet.mik.module.menu.FloatingMenuScreen;
import org.encinet.mik.module.menu.FloatingMenuState;

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

    private FloatingMenuDefinition render(FloatingMenuContext<State> context) {
        Player player = context.player();
        State state = context.state();
        VehicleDashboardMenu.View view;
        try { requireCurrent(player, state); view = module.dashboardView(player, state.vehicle()); }
        catch (IllegalArgumentException exception) {
            var menu = FloatingMenuDefinition.screen("vehicle-dashboard");
            menu.information("unavailable", language.text(player, Message.VEHICLE_DASH_UNAVAILABLE, NamedTextColor.RED));
            return menu.refreshEvery(1, (actor, handle) -> forget(actor.getUniqueId())).build();
        }
        return VehicleDashboardMenu.create(view, state.page(), (message, arguments) -> language.text(player, message, NamedTextColor.AQUA, arguments),
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
                .refreshEvery(4, (actor, handle) -> {
                    try { requireCurrent(actor, state); module.dashboardView(actor, state.vehicle()); context.redraw(); }
                    catch (IllegalArgumentException exception) { forget(actor.getUniqueId()); }
                })
                .lifecycle((actor, handle, previous, current, reason) -> {
                    if (current == FloatingMenuState.CLOSED) sessions.remove(actor.getUniqueId(), state.session());
                }).build();
    }

    private void requireCurrent(Player player, State state) {
        if (!state.session().equals(sessions.get(player.getUniqueId()))) throw new IllegalArgumentException("Dashboard session changed");
    }
    void forget(UUID player) { sessions.remove(player); screen.forget(player); }
    void close() { for (UUID player : List.copyOf(sessions.keySet())) forget(player); }
}
