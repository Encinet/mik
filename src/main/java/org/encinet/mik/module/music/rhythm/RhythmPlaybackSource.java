package org.encinet.mik.module.music.rhythm;

import org.bukkit.block.Block;

import java.util.Optional;

/** Read-only playback clock boundary used by rhythm-game sessions. */
@FunctionalInterface
public interface RhythmPlaybackSource {
    Optional<RhythmPlaybackSnapshot> rhythmPlayback(Block block);
}
