package org.encinet.mik.feature.server.performance;

/** Feedback controller for a measured host egress budget. Confined to the server thread. */
final class NetworkEgressThrottlePolicy {
    static final double MAX_SAMPLE_SECONDS = 5.0D;
    private static final double AFK_PRESSURE = 0.70D;
    private static final double ACTIVE_PRESSURE = 0.85D;
    private static final double TARGET_PRESSURE = 0.75D;
    private static final double RECOVERY_PRESSURE = 0.65D;
    private static final double CRITICAL_PRESSURE = 0.98D;
    private static final double CONFIRM_SECONDS = 2.0D;
    private static final double SETTLE_SECONDS = 5.0D;
    private static final double TARGETED_SETTLE_SECONDS = 10.0D;
    private static final double RECOVERY_SECONDS = 15.0D;
    private static final double PROBE_SECONDS = 10.0D;
    private static final double MIN_ACTIVE_RATIO = 3.0D / 32.0D;
    private static final double MIN_AFK_RATIO = 2.0D / 32.0D;
    private static final double INITIAL_RECOVERY_STEP = 0.05D;

    static final Limit OPEN = new Limit(1.0D, 1.0D);

    private Limit limit = OPEN;
    private Limit beforeProbe;
    private double fastPressure = -1.0D;
    private double slowPressure = -1.0D;
    private double highSeconds;
    private double criticalSeconds;
    private double lowSeconds;
    private double sinceChange;
    private double probeSeconds;
    private double recoveryDelay = RECOVERY_SECONDS;
    private double recoveryStep = INITIAL_RECOVERY_STEP;
    private boolean explorersPresent;
    private boolean targetedStageObserved;
    private double activeObservationSeconds = SETTLE_SECONDS;

    Limit update(double pressure, double elapsedSeconds, boolean afkReliefAvailable) {
        if (!Double.isFinite(pressure) || pressure < 0.0D
                || !Double.isFinite(elapsedSeconds) || elapsedSeconds <= 0.0D
                || elapsedSeconds > MAX_SAMPLE_SECONDS) {
            suspend();
            return limit;
        }

        fastPressure = smooth(fastPressure, pressure, elapsedSeconds, 2.0D);
        slowPressure = smooth(slowPressure, pressure, elapsedSeconds, 10.0D);
        sinceChange += elapsedSeconds;
        highSeconds = pressure >= ACTIVE_PRESSURE && fastPressure >= ACTIVE_PRESSURE
                ? highSeconds + elapsedSeconds : 0.0D;
        criticalSeconds = pressure >= CRITICAL_PRESSURE
                ? criticalSeconds + elapsedSeconds : 0.0D;
        double recoveryPressure = Math.max(pressure, Math.max(fastPressure, slowPressure));
        lowSeconds = recoveryPressure < RECOVERY_PRESSURE ? lowSeconds + elapsedSeconds : 0.0D;

        if (beforeProbe != null) {
            boolean afkProbe = beforeProbe.afkRatio() != limit.afkRatio();
            if ((afkProbe && pressure >= AFK_PRESSURE && fastPressure >= AFK_PRESSURE)
                    || highSeconds >= CONFIRM_SECONDS || criticalSeconds >= CONFIRM_SECONDS) {
                Limit rollback = beforeProbe;
                beforeProbe = null;
                recoveryDelay = Math.min(120.0D, recoveryDelay * 2.0D);
                recoveryStep = Math.max(0.01D, recoveryStep * 0.5D);
                changeTo(rollback);
                return limit;
            }
            probeSeconds += elapsedSeconds;
            if (probeSeconds >= PROBE_SECONDS) {
                beforeProbe = null;
                recoveryDelay = Math.max(RECOVERY_SECONDS, recoveryDelay * 0.8D);
                recoveryStep = Math.min(INITIAL_RECOVERY_STEP, recoveryStep * 1.25D);
            }
        }

        if (pressure >= AFK_PRESSURE && (fastPressure >= AFK_PRESSURE || criticalSeconds >= CONFIRM_SECONDS)
                && limit.afkRatio() > MIN_AFK_RATIO) {
            // Applying the AFK limit is a distinct action. Never spend the same sample on active players.
            changeTo(new Limit(limit.activeRatio(), MIN_AFK_RATIO));
            targetedStageObserved = false;
            activeObservationSeconds = SETTLE_SECONDS;
            if (!afkReliefAvailable) {
                sinceChange = SETTLE_SECONDS;
            }
            return limit;
        }

        boolean critical = criticalSeconds >= CONFIRM_SECONDS;
        if (highSeconds >= CONFIRM_SECONDS && sinceChange >= SETTLE_SECONDS
                && explorersPresent && !targetedStageObserved && !critical) {
            // Give identified high-demand explorers one observation window before global active limits.
            targetedStageObserved = true;
            // Include the explorer controller's 5-second confirmation and 5-second response window.
            activeObservationSeconds = TARGETED_SETTLE_SECONDS;
            clearEvidence();
            return limit;
        }
        if ((highSeconds >= CONFIRM_SECONDS || critical) && (sinceChange >= activeObservationSeconds || critical)) {
            // Area is only a first estimate of chunk demand. Subsequent measurements correct it.
            double areaFactor = Math.clamp(TARGET_PRESSURE / fastPressure, 0.50D, 0.90D);
            double nextRatio = Math.max(MIN_ACTIVE_RATIO, limit.activeRatio() * Math.sqrt(areaFactor));
            changeTo(new Limit(nextRatio, Math.min(limit.afkRatio(), MIN_AFK_RATIO)));
            activeObservationSeconds = SETTLE_SECONDS;
            return limit;
        }

        if (beforeProbe == null && limit.throttled() && lowSeconds >= recoveryDelay
                && sinceChange >= SETTLE_SECONDS) {
            boolean recoverActive = limit.activeRatio() < 1.0D;
            double currentRatio = recoverActive ? limit.activeRatio() : limit.afkRatio();
            // Bound the probe assuming all observed traffic scales with the square of the radius.
            double headroomRatio = currentRatio * Math.sqrt(TARGET_PRESSURE / Math.max(0.01D, recoveryPressure));
            double nextRatio = Math.min(1.0D, Math.min(currentRatio + recoveryStep, headroomRatio));
            if (nextRatio > currentRatio) {
                beforeProbe = limit;
                probeSeconds = 0.0D;
                changeTo(recoverActive
                        ? new Limit(nextRatio, limit.afkRatio())
                        : new Limit(1.0D, nextRatio));
            }
        }
        return limit;
    }

    /** Missing or stale observations provide neither escalation nor recovery evidence. */
    void suspend() {
        fastPressure = -1.0D;
        slowPressure = -1.0D;
        clearEvidence();
        probeSeconds = 0.0D;
    }

    Limit reset() {
        limit = OPEN;
        beforeProbe = null;
        recoveryDelay = RECOVERY_SECONDS;
        recoveryStep = INITIAL_RECOVERY_STEP;
        targetedStageObserved = false;
        activeObservationSeconds = SETTLE_SECONDS;
        suspend();
        return limit;
    }

    double fastPressure() {
        return fastPressure;
    }

    double slowPressure() {
        return slowPressure;
    }

    double recoveryDelay() {
        return recoveryDelay;
    }

    Limit limit() {
        return limit;
    }

    void observeExplorers(boolean present) {
        explorersPresent = present;
    }

    double explorationPressure() {
        return limit.afkThrottled() && (limit.activeRatio() < 1.0D || targetedStageObserved || sinceChange >= SETTLE_SECONDS)
                ? fastPressure : Math.min(Math.nextDown(ACTIVE_PRESSURE), fastPressure);
    }

    private void changeTo(Limit next) {
        limit = next;
        clearEvidence();
    }

    private void clearEvidence() {
        highSeconds = 0.0D;
        criticalSeconds = 0.0D;
        lowSeconds = 0.0D;
        sinceChange = 0.0D;
    }

    private static double smooth(double previous, double current, double elapsedSeconds, double timeConstant) {
        return previous < 0.0D ? current
                : previous + (current - previous) * -Math.expm1(-elapsedSeconds / timeConstant);
    }

    record Limit(double activeRatio, double afkRatio) {
        Limit {
            if (!Double.isFinite(activeRatio) || !Double.isFinite(afkRatio)
                    || activeRatio < MIN_ACTIVE_RATIO || activeRatio > 1.0D
                    || afkRatio < MIN_AFK_RATIO || afkRatio > activeRatio) {
                throw new IllegalArgumentException("Invalid send-distance ratios");
            }
        }

        boolean throttled() {
            return activeRatio < 1.0D || afkThrottled();
        }

        boolean afkThrottled() {
            return afkRatio < 1.0D;
        }

        String stage() {
            return activeRatio < 1.0D ? "ACTIVE" : afkThrottled() ? "AFK_ONLY" : "OPEN";
        }

        int sendDistance(int originalDistance, boolean afk) {
            return PlayerViewDistancePolicy.scaledDistance(originalDistance, afk ? afkRatio : activeRatio, afk ? 2 : 3);
        }
    }
}
