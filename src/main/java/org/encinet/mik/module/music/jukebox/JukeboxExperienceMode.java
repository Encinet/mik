package org.encinet.mik.module.music.jukebox;

/** Selects whether playback prioritizes immediate listening or a ready rhythm chart. */
public enum JukeboxExperienceMode {
    MUSIC,
    RHYTHM;

    public JukeboxExperienceMode next() {
        return this == MUSIC ? RHYTHM : MUSIC;
    }

    public boolean waitsForRhythmAnalysis() {
        return this == RHYTHM;
    }
}
