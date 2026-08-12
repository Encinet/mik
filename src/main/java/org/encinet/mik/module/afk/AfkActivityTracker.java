package org.encinet.mik.module.afk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

final class AfkActivityTracker {

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
            movementDistanceSinceSubstantial = 0.0D;
            return false;
        }

        recordLightActivity(now);
        if (movementGestureActive || movementReleaseRequired) {
            return false;
        }
        movementGestureActive = true;
        movementGestureWorldId = worldId;
        movementGestureX = x;
        movementGestureY = y;
        movementGestureZ = z;
        movementDistanceSinceSubstantial = 0.0D;
        return true;
    }

    boolean recordMovement(UUID worldId, double x, double y, double z, long now) {
        if (!movementGestureActive) {
            return false;
        }
        recordLightActivity(now);
        if (!Objects.equals(movementGestureWorldId, worldId)) {
            updateMovementPosition(worldId, x, y, z);
            movementDistanceSinceSubstantial = 0.0D;
            markSubstantial(now);
            return true;
        }

        movementDistanceSinceSubstantial += Math.sqrt(distanceSquared(x, y, z));
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
        if (suspendedAt == Long.MIN_VALUE) {
            behaviorAnalyzer.record(worldId, x, y, z, yaw, movementGestureActive, now);
        }
    }

    void suspendMovementGesture() {
        movementReleaseRequired = movementGestureActive;
        movementGestureActive = false;
        movementDistanceSinceSubstantial = 0.0D;
    }

    boolean recordAction(AfkActionEvidence evidence, long now) {
        recordLightActivity(now);
        if (lastCountedActionAt != Long.MIN_VALUE
                && now - lastCountedActionAt < policy.actionDeduplicationMillis()) {
            return false;
        }

        removeExpiredActions(now);
        if (recentActions.stream().anyMatch(action -> action.matchesRecentTarget(
                evidence, now, policy.actionTargetDeduplicationMillis()))) {
            return false;
        }

        lastCountedActionAt = now;
        recentActions.addLast(new TimedAction(evidence, now));
        if (recentActions.size() < policy.requiredActions()) {
            return false;
        }

        if (automationRewardLocked
                && (distinctActionTypes() < policy.requiredUnlockActionTypes()
                || distinctActionTargets() < policy.requiredActions())) {
            return false;
        }

        markSubstantial(now);
        lastIntentionalActivityAt = now;
        intentionalActivityWhileSuspended = suspendedAt != Long.MIN_VALUE;
        automationRewardLocked = false;
        behaviorAnalyzer.reset();
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
            behaviorAnalyzer.reset();
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
        movementDistanceSinceSubstantial = 0.0D;
        behaviorAnalyzer.reset();
    }

    void suspendSession(long now) {
        beginSuspension(now);
        movementGestureActive = false;
        movementReleaseRequired = false;
        movementDistanceSinceSubstantial = 0.0D;
    }

    void suspendForAfk(long now) {
        suspendForAfk(now, movementGestureActive);
    }

    void suspendForAfk(long now, boolean movementInputActive) {
        beginSuspension(now);
        movementReleaseRequired = movementInputActive;
        movementGestureActive = false;
        movementDistanceSinceSubstantial = 0.0D;
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
            if (!recentActions.isEmpty()) {
                Deque<TimedAction> shifted = new ArrayDeque<>(recentActions.size());
                for (TimedAction action : recentActions) {
                    shifted.addLast(action.shiftedBy(pausedFor));
                }
                recentActions.clear();
                recentActions.addAll(shifted);
            }
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
        movementDistanceSinceSubstantial = 0.0D;
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
    }

    private void removeExpiredActions(long now) {
        while (!recentActions.isEmpty()
                && elapsed(now, recentActions.peekFirst().at) > policy.passiveTimeoutMillis()) {
            recentActions.removeFirst();
        }
    }

    private int distinctActionTypes() {
        EnumSet<AfkActionEvidence.Type> types = EnumSet.noneOf(AfkActionEvidence.Type.class);
        for (TimedAction action : recentActions) {
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
