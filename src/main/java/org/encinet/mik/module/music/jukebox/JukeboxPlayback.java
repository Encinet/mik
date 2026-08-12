package org.encinet.mik.module.music.jukebox;

import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.encinet.mik.module.music.catalog.MusicTrack;

/** Playback operations required to advance a jukebox queue. */
public interface JukeboxPlayback {

    boolean isPlaying(Block block);

    default boolean playInsertedDisc(Player player, Jukebox jukebox) {
        return playInsertedDisc(player, jukebox, true);
    }

    boolean playInsertedDisc(Player player, Jukebox jukebox,
                             boolean notifyRequesterIfInaudible);

    default boolean playVirtualTrackOnJukebox(
            Player player, Jukebox jukebox, MusicTrack track, Runnable onStarted) {
        return playVirtualTrackOnJukebox(player, jukebox, track, onStarted, true);
    }

    boolean playVirtualTrackOnJukebox(
            Player player, Jukebox jukebox, MusicTrack track, Runnable onStarted,
            boolean notifyRequesterIfInaudible);
}
