package org.encinet.mik.module.afk;

final class AutomaticAfkGate {

    private static final long UNSET = Long.MIN_VALUE;

    private final long candidateGraceMillis;
    private final long reentryCooldownMillis;
    private long candidateSince = UNSET;
    private long cooldownUntil = UNSET;

    AutomaticAfkGate(long candidateGraceMillis, long reentryCooldownMillis) {
        if (candidateGraceMillis < 0L || reentryCooldownMillis < 0L) {
            throw new IllegalArgumentException("AFK transition durations cannot be negative");
        }
        this.candidateGraceMillis = candidateGraceMillis;
        this.reentryCooldownMillis = reentryCooldownMillis;
    }

    boolean shouldEnter(long now, boolean inactivityReached) {
        if (isCoolingDown(now) || !inactivityReached) {
            clearCandidate();
            return false;
        }

        if (candidateSince == UNSET) {
            candidateSince = now;
            if (candidateGraceMillis == 0L) {
                clearCandidate();
                return true;
            }
            return false;
        }

        if (elapsed(now, candidateSince) < candidateGraceMillis) {
            return false;
        }
        clearCandidate();
        return true;
    }

    void reset() {
        cooldownUntil = UNSET;
        clearCandidate();
    }

    void recordExit(long now) {
        cooldownUntil = deadline(now, reentryCooldownMillis);
        clearCandidate();
    }

    private boolean isCoolingDown(long now) {
        if (cooldownUntil == UNSET) {
            return false;
        }
        if (now >= cooldownUntil) {
            cooldownUntil = UNSET;
            return false;
        }
        return true;
    }

    private void clearCandidate() {
        candidateSince = UNSET;
    }

    private static long elapsed(long now, long then) {
        return Math.max(0L, now - then);
    }

    private static long deadline(long now, long delay) {
        if (delay > 0L && now > Long.MAX_VALUE - delay) {
            return Long.MAX_VALUE;
        }
        return now + delay;
    }
}
