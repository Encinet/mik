package org.encinet.mik.feature.entertainment.music.jukebox;

import org.encinet.mik.feature.entertainment.music.device.JukeboxSoundSettings;

/** One backend-owned playback attempt. All lifecycle methods are idempotent. */
interface PlaybackSession {
    enum OutputKind { UNRESOLVED, NATIVE_RECORD, NOTE_EVENTS, ENCODED_AUDIO }
    default OutputKind outputKind() { return OutputKind.NATIVE_RECORD; }


    /** Loads and analyzes the track without advancing its playback clock. */
    void prepare();

    /** Starts playback once preparation is complete, or queues that request. */
    void start();

    JukeboxPlaybackSnapshot snapshot();

    default String mediaVersion() { return null; }

    default PlaybackStatus status() { return snapshot().status(); }

    default long positionMillis() { return snapshot().positionMillis(); }

    default void updateSettings(JukeboxSoundSettings settings) {
    }

    void stop();
}
