package org.encinet.mik.module.governance.membership.participation;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Converts the append-only daily ledger into governance eligibility windows. */
public final class ParticipationPolicy {
    public static final Duration ACTIVE_WINDOW = Duration.ofDays(60);
    public static final Duration RECENT_WINDOW = Duration.ofDays(30);
    public static final Duration PARTICIPATION_DAY_MINIMUM = Duration.ofMinutes(30);

    private ParticipationPolicy() {
    }

    public static ParticipationSummary summarize(
            List<DailyParticipation> days,
            LocalDate today
    ) {
        Objects.requireNonNull(days, "days");
        Objects.requireNonNull(today, "today");
        LocalDate activeWindowStart = today.minusDays(ACTIVE_WINDOW.toDays() - 1);
        LocalDate recentWindowStart = today.minusDays(RECENT_WINDOW.toDays() - 1);
        long lifetime = 0;
        int lifetimeDays = 0;
        long activeWindowSeconds = 0;
        int activeWindowDays = 0;
        int recentWindowDays = 0;
        for (DailyParticipation day : days) {
            long seconds = day.eligibleSeconds();
            lifetime += seconds;
            if (seconds > 0) lifetimeDays++;
            if (day.date().isBefore(activeWindowStart) || day.date().isAfter(today)) continue;
            activeWindowSeconds += seconds;
            if (seconds >= PARTICIPATION_DAY_MINIMUM.toSeconds()) {
                activeWindowDays++;
                if (!day.date().isBefore(recentWindowStart)) recentWindowDays++;
            }
        }
        return new ParticipationSummary(
                lifetime, lifetimeDays, activeWindowSeconds,
                activeWindowDays, recentWindowDays);
    }
}
