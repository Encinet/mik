package org.encinet.mik.feature.world.vehicle;

import org.encinet.mik.shared.menu.MenuInputForm;
import org.encinet.mik.shared.menu.FloatingMenus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Location;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuContext;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.menu.FloatingMenuScreen;
import org.encinet.mik.shared.menu.FloatingMenuState;
import org.encinet.mik.shared.menu.MenuDialogs;
import org.encinet.mik.feature.world.vehicle.VehicleEditorRoute.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

final class VehicleModelEditor {
    private record Corner(VehicleVector point, boolean look) { }
    private record Placement(VehicleEditorPlacement operation, VehicleEditorView returnTo) { }
    private record Aim(Location point, Display display) { }
    @FunctionalInterface private interface Operation { void run() throws IOException; }
    @FunctionalInterface private interface PlayerAction { void run(Player player) throws IOException; }
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final VehicleModule module;
    private final VehicleEditorPreview previews;
    private final Map<UUID, VehicleEditorRoute> spatial = new HashMap<>();
    private final Map<UUID, VehicleModelDraft> drafts = new HashMap<>();
    private final Map<UUID, VehicleEditorStage> stages = new HashMap<>();
    private final Map<UUID, Location> draftOrigins = new HashMap<>();
    private final Map<UUID, UUID> sessions = new HashMap<>();
    private final Map<UUID, Corner> corners = new HashMap<>();
    private final Map<UUID, Double> steps = new HashMap<>();
    private final Map<UUID, Map<VehicleEditorTransform.Mode, Double>> transformSteps = new HashMap<>();
    private final Set<UUID> snapping = new java.util.HashSet<>();
    private final Map<UUID, Placement> placements = new HashMap<>();
    private final Map<UUID, Long> placementClicks = new HashMap<>();
    private final FloatingMenuScreen<VehicleEditorView> screen;
    private boolean closed;
    private long generation;
    private long ticks;

    VehicleModelEditor(JavaPlugin plugin, LanguageService language, VehicleModule module) {
        this.plugin = plugin;
        this.language = language;
        this.module = module;
        previews = new VehicleEditorPreview(plugin, language, module::refreshGizmos);
        screen = new FloatingMenuScreen<>("vehicle-models", this::render);
    }

    enum Source { EMPTY, HAND, SELECTION }

    void create(Player player, String id, VehicleDefinition.Kind kind, Source source) throws IOException {
        requireAdmin(player);
        requireNewDraft(player);
        if (source == Source.EMPTY) begin(player, VehicleModelDraft.create(id, kind));
        else if (source == Source.HAND) {
            var imported = module.importGroup(player, id, kind, null);
            begin(player, VehicleModelDraft.imported(imported.definition()), imported.origin());
        } else begin(player, VehicleModelDraft.imported(module.captureDraft(player, id, kind)));
        openPanel(player, Workspace.INSTANCE);
    }

    void edit(Player player, String id) {
        requireAdmin(player);
        VehicleModelDraft existing = drafts.get(player.getUniqueId());
        if (existing == null || !existing.id().equals(id)) begin(player, new VehicleModelDraft(model(id)));
        openPanel(player, Workspace.INSTANCE);
    }

    void cloneModel(Player player, String id, String newId) {
        requireAdmin(player);
        begin(player, VehicleModelDraft.copy(model(id), newId));
        open(player);
    }

    void anchor(Player player) {
        requireAdmin(player);
        var draft = draft(player);
        stages.put(player.getUniqueId(), VehicleEditorStage.fit(draft, player.getLocation()));
        corners.remove(player.getUniqueId()); placements.remove(player.getUniqueId());
        preview(player, draft, false); redraw(player);
    }

    void front(Player player, int turns) {
        requireAdmin(player);
        draft(player).front(turns);
        corners.remove(player.getUniqueId());
        changed(player);
    }

    void snap(Player player) {
        requireAdmin(player);
        draft(player);
        if (!snapping.remove(player.getUniqueId())) snapping.add(player.getUniqueId());
        redraw(player);
    }

    void save(Player player, boolean keepEditing) throws IOException {
        requireAdmin(player);
        var draft = draft(player);
        var saved = module.saveModel(draft);
        if (keepEditing) { draft.saved(saved); refreshPreview(player); }
        else {
            drafts.remove(player.getUniqueId()); draftOrigins.remove(player.getUniqueId()); stages.remove(player.getUniqueId());
            clearTools(player.getUniqueId()); clearPreview(player.getUniqueId());
            open(player);
        }
        screen.redrawWhere(_ -> true);
        result(player, language.t(player, Message.VEHICLE_MODEL_SAVED));
    }

    void history(Player player, boolean redo) {
        requireAdmin(player);
        var draft = draft(player);
        List<String> sections = List.of("parts", "seats", "colliders", "supports");
        var before = sections.stream().map(draft::count).toList();
        if (!(redo ? draft.redo() : draft.undo())) throw new IllegalArgumentException(language.t(player, Message.VEHICLE_MODEL_HISTORY_EMPTY));
        if (!before.equals(sections.stream().map(draft::count).toList())) {
            if (spatial.containsKey(player.getUniqueId())) spatial.put(player.getUniqueId(), Workspace.INSTANCE);
            screen.update(player, _ -> VehicleEditorView.open(Workspace.INSTANCE));
        }
        changed(player);
    }

    void corner(Player player, boolean second, boolean look) {
        requireAdmin(player);
        var point = sample(player, look ? "look" : "here");
        if (!second) {
            corners.put(player.getUniqueId(), new Corner(point, look));
            if (previews.get(player.getUniqueId()) == null) preview(player, draft(player));
            redraw(player);
            result(player, language.t(player, Message.VEHICLE_MODEL_CORNER_HINT));
        } else {
            var first = corners.get(player.getUniqueId());
            if (first == null) throw new IllegalArgumentException(language.t(player, Message.VEHICLE_MODEL_CORNER_REQUIRED));
            draft(player).collider("add", -1, VehicleEditorGeometry.box(first.point(), point));
            corners.remove(player.getUniqueId()); changed(player);
        }
    }

    private String itemPayload(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) throw new IllegalArgumentException("Hold an item in your main hand");
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    void mutate(Player player, Consumer<VehicleModelDraft> action) {
        requireAdmin(player);
        var draft = draft(player);
        var before = java.util.Arrays.stream(Section.values()).map(section -> draft.count(section.key())).toList();
        action.accept(draft);
        if (!before.equals(java.util.Arrays.stream(Section.values()).map(section -> draft.count(section.key())).toList())) {
            if (spatial.containsKey(player.getUniqueId())) spatial.put(player.getUniqueId(), Workspace.INSTANCE);
        }
        changed(player);
    }

    private void begin(Player player, VehicleModelDraft draft) {
        begin(player, draft, module.editorOrigin(player));
    }

    private void begin(Player player, VehicleModelDraft draft, Location origin) {
        requireNewDraft(player);
        if (draft.base() == null && module.models().containsKey(draft.id())) throw new IllegalArgumentException("Model already exists");
        origin = origin.clone();
        origin.setPitch(0);
        sessions.put(player.getUniqueId(), UUID.randomUUID());
        drafts.put(player.getUniqueId(), draft);
        draftOrigins.put(player.getUniqueId(), origin);
        stages.put(player.getUniqueId(), VehicleEditorStage.fit(draft, player.getLocation()));
        clearTools(player.getUniqueId());
        screen.update(player, _ -> VehicleEditorView.open(Workspace.INSTANCE));
        try { preview(player, draft, false); }
        catch (RuntimeException exception) { previewError(player, exception); }
    }

    private void requireNewDraft(Player player) {
        if (drafts.containsKey(player.getUniqueId())) throw new IllegalArgumentException("Save or cancel the current draft first");
        if (drafts.size() >= 32) throw new IllegalArgumentException("At most 32 concurrent model editors");
    }

    private VehicleVector here(Player player) {
        return sampled(player, player.getLocation());
    }

    private VehicleVector sampled(Player player, Location position) {
        VehicleVector local = VehicleEditorGeometry.local(sourceOrigin(player), position);
        return snapping.contains(player.getUniqueId()) ? VehicleEditorGeometry.snap(local, steps.getOrDefault(player.getUniqueId(), 0.1)) : local;
    }

    private VehicleVector sample(Player player, String target) {
        if (!target.equals("look")) return here(player);
        var aim = aim(player);
        if (aim == null) throw new IllegalArgumentException(language.t(player, Message.VEHICLE_MODEL_LOOK_MISSING));
        return aim.display() == null ? sampled(player, aim.point()) : stages.get(player.getUniqueId()).local(draft(player), aim.point());
    }

    private Aim aim(Player player) {
        Location eye = player.getEyeLocation();
        var obstruction = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), 32, FluidCollisionMode.NEVER, true);
        double maximum = obstruction == null ? 32 : obstruction.getHitPosition().distance(eye.toVector());
        VehicleEditorPreview.Preview preview = previews.get(player.getUniqueId());
        VehicleDisplayPicker.Hit hit = preview == null ? null
                : VehicleDisplayPicker.hit(eye, eye.getDirection(), preview.displays(), _ -> false, maximum);
        if (hit != null) return new Aim(new Location(player.getWorld(), hit.position().coordinateX(), hit.position().coordinateY(), hit.position().coordinateZ()), hit.display());
        if (obstruction != null) return new Aim(obstruction.getHitPosition().toLocation(player.getWorld()), null);
        return null;
    }

    void library(Player player) {
        requireAdmin(player); spatial.remove(player.getUniqueId()); placements.remove(player.getUniqueId());
        sessions.computeIfAbsent(player.getUniqueId(), _ -> UUID.randomUUID());
        screen.open(player, VehicleEditorView.open(new Library(0)));
    }

    void open(Player player) {
        requireAdmin(player);
        spatial.remove(player.getUniqueId());
        placements.remove(player.getUniqueId());
        sessions.computeIfAbsent(player.getUniqueId(), _ -> UUID.randomUUID());
        VehicleEditorRoute initial = drafts.containsKey(player.getUniqueId()) ? Workspace.INSTANCE : new Library(0);
        VehicleEditorView previous = screen.state(player).orElseGet(() -> VehicleEditorView.open(initial));
        screen.open(player, drafts.containsKey(player.getUniqueId()) && !(previous.route() instanceof Library || previous.route() instanceof Model)
                ? previous : VehicleEditorView.open(initial));
    }

    private void openPanel(Player player, VehicleEditorRoute route) {
        spatial.remove(player.getUniqueId());
        placements.remove(player.getUniqueId());
        screen.open(player, VehicleEditorView.open(route));
        refreshPreview(player);
    }

    private MenuScene render(FloatingMenuContext<VehicleEditorView> context) {
        Player player = context.player();
        VehicleEditorView view = context.state();
        VehicleEditorRoute state = view.route();
        VehicleModelDraft draft = drafts.get(player.getUniqueId());
        VehicleEditorRoute displayRoute = draft == null && !(state instanceof Model)
                || !player.hasPermission("mik.vehicle.admin") || closed ? new Library(0) : state;
        MenuScene.Builder menu = createMenu(panelTitle(player, displayRoute), displayRoute);
        UiNode reading = null;
        VehicleEditorComposition.Primary primary = VehicleEditorComposition.primary(displayRoute);
        if (draft != null && !(displayRoute instanceof Library || displayRoute instanceof Model) && player.hasPermission("mik.vehicle.admin") && !closed) {
            menu.information("draft", Component.text(draft.id(), NamedTextColor.WHITE).append(Component.text(" · "))
                    .append(language.text(player, Message.VEHICLE_MODEL_CHOICE, NamedTextColor.AQUA, draft.kind()))
                    .append(Component.text(" · ")).append(language.text(player,
                            draft.dirty() ? Message.VEHICLE_MODEL_DRAFT : Message.VEHICLE_MODEL_SAVED,
                            draft.dirty() ? NamedTextColor.YELLOW : NamedTextColor.GREEN))).region("draft");
            String help = state instanceof Components list ? list.section().key()
                    : state == Workspace.INSTANCE ? "overview" : state == Utilities.INSTANCE ? "tools"
                    : state instanceof Parameters ? "parameters" : null;
            if (help != null) menu.information("help", language.text(player, Message.VEHICLE_MODEL_EDITOR_HELP, NamedTextColor.WHITE, help)).region("help");
            menu.navigation("section:overview", text(player, Message.VEHICLE_MODEL_OVERVIEW)).region("sections")
                    .selected(state == Workspace.INSTANCE).primary((_, _) -> navigate(context, view, Workspace.INSTANCE));
            for (Section section : Section.values()) {
                boolean current = state instanceof Components list && list.section() == section
                        || state instanceof ObjectView object && object.target().section() == section;
                menu.navigation("section:" + section, sectionCount(player, draft, section)).region("sections").selected(current)
                        .primary((_, _) -> navigate(context, view, new Components(section, 0)));
            }
            menu.navigation("section:parameters", text(player, Message.VEHICLE_MODEL_PARAMETERS)).region("sections")
                    .selected(state instanceof Parameters || state instanceof Settings || state == Center.INSTANCE).primary((_, _) -> navigate(context, view, new Parameters(0)));
            menu.navigation("section:tools", text(player, Message.VEHICLE_MODEL_TOOLS)).region("sections")
                    .selected(state == Utilities.INSTANCE).primary((_, _) -> navigate(context, view, Utilities.INSTANCE));
            action(menu, player, "apply", Message.VEHICLE_MODEL_SAVE_CONTINUE, "commit", actor -> save(actor, true));
            action(menu, player, "save", Message.VEHICLE_MODEL_SAVE, "commit", actor -> save(actor, false));
            historyAction(menu, player, "undo", Message.VEHICLE_MODEL_UNDO, draft.canUndo(), "history");
            historyAction(menu, player, "redo", Message.VEHICLE_MODEL_REDO, draft.canRedo(), "history");
        }
        if (!player.hasPermission("mik.vehicle.admin") || closed) {
            menu.information("denied", Component.text("mik.vehicle.admin", NamedTextColor.RED)).region("summary");
            primary = VehicleEditorComposition.Primary.SUMMARY;
        } else if (state instanceof Library || draft == null && !(state instanceof Model)) {
            List<String> ids = module.models().keySet().stream().sorted().toList();
            int anchor = state instanceof Library library ? library.anchor() : 0;
            menu.information("summary", text(player, Message.VEHICLE_MODEL_LIBRARY)).region("summary");
            var models = new ArrayList<VehicleEditorList.Item>();
            for (int index = 0; index < ids.size(); index++) {
                String id = ids.get(index);
                int sourceIndex = index;
                models.add(new VehicleEditorList.Item(UiKey.item("model:" + id), Component.text(id),
                        (_, _) -> navigate(context, view, new Model(id, sourceIndex))));
            }
            reading = VehicleEditorList.content(models, text(player, Message.MENU_SEARCH_EMPTY), anchor,
                    next -> navigate(context, view, new Library(next)));
            action(menu, player, "create", Message.VEHICLE_MODEL_CREATE,
                    actor -> newModel(actor, Source.EMPTY));
            action(menu, player, "import", Message.VEHICLE_MODEL_IMPORT,
                    actor -> newModel(actor, Source.HAND));
            action(menu, player, "capture", Message.VEHICLE_MODEL_SELECTED, actor -> newModel(actor, Source.SELECTION));
            action(menu, player, "selection", Message.VEHICLE_MODEL_GROUP, module::openSelection);
            menu.navigation("vehicles", text(player, Message.VEHICLE_COMMAND_DESCRIPTION)).region("navigation").primary((actor, _) -> module.openMenu(actor));
            if (draft != null) action(menu, player, "resume", Message.VEHICLE_MODEL_EDIT, actor -> openPanel(actor, Workspace.INSTANCE));
        } else if (state instanceof Model) {
            VehicleDefinition definition = module.models().get(((Model) state).id());
            if (definition != null) {
                menu.information("summary", Component.text(definition.id() + " · ")
                        .append(language.text(player, Message.VEHICLE_MODEL_CHOICE, NamedTextColor.AQUA, definition.kind().name()))).region("summary");
                action(menu, player, "edit", Message.VEHICLE_MODEL_EDIT, actor -> run(actor, () -> edit(actor, definition.id())));
                action(menu, player, "spawn", Message.VEHICLE_MODEL_SPAWN, actor -> spawnForm(actor, definition));
                action(menu, player, "clone", Message.VEHICLE_MODEL_CLONE, actor -> prompt(actor, Message.VEHICLE_MODEL_CLONE, "ID", "", value -> cloneModel(actor, definition.id(), value)));
                action(menu, player, "delete", Message.VEHICLE_MODEL_DELETE, actor -> delete(actor, definition));
            } else {
                menu.information("missing", text(player, Message.VEHICLE_MODEL_SELECTION_UNAVAILABLE)).region("summary");
                primary = VehicleEditorComposition.Primary.SUMMARY;
            }
        } else if (state == Workspace.INSTANCE) {
            for (Section section : Section.values()) menu.control("edit:" + section, sectionCount(player, draft, section)
                    .append(Component.newline()).append(language.text(player, Message.VEHICLE_MODEL_EDITOR_HELP, NamedTextColor.WHITE, section.key())))
                    .region("items").primary((_, _) -> navigate(context, view, new Components(section, 0)));
            menu.control("edit:parameters", text(player, Message.VEHICLE_MODEL_PARAMETERS)
                    .append(Component.newline()).append(language.text(player, Message.VEHICLE_MODEL_EDITOR_HELP, NamedTextColor.WHITE, "parameters")))
                    .region("items").primary((_, _) -> navigate(context, view, new Parameters(0)));
            action(menu, player, "spatial", Message.VEHICLE_MODEL_SPATIAL, actor -> startSpatial(actor, Workspace.INSTANCE));
            action(menu, player, "front", Message.VEHICLE_MODEL_FRONT, _ -> navigate(context, view, Facing.INSTANCE));
        } else if (state == Utilities.INSTANCE) {
            action(menu, player, "preview", Message.VEHICLE_MODEL_PREVIEW, actor -> preview(actor, draft(actor)));
            menu.control("preview-off", language.text(player, Message.VEHICLE_MENU_LABEL, NamedTextColor.AQUA, "hide-preview")).region("actions")
                    .primary((actor, _) -> run(actor, () -> { clearPreview(actor.getUniqueId()); redraw(actor); }));
            action(menu, player, "anchor", Message.VEHICLE_MODEL_ANCHOR, this::anchor);
            for (boolean enlarge : List.of(false, true)) menu.control(enlarge ? "zoom-in" : "zoom-out",
                    language.text(player, Message.VEHICLE_MENU_LABEL, NamedTextColor.AQUA, enlarge ? "zoom-in" : "zoom-out"))
                    .region("actions").primary((actor, _) -> run(actor, () -> zoom(actor, enlarge)));
            action(menu, player, "pick", Message.VEHICLE_MODEL_PICK, actor -> armLook(actor, VehicleEditorPlacement.Target.PICK));
            action(menu, player, "center-here", Message.VEHICLE_MODEL_CENTER_HERE, actor -> mutate(actor, next -> next.set("center-of-mass", VehicleEditorGeometry.coordinates(here(actor)))));
            action(menu, player, "center-look", Message.VEHICLE_MODEL_CENTER_LOOK, actor -> armLook(actor, VehicleEditorPlacement.Target.CENTER));
            action(menu, player, "check", Message.VEHICLE_MODEL_CHECK, actor -> { draft(actor).validate(); result(actor, language.t(actor, Message.VEHICLE_MODEL_CHECKED)); });
            action(menu, player, "snap", snapping.contains(player.getUniqueId()) ? Message.VEHICLE_MODEL_SNAP_ON : Message.VEHICLE_MODEL_SNAP_OFF, this::snap);
            action(menu, player, "center", Message.VEHICLE_MODEL_CENTER, _ -> navigate(context, view, Center.INSTANCE));
            action(menu, player, "front", Message.VEHICLE_MODEL_FRONT, _ -> navigate(context, view, Facing.INSTANCE));
            action(menu, player, "discard", Message.VEHICLE_MODEL_DISCARD, this::discard);
        } else if (state == Facing.INSTANCE) {
            menu.information("summary", text(player, Message.VEHICLE_MODEL_FRONT_HINT)).region("summary");
            action(menu, player, "spatial", Message.VEHICLE_MODEL_SPATIAL, actor -> startSpatial(actor, Facing.INSTANCE));
            long revision = draft.revision();
            for (String direction : List.of("positive-x", "negative-x", "back", "look"))
                menu.control("front:" + direction, language.text(player, Message.VEHICLE_MODEL_FRONT_CHOICE, NamedTextColor.AQUA, direction))
                        .region("actions").primary((actor, _) -> run(actor, () -> {
                            requireCurrent(actor, draft, revision);
                            if (direction.equals("look")) armLook(actor, VehicleEditorPlacement.Target.FRONT);
                            else front(actor, switch (direction) { case "forward" -> 0; case "positive-x" -> 3; case "negative-x" -> 1; default -> 2; });
                        }));
        } else if (state == Center.INSTANCE) {
            reading = centerMenu(menu, player, draft, view, next -> read(context, view, next));
        } else if (state == Kinds.INSTANCE) {
            menu.information("summary", fieldLabel(player, "kind")).region("summary");
            long revision = draft.revision();
            for (var kind : VehicleDefinition.Kind.values()) menu.control("kind:" + kind,
                    language.text(player, Message.VEHICLE_MODEL_CHOICE, NamedTextColor.AQUA, kind.name())).region("items")
                    .selected(kind.name().equals(draft.kind())).primary((actor, _) -> run(actor, () -> {
                        requireCurrent(actor, draft, revision);
                        draft.set("kind", kind.name());
                        changed(actor);
                    }));
        } else if (state instanceof Properties properties) {
            if (properties.index() >= draft.count("parts")) { missingSelection(menu, player); primary = VehicleEditorComposition.Primary.SUMMARY; }
            else {
                menu.information("summary", text(player, Message.VEHICLE_MODEL_OPTIONS).append(Component.text(" · " + properties.index()))).region("summary");
                var part = draft.parts().get(properties.index());
                long revision = draft.revision();
                for (var property : VehicleEditorProperty.values()) if (property != VehicleEditorProperty.ANIMATION && property.applies(part.kind())) {
                    String value = property.value(part);
                    Component valueLabel = property.choices().isEmpty() ? Component.text(value)
                            : language.text(player, Message.VEHICLE_MODEL_CHOICE, NamedTextColor.AQUA, property.choiceKey(value));
                    menu.control("property:" + property, fieldLabel(player, property.key()).append(Component.text(" · ")).append(valueLabel))
                            .region("items").primary((actor, _) -> run(actor, () -> {
                                requireCurrent(actor, draft, revision);
                                if (!property.choices().isEmpty()) navigate(context, view, new Choices(properties.target(), property));
                                else propertyForm(actor, draft, properties.index(), property);
                            }));
                }
            }
        } else if (state instanceof Choices choices) {
            if (choices.index() >= draft.count("parts") || !choices.property().applies(draft.parts().get(choices.index()).kind())) {
                missingSelection(menu, player); primary = VehicleEditorComposition.Primary.SUMMARY;
            }
            else {
                menu.information("summary", fieldLabel(player, choices.property().key()).append(Component.text(" · " + choices.index()))).region("summary");
                long revision = draft.revision();
                for (String value : choices.property().choices()) menu.control("choice:" + value,
                        language.text(player, Message.VEHICLE_MODEL_CHOICE, NamedTextColor.AQUA, choices.property().choiceKey(value))).region("items")
                        .selected(value.equals(choices.property().value(draft.parts().get(choices.index())))).primary((actor, _) -> run(actor, () -> {
                            requireCurrent(actor, draft, revision);
                            choices.property().apply(draft, choices.index(), value);
                            changed(actor);
                        }));
            }
        } else if (state instanceof Position position) {
            if (position.index() >= draft.count(position.section())) {
                missingSelection(menu, player);
                primary = VehicleEditorComposition.Primary.SUMMARY;
            } else reading = positionMenu(menu, player, draft, position, view, next -> read(context, view, next));
        } else if (state instanceof Transform transform) {
            if (transform.index() >= draft.count(transform.section())) {
                missingSelection(menu, player);
                primary = VehicleEditorComposition.Primary.SUMMARY;
            } else reading = transformMenu(menu, player, draft, transform, view, next -> read(context, view, next));
        } else if (state instanceof Inspector inspector) {
            String section = inspector.section();
            int index = inspector.index();
            if (index >= draft.count(section)) {
                missingSelection(menu, player);
                primary = VehicleEditorComposition.Primary.SUMMARY;
            } else {
                menu.information("summary", componentIdentity(player, draft, section, index)).region("summary");
                componentMenu(menu, player, draft, section, index);
            }
        } else {
            String section = state instanceof Parameters || state instanceof Settings ? "parameters" : ((Components) state).section().key();
            if (section.equals("parameters")) {
                var parameters = new ArrayList<VehicleEditorList.Item>();
                long revision = draft.revision();
                if (state instanceof Parameters) {
                    for (var group : VehicleEditorParameters.groups(draft.kind())) parameters.add(new VehicleEditorList.Item(UiKey.item(group.key()),
                            language.text(player, Message.VEHICLE_MODEL_PARAMETER_GROUP, NamedTextColor.AQUA, VehicleEditorParameters.labelKey(group, draft.kind())),
                            (_, _) -> navigate(context, view, new Settings(group, 0))));
                    reading = VehicleEditorList.content(parameters, text(player, Message.MENU_SEARCH_EMPTY), ((Parameters) state).anchor(),
                            next -> navigate(context, view, new Parameters(next)));
                } else {
                    Settings settings = (Settings) state;
                    menu.information("group-help", language.text(player, Message.VEHICLE_MODEL_PARAMETER_HELP, NamedTextColor.AQUA, VehicleEditorParameters.labelKey(settings.group(), draft.kind()))).region("summary");
                    for (String field : VehicleEditorParameters.visibleFields(settings.group(), draft.kind())) {
                        String value = VehicleModelDraft.VECTORS.contains(field) ? VehicleEditorGeometry.display(draft.vectorField(field)) : VehicleEditorParameters.display(draft, field);
                        Component valueLabel = field.equals("kind") ? language.text(player, Message.VEHICLE_MODEL_CHOICE, NamedTextColor.AQUA, value) : Component.text(value);
                        parameters.add(new VehicleEditorList.Item(UiKey.item(field), fieldLabel(player, field).append(Component.text(" · ")).append(valueLabel),
                                (actor, _) -> run(actor, () -> {
                                    requireCurrent(actor, draft, revision);
                                    if (field.equals("center-of-mass")) navigate(context, view, Center.INSTANCE);
                                    else if (field.equals("kind")) navigate(context, view, Kinds.INSTANCE);
                                    else parameterForm(actor, draft, field);
                                })));
                    }
                    reading = VehicleEditorList.content(parameters, text(player, Message.MENU_SEARCH_EMPTY), settings.anchor(),
                            next -> navigate(context, view, new Settings(settings.group(), next)));
                }
            } else {
                var components = new ArrayList<VehicleEditorList.Item>();
                long listRevision = draft.revision();
                for (int index = 0; index < draft.count(section); index++) {
                    int target = index;
                    components.add(new VehicleEditorList.Item(UiKey.item("component:" + index), componentLabel(player, draft, section, index),
                            (actor, _) -> run(actor, () -> {
                                requireCurrent(actor, draft, listRevision);
                                navigate(context, view, new Inspector(new ComponentRef(Section.named(section), target)));
                            })));
                }
                reading = VehicleEditorList.content(components, text(player, Message.MENU_SEARCH_EMPTY), ((Components) state).anchor(),
                        next -> navigate(context, view, new Components(Section.named(section), next)));
                if (!section.equals("parts")) action(menu, player, "add", Message.VEHICLE_MODEL_ADD, actor -> add(actor, section));
                if (section.equals("seats")) {
                    action(menu, player, "driver-here", Message.VEHICLE_MODEL_DRIVER_HERE, actor -> mutate(actor, next -> next.seat("add", -1, here(actor), true)));
                    action(menu, player, "passenger-here", Message.VEHICLE_MODEL_PASSENGER_HERE, actor -> mutate(actor, next -> next.seat("add", -1, here(actor), false)));
                }
                if (section.equals("supports")) action(menu, player, "add-here", Message.VEHICLE_MODEL_ADD_HERE, actor -> mutate(actor, next -> next.support("add", -1, here(actor))));
                if (section.equals("parts")) {
                    action(menu, player, "add-block", Message.VEHICLE_MODEL_BLOCK, actor -> payloadForm(actor, -1, VehicleDefinition.PartKind.BLOCK));
                    action(menu, player, "add-item", Message.VEHICLE_MODEL_ITEM, actor -> mutate(actor, next -> next.addParts(List.of(VehicleModelDraft.part(VehicleDefinition.PartKind.ITEM, itemPayload(actor))))));
                    action(menu, player, "add-text", Message.VEHICLE_MODEL_TEXT, actor -> payloadForm(actor, -1, VehicleDefinition.PartKind.TEXT));
                    action(menu, player, "selected", Message.VEHICLE_MODEL_SELECTED, actor -> mutate(actor, next -> next.addParts(module.selectedParts(actor, sourceOrigin(actor)))));
                    action(menu, player, "group", Message.VEHICLE_MODEL_GROUP, actor -> mutate(actor, next -> next.addParts(module.groupParts(actor, null, sourceOrigin(actor)))));
                }
                if (section.equals("colliders")) {
                    action(menu, player, "auto", Message.VEHICLE_MODEL_AUTO, actor -> mutate(actor, next -> next.collider("auto", -1, null)));
                    action(menu, player, "corner1", Message.VEHICLE_MODEL_CORNER_FIRST, actor -> corner(actor, false, false));
                    action(menu, player, "corner1-look", Message.VEHICLE_MODEL_CORNER_FIRST_LOOK, actor -> armLook(actor, VehicleEditorPlacement.Target.CORNER_FIRST));
                    if (corners.containsKey(player.getUniqueId())) {
                        action(menu, player, "corner2", Message.VEHICLE_MODEL_CORNER_SECOND, actor -> corner(actor, true, false));
                        action(menu, player, "corner2-look", Message.VEHICLE_MODEL_CORNER_SECOND_LOOK, actor -> armLook(actor, VehicleEditorPlacement.Target.CORNER_SECOND));
                        action(menu, player, "cancel-corner", Message.VEHICLE_MODEL_CORNER_CANCEL, actor -> { corners.remove(actor.getUniqueId()); redraw(actor); });
                    }
                }
            }
        }
        if (draft != null && state instanceof ObjectView object && object.index() < draft.count(object.section())
                && player.hasPermission("mik.vehicle.admin") && !closed) {
            long revision = draft.revision();
            VehicleEditorComposition.tools(menu, object, message -> text(player, message), (actor, tool) -> run(actor, () -> {
                requireCurrent(actor, draft, revision);
                navigate(context, view, tool);
            }));
            action(menu, player, "spatial", Message.VEHICLE_MODEL_SPATIAL, actor -> {
                requireCurrent(actor, draft, revision);
                startSpatial(actor, object instanceof Inspector || object instanceof Properties || object instanceof Choices
                        ? new Position(object.target(), false) : object);
            });
        }
        if (!(displayRoute instanceof Library)) menu.navigation("back", text(player, Message.BACK)).region("navigation")
                .primary((_, _) -> navigate(context, view, displayRoute.back()));
        menu.content(VehicleEditorComposition.create(new VehicleEditorView(displayRoute, view.itemPage(), view.actionPage()),
                reading, primary, next -> read(context, view, next)));
        return finishMenu(menu, text(player, Message.CLOSE));
    }

    private Component sectionCount(Player player, VehicleModelDraft draft, Section section) {
        return language.text(player, Message.VEHICLE_MODEL_SECTION_COUNT, NamedTextColor.AQUA, section.key(), draft.count(section.key()));
    }

    private Component panelTitle(Player player, VehicleEditorRoute route) {
        return text(player, switch (route) {
            case Workspace _ -> Message.VEHICLE_MODEL_OVERVIEW;
            case Utilities _ -> Message.VEHICLE_MODEL_TOOLS;
            case Facing _ -> Message.VEHICLE_MODEL_FRONT;
            case Center _ -> Message.VEHICLE_MODEL_CENTER;
            case Components list -> sectionMessage(list.section().key());
            case Parameters _, Settings _, Kinds _ -> Message.VEHICLE_MODEL_PARAMETERS;
            case ObjectView object -> sectionMessage(object.section());
            case Library _, Model _ -> Message.VEHICLE_MODEL_TITLE;
        });
    }

    private double spatialStep(Player player, VehicleEditorRoute route) {
        if (route instanceof Transform transform) return transformSteps
                .getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(transform.mode(), VehicleEditorTransform.defaultStep(transform.mode()));
        return steps.getOrDefault(player.getUniqueId(), 0.1);
    }

    private void updateScene(Player player, VehicleModelDraft draft) {
        VehicleEditorRoute route = spatial.getOrDefault(player.getUniqueId(),
                screen.state(player).map(VehicleEditorView::route).orElse(Workspace.INSTANCE));
        previews.scene(player, spatial.containsKey(player.getUniqueId())
                ? VehicleEditorScene.stage(draft, route, spatialStep(player, route), stages.get(player.getUniqueId()).scale())
                : VehicleEditorScene.create(draft, route, false, spatialStep(player, route)));
    }

    private void startSpatial(Player player, VehicleEditorRoute route) {
        placements.remove(player.getUniqueId());
        spatial.put(player.getUniqueId(), route);
        preview(player, draft(player), false);
        FloatingMenus.current(player).ifPresent(handle -> handle.close());
        placementClicks.put(player.getUniqueId(), ticks + 6);
        result(player, language.t(player, Message.VEHICLE_MODEL_SPATIAL_HINT));
    }

    private boolean spatialClick(Player player) {
        UUID id = player.getUniqueId();
        if (!spatial.containsKey(id)) return false;
        if (hasForeground(player)) return false;
        placementClicks.put(id, ticks + 1);
        run(player, () -> {
            if (player.isSneaking()) {
                openPanel(player, spatial.get(id));
                return;
            }
            var preview = previews.get(id);
            if (preview == null) { spatial.remove(id); return; }
            var draft = draft(player);
            var handle = pointedHandle(player, preview);
            if (handle != null) {
                switch (handle.action()) {
                    case VehicleEditorScene.Select selection -> {
                        VehicleEditorRoute selected = selection.route();
                        spatial.put(id, selected);
                        updateScene(player, draft);
                    }
                    case VehicleEditorScene.Navigate navigation -> {
                        if (navigation.route() instanceof Inspector || navigation.route() instanceof Properties || navigation.route() instanceof Choices)
                            openPanel(player, navigation.route());
                        else { spatial.put(id, navigation.route()); updateScene(player, draft); }
                    }
                    case VehicleEditorScene.Tool tool -> {
                        switch (tool) {
                            case MENU -> openPanel(player, spatial.get(id));
                            case SAVE -> save(player, true);
                            case UNDO -> history(player, false);
                            case REDO -> history(player, true);
                            case FRAME -> anchor(player);
                            case ZOOM_IN, ZOOM_OUT -> zoom(player, tool == VehicleEditorScene.Tool.ZOOM_IN);
                        }
                    }
                    case VehicleEditorScene.Face face -> front(player, face.turns());
                    case VehicleEditorScene.Adjust adjust -> {
                        VehicleEditorRoute route = spatial.get(id);
                        double step = spatialStep(player, route);
                        if (route instanceof Transform transform) VehicleEditorTransform.adjust(draft, transform.mode(), transform.index(), adjust.axis(), adjust.direction(), step);
                        else {
                            VehicleVector offset = new VehicleVector(adjust.axis() == 0 ? step * adjust.direction() : 0,
                                    adjust.axis() == 1 ? step * adjust.direction() : 0, adjust.axis() == 2 ? step * adjust.direction() : 0);
                            if (route == Center.INSTANCE) draft.set("center-of-mass", VehicleEditorGeometry.coordinates(draft.vectorField("center-of-mass").add(offset)));
                            else if (route instanceof ObjectView object) draft.position(object.section(), object.index(), object.exit(),
                                    draft.position(object.section(), object.index(), object.exit()).add(offset));
                        }
                        changed(player);
                    }
                }
            } else {
                Aim aim = aim(player);
                int index = aim == null || aim.display() == null ? -1 : preview.displays().indexOf(aim.display());
                if (index >= 0) {
                    var selected = new Position(new ComponentRef(Section.PARTS, index), false);
                    spatial.put(id, selected);
                    updateScene(player, draft);
                }
            }
        });
        return true;
    }

    boolean scroll(Player player, int direction) {
        var route = spatial.get(player.getUniqueId());
        if (route == null || hasForeground(player) || !(route instanceof ObjectView || route == Center.INSTANCE)) return false;
        run(player, () -> {
            double current = spatialStep(player, route);
            double next;
            if (route instanceof Transform transform) {
                next = VehicleEditorTransform.nextStep(transform.mode(), current, direction);
                transformSteps.computeIfAbsent(player.getUniqueId(), _ -> new java.util.EnumMap<>(VehicleEditorTransform.Mode.class)).put(transform.mode(), next);
            } else {
                var choices = List.of(0.1, 0.25, 1.0);
                next = choices.get(Math.floorMod(choices.indexOf(current) + (direction < 0 ? -1 : 1), choices.size()));
                steps.put(player.getUniqueId(), next);
            }
            updateScene(player, draft(player));
            player.sendActionBar(text(player, Message.VEHICLE_MODEL_TRANSFORM_STEP).append(Component.text(" · " + next)));
        });
        return true;
    }

    private static boolean hasForeground(Player player) {
        return FloatingMenus.current(player).filter(handle -> handle.state() != FloatingMenuState.CLOSING && handle.state() != FloatingMenuState.CLOSED).isPresent();
    }

    private void zoom(Player player, boolean enlarge) {
        var draft = draft(player);
        double next = Math.clamp(stages.get(player.getUniqueId()).scale() * (enlarge ? 1.25 : 0.8), 0.02, 2);
        stages.put(player.getUniqueId(), stages.get(player.getUniqueId()).withScale(draft, next));
        preview(player, draft, false);
    }

    private VehicleEditorScene.Handle pointedHandle(Player player, VehicleEditorPreview.Preview preview) {
        Location eye = player.getEyeLocation();
        if (!eye.getWorld().equals(preview.origin().getWorld()) || eye.distanceSquared(preview.origin()) > 1024) return null;
        var block = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), 32, FluidCollisionMode.NEVER, true);
        double maximum = block == null ? 32 : block.getHitPosition().distance(eye.toVector());
        var direction = eye.getDirection();
        return VehicleEditorScene.pick(preview.handles(), VehicleEditorGeometry.local(preview.origin(), eye).multiply(1 / preview.scale()),
                new VehicleVector(direction.getX(), direction.getY(), direction.getZ()).rotate(VehicleModel.orientation(preview.origin().getYaw()).conjugate()), maximum / preview.scale(), preview.scale());
    }

    private void navigate(FloatingMenuContext<VehicleEditorView> context, VehicleEditorView source, VehicleEditorRoute route) {
        read(context, source, VehicleEditorView.open(route));
    }

    private void read(FloatingMenuContext<VehicleEditorView> context, VehicleEditorView source, VehicleEditorView next) {
        if (!closed && context.player().hasPermission("mik.vehicle.admin")
                && screen.flow(context.player()).filter(flow -> flow.state() == source
                        && flow.handle().state() != FloatingMenuState.CLOSING && flow.handle().state() != FloatingMenuState.CLOSED).isPresent())
            context.setState(next);
    }

    static MenuScene.Builder createMenu(Component title, VehicleEditorRoute route) {
        var menu = MenuScene.screen("vehicle-models").stableAnchor()
                .content(VehicleEditorComposition.create(route));
        menu.information("heading", title).region("heading");
        return menu;
    }

    static MenuScene finishMenu(MenuScene.Builder menu, Component closeLabel) {
        menu.dismiss(closeLabel).region("navigation");
        return menu.build();
    }

    private void missingSelection(MenuScene.Builder menu, Player player) {
        menu.information("missing", text(player, Message.VEHICLE_MODEL_SELECTION_UNAVAILABLE)).region("summary");
    }

    private void componentMenu(MenuScene.Builder menu, Player player, VehicleModelDraft draft, String section, int index) {
        long revision = draft.revision();
        var target = new ComponentRef(Section.named(section), index);
        menu.control("value:position", text(player, Message.VEHICLE_MODEL_POSITION)
                .append(Component.text(" · " + VehicleEditorGeometry.display(draft.position(section, index, false)))))
                .region("items").primary((actor, _) -> run(actor, () -> {
                    requireCurrent(actor, draft, revision);
                    openPanel(actor, new Position(target, false));
                }));
        if (target.section() == Section.SEATS || target.section() == Section.COLLIDERS) {
            boolean seat = target.section() == Section.SEATS;
            VehicleVector value = seat ? draft.position(section, index, true) : draft.collider(index).halfSize().multiply(2);
            menu.control("value:" + (seat ? "exit" : "size"), text(player, seat ? Message.VEHICLE_MODEL_EXIT : Message.VEHICLE_MODEL_SIZE)
                    .append(Component.text(" · " + VehicleEditorGeometry.display(value))))
                    .region("items").primary((actor, _) -> run(actor, () -> {
                        requireCurrent(actor, draft, revision);
                        openPanel(actor, seat ? new Position(target, true) : new Transform(target, VehicleEditorTransform.Mode.SIZE));
                    }));
        }
        action(menu, player, "copy", Message.VEHICLE_MODEL_COPY, actor -> {
            requireCurrent(actor, draft, revision);
            executeCopy(actor, section, index);
        });
        List<String> operations = switch (section) {
            case "parts" -> List.of("animation", "block", "item", "text", "option", "remove");
            case "seats" -> List.of("driver", "remove");
            case "colliders" -> List.of("remove");
            default -> List.of("remove");
        };
        for (String operation : operations) {
            Component label = List.of("block", "item", "text").contains(operation)
                    ? language.text(player, Message.VEHICLE_MODEL_CONTENT_ACTION, NamedTextColor.AQUA, operation)
                    : text(player, operationMessage(operation));
            if (operation.equals("animation")) label = label.append(Component.text(" · "))
                    .append(language.text(player, Message.VEHICLE_MODEL_CHOICE, NamedTextColor.WHITE, draft.parts().get(index).animation().name()));
            var control = menu.control(operation, label).region(operation.equals("remove") ? "actions" : "items").primary((actor, _) -> run(actor, () -> {
                    requireCurrent(actor, draft, revision);
                    switch (operation) {
                        case "remove" -> removeComponent(actor, draft, section, index);
                        case "animation" -> screen.open(actor, VehicleEditorView.open(new Choices(new ComponentRef(Section.PARTS, index), VehicleEditorProperty.ANIMATION)));
                        case "option" -> screen.open(actor, VehicleEditorView.open(new Properties(new ComponentRef(Section.PARTS, index))));
                        case "driver" -> mutate(actor, next -> next.seat("driver", index, null, true));
                        case "item" -> mutate(actor, next -> next.changePart(index, "item", itemPayload(actor)));
                        case "block" -> payloadForm(actor, index, VehicleDefinition.PartKind.BLOCK);
                        case "text" -> payloadForm(actor, index, VehicleDefinition.PartKind.TEXT);
                        default -> throw new IllegalStateException(operation);
                    }
                }));
            if (operation.equals("driver") && Boolean.parseBoolean(draft.field("seats." + index + ".driver")))
                control.selected(true).disabled(text(player, Message.VEHICLE_MODEL_DRIVER));
        }
    }

    private void executeCopy(Player player, String section, int index) {
        run(player, () -> {
            int target = draft(player).copy(section, index);
            var route = new Position(new ComponentRef(Section.named(section), target), false);
            if (spatial.containsKey(player.getUniqueId())) spatial.put(player.getUniqueId(), route);
            changed(player);
            screen.open(player, VehicleEditorView.open(route));
        });
    }

    private void removeComponent(Player player, VehicleModelDraft draft, String section, int index) {
        long revision = draft.revision();
        UUID session = sessions.get(player.getUniqueId());
        long expectedGeneration = generation;
        MenuDialogs.openConfirm(plugin, player, text(player, Message.VEHICLE_MODEL_REMOVE),
                text(player, Message.VEHICLE_MODEL_REMOVE_WARNING).append(Component.newline()).append(Component.text(draft.describe(section, index))),
                text(player, Message.VEHICLE_MODEL_REMOVE), text(player, Message.CLOSE), actor -> run(actor, () -> {
                    requireCurrent(actor, draft, revision);
                    requireSession(actor, session, expectedGeneration);
                    mutate(actor, next -> next.remove(section, index));
                    screen.open(actor, VehicleEditorView.open(new Components(Section.named(section), index)));
                }));
    }

    private UiNode transformMenu(MenuScene.Builder menu, Player player, VehicleModelDraft draft, Transform state,
                                 VehicleEditorView view, Consumer<VehicleEditorView> read) {
        var mode = state.mode();
        var preferences = transformSteps.computeIfAbsent(player.getUniqueId(), _ -> new java.util.EnumMap<>(VehicleEditorTransform.Mode.class));
        double step = preferences.getOrDefault(mode, VehicleEditorTransform.defaultStep(mode));
        VehicleVector value = VehicleEditorTransform.value(draft, mode, state.index());
        Message title = operationMessage(state.mode().name().toLowerCase(Locale.ROOT));
        Message hint = switch (mode) {
            case ROTATE -> Message.VEHICLE_MODEL_ROTATE_HINT;
            case SCALE -> Message.VEHICLE_MODEL_SCALE_HINT;
            case SIZE -> Message.VEHICLE_MODEL_SIZE_HINT;
        };
        menu.information("summary", componentIdentity(player, draft, state.section(), state.index()).append(Component.newline())
                .append(text(player, title)).append(Component.text(
                        mode == VehicleEditorTransform.Mode.ROTATE ? "" : " · " + VehicleEditorGeometry.display(value)))
                .append(Component.newline()).append(text(player, hint))).region("summary");
        long revision = draft.revision();
        var axes = new ArrayList<UiElement>();
        for (int axis = 0; axis < 3; axis++) for (int direction : List.of(-1, 1)) {
            int selectedAxis = axis;
            String operator = mode == VehicleEditorTransform.Mode.SCALE ? direction < 0 ? " ÷ " : " × " : direction < 0 ? " − " : " + ";
            String label = List.of("X", "Y", "Z").get(axis) + operator + step + (mode == VehicleEditorTransform.Mode.ROTATE ? "°" : "");
            axes.add(Ui.button(Component.text(label)).key("adjust:" + axis + ":" + direction).singleLine()
                    .primary((actor, _) -> run(actor, () -> {
                        requireCurrent(actor, draft, revision);
                        VehicleEditorTransform.adjust(draft, mode, state.index(), selectedAxis, direction, step);
                        changed(actor);
                    })).build());
        }
        if (mode == VehicleEditorTransform.Mode.SCALE) for (int direction : List.of(-1, 1))
            menu.control("uniform:" + direction, language.text(player, Message.VEHICLE_MODEL_SCALE_ALL, NamedTextColor.AQUA,
                    direction < 0 ? "÷" : "×", step)).region("actions").primary((actor, _) -> run(actor, () -> {
                        requireCurrent(actor, draft, revision);
                        VehicleEditorTransform.scaleAll(draft, state.index(), direction, step);
                        changed(actor);
                    }));
        menu.control("step", text(player, Message.VEHICLE_MODEL_TRANSFORM_STEP).append(Component.text(" · " + step)))
                .region("precision").primary((actor, _) -> run(actor, () -> {
                    requireCurrent(actor, draft, revision);
                    preferences.put(mode, VehicleEditorTransform.nextStep(mode, step));
                    redraw(actor);
                }));
        String initial = mode == VehicleEditorTransform.Mode.ROTATE ? "0 0 0" : mode == VehicleEditorTransform.Mode.SCALE ? "1 1 1" : VehicleEditorGeometry.coordinates(value);
        action(menu, player, "manual", Message.VEHICLE_MODEL_EXACT_INPUT, "precision", actor -> {
            requireCurrent(actor, draft, revision);
            VehicleVector vector = VehicleModelDraft.vector(initial);
            editForm(actor, draft, text(actor, title), text(actor, hint), VehicleEditorForms.vector(language, actor, vector, ""), values -> next -> {
                VehicleVector input = VehicleEditorForms.vector(values, "");
                if (mode == VehicleEditorTransform.Mode.SIZE) next.size(state.index(), input);
                else next.changePart(state.index(), mode.name().toLowerCase(Locale.ROOT), VehicleEditorGeometry.coordinates(input));
            });
        });
        return VehicleEditorComposition.axes(axes, axisReadings(player, mode == VehicleEditorTransform.Mode.ROTATE ? null : value), view, read);
    }

    private UiNode positionMenu(MenuScene.Builder menu, Player player, VehicleModelDraft draft, Position state,
                                VehicleEditorView view, Consumer<VehicleEditorView> read) {
        boolean exit = state.exit();
        VehicleVector position = draft.position(state.section(), state.index(), exit);
        menu.information("summary", componentIdentity(player, draft, state.section(), state.index()).append(Component.newline())
                .append(text(player, exit ? Message.VEHICLE_MODEL_EXIT : Message.VEHICLE_MODEL_POSITION))
                .append(Component.text(" · " + VehicleEditorGeometry.display(position)))
                .append(Component.newline()).append(text(player, Message.VEHICLE_MODEL_POSITION_HINT))).region("summary");
        double step = steps.getOrDefault(player.getUniqueId(), 0.1);
        long revision = draft.revision();
        var axes = new ArrayList<UiElement>();
        for (int axis = 0; axis < 3; axis++) for (int direction : List.of(-1, 1)) {
            VehicleVector offset = switch (axis) {
                case 0 -> new VehicleVector(direction * step, 0, 0);
                case 1 -> new VehicleVector(0, direction * step, 0);
                default -> new VehicleVector(0, 0, direction * step);
            };
            String label = List.of("X", "Y", "Z").get(axis) + (direction < 0 ? " − " : " + ") + step;
            axes.add(Ui.button(Component.text(label)).key("nudge:" + axis + ":" + direction).singleLine()
                    .primary((actor, _) -> run(actor, () -> {
                        requireCurrent(actor, draft, revision);
                        draft.position(state.section(), state.index(), exit, position.add(offset));
                        changed(actor);
                    })).build());
        }
        action(menu, player, "here", Message.VEHICLE_MODEL_HERE, actor -> {
            requireCurrent(actor, draft, revision);
            draft.position(state.section(), state.index(), exit, here(actor));
            changed(actor);
        });
        action(menu, player, "look", Message.VEHICLE_MODEL_LOOK, actor -> {
            requireCurrent(actor, draft, revision);
            armLook(actor, new VehicleEditorPlacement.Position(state));
        });
        action(menu, player, "snap", snapping.contains(player.getUniqueId()) ? Message.VEHICLE_MODEL_SNAP_ON : Message.VEHICLE_MODEL_SNAP_OFF, this::snap);
        menu.control("step", text(player, Message.VEHICLE_MODEL_STEP).append(Component.text(" · " + step)))
                .region("precision").primary((actor, _) -> run(actor, () -> {
                    requireCurrent(actor, draft, revision);
                    steps.put(actor.getUniqueId(), step == 0.1 ? 0.25 : step == 0.25 ? 1.0 : 0.1);
                    redraw(actor);
                }));
        action(menu, player, "manual", Message.VEHICLE_MODEL_MANUAL, "precision", actor -> {
            requireCurrent(actor, draft, revision);
            editForm(actor, draft, text(actor, Message.VEHICLE_MODEL_POSITION), text(actor, Message.VEHICLE_MODEL_POSITION_HINT),
                    VehicleEditorForms.vector(language, actor, position, ""), values -> next -> next.position(state.section(), state.index(), exit, VehicleEditorForms.vector(values, "")));
        });
        menu.control("offset", language.text(player, Message.VEHICLE_MENU_LABEL, NamedTextColor.AQUA, "offset")).region("actions")
                .primary((actor, _) -> run(actor, () -> {
                    requireCurrent(actor, draft, revision);
                    editForm(actor, draft, language.text(actor, Message.VEHICLE_MENU_LABEL, NamedTextColor.AQUA, "offset"), text(actor, Message.VEHICLE_MODEL_POSITION_HINT),
                            VehicleEditorForms.vector(language, actor, VehicleVector.ZERO, ""), values -> next -> next.position(state.section(), state.index(), exit,
                                    position.add(VehicleEditorForms.vector(values, ""))));
                }));
        return VehicleEditorComposition.axes(axes, axisReadings(player, position), view, read);
    }

    private List<Component> axisReadings(Player player, VehicleVector value) {
        var colors = List.of(NamedTextColor.RED, NamedTextColor.GREEN, NamedTextColor.AQUA);
        return java.util.stream.IntStream.range(0, 3).mapToObj(axis -> value == null
                ? Component.text("XYZ".substring(axis, axis + 1), colors.get(axis))
                : language.text(player, Message.VEHICLE_MODEL_AXIS_VALUE, colors.get(axis), "XYZ".substring(axis, axis + 1),
                        java.math.BigDecimal.valueOf(axis == 0 ? value.coordinateX() : axis == 1 ? value.coordinateY() : value.coordinateZ())
                                .setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros())).toList();
    }

    private void changed(Player player) {
        placements.remove(player.getUniqueId());
        refreshPreview(player);
        redraw(player);
    }

    private void redraw(Player player) {
        screen.flow(player).ifPresent(flow -> flow.redraw());
    }

    private void armLook(Player player, VehicleEditorPlacement.Action action) {
        VehicleModelDraft draft = draft(player);
        placements.put(player.getUniqueId(), new Placement(new VehicleEditorPlacement(draft, draft.revision(), action,
                ticks + 6, System.nanoTime() + 30_000_000_000L), screen.state(player).orElseGet(() -> VehicleEditorView.open(Workspace.INSTANCE))));
        FloatingMenus.current(player).ifPresent(handle -> handle.close());
        result(player, language.t(player, Message.VEHICLE_MODEL_PLACE_HINT));
    }

    boolean place(Player player) {
        if (placementClicks.getOrDefault(player.getUniqueId(), -1L) >= ticks) return true;
        Placement placement = placements.get(player.getUniqueId());
        if (placement == null) return spatialClick(player);
        VehicleEditorPlacement operation = placement.operation();
        if (!operation.current(drafts.get(player.getUniqueId()), System.nanoTime()) || !player.hasPermission("mik.vehicle.admin") || player.isSneaking()) {
            placementClicks.put(player.getUniqueId(), ticks + 1);
            placements.remove(player.getUniqueId());
            result(player, language.t(player, Message.VEHICLE_MODEL_PLACE_CANCELLED));
            if (player.isSneaking() && operation.current(drafts.get(player.getUniqueId()), System.nanoTime()) && player.hasPermission("mik.vehicle.admin"))
                screen.open(player, placement.returnTo());
            return true;
        }
        if (!operation.ready(ticks)) return true;
        placementClicks.put(player.getUniqueId(), ticks + 1);
        run(player, () -> {
            requireCurrent(player, operation.draft(), operation.revision());
            switch (operation.action()) {
                case VehicleEditorPlacement.Target target -> {
                    switch (target) {
                        case PICK -> pick(player);
                        case FRONT -> front(player, VehicleEditorFacing.nearest(player.getLocation().getYaw() - previewOrigin(player).getYaw()));
                        case CENTER -> mutate(player, next -> next.set("center-of-mass", VehicleEditorGeometry.coordinates(sample(player, "look"))));
                        case CORNER_FIRST -> corner(player, false, true);
                        case CORNER_SECOND -> corner(player, true, true);
                    }
                }
                case VehicleEditorPlacement.Position position -> mutate(player, next -> next.position(position.route().section(), position.route().index(), position.route().exit(), sample(player, "look")));
            }
            placements.remove(player.getUniqueId());
            if (operation.action() != VehicleEditorPlacement.Target.PICK) screen.open(player, placement.returnTo());
        });
        return true;
    }

    private void historyAction(MenuScene.Builder menu, Player player, String id, Message label, boolean available, String region) {
        var source = draft(player); long revision = source.revision();
        var button = menu.control(id, text(player, label)).region(region)
                .primary((actor, _) -> run(actor, () -> { requireCurrent(actor, source, revision); history(actor, id.equals("redo")); }));
        if (!available) button.disabled(text(player, Message.VEHICLE_MODEL_HISTORY_EMPTY));
    }

    private void add(Player player, String section) {
        VehicleModelDraft draft = draft(player);
        var fields = new ArrayList<>(VehicleEditorForms.vector(language, player, VehicleVector.ZERO, ""));
        if (section.equals("colliders")) fields.addAll(VehicleEditorForms.vector(language, player, new VehicleVector(1, 1, 1), "size-"));
        editForm(player, draft, text(player, Message.VEHICLE_MODEL_ADD), text(player, sectionMessage(section)), fields, values -> next -> {
            VehicleVector position = VehicleEditorForms.vector(values, "");
            switch (section) {
                case "seats" -> next.seat("add", -1, position, false);
                case "colliders" -> next.collider("add", -1, new VehicleDefinition.Collider(position, VehicleEditorForms.vector(values, "size-").multiply(0.5)));
                case "supports" -> next.support("add", -1, position);
                default -> throw new IllegalArgumentException("Unsupported component form");
            }
        });
    }

    private Component fieldLabel(Player player, String field) { return VehicleEditorForms.label(language, player, field); }

    private Component componentLabel(Player player, VehicleModelDraft draft, String section, int index) {
        Component label = componentIdentity(player, draft, section, index);
        label = label.append(Component.text(" · " + VehicleEditorGeometry.display(draft.position(section, index, false))));
        if (section.equals("seats")) label = label.append(Component.newline()).append(text(player, Message.VEHICLE_MODEL_EXIT))
                .append(Component.text(" · " + VehicleEditorGeometry.display(draft.position(section, index, true))));
        if (section.equals("colliders")) label = label.append(Component.newline()).append(text(player, Message.VEHICLE_MODEL_SIZE))
                .append(Component.text(" · " + VehicleEditorGeometry.display(draft.collider(index).halfSize().multiply(2))));
        return label;
    }

    private Component componentIdentity(Player player, VehicleModelDraft draft, String section, int index) {
        String kind = section.equals("seats") ? Boolean.parseBoolean(draft.field("seats." + index + ".driver")) ? "driver" : "passenger" : section;
        Component label = language.text(player, Message.VEHICLE_MODEL_MARKER, NamedTextColor.AQUA, kind, index);
        if (section.equals("parts")) {
            var part = draft.parts().get(index);
            label = label.append(Component.text(" · ")).append(text(player, switch (part.kind()) {
                case BLOCK -> Message.VEHICLE_MODEL_BLOCK;
                case ITEM -> Message.VEHICLE_MODEL_ITEM;
                case TEXT -> Message.VEHICLE_MODEL_TEXT;
            })).append(Component.text(" · ")).append(language.text(player, Message.VEHICLE_MODEL_CHOICE, NamedTextColor.AQUA, part.animation().name()));
        }
        return label;
    }

    private void parameterForm(Player player, VehicleModelDraft draft, String field) {
        boolean vector = VehicleModelDraft.VECTORS.contains(field);
        List<MenuInputForm.Field> fields = vector ? VehicleEditorForms.vector(language, player, draft.vectorField(field), "")
                : List.of(MenuInputForm.Field.text("value", fieldLabel(player, field), VehicleEditorParameters.display(draft, field), 256));
        Component description = VehicleModelDraft.SCALARS.contains(field)
                ? language.text(player, Message.VEHICLE_MODEL_FIELD_RANGE, NamedTextColor.AQUA,
                        VehicleDefinition.SCALAR_LIMITS.get(field) * (field.equals("maximum-speed") ? 3.6 : 1))
                : field.equals("inertia") ? language.text(player, Message.VEHICLE_MODEL_FIELD_RANGE, NamedTextColor.AQUA, VehicleDefinition.INERTIA_LIMIT)
                : field.equals("engine.ratios") ? language.text(player, Message.VEHICLE_MODEL_PARAMETER_HELP, NamedTextColor.AQUA, "engine") : text(player, Message.VEHICLE_MODEL_POSITION_HINT);
        editForm(player, draft, fieldLabel(player, field), description, fields,
                values -> next -> next.set(field, vector ? VehicleEditorGeometry.coordinates(VehicleEditorForms.vector(values, ""))
                        : VehicleEditorParameters.stored(field, values.text("value").strip())));
    }

    private void propertyForm(Player player, VehicleModelDraft draft, int index, VehicleEditorProperty property) {
        editForm(player, draft, fieldLabel(player, property.key()), language.text(player, Message.VEHICLE_MODEL_PROPERTY_HELP, NamedTextColor.AQUA, property.key()),
                List.of(MenuInputForm.Field.text("value", fieldLabel(player, property.key()), property.value(draft.parts().get(index)), 64)),
                values -> next -> property.apply(next, index, values.text("value").strip()));
    }

    private void editForm(Player player, VehicleModelDraft draft, Component title, Component description, List<MenuInputForm.Field> fields,
                          Function<MenuInputForm.Values, Consumer<VehicleModelDraft>> edit) {
        long revision = draft.revision();
        long expectedGeneration = generation;
        UUID session = sessions.computeIfAbsent(player.getUniqueId(), _ -> UUID.randomUUID());
        VehicleEditorForms.open(player, language, draft, title, description, fields, edit, actor -> {
            requireAdmin(actor); requireCurrent(actor, draft, revision); requireSession(actor, session, expectedGeneration);
        }, (actor, action) -> run(actor, action::run), this::changed);
    }

    private UiNode centerMenu(MenuScene.Builder menu, Player player, VehicleModelDraft draft, VehicleEditorView view, Consumer<VehicleEditorView> read) {
        VehicleVector position = draft.vectorField("center-of-mass");
        menu.information("summary", text(player, Message.VEHICLE_MODEL_CENTER).append(Component.text(" · " + VehicleEditorGeometry.display(position))))
                .region("summary");
        double step = spatialStep(player, Center.INSTANCE);
        long revision = draft.revision();
        var axes = new ArrayList<UiElement>();
        for (int axis = 0; axis < 3; axis++) for (int direction : new int[]{-1, 1}) {
            VehicleVector offset = new VehicleVector(axis == 0 ? direction * step : 0, axis == 1 ? direction * step : 0, axis == 2 ? direction * step : 0);
            axes.add(Ui.button(Component.text("XYZ".charAt(axis) + (direction < 0 ? " − " : " + ") + step)).key("nudge:" + axis + ":" + direction).singleLine()
                    .primary((actor, _) -> run(actor, () -> {
                        requireCurrent(actor, draft, revision);
                        draft.set("center-of-mass", VehicleEditorGeometry.coordinates(position.add(offset)));
                        changed(actor);
                    })).build());
        }
        action(menu, player, "spatial", Message.VEHICLE_MODEL_SPATIAL, actor -> { requireCurrent(actor, draft, revision); startSpatial(actor, Center.INSTANCE); });
        action(menu, player, "manual", Message.VEHICLE_MODEL_MANUAL, "precision", actor -> { requireCurrent(actor, draft, revision); parameterForm(actor, draft, "center-of-mass"); });
        action(menu, player, "here", Message.VEHICLE_MODEL_CENTER_HERE, actor -> mutate(actor, next -> next.set("center-of-mass", VehicleEditorGeometry.coordinates(here(actor)))));
        action(menu, player, "look", Message.VEHICLE_MODEL_CENTER_LOOK, actor -> armLook(actor, VehicleEditorPlacement.Target.CENTER));
        menu.control("step", text(player, Message.VEHICLE_MODEL_STEP).append(Component.text(" · " + step))).region("precision").primary((actor, _) -> run(actor, () -> {
            requireCurrent(actor, draft, revision);
            steps.put(actor.getUniqueId(), step == 0.1 ? 0.25 : step == 0.25 ? 1.0 : 0.1);
            redraw(actor);
        }));
        return VehicleEditorComposition.axes(axes, axisReadings(player, position), view, read);
    }

    private void prompt(Player player, Message title, String label, String initial, Consumer<String> action) {
        VehicleModelDraft expected = drafts.get(player.getUniqueId());
        long revision = expected == null ? -1 : expected.revision();
        long expectedGeneration = generation;
        UUID session = sessions.computeIfAbsent(player.getUniqueId(), _ -> UUID.randomUUID());
        FloatingMenus.input(player, MenuInputForm.builder(text(player, title))
                .field(MenuInputForm.Field.text("value", Component.text(label), initial, 48))
                .validate((actor, values) -> {
                    try { requireAdmin(actor); requireCurrent(actor, expected, revision); requireSession(actor, session, expectedGeneration);
                        if (!values.text("value").strip().matches("[a-z0-9][a-z0-9_-]{0,47}")) throw new IllegalArgumentException("ID: a-z, 0-9, _ and -, at most 48 characters");
                        return null;
                    } catch (IllegalArgumentException | IllegalStateException error) { return Component.text(error.getMessage(), NamedTextColor.RED); }
                })
                .cancel(text(player, Message.CLOSE))
                .submit(text(player, Message.VEHICLE_MODEL_APPLY), (actor, inputValues) -> {
                    String value = inputValues.text("value").strip();
                    run(actor, () -> {
                    requireCurrent(actor, expected, revision);
                    requireSession(actor, session, expectedGeneration);
                    action.accept(value);
                });
                }).build());
    }

    void newModel(Player player, Source source) {
        requireAdmin(player);
        requireNewDraft(player);
        sessions.put(player.getUniqueId(), UUID.randomUUID());
        var menu = MenuScene.screen("vehicle-create").stableAnchor().content(UiTemplates.sheet(
                UiTemplates.window("main", UiTemplates.menu(UiTemplates.title("heading"), UiTemplates.actions("kinds", 1))),
                UiTemplates.toolbar("footer", UiTemplates.navigation("navigation"))));
        menu.information("heading", text(player, Message.VEHICLE_MODEL_CREATE)).region("heading");
        for (var kind : VehicleDefinition.Kind.values()) menu.control(kind.name(), language.text(player, Message.VEHICLE_MODEL_CHOICE, NamedTextColor.AQUA, kind.name()))
                .region("kinds").primary((actor, _) -> prompt(actor, Message.VEHICLE_MODEL_CREATE, "ID", "", id -> run(actor, () -> create(actor, id, kind, source))));
        menu.navigation("back", text(player, Message.BACK)).region("navigation").primary((actor, _) -> open(actor));
        menu.dismiss(text(player, Message.CLOSE)).region("navigation");
        FloatingMenus.open(player, menu.build());
    }

    private void spawnForm(Player player, VehicleDefinition definition) {
        var origin = player.getLocation();
        var forward = new VehicleVector(0, 0, 4).rotate(VehicleModel.orientation(origin.getYaw()));
        var initial = new VehicleVector(origin.getX(), origin.getY() + 1, origin.getZ()).add(forward);
        var expected = drafts.get(player.getUniqueId());
        long revision = expected == null ? -1 : expected.revision();
        UUID session = sessions.get(player.getUniqueId()); long expectedGeneration = generation;
        var builder = MenuInputForm.builder(text(player, Message.VEHICLE_MODEL_SPAWN))
                .description(language.text(player, Message.VEHICLE_MENU_LABEL, NamedTextColor.AQUA, "spawn-hint"))
                .cancel(text(player, Message.CLOSE)).validate((actor, values) -> {
                    try { requireAdmin(actor); requireCurrent(actor, expected, revision); requireSession(actor, session, expectedGeneration);
                        VehicleEditorForms.vector(values, ""); return null; }
                    catch (IllegalArgumentException exception) { return Component.text(exception.getMessage(), NamedTextColor.RED); }
                }).submit(text(player, Message.VEHICLE_MODEL_APPLY), (actor, values) -> run(actor, () -> {
                    requireCurrent(actor, expected, revision); requireSession(actor, session, expectedGeneration);
                    if (module.models().get(definition.id()) != definition) throw new IllegalArgumentException("Model changed; reopen it");
                    var point = VehicleEditorForms.vector(values, "");
                    module.spawn(actor, definition.id(), new Location(origin.getWorld(), point.coordinateX(), point.coordinateY(), point.coordinateZ(), origin.getYaw(), 0));
                    module.openMenu(actor);
                }));
        VehicleEditorForms.vector(language, player, initial, "").forEach(builder::field);
        FloatingMenus.input(player, builder.build());
    }

    private void payloadForm(Player player, int index, VehicleDefinition.PartKind kind) {
        var draft = draft(player);
        String initial = kind == VehicleDefinition.PartKind.BLOCK ? "minecraft:stone" : "";
        if (index >= 0 && draft.parts().get(index).kind() == kind) {
            initial = draft.parts().get(index).payload();
            if (kind == VehicleDefinition.PartKind.TEXT) initial = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(GsonComponentSerializer.gson().deserialize(initial));
        }
        editForm(player, draft, text(player, kind == VehicleDefinition.PartKind.BLOCK ? Message.VEHICLE_MODEL_BLOCK : Message.VEHICLE_MODEL_TEXT), Component.empty(),
                List.of(MenuInputForm.Field.text("value", text(player, kind == VehicleDefinition.PartKind.BLOCK ? Message.VEHICLE_MODEL_BLOCK : Message.VEHICLE_MODEL_TEXT), initial, 4096)
                        .multiline(kind == VehicleDefinition.PartKind.TEXT)), values -> next -> {
                    String value = values.text("value");
                    String payload = kind == VehicleDefinition.PartKind.BLOCK ? plugin.getServer().createBlockData(value).getAsString()
                            : GsonComponentSerializer.gson().serialize(Component.text(value));
                    if (index < 0) next.addParts(List.of(VehicleModelDraft.part(kind, payload)));
                    else next.changePart(index, kind.name().toLowerCase(Locale.ROOT), payload);
                });
    }

    private void discard(Player player) {
        run(player, () -> {
            VehicleModelDraft expected = draft(player);
            long revision = expected.revision();
            Consumer<Player> remove = actor -> run(actor, () -> {
                requireCurrent(actor, expected, revision);
                drafts.remove(actor.getUniqueId());
                draftOrigins.remove(actor.getUniqueId()); stages.remove(actor.getUniqueId());
                clearTools(actor.getUniqueId());
                clearPreview(actor.getUniqueId());
                open(actor);
                result(actor, language.t(actor, Message.VEHICLE_MODEL_DISCARDED));
            });
            if (!expected.dirty()) remove.accept(player);
            else MenuDialogs.openConfirm(plugin, player, text(player, Message.VEHICLE_MODEL_DISCARD),
                    text(player, Message.VEHICLE_MODEL_DISCARD_WARNING), text(player, Message.VEHICLE_MODEL_APPLY), text(player, Message.CLOSE), remove);
        });
    }

    private void delete(Player player, VehicleDefinition expected) {
        run(player, () -> {
            UUID session = sessions.computeIfAbsent(player.getUniqueId(), _ -> UUID.randomUUID());
            long expectedGeneration = generation;
            MenuDialogs.openConfirm(plugin, player, text(player, Message.VEHICLE_MODEL_DELETE),
                    text(player, Message.VEHICLE_MODEL_DELETE_WARNING).append(Component.text(" · " + expected.id())),
                    text(player, Message.VEHICLE_MODEL_DELETE), text(player, Message.CLOSE), actor -> run(actor, () -> {
                        requireSession(actor, session, expectedGeneration);
                        module.deleteModel(expected);
                        screen.redrawWhere(_ -> true);
                        result(actor, expected.id() + " · " + language.t(actor, Message.VEHICLE_MODEL_DELETED));
                    }));
        });
    }

    private void preview(Player player, VehicleModelDraft draft) {
        preview(player, draft, true);
    }

    private void preview(Player player, VehicleModelDraft draft, boolean announce) {
        previews.show(player, draft, previewOrigin(player), stages.get(player.getUniqueId()).scale());
        updateScene(player, draft);
        if (announce) result(player, language.t(player, Message.VEHICLE_MODEL_PREVIEW_HINT));
    }

    private void refreshPreview(Player player) {
        if (previews.get(player.getUniqueId()) == null) return;
        try { preview(player, draft(player), false); }
        catch (RuntimeException exception) {
            clearPreview(player.getUniqueId());
            previewError(player, exception);
        }
    }

    private void previewError(Player player, RuntimeException exception) {
        player.sendMessage(language.text(player, Message.VEHICLE_ERROR, NamedTextColor.RED,
                language.t(player, Message.VEHICLE_MODEL_PREVIEW_FAILED, exception.getMessage())));
    }

    private void pick(Player player) {
        draft(player);
        VehicleEditorPreview.Preview preview = previews.get(player.getUniqueId());
        if (preview == null) throw new IllegalArgumentException(language.t(player, Message.VEHICLE_MODEL_PICK_HINT));
        Aim target = aim(player);
        int index = target == null || target.display() == null ? -1 : preview.displays().indexOf(target.display());
        if (index < 0) throw new IllegalArgumentException(language.t(player, Message.VEHICLE_MODEL_PICK_HINT));
        placements.remove(player.getUniqueId());
        screen.open(player, VehicleEditorView.open(new Inspector(new ComponentRef(Section.PARTS, index))));
    }

    private void guides(Player player, VehicleEditorPreview.Preview preview, VehicleModelDraft draft) {
        updateScene(player, draft);
        if (spatial.containsKey(player.getUniqueId())) {
            var target = pointedHandle(player, previews.get(player.getUniqueId()));
            player.sendActionBar(target == null ? text(player, Message.VEHICLE_MODEL_SPATIAL_HINT)
                    : language.text(player, target.label(), NamedTextColor.YELLOW, target.arguments().toArray()));
            if (target != null) VehicleEditorPreview.marker(player, preview.origin(), target.point().multiply(preview.scale()), VehicleEditorScene.Ink.WHITE);
        }
        if (placements.containsKey(player.getUniqueId())) {
            try {
                Aim aim = aim(player);
                if (aim != null && (placements.get(player.getUniqueId()).operation().action() != VehicleEditorPlacement.Target.PICK || aim.display() != null))
                    VehicleEditorPreview.marker(player, preview.origin(), sample(player, "look").multiply(preview.scale()), VehicleEditorScene.Ink.YELLOW);
            } catch (IllegalArgumentException ignored) { }
        }
        Corner corner = corners.get(player.getUniqueId());
        if (corner == null) return;
        VehicleEditorPreview.marker(player, preview.origin(), corner.point().multiply(preview.scale()), VehicleEditorScene.Ink.ORANGE);
        try {
            VehicleVector candidate = sample(player, corner.look() ? "look" : "here");
            for (VehicleVector point : VehicleEditorGeometry.outline(VehicleEditorGeometry.box(corner.point(), candidate)))
                VehicleEditorPreview.marker(player, preview.origin(), point.multiply(preview.scale()), VehicleEditorScene.Ink.ORANGE);
        } catch (IllegalArgumentException ignored) { }
    }

    void tick() {
        ticks++;
        placementClicks.entrySet().removeIf(entry -> entry.getValue() < ticks);
        for (UUID id : List.copyOf(placements.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.hasPermission("mik.vehicle.admin")
                    || !placements.get(id).operation().current(drafts.get(id), System.nanoTime())) {
                placements.remove(id);
                if (player != null) result(player, language.t(player, Message.VEHICLE_MODEL_PLACE_CANCELLED));
            }
        }
        for (UUID id : previews.viewers()) {
            VehicleEditorPreview.Preview preview = previews.get(id);
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.hasPermission("mik.vehicle.admin") || System.nanoTime() >= preview.expires()
                    || !player.getWorld().equals(preview.origin().getWorld()) || player.getLocation().distanceSquared(preview.origin()) > 1024
                    || preview.displays().stream().anyMatch(display -> !display.isValid())) clearPreview(id);
            else if (ticks % 10 == 0) {
                VehicleModelDraft source = drafts.get(id);
                if (source != null) guides(player, preview, source);
            }
        }
    }

    void enable() { closed = false; }
    void unload(org.bukkit.World world, Integer chunkX, Integer chunkZ) {
        for (UUID id : previews.viewers()) {
            Location origin = previews.get(id).origin();
            if (origin.getWorld().equals(world) && (chunkX == null || (origin.getBlockX() >> 4) == chunkX && (origin.getBlockZ() >> 4) == chunkZ)) clearPreview(id);
        }
    }

    VehicleEditorPreview.Preview preview(Player player) { return previews.get(player.getUniqueId()); }
    boolean isPreview(Entity entity) { return previews.isPreview(entity); }
    java.util.Set<UUID> previewIds(UUID viewer) { return previews.ids(viewer); }
    void clearPreview(UUID id) { placements.remove(id); spatial.remove(id); previews.clear(id); }
    private void clearTools(UUID id) { corners.remove(id); steps.remove(id); transformSteps.remove(id); snapping.remove(id); placements.remove(id); placementClicks.remove(id); spatial.remove(id); }
    void forget(Player player) { clearPreview(player.getUniqueId()); drafts.remove(player.getUniqueId()); draftOrigins.remove(player.getUniqueId()); stages.remove(player.getUniqueId()); clearTools(player.getUniqueId()); sessions.remove(player.getUniqueId()); screen.forget(player); }
    void close() { closed = true; generation++; for (UUID id : List.copyOf(sessions.keySet())) screen.forget(id); previews.close(); drafts.clear(); draftOrigins.clear(); stages.clear(); sessions.clear(); corners.clear(); steps.clear(); transformSteps.clear(); snapping.clear(); placements.clear(); placementClicks.clear(); spatial.clear(); }

    private Location sourceOrigin(Player player) { return VehicleEditorGeometry.oriented(draftOrigins.get(player.getUniqueId()), draft(player).frameTurns()); }

    private Location previewOrigin(Player player) {
        var stage = stages.get(player.getUniqueId());
        Location origin = stage == null ? null : stage.origin();
        if (origin == null) throw new IllegalArgumentException("Create or edit a model first");
        VehicleEditorGeometry.local(origin, player.getLocation());
        return VehicleEditorGeometry.oriented(origin, draft(player).frameTurns());
    }

    private VehicleModelDraft draft(Player player) { VehicleModelDraft draft = drafts.get(player.getUniqueId()); if (draft == null) throw new IllegalArgumentException("Create or edit a model first"); return draft; }
    private VehicleDefinition model(String id) { VehicleDefinition model = module.models().get(id); if (model == null) throw new IllegalArgumentException("Unknown model: " + id); return model; }
    private void requireAdmin(Player player) { if (closed || !player.isOnline() || !player.hasPermission("mik.vehicle.admin")) throw new IllegalArgumentException("mik.vehicle.admin required"); }
    private void requireCurrent(Player player, VehicleModelDraft expected, long revision) {
        if (drafts.get(player.getUniqueId()) != expected || expected != null && expected.revision() != revision)
            throw new IllegalArgumentException("Editor changed; reopen the form");
    }
    private void requireSession(Player player, UUID session, long expectedGeneration) {
        if (expectedGeneration != generation || !session.equals(sessions.get(player.getUniqueId())))
            throw new IllegalArgumentException("Editor closed; reopen the form");
    }
    private void run(Player player, Operation operation) {
        try { requireAdmin(player); operation.run(); }
        catch (IllegalArgumentException | IllegalStateException exception) { player.sendMessage(language.text(player, Message.VEHICLE_ERROR, NamedTextColor.RED,
                exception instanceof VehicleImportException imported ? imported.describe(language, player) : exception.getMessage())); }
        catch (IOException exception) { plugin.getLogger().log(Level.SEVERE, "Vehicle model storage failed", exception); player.sendMessage(language.text(player, Message.VEHICLE_ERROR, NamedTextColor.RED, "Storage failed; draft retained: " + exception.getMessage())); }
    }
    private Component text(Player player, Message message) { return language.text(player, message, NamedTextColor.AQUA); }
    private void result(Player player, String message) { player.sendMessage(language.text(player, Message.VEHICLE_RESULT, NamedTextColor.GREEN, message)); }
    private void action(MenuScene.Builder menu, Player player, String id, Message label, PlayerAction action) {
        action(menu, player, id, label, "actions", action);
    }
    private void action(MenuScene.Builder menu, Player player, String id, Message label, String region, PlayerAction action) {
        var source = drafts.get(player.getUniqueId());
        long revision = source == null ? -1 : source.revision();
        var view = screen.state(player).orElse(null);
        menu.control(id, language.text(player, label, region.equals("commit") && id.equals("apply") ? NamedTextColor.GREEN : NamedTextColor.AQUA))
                .region(region).primary((actor, _) -> run(actor, () -> {
            if (screen.state(actor).orElse(null) != view) throw new IllegalArgumentException("Editor page changed");
            requireCurrent(actor, source, revision);
            action.run(actor);
        }));
    }
    private static Message sectionMessage(String section) {
        return switch (section) { case "parts" -> Message.VEHICLE_MODEL_PARTS; case "seats" -> Message.VEHICLE_MODEL_SEATS;
            case "colliders" -> Message.VEHICLE_MODEL_COLLIDERS; case "supports" -> Message.VEHICLE_MODEL_SUPPORTS;
            case "tools" -> Message.VEHICLE_MODEL_TOOLS; default -> Message.VEHICLE_MODEL_PARAMETERS; };
    }
    private static Message operationMessage(String operation) {
        return switch (operation) {
            case "rotate" -> Message.VEHICLE_MODEL_ROTATE;
            case "scale" -> Message.VEHICLE_MODEL_SCALE;
            case "size" -> Message.VEHICLE_MODEL_SIZE;
            case "animation" -> Message.VEHICLE_MODEL_ANIMATION;
            case "block" -> Message.VEHICLE_MODEL_BLOCK;
            case "item" -> Message.VEHICLE_MODEL_ITEM;
            case "text" -> Message.VEHICLE_MODEL_TEXT;
            case "option" -> Message.VEHICLE_MODEL_OPTIONS;
            case "driver" -> Message.VEHICLE_MODEL_DRIVER;
            default -> Message.VEHICLE_MODEL_REMOVE;
        };
    }
}
