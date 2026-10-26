package org.encinet.mik.feature.player.afk;

/**
 * Physical stability used to defer passive AFK while inputs are still observed.
 * Idle AFK can enter during movement after the full input-inactivity timeout.
 */
record AfkEntrySafety(
        boolean gliding,
        boolean riptiding,
        float fallDistance,
        double playerVelocitySquared,
        double vehicleVelocitySquared
) {

    private static final float MAX_FALL_DISTANCE = 0.01F;
    private static final double MAX_VELOCITY_SQUARED = 0.01D;

    boolean isPhysicallyStable() {
        return !gliding
                && !riptiding
                && Float.isFinite(fallDistance)
                && fallDistance >= 0.0F
                && fallDistance <= MAX_FALL_DISTANCE
                && isStableVelocity(playerVelocitySquared)
                && isStableVelocity(vehicleVelocitySquared);
    }

    private static boolean isStableVelocity(double velocitySquared) {
        return Double.isFinite(velocitySquared)
                && velocitySquared >= 0.0D
                && velocitySquared < MAX_VELOCITY_SQUARED;
    }
}
