package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.nbs.NbsNote;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbsPlaybackVolumeTest {

    @Test
    void combinesNoteVelocityAndLayerVolumeLinearly() {
        assertEquals(1.0F, volume(100, 100));
        assertEquals(0.4F, volume(80, 50), 0.000_001F);
    }

    @Test
    void keepsVeryQuietNotesNonZeroWithoutRaisingTheirVolume() {
        float volume = volume(1, 1);

        assertEquals(0.0001F, volume, 0.000_000_1F);
        assertTrue(volume > 0.0F);
    }

    @Test
    void eitherZeroVolumeComponentMakesTheNoteSilent() {
        assertEquals(0.0F, volume(0, 100));
        assertEquals(0.0F, volume(100, 0));
        assertEquals(0.0F, volume(0, 0));
    }

    @Test
    void appliesTheJukeboxVolumeLinearly() {
        NbsNote note = new NbsNote(0, 0, 45, 100, 100, 0, 0);

        assertEquals(1.0F, NbsPlaybackVolume.volume(note, 100));
        assertEquals(0.5F, NbsPlaybackVolume.volume(note, 50));
        assertEquals(0.0F, NbsPlaybackVolume.volume(note, 0));
    }

    private static float volume(int velocity, int layerVolume) {
        return NbsPlaybackVolume.volume(
                new NbsNote(0, 0, 45, velocity, layerVolume, 0, 0), 100);
    }
}
