package org.encinet.mik.feature.server.performance;

/** Per-explorer budgets, independent of Bukkit and of the global egress controller. */
final class ChunkExplorationPolicy {
    private static final double NETWORK_PRESSURE_THRESHOLD = 0.85D;
    private static final double SERVER_PRESSURE_MSPT = 45.0D;
    private static final double RECOVERY_SECONDS = 5.0D;
    private static final double RECOVERY_STEP = 0.10D;
    static final Limit OPEN = new Limit(1.0D, 1.0D, 1.0f);
    private Limit limit = OPEN;
    private Limit beforeProbe;
    private double sentRate = -1.0D;
    private double loadRate = -1.0D;
    private double highSeconds;
    private double lowSeconds;
    private double sinceChange;
    private double probeSeconds;
    private double recoveryDelay = RECOVERY_SECONDS;

    Limit update(Observation observation, double seconds, Resources resources) {
        if (!Double.isFinite(seconds) || seconds <= 0.0D || seconds > 5.0D) {
            suspend();
            return limit;
        }
        double weight = -Math.expm1(-seconds / 3.0D);
        sentRate = smooth(sentRate, observation.sentPerSecond(), weight);
        loadRate = smooth(loadRate, observation.unloadedPerSecond(), weight);
        double networkPressure = resources.networkPressure();
        double mspt = resources.mspt();
        boolean networkBusy = Double.isFinite(networkPressure) && networkPressure >= NETWORK_PRESSURE_THRESHOLD;
        boolean serverBusy = Double.isFinite(mspt) && mspt >= SERVER_PRESSURE_MSPT;
        double sendBudget = Math.max(8.0D, 64.0D / Math.max(1, resources.senders()));
        double loadBudget = Math.max(4.0D, 24.0D / Math.max(1, resources.loaders()));
        if (networkBusy) sendBudget *= Math.clamp(0.75D / networkPressure, 0.5D, 1.0D);
        if (serverBusy) loadBudget *= Math.clamp(SERVER_PRESSURE_MSPT / mspt, 0.5D, 1.0D);
        // Demand attributes an existing bottleneck; it must never create one while resources are healthy.
        double sendPressure = networkBusy ? sentRate / sendBudget : 0.0D;
        double loadPressure = serverBusy ? loadRate / loadBudget : 0.0D;
        double pressure = Math.max(sendPressure, loadPressure);
        boolean movingFast = observation.blocksPerSecond() >= 24.0D;
        // Raw demand must also remain high; a decaying EWMA after stopping is not new pressure.
        boolean high = pressure >= 1.15D && (movingFast || limit.throttled())
                && Math.max(networkBusy ? observation.sentPerSecond() / sendBudget : 0.0D,
                        serverBusy ? observation.unloadedPerSecond() / loadBudget : 0.0D) >= 1.15D;
        highSeconds = high ? highSeconds + seconds : 0.0D;
        // Continuing to explore at a high chunk rate cannot block recovery of healthy resources.
        boolean headroom = networkPressure < 0.75D && Double.isFinite(mspt) && mspt < 40.0D;
        lowSeconds = headroom ? lowSeconds + seconds : 0.0D;
        sinceChange += seconds;

        if (beforeProbe != null) {
            if (highSeconds >= 2.0D) {
                limit = beforeProbe;
                beforeProbe = null;
                recoveryDelay = Math.min(120.0D, recoveryDelay * 2.0D);
                clearEvidence();
                return limit;
            }
            probeSeconds += seconds;
            if (probeSeconds >= 10.0D) {
                beforeProbe = null;
                recoveryDelay = Math.max(RECOVERY_SECONDS, recoveryDelay * 0.8D);
            }
        }

        boolean critical = networkPressure >= 0.98D || mspt >= 60.0D;
        if (highSeconds >= (critical ? 2.0D : 5.0D) && sinceChange >= (critical ? 2.0D : 5.0D)) {
            double sendFactor = sendPressure >= 1.15D ? factor(sendPressure) : 1.0D;
            double loadFactor = loadPressure >= 1.15D ? factor(loadPressure) : 1.0D;
            double sendRatio = Math.max(0.25D, limit.sendRatio() * Math.sqrt(sendFactor));
            double loadRatio = Math.max(0.25D, limit.loadRatio() * Math.sqrt(loadFactor));
            float speed = limit.flightCeiling();
            if (observation.flying()) {
                speed = Math.max(0.025f, Math.min(speed,
                        (float) (Math.abs(observation.flySpeed()) * Math.min(sendFactor, loadFactor))));
            }
            limit = new Limit(sendRatio, loadRatio, speed);
            clearEvidence();
        } else if (beforeProbe == null && limit.throttled() && lowSeconds >= recoveryDelay) {
            beforeProbe = limit;
            probeSeconds = 0.0D;
            limit = new Limit(Math.min(1.0D, limit.sendRatio() + RECOVERY_STEP),
                    Math.min(1.0D, limit.loadRatio() + RECOVERY_STEP),
                    Math.min(1.0f, limit.flightCeiling() + (float) RECOVERY_STEP));
            clearEvidence();
        }
        return limit;
    }

    void suspend() {
        sentRate = -1.0D;
        loadRate = -1.0D;
        clearEvidence();
        probeSeconds = 0.0D;
    }

    Limit limit() {
        return limit;
    }

    double demand() {
        return Math.max(0.0D, sentRate) + Math.max(0.0D, loadRate);
    }

    private void clearEvidence() {
        highSeconds = 0.0D;
        lowSeconds = 0.0D;
        sinceChange = 0.0D;
    }

    private static double smooth(double previous, double current, double weight) {
        return previous < 0.0D ? current : previous + (current - previous) * weight;
    }

    private static double factor(double pressure) {
        return Math.clamp(0.85D / pressure, 0.5D, 0.90D);
    }

    static double newCoverage(int radius, int deltaX, int deltaZ) {
        int side = 2 * Math.clamp(radius, 2, 32) + 1;
        long x = Math.min(Math.abs((long) deltaX), side);
        long z = Math.min(Math.abs((long) deltaZ), side);
        return (double) side * side - (side - x) * (side - z);
    }

    record Observation(double blocksPerSecond, double sentPerSecond, double unloadedPerSecond,
                       boolean flying, float flySpeed) {
    }

    record Resources(double networkPressure, double mspt, int senders, int loaders) {
    }

    record Limit(double sendRatio, double loadRatio, float flightCeiling) {
        boolean throttled() {
            return sendRatio < 1.0D || loadRatio < 1.0D || flightCeiling < 1.0f;
        }

        int sendDistance(int original) {
            return distance(original, sendRatio);
        }

        int loadDistance(int original) {
            return distance(original, loadRatio);
        }

        private static int distance(int original, double ratio) {
            return PlayerViewDistancePolicy.scaledDistance(original, ratio, 3);
        }
    }
}
