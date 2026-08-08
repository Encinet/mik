package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmNetworkLatencyGuardTest {

    @Test
    void entryAllowsTheExactLimitAndRejectsAnythingHigher() {
        assertTrue(RhythmNetworkLatencyGuard.allowsEntry(
                RhythmNetworkLatencyGuard.MAXIMUM_PLAYABLE_RTT_MILLIS));
        assertFalse(RhythmNetworkLatencyGuard.allowsEntry(
                RhythmNetworkLatencyGuard.MAXIMUM_PLAYABLE_RTT_MILLIS + 1));
    }

    @Test
    void oneSpikeWarnsButDoesNotTerminateTheSession() {
        RhythmNetworkLatencyGuard guard = new RhythmNetworkLatencyGuard(0L);

        RhythmNetworkLatencyGuard.Update spike = guard.sample(420,
                RhythmNetworkLatencyGuard.SAMPLE_INTERVAL_NANOS);
        RhythmNetworkLatencyGuard.Update recovered = guard.sample(80,
                2L * RhythmNetworkLatencyGuard.SAMPLE_INTERVAL_NANOS);

        assertTrue(spike.warning());
        assertFalse(spike.excessive());
        assertEquals(RhythmNetworkLatencyGuard.State.HEALTHY,
                recovered.state());
        assertFalse(recovered.excessive());
    }

    @Test
    void threeConsecutiveOneSecondHighSamplesTerminateFairly() {
        RhythmNetworkLatencyGuard guard = new RhythmNetworkLatencyGuard(0L);

        assertTrue(guard.sample(360, 1_000_000_000L).warning());
        assertTrue(guard.sample(370, 2_000_000_000L).warning());
        RhythmNetworkLatencyGuard.Update result = guard.sample(380,
                3_000_000_000L);

        assertTrue(result.excessive());
        assertEquals(3, result.consecutiveHighSamples());
    }

    @Test
    void repeatedTicksCannotPretendToBeIndependentPingSamples() {
        RhythmNetworkLatencyGuard guard = new RhythmNetworkLatencyGuard(0L);
        for (int tick = 1; tick < 20; tick++) {
            assertFalse(guard.sample(500, tick * 50_000_000L).sampled());
        }

        assertTrue(guard.sample(500, 1_000_000_000L).warning());
    }

    @Test
    void samplingIntervalSurvivesNanoTimeSignedWrap() {
        RhythmNetworkLatencyGuard guard = new RhythmNetworkLatencyGuard(
                Long.MAX_VALUE - 500_000_000L);

        assertTrue(guard.sample(400,
                Long.MIN_VALUE + 600_000_000L).sampled());
    }
}
