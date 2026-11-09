package org.encinet.mik.feature.entertainment.music.disc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicDiscKeysTest {

    @Test
    void usesOnlyCurrentMikTrackKeys() {
        assertEquals("mik", MusicDiscKeys.TRACK.getNamespace());
        assertEquals("music_v2_track", MusicDiscKeys.TRACK.getKey());
        assertEquals("mik", MusicDiscKeys.TRACK_DATA.getNamespace());
        assertEquals("music_v2_data", MusicDiscKeys.TRACK_DATA.getKey());
        assertEquals("mik", MusicDiscKeys.TRACK_SIGNATURE.getNamespace());
        assertEquals("music_v2_signature", MusicDiscKeys.TRACK_SIGNATURE.getKey());
    }
}
