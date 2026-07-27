package org.encinet.mik.module.music.jukebox;

/** Defines how a jukebox advances through its playlist. */
public enum JukeboxPlaybackMode {
    REPEAT_ALL,
    REPEAT_ONE,
    SHUFFLE;

    public JukeboxPlaybackMode next() {
        JukeboxPlaybackMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }
}
