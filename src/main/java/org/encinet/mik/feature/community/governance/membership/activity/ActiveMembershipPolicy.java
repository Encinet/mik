package org.encinet.mik.feature.community.governance.membership.activity;

import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Objects;

/** Rules for deciding whether a current member belongs to the electorate. */
public final class ActiveMembershipPolicy {
    public static final Duration MINIMUM_TENURE = Duration.ofDays(30);
    private static final Duration STANDARD_PLAYTIME = Duration.ofHours(6);
    private static final Duration SEASONAL_PLAYTIME = Duration.ofHours(12);
    public static final int MINIMUM_PARTICIPATION_DAYS = 6;
    public static final int MINIMUM_RECENT_PARTICIPATION_DAYS = 2;

    private ActiveMembershipPolicy() {
    }

    public static ActiveMemberEligibility evaluate(
            boolean currentMember,
            Instant memberSince,
            ParticipationSummary participation,
            boolean activelyBanned,
            Instant now,
            LocalDate today
    ) {
        Objects.requireNonNull(participation, "participation");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(today, "today");
        Duration requiredPlaytime = minimumPlaytime(today);
        boolean tenure = memberSince != null
                && !memberSince.plus(MINIMUM_TENURE).isAfter(now);
        return new ActiveMemberEligibility(
                currentMember,
                tenure,
                participation.secondsLast60Days() >= requiredPlaytime.toSeconds(),
                participation.participationDaysLast60() >= MINIMUM_PARTICIPATION_DAYS,
                participation.participationDaysLast30()
                        >= MINIMUM_RECENT_PARTICIPATION_DAYS,
                !activelyBanned,
                requiredPlaytime);
    }

    private static Duration minimumPlaytime(LocalDate today) {
        MonthDay date = MonthDay.from(today);
        boolean seasonal = !date.isAfter(MonthDay.of(3, 15))
                || (!date.isBefore(MonthDay.of(6, 25))
                    && !date.isAfter(MonthDay.of(9, 15)));
        return seasonal ? SEASONAL_PLAYTIME : STANDARD_PLAYTIME;
    }
}
