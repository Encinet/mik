package org.encinet.mik.feature.entertainment.music.jukebox;

import java.util.Objects;
import java.util.UUID;

/** Persistent device identity. Locations never stand in for identity or authorization. */
public record MusicDevice(UUID id, UUID world, String worldName, int x, int y, int z, String name, int volume,
        int range, UUID group, long lastSeen) {
    public MusicDevice {
        Objects.requireNonNull(id);
        Objects.requireNonNull(world);
        Objects.requireNonNull(name);
        if (volume < JukeboxSoundSettings.MIN_VOLUME_PERCENT || volume > JukeboxSoundSettings.MAX_VOLUME_PERCENT
                || range < JukeboxSoundSettings.MIN_RANGE_BLOCKS || range > JukeboxSoundSettings.MAX_RANGE_BLOCKS)
            throw new IllegalArgumentException("Invalid sound settings");
    }
    public MusicDevice grouped(UUID groupId) {
        return new MusicDevice(id, world, worldName, x, y, z, name, volume, range, groupId, lastSeen);
    }
    public MusicDevice settings(String title, int gain, int radius) {
        return new MusicDevice(id, world, worldName, x, y, z, title, gain, radius, group, lastSeen);
    }
}
