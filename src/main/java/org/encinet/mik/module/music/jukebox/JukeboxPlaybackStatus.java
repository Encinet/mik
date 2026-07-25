package org.encinet.mik.module.music.jukebox;

import org.bukkit.block.Block;

/** Read-only playback state exposed to views and status commands. */
@FunctionalInterface
public interface JukeboxPlaybackStatus {
    PlaybackStatus status(Block block);
}
