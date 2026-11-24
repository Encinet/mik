package org.encinet.mik.feature.server.prison;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class PrisonStore {
    private final Path file;

    PrisonStore(Path file) {
        this.file = file;
    }

    PrisonData load() throws IOException {
        if (!Files.exists(file)) return PrisonData.empty();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Prison data YAML is invalid: " + file, exception);
        }
        Map<UUID, PrisonRecord> prisons = new LinkedHashMap<>();
        ConfigurationSection prisonRoot = yaml.getConfigurationSection("prisons");
        if (yaml.contains("prisons") && prisonRoot == null) throw new IOException("Invalid prisons section");
        if (prisonRoot != null) for (String key : prisonRoot.getKeys(false)) {
            ConfigurationSection section = requiredSection(prisonRoot, key);
            UUID plot;
            try {
                plot = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Unsupported legacy prison entry keyed by name: " + key, exception);
            }
            if (prisons.putIfAbsent(plot, new PrisonRecord(plot, location(section, "exit"))) != null)
                throw new IOException("Duplicate prison plot in data: " + plot);
        }
        return new PrisonData(prisons);
    }

    void save(PrisonData data) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection prisonRoot = yaml.createSection("prisons");
        for (PrisonRecord prison : data.prisons().values())
            writeLocation(prisonRoot.createSection(prison.plot().toString()), "exit", prison.exit());
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static ConfigurationSection requiredSection(ConfigurationSection parent, String key) throws IOException {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) throw new IOException("Expected section: " + parent.getCurrentPath() + "." + key);
        return section;
    }

    private static UUID uuid(ConfigurationSection section, String key, boolean optional) throws IOException {
        String value = section.getString(key);
        if (value == null && optional) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IOException("Invalid UUID: " + section.getCurrentPath() + "." + key, exception);
        }
    }

    private static PrisonLocation location(ConfigurationSection parent, String key) throws IOException {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (parent.contains(key) && section == null)
            throw new IOException("Invalid location section: " + parent.getCurrentPath() + "." + key);
        return section == null ? null : readLocation(section);
    }

    private static PrisonLocation readLocation(ConfigurationSection section) throws IOException {
        UUID world = uuid(section, "world", false);
        return new PrisonLocation(world, requiredDouble(section, "x"), requiredDouble(section, "y"),
                requiredDouble(section, "z"), (float) requiredDouble(section, "yaw"),
                (float) requiredDouble(section, "pitch"));
    }

    private static double requiredDouble(ConfigurationSection section, String key) throws IOException {
        Object value = section.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw new IOException("Invalid number: " + section.getCurrentPath() + "." + key);
        return number.doubleValue();
    }

    private static void writeLocation(ConfigurationSection parent, String key, PrisonLocation location) {
        if (location == null) return;
        ConfigurationSection section = parent.createSection(key);
        section.set("world", location.world().toString());
        section.set("x", location.x());
        section.set("y", location.y());
        section.set("z", location.z());
        section.set("yaw", (double) location.yaw());
        section.set("pitch", (double) location.pitch());
    }
}
