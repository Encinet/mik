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
        Map<String, PrisonRecord> prisons = new LinkedHashMap<>();
        ConfigurationSection prisonRoot = yaml.getConfigurationSection("prisons");
        if (yaml.contains("prisons") && prisonRoot == null) throw new IOException("Invalid prisons section");
        if (prisonRoot != null) for (String key : prisonRoot.getKeys(false)) {
            ConfigurationSection section = requiredSection(prisonRoot, key);
            Map<String, PrisonLocation> cells = new LinkedHashMap<>();
            ConfigurationSection cellRoot = section.getConfigurationSection("cells");
            if (cellRoot != null) for (String cell : cellRoot.getKeys(false))
                cells.put(cell, readLocation(requiredSection(cellRoot, cell)));
            UUID owner = uuid(section, "owner", true);
            PrisonRecord prison = new PrisonRecord(key, owner, location(section, "spawn"), location(section, "exit"),
                    location(section, "corner1"), location(section, "corner2"), cells);
            if (prisons.putIfAbsent(prison.name(), prison) != null)
                throw new IOException("Duplicate prison name in data: " + key);
        }
        Map<UUID, PrisonerRecord> prisoners = new LinkedHashMap<>();
        ConfigurationSection prisonerRoot = yaml.getConfigurationSection("prisoners");
        if (yaml.contains("prisoners") && prisonerRoot == null) throw new IOException("Invalid prisoners section");
        if (prisonerRoot != null) for (String key : prisonerRoot.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid prisoner UUID: " + key, exception);
            }
            ConfigurationSection section = requiredSection(prisonerRoot, key);
            PrisonerRecord prisoner = new PrisonerRecord(id, requiredString(section, "name"),
                    requiredString(section, "prison"), requiredString(section, "cell"),
                    nonNegativeLong(section, "sentence-end"), requiredBoolean(section, "require-unban", true),
                    location(section, "return-location"), nonNegativeLong(section, "jailed-at"));
            if (prisoners.putIfAbsent(id, prisoner) != null)
                throw new IOException("Duplicate prisoner UUID: " + id);
        }
        return new PrisonData(prisons, prisoners);
    }

    void save(PrisonData data) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection prisonRoot = yaml.createSection("prisons");
        for (PrisonRecord prison : data.prisons().values()) {
            ConfigurationSection section = prisonRoot.createSection(prison.name());
            if (prison.owner() != null) section.set("owner", prison.owner().toString());
            writeLocation(section, "spawn", prison.spawn());
            writeLocation(section, "exit", prison.exit());
            writeLocation(section, "corner1", prison.corner1());
            writeLocation(section, "corner2", prison.corner2());
            ConfigurationSection cells = section.createSection("cells");
            prison.cells().forEach((name, location) -> writeLocation(cells, name, location));
        }
        ConfigurationSection prisonerRoot = yaml.createSection("prisoners");
        for (PrisonerRecord prisoner : data.prisoners().values()) {
            ConfigurationSection section = prisonerRoot.createSection(prisoner.playerId().toString());
            section.set("name", prisoner.playerName());
            section.set("prison", prisoner.prison());
            section.set("cell", prisoner.cell());
            section.set("sentence-end", prisoner.sentenceEndMillis());
            section.set("require-unban", prisoner.requireUnban());
            section.set("jailed-at", prisoner.jailedAtMillis());
            writeLocation(section, "return-location", prisoner.returnLocation());
        }
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

    private static String requiredString(ConfigurationSection section, String key) throws IOException {
        String value = section.getString(key);
        if (value == null || value.isBlank()) throw new IOException("Missing string: " + section.getCurrentPath() + "." + key);
        return value;
    }

    private static long nonNegativeLong(ConfigurationSection section, String key) throws IOException {
        if (!section.isLong(key) || section.getLong(key) < 0)
            throw new IOException("Invalid number: " + section.getCurrentPath() + "." + key);
        return section.getLong(key);
    }

    private static boolean requiredBoolean(ConfigurationSection section, String key, boolean defaultValue)
            throws IOException {
        if (!section.contains(key)) return defaultValue;
        Object value = section.get(key);
        if (!(value instanceof Boolean result))
            throw new IOException("Invalid boolean: " + section.getCurrentPath() + "." + key);
        return result;
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
