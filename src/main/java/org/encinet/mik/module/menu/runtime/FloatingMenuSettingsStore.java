package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuScale;
import org.encinet.mik.module.menu.FloatingMenuTextScale;
import org.encinet.mik.module.menu.FloatingMenuPreferences;
import org.encinet.mik.module.menu.FloatingMenuFieldOfView;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Small persistent player-preference store owned by the menu subsystem. */
final class FloatingMenuSettingsStore {
    private final File settingsFile;
    private final Logger logger;
    private final Map<UUID, FloatingMenuPreferences> cache = new ConcurrentHashMap<>();
    private YamlConfiguration settingsData;

    FloatingMenuSettingsStore(JavaPlugin plugin) {
        this(new File(Objects.requireNonNull(plugin, "plugin").getDataFolder(), "menu-settings.yml"), plugin.getLogger());
    }

    FloatingMenuSettingsStore(File settingsFile, Logger logger) {
        this.settingsFile = Objects.requireNonNull(settingsFile, "settingsFile");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void enable() {
        File directory = settingsFile.getParentFile();
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            logger.warning("Failed to create the plugin data folder for menu settings");
        }
        settingsData = YamlConfiguration.loadConfiguration(settingsFile);
        cache.clear();
    }

    FloatingMenuPreferences preferences(UUID playerId) {
        requireEnabled();
        return cache.computeIfAbsent(playerId, id -> new FloatingMenuPreferences(
                FloatingMenuScale.fromId(settingsData.getString(id + ".scale")),
                FloatingMenuTextScale.fromId(settingsData.getString(id + ".text-scale")),
                FloatingMenuFieldOfView.fromDegrees(settingsData.getInt(id + ".field-of-view", 70))));
    }

    void setPreferences(UUID playerId, FloatingMenuPreferences preferences) {
        requireEnabled();
        FloatingMenuPreferences value = Objects.requireNonNull(preferences, "preferences");
        if (value.equals(preferences(playerId))) return;
        cache.put(playerId, value);
        settingsData.set(playerId + ".scale", value.layout().id());
        settingsData.set(playerId + ".text-scale", value.text().id());
        settingsData.set(playerId + ".field-of-view", value.fieldOfView().degrees());
        try {
            settingsData.save(settingsFile);
        } catch (IOException exception) {
            logger.warning("Failed to save menu settings for " + playerId
                    + ": " + exception.getMessage());
        }
    }

    void forget(UUID playerId) {
        cache.remove(playerId);
    }

    private void requireEnabled() {
        if (settingsData == null) {
            throw new IllegalStateException("Floating menu settings are not enabled");
        }
    }
}
