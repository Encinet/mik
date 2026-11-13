package org.encinet.mik.feature.player.afk;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AfkIdleInputTest {
    @Test
    void idleExemptionRequiresTheWholeIdleTimeoutSinceTheLatestInputEvenWhileAfk() {
        var tracker = new AfkActivityTracker(0, UUID.randomUUID(), 0, 64, 0);
        long timeout = AfkPolicy.DEFAULT.idleTimeoutMillis();
        assertFalse(tracker.isIdle(timeout - 1));
        assertTrue(tracker.isIdle(timeout));
        tracker.suspendForAfk(timeout, false);
        tracker.recordObservedInput(timeout + 1);
        assertFalse(tracker.isIdle(2 * timeout));
        assertTrue(tracker.isIdle(2 * timeout + 1));
        tracker.recordLightActivity(2 * timeout + 2);
        assertFalse(tracker.isIdle(2 * timeout + 2));
        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE, tracker.check(2 * timeout + 2));
        assertFalse(tracker.isActivityEligible(2 * timeout + 2));
    }
}
