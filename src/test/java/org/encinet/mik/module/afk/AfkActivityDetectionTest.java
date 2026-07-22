package org.encinet.mik.module.afk;

import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkActivityDetectionTest {

    private static final UUID WORLD_ID = new UUID(0L, 1L);

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
                tracker.check(AfkActivityTracker.IDLE_TIMEOUT_MILLIS));
    }

    @Test
    void repeatedCallbacksWithinOneSecondCountAsOneAction() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordAction(1_000L);
        tracker.recordAction(1_500L);
        tracker.recordAction(1_999L);
        tracker.recordLightActivity(AfkActivityTracker.PASSIVE_TIMEOUT_MILLIS);

        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE,
                tracker.check(AfkActivityTracker.PASSIVE_TIMEOUT_MILLIS));
    }

    @Test
    void threeDistinctActionsRefreshPassiveActivity() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordAction(1_000L);
        tracker.recordAction(3_000L);
        assertFalse(tracker.recordAction(3_500L));
        assertTrue(tracker.recordAction(4_000L));

        tracker.recordLightActivity(4_000L + AfkActivityTracker.PASSIVE_TIMEOUT_MILLIS - 1L);
        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                tracker.check(4_000L + AfkActivityTracker.PASSIVE_TIMEOUT_MILLIS - 1L));
        tracker.recordLightActivity(4_000L + AfkActivityTracker.PASSIVE_TIMEOUT_MILLIS);
        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE,
                tracker.check(4_000L + AfkActivityTracker.PASSIVE_TIMEOUT_MILLIS));
    }

    @Test
    void actionThresholdUsesAnOverlappingSlidingWindow() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordAction(60_000L);
        tracker.recordAction(2 * 60_000L);
        tracker.recordAction(9 * 60_000L);
        tracker.recordAction(15 * 60_000L);
        assertTrue(tracker.recordAction(18 * 60_000L));

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
    void holdingJumpToFlyUpOnlyRefreshesActivityOnce() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        assertTrue(tracker.recordMovement(WORLD_ID, 0.0D, 8.0D, 0.0D, 10_000L));
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 100.0D, 0.0D, 5 * 60_000L);
        assertFalse(tracker.recordMovement(WORLD_ID, 0.0D, 100.0D, 0.0D, 5 * 60_000L));
        assertFalse(tracker.recordMovement(WORLD_ID, 0.0D, 200.0D, 0.0D, 9 * 60_000L));
        tracker.recordLightActivity(10_000L + AfkActivityTracker.PASSIVE_TIMEOUT_MILLIS);

        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE,
                tracker.check(10_000L + AfkActivityTracker.PASSIVE_TIMEOUT_MILLIS));
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
        assertFalse(tracker.hasActiveMovementGesture());
        assertFalse(tracker.recordMovement(WORLD_ID, 0.0D, 200.0D, 0.0D, 2_000L));

        tracker.recordMovementInput(false, WORLD_ID, 0.0D, 200.0D, 0.0D, 3_000L);
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 200.0D, 0.0D, 4_000L);
        assertTrue(tracker.hasActiveMovementGesture());
        assertTrue(tracker.recordMovement(WORLD_ID, 0.0D, 208.0D, 0.0D, 5_000L));
    }

    @Test
    void stationaryPlayerCanStartANewGestureAfterEnteringAfk() {
        AfkActivityTracker tracker = tracker(0L);
        tracker.suspendMovementGesture();

        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 1_000L);

        assertTrue(tracker.hasActiveMovementGesture());
    }

    @Test
    void externalMovementWithoutAMatchingGestureDoesNotRefreshActivity() {
        AfkActivityTracker tracker = tracker(0L);

        assertFalse(tracker.recordMovement(WORLD_ID, 100.0D, 0.0D, 0.0D, 2_000L));
        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE,
                tracker.check(AfkActivityTracker.IDLE_TIMEOUT_MILLIS));
    }

    private AfkActivityTracker tracker(long now) {
        return new AfkActivityTracker(now, WORLD_ID, 0.0D, 0.0D, 0.0D);
    }
}
