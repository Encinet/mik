package org.encinet.mik.module.governance.membership.tenure;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Rules for automatic removal of long-inactive moderators. */
public final class ModeratorTenurePolicy {
    public static final Duration INACTIVITY_LIMIT = Duration.ofDays(180);
    public static final int MINIMUM_MODERATOR_COUNT = 4;

    private ModeratorTenurePolicy() {
    }

    /** Oldest inactive moderators first, while preserving the moderator floor. */
    public static List<ModeratorPresence> automaticRemovals(
            Collection<ModeratorPresence> moderators,
            Instant now
    ) {
        Objects.requireNonNull(moderators, "moderators");
        Objects.requireNonNull(now, "now");
        int removableCount = Math.max(0, moderators.size() - MINIMUM_MODERATOR_COUNT);
        if (removableCount == 0) return List.of();
        return moderators.stream()
                .filter(moderator -> !moderator.lastSuccessfulLoginAt()
                        .plus(INACTIVITY_LIMIT).isAfter(now))
                .sorted(Comparator.comparing(ModeratorPresence::lastSuccessfulLoginAt)
                        .thenComparing(ModeratorPresence::playerId))
                .limit(removableCount)
                .toList();
    }
}
