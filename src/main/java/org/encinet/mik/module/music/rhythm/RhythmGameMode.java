package org.encinet.mik.module.music.rhythm;

/** Player-selectable presentation and approach rules over one shared rhythm chart. */
public enum RhythmGameMode {
    FALLING,
    RADIAL,
    SPATIAL_AIM;

    boolean pointerInput() {
        return this == RADIAL || this == SPATIAL_AIM;
    }
}
