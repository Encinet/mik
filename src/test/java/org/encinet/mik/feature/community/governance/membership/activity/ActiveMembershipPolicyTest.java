package org.encinet.mik.feature.community.governance.membership.activity;

import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveMembershipPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Test
    void activeMemberRequiresEveryRollingWindowCondition() {
        ParticipationSummary summary = new ParticipationSummary(
                100_000, 20, 43_200, 6, 2);

        assertTrue(ActiveMembershipPolicy.evaluate(
                true, NOW.minus(ActiveMembershipPolicy.MINIMUM_TENURE),
                summary, false, NOW, LocalDate.parse("2026-08-22")).eligible());
        assertFalse(ActiveMembershipPolicy.evaluate(
                true, NOW.minus(ActiveMembershipPolicy.MINIMUM_TENURE),
                new ParticipationSummary(100_000, 20, 43_200, 6, 1),
                false, NOW, LocalDate.parse("2026-08-22")).eligible());
        assertFalse(ActiveMembershipPolicy.evaluate(
                false, NOW.minus(ActiveMembershipPolicy.MINIMUM_TENURE),
                summary, false, NOW, LocalDate.parse("2026-08-22")).eligible());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-01-01, 12", "2026-03-15, 12", "2026-03-16, 6",
            "2026-06-24, 6", "2026-06-25, 12", "2026-07-20, 12",
            "2026-09-15, 12", "2026-09-16, 6", "2026-12-31, 6",
            "2027-01-01, 12", "2028-02-29, 12"
    })
    void playtimeThresholdFollowsInclusiveAnnualDateRanges(String value, int hours) {
        LocalDate date = LocalDate.parse(value);
        long requiredSeconds = Duration.ofHours(hours).toSeconds();

        assertEquals(Duration.ofHours(hours), evaluate(date, requiredSeconds).requiredPlaytime());
        assertTrue(evaluate(date, requiredSeconds).eligible());
        assertFalse(evaluate(date, requiredSeconds - 1).eligible());
        assertEquals(hours == 6, evaluate(date, Duration.ofHours(6).toSeconds()).eligible());
    }

    private static ActiveMemberEligibility evaluate(LocalDate date, long seconds) {
        Instant now = date.atTime(12, 0).toInstant(ZoneOffset.UTC);
        return ActiveMembershipPolicy.evaluate(
                true, now.minus(ActiveMembershipPolicy.MINIMUM_TENURE),
                new ParticipationSummary(100_000, 20, seconds, 6, 2), false, now, date);
    }
}
