package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.nbs.NbsNote;
import org.encinet.mik.module.music.catalog.nbs.NbsNoteType;

/** Converts NBS percentages to the client sound gain range of {@code 0..1}. */
final class NbsPlaybackVolume {

    private NbsPlaybackVolume() {
    }

    static float volume(NbsNote note, int volumePercent) {
        if (note.type() != NbsNoteType.SOUND) {
            return 0.0F;
        }
        return (note.velocity() / 100.0F)
                * (note.layerVolume() / 100.0F)
                * (Math.max(0, Math.min(100, volumePercent)) / 100.0F);
    }
}
