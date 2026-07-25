package org.encinet.mik.module.music.catalog.nbs;

public record NbsNote(
        int tick,
        int instrument,
        int key,
        int velocity,
        int layerVolume,
        int panning,
        int finePitch
) {
    public NbsNote {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        if (instrument < 0 || instrument > 255) {
            throw new IllegalArgumentException("instrument must be between 0 and 255");
        }
        if (key < 0 || key > 87) {
            throw new IllegalArgumentException("key must be between 0 and 87");
        }
        if (velocity < 0 || velocity > 100) {
            throw new IllegalArgumentException("velocity must be between 0 and 100");
        }
        if (layerVolume < 0 || layerVolume > 100) {
            throw new IllegalArgumentException("layerVolume must be between 0 and 100");
        }
        if (panning < -100 || panning > 100) {
            throw new IllegalArgumentException("panning must be between -100 and 100");
        }
        if (finePitch < Short.MIN_VALUE || finePitch > Short.MAX_VALUE) {
            throw new IllegalArgumentException("finePitch must fit a signed short");
        }
    }
}
