package org.encinet.mik.feature.entertainment.music.jukebox;

public enum PlaybackStatus {
    STOPPED,
    LOADING,
    PLAYING,
    BUFFERING,
    PAUSED,
    FINISHED,
    RESUMABLE;

    boolean hasLiveTransport() {
        return this == PLAYING || this == BUFFERING || this == PAUSED;
    }
}
