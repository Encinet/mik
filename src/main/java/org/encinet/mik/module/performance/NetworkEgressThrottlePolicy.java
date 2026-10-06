package org.encinet.mik.module.performance;

/** Converts sustained outbound pressure into a send-distance throttle mode. */
final class NetworkEgressThrottlePolicy {
    private static final double SOFT_PRESSURE_RATIO = 0.70D;
    private static final double HARD_PRESSURE_RATIO = 0.85D;
    private static final double CRITICAL_PRESSURE_RATIO = 0.95D;
    private static final double SOFT_RECOVERY_RATIO = 0.60D;
    private static final double HARD_RECOVERY_RATIO = 0.75D;
    private static final double CRITICAL_RECOVERY_RATIO = 0.85D;
    private static final int ESCALATION_CONFIRM_SAMPLES = 2;
    private static final int RECOVERY_CONFIRM_SAMPLES = 15;

    private ThrottleMode mode = ThrottleMode.OFF;
    private ThrottleMode pendingEscalation = ThrottleMode.OFF;
    private int escalationSamples;
    private int recoverySamples;

    synchronized ThrottleMode update(double pressureRatio) {
        ThrottleMode desired = modeForPressure(pressureRatio);
        if (desired.ordinal() > mode.ordinal()) {
            if (desired != pendingEscalation) {
                pendingEscalation = desired;
                escalationSamples = 1;
            } else {
                escalationSamples++;
            }
            int requiredSamples = desired == ThrottleMode.CRITICAL
                    ? 1 : ESCALATION_CONFIRM_SAMPLES;
            if (escalationSamples >= requiredSamples) {
                mode = desired;
                escalationSamples = 0;
            }
            recoverySamples = 0;
            return mode;
        }

        pendingEscalation = mode;
        escalationSamples = 0;
        if (mode == ThrottleMode.OFF || pressureRatio >= recoveryRatio(mode)) {
            recoverySamples = 0;
            return mode;
        }

        recoverySamples++;
        if (recoverySamples >= RECOVERY_CONFIRM_SAMPLES) {
            mode = previousMode(mode);
            recoverySamples = 0;
        }
        return mode;
    }

    synchronized ThrottleMode reset() {
        mode = ThrottleMode.OFF;
        pendingEscalation = ThrottleMode.OFF;
        escalationSamples = 0;
        recoverySamples = 0;
        return mode;
    }

    static ThrottleMode modeForPressure(double pressureRatio) {
        if (pressureRatio >= CRITICAL_PRESSURE_RATIO) return ThrottleMode.CRITICAL;
        if (pressureRatio >= HARD_PRESSURE_RATIO) return ThrottleMode.HARD;
        if (pressureRatio >= SOFT_PRESSURE_RATIO) return ThrottleMode.SOFT;
        return ThrottleMode.OFF;
    }

    private static double recoveryRatio(ThrottleMode throttleMode) {
        return switch (throttleMode) {
            case OFF -> 0.0D;
            case SOFT -> SOFT_RECOVERY_RATIO;
            case HARD -> HARD_RECOVERY_RATIO;
            case CRITICAL -> CRITICAL_RECOVERY_RATIO;
        };
    }

    private static ThrottleMode previousMode(ThrottleMode throttleMode) {
        return switch (throttleMode) {
            case OFF, SOFT -> ThrottleMode.OFF;
            case HARD -> ThrottleMode.SOFT;
            case CRITICAL -> ThrottleMode.HARD;
        };
    }

    enum ThrottleMode {
        OFF, SOFT, HARD, CRITICAL
    }
}
