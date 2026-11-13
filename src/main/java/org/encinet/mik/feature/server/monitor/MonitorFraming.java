package org.encinet.mik.feature.server.monitor;

import org.bukkit.util.Vector;

/** Bounded speed-dependent framing; input velocity is displacement per monitor tick. */
final class MonitorFraming {
    static final double BASE_DISTANCE = 4.5;
    static final double MAX_DISTANCE = 8;
    static final double MAX_COMPENSATION = 1;
    private static final double SPEED_THRESHOLD = 0.45;
    private static final double MAX_CONTINUOUS_MOVEMENT = 32;
    private static final int DIRECTION_FRAMES = 3;

    private double speed;
    private double distance = BASE_DISTANCE;
    private Vector previous = new Vector();
    private Vector compensation = new Vector();
    private int directionFrames;

    record Frame(double distance, Vector compensation, boolean retracting) { }

    void reset() {
        speed = 0;
        distance = BASE_DISTANCE;
        previous.zero();
        compensation.zero();
        directionFrames = 0;
    }

    Frame update(Vector movement) {
        double measured = movement.length();
        // A discontinuity must not turn into a burst of predicted velocity.
        if (measured > MAX_CONTINUOUS_MOVEMENT) {
            reset();
            return new Frame(distance, compensation.clone(), true);
        }
        speed += (measured - speed) * (measured > speed ? 0.35 : 0.75);
        double desiredDistance = BASE_DISTANCE + Math.min(MAX_DISTANCE - BASE_DISTANCE,
                Math.max(0, speed - SPEED_THRESHOLD) * 1.3);
        boolean retracting = desiredDistance < distance - 0.01;
        distance += (desiredDistance - distance) * (retracting ? 0.65 : 0.25);

        double previousSpeed = previous.length();
        boolean consistent = measured > SPEED_THRESHOLD && previousSpeed > SPEED_THRESHOLD
                && movement.dot(previous) >= measured * previousSpeed * 0.8;
        directionFrames = measured <= SPEED_THRESHOLD ? 0 : consistent ? Math.min(DIRECTION_FRAMES, directionFrames + 1) : 1;
        previous = movement.clone();
        if (directionFrames < DIRECTION_FRAMES) {
            retracting |= compensation.lengthSquared() > 1.0e-8;
            compensation.zero();
        } else {
            Vector desired = movement.clone().multiply(0.5 * Math.min(1, (measured - SPEED_THRESHOLD) / 0.5));
            if (desired.lengthSquared() > MAX_COMPENSATION * MAX_COMPENSATION) {
                desired.normalize().multiply(MAX_COMPENSATION);
            }
            boolean reducing = desired.lengthSquared() < compensation.lengthSquared();
            retracting |= reducing;
            compensation.add(desired.subtract(compensation).multiply(reducing ? 0.75 : 0.35));
        }
        return new Frame(distance, compensation.clone(), retracting);
    }
}
