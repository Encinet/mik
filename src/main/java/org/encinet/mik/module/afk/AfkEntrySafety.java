package org.encinet.mik.module.afk;

/**
 * Physical conditions required before AFK protection can be applied safely.
 * Stable flight and stationary vehicles are allowed; active movement modes are
 * deferred until they settle.
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

    boolean permitsAutomaticAfk() {
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
