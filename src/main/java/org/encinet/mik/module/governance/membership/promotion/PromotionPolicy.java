package org.encinet.mik.module.governance.membership.promotion;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Rules for acquiring the initial member role. */
public final class PromotionPolicy {
    public static final Duration MINIMUM_ACCOUNT_AGE = Duration.ofDays(3);
    public static final Duration MINIMUM_PLAYTIME = Duration.ofHours(8);
    public static final int MINIMUM_PLAY_DAYS = 2;

    private PromotionPolicy() {
    }

    public static PromotionEligibility evaluate(
            Instant firstJoined,
            long eligiblePlaySeconds,
            int eligiblePlayDays,
            boolean activelyBanned,
            PromotionPause activePause,
            Instant now
    ) {
        Objects.requireNonNull(firstJoined, "firstJoined");
        Objects.requireNonNull(now, "now");
        return new PromotionEligibility(
                !firstJoined.plus(MINIMUM_ACCOUNT_AGE).isAfter(now),
                eligiblePlaySeconds >= MINIMUM_PLAYTIME.toSeconds(),
                eligiblePlayDays >= MINIMUM_PLAY_DAYS,
                !activelyBanned,
                activePause == null,
                activePause);
    }
}
