package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticAfkGateTest {

    @Test
    void entersOnlyAfterAnUninterruptedCandidatePeriod() {
        AutomaticAfkGate gate = new AutomaticAfkGate(1_000L, 1_250L);

        assertFalse(gate.shouldEnter(0L, true, 7L));
        assertFalse(gate.shouldEnter(999L, true, 7L));
        assertTrue(gate.shouldEnter(1_000L, true, 7L));
        assertFalse(gate.shouldEnter(1_001L, true, 7L));
    }

    @Test
    void newActivityRestartsTheCandidatePeriod() {
        AutomaticAfkGate gate = new AutomaticAfkGate(1_000L, 1_250L);

        assertFalse(gate.shouldEnter(0L, true, 1L));
        assertFalse(gate.shouldEnter(900L, true, 2L));
        assertFalse(gate.shouldEnter(1_899L, true, 2L));
        assertTrue(gate.shouldEnter(1_900L, true, 2L));
    }

    @Test
    void losingEligibilityDiscardsTheOldCandidate() {
        AutomaticAfkGate gate = new AutomaticAfkGate(1_000L, 1_250L);

        assertFalse(gate.shouldEnter(0L, true, 1L));
        assertFalse(gate.shouldEnter(1_000L, false, 1L));
        assertFalse(gate.shouldEnter(2_000L, true, 1L));
        assertFalse(gate.shouldEnter(2_999L, true, 1L));
        assertTrue(gate.shouldEnter(3_000L, true, 1L));
    }

    @Test
    void cooldownRequiresANewCandidatePeriodAfterExit() {
        AutomaticAfkGate gate = new AutomaticAfkGate(1_000L, 1_250L);
        gate.recordExit(0L);

        assertFalse(gate.shouldEnter(1_249L, true, 1L));
        assertFalse(gate.shouldEnter(1_250L, true, 1L));
        assertTrue(gate.shouldEnter(2_250L, true, 1L));
    }
}
