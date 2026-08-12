package org.encinet.mik.module.performance;

/**
 * Debounces emergency server-tick pauses and recovery using individual tick
 * durations. Keeping this state machine independent from Bukkit makes its
 * safety boundaries deterministic and testable.
 */
final class EmergencyTickPausePolicy {

    enum Decision {FREEZE, FREEZE_AND_KICK, KICK, UNFREEZE, HOLD}

    static final double FREEZE_TICK_MILLIS = 1_000.0;
    static final double KICK_TICK_MILLIS = 2_000.0;
    static final double RECOVERY_TICK_MILLIS = 200.0;
    static final int FREEZE_CONFIRM_TICKS = 2;
    static final int KICK_CONFIRM_TICKS = 2;
    static final int RECOVERY_CONFIRM_TICKS = 100;

    private int freezeCount;
    private int kickCount;
    private int recoveryCount;
    private boolean kickedDuringPause;

    Decision evaluate(double tickDurationMillis, boolean pausedByModule) {
        if (!Double.isFinite(tickDurationMillis) || tickDurationMillis < 0.0) {
            freezeCount = 0;
            kickCount = 0;
            recoveryCount = 0;
            return Decision.HOLD;
        }

        if (pausedByModule) {
            freezeCount = 0;
            if (!kickedDuringPause && tickDurationMillis >= KICK_TICK_MILLIS) {
                recoveryCount = 0;
                kickCount = Math.min(kickCount + 1, KICK_CONFIRM_TICKS);
                if (kickCount >= KICK_CONFIRM_TICKS) {
                    kickCount = 0;
                    kickedDuringPause = true;
                    return Decision.KICK;
                }
                return Decision.HOLD;
            }

            kickCount = 0;
            if (tickDurationMillis > RECOVERY_TICK_MILLIS) {
                recoveryCount = 0;
                return Decision.HOLD;
            }

            recoveryCount = Math.min(recoveryCount + 1, RECOVERY_CONFIRM_TICKS);
            if (recoveryCount < RECOVERY_CONFIRM_TICKS) {
                return Decision.HOLD;
            }

            recoveryCount = 0;
            kickedDuringPause = false;
            return Decision.UNFREEZE;
        }

        recoveryCount = 0;
        kickedDuringPause = false;
        freezeCount = tickDurationMillis >= FREEZE_TICK_MILLIS
                ? Math.min(freezeCount + 1, FREEZE_CONFIRM_TICKS)
                : 0;
        kickCount = tickDurationMillis >= KICK_TICK_MILLIS
                ? Math.min(kickCount + 1, KICK_CONFIRM_TICKS)
                : 0;

        if (kickCount >= KICK_CONFIRM_TICKS) {
            freezeCount = 0;
            kickCount = 0;
            kickedDuringPause = true;
            return Decision.FREEZE_AND_KICK;
        }
        if (freezeCount >= FREEZE_CONFIRM_TICKS) {
            freezeCount = 0;
            kickCount = 0;
            return Decision.FREEZE;
        }
        return Decision.HOLD;
    }

    void reset() {
        freezeCount = 0;
        kickCount = 0;
        recoveryCount = 0;
        kickedDuringPause = false;
    }
}
