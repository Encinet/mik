package org.encinet.mik.module.afk;

/**
 * Tunable rules for activity clocks, fresh evidence batches and AFK transitions.
 *
 * <p>The passive timeout bounds pending evidence lifetime, not a renewable pool
 * of already credited actions. Target deduplication survives successful batches.
 * Movement-only reward expiry does not create AFK status: running/jumping can be
 * legitimate even when the server has insufficient evidence for activity rewards.
 * Statistical window sizes are maintained by {@link AfkBehaviorAnalyzer}; they
 * are conservative policy defaults rather than calibrated bot probabilities.
 *
 * @param idleTimeoutMillis maximum age of observed input before idle AFK
 * @param passiveTimeoutMillis maximum age of substantial activity before passive AFK
 * @param substantialMovementDistance player-controlled path length per substantial update
 * @param actionDeduplicationMillis global minimum spacing between accepted evidence
 * @param actionTargetDeduplicationMillis minimum spacing for evidence against one target
 * @param movementOnlyRewardTimeoutMillis reward grace without an intentional outcome batch
 * @param requiredActions fresh evidence/outcome count required for a batch
 * @param requiredUnlockActionTypes accepted-outcome type diversity required for recovery
 * @param automaticEntryGraceMillis continuous candidate duration before AFK protection
 * @param automaticReentryCooldownMillis grace after leaving AFK to establish real activity
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
