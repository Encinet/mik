package org.encinet.mik.module.music.jukebox;

/** Terminal and start events emitted by a playback backend. */
interface PlaybackCallbacks {

    boolean isValid();

    void started();

    void failed(Throwable error);

    void finished();

    void cancelled();
}
