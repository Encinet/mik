package org.encinet.mik.module.player;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Main-thread home cache with ordered, atomic YAML snapshots. */
final class HomeStore implements AutoCloseable {
    private final Path file;
    private final Logger logger;
    private final Map<UUID, Map<String, Entry>> homes = new HashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("mik-home-writer").factory());
    private boolean closed;
    private volatile RuntimeException lastWriteFailure;

    HomeStore(Path file, Logger logger) {
        this.file = Objects.requireNonNull(file, "file");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    int load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.createFile(file);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to create homes.yml", error);
        }
        homes.clear();
        YamlConfiguration data = new YamlConfiguration();
        data.options().pathSeparator('\0');
        try {
            data.load(file.toFile());
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalStateException("Failed to load homes.yml", error);
        }
        for (String rawPlayerId : data.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(rawPlayerId);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            var section = data.getConfigurationSection(rawPlayerId);
            if (section == null) continue;
            Map<String, Entry> playerHomes = new HashMap<>();
            readEntries(section, "", playerHomes);
            homes.put(playerId, playerHomes);
        }
        return homes.values().stream().mapToInt(Map::size).sum();
    }

    Entry get(UUID playerId, String name) {
        Map<String, Entry> playerHomes = homes.get(playerId);
        return playerHomes == null ? null : playerHomes.get(name);
    }

    List<String> names(UUID playerId) {
        Map<String, Entry> playerHomes = homes.get(playerId);
        return playerHomes == null ? new ArrayList<>() : new ArrayList<>(playerHomes.keySet());
    }

    void put(UUID playerId, String name, Entry entry) {
        requireOpen();
        homes.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(name, entry);
        saveAsync();
    }

    boolean remove(UUID playerId, String name) {
        requireOpen();
        Map<String, Entry> playerHomes = homes.get(playerId);
        if (playerHomes == null || playerHomes.remove(name) == null) return false;
        saveAsync();
        return true;
    }

    static Material icon(String input) {
        Material material = Material.matchMaterial(input.toUpperCase(Locale.ROOT));
        return material == null || !material.isItem() || material == Material.AIR
                ? null : material;
    }

    private void saveAsync() {
        Map<UUID, Map<String, Entry>> snapshot = new HashMap<>();
        homes.forEach((playerId, playerHomes) ->
                snapshot.put(playerId, new HashMap<>(playerHomes)));
        writer.execute(() -> write(snapshot));
    }

    private void write(Map<UUID, Map<String, Entry>> snapshot) {
        YamlConfiguration data = new YamlConfiguration();
        data.options().pathSeparator('\0');
        snapshot.forEach((playerId, playerHomes) -> {
            var section = data.createSection(playerId.toString());
            playerHomes.forEach((name, entry) ->
                    section.set(formatKey(name, entry), entry.locationRaw()));
        });
        Path temporary = null;
        try {
            temporary = Files.createTempFile(file.getParent(), ".homes-", ".tmp");
            data.save(temporary.toFile());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            lastWriteFailure = null;
        } catch (IOException | RuntimeException error) {
            logger.severe("Failed to save homes.yml: " + error.getMessage());
            lastWriteFailure = new IllegalStateException("Failed to save homes.yml", error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The next save still uses a new temporary file.
                }
            }
        }
    }

    private static void readEntries(ConfigurationSection section, String prefix,
                                    Map<String, Entry> playerHomes) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection nested) {
                // Older saves split names containing '.' into nested YAML sections.
                readEntries(nested, prefix + key + ".", playerHomes);
            } else if (value instanceof String rawLocation) {
                ParsedKey parsed = parseKey(prefix + key);
                playerHomes.put(parsed.name(), new Entry(rawLocation, parsed.icon()));
            }
        }
    }

    private static ParsedKey parseKey(String storedName) {
        int left = storedName.lastIndexOf('<');
        int right = storedName.endsWith(">") ? storedName.length() - 1 : -1;
        if (left > 0 && right > left) {
            Material material = icon(storedName.substring(left + 1, right));
            if (material != null) return new ParsedKey(storedName.substring(0, left), material);
        }
        return new ParsedKey(storedName, null);
    }

    private static String formatKey(String name, Entry entry) {
        return entry.icon() == null ? name : name + "<" + entry.icon().name() + ">";
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Home store is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for homes.yml writes to finish");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for homes.yml writes to finish", error);
        }
        if (lastWriteFailure != null) throw lastWriteFailure;
    }

    record Entry(String locationRaw, Material icon) {
        Entry {
            Objects.requireNonNull(locationRaw, "locationRaw");
        }
    }

    private record ParsedKey(String name, Material icon) { }
}
