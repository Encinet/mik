package org.encinet.mik.module.music.jukebox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JukeboxAudioSynchronizerTest {

    @Test
    void preRollsOnlySeekableAudioJoiningAnActiveSong() {
        assertTrue(JukeboxAudioSynchronizer.requiresClientPreRoll(true, true));
        assertFalse(JukeboxAudioSynchronizer.requiresClientPreRoll(false, true));
        assertFalse(JukeboxAudioSynchronizer.requiresClientPreRoll(true, false));
        assertEquals(500L, JukeboxAudioSynchronizer.CLIENT_PRE_ROLL_MILLIS);
        assertEquals(1_500L, JukeboxAudioSynchronizer.preRollTargetPositionMillis(
                1_000L, 180_000L));
    }

    @Test
    void catchesUpAtTheFirstOutgoingFrameAfterSenderStartupDelay() {
        assertEquals(1_180L, JukeboxAudioSynchronizer.catchUpPositionMillis(
                200L, 1_180L, 180_000L));
    }

    @Test
    void keepsFramesThatAreAlreadyInsideTheSameFrameWindow() {
        assertEquals(-1L, JukeboxAudioSynchronizer.catchUpPositionMillis(
                1_161L, 1_180L, 180_000L));
        assertEquals(-1L, JukeboxAudioSynchronizer.catchUpPositionMillis(
                1_180L, 1_160L, 180_000L));
        assertEquals(1_180L, JukeboxAudioSynchronizer.catchUpPositionMillis(
                1_160L, 1_180L, 180_000L));
    }

    @Test
    void clampsCatchUpAtTheTrackEnd() {
        assertEquals(9_999L, JukeboxAudioSynchronizer.catchUpPositionMillis(
                1_000L, 12_000L, 10_000L));
    }

    @Test
    void discardsDecodedStartupFramesUntilTheyReachTheLiveGroupPosition() {
        assertTrue(JukeboxAudioSynchronizer.frameIsBehind(1_020L, 1_040L));
        assertFalse(JukeboxAudioSynchronizer.frameIsBehind(1_021L, 1_040L));
        assertFalse(JukeboxAudioSynchronizer.frameIsBehind(1_060L, 1_040L));
    }

    @Test
    void holdsFramesThatSeekAheadOfTheLeader() {
        assertTrue(JukeboxAudioSynchronizer.frameIsAhead(1_060L, 1_040L));
        assertFalse(JukeboxAudioSynchronizer.frameIsAhead(1_059L, 1_040L));
        assertFalse(JukeboxAudioSynchronizer.frameIsAhead(1_020L, 1_040L));
    }
}
