package org.encinet.mik.module.music.catalog.nbs;

/** Complete custom-instrument declaration from the optional NBS tail. */
public record NbsCustomInstrument(
        String name,
        String fileName,
        int key,
        int pressKeyFlag
) {
    public NbsCustomInstrument {
        name = name == null ? "" : name;
        fileName = fileName == null ? "" : fileName;
        if (key < 0 || key > 87) {
            throw new IllegalArgumentException("key is out of range");
        }
        if (pressKeyFlag < 0 || pressKeyFlag > 255) {
            throw new IllegalArgumentException("pressKeyFlag is out of range");
        }
    }

    public boolean pressKey() {
        return pressKeyFlag != 0;
    }
}
