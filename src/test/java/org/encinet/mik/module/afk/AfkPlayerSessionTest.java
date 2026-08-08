package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkPlayerSessionTest {

    private static final UUID WORLD_ID = new UUID(0L, 1L);

    @Test
    void stationaryPlayerEntersAfkNearTheIdleDeadlineInsteadOfRemainingOnlineForHalfAnHour() {
        AfkPlayerSession session = session();
        long enteredAt = Long.MAX_VALUE;

        for (long now = 0L; now <= 30L * 60L * 1_000L; now += 1_000L) {
            if (session.checkAutomaticAfk(now, true).shouldEnterAfk()) {
                enteredAt = now;
                break;
            }
        }

        assertEquals(AfkPolicy.DEFAULT.idleTimeoutMillis()
                + AfkPolicy.DEFAULT.automaticEntryGraceMillis(), enteredAt);
    }

    @Test
    void lightActivityCannotPostponeAnAlreadyPassivePlayerForever() {
        AfkPlayerSession session = session();
        AfkActivityTracker activity = session.activity();
        long passiveAt = AfkPolicy.DEFAULT.passiveTimeoutMillis();

        for (long now = 250L; now <= passiveAt; now += 250L) {
            activity.recordLightActivity(now);
        }

        AfkPlayerSession.AutomaticCheck first = session.checkAutomaticAfk(passiveAt, true);
        assertEquals(AfkActivityTracker.CheckResult.AFK_PASSIVE, first.result());
        assertFalse(first.shouldEnterAfk());

        for (long now = passiveAt + 250L; now < passiveAt + 1_000L; now += 250L) {
            activity.recordLightActivity(now);
            assertFalse(session.checkAutomaticAfk(now, true).shouldEnterAfk());
        }
        activity.recordLightActivity(passiveAt + 1_000L);
        assertTrue(session.checkAutomaticAfk(passiveAt + 1_000L, true).shouldEnterAfk());
    }

    @Test
    void heldMovementInputWithoutDisplacementDoesNotBypassIdleAfk() {
        AfkPlayerSession session = session();
        session.activity().recordMovementInput(
                true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);

        assertFalse(session.checkAutomaticAfk(AfkPolicy.DEFAULT.idleTimeoutMillis(), true)
                .shouldEnterAfk());
        assertTrue(session.checkAutomaticAfk(
                AfkPolicy.DEFAULT.idleTimeoutMillis()
                        + AfkPolicy.DEFAULT.automaticEntryGraceMillis(), true).shouldEnterAfk());
    }

    @Test
    void unsafePhysicsDefersTheTransitionWithoutLosingTheInactivityDecision() {
        AfkPlayerSession session = session();
        long idleAt = AfkPolicy.DEFAULT.idleTimeoutMillis();
        long safeAt = idleAt + 30_000L;

        AfkPlayerSession.AutomaticCheck deferred = session.checkAutomaticAfk(idleAt, false);
        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE, deferred.result());
        assertFalse(deferred.safeToEnter());
        assertFalse(deferred.shouldEnterAfk());

        assertFalse(session.checkAutomaticAfk(safeAt, true).shouldEnterAfk());
        assertTrue(session.checkAutomaticAfk(
                safeAt + AfkPolicy.DEFAULT.automaticEntryGraceMillis(), true).shouldEnterAfk());
    }

    @Test
    void interactiveSessionSuppressesAfkWithoutResettingTheUnderlyingClock() {
        AfkPlayerSession session = session();
        long thirtyMinutes = 30L * 60L * 1_000L;

        AfkPlayerSession.AutomaticCheck suppressed = session.checkAutomaticAfk(
                thirtyMinutes, true, true);
        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE, suppressed.result());
        assertTrue(suppressed.automaticAfkSuppressed());
        assertFalse(suppressed.shouldEnterAfk());
        assertFalse(session.isActivityEligible(thirtyMinutes));
        assertFalse(session.checkAutomaticAfk(
                thirtyMinutes + 60_000L, true, true).shouldEnterAfk());

        long releasedAt = thirtyMinutes + 60_001L;
        assertFalse(session.checkAutomaticAfk(releasedAt, true, false).shouldEnterAfk());
        assertTrue(session.checkAutomaticAfk(
                releasedAt + AfkPolicy.DEFAULT.automaticEntryGraceMillis(),
                true,
                false).shouldEnterAfk());
    }

    private static AfkPlayerSession session() {
        return new AfkPlayerSession(0L, WORLD_ID, 0.0D, 0.0D, 0.0D);
    }
}
