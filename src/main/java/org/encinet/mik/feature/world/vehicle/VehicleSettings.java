package org.encinet.mik.feature.world.vehicle;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

record VehicleSettings(
        int maximumActive,
        int maximumInstances,
        int maximumPerOwner,
        double activationDistance,
        Set<String> disabledWorlds
) {
    static final VehicleSettings DEFAULT = new VehicleSettings(16, 256, 16, 80, Set.of());
    private static final Set<String> KEYS = Set.of(
            "maximum-active", "maximum-instances", "maximum-per-owner", "activation-distance", "disabled-worlds");

    static VehicleSettings load(Path path) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var reader = Files.newBufferedReader(path)) {
            yaml.load(reader);
        } catch (NoSuchFileException ignored) {
            return DEFAULT;
        }
        for (String key : yaml.getKeys(false)) {
            if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown vehicle setting: " + key);
        }
        int maximumInstances = boundedInteger(yaml, "maximum-instances", DEFAULT.maximumInstances, 512);
        int maximumPerOwner = boundedInteger(yaml, "maximum-per-owner",
                Math.min(DEFAULT.maximumPerOwner, maximumInstances), maximumInstances);
        int maximumActive = boundedInteger(yaml, "maximum-active", DEFAULT.maximumActive, 64);

        if (!(yaml.get("activation-distance", DEFAULT.activationDistance) instanceof Number distance)) {
            throw new IllegalArgumentException("activation-distance must be a number");
        }
        double activationDistance = distance.doubleValue();
        VehicleDefinition.positive(activationDistance, "activation-distance", 128);

        if (!(yaml.get("disabled-worlds", List.of()) instanceof List<?> entries)) {
            throw new IllegalArgumentException("disabled-worlds must be a list of world names");
        }
        Set<String> worlds = new HashSet<>();
        for (Object entry : entries) {
            if (!(entry instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("disabled-worlds must contain non-empty world names");
            }
            worlds.add(name);
        }
        return new VehicleSettings(maximumActive, maximumInstances, maximumPerOwner, activationDistance,
                Set.copyOf(worlds));
    }

    private static int boundedInteger(YamlConfiguration yaml, String key, int fallback, int maximum) {
        if (yaml.get(key, fallback) instanceof Integer value && value >= 1 && value <= maximum) {
            return value;
        }
        throw new IllegalArgumentException(key + " must be an integer between 1 and " + maximum);
    }
}
