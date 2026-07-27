package org.encinet.mik.module.music.catalog.nbs;

public record NbsNote(
        int tick,
        int instrument,
        int key,
        int velocity,
        int layerVolume,
        int panning,
        int finePitch,
        int layer,
        int sourceInstrument,
        int notePanning,
        int instrumentKey,
        NbsNoteType type
) {
    public NbsNote(int tick, int instrument, int key, int velocity,
                   int layerVolume, int panning, int finePitch) {
        this(tick, instrument, key, velocity, layerVolume, panning, finePitch,
                0, instrument, panning, 45, NbsNoteType.SOUND);
    }

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
        if (layer < 0) {
            throw new IllegalArgumentException("layer must not be negative");
        }
        if (sourceInstrument < 0 || sourceInstrument > 255) {
            throw new IllegalArgumentException("sourceInstrument must be between 0 and 255");
        }
        if (notePanning < -100 || notePanning > 100) {
            throw new IllegalArgumentException("notePanning must be between -100 and 100");
        }
        if (instrumentKey < 0 || instrumentKey > 87) {
            throw new IllegalArgumentException("instrumentKey must be between 0 and 87");
        }
        type = java.util.Objects.requireNonNull(type, "type");
    }

    public int playbackPitchCents() {
        return (key - 45) * 100 + (instrumentKey - 45) * 100 + finePitch;
    }

    public double tempoChangeTicksPerSecond() {
        if (type != NbsNoteType.TEMPO_CHANGE) {
            return Double.NaN;
        }
        return Math.abs((long) finePitch) / 15.0;
    }
}
