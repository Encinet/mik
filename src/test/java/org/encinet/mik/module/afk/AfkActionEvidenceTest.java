package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkActionEvidenceTest {

    private static final UUID WORLD = new UUID(0L, 1L);
    private static final AfkPolicy POLICY = AfkPolicy.DEFAULT;

    @Test
    void repeatedClicksAfterTheTargetCooldownCannotRenewActivityForever() {
        AfkActivityTracker tracker = tracker();
        long duration = 60L * 60L * 1_000L;
        for (long now = 31_000L; now <= duration; now += 31_000L) {
            assertFalse(tracker.recordAction(click("same-block"), now));
        }

        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE, tracker.check(duration));
        assertFalse(tracker.isActivityEligible(duration));
    }

    @Test
    void distinctClicksCanEstablishActivityButCannotClaimAcceptedOutcomes() {
        AfkActivityTracker tracker = tracker();
        assertFalse(tracker.recordAction(click("first"), 1_000L));
        assertFalse(tracker.recordAction(click("second"), 2_000L));
        assertTrue(tracker.recordAction(click("third"), 3_000L));

        tracker.recordLightActivity(POLICY.passiveTimeoutMillis());
        assertEquals(AfkActivityTracker.CheckResult.ACTIVE, tracker.check(POLICY.passiveTimeoutMillis()));
        tracker.recordLightActivity(POLICY.movementOnlyRewardTimeoutMillis());
        assertFalse(tracker.isActivityEligible(POLICY.movementOnlyRewardTimeoutMillis()));
    }

    @Test
    void acceptedRepeatedOutcomesRemainValidForStationaryGameplay() {
        AfkActivityTracker tracker = tracker();
        long duration = 60L * 60L * 1_000L;
        for (long now = 31_000L; now <= duration; now += 31_000L) {
            tracker.recordAction(new AfkActionEvidence(AfkActionEvidence.Type.COMBAT, "same-mob"), now);
        }

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE, tracker.check(duration));
        assertTrue(tracker.isActivityEligible(duration));
    }

    @Test
    void mixedWeakEvidenceCannotRenewIntentionalActivity() {
        AfkActivityTracker tracker = tracker();
        tracker.recordAction(outcome("first"), 1_000L);
        tracker.recordAction(click("second"), 2_000L);
        assertTrue(tracker.recordAction(click("third"), 3_000L));

        tracker.recordLightActivity(POLICY.movementOnlyRewardTimeoutMillis());
        assertFalse(tracker.isActivityEligible(POLICY.movementOnlyRewardTimeoutMillis()));
    }

    @Test
    void weakBatchesCannotDiscardStillUncreditedBuildingOutcomes() {
        AfkActivityTracker tracker = tracker();
        for (int batch = 0; batch < 3; batch++) {
            long now = POLICY.movementOnlyRewardTimeoutMillis() + batch * 4_000L;
            tracker.recordAction(outcome("built:" + batch), now);
            tracker.recordAction(click("clicked:" + batch + ":first"), now + 1_000L);
            tracker.recordAction(click("clicked:" + batch + ":second"), now + 2_000L);
        }

        assertTrue(tracker.isActivityEligible(POLICY.movementOnlyRewardTimeoutMillis() + 10_000L));
    }

    @Test
    void successfulBatchKeepsItsTargetDeduplicationHistory() {
        AfkActivityTracker tracker = tracker();
        tracker.recordAction(outcome("first"), 1_000L);
        tracker.recordAction(outcome("second"), 2_000L);
        assertTrue(tracker.recordAction(outcome("third"), 3_000L));
        assertFalse(tracker.recordAction(outcome("first"), 4_000L));
        assertFalse(tracker.recordAction(outcome("second"), 5_000L));
        assertFalse(tracker.recordAction(outcome("third"), 6_000L));

        tracker.recordLightActivity(3_000L + POLICY.passiveTimeoutMillis());
        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE,
                tracker.check(3_000L + POLICY.passiveTimeoutMillis()));
    }

    @Test
    void reconnectPreservesBatchConsumptionAndDeduplicationAge() {
        AfkActivityTracker tracker = tracker();
        tracker.recordAction(outcome("first"), 1_000L);
        tracker.recordAction(outcome("second"), 2_000L);
        tracker.recordAction(outcome("third"), 3_000L);
        tracker.suspendSession(4_000L);
        tracker.resumeSession(104_000L, WORLD, 0.0D, 0.0D, 0.0D);

        assertFalse(tracker.recordAction(outcome("first"), 105_000L));
        assertFalse(tracker.recordAction(outcome("second"), 106_000L));
        assertFalse(tracker.recordAction(outcome("third"), 107_000L));
        assertFalse(tracker.recordAction(outcome("fourth"), 108_000L));
    }

    @Test
    void teleportAndWorldChangeCannotCountAsSubstantialMovement() {
        AfkActivityTracker tracker = tracker();
        tracker.recordMovementInput(true, WORLD, 0.0D, 0.0D, 0.0D, 0L);
        tracker.rebasePosition(WORLD, 100_000.0D, 0.0D, 0.0D);
        assertFalse(tracker.recordMovement(WORLD, 100_000.5D, 0.0D, 0.0D, 1_000L));
        assertFalse(tracker.recordMovement(new UUID(0L, 2L), 1_000_000.0D, 0.0D, 0.0D, 2_000L));
        tracker.recordLightActivity(POLICY.passiveTimeoutMillis());

        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE, tracker.check(POLICY.passiveTimeoutMillis()));
    }

    @Test
    void nonFiniteDisplacementCannotPoisonTheNextValidMovement() {
        AfkActivityTracker tracker = tracker();
        tracker.recordMovementInput(true, WORLD, 0.0D, 0.0D, 0.0D, 0L);
        assertFalse(tracker.recordMovement(WORLD, Double.NaN, 0.0D, 0.0D, 1_000L));
        assertTrue(tracker.recordMovement(WORLD, 8.0D, 0.0D, 0.0D, 2_000L));
    }

    @Test
    void saturatedHistoryCannotBeEvictedToReplayTargetsUnderCustomPolicy() {
        AfkPolicy policy = new AfkPolicy(POLICY.idleTimeoutMillis(), POLICY.passiveTimeoutMillis(),
                POLICY.substantialMovementDistance(), 0L, POLICY.actionTargetDeduplicationMillis(),
                POLICY.movementOnlyRewardTimeoutMillis(), POLICY.requiredActions(),
                POLICY.requiredUnlockActionTypes(), POLICY.automaticEntryGraceMillis(),
                POLICY.automaticReentryCooldownMillis());
        AfkActivityTracker tracker = new AfkActivityTracker(policy, 0L, WORLD, 0.0D, 0.0D, 0.0D);
        for (int index = 0; index < 129; index++) {
            tracker.recordAction(outcome("target:" + index), 1_000L);
        }

        assertFalse(tracker.recordAction(outcome("target:0"), 2_000L));
        assertFalse(tracker.recordAction(outcome("target:1"), 2_000L));
        assertFalse(tracker.recordAction(outcome("target:2"), 2_000L));
        assertTrue(tracker.recordAction(outcome("after-expiry"), 31_000L));
    }

    private static AfkActivityTracker tracker() {
        return new AfkActivityTracker(0L, WORLD, 0.0D, 0.0D, 0.0D);
    }

    private static AfkActionEvidence click(String target) {
        return new AfkActionEvidence(AfkActionEvidence.Type.BLOCK_INTERACTION, target);
    }

    private static AfkActionEvidence outcome(String target) {
        return new AfkActionEvidence(AfkActionEvidence.Type.BLOCK_CHANGE, target);
    }
}
