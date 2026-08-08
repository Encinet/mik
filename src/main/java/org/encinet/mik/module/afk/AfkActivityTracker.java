package org.encinet.mik.module.afk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

final class AfkActivityTracker {

    static final long IDLE_TIMEOUT_MILLIS = 3L * 60L * 1_000L;
    static final long PASSIVE_TIMEOUT_MILLIS = 10L * 60L * 1_000L;
    static final double SUBSTANTIAL_MOVEMENT_DISTANCE = 8.0D;
    static final long ACTION_DEDUPLICATION_MILLIS = 1_000L;
    static final long ACTION_TARGET_DEDUPLICATION_MILLIS = 30_000L;
    static final long MOVEMENT_ONLY_REWARD_TIMEOUT_MILLIS = 15L * 60L * 1_000L;
    static final int REQUIRED_ACTIONS = 3;
    static final int REQUIRED_UNLOCK_ACTION_TYPES = 2;

    enum CheckResult {
        ACTIVE,
        AFK_IDLE,
        AFK_PASSIVE,
        ACTIVITY_REWARD_LOCKED
    }

    private final AfkBehaviorAnalyzer behaviorAnalyzer = new AfkBehaviorAnalyzer();
    private final Deque<TimedAction> recentActions = new ArrayDeque<>();
    private long lastObservedAt;
    private long lastSubstantialAt;
    private long lastIntentionalActivityAt;
    private long activityVersion;
    private long lastCountedActionAt = Long.MIN_VALUE;
    private boolean movementGestureActive;
    private boolean movementReleaseRequired;
    private boolean automationRewardLocked;
    private boolean intentionalActivityWhileSuspended;
    private long suspendedAt = Long.MIN_VALUE;
    private UUID movementGestureWorldId;
    private double movementGestureX;
    private double movementGestureY;
    private double movementGestureZ;
    private double movementDistanceSinceSubstantial;

    AfkActivityTracker(long now, UUID worldId, double x, double y, double z) {
        reset(now, worldId, x, y, z);
    }

    void recordLightActivity(long now) {
        if (suspendedAt == Long.MIN_VALUE) {
            lastObservedAt = now;
            activityVersion++;
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
        if (movementDistanceSinceSubstantial >= SUBSTANTIAL_MOVEMENT_DISTANCE) {
            movementDistanceSinceSubstantial %= SUBSTANTIAL_MOVEMENT_DISTANCE;
            markSubstantial(now);
            return true;
        }
        return false;
    }

    boolean canMovementClearAfk() {
        return movementGestureActive;
    }

    long activityVersion() {
        return activityVersion;
    }

    boolean isActivityEligible(long now) {
        return !automationRewardLocked
                && elapsed(now, lastObservedAt) < IDLE_TIMEOUT_MILLIS
                && elapsed(now, lastIntentionalActivityAt) < MOVEMENT_ONLY_REWARD_TIMEOUT_MILLIS;
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
                && now - lastCountedActionAt < ACTION_DEDUPLICATION_MILLIS) {
            return false;
        }

        removeExpiredActions(now);
        if (recentActions.stream().anyMatch(action -> action.matchesRecentTarget(evidence, now))) {
            return false;
        }

        lastCountedActionAt = now;
        recentActions.addLast(new TimedAction(evidence, now));
        if (recentActions.size() < REQUIRED_ACTIONS) {
            return false;
        }

        if (automationRewardLocked
                && (distinctActionTypes() < REQUIRED_UNLOCK_ACTION_TYPES
                || distinctActionTargets() < REQUIRED_ACTIONS)) {
            return false;
        }

        markSubstantial(now);
        lastIntentionalActivityAt = now;
        intentionalActivityWhileSuspended = suspendedAt != Long.MIN_VALUE;
        automationRewardLocked = false;
        behaviorAnalyzer.reset();
        return true;
    }

    CheckResult check(long now) {
        if (elapsed(now, lastObservedAt) >= IDLE_TIMEOUT_MILLIS) {
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
        if (passiveFor >= PASSIVE_TIMEOUT_MILLIS) {
            return CheckResult.AFK_PASSIVE;
        }
        return CheckResult.ACTIVE;
    }

    void reset(long now, UUID worldId, double x, double y, double z) {
        lastObservedAt = now;
        lastSubstantialAt = now;
        lastIntentionalActivityAt = now;
        activityVersion = 0L;
        resetActionWindow();
        movementGestureActive = false;
        movementReleaseRequired = false;
        automationRewardLocked = false;
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
        if (pausedFor > 0L && !intentionalActivityWhileSuspended) {
            lastIntentionalActivityAt += pausedFor;
        }

        lastObservedAt = now;
        lastSubstantialAt = now;
        activityVersion++;
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
        intentionalActivityWhileSuspended = false;
    }

    private void markSubstantial(long now) {
        lastSubstantialAt = now;
    }

    private void resetActionWindow() {
        lastCountedActionAt = Long.MIN_VALUE;
        recentActions.clear();
    }

    private void removeExpiredActions(long now) {
        while (!recentActions.isEmpty()
                && elapsed(now, recentActions.peekFirst().at) > PASSIVE_TIMEOUT_MILLIS) {
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

        private boolean matchesRecentTarget(AfkActionEvidence other, long now) {
            return evidence.targetKey().equals(other.targetKey())
                    && elapsed(now, at) < ACTION_TARGET_DEDUPLICATION_MILLIS;
        }

        private TimedAction shiftedBy(long delta) {
            return new TimedAction(evidence, at + delta);
        }
    }
}
