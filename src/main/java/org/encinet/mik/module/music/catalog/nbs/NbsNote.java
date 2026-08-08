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
        type = java.util.Objects.requireNonNull(type, "type");
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        if (instrument < 0 || instrument > 255) {
            throw new IllegalArgumentException("instrument must be between 0 and 255");
        }
        if (key < 0 || key > 87) {
            throw new IllegalArgumentException("key must be between 0 and 87");
        }
        int maximumVelocity = type == NbsNoteType.SOUND ? 100 : 255;
        if (velocity < 0 || velocity > maximumVelocity) {
            throw new IllegalArgumentException(
                    "velocity is out of range for the note type");
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
        int maximumNotePanning = type == NbsNoteType.SOUND ? 100 : 155;
        if (notePanning < -100 || notePanning > maximumNotePanning) {
            throw new IllegalArgumentException(
                    "notePanning is out of range for the note type");
        }
        if (instrumentKey < 0 || instrumentKey > 87) {
            throw new IllegalArgumentException("instrumentKey must be between 0 and 87");
        }
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

    /** Raw unsigned panning byte stored in the NBS note record. */
    public int encodedPanning() {
        return notePanning + 100;
    }

    /**
     * First one-based layer affected by a Sound Stopper; zero means all layers.
     */
    public int soundStopStartLayer() {
        requireSoundStopper();
        return Math.max(0, finePitch);
    }

    /**
     * Last one-based layer affected by a Sound Stopper, decoded from the two
     * bytes that normally hold panning and velocity.
     */
    public int soundStopEndLayer() {
        requireSoundStopper();
        int lowByte = Math.floorMod(encodedPanning() - 100, 256);
        int highByte = Math.floorMod(velocity - 100, 256);
        return lowByte | highByte << 8;
    }

    private void requireSoundStopper() {
        if (type != NbsNoteType.SOUND_STOP) {
            throw new IllegalStateException("note is not a Sound Stopper");
        }
    }
}
