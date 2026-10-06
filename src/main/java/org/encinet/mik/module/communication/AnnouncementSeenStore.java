package org.encinet.mik.module.communication;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/** Main-thread ledger for the announcement position each player has seen. */
final class AnnouncementSeenStore {
    private final Path file;
    private final Logger logger;
    private final Map<UUID, Long> seenUntil = new HashMap<>();
    private boolean loaded;

    AnnouncementSeenStore(Path file, Logger logger) {
        this.file = Objects.requireNonNull(file, "file");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void load() {
        loaded = false;
        if (!Files.exists(file)) {
            seenUntil.clear();
            loaded = true;
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file.toFile());
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalStateException("Could not load announcements-state.yml", error);
        }
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null && !config.getKeys(false).isEmpty()) {
            throw new IllegalStateException("Invalid players section in announcements-state.yml");
        }
        Map<UUID, Long> loadedPositions = new HashMap<>();
        if (players != null) {
            for (String rawId : players.getKeys(false)) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(rawId);
                } catch (IllegalArgumentException invalidId) {
                    logger.warning("Invalid UUID in announcements-state.yml: " + rawId);
                    continue;
                }
                ConfigurationSection player = players.getConfigurationSection(rawId);
                Object rawPosition = player == null ? null : player.get("seen-until");
                if (!(rawPosition instanceof Number position)) {
                    throw new IllegalStateException(
                            "Invalid seen-until for " + rawId + " in announcements-state.yml");
                }
                if (position.longValue() > 0) loadedPositions.put(playerId, position.longValue());
            }
        }
        seenUntil.clear();
        seenUntil.putAll(loadedPositions);
        loaded = true;
    }

    long positionOrStartAt(UUID playerId, long latestTimestamp) {
        requireLoaded();
        return seenUntil.computeIfAbsent(playerId, ignored -> latestTimestamp);
    }

    void markSeenThrough(UUID playerId, long timestamp) {
        requireLoaded();
        if (timestamp > 0) seenUntil.merge(playerId, timestamp, Math::max);
    }

    void save() {
        if (!loaded) return;
        YamlConfiguration config = new YamlConfiguration();
        seenUntil.forEach((playerId, position) ->
                config.set("players." + playerId + ".seen-until", position));
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), ".announcements-state-", ".tmp");
            config.save(temporary.toFile());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not save announcements-state.yml", error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The next save creates a separate temporary file.
                }
            }
        }
    }

    private void requireLoaded() {
        if (!loaded) throw new IllegalStateException("Announcement positions are not loaded");
    }
}
