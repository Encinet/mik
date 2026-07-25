package org.encinet.mik.module.music.disc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicDiscKeysTest {

    @Test
    void usesOnlyCurrentMikTrackKeys() {
        assertEquals("mik", MusicDiscKeys.TRACK.getNamespace());
        assertEquals("music_track", MusicDiscKeys.TRACK.getKey());
        assertEquals("mik", MusicDiscKeys.TRACK_DATA.getNamespace());
        assertEquals("music_track_data", MusicDiscKeys.TRACK_DATA.getKey());
        assertEquals("mik", MusicDiscKeys.TRACK_SIGNATURE.getNamespace());
        assertEquals("music_track_signature", MusicDiscKeys.TRACK_SIGNATURE.getKey());
        assertEquals("mik", MusicDiscKeys.INTERNAL.getNamespace());
        assertEquals("music_internal", MusicDiscKeys.INTERNAL.getKey());
    }
}
