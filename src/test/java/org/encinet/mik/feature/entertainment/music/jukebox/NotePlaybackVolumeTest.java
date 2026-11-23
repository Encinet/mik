package org.encinet.mik.feature.entertainment.music.jukebox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotePlaybackVolumeTest {
    @Test
    void usesNormalizedGainWithoutRoundingQuietNotesOrBoostingVolume() {
        assertEquals(1.0F, NotePlaybackVolume.volume(10_000, 100));
        assertEquals(0.4F, NotePlaybackVolume.volume(4_000, 100), 0.000_001F);
        assertEquals(0.0001F, NotePlaybackVolume.volume(1, 100), 0.000_000_1F);
        assertEquals(0.0F, NotePlaybackVolume.volume(0, 100));
        assertEquals(0.5F, NotePlaybackVolume.volume(10_000, 50));
        assertEquals(0.0F, NotePlaybackVolume.volume(10_000, 0));
    }
}
