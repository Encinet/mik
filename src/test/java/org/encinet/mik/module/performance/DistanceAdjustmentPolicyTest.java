package org.encinet.mik.module.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceAdjustmentPolicyTest {

    @Test
    void afkFlappingProgressivelySlowsRecoveryButNeverDelaysReduction() {
        DistanceAdjustmentPolicy policy = new DistanceAdjustmentPolicy(12, 8, 2_000L, 2_000L, 2);
        assertDistance(policy.update(0L, 2, 2, true), 2, 2);
        assertDistance(policy.update(1L, 12, 8, true), 2, 2);
        assertDistance(policy.update(2_001L, 12, 8, true), 4, 4);
        assertDistance(policy.update(2_002L, 2, 2, true), 2, 2);
        policy.update(2_003L, 12, 8, true);
        assertDistance(policy.update(10_001L, 12, 8, true), 2, 2);
        assertDistance(policy.update(10_002L, 12, 8, true), 4, 4);
        // Backoff is now capped at 8x, and deliberate recovery steps don't increase it.
        assertDistance(policy.update(26_001L, 12, 8, true), 4, 4);
        assertDistance(policy.update(26_002L, 12, 8, true), 6, 6);
    }

    @Test
    void stablePeriodDecaysThePenaltyAndNoOpChecksDoNotExtendIt() {
        DistanceAdjustmentPolicy policy = new DistanceAdjustmentPolicy(12, 8, 2_000L, 0L, 2);
        policy.update(0L, 2, 2, true);
        policy.update(2_000L, 4, 4, true);
        policy.update(2_001L, 2, 2, true);
        for (long now = 3_000L; now < 602_001L; now += 1_000L) {
            policy.update(now, 2, 2, true);
        }
        assertDistance(policy.update(602_001L, 4, 4, true), 4, 4);
        assertDistance(policy.update(604_001L, 6, 6, true), 6, 6);
    }

    @Test
    void performanceRecoveryRequiresContinuousConfirmationAndMovesOneChunkAtATime() {
        DistanceAdjustmentPolicy policy = new DistanceAdjustmentPolicy(8, 6, 30_000L, 10_000L, 1);
        policy.update(0L, 12, 8, false);
        assertDistance(policy.update(9_999L, 12, 8, false), 8, 6);
        policy.update(10_000L, 8, 6, false);
        policy.update(11_000L, 12, 8, false);
        assertDistance(policy.update(20_999L, 12, 8, false), 8, 6);
        assertDistance(policy.update(21_000L, 12, 8, false), 9, 7);
        assertDistance(policy.update(50_999L, 12, 8, false), 9, 7);
        assertDistance(policy.update(51_000L, 12, 8, false), 10, 8);
    }

    @Test
    void emergencyReductionBypassesCooldownAndMixedTargetsNeverRaiseDistance() {
        DistanceAdjustmentPolicy policy = new DistanceAdjustmentPolicy(12, 8, 30_000L, 10_000L, 1);
        assertDistance(policy.update(0L, 10, 6, false), 10, 6);
        assertDistance(policy.update(1L, 8, 7, false), 10, 6);
        assertDistance(policy.update(2L, 5, 4, true), 5, 4);
        assertDistance(policy.update(30_002L, 4, 8, false), 4, 4);
    }

    @Test
    void playersDoNotShareAfkBackoff() {
        DistanceAdjustmentPolicy first = new DistanceAdjustmentPolicy(12, 8, 2_000L, 0L, 2);
        DistanceAdjustmentPolicy second = new DistanceAdjustmentPolicy(12, 8, 2_000L, 0L, 2);
        first.update(0L, 2, 2, true);
        first.update(2_000L, 12, 8, true);
        first.update(2_001L, 2, 2, true);
        second.update(2_001L, 2, 2, true);
        assertDistance(first.update(4_001L, 12, 8, true), 2, 2);
        assertDistance(second.update(4_001L, 12, 8, true), 4, 4);
    }

    private static void assertDistance(PlayerViewDistancePolicy.EffectiveDistances actual,
                                       int render, int simulation) {
        assertEquals(new PlayerViewDistancePolicy.EffectiveDistances(render, simulation), actual);
    }
}
