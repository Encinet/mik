package org.encinet.mik.feature.world.vehicle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** Feature workspace: navigation keeps identity; every mutation rechecks its exact source and permissions. */
final class VehicleMenu {
    enum Page { NEARBY, FLEET, VEHICLE, MAINTENANCE, DETAILS, SELECTION, DISPLAYS, DISPLAY }
    record State(UUID session, Page page, UUID target, int anchor, int actions) {
        State route(Page page, UUID target) { return new State(session, page, target, 0, 0); }
        State anchor(int value) { return new State(session, page, target, value, actions); }
        State actions(int value) { return new State(session, page, target, anchor, value); }
    }
    private record Pick(State state, long ready, long expires) { }
    @FunctionalInterface private interface PlayerAction { void run(Player player) throws IOException; }
    @FunctionalInterface private interface Operation { void run() throws IOException; }
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final VehicleModule module;
    private final FloatingMenuScreen<State> screen;
    private final Map<UUID, Pick> picks = new HashMap<>();
    private long tick;
    private boolean closed;

    VehicleMenu(JavaPlugin plugin, LanguageService language, VehicleModule module) {
        this.plugin = plugin; this.language = language; this.module = module;
        screen = new FloatingMenuScreen<>("vehicle-workspace", this::render);
    }
    void enable() { closed = false; }
    void open(Player player) {
        var state = new State(UUID.randomUUID(), Page.NEARBY, null, 0, 0);
        UUID mounted = module.mountedVehicle(player);
        picks.remove(player.getUniqueId());
        screen.open(player, mounted == null ? state : state.route(Page.VEHICLE, mounted));
    }
    void selection(Player player) {
        picks.remove(player.getUniqueId());
        screen.open(player, new State(UUID.randomUUID(), Page.SELECTION, null, 0, 0));
    }
    private void requireCurrent(Player player, State state) {
        if (closed || screen.state(player).orElse(null) != state) throw new IllegalArgumentException("Vehicle menu changed; reopen it");
        module.requireMenuAccess(player, player.hasPermission("mik.vehicle.use") ? "mik.vehicle.use" : "mik.vehicle.admin");
        if (state.page() == Page.SELECTION || state.page() == Page.DISPLAYS || state.page() == Page.DISPLAY)
            module.requireMenuAccess(player, "mik.vehicle.admin");
    }
    private MenuScene render(FloatingMenuContext<State> context) {
        var player = context.player(); var state = context.state();
        var menu = MenuScene.screen("vehicle-workspace").stableAnchor();
        var items = new ArrayList<VehicleEditorList.Item>();
        menu.information("heading", state.page() == Page.DETAILS ? text(player, Message.VEHICLE_DASH_DETAILS)
                : label(player, state.page() == Page.VEHICLE ? "vehicle" : state.page() == Page.MAINTENANCE ? "maintenance" : "home")).region("heading");
        try {
            requireCurrent(player, state);
            for (Page page : List.of(Page.NEARBY, Page.FLEET)) menu.navigation("section:" + page, label(player, page == Page.NEARBY ? "nearby" : "fleet"))
                    .region("sections").selected(state.page() == page).primary((actor, _) -> run(actor, state, () -> context.setState(state.route(page, null))));
            if (player.hasPermission("mik.vehicle.admin")) {
                menu.navigation("models", text(player, Message.VEHICLE_MODEL_LIBRARY)).region("sections")
                        .primary((actor, _) -> run(actor, state, () -> module.openModels(actor)));
                menu.navigation("selection", label(player, "selection")).region("sections").selected(state.page() == Page.SELECTION || state.page() == Page.DISPLAYS || state.page() == Page.DISPLAY)
                        .primary((actor, _) -> run(actor, state, () -> context.setState(state.route(Page.SELECTION, null))));
            }
            if (state.page() == Page.VEHICLE || state.page() == Page.MAINTENANCE) {
                var selected = module.menuTarget(player, state.target());
                menu.navigation("driving", label(player, "driving")).region("tabs").selected(state.page() == Page.VEHICLE)
                        .primary((actor, _) -> run(actor, state, () -> context.setState(state.route(Page.VEHICLE, selected.id))));
                if (module.manages(player, selected)) menu.navigation("maintenance", label(player, "maintenance")).region("tabs").selected(state.page() == Page.MAINTENANCE)
                        .primary((actor, _) -> run(actor, state, () -> context.setState(state.route(Page.MAINTENANCE, selected.id))));
            }
            switch (state.page()) {
                case NEARBY, FLEET -> {
                    menu.information("summary", label(player, state.page() == Page.NEARBY ? "nearby" : "fleet")).region("summary");
                    for (var instance : module.vehicles(player)) {
                        if (state.page() == Page.NEARBY && !module.nearby(player, instance)) continue;
                        var body = instance.body;
                        Component row = Component.text(body.definition.id() + " · " + instance.id.toString().substring(0, 8) + " · ")
                                .append(label(player, instance.publicAccess ? "public" : "private"))
                                .append(Component.text(" · " + position(body.origin())));
                        items.add(new VehicleEditorList.Item(UiKey.item("vehicle:" + instance.id), row,
                                (actor, _) -> run(actor, state, () -> { module.menuTarget(actor, instance.id); context.setState(state.route(Page.VEHICLE, instance.id)); })));
                    }
                    boolean hudVisible = module.hudVisible(player);
                    control(menu, player, state, "hud", label(player, "hud"), actor -> module.toggleHud(actor, !hudVisible)).selected(hudVisible);
                    if (player.hasPermission("mik.vehicle.admin")) {
                        control(menu, player, state, "create", text(player, Message.VEHICLE_MODEL_CREATE), actor -> module.createModel(actor, VehicleModelEditor.Source.EMPTY));
                        control(menu, player, state, "reload", label(player, "reload"), actor -> confirm(actor, state, label(actor, "reload"), label(actor, "reload-hint"), () -> { module.reload(actor); context.redraw(); }));
                    }
                }
                case DETAILS -> details(menu, player, module.menuTarget(player, state.target()));
                case VEHICLE -> vehicle(menu, context, module.menuTarget(player, state.target()));
                case MAINTENANCE -> maintenance(menu, context, module.menuTarget(player, state.target()));
                case SELECTION -> {
                    var selected = module.selected(player);
                    Location origin = module.selectedOrigin(player);
                    menu.information("summary", language.text(player, Message.VEHICLE_SELECTION_INFO, NamedTextColor.AQUA, selected.ids().size(),
                            selected.root() == null ? "—" : selected.root().toString().substring(0, 8), origin.getX(), origin.getY(), origin.getZ())).region("summary");
                    control(menu, player, state, "origin", label(player, "origin"), actor -> { module.setOrigin(actor); context.redraw(); });
                    control(menu, player, state, "group", label(player, "group"), actor -> {
                        picks.put(actor.getUniqueId(), new Pick(state, tick + 6, tick + 600)); context.close();
                        actor.sendMessage(label(actor, "pick-hint"));
                    });
                    control(menu, player, state, "radius", label(player, "radius"), actor -> input(actor, state, label(actor, "radius"),
                            List.of(MenuInputForm.Field.text("radius", label(actor, "radius"), "4", 16)), values -> {
                                module.selectRadius(actor, Double.parseDouble(values.text("radius"))); context.redraw();
                            }));
                    control(menu, player, state, "displays", label(player, "displays"), _ -> context.setState(state.route(Page.DISPLAYS, null)));
                    control(menu, player, state, "clear", label(player, "clear"), actor -> { module.clearSelection(actor); context.redraw(); });
                    var capture = control(menu, player, state, "capture", label(player, "capture"), actor -> module.createModel(actor, VehicleModelEditor.Source.SELECTION));
                    if (selected.ids().isEmpty()) capture.disabled(text(player, Message.MENU_SEARCH_EMPTY));
                    control(menu, player, state, "import", text(player, Message.VEHICLE_MODEL_IMPORT), actor -> module.createModel(actor, VehicleModelEditor.Source.HAND));
                }
                case DISPLAYS -> {
                    menu.information("summary", label(player, "displays")).region("summary");
                    for (UUID id : module.selectableDisplayIds(player)) {
                        var display = module.selectableDisplay(player, id); var point = display.getLocation();
                        Component row = Component.text(display.getType().name() + " · " + id.toString().substring(0, 8)
                                + " · " + position(new VehicleVector(point.getX(), point.getY(), point.getZ())));
                        items.add(new VehicleEditorList.Item(UiKey.item("display:" + id), row, (actor, _) -> run(actor, state,
                                () -> context.setState(state.route(Page.DISPLAY, id)))));
                    }
                }
                case DISPLAY -> {
                    var display = module.selectableDisplay(player, state.target());
                    menu.information("summary", Component.text(display.getType().name() + " · " + display.getUniqueId())).region("summary");
                    control(menu, player, state, "toggle", label(player, "toggle-selection"), actor -> { module.selectEntity(actor, state.target()); context.setState(state.route(Page.DISPLAYS, null)); })
                            .selected(module.selected(player).ids().contains(state.target()));
                    control(menu, player, state, "group", label(player, "group"), actor -> { module.selectGroup(actor, state.target()); context.setState(state.route(Page.SELECTION, null)); });
                }
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            menu.information("unavailable", text(player, Message.VEHICLE_DASH_UNAVAILABLE)).region("summary");
        }
        if (state.page() != Page.NEARBY && state.page() != Page.FLEET) menu.navigation("back", text(player, Message.BACK)).region("navigation")
                .primary((actor, _) -> run(actor, state, () -> context.setState(state.route(state.page() == Page.DETAILS || state.page() == Page.MAINTENANCE ? Page.VEHICLE : state.page() == Page.DISPLAY ? Page.DISPLAYS : state.page() == Page.DISPLAYS ? Page.SELECTION : Page.NEARBY,
                        state.page() == Page.DETAILS || state.page() == Page.MAINTENANCE ? state.target() : null))));
        menu.dismiss(text(player, Message.CLOSE)).region("navigation");
        UiNode rows = VehicleEditorList.content(items, text(player, Message.MENU_SEARCH_EMPTY), state.anchor(),
                next -> run(player, state, () -> context.setState(state.anchor(next))));
        boolean listing = state.page() == Page.NEARBY || state.page() == Page.FLEET || state.page() == Page.DISPLAYS;
        var actions = new UiPagedPort(UiTemplates.actions("actions", 2).withSummaryLines(1), 2, state.actions(),
                next -> run(player, state, () -> context.setState(state.actions(next)))).withSizing(new UiSizing(UiSize.ZERO, new UiSize(560, 240)));
        if (state.page() == Page.DETAILS) rows = new UiPagedPort(UiTemplates.list("readings").withSummaryLines(2), 3,
                state.anchor(), next -> run(player, state, () -> context.setState(state.anchor(next)))).withSizing(new UiSizing(UiSize.ZERO, new UiSize(560, 240)));
        UiNode content = UiTemplates.menu(UiTemplates.title("heading"), UiTemplates.information("summary").withSummaryLines(4),
                UiTemplates.navigation("tabs"), (listing || state.page() == Page.DETAILS) ? rows : actions);
        UiNode footer = listing ? UiTemplates.menu(actions, UiTemplates.navigation("navigation")) : UiTemplates.navigation("navigation");
        menu.content(UiTemplates.split(UiTemplates.sidebar("sections", UiTemplates.list("sections").withSummaryLines(1)),
                UiTemplates.sheet(UiTemplates.window("main", content), UiTemplates.toolbar("footer", footer))));
        return menu.refreshEvery(20, (actor, _) -> {
            try { requireCurrent(actor, state); context.redraw(); }
            catch (IllegalArgumentException | IllegalStateException exception) { forget(actor.getUniqueId()); }
        }).build();
    }

    private void vehicle(MenuScene.Builder menu, FloatingMenuContext<State> context, VehicleInstance instance) {
        var player = context.player(); var state = context.state(); var body = instance.body;
        vehicleSummary(menu, player, instance);
        control(menu, player, state, "details", text(player, Message.VEHICLE_DASH_DETAILS), _ -> context.setState(state.route(Page.DETAILS, instance.id)));
        boolean use = player.hasPermission("mik.vehicle.use"); boolean nearby = module.nearby(player, instance);
        if (instance.id.equals(module.mountedVehicle(player))) {
            control(menu, player, state, "leave", label(player, "leave"), actor -> { module.requireSame(instance); if (!instance.id.equals(module.mountedVehicle(actor))) throw new IllegalArgumentException("Seat changed; reopen the menu"); module.leave(actor); context.redraw(); });
            if (module.driving(player, instance)) {
                for (var page : VehicleDashboardMenu.Page.values()) {
                    if (page == VehicleDashboardMenu.Page.GEARBOX && body.definition.kind() != VehicleDefinition.Kind.CAR) continue;
                    Message message = switch (page) { case DRIVE -> Message.VEHICLE_DASH_DRIVE; case GEARBOX -> Message.VEHICLE_DASH_GEARBOX; case DETAILS -> Message.VEHICLE_DASH_DETAILS; };
                    control(menu, player, state, "dashboard:" + page, text(player, message), actor -> { module.requireSame(instance); module.dashboardView(actor, instance.id); module.openDashboard(actor, page); });
                }
                control(menu, player, state, "engine", text(player, body.engineRunning ? Message.VEHICLE_DASH_ENGINE_STOP : Message.VEHICLE_DASH_ENGINE_START), actor -> { module.drivingControl(actor, instance.id, VehicleControls.Toggle.ENGINE); context.redraw(); });
                control(menu, player, state, "hud", label(player, "hud"), actor -> { module.dashboardView(actor, instance.id); module.toggleHud(actor, !module.hudVisible(actor)); }).selected(module.hudVisible(player));
            }
        } else {
            var drive = control(menu, player, state, "drive", text(player, Message.VEHICLE_DASH_DRIVE), actor -> module.enter(actor, instance, true));
            var passenger = control(menu, player, state, "passenger", label(player, "passenger"), actor -> { module.enter(actor, instance, false); context.redraw(); });
            if (!use || !nearby || module.mountedVehicle(player) != null) {
                Component reason = label(player, use ? "nearby-hint" : "use-required"); drive.disabled(reason); passenger.disabled(reason);
            }
        }
    }

    private void vehicleSummary(MenuScene.Builder menu, Player player, VehicleInstance instance) {
        var body = instance.body;
        int occupied = instance.entities == null ? 0 : instance.entities.passengerCount();
        menu.information("summary", Component.text(body.definition.id() + " · " + instance.id.toString().substring(0, 8))
                .append(Component.text(" · ")).append(label(player, instance.publicAccess ? "public-status" : "private-status"))
                .append(Component.newline()).append(language.text(player, Message.VEHICLE_DASH_FUEL, NamedTextColor.AQUA, body.fuel, body.definition.fuelCapacity(), Math.round(body.fuel / body.definition.fuelCapacity() * 100)))
                .append(Component.newline()).append(language.text(player, Message.VEHICLE_DASH_HEALTH, NamedTextColor.AQUA, Math.round(body.health), occupied, body.definition.seats().size()))
                .append(Component.newline()).append(Component.text(position(body.origin()) + " · " + module.worldName(instance.worldId)))).region("summary");
    }

    private void maintenance(MenuScene.Builder menu, FloatingMenuContext<State> context, VehicleInstance instance) {
        var player = context.player(); var state = context.state(); var body = instance.body;
        vehicleSummary(menu, player, instance);
        boolean nearby = module.nearby(player, instance);
        int occupied = instance.entities == null ? 0 : instance.entities.passengerCount();
        if (module.manages(player, instance)) {
            for (boolean fuel : List.of(true, false)) {
                var button = control(menu, player, state, fuel ? "fuel" : "repair", label(player, fuel ? "fuel" : "repair"), actor -> {
                    module.requireSame(instance); module.service(actor, instance.id, fuel); context.redraw();
                });
                if (!nearby || body.engineRunning || body.velocity.length() > 0.5) button.disabled(label(player, "service-hint"));
            }
            boolean requestedAccess = !instance.publicAccess;
            control(menu, player, state, "access", label(player, instance.publicAccess ? "private" : "public"), actor -> {
                module.requireSame(instance); module.setAccess(actor, instance.id, requestedAccess); context.redraw();
            });
            var remove = control(menu, player, state, "remove", label(player, "remove"), actor -> confirm(actor, state, label(actor, "remove"),
                    Component.text(body.definition.id() + " · " + instance.id), () -> { module.requireSame(instance); module.remove(actor, instance); context.setState(state.route(Page.NEARBY, null)); }));
            if (occupied > 0 || body.velocity.length() > 0.5) remove.disabled(label(player, "remove-hint"));
        }
    }

    private void details(MenuScene.Builder menu, Player player, VehicleInstance instance) {
        var frame = VehicleHud.Frame.of(instance.body);
        menu.information("summary", Component.text(instance.body.definition.id())).region("summary");
        Object[][] readings = {
                {Message.VEHICLE_DASH_SPEED, Math.round(frame.speed() * 36) / 10.0, Math.round(frame.maximumSpeed() * 36) / 10.0},
                {Message.VEHICLE_DASH_GEAR, frame.gear()},
                {Message.VEHICLE_DASH_RPM, Math.round(frame.rpm()), Math.round(frame.energy().redline())},
                {Message.VEHICLE_DASH_FUEL, frame.energy().amount(), frame.energy().capacity(), Math.round(frame.fuel() * 100)},
                {Message.VEHICLE_DASH_HEALTH, Math.round(frame.health()), instance.entities == null ? 0 : instance.entities.passengerCount(), instance.body.definition.seats().size()},
                {Message.VEHICLE_DASH_POSITION, frame.origin().coordinateX(), frame.origin().coordinateY(), frame.origin().coordinateZ()},
                {Message.VEHICLE_DASH_HEADING, Math.round(frame.heading())}
        };
        for (int index = 0; index < readings.length; index++) {
            var row = readings[index];
            menu.information("reading:" + index, language.text(player, (Message) row[0], NamedTextColor.AQUA,
                    java.util.Arrays.copyOfRange(row, 1, row.length))).region("readings");
        }
    }

    private MenuScene.NodeBuilder control(MenuScene.Builder menu, Player player, State state, String id, Component label, PlayerAction action) {
        return menu.control(id, label).region("actions").primary((actor, _) -> run(actor, state, () -> action.run(actor)));
    }
    private void confirm(Player player, State state, Component title, Component detail, Operation action) {
        MenuDialogs.openConfirm(plugin, player, title, detail, text(player, Message.VEHICLE_MODEL_APPLY), text(player, Message.CLOSE), actor -> run(actor, state, action));
    }
    private void input(Player player, State state, Component title, List<MenuInputForm.Field> fields, Consumer<MenuInputForm.Values> action) {
        var builder = MenuInputForm.builder(title).cancel(text(player, Message.CLOSE)).validate((actor, values) -> {
            try { requireCurrent(actor, state); double radius = Double.parseDouble(values.text("radius")); VehicleDefinition.positive(radius, "radius", 16); return null; }
            catch (IllegalArgumentException | IllegalStateException error) { return Component.text(error.getMessage(), NamedTextColor.RED); }
        }).submit(text(player, Message.VEHICLE_MODEL_APPLY), (actor, values) -> run(actor, state, () -> action.accept(values)));
        fields.forEach(builder::field); FloatingMenus.input(player, builder.build());
    }
    boolean place(Player player) {
        var pick = picks.get(player.getUniqueId()); if (pick == null) return false;
        if (tick < pick.ready()) return true;
        picks.remove(player.getUniqueId());
        if (player.isSneaking() || tick >= pick.expires()) { player.sendMessage(text(player, Message.VEHICLE_MODEL_PLACE_CANCELLED)); return true; }
        run(player, pick.state(), () -> { module.selectLookGroup(player); screen.open(player, pick.state()); }); return true;
    }
    void tick() { tick++; picks.entrySet().removeIf(entry -> entry.getValue().expires() <= tick); }
    void cancelPick(UUID player) { picks.remove(player); }
    void forget(UUID player) { picks.remove(player); screen.forget(player); }
    void close() { closed = true; picks.clear(); screen.forgetAll(); }
    private void run(Player player, State state, Operation operation) {
        try { requireCurrent(player, state); operation.run(); }
        catch (IllegalArgumentException | IllegalStateException error) { player.sendMessage(language.text(player, Message.VEHICLE_ERROR, NamedTextColor.RED,
                error instanceof VehicleImportException imported ? imported.describe(language, player) : error.getMessage())); }
        catch (IOException error) { module.storageFailure(player, error); }
    }
    private Component label(Player player, String key) { return language.text(player, Message.VEHICLE_MENU_LABEL, NamedTextColor.AQUA, key); }
    private Component text(Player player, Message key) { return language.text(player, key, NamedTextColor.AQUA); }
    private static String position(VehicleVector point) { return String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f", point.coordinateX(), point.coordinateY(), point.coordinateZ()); }
}
