package org.encinet.mik.module.music.jukebox;

import org.bukkit.block.Block;

/** Read-only playback state exposed to views and status commands. */
@FunctionalInterface
public interface JukeboxPlaybackStatus {
    PlaybackStatus status(Block block);

    /**
     * Returns the status and playback position from the same observation.
     * Implementations that only expose a status retain a zeroed clock.
     */
    default JukeboxPlaybackSnapshot snapshot(Block block) {
        return new JukeboxPlaybackSnapshot(status(block), 0L);
    }
}
