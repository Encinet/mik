package org.encinet.mik.module.music.jukebox;

import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.encinet.mik.module.music.catalog.MusicTrack;

/** Playback operations required to advance a jukebox queue. */
public interface JukeboxPlayback {

    boolean isPlaying(Block block);

    boolean playVirtualTrackOnJukebox(
            Player player, Jukebox jukebox, MusicTrack track, Runnable onStarted);
}
