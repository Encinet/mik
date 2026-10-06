package org.encinet.mik.module.governance.membership.participation;

/** Effective-play totals as of one qualification check. */
public record ParticipationSummary(
        long lifetimeSeconds,
        int lifetimePlayDays,
        long secondsLast60Days,
        int participationDaysLast60,
        int participationDaysLast30
) {
    public ParticipationSummary {
        if (lifetimeSeconds < 0 || lifetimePlayDays < 0 || secondsLast60Days < 0
                || participationDaysLast60 < 0 || participationDaysLast30 < 0) {
            throw new IllegalArgumentException("participation values must not be negative");
        }
    }
}
