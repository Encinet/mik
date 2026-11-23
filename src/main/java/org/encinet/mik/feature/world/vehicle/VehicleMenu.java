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
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** Feature workspace: navigation keeps identity; every mutation rechecks its exact source and permissions. */
final class VehicleMenu {
    private enum Page {
        NEARBY("nearby"), FLEET("fleet"), VEHICLE("vehicle"), MAINTENANCE("maintenance"),
        SELECTION("selection"), DISPLAYS("displays"), DISPLAY("display");

        final String label;
        Page(String label) { this.label = label; }
        boolean browsing() { return this == NEARBY || this == FLEET; }
        boolean capture() { return this == SELECTION || this == DISPLAYS || this == DISPLAY; }
    }
    private record State(Page page, UUID target, int anchor, State parent) {
        static State root(Page page) { return new State(page, null, 0, null); }
        State open(Page page, UUID target) { return new State(page, target, 0, this); }
        State anchor(int value) { return new State(page, target, value, parent); }
        State resumed() { return anchor(anchor); }
    }
    private record Pick(State state, MenuInputLease lease, long ready, long expires) { }
    @FunctionalInterface private interface PlayerAction { void run(Player player) throws IOException; }
    @FunctionalInterface private interface Operation { void run() throws IOException; }
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final VehicleModule module;
    private final FloatingMenuScreen<State> screen;
    private final FloatingMenuScreen<State> captureScreen;
    private final Map<UUID, Pick> picks = new HashMap<>();
    private long tick;
    private boolean closed;

    VehicleMenu(JavaPlugin plugin, LanguageService language, VehicleModule module) {
        this.plugin = plugin; this.language = language; this.module = module;
        screen = new FloatingMenuScreen<>("vehicle-workspace", this::render);
        captureScreen = new FloatingMenuScreen<>("vehicle-capture", this::render);
    }
    void enable() { closed = false; }
    void open(Player player) {
        var state = State.root(Page.NEARBY);
        UUID mounted = module.mountedVehicle(player);
        cancelPick(player.getUniqueId());
        screen.open(player, mounted == null ? state : state.open(Page.VEHICLE, mounted));
    }
    void openCreated(Player player, UUID vehicle) {
        module.menuTarget(player, vehicle);
        cancelPick(player.getUniqueId());
        screen.open(player, State.root(Page.FLEET).open(Page.VEHICLE, vehicle));
    }
    void selection(Player player) {
        cancelPick(player.getUniqueId());
        captureScreen.open(player, State.root(Page.SELECTION));
    }
    private boolean isCurrent(Player player, State state) {
        var owner = state.page().capture() ? captureScreen : screen;
        return !closed && owner.isCurrent(player, state);
    }
    private void requireAccess(Player player, State state) {
        module.requireMenuAccess(player, state.page().capture() || !player.hasPermission("mik.vehicle.use")
                ? "mik.vehicle.admin" : "mik.vehicle.use");
    }
    private void requireCurrent(Player player, State state) {
        if (!isCurrent(player, state)) throw new IllegalArgumentException("Vehicle menu changed; reopen it");
        requireAccess(player, state);
    }
    private MenuScene render(FloatingMenuContext<State> context) {
        var player = context.player();
        var state = context.state();
        var menu = MenuScene.screen(state.page().capture() ? captureScreen.id() : screen.id()).stableAnchor();
        UUID mounted = module.mountedVehicle(player);
        if (mounted != null) menu.anchorMode(FloatingMenuAnchorMode.FOLLOW_PLAYER_POSITION);
        UiNode rows = null;
        menu.information("heading", label(player, state.page().browsing() ? "home" : state.page().label)).region("heading");
        try {
            requireAccess(player, state);
            switch (state.page()) {
                case NEARBY, FLEET -> rows = vehicles(menu, context, mounted);
                case VEHICLE -> vehicle(menu, context, module.menuTarget(player, state.target()), mounted);
                case MAINTENANCE -> maintenance(menu, context, module.menuTarget(player, state.target()));
                case SELECTION -> capture(menu, context);
                case DISPLAYS -> rows = displays(context);
                case DISPLAY -> display(menu, context);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            menu.information("unavailable", text(player, Message.VEHICLE_DASH_UNAVAILABLE)).region("summary");
        }
        if (state.parent() != null) menu.navigation("back", text(player, Message.BACK)).region("navigation")
                .primary((actor, _) -> run(actor, state, () -> context.setState(state.parent().resumed())));
        menu.dismiss(text(player, state.page() == Page.SELECTION ? Message.BACK : Message.CLOSE)).region("navigation");
        UiNode reading = rows == null ? UiTemplates.information("summary").withSummaryLines(4) : rows;
        UiNode content = UiTemplates.reading(reading.key(), UiTemplates.title("heading"), UiTemplates.navigation("tabs"), reading);
        UiNode footer = UiTemplates.menu(UiTemplates.contentActions("primary", 2, 1), UiTemplates.contentActions("secondary", 3, 2),
                UiTemplates.navigation("navigation"));
        menu.content(UiTemplates.sheet(UiTemplates.window("main", content), UiTemplates.toolbar("footer", footer)));
        if (state.page() != Page.SELECTION && state.page() != Page.DISPLAY) menu.refreshEvery(state.page().browsing() || state.page() == Page.DISPLAYS ? 40 : 20, (actor, _) -> {
            if (!isCurrent(actor, state)) return;
            try { requireAccess(actor, state); context.redraw(); }
            catch (IllegalArgumentException | IllegalStateException exception) { forget(actor.getUniqueId()); }
        });
        return menu.build();
    }

    private UiNode vehicles(MenuScene.Builder menu, FloatingMenuContext<State> context, UUID mounted) {
        var player = context.player();
        var state = context.state();
        for (Page page : List.of(Page.NEARBY, Page.FLEET)) {
            menu.navigation("section:" + page, label(player, page.label)).region("tabs").selected(state.page() == page)
                    .primary((actor, _) -> run(actor, state, () -> {
                        if (state.page() != page) context.setState(State.root(page));
                    }));
        }
        if (mounted != null) control(menu, context, "current", "primary", label(player, "current"),
                actor -> openVehicle(actor, context, mounted));
        if (player.hasPermission("mik.vehicle.admin")) control(menu, context, "models", "secondary", text(player, Message.VEHICLE_MODEL_TITLE), module::openModels);
        boolean nearby = state.page() == Page.NEARBY;
        var rows = new ArrayList<VehicleEditorList.Item>();
        var publicLabel = label(player, "public-status");
        var privateLabel = label(player, "private-status");
        for (var listing : module.vehicles(player, nearby)) {
            var instance = listing.vehicle();
            UUID id = instance.id;
            var location = Double.isFinite(listing.distanceSquared())
                    ? language.text(player, Message.VEHICLE_MENU_DISTANCE, NamedTextColor.WHITE, Math.round(Math.sqrt(listing.distanceSquared())))
                    : Component.text(module.worldName(instance.worldId), NamedTextColor.WHITE);
            var row = identity(instance).append(Component.text(" · ")).append(location)
                    .append(Component.text(" · ")).append(instance.publicAccess ? publicLabel : privateLabel);
            rows.add(new VehicleEditorList.Item(UiKey.item("vehicle:" + id), row,
                    (actor, _) -> run(actor, state, () -> openVehicle(actor, context, id))));
        }
        return rows(context, rows, label(player, nearby ? "empty-nearby" : "empty-fleet"));
    }

    private void openVehicle(Player player, FloatingMenuContext<State> context, UUID id) {
        module.menuTarget(player, id);
        context.setState(context.state().open(Page.VEHICLE, id));
    }

    private void vehicle(MenuScene.Builder menu, FloatingMenuContext<State> context, VehicleInstance instance, UUID mounted) {
        var player = context.player();
        boolean onboard = instance.id.equals(mounted);
        boolean driver = onboard && module.driving(player, instance);
        vehicleSummary(menu, player, instance, onboard ? label(player, driver ? "driver-seat" : "passenger-seat") : null);
        if (onboard) {
            if (driver) control(menu, context, "dashboard", "primary", text(player, Message.VEHICLE_DASH_DRIVE), actor -> {
                module.requireSame(instance);
                module.openDashboard(actor, instance.id);
            }).keepAccessible();
            control(menu, context, "leave", "primary", label(player, "leave"), actor -> {
                module.requireSame(instance);
                if (!instance.id.equals(module.mountedVehicle(actor))) throw new IllegalArgumentException("Seat changed; reopen the menu");
                module.leave(actor);
                context.redraw();
            }).keepAccessible();
        } else {
            for (boolean drive : List.of(true, false)) {
                String key = drive ? "drive" : "passenger";
                var seat = control(menu, context, key, "primary", label(player, key), actor -> {
                    module.enter(actor, instance, drive);
                    context.redraw();
                }).keepAccessible();
                explain(player, seat, module.boardingBlock(player, instance, drive));
            }
        }
        if (module.manages(player, instance)) control(menu, context, "maintenance", "secondary", label(player, "maintenance"),
                _ -> context.setState(context.state().open(Page.MAINTENANCE, instance.id)));
    }

    private static Component identity(VehicleInstance instance) {
        return Component.text(instance.body.definition.id() + " · " + shortId(instance.id), NamedTextColor.WHITE);
    }

    private void vehicleSummary(MenuScene.Builder menu, Player player, VehicleInstance instance, Component seat) {
        var body = instance.body;
        int occupied = instance.entities == null ? 0 : instance.entities.passengerCount();
        Component identity = identity(instance).append(Component.text(" · "))
                .append(language.text(player, Message.VEHICLE_MODEL_CHOICE, NamedTextColor.AQUA, body.definition.kind().name()))
                .append(Component.text(" · ")).append(label(player, instance.publicAccess ? "public-status" : "private-status"));
        if (seat != null) identity = identity.append(Component.text(" · ")).append(seat);
        menu.information("summary", identity.append(Component.newline())
                .append(language.text(player, Message.VEHICLE_DASH_FUEL, NamedTextColor.AQUA, tenth(body.fuel), tenth(body.definition.fuelCapacity()), Math.round(body.fuel / body.definition.fuelCapacity() * 100)))
                .append(Component.newline()).append(language.text(player, Message.VEHICLE_DASH_HEALTH, NamedTextColor.AQUA, Math.round(body.health), occupied, body.definition.seats().size()))
                .append(Component.newline()).append(Component.text(module.worldName(instance.worldId) + " · " + position(body.origin()), NamedTextColor.WHITE))).region("summary");
    }

    private void maintenance(MenuScene.Builder menu, FloatingMenuContext<State> context, VehicleInstance instance) {
        var player = context.player();
        var state = context.state();
        vehicleSummary(menu, player, instance, null);
        if (!module.manages(player, instance)) return;
        for (boolean fuel : List.of(true, false)) {
            String key = fuel ? "fuel" : "repair";
            var button = control(menu, context, key, "primary", label(player, key), actor -> {
                module.requireSame(instance);
                module.service(actor, instance.id, fuel);
                context.redraw();
            });
            explain(player, button, module.serviceBlock(player, instance, fuel));
        }
        boolean requestedAccess = !instance.publicAccess;
        control(menu, context, "access", "secondary", label(player, requestedAccess ? "public" : "private"), actor -> {
            module.requireSame(instance);
            module.setAccess(actor, instance.id, requestedAccess);
            context.redraw();
        });
        var remove = control(menu, context, "remove", "secondary", label(player, "remove"), actor -> confirm(actor, state, label(actor, "remove"),
                identity(instance).append(Component.newline()).append(label(actor, "remove-confirm")), () -> {
                    module.requireSame(instance);
                    module.remove(actor, instance);
                    context.setState(state.parent().parent().resumed());
                }));
        explain(player, remove, module.removalBlock(instance));
    }

    private void capture(MenuScene.Builder menu, FloatingMenuContext<State> context) {
        var player = context.player();
        var state = context.state();
        var selected = module.selected(player);
        Component originProblem = null;
        try {
            var origin = module.selectedOrigin(player);
            menu.information("summary", language.text(player, Message.VEHICLE_SELECTION_INFO, NamedTextColor.AQUA, selected.ids().size(),
                    selected.root() == null ? "—" : shortId(selected.root()), origin.getX(), origin.getY(), origin.getZ())).region("summary");
        } catch (IllegalArgumentException exception) {
            originProblem = problem(player, exception);
            menu.information("capture-problem", originProblem).region("summary");
        }
        var capture = control(menu, context, "capture", "primary", label(player, "capture"), actor -> module.createModel(actor, VehicleModelEditor.Source.SELECTION));
        if (module.hasModelDraft(player)) capture.disabled(label(player, "draft-open"));
        else if (originProblem != null) capture.disabled(originProblem);
        else if (selected.ids().isEmpty()) capture.disabled(label(player, "empty-selection"));
        control(menu, context, "origin", "secondary", label(player, "origin"), actor -> { module.setOrigin(actor); context.redraw(); });
        control(menu, context, "group", "secondary", label(player, "aim-group"), actor -> {
            cancelPick(actor.getUniqueId());
            picks.put(actor.getUniqueId(), new Pick(state, context.suspend(), tick + 6, tick + 600));
            actor.sendMessage(label(actor, "pick-hint"));
        });
        control(menu, context, "radius", "secondary", label(player, "radius"), actor -> input(actor, state, label(actor, "radius"),
                List.of(MenuInputForm.Field.text("radius", label(actor, "radius"), "4", 16)), values -> {
                    module.selectRadius(actor, Double.parseDouble(values.text("radius"))); context.redraw();
                }));
        control(menu, context, "displays", "secondary", label(player, "displays"), _ -> context.setState(state.open(Page.DISPLAYS, null)));
        control(menu, context, "clear", "secondary", label(player, "clear"), actor -> { module.clearSelection(actor); context.redraw(); });
    }

    private UiNode displays(FloatingMenuContext<State> context) {
        var player = context.player();
        var state = context.state();
        var items = new ArrayList<VehicleEditorList.Item>();
        for (var display : module.selectableDisplays(player)) {
            UUID id = display.id();
            var row = Component.text(display.type().name() + " · " + shortId(id) + " · " + position(display.origin()));
            items.add(new VehicleEditorList.Item(UiKey.item("display:" + id), row, (actor, _) -> run(actor, state, () -> {
                module.selectableDisplay(actor, id);
                context.setState(state.open(Page.DISPLAY, id));
            })));
        }
        return rows(context, items, text(player, Message.MENU_SEARCH_EMPTY));
    }

    private void display(MenuScene.Builder menu, FloatingMenuContext<State> context) {
        var player = context.player();
        var state = context.state();
        var display = module.selectableDisplay(player, state.target());
        menu.information("summary", Component.text(display.getType().name() + " · " + shortId(display.getUniqueId()))).region("summary");
        var selected = module.selected(player).ids().contains(state.target());
        control(menu, context, "toggle", "primary", label(player, selected ? "deselect-display" : "select-display"), actor -> {
            module.selectEntity(actor, state.target());
            context.setState(state.parent().resumed());
        });
        control(menu, context, "group", "secondary", label(player, "group"), actor -> {
            module.selectableDisplay(actor, state.target());
            module.selectGroup(actor, state.target());
            context.setState(state.parent().parent().resumed());
        });
    }

    private UiNode rows(FloatingMenuContext<State> context, List<VehicleEditorList.Item> items, Component empty) {
        var state = context.state();
        return VehicleEditorList.content(items, empty, state.anchor(), next -> run(context.player(), state, () -> context.setState(state.anchor(next))));
    }

    private void explain(Player player, MenuScene.NodeBuilder control, VehicleModule.Block reason) {
        if (reason != null) control.disabled(language.text(player, Message.VEHICLE_MENU_BLOCKED, NamedTextColor.YELLOW, reason.name()));
    }

    private MenuScene.NodeBuilder control(MenuScene.Builder menu, FloatingMenuContext<State> context, String id, String region, Component label, PlayerAction action) {
        return menu.control(id, label).region(region).primary((actor, _) -> run(actor, context.state(), () -> action.run(actor)));
    }
    private void confirm(Player player, State state, Component title, Component detail, Operation action) {
        MenuDialogs.openConfirm(plugin, player, title, detail, title, text(player, Message.CANCEL), actor -> run(actor, state, action));
    }
    private void input(Player player, State state, Component title, List<MenuInputForm.Field> fields, Consumer<MenuInputForm.Values> action) {
        var builder = MenuInputForm.builder(title).cancel(text(player, Message.CANCEL)).validate((actor, values) -> {
            try { requireCurrent(actor, state); double radius = Double.parseDouble(values.text("radius")); VehicleDefinition.positive(radius, "radius", 16); return null; }
            catch (IllegalArgumentException | IllegalStateException error) { return problem(actor, error); }
        }).submitAsync(text(player, Message.VEHICLE_MODEL_APPLY), (actor, values, completion) -> {
            try { requireCurrent(actor, state); action.accept(values); }
            catch (IllegalArgumentException | IllegalStateException error) { completion.failure(problem(actor, error)); return; }
            completion.success();
        });
        fields.forEach(builder::field); FloatingMenus.input(player, builder.build());
    }
    boolean place(Player player) {
        var pick = picks.get(player.getUniqueId());
        if (pick == null) return false;
        if (!pick.lease().active()) { cancelPick(player.getUniqueId()); return false; }
        if (tick < pick.ready()) return true;
        picks.remove(player.getUniqueId());
        if (!pick.lease().complete()) return true;
        if (player.isSneaking() || tick >= pick.expires()) {
            player.sendMessage(text(player, Message.VEHICLE_MODEL_PLACE_CANCELLED));
            return true;
        }
        run(player, pick.state(), () -> {
            module.selectLookGroup(player);
            captureScreen.flow(player).ifPresent(FloatingMenuFlow::redraw);
        });
        return true;
    }
    void tick() {
        tick++;
        for (var entry : List.copyOf(picks.entrySet())) {
            if (entry.getValue().expires() <= tick || !entry.getValue().lease().active()) cancelPick(entry.getKey());
        }
    }
    void cancelPick(UUID player) {
        var pick = picks.remove(player);
        if (pick != null) pick.lease().close();
    }
    void forget(UUID player) { cancelPick(player); screen.forget(player); captureScreen.forget(player); }
    void close() {
        closed = true;
        for (UUID player : List.copyOf(picks.keySet())) cancelPick(player);
        screen.forgetAll();
        captureScreen.forgetAll();
    }
    private void run(Player player, State state, Operation operation) {
        try { requireCurrent(player, state); operation.run(); }
        catch (IllegalArgumentException | IllegalStateException error) { player.sendMessage(problem(player, error)); }
        catch (IOException error) { module.storageFailure(player, error); }
    }
    private Component problem(Player player, RuntimeException error) {
        return language.text(player, Message.VEHICLE_ERROR, NamedTextColor.RED,
                error instanceof VehicleImportException imported ? imported.describe(language, player) : error.getMessage());
    }
    private Component label(Player player, String key) { return language.text(player, Message.VEHICLE_MENU_LABEL, NamedTextColor.AQUA, key); }
    private Component text(Player player, Message key) { return language.text(player, key, NamedTextColor.AQUA); }
    private static String shortId(UUID id) { return id.toString().substring(0, 8); }
    private static double tenth(double value) { return Math.round(value * 10) / 10.0; }
    private static String position(VehicleVector point) { return String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f", point.coordinateX(), point.coordinateY(), point.coordinateZ()); }
}
