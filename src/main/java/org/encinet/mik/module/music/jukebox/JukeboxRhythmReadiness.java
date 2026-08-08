package org.encinet.mik.module.music.jukebox;

/** Read-only preparation state used by jukebox controls without exposing chart internals. */
public enum JukeboxRhythmReadiness {
    UNAVAILABLE,
    WAITING_FOR_PLAYER,
    PREPARING,
    READY,
    NO_BEATS;

    public boolean playable() {
        return this == READY;
    }
}
