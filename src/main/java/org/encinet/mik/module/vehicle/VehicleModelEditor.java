package org.encinet.mik.module.vehicle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuContext;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuPage;
import org.encinet.mik.module.menu.FloatingMenuScreen;
import org.encinet.mik.module.menu.MenuDialogs;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

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
import java.util.logging.Level;

final class VehicleModelEditor {
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(255, 85, 85), 0.8f);
    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.fromRGB(85, 255, 85), 0.8f);
    private static final Particle.DustOptions BLUE = new Particle.DustOptions(Color.fromRGB(85, 170, 255), 0.8f);
    private static final Particle.DustOptions ORANGE = new Particle.DustOptions(Color.fromRGB(255, 170, 0), 0.8f);
    private static final Particle.DustOptions YELLOW = new Particle.DustOptions(Color.fromRGB(255, 255, 85), 0.8f);
    private static final Particle.DustOptions PURPLE = new Particle.DustOptions(Color.fromRGB(255, 85, 255), 0.8f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.WHITE, 0.8f);
    private record State(String view, int page, String id) { }
    private record Corner(VehicleVector point, boolean look) { }
    private record Placement(VehicleEditorPlacement operation, State returnTo) { }
    private record Aim(Location point, Display display) { }
    private record Preview(Location origin, List<Display> displays, List<VehicleDefinition.Part> parts, long expires) { }
    @FunctionalInterface private interface Operation { void run() throws IOException; }
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final VehicleModule module;
    private final NamespacedKey previewKey;
    private final Map<UUID, VehicleModelDraft> drafts = new HashMap<>();
    private final Map<UUID, Location> draftOrigins = new HashMap<>();
    private final Map<UUID, Preview> previews = new HashMap<>();
    private final Map<UUID, UUID> sessions = new HashMap<>();
    private final Map<UUID, Corner> corners = new HashMap<>();
    private final Map<UUID, Double> steps = new HashMap<>();
    private final Map<UUID, Map<VehicleEditorTransform.Mode, Double>> transformSteps = new HashMap<>();
    private final Set<UUID> snapping = new java.util.HashSet<>();
    private final Map<UUID, State> focused = new HashMap<>();
    private final Map<UUID, Placement> placements = new HashMap<>();
    private final Map<UUID, Long> placementClicks = new HashMap<>();
    private final FloatingMenuScreen<State> screen;
    private boolean closed;
    private long generation;
    private long ticks;

    VehicleModelEditor(JavaPlugin plugin, LanguageService language, VehicleModule module) {
        this.plugin = plugin;
        this.language = language;
        this.module = module;
        previewKey = new NamespacedKey(plugin, "vehicle_model_preview");
        screen = new FloatingMenuScreen<>("vehicle-models", this::render);
    }

    void execute(Player player, String input) throws IOException {
        execute(player, input.isBlank() ? new String[]{"menu"} : input.strip().split("\\s+"));
    }

    void execute(Player player, String[] args) throws IOException {
        requireAdmin(player);
        if (args.length > 1 && args[1].equals("copy") && Set.of("part", "seat", "collider", "support").contains(args[0])) {
            exact(args, 3);
            String section = switch (args[0]) { case "part" -> "parts"; case "seat" -> "seats"; case "collider" -> "colliders"; default -> "supports"; };
            int target = draft(player).copy(section, index(args, 2));
            State state = new State("position", target, section);
            focused.put(player.getUniqueId(), state);
            screen.update(player, ignored -> state);
            changed(player);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> { open(player); return; }
            case "help" -> { player.sendMessage(text(player, Message.VEHICLE_MODEL_HELP)); return; }
            case "list" -> { result(player, String.join(", ", module.models().keySet().stream().sorted().toList())); return; }
            case "info" -> {
                VehicleModelDraft draft = args.length == 1 ? draft(player) : new VehicleModelDraft(model(argument(args, 1)));
                result(player, draft.id() + " · " + draft.kind());
                for (String section : List.of("parts", "seats", "colliders", "supports"))
                    for (int index = 0; index < draft.count(section); index++) result(player, section + " " + draft.describe(section, index));
                return;
            }
            case "create" -> {
                exact(args, 3);
                begin(player, VehicleModelDraft.create(args[1], VehicleDefinition.parseKind(args[2])));
                open(player);
                return;
            }
            case "import" -> {
                if (args.length != 3 && args.length != 4) throw new IllegalArgumentException("import <id> <car|boat|plane> [group-uuid]");
                requireNewDraft(player);
                VehicleModule.ModelImport imported = module.importGroup(player, args[1], VehicleDefinition.parseKind(args[2]), args.length == 4 ? UUID.fromString(args[3]) : null);
                begin(player, VehicleModelDraft.imported(imported.definition()), imported.origin());
                open(player);
                return;
            }
            case "edit" -> {
                exact(args, 2);
                VehicleModelDraft existing = drafts.get(player.getUniqueId());
                if (existing == null || !existing.id().equals(args[1])) begin(player, new VehicleModelDraft(model(args[1])));
                open(player);
                return;
            }
            case "clone" -> { exact(args, 3); begin(player, VehicleModelDraft.copy(model(args[1]), args[2])); open(player); return; }
            case "anchor" -> {
                exact(args, 1);
                draft(player);
                Location origin = player.getLocation().clone();
                origin.setPitch(0);
                Location previousOrigin = draftOrigins.get(player.getUniqueId());
                draftOrigins.put(player.getUniqueId(), origin);
                try { preview(player, draft(player)); }
                catch (RuntimeException exception) { draftOrigins.put(player.getUniqueId(), previousOrigin); throw exception; }
                corners.remove(player.getUniqueId());
                placements.remove(player.getUniqueId());
                sessions.put(player.getUniqueId(), UUID.randomUUID());
                redraw(player);
                return;
            }
            case "pick" -> { exact(args, 1); pick(player); return; }
            case "snap" -> {
                draft(player);
                if (args.length > 2 || args.length == 2 && !Set.of("on", "off").contains(args[1]))
                    throw new IllegalArgumentException("snap [on|off]");
                boolean enabled = args.length == 1 ? !snapping.contains(player.getUniqueId()) : args[1].equals("on");
                if (enabled) snapping.add(player.getUniqueId()); else snapping.remove(player.getUniqueId());
                redraw(player);
                result(player, language.t(player, enabled ? Message.VEHICLE_MODEL_SNAP_ON : Message.VEHICLE_MODEL_SNAP_OFF));
                return;
            }
            case "check" -> {
                exact(args, 1);
                draft(player).validate();
                result(player, language.t(player, Message.VEHICLE_MODEL_CHECKED));
                return;
            }
            case "apply" -> {
                exact(args, 1);
                VehicleModelDraft draft = draft(player);
                draft.saved(module.saveModel(draft));
                refreshPreview(player);
                screen.redrawWhere(ignored -> true);
                result(player, language.t(player, Message.VEHICLE_MODEL_SAVED));
                return;
            }
            case "here", "look" -> {
                exact(args, 1);
                State focus = focused.get(player.getUniqueId());
                if (focus == null) throw new IllegalArgumentException(language.t(player, Message.VEHICLE_MODEL_PICK_HINT));
                draft(player).position(focus.id(), focus.page(), focus.view().equals("position-exit"), sample(player, args[0]));
            }
            case "set" -> draft(player).set(argument(args, 1), args.length == 3 && args[1].equals("center-of-mass") && Set.of("here", "look").contains(args[2])
                    ? VehicleEditorGeometry.coordinates(sample(player, args[2])) : tail(args, 2));
            case "part" -> part(player, args);
            case "seat" -> { if (!positionCommand(player, "seats", args)) seat(draft(player), expandHere(player, args)); }
            case "collider" -> {
                argument(args, 1);
                if (args.length == 2 && args[1].equals("cancel")) {
                    draft(player);
                    corners.remove(player.getUniqueId());
                    redraw(player);
                    return;
                }
                if (args[1].equals("corner1")) {
                    if (args.length != 2 && !(args.length == 3 && args[2].equals("look"))) throw new IllegalArgumentException("collider corner1 [look]");
                    draft(player);
                    corners.put(player.getUniqueId(), new Corner(sample(player, args.length == 3 ? "look" : "here"), args.length == 3));
                    if (!previews.containsKey(player.getUniqueId())) {
                        try { preview(player, draft(player)); } catch (RuntimeException exception) { previewError(player, exception); }
                    }
                    redraw(player);
                    result(player, language.t(player, Message.VEHICLE_MODEL_CORNER_HINT));
                    return;
                }
                if (args[1].equals("corner2")) {
                    if (args.length != 2 && !(args.length == 3 && args[2].equals("look"))) throw new IllegalArgumentException("collider corner2 [look]");
                    Corner first = corners.get(player.getUniqueId());
                    if (first == null) throw new IllegalArgumentException(language.t(player, Message.VEHICLE_MODEL_CORNER_REQUIRED));
                    draft(player).collider("add", -1, VehicleEditorGeometry.box(first.point(), sample(player, args.length == 3 ? "look" : "here")));
                    corners.remove(player.getUniqueId());
                } else if (!positionCommand(player, "colliders", args)) collider(draft(player), args);
            }
            case "support" -> { if (!positionCommand(player, "supports", args)) support(draft(player), expandHere(player, args)); }
            case "undo", "redo" -> {
                exact(args, 1);
                VehicleModelDraft draft = draft(player);
                List<String> sections = List.of("parts", "seats", "colliders", "supports");
                List<Integer> before = sections.stream().map(draft::count).toList();
                boolean restored = args[0].equals("redo") ? draft.redo() : draft.undo();
                if (!restored) throw new IllegalArgumentException(language.t(player, Message.VEHICLE_MODEL_HISTORY_EMPTY));
                if (!before.equals(sections.stream().map(draft::count).toList())) {
                    focused.remove(player.getUniqueId());
                    screen.update(player, ignored -> new State("editor", 0, null));
                }
            }
            case "save" -> {
                exact(args, 1);
                module.saveModel(draft(player));
                drafts.remove(player.getUniqueId());
                draftOrigins.remove(player.getUniqueId());
                clearTools(player.getUniqueId());
                clearPreview(player.getUniqueId());
                screen.redrawWhere(ignored -> true);
                result(player, language.t(player, Message.VEHICLE_MODEL_SAVED));
                return;
            }
            case "cancel" -> { exact(args, 1); discard(player); return; }
            case "delete" -> { exact(args, 2); delete(player, model(args[1])); return; }
            case "preview" -> {
                if (args.length == 2 && args[1].equals("off")) clearPreview(player.getUniqueId());
                else { exact(args, 1); preview(player, draft(player)); }
                return;
            }
            default -> throw new IllegalArgumentException("Use /vehicle model help");
        }
        if (args.length > 1 && args[1].equals("remove")) {
            focused.remove(player.getUniqueId());
            screen.update(player, ignored -> new State("editor", 0, null));
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
        drafts.put(player.getUniqueId(), draft);
        draftOrigins.put(player.getUniqueId(), origin);
        clearTools(player.getUniqueId());
        try { preview(player, draft); }
        catch (RuntimeException exception) { previewError(player, exception); }
    }

    private void requireNewDraft(Player player) {
        if (drafts.containsKey(player.getUniqueId())) throw new IllegalArgumentException("Save or cancel the current draft first");
        if (drafts.size() >= 32) throw new IllegalArgumentException("At most 32 concurrent model editors");
    }

    private void part(Player player, String[] args) {
        VehicleModelDraft draft = draft(player);
        if (positionCommand(player, "parts", args)) return;
        String operation = argument(args, 1);
        if (operation.equals("add")) {
            String kind = argument(args, 2);
            if (kind.equals("selected")) { exact(args, 3); draft.addParts(module.selectedParts(player, previewOrigin(player))); }
            else if (kind.equals("group")) {
                if (args.length != 3 && args.length != 4) throw new IllegalArgumentException("part add group [group-uuid]");
                draft.addParts(module.groupParts(player, args.length == 4 ? UUID.fromString(args[3]) : null, previewOrigin(player)));
            }
            else draft.addParts(List.of(VehicleModelDraft.part(VehicleDefinition.PartKind.valueOf(kind.toUpperCase(Locale.ROOT)), payload(player, kind, args, 3))));
        } else if (operation.equals("remove")) { exact(args, 3); draft.remove("parts", index(args, 2)); }
        else {
            int index = index(args, 2);
            String value = Set.of("block", "item", "text").contains(operation) ? payload(player, operation, args, 3) : tail(args, 3);
            draft.changePart(index, operation, value);
        }
    }

    private VehicleVector here(Player player) {
        return sampled(player, player.getLocation());
    }

    private VehicleVector sampled(Player player, Location position) {
        VehicleVector local = VehicleEditorGeometry.local(previewOrigin(player), position);
        return snapping.contains(player.getUniqueId()) ? VehicleEditorGeometry.snap(local, steps.getOrDefault(player.getUniqueId(), 0.1)) : local;
    }

    private VehicleVector sample(Player player, String target) {
        return target.equals("look") ? sampled(player, lookPoint(player)) : here(player);
    }

    private Location lookPoint(Player player) {
        Aim target = aim(player);
        if (target != null) return target.point();
        throw new IllegalArgumentException(language.t(player, Message.VEHICLE_MODEL_LOOK_MISSING));
    }

    private Aim aim(Player player) {
        Location eye = player.getEyeLocation();
        var obstruction = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), 32, FluidCollisionMode.NEVER, true);
        double maximum = obstruction == null ? 32 : obstruction.getHitPosition().distance(eye.toVector());
        Preview preview = previews.get(player.getUniqueId());
        VehicleDisplayPicker.Hit hit = preview == null ? null
                : VehicleDisplayPicker.hit(eye, eye.getDirection(), preview.displays(), ignored -> false, maximum);
        if (hit != null) return new Aim(new Location(player.getWorld(), hit.position().coordinateX(), hit.position().coordinateY(), hit.position().coordinateZ()), hit.display());
        if (obstruction != null) return new Aim(obstruction.getHitPosition().toLocation(player.getWorld()), null);
        return null;
    }

    private String[] expandHere(Player player, String[] args) {
        int offset = args.length > 2 && Set.of("here", "look").contains(args[2]) ? 2 : args.length > 3 && Set.of("here", "look").contains(args[3]) ? 3 : -1;
        if (offset < 0) return args;
        List<String> expanded = new ArrayList<>(List.of(args).subList(0, offset));
        expanded.addAll(List.of(VehicleEditorGeometry.coordinates(sample(player, args[offset])).split(" ")));
        expanded.addAll(List.of(args).subList(offset + 1, args.length));
        return expanded.toArray(String[]::new);
    }

    private boolean positionCommand(Player player, String section, String[] args) {
        String operation = argument(args, 1);
        boolean exit = operation.equals("exit");
        if (!operation.equals("move") && !Set.of("position", "exit", "set").contains(operation)) return false;
        if (!operation.equals("move") && !(args.length == 4 && Set.of("here", "look").contains(args[3]))
                && !(section.equals("colliders") && operation.equals("position"))) return false;
        int index = index(args, 2);
        VehicleModelDraft draft = draft(player);
        VehicleVector position;
        if (args.length == 4 && Set.of("here", "look").contains(args[3])) position = sample(player, args[3]);
        else {
            exact(args, 6);
            position = VehicleModelDraft.vector(tail(args, 3));
            if (operation.equals("move")) position = draft.position(section, index, exit).add(position);
        }
        draft.position(section, index, exit, position);
        return true;
    }

    private String payload(Player player, String kind, String[] args, int offset) {
        return switch (kind) {
            case "block" -> plugin.getServer().createBlockData(tail(args, offset)).getAsString();
            case "text" -> GsonComponentSerializer.gson().serialize(Component.text(tail(args, offset)));
            case "item" -> {
                exact(args, offset);
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) throw new IllegalArgumentException("Hold an item in your main hand");
                yield Base64.getEncoder().encodeToString(item.serializeAsBytes());
            }
            default -> throw new IllegalArgumentException("Use block|item|text|selected");
        };
    }

    private void seat(VehicleModelDraft draft, String[] args) {
        String operation = argument(args, 1);
        if (operation.equals("remove")) { exact(args, 3); draft.remove("seats", index(args, 2)); }
        else if (operation.equals("driver")) { exact(args, 3); draft.seat(operation, index(args, 2), null, true); }
        else if (operation.equals("add")) {
            exact(args, 6);
            if (!Set.of("driver", "passenger").contains(args[5])) throw new IllegalArgumentException("seat add <x y z> <driver|passenger>");
            draft.seat(operation, -1, VehicleModelDraft.vector(String.join(" ", args[2], args[3], args[4])), args[5].equals("driver"));
        } else draft.seat(operation, index(args, 2), VehicleModelDraft.vector(tail(args, 3)), false);
    }

    private void collider(VehicleModelDraft draft, String[] args) {
        String operation = argument(args, 1);
        if (operation.equals("auto")) { exact(args, 2); draft.collider("auto", -1, null); }
        else if (operation.equals("remove")) { exact(args, 3); draft.remove("colliders", index(args, 2)); }
        else if (operation.equals("size")) { exact(args, 6); draft.size(index(args, 2), VehicleModelDraft.vector(tail(args, 3))); }
        else {
            int offset = operation.equals("add") ? 2 : 3;
            exact(args, offset + 6);
            VehicleVector center = VehicleModelDraft.vector(String.join(" ", args[offset], args[offset + 1], args[offset + 2]));
            VehicleVector half = VehicleModelDraft.vector(String.join(" ", args[offset + 3], args[offset + 4], args[offset + 5]));
            draft.collider(operation, offset == 2 ? -1 : index(args, 2), new VehicleDefinition.Collider(center, half));
        }
    }

    private void support(VehicleModelDraft draft, String[] args) {
        String operation = argument(args, 1);
        if (operation.equals("remove")) { exact(args, 3); draft.remove("supports", index(args, 2)); }
        else draft.support(operation, operation.equals("add") ? -1 : index(args, 2), VehicleModelDraft.vector(tail(args, operation.equals("add") ? 2 : 3)));
    }

    private void open(Player player) {
        placements.remove(player.getUniqueId());
        sessions.computeIfAbsent(player.getUniqueId(), ignored -> UUID.randomUUID());
        State initial = new State(drafts.containsKey(player.getUniqueId()) ? "editor" : "library", 0, null);
        State previous = screen.state(player).orElse(initial);
        screen.open(player, focused.containsKey(player.getUniqueId()) && !Set.of("library", "model").contains(previous.view()) ? previous : initial);
    }

    private FloatingMenuDefinition render(FloatingMenuContext<State> context) {
        Player player = context.player();
        State state = context.state();
        VehicleModelDraft draft = drafts.get(player.getUniqueId());
        FloatingMenuDefinition.Builder menu = createMenu(text(player, Message.VEHICLE_MODEL_TITLE));
        if (!player.hasPermission("mik.vehicle.admin") || closed) {
            menu.information("denied", Component.text("mik.vehicle.admin", NamedTextColor.RED)).region("summary");
        } else if (state.view().equals("library") || draft == null && !state.view().equals("model")) {
            List<String> ids = module.models().keySet().stream().sorted().toList();
            FloatingMenuPage page = new FloatingMenuPage(state.page(), ids.size(), 6);
            menu.information("summary", text(player, Message.VEHICLE_MODEL_LIBRARY)).region("summary");
            for (String id : page.slice(ids)) menu.item("model:" + id, Material.MINECART, Component.text(id)).region("items")
                    .primary((actor, handle) -> context.setState(new State("model", 0, id)));
            menu.pagination("pages", page, next -> context.setState(new State("library", next, null)));
            action(menu, player, "create", Material.EMERALD, Message.VEHICLE_MODEL_CREATE,
                    actor -> prompt(actor, Message.VEHICLE_MODEL_CREATE, "<id> <car|boat|plane>", "", "create "));
            action(menu, player, "import", Material.CHEST, Message.VEHICLE_MODEL_IMPORT,
                    actor -> prompt(actor, Message.VEHICLE_MODEL_IMPORT, "<id> <car|boat|plane> [group-uuid]", "", "import "));
            if (draft != null) action(menu, player, "resume", Material.WRITABLE_BOOK, Message.VEHICLE_MODEL_EDIT,
                    actor -> context.setState(new State("editor", 0, null)));
        } else if (state.view().equals("model")) {
            VehicleDefinition definition = module.models().get(state.id());
            if (definition != null) {
                menu.information("summary", Component.text(definition.id() + " · " + definition.kind() + " · " + definition.parts().size())).region("summary");
                action(menu, player, "edit", Material.WRITABLE_BOOK, Message.VEHICLE_MODEL_EDIT, actor -> run(actor, () -> execute(actor, "edit " + definition.id())));
                action(menu, player, "clone", Material.PAPER, Message.VEHICLE_MODEL_CLONE, actor -> prompt(actor, Message.VEHICLE_MODEL_CLONE, "<new-id>", "", "clone " + definition.id() + " "));
                action(menu, player, "delete", Material.BARRIER, Message.VEHICLE_MODEL_DELETE, actor -> delete(actor, definition));
            }
        } else if (state.view().equals("editor")) {
            menu.information("summary", Component.text(draft.id() + " · " + draft.kind() + " · ")
                    .append(text(player, draft.dirty() ? Message.VEHICLE_MODEL_DRAFT : Message.VEHICLE_MODEL_SAVED))).region("summary");
            for (String section : List.of("parts", "seats", "colliders", "supports", "parameters", "tools")) {
                Message label = sectionMessage(section);
                menu.item(section, Material.BOOK, text(player, label).append(Component.text(Set.of("parameters", "tools").contains(section) ? "" : " · " + draft.count(section))))
                        .region("items").primary((actor, handle) -> context.setState(new State(section, 0, null)));
            }
            commandAction(menu, player, "save", Material.EMERALD, Message.VEHICLE_MODEL_SAVE, "save");
            commandAction(menu, player, "apply", Material.WRITABLE_BOOK, Message.VEHICLE_MODEL_SAVE_CONTINUE, "apply");
            historyAction(menu, player, "undo", Message.VEHICLE_MODEL_UNDO, draft.canUndo());
            historyAction(menu, player, "redo", Message.VEHICLE_MODEL_REDO, draft.canRedo());
            commandAction(menu, player, "preview", Material.ENDER_EYE, Message.VEHICLE_MODEL_PREVIEW, "preview");
            action(menu, player, "discard", Material.BARRIER, Message.VEHICLE_MODEL_DISCARD, this::discard);
        } else if (state.view().equals("tools")) {
            menu.information("summary", text(player, Message.VEHICLE_MODEL_TOOLS)).region("summary");
            commandAction(menu, player, "anchor", Material.COMPASS, Message.VEHICLE_MODEL_ANCHOR, "anchor");
            action(menu, player, "pick", Material.SPYGLASS, Message.VEHICLE_MODEL_PICK, actor -> armLook(actor, "pick"));
            commandAction(menu, player, "center-here", Material.IRON_BLOCK, Message.VEHICLE_MODEL_CENTER_HERE, "set center-of-mass here");
            action(menu, player, "center-look", Material.SPYGLASS, Message.VEHICLE_MODEL_CENTER_LOOK, actor -> armLook(actor, "set", "center-of-mass", "look"));
            commandAction(menu, player, "check", Material.LIME_DYE, Message.VEHICLE_MODEL_CHECK, "check");
            commandAction(menu, player, "snap", Material.REPEATER, snapping.contains(player.getUniqueId()) ? Message.VEHICLE_MODEL_SNAP_ON : Message.VEHICLE_MODEL_SNAP_OFF, "snap");
        } else if (state.view().equals("position") || state.view().equals("position-exit")) {
            if (state.page() >= draft.count(state.id())) menu.information("missing", Component.text("Component removed; go back")).region("summary");
            else positionMenu(menu, player, draft, state);
        } else if (Set.of("rotate", "scale", "size").contains(state.view())) {
            if (state.page() >= draft.count(state.id())) menu.information("missing", Component.text("Component removed; go back")).region("summary");
            else transformMenu(menu, player, draft, state);
        } else if (state.view().equals("component")) {
            String section = state.id();
            int index = state.page();
            if (index >= draft.count(section)) {
                menu.information("missing", Component.text("Component removed; go back")).region("summary");
            } else {
                focused.put(player.getUniqueId(), state);
                menu.information("summary", Component.text(draft.describe(section, index))).region("summary");
                componentMenu(menu, player, draft, section, index);
            }
        } else {
            String section = state.view();
            menu.information("summary", text(player, sectionMessage(section))).region("summary");
            if (section.equals("parameters")) {
                List<String> fields = new ArrayList<>(VehicleModelDraft.SCALARS);
                fields.addAll(VehicleModelDraft.VECTORS);
                fields.addAll(List.of("kind", "engine.ratios"));
                fields.sort(String::compareTo);
                FloatingMenuPage page = new FloatingMenuPage(state.page(), fields.size(), 6);
                for (String field : page.slice(fields)) menu.item(field, Material.PAPER, Component.text(field + " · " + fieldValue(draft, field))).region("items")
                        .primary((actor, handle) -> prompt(actor, Message.VEHICLE_MODEL_PARAMETERS, field, fieldValue(draft, field), "set " + field + " "));
                menu.pagination("pages", page, next -> context.setState(new State(section, next, null)));
            } else {
                List<Integer> indices = java.util.stream.IntStream.range(0, draft.count(section)).boxed().toList();
                FloatingMenuPage page = new FloatingMenuPage(state.page(), indices.size(), 6);
                for (int index : page.slice(indices)) menu.item("component:" + index, Material.PAPER, Component.text(draft.describe(section, index))).region("items")
                        .primary((actor, handle) -> context.setState(new State("component", index, section)));
                menu.pagination("pages", page, next -> context.setState(new State(section, next, null)));
                action(menu, player, "add", Material.EMERALD, Message.VEHICLE_MODEL_ADD, actor -> add(actor, section));
                if (section.equals("seats")) {
                    commandAction(menu, player, "driver-here", Material.MINECART, Message.VEHICLE_MODEL_DRIVER_HERE, "seat add here driver");
                    commandAction(menu, player, "passenger-here", Material.OAK_BOAT, Message.VEHICLE_MODEL_PASSENGER_HERE, "seat add here passenger");
                }
                if (section.equals("supports")) commandAction(menu, player, "add-here", Material.COMPASS, Message.VEHICLE_MODEL_ADD_HERE, "support add here");
                if (section.equals("parts")) {
                    commandAction(menu, player, "selected", Material.GLASS, Message.VEHICLE_MODEL_SELECTED, "part add selected");
                    action(menu, player, "group", Material.CHEST, Message.VEHICLE_MODEL_GROUP,
                            actor -> prompt(actor, Message.VEHICLE_MODEL_GROUP, "[group-uuid]", "", "part add group "));
                }
                if (section.equals("colliders")) {
                    commandAction(menu, player, "auto", Material.IRON_BLOCK, Message.VEHICLE_MODEL_AUTO, "collider auto");
                    commandAction(menu, player, "corner1", Material.COMPASS, Message.VEHICLE_MODEL_CORNER_FIRST, "collider corner1");
                    action(menu, player, "corner1-look", Material.SPYGLASS, Message.VEHICLE_MODEL_CORNER_FIRST_LOOK, actor -> armLook(actor, "collider", "corner1", "look"));
                    if (corners.containsKey(player.getUniqueId())) {
                        commandAction(menu, player, "corner2", Material.IRON_BLOCK, Message.VEHICLE_MODEL_CORNER_SECOND, "collider corner2");
                        action(menu, player, "corner2-look", Material.SPYGLASS, Message.VEHICLE_MODEL_CORNER_SECOND_LOOK, actor -> armLook(actor, "collider", "corner2", "look"));
                        commandAction(menu, player, "cancel-corner", Material.BARRIER, Message.VEHICLE_MODEL_CORNER_CANCEL, "collider cancel");
                    }
                }
            }
        }
        menu.navigation("back", text(player, Message.BACK)).region("navigation").primary((actor, handle) -> {
            if (state.view().startsWith("position") || Set.of("rotate", "scale", "size").contains(state.view()))
                context.setState(new State("component", state.page(), state.id()));
            else context.setState(new State(state.view().equals("component") ? state.id()
                    : state.view().equals("library") || state.view().equals("model") || state.view().equals("editor") ? "library" : "editor", 0, null));
        });
        return finishMenu(menu, text(player, Message.CLOSE));
    }

    static FloatingMenuDefinition.Builder createMenu(Component title) {
        return FloatingMenuDefinition.screen("vehicle-models", title)
                .layout(FloatingMenuLayouts.menu(FloatingMenuLayouts.information("summary"), FloatingMenuLayouts.actions("items", 2),
                        FloatingMenuLayouts.navigation("pages"), FloatingMenuLayouts.actions("actions", 3), FloatingMenuLayouts.navigation("navigation")));
    }

    static FloatingMenuDefinition finishMenu(FloatingMenuDefinition.Builder menu, Component closeLabel) {
        menu.dismiss(closeLabel).region("navigation");
        return menu.build();
    }

    private void componentMenu(FloatingMenuDefinition.Builder menu, Player player, VehicleModelDraft draft, String section, int index) {
        long revision = draft.revision();
        action(menu, player, "adjust-position", Material.COMPASS, Message.VEHICLE_MODEL_POSITION,
                actor -> { requireCurrent(actor, draft, revision); screen.open(actor, new State("position", index, section)); });
        if (section.equals("seats")) action(menu, player, "adjust-exit", Material.OAK_DOOR, Message.VEHICLE_MODEL_EXIT,
                actor -> { requireCurrent(actor, draft, revision); screen.open(actor, new State("position-exit", index, section)); });
        if (section.equals("colliders")) action(menu, player, "adjust-size", Material.IRON_BLOCK, Message.VEHICLE_MODEL_SIZE,
                actor -> { requireCurrent(actor, draft, revision); screen.open(actor, new State("size", index, section)); });
        action(menu, player, "copy", Material.PAPER, Message.VEHICLE_MODEL_COPY, actor -> {
            requireCurrent(actor, draft, revision);
            executeCopy(actor, section, index);
        });
        List<String> operations = switch (section) {
            case "parts" -> List.of("rotate", "scale", "animation", "block", "item", "text", "option", "remove");
            case "seats" -> List.of("driver", "remove");
            case "colliders" -> List.of("remove");
            default -> List.of("remove");
        };
        for (String operation : operations) menu.item(operation, operation.equals("remove") ? Material.BARRIER : Material.PAPER,
                text(player, operationMessage(operation))).region("items").primary((actor, handle) -> run(actor, () -> {
                    requireCurrent(actor, draft, revision);
                    String command = singular(section) + " " + operation + " " + index;
                    if (Set.of("rotate", "scale").contains(operation)) {
                        screen.open(actor, new State(operation, index, section));
                    } else if (operation.equals("remove")) {
                        removeComponent(actor, draft, section, index);
                    } else if (Set.of("driver", "item").contains(operation)) {
                        execute(actor, command);
                    }
                    else {
                        String initial = "";
                        String hint = operation;
                        if (Set.of("position", "exit", "set").contains(operation)) {
                            String path = section + "." + index;
                            if (section.equals("parts")) {
                                var matrix = draft.parts().get(index).transform();
                                initial = matrix.m30() + " " + matrix.m31() + " " + matrix.m32();
                            } else if (section.equals("colliders")) initial = fieldValue(draft, path + ".center") + " " + fieldValue(draft, path + ".half-size");
                            else initial = fieldValue(draft, path + (section.equals("seats") ? "." + operation : ""));
                            hint = section.equals("colliders") ? "x y z halfX halfY halfZ" : "x y z (local coordinates)";
                        } else if (operation.equals("animation")) { initial = draft.parts().get(index).animation().name(); hint = "BODY | WHEEL | FRONT_WHEEL | PROPELLER | RUDDER"; }
                        else if (operation.equals("block")) {
                            var part = draft.parts().get(index);
                            initial = part.kind() == VehicleDefinition.PartKind.BLOCK ? part.payload() : "minecraft:stone";
                            hint = "block data";
                        }
                        else if (operation.equals("text")) {
                            var part = draft.parts().get(index);
                            if (part.kind() == VehicleDefinition.PartKind.TEXT) initial = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                    .serialize(GsonComponentSerializer.gson().deserialize(part.payload()));
                            hint = "plain text";
                        }
                        else if (operation.equals("option")) hint = "billboard|item-transform|alignment|block-light|sky-light|background|line-width|opacity|shadow|see-through <value>";
                        prompt(actor, sectionMessage(section), hint, initial, command + " ");
                    }
                }));
    }

    private void executeCopy(Player player, String section, int index) {
        run(player, () -> {
            execute(player, singular(section) + " copy " + index);
            screen.open(player, focused.get(player.getUniqueId()));
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
                    execute(actor, singular(section) + " remove " + index);
                    screen.open(actor, new State(section, 0, null));
                }));
    }

    private void transformMenu(FloatingMenuDefinition.Builder menu, Player player, VehicleModelDraft draft, State state) {
        focused.put(player.getUniqueId(), state);
        var mode = VehicleEditorTransform.Mode.valueOf(state.view().toUpperCase(Locale.ROOT));
        var preferences = transformSteps.computeIfAbsent(player.getUniqueId(), ignored -> new java.util.EnumMap<>(VehicleEditorTransform.Mode.class));
        double step = preferences.getOrDefault(mode, VehicleEditorTransform.defaultStep(mode));
        VehicleVector value = VehicleEditorTransform.value(draft, mode, state.page());
        Message title = operationMessage(state.view());
        Message hint = switch (mode) {
            case ROTATE -> Message.VEHICLE_MODEL_ROTATE_HINT;
            case SCALE -> Message.VEHICLE_MODEL_SCALE_HINT;
            case SIZE -> Message.VEHICLE_MODEL_SIZE_HINT;
        };
        menu.information("summary", text(player, title).append(Component.text(" · " + state.page()
                        + (mode == VehicleEditorTransform.Mode.ROTATE ? "" : " · " + VehicleEditorGeometry.display(value))))
                .append(Component.newline()).append(text(player, hint))).region("summary");
        long revision = draft.revision();
        for (int axis = 0; axis < 3; axis++) for (int direction : List.of(-1, 1)) {
            int selectedAxis = axis;
            String operator = mode == VehicleEditorTransform.Mode.SCALE ? direction < 0 ? " ÷ " : " × " : direction < 0 ? " − " : " + ";
            String label = List.of("X", "Y", "Z").get(axis) + operator + step + (mode == VehicleEditorTransform.Mode.ROTATE ? "°" : "");
            menu.item("adjust:" + axis + ":" + direction, Material.ARROW, Component.text(label)).region("items")
                    .primary((actor, handle) -> run(actor, () -> {
                        requireCurrent(actor, draft, revision);
                        VehicleEditorTransform.adjust(draft, mode, state.page(), selectedAxis, direction, step);
                        changed(actor);
                    }));
        }
        if (mode == VehicleEditorTransform.Mode.SCALE) for (int direction : List.of(-1, 1))
            menu.item("uniform:" + direction, Material.SLIME_BLOCK, language.text(player, Message.VEHICLE_MODEL_SCALE_ALL, NamedTextColor.AQUA,
                    (direction < 0 ? "÷ " : "× ") + step)).region("actions").primary((actor, handle) -> run(actor, () -> {
                        requireCurrent(actor, draft, revision);
                        VehicleEditorTransform.scaleAll(draft, state.page(), direction, step);
                        changed(actor);
                    }));
        menu.item("step", Material.REPEATER, text(player, Message.VEHICLE_MODEL_TRANSFORM_STEP).append(Component.text(" · " + step)))
                .region("actions").primary((actor, handle) -> run(actor, () -> {
                    requireCurrent(actor, draft, revision);
                    preferences.put(mode, VehicleEditorTransform.nextStep(mode, step));
                    redraw(actor);
                }));
        String initial = mode == VehicleEditorTransform.Mode.ROTATE ? "0 0 0" : mode == VehicleEditorTransform.Mode.SCALE ? "1 1 1" : VehicleEditorGeometry.coordinates(value);
        String prefix = singular(state.id()) + " " + state.view() + " " + state.page() + " ";
        action(menu, player, "manual", Material.PAPER, Message.VEHICLE_MODEL_MANUAL, actor -> {
            requireCurrent(actor, draft, revision);
            prompt(actor, title, language.t(actor, hint), initial, prefix);
        });
        historyAction(menu, player, "undo", Message.VEHICLE_MODEL_UNDO, draft.canUndo(), "navigation");
        historyAction(menu, player, "redo", Message.VEHICLE_MODEL_REDO, draft.canRedo(), "navigation");
    }

    private void positionMenu(FloatingMenuDefinition.Builder menu, Player player, VehicleModelDraft draft, State state) {
        focused.put(player.getUniqueId(), state);
        boolean exit = state.view().equals("position-exit");
        VehicleVector position = draft.position(state.id(), state.page(), exit);
        menu.information("summary", text(player, exit ? Message.VEHICLE_MODEL_EXIT : Message.VEHICLE_MODEL_POSITION)
                .append(Component.text(" · " + state.page() + " · " + VehicleEditorGeometry.display(position)))
                .append(Component.newline()).append(text(player, Message.VEHICLE_MODEL_POSITION_HINT))).region("summary");
        double step = steps.getOrDefault(player.getUniqueId(), 0.1);
        long revision = draft.revision();
        String operation = state.id().equals("supports") ? "set" : exit ? "exit" : "position";
        for (int axis = 0; axis < 3; axis++) for (int direction : List.of(-1, 1)) {
            VehicleVector offset = switch (axis) {
                case 0 -> new VehicleVector(direction * step, 0, 0);
                case 1 -> new VehicleVector(0, direction * step, 0);
                default -> new VehicleVector(0, 0, direction * step);
            };
            String label = List.of("X", "Y", "Z").get(axis) + (direction < 0 ? " − " : " + ") + step;
            menu.item("nudge:" + axis + ":" + direction, Material.ARROW, Component.text(label)).region("items")
                    .primary((actor, handle) -> run(actor, () -> {
                        requireCurrent(actor, draft, revision);
                        draft.position(state.id(), state.page(), exit, position.add(offset));
                        changed(actor);
                    }));
        }
        action(menu, player, "here", Material.COMPASS, Message.VEHICLE_MODEL_HERE, actor -> {
            requireCurrent(actor, draft, revision);
            draft.position(state.id(), state.page(), exit, here(actor));
            changed(actor);
        });
        action(menu, player, "look", Material.SPYGLASS, Message.VEHICLE_MODEL_LOOK, actor -> {
            requireCurrent(actor, draft, revision);
            armLook(actor, singular(state.id()), operation, String.valueOf(state.page()), "look");
        });
        commandAction(menu, player, "snap", Material.REPEATER, snapping.contains(player.getUniqueId()) ? Message.VEHICLE_MODEL_SNAP_ON : Message.VEHICLE_MODEL_SNAP_OFF, "snap");
        menu.item("step", Material.REPEATER, text(player, Message.VEHICLE_MODEL_STEP).append(Component.text(" · " + step)))
                .region("actions").primary((actor, handle) -> run(actor, () -> {
                    requireCurrent(actor, draft, revision);
                    steps.put(actor.getUniqueId(), step == 0.1 ? 0.25 : step == 0.25 ? 1.0 : 0.1);
                    redraw(actor);
                }));
        action(menu, player, "manual", Material.PAPER, Message.VEHICLE_MODEL_MANUAL, actor -> prompt(actor,
                Message.VEHICLE_MODEL_POSITION, "X Y Z", VehicleEditorGeometry.coordinates(position),
                singular(state.id()) + " " + operation + " " + state.page() + " "));
        historyAction(menu, player, "undo", Message.VEHICLE_MODEL_UNDO, draft.canUndo(), "navigation");
        historyAction(menu, player, "redo", Message.VEHICLE_MODEL_REDO, draft.canRedo(), "navigation");
    }

    private void changed(Player player) {
        refreshPreview(player);
        redraw(player);
        result(player, draft(player).id() + " · " + language.t(player, draft(player).dirty() ? Message.VEHICLE_MODEL_DRAFT : Message.VEHICLE_MODEL_SAVED));
    }

    private void redraw(Player player) {
        screen.flow(player).ifPresent(flow -> flow.redraw());
    }

    private void armLook(Player player, String... command) {
        VehicleModelDraft draft = draft(player);
        placements.put(player.getUniqueId(), new Placement(new VehicleEditorPlacement(draft, draft.revision(), List.of(command),
                ticks + 6, System.nanoTime() + 30_000_000_000L), screen.state(player).orElse(new State("editor", 0, null))));
        screen.flow(player).ifPresent(flow -> flow.close());
        result(player, language.t(player, Message.VEHICLE_MODEL_PLACE_HINT));
    }

    boolean place(Player player) {
        if (placementClicks.getOrDefault(player.getUniqueId(), -1L) >= ticks) return true;
        Placement placement = placements.get(player.getUniqueId());
        if (placement == null) return false;
        VehicleEditorPlacement operation = placement.operation();
        if (!operation.current(drafts.get(player.getUniqueId()), System.nanoTime()) || !player.hasPermission("mik.vehicle.admin") || player.isSneaking()) {
            placementClicks.put(player.getUniqueId(), ticks + 1);
            placements.remove(player.getUniqueId());
            result(player, language.t(player, Message.VEHICLE_MODEL_PLACE_CANCELLED));
            return true;
        }
        if (!operation.ready(ticks)) return true;
        placementClicks.put(player.getUniqueId(), ticks + 1);
        run(player, () -> {
            requireCurrent(player, operation.draft(), operation.revision());
            execute(player, operation.command().toArray(String[]::new));
            placements.remove(player.getUniqueId());
            if (!operation.command().getFirst().equals("pick")) screen.open(player, placement.returnTo());
        });
        return true;
    }

    private void historyAction(FloatingMenuDefinition.Builder menu, Player player, String id, Message label, boolean available) {
        historyAction(menu, player, id, label, available, "actions");
    }

    private void historyAction(FloatingMenuDefinition.Builder menu, Player player, String id, Message label, boolean available, String region) {
        var button = menu.item(id, Material.CLOCK, text(player, label)).region(region)
                .primary((actor, handle) -> run(actor, () -> execute(actor, id)));
        if (!available) button.disabled(text(player, Message.VEHICLE_MODEL_HISTORY_EMPTY));
    }

    private void add(Player player, String section) {
        String hint = switch (section) {
            case "parts" -> "block <blockdata> | item (held) | text <text> | selected";
            case "seats" -> "<x y z> <driver|passenger>";
            case "colliders" -> "<x y z halfX halfY halfZ>";
            default -> "<x y z>";
        };
        prompt(player, Message.VEHICLE_MODEL_ADD, hint, "", singular(section) + " add ");
    }

    private void prompt(Player player, Message title, String label, String initial, String prefix) {
        VehicleModelDraft expected = drafts.get(player.getUniqueId());
        long revision = expected == null ? -1 : expected.revision();
        long expectedGeneration = generation;
        UUID session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> UUID.randomUUID());
        MenuDialogs.openTextInput(plugin, player, text(player, title), Component.text(label), initial, 4096, false,
                text(player, Message.VEHICLE_MODEL_APPLY), text(player, Message.CLOSE), (actor, value) -> run(actor, () -> {
                    requireCurrent(actor, expected, revision);
                    requireSession(actor, session, expectedGeneration);
                    execute(actor, prefix + value);
                }));
    }

    private void discard(Player player) {
        run(player, () -> {
            VehicleModelDraft expected = draft(player);
            long revision = expected.revision();
            Consumer<Player> remove = actor -> run(actor, () -> {
                requireCurrent(actor, expected, revision);
                drafts.remove(actor.getUniqueId());
                draftOrigins.remove(actor.getUniqueId());
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
            UUID session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> UUID.randomUUID());
            long expectedGeneration = generation;
            MenuDialogs.openConfirm(plugin, player, text(player, Message.VEHICLE_MODEL_DELETE),
                    text(player, Message.VEHICLE_MODEL_DELETE_WARNING).append(Component.text(" · " + expected.id())),
                    text(player, Message.VEHICLE_MODEL_DELETE), text(player, Message.CLOSE), actor -> run(actor, () -> {
                        requireSession(actor, session, expectedGeneration);
                        module.deleteModel(expected);
                        screen.redrawWhere(ignored -> true);
                        result(actor, expected.id() + " · " + language.t(actor, Message.VEHICLE_MODEL_DELETED));
                    }));
        });
    }

    private void preview(Player player, VehicleModelDraft draft) {
        preview(player, draft, true);
    }

    private void preview(Player player, VehicleModelDraft draft, boolean announce) {
        List<VehicleDefinition.Part> parts = draft.parts();
        if (previews.entrySet().stream().filter(entry -> !entry.getKey().equals(player.getUniqueId()))
                .mapToInt(entry -> entry.getValue().displays().size()).sum() + parts.size() > 512)
            throw new IllegalArgumentException("Preview display quota reached");
        Location origin = previewOrigin(player);
        if (!origin.getWorld().isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) throw new IllegalArgumentException("Preview chunk unavailable");
        Preview existing = previews.get(player.getUniqueId());
        Matrix4f rotation = new Matrix4f().rotate(new Quaternionf().set(VehicleModel.orientation(origin.getYaw())));
        if (existing != null && existing.origin().equals(origin) && existing.parts().size() == parts.size()
                && existing.displays().stream().allMatch(Entity::isValid)
                && java.util.stream.IntStream.range(0, parts.size()).allMatch(index -> sameAppearance(existing.parts().get(index), parts.get(index)))) {
            for (int index = 0; index < parts.size(); index++) {
                existing.displays().get(index).setTransformationMatrix(new Matrix4f(rotation).mul(parts.get(index).transform()));
                existing.displays().get(index).setInterpolationDelay(0);
            }
            previews.put(player.getUniqueId(), new Preview(origin, existing.displays(), parts, System.nanoTime() + 300_000_000_000L));
            if (announce) result(player, language.t(player, Message.VEHICLE_MODEL_PREVIEW_HINT));
            return;
        }
        List<Display> displays = new ArrayList<>();
        try {
            Location anchor = origin.clone();
            anchor.setYaw(0);
            anchor.setPitch(0);
            for (VehicleDefinition.Part part : parts) {
                Display display = VehicleEntityGroup.createDisplay(anchor, part, entity -> {
                    entity.setVisibleByDefault(false);
                    entity.getPersistentDataContainer().set(previewKey, PersistentDataType.STRING, player.getUniqueId().toString());
                    entity.setTransformationMatrix(new Matrix4f(rotation).mul(part.transform()));
                });
                displays.add(display);
                player.showEntity(plugin, display);
            }
            Preview previous = previews.put(player.getUniqueId(), new Preview(origin, displays, parts, System.nanoTime() + 300_000_000_000L));
            if (previous != null) previous.displays().forEach(Entity::remove);
            module.refreshGizmos();
            if (announce) result(player, language.t(player, Message.VEHICLE_MODEL_PREVIEW_HINT));
        } catch (RuntimeException exception) { displays.forEach(Entity::remove); throw exception; }
    }

    private static boolean sameAppearance(VehicleDefinition.Part first, VehicleDefinition.Part second) {
        return first.kind() == second.kind() && first.payload().equals(second.payload()) && first.billboard().equals(second.billboard())
                && first.itemTransform().equals(second.itemTransform()) && first.alignment().equals(second.alignment())
                && first.blockLight() == second.blockLight() && first.skyLight() == second.skyLight() && first.background() == second.background()
                && first.lineWidth() == second.lineWidth() && first.textOpacity() == second.textOpacity()
                && first.shadow() == second.shadow() && first.seeThrough() == second.seeThrough();
    }

    private void refreshPreview(Player player) {
        if (!previews.containsKey(player.getUniqueId())) return;
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
        Preview preview = previews.get(player.getUniqueId());
        if (preview == null) throw new IllegalArgumentException(language.t(player, Message.VEHICLE_MODEL_PICK_HINT));
        Aim target = aim(player);
        int index = target == null || target.display() == null ? -1 : preview.displays().indexOf(target.display());
        if (index < 0) throw new IllegalArgumentException(language.t(player, Message.VEHICLE_MODEL_PICK_HINT));
        placements.remove(player.getUniqueId());
        screen.open(player, new State("component", index, "parts"));
    }

    private void guides(Player player, Preview preview, VehicleModelDraft draft) {
        Location origin = preview.origin();
        for (int sample = 0; sample <= 5; sample++) {
            double offset = sample * 0.2;
            marker(player, origin, new VehicleVector(offset, 0, 0), RED);
            marker(player, origin, new VehicleVector(0, offset, 0), GREEN);
            marker(player, origin, new VehicleVector(0, 0, offset), BLUE);
        }
        for (int index = 0; index < draft.count("seats"); index++) {
            marker(player, origin, draft.position("seats", index, false), Boolean.parseBoolean(draft.field("seats." + index + ".driver")) ? GREEN : BLUE);
            marker(player, origin, draft.position("seats", index, true), ORANGE);
        }
        for (int index = 0; index < draft.count("supports"); index++) marker(player, origin, draft.position("supports", index, false), YELLOW);
        marker(player, origin, VehicleModelDraft.vector(fieldValue(draft, "center-of-mass")), PURPLE);
        State focus = focused.get(player.getUniqueId());
        int collider = focus != null && focus.id().equals("colliders") ? focus.page() : 0;
        if (collider < draft.count("colliders"))
            for (VehicleVector point : VehicleEditorGeometry.outline(draft.collider(collider))) marker(player, origin, point, WHITE);
        if (focus != null && focus.page() < draft.count(focus.id())) {
            VehicleVector point = draft.position(focus.id(), focus.page(), focus.view().equals("position-exit"));
            marker(player, origin, point, WHITE);
            marker(player, origin, point.add(new VehicleVector(0, 0.15, 0)), WHITE);
        }
        if (placements.containsKey(player.getUniqueId())) {
            try {
                Aim target = aim(player);
                if (target != null && (!placements.get(player.getUniqueId()).operation().command().getFirst().equals("pick") || target.display() != null)) {
                    VehicleVector cursor = sampled(player, target.point());
                    marker(player, origin, cursor, YELLOW);
                    marker(player, origin, cursor.add(new VehicleVector(0.1, 0, 0)), YELLOW);
                    marker(player, origin, cursor.add(new VehicleVector(0, 0.1, 0)), YELLOW);
                }
            } catch (IllegalArgumentException exception) { }
        }
        Corner corner = corners.get(player.getUniqueId());
        if (corner != null) {
            marker(player, origin, corner.point(), ORANGE);
            VehicleVector candidate;
            try { candidate = sample(player, corner.look() ? "look" : "here"); }
            catch (IllegalArgumentException exception) { return; }
            try {
                for (VehicleVector point : VehicleEditorGeometry.outline(VehicleEditorGeometry.box(corner.point(), candidate)))
                    marker(player, origin, point, ORANGE);
            } catch (IllegalArgumentException exception) { marker(player, origin, candidate, RED); }
        }
    }

    private static void marker(Player player, Location origin, VehicleVector point, Particle.DustOptions color) {
        if (point.length() > 32) return;
        player.spawnParticle(Particle.DUST, VehicleEditorGeometry.world(origin, point), 1, 0, 0, 0, 0, color);
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
        for (UUID id : List.copyOf(previews.keySet())) {
            Preview preview = previews.get(id);
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.hasPermission("mik.vehicle.admin") || System.nanoTime() >= preview.expires()
                    || !player.getWorld().equals(preview.origin().getWorld()) || player.getLocation().distanceSquared(preview.origin()) > 1024
                    || preview.displays().stream().anyMatch(display -> !display.isValid())) clearPreview(id);
            else if (ticks % 10 == 0 && drafts.containsKey(id)) guides(player, preview, drafts.get(id));
        }
    }

    void enable() { closed = false; }
    void unload(org.bukkit.World world, Integer chunkX, Integer chunkZ) {
        for (UUID id : List.copyOf(previews.keySet())) {
            Location origin = previews.get(id).origin();
            if (origin.getWorld().equals(world) && (chunkX == null || (origin.getBlockX() >> 4) == chunkX && (origin.getBlockZ() >> 4) == chunkZ)) clearPreview(id);
        }
    }

    boolean isPreview(Entity entity) { return entity.getPersistentDataContainer().has(previewKey); }
    java.util.Set<UUID> previewIds(UUID viewer) {
        Preview preview = previews.get(viewer);
        return preview == null ? java.util.Set.of() : java.util.Set.copyOf(preview.displays().stream().map(Entity::getUniqueId).toList());
    }
    void clearPreview(UUID id) { placements.remove(id); Preview preview = previews.remove(id); if (preview != null) { preview.displays().forEach(Entity::remove); module.refreshGizmos(); } }
    private void clearTools(UUID id) { corners.remove(id); steps.remove(id); transformSteps.remove(id); snapping.remove(id); focused.remove(id); placements.remove(id); placementClicks.remove(id); }
    void forget(Player player) { clearPreview(player.getUniqueId()); drafts.remove(player.getUniqueId()); draftOrigins.remove(player.getUniqueId()); clearTools(player.getUniqueId()); sessions.remove(player.getUniqueId()); screen.forget(player); }
    void close() { closed = true; generation++; for (UUID id : List.copyOf(sessions.keySet())) screen.forget(id); for (UUID id : List.copyOf(previews.keySet())) clearPreview(id); drafts.clear(); draftOrigins.clear(); sessions.clear(); corners.clear(); steps.clear(); transformSteps.clear(); snapping.clear(); focused.clear(); placements.clear(); placementClicks.clear(); }

    private Location previewOrigin(Player player) {
        Location origin = draftOrigins.get(player.getUniqueId());
        if (origin == null) throw new IllegalArgumentException("Create or edit a model first");
        VehicleEditorGeometry.local(origin, player.getLocation());
        return origin.clone();
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
    private void action(FloatingMenuDefinition.Builder menu, Player player, String id, Material material, Message label, Consumer<Player> action) {
        menu.item(id, material, text(player, label)).region("actions").primary((actor, handle) -> run(actor, () -> action.accept(actor)));
    }
    private void commandAction(FloatingMenuDefinition.Builder menu, Player player, String id, Material material, Message label, String command) {
        action(menu, player, id, material, label, actor -> run(actor, () -> execute(actor, command)));
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
    private static String singular(String section) { return switch (section) { case "parts" -> "part"; case "seats" -> "seat"; case "colliders" -> "collider"; default -> "support"; }; }
    private static String fieldValue(VehicleModelDraft draft, String field) {
        if (VehicleModelDraft.VECTORS.contains(field) || field.startsWith("seats.") || field.startsWith("colliders.") || field.startsWith("supports."))
            return draft.field(field + ".x") + " " + draft.field(field + ".y") + " " + draft.field(field + ".z");
        if (field.equals("engine.ratios")) return draft.field(field).replace("[", "").replace("]", "");
        return draft.field(field);
    }
    private static String argument(String[] args, int index) { if (args.length <= index) throw new IllegalArgumentException("Missing argument; use /vehicle model help"); return args[index]; }
    private static int index(String[] args, int offset) { return Integer.parseInt(argument(args, offset)); }
    private static String tail(String[] args, int offset) { argument(args, offset); return String.join(" ", java.util.Arrays.copyOfRange(args, offset, args.length)); }
    private static void exact(String[] args, int count) { if (args.length != count) throw new IllegalArgumentException("Expected " + (count - 1) + " arguments; use /vehicle model help"); }
}
