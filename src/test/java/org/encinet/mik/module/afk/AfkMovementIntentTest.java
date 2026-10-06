package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkMovementIntentTest {

    private static final UUID WORLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void automaticAfkAcceptsTheFirstNewMovementGesture() {
        AfkActivityTracker tracker = tracker();
        tracker.suspendForAfk(100L, false);

        assertTrue(tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 101L));
        assertFalse(tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 102L));
    }

    @Test
    void heldInputAtManualEntryRequiresReleaseBeforeItCanExit() {
        AfkActivityTracker tracker = tracker();
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 10L);
        tracker.suspendForAfk(100L, true);

        assertFalse(tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 101L));
        assertFalse(tracker.recordMovementInput(false, WORLD_ID, 0.0D, 0.0D, 0.0D, 102L));
        assertTrue(tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 103L));
    }

    @Test
    void displacementWithoutPlayerInputDoesNotCreateActivity() {
        AfkActivityTracker tracker = tracker();

        assertFalse(tracker.recordMovement(WORLD_ID, 0.01D, 0.0D, 0.0D, 100L));
        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE,
                tracker.check(AfkPolicy.DEFAULT.idleTimeoutMillis()));
    }

    @Test
    void shortReleasedGesturesPreserveOnlyTheirActualTravel() {
        AfkActivityTracker tracker = tracker();
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        assertFalse(tracker.recordMovement(WORLD_ID, 7.5D, 0.0D, 0.0D, 1_000L));
        tracker.recordMovementInput(false, WORLD_ID, 7.5D, 0.0D, 0.0D, 1_500L);
        assertFalse(tracker.recordMovement(WORLD_ID, 100.0D, 0.0D, 0.0D, 2_000L));
        tracker.recordMovementInput(true, WORLD_ID, 100.0D, 0.0D, 0.0D, 2_500L);

        assertTrue(tracker.recordMovement(WORLD_ID, 100.5D, 0.0D, 0.0D, 3_000L));
    }

    @Test
    void oldSubThresholdTravelCannotBeBankedAcrossAnIdleGap() {
        AfkActivityTracker tracker = tracker();
        tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        tracker.recordMovement(WORLD_ID, 7.5D, 0.0D, 0.0D, 1_000L);
        tracker.recordMovementInput(false, WORLD_ID, 7.5D, 0.0D, 0.0D, 1_500L);
        tracker.recordMovementInput(true, WORLD_ID, 100.0D, 0.0D, 0.0D, 20_000L);

        assertFalse(tracker.recordMovement(WORLD_ID, 100.5D, 0.0D, 0.0D, 20_500L));
    }

    @Test
    void repeatedTappingJumpsDoesNotBecomePassiveAfk() {
        AfkPlayerSession session = new AfkPlayerSession(0L, WORLD_ID, 0.0D, 0.0D, 0.0D);
        AfkActivityTracker tracker = session.activity();
        long duration = 30L * 60L * 1_000L;
        for (long now = 0L; now < duration; now += 1_000L) {
            tracker.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, now);
            tracker.recordMovement(WORLD_ID, 0.0D, 0.5D, 0.0D, now + 100L);
            tracker.recordMovementInput(false, WORLD_ID, 0.0D, 0.5D, 0.0D, now + 200L);
            assertFalse(session.checkAutomaticAfk(now + 200L, true).shouldEnterAfk());
        }

        assertEquals(AfkActivityTracker.CheckResult.ACTIVE, tracker.check(duration));
        assertFalse(tracker.isAutomaticAfkCandidate(duration));
        assertFalse(tracker.isActivityEligible(duration));
    }

    private static AfkActivityTracker tracker() {
        return new AfkActivityTracker(0L, WORLD_ID, 0.0D, 0.0D, 0.0D);
    }
}
