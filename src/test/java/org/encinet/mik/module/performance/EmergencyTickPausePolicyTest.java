package org.encinet.mik.module.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmergencyTickPausePolicyTest {

    @Test
    void pausesOnlyAfterConsecutiveExtremeTicks() {
        EmergencyTickPausePolicy policy = new EmergencyTickPausePolicy();

        assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(1_500.0, false));
        assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(50.0, false));
        assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(1_000.0, false));
        assertEquals(EmergencyTickPausePolicy.Decision.FREEZE, policy.evaluate(1_001.0, false));
    }

    @Test
    void pausesAndKicksImmediatelyAfterConsecutiveCriticalTicks() {
        EmergencyTickPausePolicy policy = new EmergencyTickPausePolicy();

        assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(2_000.0, false));
        assertEquals(EmergencyTickPausePolicy.Decision.FREEZE_AND_KICK, policy.evaluate(2_500.0, false));
    }

    @Test
    void recoversOnlyAfterAStableHealthyWindow() {
        EmergencyTickPausePolicy policy = new EmergencyTickPausePolicy();

        for (int tick = 1; tick < EmergencyTickPausePolicy.RECOVERY_CONFIRM_TICKS; tick++) {
            assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(50.0, true));
        }
        assertEquals(EmergencyTickPausePolicy.Decision.UNFREEZE, policy.evaluate(50.0, true));
    }

    @Test
    void unhealthyPausedTickRestartsRecoveryConfirmation() {
        EmergencyTickPausePolicy policy = new EmergencyTickPausePolicy();

        for (int tick = 0; tick < EmergencyTickPausePolicy.RECOVERY_CONFIRM_TICKS - 1; tick++) {
            assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(50.0, true));
        }
        assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(201.0, true));
        for (int tick = 1; tick < EmergencyTickPausePolicy.RECOVERY_CONFIRM_TICKS; tick++) {
            assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(50.0, true));
        }
        assertEquals(EmergencyTickPausePolicy.Decision.UNFREEZE, policy.evaluate(50.0, true));
    }

    @Test
    void kicksAtMostOnceDuringOnePause() {
        EmergencyTickPausePolicy policy = new EmergencyTickPausePolicy();

        assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(2_000.0, true));
        assertEquals(EmergencyTickPausePolicy.Decision.KICK, policy.evaluate(2_000.0, true));
        assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(2_000.0, true));
        assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(2_000.0, true));
    }

    @Test
    void invalidDurationsCannotTriggerPauseOrRecovery() {
        EmergencyTickPausePolicy policy = new EmergencyTickPausePolicy();

        assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(Double.NaN, false));
        assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(Double.NaN, true));
        assertEquals(EmergencyTickPausePolicy.Decision.HOLD, policy.evaluate(-1.0, true));
    }
}
