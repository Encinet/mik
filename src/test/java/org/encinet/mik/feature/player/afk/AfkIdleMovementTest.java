package org.encinet.mik.feature.player.afk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkIdleMovementTest {

    private static final UUID WORLD_ID = new UUID(0L, 1L);
    private static final AfkPolicy POLICY = AfkPolicy.DEFAULT;

    @ParameterizedTest(name = "{0}")
    @MethodSource("passiveMovement")
    void movementWithoutInputEntersIdleAfkAndNeedsANewGestureToExit(
            String scenario,
            AfkEntrySafety safety,
            double stepX,
            double stepY,
            boolean playerControlledContext
    ) {
        AfkPlayerSession session = session();
        AfkActivityTracker activity = session.activity();
        long releasedAt = 1_000L;
        activity.recordMovementInput(true, WORLD_ID, 0.0D, 0.0D, 0.0D, 0L);
        assertTrue(activity.recordMovement(WORLD_ID, 8.0D, 0.0D, 0.0D, releasedAt));
        activity.recordMovementInput(false, WORLD_ID, 8.0D, 0.0D, 0.0D, releasedAt);
        long enteredAt = releasedAt + POLICY.idleTimeoutMillis() + POLICY.automaticEntryGraceMillis();
        assertFalse(safety.isPhysicallyStable());

        for (long now = releasedAt + 250L; now <= enteredAt; now += 250L) {
            double x = 8.0D + (now - releasedAt) / 250L * stepX;
            double y = (now - releasedAt) / 250L * stepY;
            assertFalse(activity.recordMovement(WORLD_ID, x, y, 0.0D, now));
            activity.recordObservation(WORLD_ID, x, y, 0.0D, 0.0F, playerControlledContext, now);

            AfkPlayerSession.AutomaticCheck check = session.checkAutomaticAfk(
                    now, safety.isPhysicallyStable());
            assertEquals(now == enteredAt, check.shouldEnterAfk());
        }

        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE, activity.check(enteredAt));
        assertFalse(session.isActivityEligible(enteredAt));
        session.enterAfk(enteredAt, false);
        assertFalse(activity.recordMovement(WORLD_ID, 1_000.0D, -5_000.0D, 0.0D, enteredAt + 250L));
        assertFalse(activity.canMovementClearAfk());
        assertTrue(activity.recordMovementInput(
                true, WORLD_ID, 1_000.0D, -5_000.0D, 0.0D, enteredAt + 500L));
    }

    private static Stream<Arguments> passiveMovement() {
        return Stream.of(
                Arguments.of("water current", new AfkEntrySafety(false, false, 0.0F, 0.04D, 0.0D),
                        0.25D, 0.0D, true),
                Arguments.of("flight inertia", new AfkEntrySafety(false, false, 0.0F, 1.0D, 0.0D),
                        1.0D, 0.0D, true),
                Arguments.of("gliding", new AfkEntrySafety(true, false, 0.0F, 4.0D, 0.0D),
                        2.0D, -0.1D, false),
                Arguments.of("free fall", new AfkEntrySafety(false, false, 1_000.0F, 16.0D, 0.0D),
                        0.0D, -4.0D, true),
                Arguments.of("riptide", new AfkEntrySafety(false, true, 0.0F, 4.0D, 0.0D),
                        2.0D, 0.0D, false),
                Arguments.of("moving vehicle", new AfkEntrySafety(false, false, 0.0F, 0.0D, 4.0D),
                        2.0D, 0.0D, false)
        );
    }

    @Test
    void freshInputDuringTheIdleGraceRestartsTheTimeoutEvenWhileMoving() {
        AfkPlayerSession session = session();
        long idleAt = POLICY.idleTimeoutMillis();
        assertFalse(session.checkAutomaticAfk(idleAt, false).shouldEnterAfk());

        long inputAt = idleAt + 500L;
        session.activity().recordLightActivity(inputAt);
        assertEquals(AfkActivityTracker.CheckResult.ACTIVE,
                session.checkAutomaticAfk(idleAt + 1_000L, false).result());
        assertFalse(session.checkAutomaticAfk(inputAt + POLICY.idleTimeoutMillis() - 1L, false)
                .shouldEnterAfk());
        assertFalse(session.checkAutomaticAfk(inputAt + POLICY.idleTimeoutMillis(), false)
                .shouldEnterAfk());
        assertTrue(session.checkAutomaticAfk(
                inputAt + POLICY.idleTimeoutMillis() + POLICY.automaticEntryGraceMillis(), false)
                .shouldEnterAfk());
    }

    @Test
    void passiveCandidateCanEnterDuringMovementOnceInputsStop() {
        AfkPlayerSession session = session();
        long passiveAt = POLICY.passiveTimeoutMillis();
        session.activity().recordLightActivity(passiveAt);
        assertFalse(session.checkAutomaticAfk(passiveAt, false).shouldEnterAfk());

        long idleAt = passiveAt + POLICY.idleTimeoutMillis();
        assertFalse(session.checkAutomaticAfk(idleAt - 1L, false).shouldEnterAfk());
        AfkPlayerSession.AutomaticCheck idle = session.checkAutomaticAfk(idleAt, false);
        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE, idle.result());
        assertFalse(idle.shouldEnterAfk());
        assertTrue(session.checkAutomaticAfk(idleAt + POLICY.automaticEntryGraceMillis(), false)
                .shouldEnterAfk());
    }

    @Test
    void idleReconnectCanRestoreAfkBeforePhysicalMovementSettles() {
        AfkPlayerSession session = session();
        long disconnectedAt = POLICY.idleTimeoutMillis();
        session.suspendForDisconnect(disconnectedAt);
        long reconnectedAt = disconnectedAt + 60_000L;
        session.resumeAfterReconnect(reconnectedAt, WORLD_ID, 0.0D, -5_000.0D, 0.0D);

        assertTrue(session.canRestoreAutomaticAfk(reconnectedAt, false));
        assertFalse(session.isActivityEligible(reconnectedAt));
    }

    @Test
    void passiveReconnectStillWaitsForPhysicalStability() {
        AfkPlayerSession session = session();
        long disconnectedAt = POLICY.passiveTimeoutMillis();
        session.activity().recordLightActivity(disconnectedAt);
        session.suspendForDisconnect(disconnectedAt);
        long reconnectedAt = disconnectedAt + 60_000L;
        session.resumeAfterReconnect(reconnectedAt, WORLD_ID, 0.0D, -5_000.0D, 0.0D);

        assertFalse(session.canRestoreAutomaticAfk(reconnectedAt, false));
        assertTrue(session.canRestoreAutomaticAfk(reconnectedAt, true));
    }

    private static AfkPlayerSession session() {
        return new AfkPlayerSession(0L, WORLD_ID, 0.0D, 0.0D, 0.0D);
    }
}
