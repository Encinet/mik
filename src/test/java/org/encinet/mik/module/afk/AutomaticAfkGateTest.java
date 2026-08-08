package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticAfkGateTest {

    @Test
    void entersOnlyAfterAnUninterruptedCandidatePeriod() {
        AutomaticAfkGate gate = new AutomaticAfkGate(1_000L, 1_250L);

        assertFalse(gate.shouldEnter(0L, true));
        assertFalse(gate.shouldEnter(999L, true));
        assertTrue(gate.shouldEnter(1_000L, true));
        assertFalse(gate.shouldEnter(1_001L, true));
    }

    @Test
    void explicitResetDiscardsTheCandidateAndCooldown() {
        AutomaticAfkGate gate = new AutomaticAfkGate(1_000L, 1_250L);

        assertFalse(gate.shouldEnter(0L, true));
        gate.recordExit(100L);
        gate.reset();
        assertFalse(gate.shouldEnter(900L, true));
        assertTrue(gate.shouldEnter(1_900L, true));
    }

    @Test
    void repeatedEligibleChecksDoNotRestartTheCandidatePeriod() {
        AutomaticAfkGate gate = new AutomaticAfkGate(1_000L, 1_250L);

        assertFalse(gate.shouldEnter(10 * 60_000L, true));
        assertFalse(gate.shouldEnter(10 * 60_000L + 250L, true));
        assertFalse(gate.shouldEnter(10 * 60_000L + 500L, true));
        assertFalse(gate.shouldEnter(10 * 60_000L + 750L, true));
        assertTrue(gate.shouldEnter(10 * 60_000L + 1_000L, true));
    }

    @Test
    void losingEligibilityDiscardsTheOldCandidate() {
        AutomaticAfkGate gate = new AutomaticAfkGate(1_000L, 1_250L);

        assertFalse(gate.shouldEnter(0L, true));
        assertFalse(gate.shouldEnter(1_000L, false));
        assertFalse(gate.shouldEnter(2_000L, true));
        assertFalse(gate.shouldEnter(2_999L, true));
        assertTrue(gate.shouldEnter(3_000L, true));
    }

    @Test
    void cooldownRequiresANewCandidatePeriodAfterExit() {
        AutomaticAfkGate gate = new AutomaticAfkGate(1_000L, 1_250L);
        gate.recordExit(0L);

        assertFalse(gate.shouldEnter(1_249L, true));
        assertFalse(gate.shouldEnter(1_250L, true));
        assertTrue(gate.shouldEnter(2_250L, true));
    }
}
