package org.encinet.mik.module.governance.membership.activity;

import org.encinet.mik.module.governance.membership.participation.ParticipationSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveMembershipPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Test
    void activeMemberRequiresEveryRollingWindowCondition() {
        ParticipationSummary summary = new ParticipationSummary(
                100_000, 20, 21_600, 6, 2);

        assertTrue(ActiveMembershipPolicy.evaluate(
                true, NOW.minus(ActiveMembershipPolicy.MINIMUM_TENURE),
                summary, false, NOW).eligible());
        assertFalse(ActiveMembershipPolicy.evaluate(
                true, NOW.minus(ActiveMembershipPolicy.MINIMUM_TENURE),
                new ParticipationSummary(100_000, 20, 21_600, 6, 1),
                false, NOW).eligible());
        assertFalse(ActiveMembershipPolicy.evaluate(
                false, NOW.minus(ActiveMembershipPolicy.MINIMUM_TENURE),
                summary, false, NOW).eligible());
    }
}
