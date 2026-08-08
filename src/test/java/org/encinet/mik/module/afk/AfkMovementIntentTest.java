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
        long version = tracker.activityVersion();

        assertFalse(tracker.recordMovement(WORLD_ID, 0.01D, 0.0D, 0.0D, 100L));
        assertEquals(version, tracker.activityVersion());
    }

    private static AfkActivityTracker tracker() {
        return new AfkActivityTracker(0L, WORLD_ID, 0.0D, 0.0D, 0.0D);
    }
}
