package org.encinet.mik.module.vehicle;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.joml.Matrix4f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

final class VehicleModelDraft {
    static final Set<String> SCALARS = Set.of("mass", "support-length", "grip", "maximum-speed", "fuel.capacity", "fuel.consumption",
            "buoyancy", "wing-area", "lift", "engine.torque", "engine.idle-rpm", "engine.redline-rpm", "engine.inertia",
            "engine.wheel-radius", "engine.final-drive", "engine.reverse-ratio", "engine.shift-seconds");
    static final Set<String> VECTORS = Set.of("center-of-mass", "inertia");
    private record Revision(String content, int bytes) { }
    private final UUID token = UUID.randomUUID();
    private VehicleDefinition base;
    private final Deque<Revision> history = new ArrayDeque<>();
    private final Deque<Revision> future = new ArrayDeque<>();
    private YamlConfiguration config;
    private String savedContent;
    private List<VehicleDefinition.Part> cachedParts;
    private int historyBytes;
    private long revision;

    VehicleModelDraft(VehicleDefinition base) { this(base, VehicleStore.encodeDefinition(base)); }
    static VehicleModelDraft imported(VehicleDefinition definition) { return new VehicleModelDraft(null, VehicleStore.encodeDefinition(definition)); }
    private VehicleModelDraft(VehicleDefinition base, YamlConfiguration config) {
        this.base = base;
        this.config = config;
        savedContent = base == null ? null : config.saveToString();
    }

    static VehicleModelDraft create(String id, VehicleDefinition.Kind kind) {
        VehicleDefinition.Part placeholder = part(VehicleDefinition.PartKind.BLOCK, "minecraft:air");
        YamlConfiguration config = VehicleStore.encodeDefinition(VehicleDefinition.preset(id, kind, List.of(placeholder),
                new VehicleDefinition.Collider(new VehicleVector(0, 0.5, 0), new VehicleVector(0.5, 0.5, 0.5))));
        config.set("parts", null);
        config.createSection("parts");
        return new VehicleModelDraft(null, config);
    }

    static VehicleModelDraft copy(VehicleDefinition source, String id) {
        VehicleModelDraft draft = new VehicleModelDraft(source);
        YamlConfiguration config = draft.config;
        config.set("id", id);
        VehicleDefinition proposed = VehicleStore.decodeDefinition(config);
        return new VehicleModelDraft(null, VehicleStore.encodeDefinition(proposed));
    }

    UUID token() { return token; }
    long revision() { return revision; }
    VehicleDefinition base() { return base; }
    String id() { return config.getString("id"); }
    String kind() { return config.getString("kind"); }
    int count(String section) { return root(config, section).getKeys(false).size(); }
    boolean dirty() { return savedContent == null || !savedContent.equals(config.saveToString()); }
    boolean canUndo() { return !history.isEmpty(); }
    boolean canRedo() { return !future.isEmpty(); }
    String field(String name) { return String.valueOf(config.get(name)); }
    String describe(String section, int index) {
        ConfigurationSection entry = element(config, section, index);
        if (section.equals("parts")) {
            VehicleDefinition.Part part = parts().get(index);
            org.joml.Matrix4fc matrix = part.transform();
            return index + " · " + part.kind() + " · " + part.animation() + " · "
                    + matrix.m30() + ", " + matrix.m31() + ", " + matrix.m32();
        }
        if (section.equals("seats")) return index + " · " + coordinates(entry, "position") + " → " + coordinates(entry, "exit")
                + (entry.getBoolean("driver") ? " · DRIVER" : " · PASSENGER");
        if (section.equals("colliders")) return index + " · " + coordinates(entry, "center") + " · ± " + coordinates(entry, "half-size");
        return index + " · " + coordinates(entry, "");
    }
    private static String coordinates(ConfigurationSection entry, String prefix) {
        String path = prefix.isEmpty() ? "" : prefix + ".";
        return entry.getDouble(path + "x") + ", " + entry.getDouble(path + "y") + ", " + entry.getDouble(path + "z");
    }
    VehicleDefinition validate() { return VehicleStore.decodeDefinition(config); }
    List<VehicleDefinition.Part> parts() {
        if (cachedParts == null) cachedParts = VehicleStore.decodeParts(config);
        return cachedParts;
    }

    void saved(VehicleDefinition definition) {
        if (!validate().equals(definition)) throw new IllegalArgumentException("Saved model does not match draft");
        base = definition;
        config = VehicleStore.encodeDefinition(definition);
        savedContent = config.saveToString();
        cachedParts = null;
        revision++;
    }

    VehicleVector position(String section, int index, boolean exit) {
        ConfigurationSection entry = element(config, section, index);
        if (section.equals("parts")) {
            var matrix = parts().get(index).transform();
            return new VehicleVector(matrix.m30(), matrix.m31(), matrix.m32());
        }
        String prefix = section.equals("seats") ? (exit ? "exit." : "position.") : section.equals("colliders") ? "center." : "";
        return new VehicleVector(entry.getDouble(prefix + "x"), entry.getDouble(prefix + "y"), entry.getDouble(prefix + "z"));
    }

    VehicleDefinition.Collider collider(int index) {
        ConfigurationSection entry = element(config, "colliders", index);
        return new VehicleDefinition.Collider(position("colliders", index, false), new VehicleVector(entry.getDouble("half-size.x"),
                entry.getDouble("half-size.y"), entry.getDouble("half-size.z")));
    }

    void position(String section, int index, boolean exit, VehicleVector position) {
        switch (section) {
            case "parts" -> changePart(index, "position", VehicleEditorGeometry.coordinates(position));
            case "seats" -> seat(exit ? "exit" : "position", index, position, false);
            case "colliders" -> collider("set", index, new VehicleDefinition.Collider(position, collider(index).halfSize()));
            case "supports" -> support("set", index, position);
            default -> throw new IllegalArgumentException("Unknown component: " + section);
        }
    }

    void size(int index, VehicleVector fullSize) {
        VehicleDefinition.Collider current = collider(index);
        collider("set", index, new VehicleDefinition.Collider(current.center(), fullSize.multiply(0.5)));
    }

    int copy(String section, int index) {
        if (!Set.of("parts", "seats", "colliders", "supports").contains(section)) throw new IllegalArgumentException("Unknown component");
        element(config, section, index);
        int target = count(section);
        int maximum = section.equals("parts") ? 128 : 16;
        if (target >= maximum) throw new IllegalArgumentException("At most " + maximum + " " + section);
        mutate(next -> {
            ConfigurationSection source = element(next, section, index);
            ConfigurationSection duplicate = next.createSection(section + "." + target);
            source.getValues(true).forEach((key, value) -> { if (!(value instanceof ConfigurationSection)) duplicate.set(key, value); });
            if (section.equals("seats")) duplicate.set("driver", false);
        });
        return target;
    }

    void set(String field, String input) {
        if (field.equals("kind")) {
            String kind = VehicleDefinition.parseKind(input).name();
            mutate(next -> next.set(field, kind));
        } else if (SCALARS.contains(field)) {
            double value = finite(input);
            if (value <= 0) throw new IllegalArgumentException("Positive number required");
            mutate(next -> next.set(field, value));
        } else if (VECTORS.contains(field)) {
            VehicleVector value = vector(input);
            mutate(next -> putVector(next, field, value));
        } else if (field.equals("engine.ratios")) {
            List<Double> ratios = new ArrayList<>();
            double previous = Double.POSITIVE_INFINITY;
            for (String token : input.trim().split("[,\\s]+")) {
                double ratio = finite(token);
                if (ratio <= 0 || ratio >= previous) throw new IllegalArgumentException("Gear ratios must be positive and decrease");
                ratios.add(ratio);
                previous = ratio;
            }
            if (ratios.isEmpty() || ratios.size() > 12) throw new IllegalArgumentException("Use 1..12 gears");
            mutate(next -> next.set(field, ratios));
        } else throw new IllegalArgumentException("Unknown editable field: " + field);
    }

    void addParts(List<VehicleDefinition.Part> parts) {
        if (parts.isEmpty() || count("parts") + parts.size() > 128) throw new IllegalArgumentException("Use 1..128 parts");
        mutate(next -> {
            int index = count("parts");
            for (VehicleDefinition.Part part : parts) VehicleStore.encodePart(next.createSection("parts." + index++), part);
        });
    }

    void changePart(int index, String operation, String value) {
        element(config, "parts", index);
        mutate(next -> {
            ConfigurationSection saved = element(next, "parts", index);
            VehicleDefinition.Part current = VehicleStore.decodeParts(next).get(index);
            switch (operation) {
                case "animation" -> saved.set("animation", VehicleDefinition.Animation.valueOf(value.toUpperCase(Locale.ROOT)).name());
                case "position", "rotate", "scale" -> {
                    VehicleVector vector = vector(value);
                    Matrix4f matrix = new Matrix4f(current.transform());
                    if (operation.equals("position")) matrix.setTranslation((float) vector.coordinateX(), (float) vector.coordinateY(), (float) vector.coordinateZ());
                    else if (operation.equals("rotate")) matrix.rotateXYZ((float) Math.toRadians(vector.coordinateX()),
                            (float) -Math.toRadians(vector.coordinateY()), (float) Math.toRadians(vector.coordinateZ()));
                    else {
                        if (vector.coordinateX() <= 0 || vector.coordinateY() <= 0 || vector.coordinateZ() <= 0)
                            throw new IllegalArgumentException("Positive scale required");
                        matrix.scale((float) vector.coordinateX(), (float) vector.coordinateY(), (float) vector.coordinateZ());
                    }
                    List<Float> values = new ArrayList<>();
                    for (float number : matrix.get(new float[16])) values.add(number);
                    saved.set("matrix", values);
                }
                case "block", "item", "text" -> {
                    saved.set("kind", operation.toUpperCase(Locale.ROOT));
                    saved.set("payload", value);
                }
                case "option" -> {
                    String[] args = value.split("\\s+", 2);
                    if (args.length != 2) throw new IllegalArgumentException("option <property> <value>");
                    Object proposed = switch (args[0]) {
                        case "billboard", "item-transform", "alignment" -> args[1].toUpperCase(Locale.ROOT);
                        case "block-light", "sky-light", "background", "line-width" -> Integer.decode(args[1]);
                        case "opacity" -> {
                            int opacity = Integer.parseInt(args[1]);
                            if (opacity < -128 || opacity > 255) throw new IllegalArgumentException("Opacity: -128..255");
                            yield (int) (byte) opacity;
                        }
                        case "shadow", "see-through" -> {
                            if (!Set.of("true", "false").contains(args[1])) throw new IllegalArgumentException("Use true or false");
                            yield Boolean.parseBoolean(args[1]);
                        }
                        default -> throw new IllegalArgumentException("Unknown display property");
                    };
                    saved.set(args[0], proposed);
                }
                default -> throw new IllegalArgumentException("Unknown part operation");
            }
            VehicleStore.decodeParts(next);
        });
    }

    void seat(String operation, int index, VehicleVector value, boolean driver) {
        if (operation.equals("add") && count("seats") >= 16) throw new IllegalArgumentException("At most 16 seats");
        if (!operation.equals("add")) element(config, "seats", index);
        if (value != null && value.length() > 24) throw new IllegalArgumentException("Seat exceeds 24 blocks");
        mutate(next -> {
            int target = operation.equals("add") ? count("seats") : index;
            if (operation.equals("add")) {
                VehicleDefinition.Seat seat = new VehicleDefinition.Seat(value, value.add(new VehicleVector(1.5, 0, 0)), driver);
                putVector(next, "seats." + target + ".position", seat.position());
                putVector(next, "seats." + target + ".exit", seat.exit());
                next.set("seats." + target + ".driver", driver);
            } else if (operation.equals("position") || operation.equals("exit")) putVector(next, "seats." + target + "." + operation, value);
            else if (!operation.equals("driver")) throw new IllegalArgumentException("seat add|position|exit|driver|remove");
            if (operation.equals("driver") || operation.equals("add") && driver)
                for (String key : root(next, "seats").getKeys(false)) next.set("seats." + key + ".driver", key.equals(String.valueOf(target)));
        });
    }

    void collider(String operation, int index, VehicleDefinition.Collider collider) {
        if (operation.equals("auto")) {
            VehicleDefinition.Collider bounds = VehicleModel.bounds(parts());
            mutate(next -> { next.set("colliders", null); putCollider(next, 0, bounds); });
            return;
        }
        if (operation.equals("add") && count("colliders") >= 16) throw new IllegalArgumentException("At most 16 colliders");
        if (operation.equals("set")) element(config, "colliders", index);
        if (!Set.of("add", "set").contains(operation)) throw new IllegalArgumentException("collider add|set|auto|remove");
        mutate(next -> putCollider(next, operation.equals("add") ? count("colliders") : index, collider));
    }

    void support(String operation, int index, VehicleVector value) {
        if (value.length() > 24) throw new IllegalArgumentException("Support exceeds 24 blocks");
        if (operation.equals("add") && count("supports") >= 16) throw new IllegalArgumentException("At most 16 support points");
        if (operation.equals("set")) element(config, "supports", index);
        if (!Set.of("add", "set").contains(operation)) throw new IllegalArgumentException("support add|set|remove");
        mutate(next -> putVector(next, "supports." + (operation.equals("add") ? count("supports") : index), value));
    }

    void remove(String section, int index) {
        if (!Set.of("parts", "seats", "supports", "colliders").contains(section)) throw new IllegalArgumentException("Unknown component");
        element(config, section, index);
        mutate(next -> {
            List<Map<String, Object>> remaining = new ArrayList<>();
            int current = 0;
            for (String key : root(next, section).getKeys(false)) {
                if (current++ != index) remaining.add(root(next, section).getConfigurationSection(key).getValues(true));
            }
            next.set(section, null);
            next.createSection(section);
            for (int target = 0; target < remaining.size(); target++) {
                ConfigurationSection entry = next.createSection(section + "." + target);
                remaining.get(target).forEach((key, value) -> { if (!(value instanceof ConfigurationSection)) entry.set(key, value); });
            }
        });
    }

    boolean undo() {
        return restore(history, future);
    }

    boolean redo() {
        return restore(future, history);
    }

    private boolean restore(Deque<Revision> source, Deque<Revision> destination) {
        if (source.isEmpty()) return false;
        Revision snapshot = source.removeLast();
        historyBytes -= snapshot.bytes();
        remember(destination, config.saveToString());
        config = read(snapshot.content());
        cachedParts = null;
        revision++;
        return true;
    }

    private void remember(Deque<Revision> destination, String content) {
        int bytes = content.getBytes(StandardCharsets.UTF_8).length;
        destination.addLast(new Revision(content, bytes));
        historyBytes += bytes;
        while (history.size() + future.size() > 20 || historyBytes > 8388608) {
            Deque<Revision> oldest = history.isEmpty() ? future : history;
            historyBytes -= oldest.removeFirst().bytes();
        }
    }

    private void mutate(Consumer<YamlConfiguration> operation) {
        String previous = config.saveToString();
        YamlConfiguration candidate = read(previous);
        operation.accept(candidate);
        String proposed = candidate.saveToString();
        if (proposed.getBytes(StandardCharsets.UTF_8).length > 8388608) throw new IllegalArgumentException("Model exceeds 8 MiB");
        if (previous.equals(proposed)) return;
        for (Revision snapshot : future) historyBytes -= snapshot.bytes();
        future.clear();
        remember(history, previous);
        config = candidate;
        cachedParts = null;
        revision++;
    }

    static VehicleDefinition.Part part(VehicleDefinition.PartKind kind, String payload) {
        return new VehicleDefinition.Part(kind, payload, new Matrix4f(), VehicleDefinition.Animation.BODY,
                "FIXED", "NONE", "CENTER", -1, -1, 0x40000000, 200, (byte) -1, false, false);
    }
    static VehicleVector vector(String input) {
        String[] values = input.trim().split("[,\\s]+");
        if (values.length != 3) throw new IllegalArgumentException("Three coordinates required");
        return new VehicleVector(finite(values[0]), finite(values[1]), finite(values[2]));
    }
    private static double finite(String input) {
        double value = Double.parseDouble(input);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Finite number required");
        return value;
    }
    private static YamlConfiguration read(String content) {
        YamlConfiguration copy = new YamlConfiguration();
        try { copy.loadFromString(content); }
        catch (InvalidConfigurationException exception) { throw new IllegalStateException("Invalid editor snapshot", exception); }
        return copy;
    }
    private static ConfigurationSection root(ConfigurationSection config, String section) {
        ConfigurationSection root = config.getConfigurationSection(section);
        if (root == null) throw new IllegalArgumentException("Missing component section");
        return root;
    }
    private static ConfigurationSection element(ConfigurationSection config, String section, int index) {
        if (index < 0) throw new IllegalArgumentException("Index must be nonnegative");
        ConfigurationSection element = root(config, section).getConfigurationSection(String.valueOf(index));
        if (element == null) throw new IllegalArgumentException("Unknown component index: " + index);
        return element;
    }
    private static void putVector(ConfigurationSection config, String prefix, VehicleVector vector) {
        config.set(prefix + ".x", vector.coordinateX());
        config.set(prefix + ".y", vector.coordinateY());
        config.set(prefix + ".z", vector.coordinateZ());
    }
    private static void putCollider(ConfigurationSection config, int index, VehicleDefinition.Collider collider) {
        putVector(config, "colliders." + index + ".center", collider.center());
        putVector(config, "colliders." + index + ".half-size", collider.halfSize());
    }
}
