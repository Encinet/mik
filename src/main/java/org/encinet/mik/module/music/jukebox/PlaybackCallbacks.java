package org.encinet.mik.module.music.jukebox;

/** Terminal and start events emitted by a playback backend. */
interface PlaybackCallbacks {

    boolean isValid();

    void started();

    /** The backend is replacing a failed online stream while retaining user-visible state. */
    default void retrying() {
    }

    void failed(Throwable error);

    void finished();

    void cancelled();
}
