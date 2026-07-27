package org.encinet.mik.module.music.catalog.nbs;

/** One NBS layer, including its editor state and playback mix. */
public record NbsLayer(String name, int lockState, int volume, int panning) {
    public NbsLayer {
        name = name == null ? "" : name;
        if (lockState < 0 || lockState > 255) {
            throw new IllegalArgumentException("lockState is out of range");
        }
        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException("volume is out of range");
        }
        if (panning < -100 || panning > 100) {
            throw new IllegalArgumentException("panning is out of range");
        }
    }

    public boolean locked() {
        return lockState != 0;
    }

    static NbsLayer defaults() {
        return new NbsLayer("", 0, 100, 0);
    }
}
