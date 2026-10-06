package org.encinet.mik.module.governance.membership.participation;

import java.time.LocalDate;
import java.util.Objects;

/** Effective playtime recorded for one governance-zone natural day. */
public record DailyParticipation(LocalDate date, long eligibleSeconds) {
    public DailyParticipation {
        Objects.requireNonNull(date, "date");
        if (eligibleSeconds < 0) {
            throw new IllegalArgumentException("eligibleSeconds must not be negative");
        }
    }
}
