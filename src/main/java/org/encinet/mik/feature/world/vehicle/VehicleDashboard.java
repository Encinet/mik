package org.encinet.mik.feature.world.vehicle;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuContext;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuFeedbackKind;
import org.encinet.mik.shared.menu.FloatingMenuScreen;

import java.util.UUID;

final class VehicleDashboard {
    private record State(UUID vehicle, VehicleDashboardMenu.Page page, int index) { }
    private final LanguageService language;
    private final VehicleModule module;
    private final FloatingMenuScreen<State> screen;

    VehicleDashboard(LanguageService language, VehicleModule module) {
        this.language = language;
        this.module = module;
        screen = new FloatingMenuScreen<>("vehicle-dashboard", this::render);
    }

    void open(Player player, UUID vehicle) {
        screen.open(player, new State(vehicle, VehicleDashboardMenu.Page.DRIVE, 0));
    }

    private MenuScene render(FloatingMenuContext<State> context) {
        Player player = context.player();
        State state = context.state();
        VehicleDashboardMenu.View view;
        try { view = module.dashboardView(player, state.vehicle()); }
        catch (IllegalArgumentException exception) {
            var menu = MenuScene.screen("vehicle-dashboard")
                    .content(org.encinet.mik.shared.menu.ui.UiTemplates.information("status"));
            menu.information("unavailable", language.text(player, Message.VEHICLE_DASH_UNAVAILABLE, NamedTextColor.RED)).region("status");
            return menu.refreshEvery(1, (actor, _) -> { if (current(actor, state)) forget(actor.getUniqueId()); }).build();
        }
        var menu = VehicleDashboardMenu.create(view, state.page(), state.index(), (message, arguments) -> language.text(player, message, NamedTextColor.AQUA, arguments),
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
                    if (current(player, state)) context.setState(new State(state.vehicle(), page, 0));
                }, index -> {
                    if (current(player, state)) context.setState(new State(state.vehicle(), state.page(), index));
                }, (actor, visible) -> {
                    if (!current(actor, state)) return;
                    try { module.setHudVisible(actor, state.vehicle(), visible); context.redraw(); }
                    catch (IllegalArgumentException exception) { forget(actor.getUniqueId()); }
                })
                .refreshEvery(state.page() == VehicleDashboardMenu.Page.HELP ? 20 : state.page() == VehicleDashboardMenu.Page.DETAILS ? 10 : 4, (actor, _) -> {
                    if (current(actor, state)) context.redraw();
                });
        menu.navigation("workspace", language.text(player, Message.BACK, NamedTextColor.AQUA)).region("navigation")
                .primary((actor, handle) -> { if (current(actor, state)) handle.back(); });
        return menu.build();
    }

    private void requireCurrent(Player player, State state) {
        if (!current(player, state)) throw new IllegalArgumentException("Dashboard session changed");
    }
    private boolean current(Player player, State state) { return screen.isCurrent(player, state); }
    void forget(UUID player) { screen.forget(player); }
    void close() { screen.forgetAll(); }
}
