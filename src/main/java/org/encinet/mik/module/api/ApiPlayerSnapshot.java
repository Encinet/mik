package org.encinet.mik.module.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Main-thread player state published as an immutable byte snapshot for HTTP readers. */
final class ApiPlayerSnapshot {
    private static final String STATE_FILE_NAME = "api-state.yml";
    private static final String PEAK_ONLINE_PATH = "peak-online";

    record Identity(UUID uuid, String name) {
    }

    private record OnlinePlayer(UUID uuid, String name, String joinedAt) {
    }

    private final Path stateFile;
    private final Logger logger;
    private final Map<UUID, OnlinePlayer> onlinePlayers = new HashMap<>();
    private volatile byte[] jsonBytes = bytes("{\"online\":0,\"peak_online\":0,\"players\":[]}");
    private int peakOnline;
    private boolean loaded;
    private boolean peakDirty;

    ApiPlayerSnapshot(Path dataFolder, Logger logger) {
        this.stateFile = Objects.requireNonNull(dataFolder, "dataFolder").resolve(STATE_FILE_NAME);
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void load() {
        loaded = false;
        if (Files.notExists(stateFile)) {
            peakOnline = 0;
            peakDirty = false;
            loaded = true;
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(stateFile.toFile());
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalStateException("Could not load " + STATE_FILE_NAME, error);
        }
        Object rawPeak = config.get(PEAK_ONLINE_PATH);
        if (!(rawPeak instanceof Number number)
                || number.longValue() < 0L
                || number.longValue() > Integer.MAX_VALUE
                || number.doubleValue() != number.longValue()) {
            throw new IllegalStateException("Invalid " + PEAK_ONLINE_PATH
                    + " in " + STATE_FILE_NAME);
        }
        peakOnline = number.intValue();
        peakDirty = false;
        loaded = true;
    }

    void bootstrap(Collection<Identity> players, Instant joinedAt) {
        requireLoaded();
        onlinePlayers.clear();
        String timestamp = joinedAt.toString();
        for (Identity player : players) {
            onlinePlayers.put(player.uuid(), new OnlinePlayer(player.uuid(), player.name(), timestamp));
        }
        publish();
    }

    void joined(UUID playerId, String name, Instant joinedAt) {
        requireLoaded();
        onlinePlayers.put(playerId, new OnlinePlayer(playerId, name, joinedAt.toString()));
        publish();
    }

    void left(UUID playerId) {
        requireLoaded();
        onlinePlayers.remove(playerId);
        publish();
    }

    int onlineCount() {
        return onlinePlayers.size();
    }

    int peakOnline() {
        return peakOnline;
    }

    byte[] jsonBytes() {
        return jsonBytes.clone();
    }

    void clear() {
        onlinePlayers.clear();
        rebuildJson();
        if (peakDirty) saveState();
    }

    private void publish() {
        boolean peakChanged = onlinePlayers.size() > peakOnline;
        if (peakChanged) {
            peakOnline = onlinePlayers.size();
            peakDirty = true;
        }
        rebuildJson();
        if (peakDirty) {
            try {
                saveState();
            } catch (IllegalStateException error) {
                logger.log(Level.SEVERE, "Failed to save " + STATE_FILE_NAME, error);
            }
        }
    }

    private void rebuildJson() {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("online", onlinePlayers.size());
        snapshot.addProperty("peak_online", peakOnline);
        JsonArray entries = new JsonArray();
        for (OnlinePlayer player : onlinePlayers.values().stream()
                .sorted(Comparator.comparing(OnlinePlayer::uuid)).toList()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", player.uuid().toString());
            entry.addProperty("name", player.name());
            entry.addProperty("joined_at", player.joinedAt());
            entries.add(entry);
        }
        snapshot.add("players", entries);
        jsonBytes = bytes(snapshot.toString());
    }

    private void saveState() {
        requireLoaded();
        YamlConfiguration config = new YamlConfiguration();
        config.set(PEAK_ONLINE_PATH, peakOnline);
        Path temporary = null;
        try {
            Files.createDirectories(stateFile.getParent());
            temporary = Files.createTempFile(stateFile.getParent(), ".api-state-", ".tmp");
            config.save(temporary.toFile());
            try {
                Files.move(temporary, stateFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
            peakDirty = false;
        } catch (IOException error) {
            throw new IllegalStateException("Could not save " + STATE_FILE_NAME, error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A later save uses a separate temporary file.
                }
            }
        }
    }

    private void requireLoaded() {
        if (!loaded) throw new IllegalStateException("API player state is not loaded");
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
