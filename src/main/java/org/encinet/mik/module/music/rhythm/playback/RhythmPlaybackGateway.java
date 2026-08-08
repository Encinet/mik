package org.encinet.mik.module.music.rhythm.playback;

import org.bukkit.block.Block;

import java.util.Optional;
import java.util.UUID;

/**
 * Game-facing boundary for a jukebox rhythm transport.
 *
 * <p>Browsing mode and difficulty never acquire a participation. The first
 * actual game participation prepares the song while keeping its clock at zero;
 * the game explicitly starts playback after its countdown. Closing the last
 * participation returns the jukebox to its silent waiting state.</p>
 */
public interface RhythmPlaybackGateway {

    Optional<RhythmPlaybackSnapshot> rhythmPlayback(Block block);

    Optional<Participation> join(Block block, UUID playerId);

    interface Participation extends AutoCloseable {
        UUID playbackId();

        /** Releases the prepared transport after the player's pre-roll. */
        void startPlayback();

        @Override
        void close();
    }
}
