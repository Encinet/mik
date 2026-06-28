package org.encinet.mik.module.pvp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PvpSettingsStore {

    private static final boolean DEFAULT_PVP_ENABLED = false;
    private static final boolean DEFAULT_PROTECT_MOBS = true;
    private static final boolean DEFAULT_ALLOW_MOUNTED_DAMAGE = true;
    private static final boolean DEFAULT_ENABLE_ON_DEATH = true;

    private final JavaPlugin plugin;
    private final Map<UUID, PvpSettings> cache = new ConcurrentHashMap<>();

    private File settingsFile;
    private YamlConfiguration settingsData;

    PvpSettingsStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        settingsFile = new File(plugin.getDataFolder(), "pvp-settings.yml");
        if (!settingsFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().severe("Failed to create plugin data folder.");
                }
                if (!settingsFile.createNewFile()) {
                    plugin.getLogger().warning("pvp-settings.yml already exists but was not visible during setup.");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create pvp-settings.yml: " + e.getMessage());
            }
        }
        settingsData = YamlConfiguration.loadConfiguration(settingsFile);
    }

    PvpSettings get(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::load);
    }

    void save(UUID playerId, PvpSettings settings) {
        cache.put(playerId, settings);
        String path = playerId.toString();
        settingsData.set(path + ".enabled", settings.enabled());
        settingsData.set(path + ".protect-mobs", settings.protectMobs());
        settingsData.set(path + ".allow-mounted-mob-damage", settings.allowMountedMobDamage());
        settingsData.set(path + ".enable-on-death", settings.enableOnDeath());
        try {
            settingsData.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save PVP settings for " + playerId + ": " + e.getMessage());
        }
    }

    void invalidate(UUID playerId) {
        cache.remove(playerId);
    }

    private PvpSettings load(UUID playerId) {
        String path = playerId.toString();
        return new PvpSettings(
                loadBoolean(path, "enabled", DEFAULT_PVP_ENABLED),
                loadBoolean(path, "protect-mobs", DEFAULT_PROTECT_MOBS),
                loadBoolean(path, "allow-mounted-mob-damage", DEFAULT_ALLOW_MOUNTED_DAMAGE),
                loadBoolean(path, "enable-on-death", DEFAULT_ENABLE_ON_DEATH)
        );
    }

    private boolean loadBoolean(String path, String key, boolean fallback) {
        String fullPath = path + "." + key;
        if (settingsData.contains(fullPath)) {
            return settingsData.getBoolean(fullPath, fallback);
        }
        return fallback;
    }
}
