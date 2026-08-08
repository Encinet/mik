package org.encinet.mik.module.afk;

final class AutomaticAfkGate {

    private static final long UNSET = Long.MIN_VALUE;

    private final long candidateGraceMillis;
    private final long reentryCooldownMillis;
    private long candidateSince = UNSET;
    private long candidateActivityVersion = UNSET;
    private long cooldownUntil = UNSET;

    AutomaticAfkGate(long candidateGraceMillis, long reentryCooldownMillis) {
        if (candidateGraceMillis < 0L || reentryCooldownMillis < 0L) {
            throw new IllegalArgumentException("AFK transition durations cannot be negative");
        }
        this.candidateGraceMillis = candidateGraceMillis;
        this.reentryCooldownMillis = reentryCooldownMillis;
    }

    boolean shouldEnter(long now, boolean eligible, long activityVersion) {
        if (isCoolingDown(now) || !eligible) {
            clearCandidate();
            return false;
        }

        if (candidateSince == UNSET || candidateActivityVersion != activityVersion) {
            candidateSince = now;
            candidateActivityVersion = activityVersion;
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
        candidateActivityVersion = UNSET;
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
