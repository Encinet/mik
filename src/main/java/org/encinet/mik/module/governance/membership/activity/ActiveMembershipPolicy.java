package org.encinet.mik.module.governance.membership.activity;

import org.encinet.mik.module.governance.membership.participation.ParticipationSummary;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Rules for deciding whether a current member belongs to the electorate. */
public final class ActiveMembershipPolicy {
    public static final Duration MINIMUM_TENURE = Duration.ofDays(30);
    public static final Duration MINIMUM_PLAYTIME = Duration.ofHours(6);
    public static final int MINIMUM_PARTICIPATION_DAYS = 6;
    public static final int MINIMUM_RECENT_PARTICIPATION_DAYS = 2;

    private ActiveMembershipPolicy() {
    }

    public static ActiveMemberEligibility evaluate(
            boolean currentMember,
            Instant memberSince,
            ParticipationSummary participation,
            boolean activelyBanned,
            Instant now
    ) {
        Objects.requireNonNull(participation, "participation");
        Objects.requireNonNull(now, "now");
        boolean tenure = memberSince != null
                && !memberSince.plus(MINIMUM_TENURE).isAfter(now);
        return new ActiveMemberEligibility(
                currentMember,
                tenure,
                participation.secondsLast60Days() >= MINIMUM_PLAYTIME.toSeconds(),
                participation.participationDaysLast60() >= MINIMUM_PARTICIPATION_DAYS,
                participation.participationDaysLast30()
                        >= MINIMUM_RECENT_PARTICIPATION_DAYS,
                !activelyBanned);
    }
}
