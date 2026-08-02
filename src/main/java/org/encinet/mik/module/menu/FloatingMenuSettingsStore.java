package org.encinet.mik.module.menu;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Small persistent player-preference store owned by the menu subsystem. */
final class FloatingMenuSettingsStore {
    private final JavaPlugin plugin;
    private final Map<UUID, FloatingMenuScale> cache = new ConcurrentHashMap<>();
    private File settingsFile;
    private YamlConfiguration settingsData;

    FloatingMenuSettingsStore(JavaPlugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
    }

    void enable() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create the plugin data folder for menu settings");
        }
        settingsFile = new File(plugin.getDataFolder(), "menu-settings.yml");
        settingsData = YamlConfiguration.loadConfiguration(settingsFile);
    }

    FloatingMenuScale scale(UUID playerId) {
        requireEnabled();
        return cache.computeIfAbsent(playerId, id -> FloatingMenuScale.fromId(
                settingsData.getString(id + ".scale")));
    }

    void setScale(UUID playerId, FloatingMenuScale scale) {
        requireEnabled();
        FloatingMenuScale value = java.util.Objects.requireNonNull(scale, "scale");
        cache.put(playerId, value);
        settingsData.set(playerId + ".scale", value.id());
        try {
            settingsData.save(settingsFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save menu settings for " + playerId
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
