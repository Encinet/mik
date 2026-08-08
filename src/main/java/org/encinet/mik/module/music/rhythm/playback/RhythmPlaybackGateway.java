package org.encinet.mik.module.music.rhythm.playback;

import org.bukkit.block.Block;

import java.util.Optional;
import java.util.UUID;

/**
 * Game-facing boundary for a jukebox rhythm transport.
 *
 * <p>Browsing mode and difficulty never acquire a participation. The first
 * actual game participation starts the song; closing the last participation
 * returns the jukebox to its silent waiting state.</p>
 */
public interface RhythmPlaybackGateway {

    Optional<RhythmPlaybackSnapshot> rhythmPlayback(Block block);

    Optional<Participation> join(Block block, UUID playerId);

    interface Participation extends AutoCloseable {
        UUID playbackId();

        @Override
        void close();
    }
}
