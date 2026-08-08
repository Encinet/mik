package org.encinet.mik.module.music.jukebox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JukeboxExperienceModeTest {

    @Test
    void cyclesIndependentlyFromPlaylistOrdering() {
        assertSame(JukeboxExperienceMode.RHYTHM,
                JukeboxExperienceMode.MUSIC.next());
        assertSame(JukeboxExperienceMode.MUSIC,
                JukeboxExperienceMode.RHYTHM.next());
    }

    @Test
    void onlyRhythmModeWaitsForTheCompleteChart() {
        assertFalse(JukeboxExperienceMode.MUSIC.waitsForRhythmAnalysis());
        assertTrue(JukeboxExperienceMode.RHYTHM.waitsForRhythmAnalysis());
    }

    @Test
    void onlyReadyRhythmPreparationCanLaunchGameplay() {
        assertFalse(JukeboxRhythmReadiness.UNAVAILABLE.playable());
        assertFalse(JukeboxRhythmReadiness.WAITING_FOR_PLAYER.playable());
        assertFalse(JukeboxRhythmReadiness.PREPARING.playable());
        assertTrue(JukeboxRhythmReadiness.READY.playable());
        assertFalse(JukeboxRhythmReadiness.NO_BEATS.playable());
    }
}
