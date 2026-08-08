package org.encinet.mik.module.afk;

/**
 * Tunable rules for activity classification and automatic AFK transitions.
 */
record AfkPolicy(
        long idleTimeoutMillis,
        long passiveTimeoutMillis,
        double substantialMovementDistance,
        long actionDeduplicationMillis,
        long actionTargetDeduplicationMillis,
        long movementOnlyRewardTimeoutMillis,
        int requiredActions,
        int requiredUnlockActionTypes,
        long automaticEntryGraceMillis,
        long automaticReentryCooldownMillis
) {

    static final AfkPolicy DEFAULT = new AfkPolicy(
            3L * 60L * 1_000L,
            10L * 60L * 1_000L,
            8.0D,
            1_000L,
            30_000L,
            15L * 60L * 1_000L,
            3,
            2,
            1_000L,
            1_250L
    );

    AfkPolicy {
        if (idleTimeoutMillis <= 0L) {
            throw new IllegalArgumentException("idle timeout must be positive");
        }
        if (passiveTimeoutMillis < idleTimeoutMillis) {
            throw new IllegalArgumentException("passive timeout cannot be shorter than idle timeout");
        }
        if (!Double.isFinite(substantialMovementDistance) || substantialMovementDistance <= 0.0D) {
            throw new IllegalArgumentException("substantial movement distance must be finite and positive");
        }
        if (actionDeduplicationMillis < 0L || actionTargetDeduplicationMillis < 0L) {
            throw new IllegalArgumentException("action deduplication durations cannot be negative");
        }
        if (movementOnlyRewardTimeoutMillis <= 0L) {
            throw new IllegalArgumentException("movement-only reward timeout must be positive");
        }
        if (requiredActions <= 0
                || requiredUnlockActionTypes <= 0
                || requiredUnlockActionTypes > requiredActions) {
            throw new IllegalArgumentException("action evidence thresholds are inconsistent");
        }
        if (automaticEntryGraceMillis < 0L || automaticReentryCooldownMillis < 0L) {
            throw new IllegalArgumentException("automatic AFK transition durations cannot be negative");
        }
    }
}
