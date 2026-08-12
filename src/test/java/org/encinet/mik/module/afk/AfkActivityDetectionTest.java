package org.encinet.mik.module.afk;

import org.bukkit.Input;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkActivityDetectionTest {

    private static final UUID WORLD_ID = new UUID(0L, 1L);
    private static final AfkPolicy POLICY = AfkPolicy.DEFAULT;
    private static final AfkActionEvidence.Type BLOCK = AfkActionEvidence.Type.BLOCK_CHANGE;
    private static final AfkActionEvidence.Type INVENTORY = AfkActionEvidence.Type.INVENTORY;

    @Test
    void yawOnlyRotationDoesNotCountAsActivity() {
        assertFalse(AfkModule.isMeaningfulRotation(0.0F, 20.0F, 90.0F, 20.0F));
    }

    @Test
    void pitchOnlyRotationDoesNotCountAsActivity() {
        assertFalse(AfkModule.isMeaningfulRotation(45.0F, 0.0F, 45.0F, 45.0F));
    }

    @Test
    void bothAxesMustExceedTheActivityThreshold() {
        assertTrue(AfkModule.isMeaningfulRotation(0.0F, 0.0F, 8.0F, 8.0F));
        assertFalse(AfkModule.isMeaningfulRotation(0.0F, 0.0F, 7.99F, 30.0F));
        assertFalse(AfkModule.isMeaningfulRotation(0.0F, 0.0F, 30.0F, 7.99F));
    }

    @Test
    void yawWrapAroundUsesTheShortestAngle() {
        assertTrue(AfkModule.isMeaningfulRotation(356.0F, 0.0F, 4.0F, 8.0F));
        assertFalse(AfkModule.isMeaningfulRotation(358.0F, 0.0F, 2.0F, 30.0F));
    }

    @Test
    void clickingAirIsNotASubstantialInteraction() {
        assertFalse(AfkModule.isSubstantialInteraction(Action.LEFT_CLICK_AIR));
        assertFalse(AfkModule.isSubstantialInteraction(Action.RIGHT_CLICK_AIR));
        assertFalse(AfkModule.isSubstantialInteraction(Action.PHYSICAL));
        assertTrue(AfkModule.isSubstantialInteraction(Action.LEFT_CLICK_BLOCK));
        assertTrue(AfkModule.isSubstantialInteraction(Action.RIGHT_CLICK_BLOCK));
    }

    @Test
    void emptyInventoryClicksAreNotSubstantialActions() {
        assertFalse(AfkModule.isSubstantialInventoryAction(InventoryAction.NOTHING));
        assertFalse(AfkModule.isSubstantialInventoryAction(InventoryAction.UNKNOWN));
        assertTrue(AfkModule.isSubstantialInventoryAction(InventoryAction.PICKUP_ALL));
        assertTrue(AfkModule.isSubstantialInventoryAction(InventoryAction.MOVE_TO_OTHER_INVENTORY));
    }

    @Test
    void chatAndCameraActivityDoNotPreventPassiveAfk() {
        AfkActivityTracker tracker = tracker(0L);
        for (long now = 60_000L; now <= 9 * 60_000L; now += 60_000L) {
            tracker.recordLightActivity(now);
        }

        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE,
                tracker.check(10 * 60_000L));
    }

    @Test
    void ordinarySilenceUsesTheShorterTimeout() {
        AfkActivityTracker tracker = tracker(0L);

        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE,
                tracker.check(POLICY.idleTimeoutMillis()));
    }

    @Test
    void repeatedCallbacksWithinOneSecondCountAsOneAction() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordAction(action(BLOCK, "a"), 1_000L);
        tracker.recordAction(action(BLOCK, "b"), 1_500L);
        tracker.recordAction(action(BLOCK, "c"), 1_999L);
        tracker.recordLightActivity(POLICY.passiveTimeoutMillis());

        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE,
                tracker.check(POLICY.passiveTimeoutMillis()));
    }

    @Test
    void threeDistinctActionsRefreshPassiveActivity() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordAction(action(BLOCK, "a"), 1_000L);
        tracker.recordAction(action(BLOCK, "b"), 3_000L);
        assertFalse(tracker.recordAction(action(BLOCK, "c"), 3_500L));
        assertTrue(tracker.recordAction(action(BLOCK, "c"), 4_000L));

        tracker.recordLightActivity(4_000L + POLICY.passiveTimeoutMillis() - 1L);
        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(4_000L + POLICY.passiveTimeoutMillis() - 1L));
        tracker.recordLightActivity(4_000L + POLICY.passiveTimeoutMillis());
        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE,
                tracker.check(4_000L + POLICY.passiveTimeoutMillis()));
    }

    @Test
    void actionThresholdUsesAnOverlappingSlidingWindow() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordAction(action(BLOCK, "a"), 60_000L);
        tracker.recordAction(action(BLOCK, "b"), 2 * 60_000L);
        tracker.recordAction(action(BLOCK, "c"), 9 * 60_000L);
        tracker.recordAction(action(BLOCK, "d"), 15 * 60_000L);
        assertTrue(tracker.recordAction(action(BLOCK, "e"), 18 * 60_000L));

        tracker.recordLightActivity(19 * 60_000L);
        assertEquals(AfkActivityTracker.CheckResult.ACTIVE, tracker.check(19 * 60_000L));
    }

    @Test
    void movementRequiresAnActiveGestureAndEnoughDistance() {
        AfkActivityTracker tracker = tracker(0L);

        assertFalse(tracker.recordMovement(WORLD_ID, 100.0D, 0.0D, 0.0D, 500L));
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 1_000L);
        assertFalse(tracker.recordMovement(WORLD_ID, 7.9D, 0.0D, 0.0D, 2_000L));
        assertTrue(tracker.recordMovement(WORLD_ID, 8.0D, 0.0D, 0.0D, 3_000L));
    }

    @Test
    void continuousFlightRefreshesActivityForEachTravelSegment() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        assertTrue(tracker.recordMovement(WORLD_ID, 0.0D, 8.0D, 0.0D, 10_000L));
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 100.0D, 0.0D, 5 * 60_000L);
        assertTrue(tracker.recordMovement(WORLD_ID, 0.0D, 100.0D, 0.0D, 5 * 60_000L));
        assertTrue(tracker.recordMovement(WORLD_ID, 0.0D, 200.0D, 0.0D, 9 * 60_000L));
        tracker.recordLightActivity(10_000L + POLICY.passiveTimeoutMillis());

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(10_000L + POLICY.passiveTimeoutMillis()));
    }

    @Test
    void fullyReleasedInputAllowsANewMovementGestureFromTheCurrentPosition() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        assertTrue(tracker.recordMovement(WORLD_ID, 8.0D, 0.0D, 0.0D, 1_000L));

        tracker.recordMovementInput(false, WORLD_ID, 20.0D, 0.0D, 0.0D, 2_000L);
        tracker.recordMovementInput(true, WORLD_ID, 20.0D, 0.0D, 0.0D, 2_500L);
        assertFalse(tracker.recordMovement(WORLD_ID, 27.9D, 0.0D, 0.0D, 3_000L));
        assertTrue(tracker.recordMovement(WORLD_ID, 28.0D, 0.0D, 0.0D, 4_000L));
    }

    @Test
    void heldMovementMustBeReleasedAfterEnteringAfk() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        tracker.suspendMovementGesture();

        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 100.0D, 0.0D, 1_000L);
        assertFalse(tracker.canMovementClearAfk());
        assertFalse(tracker.recordMovement(WORLD_ID, 0.0D, 200.0D, 0.0D, 2_000L));

        tracker.recordMovementInput(false, WORLD_ID, 0.0D, 200.0D, 0.0D, 3_000L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 200.0D, 0.0D, 4_000L);
        assertTrue(tracker.canMovementClearAfk());
        assertTrue(tracker.recordMovement(WORLD_ID, 0.0D, 208.0D, 0.0D, 5_000L));
    }

    @Test
    void stationaryPlayerCanStartANewGestureAfterEnteringAfk() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.suspendMovementGesture();

        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 1_000L);

        assertTrue(tracker.canMovementClearAfk());
    }

    @Test
    void currentReleasedInputOverridesAStaleMovementGestureWhenEnteringAfk() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        tracker.suspendForAfk(1_000L, false);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 2_000L);

        assertTrue(tracker.canMovementClearAfk());
    }

    @Test
    void currentlyHeldInputStillRequiresAReleaseAfterEnteringAfk() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.suspendForAfk(1_000L, true);

        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 2_000L);
        assertFalse(tracker.canMovementClearAfk());

        tracker.recordMovementInput(false, WORLD_ID, 0.0D, 0.0D, 0.0D, 3_000L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 4_000L);
        assertTrue(tracker.canMovementClearAfk());
    }

    @Test
    void afkExitPreservesPassiveInactivityUntilSubstantialMovement() {
        AfkActivityTracker tracker = tracker(0L);
        for (long now = 60_000L; now < POLICY.passiveTimeoutMillis(); now += 60_000L) {
            tracker.recordLightActivity(now);
        }

        long afkAt = POLICY.passiveTimeoutMillis();
        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE, tracker.check(afkAt));
        tracker.suspendForAfk(afkAt, false);

        long returnedAt = afkAt + 5L * 60L * 1_000L;
        tracker.resumeFromAfk(returnedAt, WORLD_ID, 0.0D, 0.0D, 0.0D);

        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE,
                tracker.check(returnedAt + 1_000L));

        tracker.recordMovementInput(
                true, WORLD_ID, 0.0D, 0.0D, 0.0D, returnedAt + 1_000L);
        assertTrue(tracker.recordMovement(
                WORLD_ID, POLICY.substantialMovementDistance(), 0.0D, 0.0D,
                returnedAt + 2_000L));
        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(returnedAt + 2_000L));
        assertFalse(tracker.isAutomaticAfkCandidate(returnedAt + 2_000L));
    }

    @Test
    void afkExitRetainsTheReleaseGateForHeldDirectionalInput() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.suspendForAfk(1_000L, true);

        tracker.resumeFromAfk(2_000L, WORLD_ID, 0.0D, 0.0D, 0.0D);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 2_000L);
        assertFalse(tracker.canMovementClearAfk());

        tracker.recordMovementInput(false, WORLD_ID, 0.0D, 0.0D, 0.0D, 3_000L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 4_000L);
        assertTrue(tracker.canMovementClearAfk());
    }

    @Test
    void sneakAndSprintAloneDoNotBlockTheMovementReleaseGate() {
        assertFalse(AfkModule.hasMovementInput(input(false, false, false, false, false, true, false)));
        assertFalse(AfkModule.hasMovementInput(input(false, false, false, false, false, false, true)));
        assertTrue(AfkModule.hasMovementInput(input(false, false, false, false, true, false, false)));
        assertTrue(AfkModule.hasMovementInput(input(true, false, false, false, false, true, true)));
    }

    @Test
    void externalMovementWithoutAMatchingGestureDoesNotRefreshActivity() {
        AfkActivityTracker tracker = tracker(0L);

        assertFalse(tracker.recordMovement(WORLD_ID, 100.0D, 0.0D, 0.0D, 2_000L));
        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE,
                tracker.check(POLICY.idleTimeoutMillis()));
    }

    @Test
    void randomCameraMovementDuringDirectedTravelIsNotAutomated() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        recordTravelSamples(tracker, AfkActivityDetectionTest::randomYaw);

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));
    }

    @Test
    void directedLongDistanceTravelIsNotDetectedAsAutomated() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        recordTravelSamples(tracker, index -> 15.0F);

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));
    }

    @Test
    void smoothCircularTravelIsNotDetectedAsRandomAutomation() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        recordTravelSamples(tracker, index -> index % 360);

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));
    }

    @Test
    void evenlyCyclingThroughMovementDirectionsWithAFixedViewIsDetectedAsAutomated() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        recordDirectionalTravelSamples(tracker, index -> (index - 1) / 5 % 4);

        assertEquals(AfkActivityTracker.CheckResult.ACTIVITY_REWARD_LOCKED,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));
    }

    @Test
    void lopsidedMovementDirectionDistributionIsNotDetectedAsAutomated() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        recordDirectionalTravelSamples(tracker, index -> {
            int position = (index - 1) % 10;
            return position < 7 ? 0 : position - 6;
        });

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));
    }

    @Test
    void longSquareRouteIsNotDetectedFromDirectionBalanceAlone() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        recordDirectionalTravelSamples(tracker, index -> (index - 1) / 150 % 4);

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));
    }

    @Test
    void verticalMovementDoesNotContributeToAutomationDetection() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        int samples = analysisSampleCount();
        for (int index = 1; index <= samples; index++) {
            long now = index * AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS;
            double y = index * 0.25D;
            tracker.recordObservation(WORLD_ID, 0.0D, y, 0.0D,
                    randomYaw(index), now);
            tracker.recordMovement(WORLD_ID, 0.0D, y, 0.0D, now);
        }

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));
    }

    @Test
    void movementAloneEventuallyStopsRewardEligibility() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        long beforeLimit = POLICY.movementOnlyRewardTimeoutMillis() - 1L;
        tracker.recordLightActivity(beforeLimit);
        assertTrue(tracker.isActivityEligible(beforeLimit));

        long atLimit = POLICY.movementOnlyRewardTimeoutMillis();
        tracker.recordLightActivity(atLimit);
        assertFalse(tracker.isActivityEligible(atLimit));

        recordIntentionalActions(tracker, atLimit + 1_500L);
        assertTrue(tracker.isActivityEligible(atLimit + 4_500L));
    }

    @Test
    void afkExitDoesNotResetMovementOnlyEligibilityAge() {
        AfkActivityTracker tracker = tracker(0L);
        long beforePause = 14L * 60L * 1_000L;
        tracker.recordLightActivity(beforePause);
        tracker.suspendForAfk(beforePause);

        long resumedAt = beforePause + 10L * 60L * 1_000L;
        tracker.resumeFromAfk(resumedAt, WORLD_ID, 0.0D, 0.0D, 0.0D);
        long oneMinuteLater = resumedAt + 60_000L;
        tracker.recordLightActivity(oneMinuteLater);

        assertFalse(tracker.isActivityEligible(oneMinuteLater));
    }

    @Test
    void partialAutomationUnlockKeepsTheTrackerSuspended() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        recordDirectionalTravelSamples(tracker, index -> (index - 1) / 5 % 4);
        assertEquals(AfkActivityTracker.CheckResult.ACTIVITY_REWARD_LOCKED,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));

        tracker.suspendForAfk(AfkBehaviorAnalyzer.ANALYSIS_MILLIS, false);
        tracker.recordAction(action(BLOCK, "unlock-a"),
                AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 1_500L);

        assertTrue(tracker.isSuspended());
        assertTrue(tracker.isAutomationRewardLocked());
    }

    @Test
    void completedAutomationUnlockMakesActivityEligibleAfterAfkExit() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        recordDirectionalTravelSamples(tracker, index -> (index - 1) / 5 % 4);
        long detectedAt = AfkBehaviorAnalyzer.ANALYSIS_MILLIS;
        assertEquals(AfkActivityTracker.CheckResult.ACTIVITY_REWARD_LOCKED, tracker.check(detectedAt));

        tracker.suspendForAfk(detectedAt, false);
        tracker.recordAction(action(BLOCK, "unlock-a"), detectedAt + 1_500L);
        tracker.recordAction(action(INVENTORY, "unlock-b"), detectedAt + 3_000L);
        assertTrue(tracker.recordAction(action(BLOCK, "unlock-c"), detectedAt + 4_500L));

        long returnedAt = detectedAt + 4_500L;
        tracker.resumeFromAfk(returnedAt, WORLD_ID, 0.0D, 0.0D, 0.0D);

        assertFalse(tracker.isAutomationRewardLocked());
        assertTrue(tracker.isActivityEligible(returnedAt));
        assertEquals(AfkActivityTracker.CheckResult.ACTIVE, tracker.check(returnedAt + 1_000L));
    }

    @Test
    void trustedGameplayActivityImmediatelyClearsAutomationRewardLock() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        recordDirectionalTravelSamples(tracker, index -> (index - 1) / 5 % 4);
        long detectedAt = AfkBehaviorAnalyzer.ANALYSIS_MILLIS;
        assertEquals(AfkActivityTracker.CheckResult.ACTIVITY_REWARD_LOCKED,
                tracker.check(detectedAt));

        long trustedAt = detectedAt + 1_000L;
        tracker.recordTrustedActivity(trustedAt);

        assertFalse(tracker.isAutomationRewardLocked());
        assertTrue(tracker.isActivityEligible(trustedAt));
        assertEquals(AfkActivityTracker.CheckResult.ACTIVE, tracker.check(trustedAt));
    }

    @Test
    void trustedGameplayActivityWhileAfkKeepsItsRealTimestampOnExit() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.suspendForAfk(1_000L, false);
        long trustedAt = 2_000L;
        tracker.recordTrustedActivity(trustedAt);
        tracker.resumeFromAfk(trustedAt, WORLD_ID, 0.0D, 0.0D, 0.0D);

        long rewardDeadline = trustedAt + POLICY.movementOnlyRewardTimeoutMillis();
        tracker.recordLightActivity(rewardDeadline - 1L);
        assertTrue(tracker.isActivityEligible(rewardDeadline - 1L));
        tracker.recordLightActivity(rewardDeadline);
        assertFalse(tracker.isActivityEligible(rewardDeadline));
    }

    @Test
    void commandStyleExitReleasesMovementButKeepsAutomationRewardLock() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        recordDirectionalTravelSamples(tracker, index -> (index - 1) / 5 % 4);
        long detectedAt = AfkBehaviorAnalyzer.ANALYSIS_MILLIS;
        assertEquals(AfkActivityTracker.CheckResult.ACTIVITY_REWARD_LOCKED, tracker.check(detectedAt));

        tracker.suspendForAfk(detectedAt, false);
        long returnedAt = detectedAt + 60_000L;
        tracker.resumeFromAfk(returnedAt, WORLD_ID, 300.0D, 0.0D, 0.0D);

        assertFalse(tracker.isSuspended());
        assertTrue(tracker.isAutomationRewardLocked());
        assertFalse(tracker.isActivityEligible(returnedAt));
        assertEquals(AfkActivityTracker.CheckResult.ACTIVE, tracker.check(returnedAt + 1_000L));

        tracker.recordMovementInput(true, WORLD_ID, 300.0D, 0.0D, 0.0D, returnedAt + 1_000L);
        assertTrue(tracker.canMovementClearAfk());

        tracker.recordAction(action(BLOCK, "unlock-a"), returnedAt + 1_500L);
        tracker.recordAction(action(INVENTORY, "unlock-b"), returnedAt + 3_000L);
        assertTrue(tracker.recordAction(action(BLOCK, "unlock-c"), returnedAt + 4_500L));
        assertTrue(tracker.isActivityEligible(returnedAt + 4_500L));
    }

    @Test
    void afkExitDiscardsSuspiciousBehaviorFromBeforeAfk() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        recordTravelSamples(tracker, AfkActivityDetectionTest::randomYaw);

        long afkAt = AfkBehaviorAnalyzer.ANALYSIS_MILLIS;
        tracker.suspendForAfk(afkAt, false);
        long returnedAt = afkAt + 60_000L;
        tracker.resumeFromAfk(returnedAt, WORLD_ID, 300.0D, 0.0D, 0.0D);

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE, tracker.check(returnedAt + 1_000L));
        assertFalse(tracker.isAutomationRewardLocked());
    }

    @Test
    void disconnectAfterAfkExitKeepsExpiredPassiveTimer() {
        AfkActivityTracker tracker = tracker(0L);
        for (long now = 60_000L; now < POLICY.passiveTimeoutMillis(); now += 60_000L) {
            tracker.recordLightActivity(now);
        }
        long afkAt = POLICY.passiveTimeoutMillis();
        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE, tracker.check(afkAt));

        tracker.suspendForAfk(afkAt, false);
        long disconnectedAt = afkAt + 60_000L;
        tracker.resumeFromAfk(disconnectedAt, WORLD_ID, 0.0D, 0.0D, 0.0D);
        tracker.suspendSession(disconnectedAt);

        long reconnectedAt = disconnectedAt + 60_000L;
        tracker.resumeSession(reconnectedAt, WORLD_ID, 0.0D, 0.0D, 0.0D);
        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE,
                tracker.check(reconnectedAt + 1_000L));
    }

    @Test
    void oneUniformWindowIsNotEnoughToDetectAutomation() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        recordTravelSamples(tracker, index -> index <= 600
                ? 15.0F
                : randomYaw(index));

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));
    }

    @Test
    void visitingEveryDirectionWithALopsidedDistributionIsNotAutomated() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        recordTravelSamples(tracker, index -> index % 5 == 0
                ? (float) (index % 360)
                : 15.0F);

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));
    }

    @Test
    void intentionalInteractionRestartsAutomationObservation() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        recordTravelSamples(tracker, AfkActivityDetectionTest::randomYaw);

        recordIntentionalActions(tracker, AfkBehaviorAnalyzer.ANALYSIS_MILLIS);

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 3_000L));
    }

    @Test
    void automatedMovementLocksRewardsWithoutBlockingMovement() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        recordDirectionalTravelSamples(tracker, index -> (index - 1) / 5 % 4);
        assertEquals(AfkActivityTracker.CheckResult.ACTIVITY_REWARD_LOCKED,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));

        tracker.suspendMovementGesture();
        tracker.recordMovementInput(false, WORLD_ID, 300.0D, 0.0D, 0.0D,
                AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 500L);
        tracker.recordMovementInput(true, WORLD_ID, 300.0D, 0.0D, 0.0D,
                AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 1_000L);

        assertTrue(tracker.canMovementClearAfk());
        tracker.recordAction(action(BLOCK, "unlock-a"), AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 1_500L);
        tracker.recordAction(action(BLOCK, "unlock-b"), AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 3_000L);
        assertTrue(tracker.isAutomationRewardLocked());
        tracker.recordAction(action(BLOCK, "unlock-c"), AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 4_500L);
        assertTrue(tracker.isAutomationRewardLocked());
        tracker.recordAction(action(INVENTORY, "unlock-d"), AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 6_000L);
        assertFalse(tracker.isAutomationRewardLocked());
        assertTrue(tracker.canMovementClearAfk());
    }

    @Test
    void automatedAfkRequiresThreeDifferentTargetsEvenAfterCooldown() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        recordDirectionalTravelSamples(tracker, index -> (index - 1) / 5 % 4);
        assertEquals(AfkActivityTracker.CheckResult.ACTIVITY_REWARD_LOCKED,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));

        long start = AfkBehaviorAnalyzer.ANALYSIS_MILLIS;
        tracker.recordAction(action(BLOCK, "same"), start + 1_500L);
        tracker.recordAction(action(INVENTORY, "same"),
                start + POLICY.actionTargetDeduplicationMillis() + 2_000L);
        tracker.recordAction(action(BLOCK, "different"),
                start + POLICY.actionTargetDeduplicationMillis() + 3_500L);

        assertTrue(tracker.isAutomationRewardLocked());
        tracker.recordAction(action(INVENTORY, "third"),
                start + POLICY.actionTargetDeduplicationMillis() + 5_000L);
        assertFalse(tracker.isAutomationRewardLocked());
    }

    @Test
    void actionsBeforeAutomationDetectionDoNotCountTowardUnlocking() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        int samples = (int) (AfkBehaviorAnalyzer.ANALYSIS_MILLIS
                / AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS);
        double x = 0.0D;
        double z = 0.0D;
        for (int index = 1; index <= samples; index++) {
            long now = index * AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS;
            switch ((index - 1) / 5 % 4) {
                case 0 -> x += 0.25D;
                case 1 -> z += 0.25D;
                case 2 -> x -= 0.25D;
                case 3 -> z -= 0.25D;
                default -> throw new IllegalStateException("unreachable direction");
            }
            tracker.recordObservation(WORLD_ID, x, 0.0D, z,
                    randomYaw(index), now);
            tracker.recordMovement(WORLD_ID, x, 0.0D, z, now);
            if (now == 5L * 60L * 1_000L || now == 9L * 60L * 1_000L) {
                tracker.recordAction(action(BLOCK, "before-" + now), now);
            }
        }
        assertEquals(AfkActivityTracker.CheckResult.ACTIVITY_REWARD_LOCKED,
                tracker.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));

        tracker.recordAction(action(BLOCK, "unlock-a"), AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 1_500L);
        tracker.recordAction(action(INVENTORY, "unlock-b"), AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 3_000L);
        assertTrue(tracker.isAutomationRewardLocked());
        tracker.recordAction(action(BLOCK, "unlock-c"), AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 4_500L);
        assertFalse(tracker.isAutomationRewardLocked());
    }

    @Test
    void repeatedActionsAgainstOneTargetAreDeduplicated() {
        AfkActivityTracker tracker = tracker(0L);

        assertFalse(tracker.recordAction(action(BLOCK, "same"), 1_000L));
        assertFalse(tracker.recordAction(action(BLOCK, "same"), 3_000L));
        assertFalse(tracker.recordAction(action(BLOCK, "same"), 5_000L));
        tracker.recordLightActivity(POLICY.passiveTimeoutMillis());

        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE,
                tracker.check(POLICY.passiveTimeoutMillis()));
    }

    @Test
    void changingActionTypeDoesNotMakeOneTargetDistinct() {
        AfkActivityTracker tracker = tracker(0L);

        assertFalse(tracker.recordAction(action(AfkActionEvidence.Type.BLOCK_INTERACTION, "block:a"), 1_000L));
        assertFalse(tracker.recordAction(action(AfkActionEvidence.Type.BLOCK_CHANGE, "block:a"), 3_000L));
        assertFalse(tracker.recordAction(action(INVENTORY, "inventory:b"), 5_000L));

        assertTrue(tracker.recordAction(action(BLOCK, "block:c"), 7_000L));
    }

    @Test
    void shortReconnectPausesInsteadOfResettingObservationHistory() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        recordTravelSamples(tracker, AfkActivityDetectionTest::randomYaw, 0L, 600);
        long disconnectAt = AfkBehaviorAnalyzer.WINDOW_MILLIS;
        tracker.suspendSession(disconnectAt);
        long reconnectAt = disconnectAt + 60_000L;
        tracker.resumeSession(reconnectAt, WORLD_ID, 150.0D, 0.0D, 0.0D);
        tracker.recordMovementInput(true, WORLD_ID, 150.0D, 0.0D, 0.0D, reconnectAt);
        recordTravelSamples(tracker, index -> randomYaw(index + 600), reconnectAt, 600);

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(reconnectAt + AfkBehaviorAnalyzer.WINDOW_MILLIS));
    }

    @Test
    void randomLookingWithoutMovementDoesNotTriggerAutomationDetection() {
        AfkBehaviorAnalyzer analyzer = new AfkBehaviorAnalyzer();
        int samples = (int) (AfkBehaviorAnalyzer.ANALYSIS_MILLIS
                / AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS);
        for (int index = 1; index <= samples; index++) {
            long now = index * AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS;
            analyzer.record(WORLD_ID, 0.0D, 0.0D, 0.0D,
                    randomYaw(index), false, now);
        }

        assertFalse(analyzer.isLikelyAutomated(AfkBehaviorAnalyzer.ANALYSIS_MILLIS, 0L));
    }

    private void recordTravelSamples(AfkActivityTracker tracker, YawSequence yaws) {
        recordTravelSamples(tracker, yaws, 0L, analysisSampleCount());
    }

    private void recordTravelSamples(
            AfkActivityTracker tracker,
            YawSequence yaws,
            long startAt,
            int samples
    ) {
        for (int index = 1; index <= samples; index++) {
            long now = startAt + index * AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS;
            double x = (double) now / AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS * 0.25D;
            tracker.recordObservation(WORLD_ID, x, 0.0D, 0.0D, yaws.yaw(index), now);
            tracker.recordMovement(WORLD_ID, x, 0.0D, 0.0D, now);
        }
    }

    private void recordDirectionalTravelSamples(
            AfkActivityTracker tracker,
            DirectionSequence directions
    ) {
        double x = 0.0D;
        double z = 0.0D;
        for (int index = 1; index <= analysisSampleCount(); index++) {
            switch (directions.direction(index)) {
                case 0 -> x += 0.25D;
                case 1 -> z += 0.25D;
                case 2 -> x -= 0.25D;
                case 3 -> z -= 0.25D;
                default -> throw new IllegalArgumentException("direction must be between 0 and 3");
            }
            long now = index * AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS;
            tracker.recordObservation(WORLD_ID, x, 0.0D, z, 15.0F, now);
            tracker.recordMovement(WORLD_ID, x, 0.0D, z, now);
        }
    }

    private void recordIntentionalActions(AfkActivityTracker tracker, long startAt) {
        tracker.recordAction(action(BLOCK, "a-" + startAt), startAt);
        tracker.recordAction(action(BLOCK, "b-" + startAt), startAt + 1_500L);
        tracker.recordAction(action(BLOCK, "c-" + startAt), startAt + 3_000L);
    }

    private static AfkActionEvidence action(AfkActionEvidence.Type type, String target) {
        return new AfkActionEvidence(type, target);
    }

    private static Input input(
            boolean forward,
            boolean backward,
            boolean left,
            boolean right,
            boolean jump,
            boolean sneak,
            boolean sprint
    ) {
        return new Input() {
            @Override
            public boolean isForward() {
                return forward;
            }

            @Override
            public boolean isBackward() {
                return backward;
            }

            @Override
            public boolean isLeft() {
                return left;
            }

            @Override
            public boolean isRight() {
                return right;
            }

            @Override
            public boolean isJump() {
                return jump;
            }

            @Override
            public boolean isSneak() {
                return sneak;
            }

            @Override
            public boolean isSprint() {
                return sprint;
            }
        };
    }

    private static int analysisSampleCount() {
        return (int) (AfkBehaviorAnalyzer.ANALYSIS_MILLIS
                / AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS);
    }

    private static float randomYaw(int index) {
        long value = index * 0x9E3779B97F4A7C15L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (float) ((value >>> 11) * 0x1.0p-53 * 360.0D);
    }

    @FunctionalInterface
    private interface YawSequence {
        float yaw(int index);
    }

    @FunctionalInterface
    private interface DirectionSequence {
        int direction(int index);
    }

    private AfkActivityTracker tracker(long now) {
        return new AfkActivityTracker(now, WORLD_ID, 0.0D, 0.0D, 0.0D);
    }
}
