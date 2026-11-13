package org.encinet.mik.feature.server.monitor;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonitorTransitionTest {
    @Test
    void distanceAndHeightChangesAccelerateAndSettleWithoutJumpingToTheNewView() {
        MonitorTransition transition = new MonitorTransition();
        Location target = point(0, 0, 0);
        Location current = point(4.5, 1.2, 0);
        Location desired = point(8, 0.15, 0);
        double firstStep = 0;
        double fastestStep = 0;
        double lastStep = 0;
        for (int tick = 0; tick < 90; tick++) {
            Location next = transition.position(current, target, new Vector(), desired, false, (_, _) -> true);
            assertNotNull(next);
            double distance = next.toVector().distance(current.toVector());
            if (tick == 0) firstStep = distance;
            fastestStep = Math.max(fastestStep, distance);
            lastStep = distance;
            assertTrue(next.getX() >= current.getX() && next.getX() <= desired.getX() + 1.0e-8);
            assertTrue(next.getY() <= current.getY() && next.getY() >= desired.getY() - 1.0e-8);
            assertTrue(distance < 0.75);
            current = next;
        }
        assertTrue(firstStep < 0.3 && fastestStep > firstStep * 1.5);
        assertTrue(lastStep < 1.0e-6);
        assertTrue(current.toVector().distance(desired.toVector()) < 1.0e-6);
    }

    @Test
    void anObstructedShortArcUsesTheOtherSideAndKeepsItsDirection() {
        MonitorTransition transition = new MonitorTransition();
        Location target = point(0, 0, 0);
        Location current = point(0, 1.2, -4.5);
        Location desired = point(0, 1.2, 4.5);
        int[] segments = {0};
        for (int tick = 0; tick < 90; tick++) {
            Location next = transition.position(current, target, new Vector(), desired, false, (from, to) -> {
                segments[0]++;
                return from.getX() >= -0.01 && to.getX() >= -0.01;
            });
            assertNotNull(next);
            assertTrue(next.getX() >= -0.01, "the selected arc must not reverse into the blocked side");
            assertTrue(next.toVector().distance(current.toVector()) < 0.5);
            assertEquals(4.5, Math.hypot(next.getX(), next.getZ()), 1.0e-8);
            current = next;
        }
        assertTrue(segments[0] < 30, "a committed orbit must not rescan both routes every tick");
        assertTrue(current.toVector().distance(desired.toVector()) < 0.01);
    }

    @Test
    void reversingTheViewpointBrakesBeforeOrbitingInTheOtherDirection() {
        MonitorTransition transition = new MonitorTransition();
        Location target = point(0, 0, 0);
        Location current = point(0, 1.2, -4.5);
        double previousAngle = 0;
        double previousStep = 0;
        for (int tick = 0; tick < 3; tick++) {
            current = transition.position(current, target, new Vector(), point(4.5, 1.2, 0), false, (_, _) -> true);
            assertNotNull(current);
            double angle = Math.atan2(current.getX(), -current.getZ());
            previousStep = angle - previousAngle;
            previousAngle = angle;
        }
        Location braking = transition.position(current, target, new Vector(), point(-4.5, 1.2, 0), false, (_, _) -> true);
        assertNotNull(braking);
        double step = Math.atan2(braking.getX(), -braking.getZ()) - previousAngle;
        assertTrue(step > 0 && step < previousStep, "a new destination must preserve and brake the existing angular velocity");
        for (int tick = 0; tick < 90; tick++) {
            braking = transition.position(braking, target, new Vector(), point(-4.5, 1.2, 0), false, (_, _) -> true);
            assertNotNull(braking);
        }
        assertTrue(braking.toVector().distance(new Vector(-4.5, 1.2, 0)) < 0.01);
    }

    @Test
    void aimCrossesTheYawBoundaryOnTheShortArcWithAngularInertia() {
        MonitorTransition transition = new MonitorTransition();
        Location current = point(0, 0, 0);
        current.setYaw(179);
        double firstStep = 0;
        double fastestStep = 0;
        for (int tick = 0; tick < 40; tick++) {
            Location next = point(0, 0, 0);
            next.setYaw(-179);
            transition.aim(current, next, _ -> true);
            double step = next.getYaw() - current.getYaw();
            assertTrue(step >= -1.0e-5 && step < 1);
            if (tick == 0) firstStep = step;
            fastestStep = Math.max(fastestStep, step);
            current = next;
        }
        assertTrue(firstStep > 0 && fastestStep > firstStep);
        assertEquals(181, current.getYaw(), 1.0e-4);
    }

    @Test
    void framingLimitsAimLagAndResetDiscardsAngularMomentum() {
        MonitorTransition transition = new MonitorTransition();
        Location current = point(0, 0, 0);
        Location desired = point(0, 0, 0);
        desired.setYaw(30);
        Vector required = desired.getDirection();
        transition.aim(current, desired, direction -> direction.dot(required) >= Math.cos(Math.toRadians(5)));
        assertTrue(desired.getYaw() >= 25 && desired.getYaw() < 25.1,
                "only the damping that would lose the required framing should be released");
        transition.reset();
        Location next = desired.clone();
        transition.aim(desired, next, _ -> true);
        assertEquals(desired.getYaw(), next.getYaw(), 1.0e-6, "a cut must not carry old pan velocity into a stationary shot");
    }

    @Test
    void twoBlockedArcsRequireACut() {
        MonitorTransition transition = new MonitorTransition();
        assertNull(transition.position(point(0, 1.2, -4.5), point(0, 0, 0), new Vector(),
                point(0, 1.2, 4.5), false, (_, _) -> false));
    }

    private static Location point(double x, double y, double z) { return new Location(null, x, y, z); }
}
