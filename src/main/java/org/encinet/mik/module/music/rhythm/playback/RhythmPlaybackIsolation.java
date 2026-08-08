package org.encinet.mik.module.music.rhythm.playback;

import org.bukkit.entity.Player;

/** Temporarily excludes one player from managed jukebox output. */
@FunctionalInterface
public interface RhythmPlaybackIsolation {
    SilenceLease silenceFor(Player player);

    @FunctionalInterface
    interface SilenceLease extends AutoCloseable {
        @Override
        void close();
    }
}
