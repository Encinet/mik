package org.encinet.mik.module.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerViewDistancePolicyTest {

    @Test
    void preservesPapersClientBoundaryChunk() {
        assertEquals(3, PlayerViewDistancePolicy.clientChunkLimit(2));
        assertEquals(9, PlayerViewDistancePolicy.clientChunkLimit(8));
        assertEquals(32, PlayerViewDistancePolicy.clientChunkLimit(31));
        assertEquals(32, PlayerViewDistancePolicy.clientChunkLimit(32));
    }

    @Test
    void limitsPerformanceDistancesToWhatTheClientCanUse() {
        PlayerViewDistancePolicy.EffectiveDistances distances =
                PlayerViewDistancePolicy.performanceDistances(20, 12, 8);

        assertEquals(9, distances.viewDistance());
        assertEquals(9, distances.simulationDistance());
    }

    @Test
    void keepsLowerPerformanceLimitsUnchanged() {
        PlayerViewDistancePolicy.EffectiveDistances distances =
                PlayerViewDistancePolicy.performanceDistances(6, 4, 16);

        assertEquals(6, distances.viewDistance());
        assertEquals(4, distances.simulationDistance());
    }

    @Test
    void changesEachPerformanceDistanceIndependently() {
        PlayerViewDistancePolicy.DistanceChanges changes =
                PlayerViewDistancePolicy.changes(8, 6, 8, 4);

        assertFalse(changes.viewDistanceChanged());
        assertTrue(changes.simulationDistanceChanged());
    }

    @Test
    void clientBoundSendDistanceDoesNotInstallARedundantOverride() {
        int targetDistance = PlayerViewDistancePolicy.sendDistance(12, 8);
        NetworkEgressModule.SendDistanceUpdate update = NetworkEgressModule.sendDistanceUpdate(
                9,
                9,
                targetDistance,
                null,
                true);

        assertEquals(9, targetDistance);
        assertFalse(update.apply());
        assertEquals(9, update.distance());
        assertNull(update.override());
    }

    @Test
    void remembersWhenTheSendDistanceOriginallyInheritedTheWorldDefault() {
        NetworkEgressModule.SendDistanceUpdate update = NetworkEgressModule.sendDistanceUpdate(
                16,
                16,
                8,
                null,
                true);

        assertTrue(update.apply());
        assertEquals(8, update.distance());
        assertEquals(new NetworkEgressModule.SendDistanceOverride(-1, 8), update.override());
    }

    @Test
    void updatesOwnedSendOverrideWhenTheClientLimitChanges() {
        NetworkEgressModule.SendDistanceOverride currentOverride =
                new NetworkEgressModule.SendDistanceOverride(16, 8);

        NetworkEgressModule.SendDistanceUpdate update = NetworkEgressModule.sendDistanceUpdate(
                7,
                16,
                7,
                currentOverride);

        assertTrue(update.apply());
        assertEquals(7, update.distance());
        assertEquals(new NetworkEgressModule.SendDistanceOverride(16, 7), update.override());
    }

    @Test
    void doesNotRewriteAnUnchangedOverrideHiddenByALowerViewLimit() {
        NetworkEgressModule.SendDistanceOverride currentOverride =
                new NetworkEgressModule.SendDistanceOverride(16, 8);

        NetworkEgressModule.SendDistanceUpdate update = NetworkEgressModule.sendDistanceUpdate(
                7,
                16,
                8,
                currentOverride);

        assertFalse(update.apply());
        assertEquals(7, update.distance());
        assertEquals(currentOverride, update.override());
    }
}
