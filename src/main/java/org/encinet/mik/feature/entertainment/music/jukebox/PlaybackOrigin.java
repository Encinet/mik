package org.encinet.mik.feature.entertainment.music.jukebox;

/** Distinguishes a player's action from queue continuation, automation and chunk restoration. */
public enum PlaybackOrigin {
    /** Applies the player's online playback quota and reports inaudible voice output. */
    PLAYER,
    /** May carry a nearby listener for presentation, without spending that listener's quota. */
    AUTOMATIC
}
