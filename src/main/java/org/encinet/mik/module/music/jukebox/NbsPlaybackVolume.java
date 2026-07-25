package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.nbs.NbsNote;

/** Converts the two NBS volume components to Bukkit's note-block sound volume. */
final class NbsPlaybackVolume {

    private static final float MAX_SOUND_VOLUME = 4.0F;

    private NbsPlaybackVolume() {
    }

    static float volume(NbsNote note) {
        return MAX_SOUND_VOLUME
                * (note.velocity() / 100.0F)
                * (note.layerVolume() / 100.0F);
    }
}
