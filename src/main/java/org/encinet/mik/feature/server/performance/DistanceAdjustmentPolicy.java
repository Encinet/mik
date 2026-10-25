package org.encinet.mik.feature.server.performance;

/** Monotonic-time distance control: fast relief, gradual recovery and bounded reversal backoff. */
final class DistanceAdjustmentPolicy {

    private static final long STABLE_DECAY_MILLIS = 300_000L;
    private static final int MAX_PENALTY = 3;

    private final long baseCooldownMillis;
    private final long recoveryConfirmMillis;
    private final int recoveryStep;
    private int render;
    private int simulation;
    private int direction;
    private int penalty;
    private long lastChange;
    private long decayAt;
    private long recoverySince;
    private boolean changed;
    private boolean recovering;

    DistanceAdjustmentPolicy(int render, int simulation, long baseCooldownMillis,
                             long recoveryConfirmMillis, int recoveryStep) {
        this.render = render;
        this.simulation = simulation;
        this.baseCooldownMillis = baseCooldownMillis;
        this.recoveryConfirmMillis = recoveryConfirmMillis;
        this.recoveryStep = recoveryStep;
    }

    PlayerViewDistancePolicy.EffectiveDistances update(long now, int targetRender,
                                                       int targetSimulation, boolean urgentReduction) {
        if (changed && now - decayAt >= STABLE_DECAY_MILLIS) {
            long periods = (now - decayAt) / STABLE_DECAY_MILLIS;
            penalty = Math.max(0, penalty - (int) Math.min(MAX_PENALTY, periods));
            decayAt += periods * STABLE_DECAY_MILLIS;
        }
        boolean lowering = targetRender < render || targetSimulation < simulation;
        boolean raising = targetRender > render || targetSimulation > simulation;
        if (lowering) {
            recovering = false;
            if (urgentReduction || ready(now)) {
                // A mixed target must not increase either distance while shedding load.
                change(now, Math.min(render, targetRender), Math.min(simulation, targetSimulation), -1);
            }
        } else if (raising) {
            if (!recovering) {
                recovering = true;
                recoverySince = now;
            }
            if (ready(now) && now - recoverySince >= recoveryConfirmMillis) {
                change(now, Math.min(targetRender, render + recoveryStep),
                        Math.min(targetSimulation, simulation + recoveryStep), 1);
            }
        } else {
            recovering = false;
        }
        return new PlayerViewDistancePolicy.EffectiveDistances(render, simulation);
    }

    private boolean ready(long now) {
        return !changed || now - lastChange >= baseCooldownMillis * (1L << penalty);
    }

    private void change(long now, int nextRender, int nextSimulation, int nextDirection) {
        // Deliberate steps in the same direction are recovery, not oscillation.
        if (changed && direction != nextDirection && now - lastChange < STABLE_DECAY_MILLIS) {
            penalty = Math.min(MAX_PENALTY, penalty + 1);
        }
        render = nextRender;
        simulation = nextSimulation;
        direction = nextDirection;
        lastChange = now;
        decayAt = now;
        changed = true;
    }
}
