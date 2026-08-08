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

    /** Returns the mode captured by the active attempt; setting changes affect the next one. */
    default java.util.Optional<JukeboxExperienceMode> activeExperienceMode(Block block) {
        return java.util.Optional.empty();
    }

    /** Reports chart readiness without leaking the mutable extraction timeline into UI code. */
    default JukeboxRhythmReadiness rhythmReadiness(Block block) {
        return JukeboxRhythmReadiness.UNAVAILABLE;
    }
}
