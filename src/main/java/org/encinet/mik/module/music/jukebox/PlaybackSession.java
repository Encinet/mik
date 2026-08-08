package org.encinet.mik.module.music.jukebox;

/** One backend-owned playback attempt. All lifecycle methods are idempotent. */
interface PlaybackSession {

    /** Loads and analyzes the track without advancing its playback clock. */
    void prepare();

    /** Starts playback once preparation is complete, or queues that request. */
    void start();

    PlaybackStatus status();

    long positionMillis();

    default void updateSettings(JukeboxSoundSettings settings) {
    }

    void stop();
}
