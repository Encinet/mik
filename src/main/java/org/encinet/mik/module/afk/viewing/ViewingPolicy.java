package org.encinet.mik.module.afk.viewing;

/**
 * Fixed viewing tolerances, not a calibrated presence classifier.
 * Enter/retain hysteresis and bounded grace avoid requiring viewers to continually
 * move their mouse. A separate short freshness deadline prevents stale suppression.
 */
public record ViewingPolicy(
        double enterAngleDegrees, double retainAngleDegrees, double minimumSizeDegrees,
        long confirmationMillis, long lookAwayGraceMillis, long pauseGraceMillis,
        long exitGraceMillis, long freshnessMillis
) {
    public static final ViewingPolicy DEFAULT = new ViewingPolicy(30, 45, 3,
            5_000, 20_000, 60_000, 30_000, 2_500);

    /** Small screens have a smaller audience range; even a huge screen is capped at 48 blocks. */
    public double maximumDistance(ScreenGeometry geometry) {
        return Math.clamp(8 * Math.max(geometry.halfWidth(), geometry.halfHeight()), 12, 48);
    }

    /** Cheap geometry filters are evaluated before any world/block ray query. */
    public boolean withinContext(ScreenGeometry geometry, ScreenGeometry.Point eye) {
        return geometry.isInFront(eye)
                && geometry.distanceTo(eye) <= maximumDistance(geometry)
                && geometry.angularSizeDegrees(eye) >= minimumSizeDegrees;
    }

    public ViewingPolicy {
        if (!Double.isFinite(enterAngleDegrees) || !Double.isFinite(retainAngleDegrees)
                || !Double.isFinite(minimumSizeDegrees)
                || enterAngleDegrees <= 0 || retainAngleDegrees < enterAngleDegrees
                || retainAngleDegrees >= 90 || minimumSizeDegrees <= 0 || minimumSizeDegrees >= 90
                || confirmationMillis <= 0 || confirmationMillis > 60_000
                || lookAwayGraceMillis < 0 || lookAwayGraceMillis > 120_000
                || pauseGraceMillis < 0 || pauseGraceMillis > 300_000
                || exitGraceMillis < 0 || exitGraceMillis > 120_000
                || freshnessMillis <= 0 || freshnessMillis > 5_000) {
            throw new IllegalArgumentException("Invalid viewing policy");
        }
    }
}
