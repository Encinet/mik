package org.encinet.mik.module.afk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

/**
 * Separates observed input, substantial activity and accepted gameplay outcomes.
 *
 * <p>Randomness, repetition and confined movement are only reward-policy evidence:
 * they never create an automatic AFK candidate or freeze a running player. This
 * deliberately leaves ambiguous human/bot movement active. Ordinary inactivity
 * still uses the observation/substantial clocks, while rewards additionally need
 * sufficiently recent accepted outcomes or trusted game-module activity.
 */
final class AfkActivityTracker {

    private static final int MAX_PENDING_ACTIONS = 64;
    private static final int MAX_DEDUPLICATION_ACTIONS = 128;
    private static final long MOVEMENT_ACCUMULATION_GRACE_MILLIS = 5_000L;

    enum CheckResult {
        ACTIVE(false),
        AFK_IDLE(true),
        AFK_PASSIVE(true),
        ACTIVITY_REWARD_LOCKED(false);

        private final boolean automaticAfkCandidate;

        CheckResult(boolean automaticAfkCandidate) {
            this.automaticAfkCandidate = automaticAfkCandidate;
        }

        boolean automaticAfkCandidate() {
            return automaticAfkCandidate;
        }
    }

    private final AfkPolicy policy;
    private final AfkBehaviorAnalyzer behaviorAnalyzer = new AfkBehaviorAnalyzer();
    private final Deque<TimedAction> recentActions = new ArrayDeque<>();
    private final Deque<TimedAction> recentOutcomes = new ArrayDeque<>();
    private final Deque<TimedAction> actionDeduplicationHistory = new ArrayDeque<>();
    private long lastObservedAt;
    private long lastSubstantialAt;
    private long lastIntentionalActivityAt;
    private long lastCountedActionAt = Long.MIN_VALUE;
    private boolean movementGestureActive;
    private boolean movementReleaseRequired;
    private boolean automationRewardLocked;
    private boolean substantialActivityWhileSuspended;
    private boolean intentionalActivityWhileSuspended;
    private long suspendedAt = Long.MIN_VALUE;
    private UUID movementGestureWorldId;
    private double movementGestureX;
    private double movementGestureY;
    private double movementGestureZ;
    private double movementDistanceSinceSubstantial;
    private long lastMovementAt = Long.MIN_VALUE;

    AfkActivityTracker(long now, UUID worldId, double x, double y, double z) {
        this(AfkPolicy.DEFAULT, now, worldId, x, y, z);
    }

    AfkActivityTracker(AfkPolicy policy, long now, UUID worldId, double x, double y, double z) {
        this.policy = Objects.requireNonNull(policy, "policy");
        reset(now, worldId, x, y, z);
    }

    void recordLightActivity(long now) {
        if (suspendedAt == Long.MIN_VALUE) {
            lastObservedAt = now;
        }
    }

    /**
     * Opens/rebases a client gesture, preserving recent sub-threshold travel across
     * short releases. Tapping jump or changing keys must not erase all progress.
     * The new position is the baseline, so external displacement while released
     * is never credited. Credit expires after five seconds without actual movement
     * and is cleared at AFK, disconnect, teleport and world-change boundaries.
     */
    boolean recordMovementInput(
            boolean active,
            UUID worldId,
            double x,
            double y,
            double z,
            long now
    ) {
        if (!active) {
            movementGestureActive = false;
            movementReleaseRequired = false;
            return false;
        }

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return false;
        }

        recordLightActivity(now);
        if (movementGestureActive || movementReleaseRequired) {
            return false;
        }
        if (!Objects.equals(movementGestureWorldId, worldId)
                || lastMovementAt == Long.MIN_VALUE
                || elapsed(now, lastMovementAt) > MOVEMENT_ACCUMULATION_GRACE_MILLIS) {
            resetMovementDistance();
        }
        movementGestureActive = true;
        movementGestureWorldId = worldId;
        movementGestureX = x;
        movementGestureY = y;
        movementGestureZ = z;
        return true;
    }

    boolean recordMovement(UUID worldId, double x, double y, double z, long now) {
        if (!movementGestureActive || !Double.isFinite(x)
                || !Double.isFinite(y) || !Double.isFinite(z)) {
            return false;
        }
        recordLightActivity(now);
        if (!Objects.equals(movementGestureWorldId, worldId)) {
            updateMovementPosition(worldId, x, y, z);
            resetMovementDistance();
            behaviorAnalyzer.reset();
            return false;
        }

        if (lastMovementAt != Long.MIN_VALUE
                && elapsed(now, lastMovementAt) > MOVEMENT_ACCUMULATION_GRACE_MILLIS) {
            resetMovementDistance();
        }
        double distance = Math.sqrt(distanceSquared(x, y, z));
        movementDistanceSinceSubstantial += distance;
        if (distance > 0.0D) {
            lastMovementAt = now;
        }
        updateMovementPosition(worldId, x, y, z);
        if (movementDistanceSinceSubstantial >= policy.substantialMovementDistance()) {
            movementDistanceSinceSubstantial %= policy.substantialMovementDistance();
            markSubstantial(now);
            return true;
        }
        return false;
    }

    boolean canMovementClearAfk() {
        return movementGestureActive;
    }

    boolean isActivityEligible(long now) {
        return !automationRewardLocked
                && elapsed(now, lastObservedAt) < policy.idleTimeoutMillis()
                && elapsed(now, lastIntentionalActivityAt) < policy.movementOnlyRewardTimeoutMillis();
    }

    boolean isAutomationRewardLocked() {
        return automationRewardLocked;
    }

    void recordObservation(UUID worldId, double x, double y, double z, float yaw, long now) {
        recordObservation(worldId, x, y, z, yaw, true, now);
    }

    /**
     * Samples position without refreshing any activity clock or inventing input.
     * Vehicle/gliding/riptide context is excluded by the adapter because a held
     * input is insufficient to attribute those trajectories to active control.
     */
    void recordObservation(
            UUID worldId, double positionX, double positionY, double positionZ, float yaw,
            boolean playerControlledContext, long now
    ) {
        if (suspendedAt == Long.MIN_VALUE) {
            behaviorAnalyzer.record(worldId, positionX, positionY, positionZ, yaw,
                    movementGestureActive && playerControlledContext, now);
        }
    }

    /** Rebaselines a teleport without counting the discontinuity as travel. */
    void rebasePosition(UUID worldId, double positionX, double positionY, double positionZ) {
        updateMovementPosition(worldId, positionX, positionY, positionZ);
        resetMovementDistance();
        behaviorAnalyzer.reset();
    }

    AfkBehaviorAnalyzer.Analysis movementAnalysis(long now) {
        return behaviorAnalyzer.analyze(now);
    }

    void suspendMovementGesture() {
        movementReleaseRequired = movementGestureActive;
        movementGestureActive = false;
        resetMovementDistance();
    }

    /**
     * Builds a consumable batch of fresh, globally/target-deduplicated evidence.
     *
     * <p>A batch of distinct weak interactions can establish substantial activity
     * but cannot renew rewards or clear a restriction. A batch containing enough
     * accepted outcomes can renew intentional activity. Restriction recovery also
     * requires target/type diversity. A successful batch is consumed, while its
     * deduplication history survives: neither stale batch members nor an immediate
     * replay can repeatedly refresh the clocks. Routine batches do not erase the
     * movement history; actual outcomes instead provide a full observation grace
     * period through {@code lastIntentionalActivityAt}. Outcome batches are kept
     * independently so intervening weak clicks cannot consume a builder's valid,
     * still-uncredited outcomes before they reach the intentional threshold.
     * Saturated deduplication history downgrades new input to observation rather
     * than evicting live targets and permitting an early replay under custom policy.
     *
     * @return whether this event completed a substantial-activity batch
     */
    boolean recordAction(AfkActionEvidence evidence, long now) {
        Objects.requireNonNull(evidence, "evidence");
        recordLightActivity(now);
        if (lastCountedActionAt != Long.MIN_VALUE
                && now - lastCountedActionAt < policy.actionDeduplicationMillis()) {
            return false;
        }

        removeExpiredActions(now);
        if (actionDeduplicationHistory.stream().anyMatch(action -> action.matchesRecentTarget(
                evidence, now, policy.actionTargetDeduplicationMillis()))) {
            return false;
        }
        if (actionDeduplicationHistory.size() >= MAX_DEDUPLICATION_ACTIONS) {
            return false;
        }

        lastCountedActionAt = now;
        TimedAction action = new TimedAction(evidence, now);
        recentActions.addLast(action);
        if (evidence.hasAcceptedOutcome()) {
            recentOutcomes.addLast(action);
        }
        actionDeduplicationHistory.addLast(action);
        while (recentActions.size() > Math.max(MAX_PENDING_ACTIONS, policy.requiredActions())) {
            recentActions.removeFirst();
        }
        while (recentOutcomes.size() > Math.max(MAX_PENDING_ACTIONS, policy.requiredActions())) {
            recentOutcomes.removeFirst();
        }
        if (recentActions.size() < policy.requiredActions()
                && recentOutcomes.size() < policy.requiredActions()) {
            return false;
        }

        boolean intentionalBatch = recentOutcomes.size() >= policy.requiredActions();
        if (!intentionalBatch && distinctActionTargets() < policy.requiredActions()) {
            return false;
        }
        if (automationRewardLocked
                && (!intentionalBatch
                || distinctOutcomeTypes() < policy.requiredUnlockActionTypes()
                || distinctOutcomeTargets() < policy.requiredActions())) {
            return false;
        }

        markSubstantial(now);
        if (intentionalBatch) {
            lastIntentionalActivityAt = now;
            intentionalActivityWhileSuspended = suspendedAt != Long.MIN_VALUE;
            automationRewardLocked = false;
            recentOutcomes.clear();
        }
        recentActions.clear();
        return true;
    }

    void recordTrustedActivity(long now) {
        if (suspendedAt == Long.MIN_VALUE) {
            lastObservedAt = now;
        }
        markSubstantial(now);
        lastIntentionalActivityAt = now;
        intentionalActivityWhileSuspended = suspendedAt != Long.MIN_VALUE;
        automationRewardLocked = false;
        resetActionWindow();
        behaviorAnalyzer.reset();
    }

    CheckResult check(long now) {
        if (elapsed(now, lastObservedAt) >= policy.idleTimeoutMillis()) {
            return CheckResult.AFK_IDLE;
        }

        if (!automationRewardLocked
                && behaviorAnalyzer.isLikelyAutomated(now, lastIntentionalActivityAt)) {
            automationRewardLocked = true;
            resetActionWindow();
            return CheckResult.ACTIVITY_REWARD_LOCKED;
        }

        long passiveFor = elapsed(now, lastSubstantialAt);
        if (passiveFor >= policy.passiveTimeoutMillis()) {
            return CheckResult.AFK_PASSIVE;
        }
        return CheckResult.ACTIVE;
    }

    boolean isAutomaticAfkCandidate(long now) {
        return elapsed(now, lastObservedAt) >= policy.idleTimeoutMillis()
                || elapsed(now, lastSubstantialAt) >= policy.passiveTimeoutMillis();
    }

    void reset(long now, UUID worldId, double x, double y, double z) {
        lastObservedAt = now;
        lastSubstantialAt = now;
        lastIntentionalActivityAt = now;
        resetActionWindow();
        movementGestureActive = false;
        movementReleaseRequired = false;
        automationRewardLocked = false;
        substantialActivityWhileSuspended = false;
        intentionalActivityWhileSuspended = false;
        suspendedAt = Long.MIN_VALUE;
        movementGestureWorldId = worldId;
        movementGestureX = x;
        movementGestureY = y;
        movementGestureZ = z;
        resetMovementDistance();
        behaviorAnalyzer.reset();
    }

    void suspendSession(long now) {
        beginSuspension(now);
        movementGestureActive = false;
        movementReleaseRequired = false;
        resetMovementDistance();
    }

    void suspendForAfk(long now) {
        suspendForAfk(now, movementGestureActive);
    }

    void suspendForAfk(long now, boolean movementInputActive) {
        beginSuspension(now);
        movementReleaseRequired = movementInputActive;
        movementGestureActive = false;
        resetMovementDistance();
    }

    void resumeSession(long now, UUID worldId, double x, double y, double z) {
        long pausedFor = suspendedAt == Long.MIN_VALUE ? 0L : elapsed(now, suspendedAt);
        if (pausedFor > 0L) {
            lastObservedAt += pausedFor;
            lastSubstantialAt += pausedFor;
            lastIntentionalActivityAt += pausedFor;
            if (lastCountedActionAt != Long.MIN_VALUE) {
                lastCountedActionAt += pausedFor;
            }
            shiftActions(recentActions, pausedFor);
            shiftActions(recentOutcomes, pausedFor);
            shiftActions(actionDeduplicationHistory, pausedFor);
        }
        behaviorAnalyzer.resumeAfter(pausedFor, worldId, x, y, z);
        resetMovementGesture(worldId, x, y, z, false);
        finishSuspension();
    }

    void resumeFromAfk(long now, UUID worldId, double x, double y, double z) {
        long pausedFor = suspendedAt == Long.MIN_VALUE ? 0L : elapsed(now, suspendedAt);
        if (pausedFor > 0L) {
            if (!substantialActivityWhileSuspended) {
                lastSubstantialAt += pausedFor;
            }
            if (!intentionalActivityWhileSuspended) {
                lastIntentionalActivityAt += pausedFor;
            }
        }

        lastObservedAt = now;
        resetActionWindow();
        behaviorAnalyzer.reset();
        resetMovementGesture(worldId, x, y, z, movementReleaseRequired);
        finishSuspension();
    }

    /**
     * Ends automatic-AFK protection for a passive viewing context, not an input.
     * Unlike normal AFK recovery, no clock is refreshed or shifted and no reward
     * lock is cleared. A held key still needs release before it becomes a gesture.
     */
    void resumeForViewing(UUID worldId, double x, double y, double z, boolean movementInputActive) {
        resetActionWindow();
        behaviorAnalyzer.reset();
        resetMovementGesture(worldId, x, y, z, movementInputActive);
        finishSuspension();
    }

    boolean isSuspended() {
        return suspendedAt != Long.MIN_VALUE;
    }

    private void beginSuspension(long now) {
        if (suspendedAt == Long.MIN_VALUE) {
            suspendedAt = now;
            substantialActivityWhileSuspended = false;
            intentionalActivityWhileSuspended = false;
        }
    }

    private void resetMovementGesture(
            UUID worldId,
            double x,
            double y,
            double z,
            boolean releaseRequired
    ) {
        movementGestureActive = false;
        movementReleaseRequired = releaseRequired;
        movementGestureWorldId = worldId;
        movementGestureX = x;
        movementGestureY = y;
        movementGestureZ = z;
        resetMovementDistance();
    }

    private void resetMovementDistance() {
        movementDistanceSinceSubstantial = 0.0D;
        lastMovementAt = Long.MIN_VALUE;
    }

    private void finishSuspension() {
        suspendedAt = Long.MIN_VALUE;
        substantialActivityWhileSuspended = false;
        intentionalActivityWhileSuspended = false;
    }

    private void markSubstantial(long now) {
        lastSubstantialAt = now;
        substantialActivityWhileSuspended = suspendedAt != Long.MIN_VALUE;
    }

    private void resetActionWindow() {
        lastCountedActionAt = Long.MIN_VALUE;
        recentActions.clear();
        recentOutcomes.clear();
        actionDeduplicationHistory.clear();
    }

    private void removeExpiredActions(long now) {
        while (!recentActions.isEmpty()
                && elapsed(now, recentActions.peekFirst().at) > policy.passiveTimeoutMillis()) {
            recentActions.removeFirst();
        }
        while (!recentOutcomes.isEmpty()
                && elapsed(now, recentOutcomes.peekFirst().at) > policy.passiveTimeoutMillis()) {
            recentOutcomes.removeFirst();
        }
        while (!actionDeduplicationHistory.isEmpty()
                && elapsed(now, actionDeduplicationHistory.peekFirst().at)
                >= policy.actionTargetDeduplicationMillis()) {
            actionDeduplicationHistory.removeFirst();
        }
    }

    private int distinctOutcomeTypes() {
        EnumSet<AfkActionEvidence.Type> types = EnumSet.noneOf(AfkActionEvidence.Type.class);
        for (TimedAction action : recentOutcomes) {
            types.add(action.evidence.type());
        }
        return types.size();
    }

    private long distinctActionTargets() {
        return recentActions.stream()
                .map(action -> action.evidence.targetKey())
                .distinct()
                .count();
    }

    private long distinctOutcomeTargets() {
        return recentOutcomes.stream()
                .map(action -> action.evidence.targetKey())
                .distinct()
                .count();
    }

    private static void shiftActions(Deque<TimedAction> actions, long pausedFor) {
        Deque<TimedAction> shifted = new ArrayDeque<>(actions.size());
        for (TimedAction action : actions) {
            shifted.addLast(action.shiftedBy(pausedFor));
        }
        actions.clear();
        actions.addAll(shifted);
    }

    private double distanceSquared(double x, double y, double z) {
        double dx = movementGestureX - x;
        double dy = movementGestureY - y;
        double dz = movementGestureZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void updateMovementPosition(UUID worldId, double x, double y, double z) {
        movementGestureWorldId = worldId;
        movementGestureX = x;
        movementGestureY = y;
        movementGestureZ = z;
    }

    private static long elapsed(long now, long then) {
        return Math.max(0L, now - then);
    }

    private record TimedAction(AfkActionEvidence evidence, long at) {

        private boolean matchesRecentTarget(AfkActionEvidence other, long now, long deduplicationMillis) {
            return evidence.targetKey().equals(other.targetKey())
                    && elapsed(now, at) < deduplicationMillis;
        }

        private TimedAction shiftedBy(long delta) {
            return new TimedAction(evidence, at + delta);
        }
    }
}
