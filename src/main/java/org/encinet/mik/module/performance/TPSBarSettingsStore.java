package org.encinet.mik.module.performance;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Persistent per-player TPS bar visibility preferences. */
final class TPSBarSettingsStore {

    private final File settingsFile;
    private YamlConfiguration settingsData;

    TPSBarSettingsStore(File settingsFile) {
        this.settingsFile = Objects.requireNonNull(settingsFile, "settingsFile");
    }

    void load() {
        settingsData = YamlConfiguration.loadConfiguration(settingsFile);
    }

    boolean isEnabled(UUID playerId) {
        requireLoaded();
        return settingsData.getBoolean(path(playerId), false);
    }

    void setEnabled(UUID playerId, boolean enabled) throws IOException {
        requireLoaded();
        settingsData.set(path(playerId), enabled);
        settingsData.save(settingsFile);
    }

    private static String path(UUID playerId) {
        return Objects.requireNonNull(playerId, "playerId") + ".enabled";
    }

    private void requireLoaded() {
        if (settingsData == null) {
            throw new IllegalStateException("TPS bar settings are not loaded");
        }
    }
}
