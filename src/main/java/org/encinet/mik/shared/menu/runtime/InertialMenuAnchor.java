package org.encinet.mik.shared.menu.runtime;

/** Bounded yaw following. A glance or quick turn holds the tool in place; only explicit input activates it. */
final class InertialMenuAnchor {
    private static final double OFFSET = 50, DEAD_ZONE = 10, ACQUIRE_ANGLE = 22;
    private double yaw, velocity, previousHeadYaw;
    private int holdTicks;
    InertialMenuAnchor(double headYaw) { yaw = headYaw + OFFSET; previousHeadYaw = headYaw; }
    double yaw() { return yaw; }

    double step(double headYaw, boolean engaged, boolean reducedMotion) {
        if (!Double.isFinite(headYaw)) throw new IllegalArgumentException("Head yaw must be finite");
        double headTurn = Math.abs(delta(headYaw, previousHeadYaw));
        previousHeadYaw = headYaw;
        if (engaged || Math.abs(delta(headYaw, yaw)) < ACQUIRE_ANGLE) holdTicks = 10;
        else if (headTurn > 8) holdTicks = Math.max(holdTicks, 6);
        if (holdTicks > 0) { holdTicks--; velocity = 0; return yaw; }
        double error = delta(headYaw + OFFSET, yaw);
        if (Math.abs(error) <= DEAD_ZONE && Math.abs(velocity) < 3) { velocity = 0; return yaw; }
        if (reducedMotion) { yaw += error; velocity = 0; return yaw; }
        velocity = Math.clamp(velocity + (34 * error - 12 * velocity) * 0.05, -90, 90);
        double step = velocity * 0.05;
        if (Math.signum(step) == Math.signum(error) && Math.abs(step) > Math.abs(error)) step = error;
        yaw += step;
        return yaw;
    }
    private static double delta(double target, double current) { return Math.IEEEremainder(target - current, 360); }
}
