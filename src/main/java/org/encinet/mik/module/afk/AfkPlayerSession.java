package org.encinet.mik.module.afk;

import java.util.UUID;

/**
 * Owns all automatic-AFK state for one online player session.
 *
 * <p>The activity tracker decides whether inactivity has been reached. The
 * transition gate only debounces that decision and applies the short re-entry
 * cooldown. Keeping both objects under one owner prevents their lifecycles from
 * drifting apart in parallel maps.</p>
 */
final class AfkPlayerSession {

    private final AfkActivityTracker activity;
    private final AutomaticAfkGate automaticEntry;

    AfkPlayerSession(long now, UUID worldId, double x, double y, double z) {
        this(AfkPolicy.DEFAULT, now, worldId, x, y, z);
    }

    AfkPlayerSession(
            AfkPolicy policy,
            long now,
            UUID worldId,
            double x,
            double y,
            double z
    ) {
        this.activity = new AfkActivityTracker(policy, now, worldId, x, y, z);
        this.automaticEntry = new AutomaticAfkGate(
                policy.automaticEntryGraceMillis(), policy.automaticReentryCooldownMillis());
    }

    AfkActivityTracker activity() {
        return activity;
    }

    AutomaticCheck checkAutomaticAfk(long now, boolean safeToEnter) {
        return checkAutomaticAfk(now, safeToEnter, false);
    }

    AutomaticCheck checkAutomaticAfk(
            long now,
            boolean safeToEnter,
            boolean automaticAfkSuppressed
    ) {
        AfkActivityTracker.CheckResult result = activity.check(now);
        boolean shouldEnter = automaticEntry.shouldEnter(
                now, !automaticAfkSuppressed
                        && safeToEnter
                        && result.automaticAfkCandidate());
        return new AutomaticCheck(
                result, safeToEnter, automaticAfkSuppressed, shouldEnter);
    }

    boolean isActivityEligible(long now) {
        return activity.isActivityEligible(now);
    }

    boolean isAutomaticAfkCandidate(long now) {
        return activity.isAutomaticAfkCandidate(now);
    }

    void enterAfk(long now, boolean movementInputActive) {
        automaticEntry.reset();
        activity.suspendForAfk(now, movementInputActive);
    }

    void exitAfk(long now, UUID worldId, double x, double y, double z) {
        automaticEntry.recordExit(now);
        activity.resumeFromAfk(now, worldId, x, y, z);
    }

    /** A viewing context removes protection without pretending the player provided input. */
    void exitAfkForViewing(UUID worldId, double x, double y, double z, boolean movementInputActive) {
        automaticEntry.reset();
        activity.resumeForViewing(worldId, x, y, z, movementInputActive);
    }

    void suspendForDisconnect(long now) {
        automaticEntry.reset();
        activity.suspendSession(now);
    }

    void resumeAfterReconnect(long now, UUID worldId, double x, double y, double z) {
        automaticEntry.reset();
        activity.resumeSession(now, worldId, x, y, z);
    }

    void cancelAutomaticEntry() {
        automaticEntry.reset();
    }

    record AutomaticCheck(
            AfkActivityTracker.CheckResult result,
            boolean safeToEnter,
            boolean automaticAfkSuppressed,
            boolean shouldEnterAfk
    ) {
    }
}
