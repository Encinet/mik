package org.encinet.mik.module.vehicle;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class VehicleStore {
    private final Path directory;

    VehicleStore(Path directory) { this.directory = directory; }

    Map<String, VehicleDefinition> loadDefinitions() throws IOException {
        Path definitions = directory.resolve("definitions");
        Files.createDirectories(definitions);
        Map<String, VehicleDefinition> result = new LinkedHashMap<>();
        try (var files = Files.list(definitions)) {
            for (Path path : files.filter(file -> file.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                VehicleDefinition definition = decodeDefinition(load(path));
                if (!path.getFileName().toString().equals(definition.id() + ".yml"))
                    throw new IOException("Definition id must match filename: " + path);
                if (result.put(definition.id(), definition) != null || result.size() > 256)
                    throw new IOException("Duplicate or excessive vehicle definitions");
            }
        } catch (IllegalArgumentException exception) { throw new IOException("Invalid vehicle definition", exception); }
        return result;
    }

    void saveDefinition(VehicleDefinition definition) throws IOException {
        Path path = directory.resolve("definitions").resolve(definition.id() + ".yml");
        if (Files.exists(path)) throw new IOException("Model already exists: " + definition.id());
        write(path, encodeDefinition(definition));
    }

    void replaceDefinition(VehicleDefinition expected, VehicleDefinition replacement) throws IOException {
        if (!expected.id().equals(replacement.id())) throw new IllegalArgumentException("Model ids cannot be changed");
        Path path = directory.resolve("definitions").resolve(expected.id() + ".yml");
        if (!expected.equals(decodeDefinition(load(path)))) throw new IOException("Model changed on disk; reload models and reopen its editor");
        write(path, encodeDefinition(replacement));
    }

    void deleteDefinition(VehicleDefinition expected) throws IOException {
        Path path = directory.resolve("definitions").resolve(expected.id() + ".yml");
        if (!expected.equals(decodeDefinition(load(path)))) throw new IOException("Model changed on disk; reload models and confirm deletion again");
        Files.delete(path);
    }

    List<VehicleInstance.Snapshot> loadInstances() throws IOException {
        Path path = directory.resolve("instances.yml");
        if (!Files.exists(path)) return List.of();
        try {
            YamlConfiguration config = load(path);
            ConfigurationSection root = section(config, "instances");
            List<VehicleInstance.Snapshot> result = new ArrayList<>();
            for (String key : root.getKeys(false)) {
                ConfigurationSection saved = section(root, key);
                result.add(new VehicleInstance.Snapshot(UUID.fromString(key), UUID.fromString(string(saved, "world")),
                        UUID.fromString(string(saved, "owner")), string(saved, "definition"), saved.getBoolean("public"),
                        vector(saved, "origin"), number(saved, "rotation.x"), number(saved, "rotation.y"),
                        number(saved, "rotation.z"), number(saved, "rotation.w"), vector(saved, "velocity"),
                        vector(saved, "angular-velocity"), number(saved, "fuel"), number(saved, "health"),
                        number(saved, "throttle"), saved.getBoolean("engine-running"), saved.getBoolean("assisted"),
                        VehicleTransmission.Mode.valueOf(string(saved, "mode")), VehicleTransmission.Selector.valueOf(string(saved, "selector")),
                        integer(saved, "gear"), integer(saved, "target-gear"), number(saved, "shift-remaining"),
                        number(saved, "gear-age"), number(saved, "rpm")));
                if (result.size() > 512) throw new IllegalArgumentException("Instance limit exceeded");
            }
            return List.copyOf(result);
        } catch (IllegalArgumentException exception) { throw new IOException("Invalid vehicle instances; existing data retained", exception); }
    }

    void saveInstances(List<VehicleInstance.Snapshot> snapshots) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        config.set("version", 1);
        ConfigurationSection root = config.createSection("instances");
        for (VehicleInstance.Snapshot snapshot : snapshots) {
            ConfigurationSection saved = root.createSection(snapshot.id().toString());
            saved.set("world", snapshot.world().toString());
            saved.set("owner", snapshot.owner().toString());
            saved.set("definition", snapshot.definition());
            saved.set("public", snapshot.publicAccess());
            putVector(saved, "origin", snapshot.origin());
            saved.set("rotation.x", snapshot.rotationX());
            saved.set("rotation.y", snapshot.rotationY());
            saved.set("rotation.z", snapshot.rotationZ());
            saved.set("rotation.w", snapshot.rotationW());
            putVector(saved, "velocity", snapshot.velocity());
            putVector(saved, "angular-velocity", snapshot.angularVelocity());
            saved.set("fuel", snapshot.fuel());
            saved.set("health", snapshot.health());
            saved.set("throttle", snapshot.throttle());
            saved.set("engine-running", snapshot.engineRunning());
            saved.set("assisted", snapshot.assisted());
            saved.set("mode", snapshot.mode().name());
            saved.set("selector", snapshot.selector().name());
            saved.set("gear", snapshot.gear());
            saved.set("target-gear", snapshot.targetGear());
            saved.set("shift-remaining", snapshot.shiftRemaining());
            saved.set("gear-age", snapshot.gearAge());
            saved.set("rpm", snapshot.rpm());
        }
        write(directory.resolve("instances.yml"), config);
    }

    static YamlConfiguration encodeDefinition(VehicleDefinition definition) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("version", 1);
        config.set("id", definition.id());
        config.set("kind", definition.kind().name());
        config.set("mass", definition.mass());
        putVector(config, "center-of-mass", definition.centerOfMass());
        putVector(config, "inertia", definition.inertia());
        config.set("support-length", definition.supportLength());
        config.set("grip", definition.grip());
        config.set("maximum-speed", definition.maximumSpeed());
        config.set("fuel.capacity", definition.fuelCapacity());
        config.set("fuel.consumption", definition.consumption());
        config.set("buoyancy", definition.buoyancy());
        config.set("wing-area", definition.wingArea());
        config.set("lift", definition.lift());
        VehicleDefinition.Engine engine = definition.engine();
        config.set("engine.torque", engine.peakTorque());
        config.set("engine.idle-rpm", engine.idleRpm());
        config.set("engine.redline-rpm", engine.redlineRpm());
        config.set("engine.inertia", engine.inertia());
        config.set("engine.wheel-radius", engine.wheelRadius());
        config.set("engine.final-drive", engine.finalDrive());
        config.set("engine.reverse-ratio", engine.reverseRatio());
        config.set("engine.ratios", engine.ratios());
        config.set("engine.shift-seconds", engine.shiftSeconds());
        for (int index = 0; index < definition.colliders().size(); index++) {
            putVector(config, "colliders." + index + ".center", definition.colliders().get(index).center());
            putVector(config, "colliders." + index + ".half-size", definition.colliders().get(index).halfSize());
        }
        for (int index = 0; index < definition.seats().size(); index++) {
            VehicleDefinition.Seat seat = definition.seats().get(index);
            putVector(config, "seats." + index + ".position", seat.position());
            putVector(config, "seats." + index + ".exit", seat.exit());
            config.set("seats." + index + ".driver", seat.driver());
        }
        for (int index = 0; index < definition.supports().size(); index++)
            putVector(config, "supports." + index, definition.supports().get(index));
        for (int index = 0; index < definition.parts().size(); index++) {
            VehicleDefinition.Part part = definition.parts().get(index);
            ConfigurationSection saved = config.createSection("parts." + index);
            encodePart(saved, part);
        }
        return config;
    }

    static void encodePart(ConfigurationSection saved, VehicleDefinition.Part part) {
        saved.set("kind", part.kind().name());
        saved.set("payload", part.payload());
        List<Float> matrix = new ArrayList<>();
        for (float value : part.transform().get(new float[16])) matrix.add(value);
        saved.set("matrix", matrix);
        saved.set("animation", part.animation().name());
        saved.set("billboard", part.billboard());
        saved.set("item-transform", part.itemTransform());
        saved.set("alignment", part.alignment());
        saved.set("block-light", part.blockLight());
        saved.set("sky-light", part.skyLight());
        saved.set("background", part.background());
        saved.set("line-width", part.lineWidth());
        saved.set("opacity", (int) part.textOpacity());
        saved.set("shadow", part.shadow());
        saved.set("see-through", part.seeThrough());
    }

    static VehicleDefinition decodeDefinition(ConfigurationSection config) {
        List<VehicleDefinition.Collider> colliders = new ArrayList<>();
        ConfigurationSection colliderRoot = section(config, "colliders");
        for (String key : colliderRoot.getKeys(false)) colliders.add(new VehicleDefinition.Collider(
                vector(colliderRoot, key + ".center"), vector(colliderRoot, key + ".half-size")));
        List<VehicleDefinition.Seat> seats = new ArrayList<>();
        ConfigurationSection seatRoot = section(config, "seats");
        for (String key : seatRoot.getKeys(false)) seats.add(new VehicleDefinition.Seat(
                vector(seatRoot, key + ".position"), vector(seatRoot, key + ".exit"), seatRoot.getBoolean(key + ".driver")));
        List<VehicleVector> supports = new ArrayList<>();
        ConfigurationSection supportRoot = section(config, "supports");
        for (String key : supportRoot.getKeys(false)) supports.add(vector(supportRoot, key));
        List<VehicleDefinition.Part> parts = decodeParts(config);
        List<Double> ratios = new ArrayList<>();
        List<?> rawRatios = config.getList("engine.ratios");
        if (rawRatios == null) throw new IllegalArgumentException("Gear ratios required");
        for (Object raw : rawRatios) {
            if (!(raw instanceof Number value)) throw new IllegalArgumentException("Numeric gear ratios required");
            ratios.add(value.doubleValue());
        }
        VehicleDefinition.Engine engine = new VehicleDefinition.Engine(number(config, "engine.torque"),
                number(config, "engine.idle-rpm"), number(config, "engine.redline-rpm"), number(config, "engine.inertia"),
                number(config, "engine.wheel-radius"), number(config, "engine.final-drive"), number(config, "engine.reverse-ratio"),
                ratios, number(config, "engine.shift-seconds"));
        return new VehicleDefinition(string(config, "id"), VehicleDefinition.parseKind(string(config, "kind")), number(config, "mass"),
                vector(config, "center-of-mass"), vector(config, "inertia"), colliders, seats, supports,
                number(config, "support-length"), number(config, "grip"), number(config, "maximum-speed"),
                number(config, "fuel.capacity"), number(config, "fuel.consumption"), engine, number(config, "buoyancy"),
                number(config, "wing-area"), number(config, "lift"), parts);
    }

    static List<VehicleDefinition.Part> decodeParts(ConfigurationSection config) {
        List<VehicleDefinition.Part> parts = new ArrayList<>();
        ConfigurationSection partRoot = section(config, "parts");
        for (String key : partRoot.getKeys(false)) {
            ConfigurationSection saved = section(partRoot, key);
            List<?> list = saved.getList("matrix");
            if (list == null || list.size() != 16) throw new IllegalArgumentException("16 matrix values required");
            float[] values = new float[16];
            for (int index = 0; index < values.length; index++) {
                if (!(list.get(index) instanceof Number value)) throw new IllegalArgumentException("Numeric matrix required");
                values[index] = value.floatValue();
            }
            parts.add(new VehicleDefinition.Part(VehicleDefinition.PartKind.valueOf(string(saved, "kind")), string(saved, "payload"),
                    new Matrix4f().set(values), VehicleDefinition.Animation.valueOf(string(saved, "animation")),
                    string(saved, "billboard"), string(saved, "item-transform"), string(saved, "alignment"),
                    integer(saved, "block-light"), integer(saved, "sky-light"), integer(saved, "background"),
                    integer(saved, "line-width"), (byte) integer(saved, "opacity"), saved.getBoolean("shadow"), saved.getBoolean("see-through")));
        }
        return List.copyOf(parts);
    }

    private static YamlConfiguration load(Path path) throws IOException {
        if (Files.size(path) > 8388608) throw new IOException("Vehicle file exceeds 8 MiB: " + path);
        YamlConfiguration config = new YamlConfiguration();
        try { config.load(path.toFile()); }
        catch (InvalidConfigurationException exception) { throw new IOException("Invalid YAML: " + path, exception); }
        if (integer(config, "version") != 1) throw new IOException("Unsupported vehicle data version: " + path);
        return config;
    }

    private static void write(Path path, YamlConfiguration config) throws IOException {
        String content = config.saveToString();
        if (content.getBytes(StandardCharsets.UTF_8).length > 8388608) throw new IOException("Vehicle file exceeds 8 MiB");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        ConfigurationSection result = parent.getConfigurationSection(key);
        if (result == null) throw new IllegalArgumentException("Missing section: " + key);
        return result;
    }
    private static String string(ConfigurationSection section, String key) {
        Object value = section.get(key);
        if (!(value instanceof String result)) throw new IllegalArgumentException("Missing string: " + key);
        return result;
    }
    private static double number(ConfigurationSection section, String key) {
        Object value = section.get(key);
        if (!(value instanceof Number result) || !Double.isFinite(result.doubleValue()))
            throw new IllegalArgumentException("Missing finite number: " + key);
        return result.doubleValue();
    }
    private static int integer(ConfigurationSection section, String key) {
        double value = number(section, key);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Integer required: " + key);
        return (int) value;
    }
    private static VehicleVector vector(ConfigurationSection section, String key) {
        return new VehicleVector(number(section, key + ".x"), number(section, key + ".y"), number(section, key + ".z"));
    }
    private static void putVector(ConfigurationSection section, String key, VehicleVector vector) {
        section.set(key + ".x", vector.coordinateX());
        section.set(key + ".y", vector.coordinateY());
        section.set(key + ".z", vector.coordinateZ());
    }
}
