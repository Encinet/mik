package org.encinet.mik.module.music.jukebox;

import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.block.Jukebox;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.function.Consumer;

/** Persists sound settings directly on each jukebox block entity. */
public final class JukeboxSettingsStore {

    private static final NamespacedKey VOLUME_PERCENT =
            new NamespacedKey("mik", "music_volume_percent");
    private static final NamespacedKey RANGE_BLOCKS =
            new NamespacedKey("mik", "music_range_blocks");
    private volatile Consumer<Location> stateChangedListener = ignored -> { };

    public void setStateChangedListener(Consumer<Location> listener) {
        stateChangedListener = Objects.requireNonNull(listener, "listener");
    }

    public JukeboxSoundSettings read(Jukebox jukebox) {
        Objects.requireNonNull(jukebox, "jukebox");
        PersistentDataContainer data = jukebox.getPersistentDataContainer();
        Integer volumePercent = data.get(VOLUME_PERCENT, PersistentDataType.INTEGER);
        Integer rangeBlocks = data.get(RANGE_BLOCKS, PersistentDataType.INTEGER);
        return new JukeboxSoundSettings(
                volumePercent == null
                        ? JukeboxSoundSettings.DEFAULT_VOLUME_PERCENT : volumePercent,
                rangeBlocks == null
                        ? JukeboxSoundSettings.DEFAULT_RANGE_BLOCKS : rangeBlocks);
    }

    public void write(Jukebox jukebox, JukeboxSoundSettings settings) {
        Objects.requireNonNull(jukebox, "jukebox");
        Objects.requireNonNull(settings, "settings");
        PersistentDataContainer data = jukebox.getPersistentDataContainer();
        data.set(VOLUME_PERCENT, PersistentDataType.INTEGER, settings.volumePercent());
        data.set(RANGE_BLOCKS, PersistentDataType.INTEGER, settings.rangeBlocks());
        jukebox.update(true, false);
        stateChangedListener.accept(jukebox.getLocation());
    }
}
