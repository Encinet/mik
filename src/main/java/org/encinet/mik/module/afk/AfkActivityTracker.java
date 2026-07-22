package org.encinet.mik.module.afk;

import java.util.Objects;
import java.util.UUID;

final class AfkActivityTracker {

    static final long IDLE_TIMEOUT_MILLIS = 3L * 60L * 1_000L;
    static final long PASSIVE_TIMEOUT_MILLIS = 10L * 60L * 1_000L;
    static final double SUBSTANTIAL_MOVEMENT_DISTANCE = 8.0D;
    static final long ACTION_DEDUPLICATION_MILLIS = 1_000L;

    enum CheckResult {
        ACTIVE,
        AFK_IDLE,
        AFK_PASSIVE
    }

    private long lastObservedAt;
    private long lastSubstantialAt;
    private long lastCountedActionAt = Long.MIN_VALUE;
    private long thirdLatestActionAt = Long.MIN_VALUE;
    private long secondLatestActionAt = Long.MIN_VALUE;
    private long latestActionAt = Long.MIN_VALUE;
    private boolean movementGestureActive;
    private boolean movementGestureCredited;
    private boolean movementReleaseRequired;
    private UUID movementGestureWorldId;
    private double movementGestureX;
    private double movementGestureY;
    private double movementGestureZ;

    AfkActivityTracker(long now, UUID worldId, double x, double y, double z) {
        reset(now, worldId, x, y, z);
    }

    void recordLightActivity(long now) {
        lastObservedAt = now;
    }

    void recordMovementInput(
            boolean active,
            UUID worldId,
            double x,
            double y,
            double z,
            long now
    ) {
        if (!active) {
            movementGestureActive = false;
            movementGestureCredited = false;
            movementReleaseRequired = false;
            return;
        }

        recordLightActivity(now);
        if (movementGestureActive || movementReleaseRequired) {
            return;
        }
        movementGestureActive = true;
        movementGestureCredited = false;
        movementGestureWorldId = worldId;
        movementGestureX = x;
        movementGestureY = y;
        movementGestureZ = z;
    }

    boolean recordMovement(UUID worldId, double x, double y, double z, long now) {
        if (!movementGestureActive) {
            return false;
        }
        recordLightActivity(now);
        if (movementGestureCredited) {
            return false;
        }
        if (!Objects.equals(movementGestureWorldId, worldId)
                || distanceSquared(x, y, z) >= square(SUBSTANTIAL_MOVEMENT_DISTANCE)) {
            markSubstantial(now);
            movementGestureCredited = true;
            return true;
        }
        return false;
    }

    boolean hasActiveMovementGesture() {
        return movementGestureActive;
    }

    void suspendMovementGesture() {
        movementReleaseRequired = movementGestureActive;
        movementGestureActive = false;
        movementGestureCredited = false;
    }

    boolean recordAction(long now) {
        recordLightActivity(now);
        if (lastCountedActionAt != Long.MIN_VALUE
                && now - lastCountedActionAt < ACTION_DEDUPLICATION_MILLIS) {
            return false;
        }

        lastCountedActionAt = now;
        thirdLatestActionAt = secondLatestActionAt;
        secondLatestActionAt = latestActionAt;
        latestActionAt = now;
        if (thirdLatestActionAt == Long.MIN_VALUE
                || elapsed(now, thirdLatestActionAt) > PASSIVE_TIMEOUT_MILLIS) {
            return false;
        }

        markSubstantial(now);
        return true;
    }

    CheckResult check(long now) {
        if (elapsed(now, lastObservedAt) >= IDLE_TIMEOUT_MILLIS) {
            return CheckResult.AFK_IDLE;
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
        lastCountedActionAt = Long.MIN_VALUE;
        thirdLatestActionAt = Long.MIN_VALUE;
        secondLatestActionAt = Long.MIN_VALUE;
        latestActionAt = Long.MIN_VALUE;
        movementGestureActive = false;
        movementGestureCredited = false;
        movementReleaseRequired = false;
        movementGestureWorldId = worldId;
        movementGestureX = x;
        movementGestureY = y;
        movementGestureZ = z;
    }

    private void markSubstantial(long now) {
        lastSubstantialAt = now;
    }

    private double distanceSquared(double x, double y, double z) {
        double dx = movementGestureX - x;
        double dy = movementGestureY - y;
        double dz = movementGestureZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double square(double value) {
        return value * value;
    }

    private static long elapsed(long now, long then) {
        return Math.max(0L, now - then);
    }
}
