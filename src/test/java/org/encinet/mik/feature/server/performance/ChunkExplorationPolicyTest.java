package org.encinet.mik.feature.server.performance;

import org.encinet.mik.feature.server.performance.ChunkExplorationPolicy.Limit;
import org.encinet.mik.feature.server.performance.ChunkExplorationPolicy.Observation;
import org.encinet.mik.feature.server.performance.ChunkExplorationPolicy.Resources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkExplorationPolicyTest {
    @Test
    void coverageCountsNewStripsOnceAndBoundsHugeJumps() {
        assertEquals(0.0D, ChunkExplorationPolicy.newCoverage(16, 0, 0));
        assertEquals(33.0D, ChunkExplorationPolicy.newCoverage(16, 1, 0));
        assertEquals(65.0D, ChunkExplorationPolicy.newCoverage(16, -1, 1));
        assertEquals(1089.0D, ChunkExplorationPolicy.newCoverage(16, 100, 0));
        assertEquals(1089.0D, ChunkExplorationPolicy.newCoverage(16, Integer.MIN_VALUE, 0));
    }

    @Test
    void healthyResourcesNeverLimitEvenExtremeExplorationDemandOrManyExplorers() {
        Observation high = new Observation(200.0D, 1000.0D, 1000.0D, true, 1.0f);
        for (int explorers : new int[]{1, 16}) {
            ChunkExplorationPolicy policy = new ChunkExplorationPolicy();
            assertEquals(ChunkExplorationPolicy.OPEN,
                    feed(policy, high, new Resources(0.20D, 20.0D, explorers, explorers), 180));
        }
    }

    @Test
    void stayingBelowRealPressureThresholdsDoesNotCreateLimits() {
        Observation high = new Observation(100.0D, 300.0D, 300.0D, true, 1.0f);
        assertEquals(ChunkExplorationPolicy.OPEN, feed(new ChunkExplorationPolicy(), high,
                new Resources(0.84D, 44.0D, 4, 4), 60));
    }

    @Test
    void fastFlightInLoadedTerrainWithLittleDeliveryNeedsNoRestrictionEvenDuringCpuPressure() {
        ChunkExplorationPolicy policy = new ChunkExplorationPolicy();
        Limit limit = feed(policy, new Observation(100.0D, 4.0D, 0.0D, true, 1.0f),
                new Resources(0.20D, 60.0D, 1, 0), 60);
        assertEquals(ChunkExplorationPolicy.OPEN, limit);
    }

    @Test
    void networkDeliveryPressureCutsSendAndFlightButKeepsServerLoadDistance() {
        ChunkExplorationPolicy policy = new ChunkExplorationPolicy();
        Limit limit = feed(policy, new Observation(100.0D, 200.0D, 0.0D, true, 1.0f),
                new Resources(0.90D, 10.0D, 1, 0), 5);
        assertTrue(limit.sendRatio() < 1.0D);
        assertEquals(1.0D, limit.loadRatio());
        assertTrue(limit.flightCeiling() < 1.0f);
    }

    @Test
    void unloadedFrontierAndCpuPressureCanActWhenBandwidthIsHealthy() {
        ChunkExplorationPolicy policy = new ChunkExplorationPolicy();
        Limit limit = feed(policy, new Observation(100.0D, 4.0D, 100.0D, true, 0.8f),
                new Resources(0.20D, 50.0D, 0, 1), 5);
        assertEquals(1.0D, limit.sendRatio());
        assertTrue(limit.loadRatio() < 1.0D);
        assertTrue(limit.flightCeiling() < 0.8f);
    }

    @Test
    void pressureOnOneResourceCannotActivateLimitsForTheOtherResource() {
        assertEquals(ChunkExplorationPolicy.OPEN, feed(new ChunkExplorationPolicy(),
                new Observation(100.0D, 1000.0D, 0.0D, true, 1.0f),
                new Resources(0.2D, 60.0D, 1, 0), 60));
        assertEquals(ChunkExplorationPolicy.OPEN, feed(new ChunkExplorationPolicy(),
                new Observation(100.0D, 0.0D, 1000.0D, true, 1.0f),
                new Resources(0.99D, 20.0D, 0, 1), 60));
    }

    @Test
    void ordinaryPressureRequiresFiveContinuousSeconds() {
        ChunkExplorationPolicy policy = new ChunkExplorationPolicy();
        Observation high = new Observation(100.0D, 300.0D, 300.0D, true, 1.0f);
        Resources busy = new Resources(0.90D, 50.0D, 1, 1);
        assertEquals(ChunkExplorationPolicy.OPEN, feed(policy, high, busy, 4));
        assertTrue(policy.update(high, 1.0D, busy).throttled());
    }

    @Test
    void separatedPressureBurstsCannotAccumulateIntoARestriction() {
        ChunkExplorationPolicy policy = new ChunkExplorationPolicy();
        Observation high = new Observation(100.0D, 300.0D, 300.0D, true, 1.0f);
        for (int burst = 0; burst < 20; burst++) {
            assertEquals(ChunkExplorationPolicy.OPEN, feed(policy, high, new Resources(0.9D, 50.0D, 1, 1), 4));
            assertEquals(ChunkExplorationPolicy.OPEN, policy.update(high, 1.0D, new Resources(0.2D, 20.0D, 1, 1)));
        }
    }

    @Test
    void walkingAndOneOffBurstsCannotEnterExplorationLimits() {
        ChunkExplorationPolicy policy = new ChunkExplorationPolicy();
        Resources resources = new Resources(0.95D, 50.0D, 1, 1);
        assertFalse(feed(policy, new Observation(6.0D, 200.0D, 100.0D, false, 0.1f), resources, 60).throttled());
        policy = new ChunkExplorationPolicy();
        assertFalse(policy.update(new Observation(100.0D, 300.0D, 100.0D, true, 1.0f), 1.0D, resources).throttled());
        assertFalse(feed(policy, new Observation(0.0D, 0.0D, 0.0D, false, 1.0f), resources, 30).throttled());
    }

    @Test
    void independentResourceSharesDoNotChargeSendOnlyPlayersAgainstTheGenerationPool() {
        Observation load = new Observation(50.0D, 10.0D, 10.0D, true, 0.5f);
        Limit single = feed(new ChunkExplorationPolicy(), load, new Resources(0.2D, 50.0D, 1, 1), 5);
        Limit manySenders = feed(new ChunkExplorationPolicy(), load, new Resources(0.2D, 50.0D, 4, 1), 5);
        assertEquals(single.loadRatio(), manySenders.loadRatio());
        assertEquals(1.0D, single.loadRatio());
        Limit manyLoaders = feed(new ChunkExplorationPolicy(), load, new Resources(0.2D, 50.0D, 1, 4), 5);
        assertTrue(manyLoaders.loadRatio() < single.loadRatio());
    }

    @Test
    void recoveryIsGradualAndTemporaryLimitsEventuallyRelease() {
        ChunkExplorationPolicy policy = new ChunkExplorationPolicy();
        Limit last = feed(policy, new Observation(100.0D, 300.0D, 300.0D, true, 1.0f),
                new Resources(0.90D, 50.0D, 1, 1), 15);
        assertTrue(last.throttled());
        for (int second = 0; second < 500; second++) {
            Limit next = policy.update(new Observation(0.0D, 0.0D, 0.0D, false, last.flightCeiling()),
                    1.0D, new Resources(0.1D, 10.0D, 0, 0));
            assertTrue(next.sendRatio() - last.sendRatio() <= 0.10D + 1.0E-10D);
            assertTrue(next.loadRatio() - last.loadRatio() <= 0.10D + 1.0E-10D);
            last = next;
        }
        assertFalse(last.throttled());
    }

    @Test
    void continuingToExploreCannotBlockRecoveryOnceResourcesAreHealthy() {
        ChunkExplorationPolicy policy = new ChunkExplorationPolicy();
        Observation high = new Observation(100.0D, 300.0D, 300.0D, true, 1.0f);
        Limit limited = feed(policy, high, new Resources(0.9D, 50.0D, 1, 1), 5);
        assertTrue(limited.throttled());
        Limit probe = feed(policy, high, new Resources(0.2D, 20.0D, 1, 1), 5);
        assertTrue(probe.flightCeiling() > limited.flightCeiling());
        assertTrue(probe.sendRatio() > limited.sendRatio());
        assertEquals(ChunkExplorationPolicy.OPEN, feed(policy, high, new Resources(0.2D, 20.0D, 1, 1), 200));
    }

    @Test
    void failedRecoveryReturnsToPreviousLimitsAndSamplingGapsGiveNoEvidence() {
        ChunkExplorationPolicy policy = new ChunkExplorationPolicy();
        Observation high = new Observation(100.0D, 200.0D, 200.0D, true, 1.0f);
        Resources busy = new Resources(0.90D, 50.0D, 1, 1);
        Limit stable = feed(policy, high, busy, 5);
        assertEquals(stable, policy.update(new Observation(0, 0, 0, false, 1.0f), 60.0D, busy));
        for (int second = 0; second < 100; second++) {
            Limit probe = policy.update(new Observation(0, 0, 0, false, stable.flightCeiling()),
                    1.0D, new Resources(0.1D, 10.0D, 0, 0));
            if (!probe.equals(stable)) break;
        }
        assertEquals(stable, feed(policy, high, busy, 4));
    }

    private static Limit feed(ChunkExplorationPolicy policy, Observation observation, Resources resources, int seconds) {
        Limit limit = policy.limit();
        for (int second = 0; second < seconds; second++) limit = policy.update(observation, 1.0D, resources);
        return limit;
    }
}
