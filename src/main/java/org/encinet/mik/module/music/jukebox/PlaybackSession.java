package org.encinet.mik.module.music.jukebox;

/** One backend-owned playback attempt. All lifecycle methods are idempotent. */
interface PlaybackSession {

    void start();

    PlaybackStatus status();

    long positionMillis();

    default void updateSettings(JukeboxSoundSettings settings) {
    }

    void stop();
}
