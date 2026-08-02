package org.encinet.mik.module.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpaceTransformTest {

    @Test
    void transformsPassengerPointsDirectionsAndVelocityWithOneRigidMapping() {
        SpaceFrame source = SpaceFrame.oriented(
                new SpaceVector(3.0, 70.0, -2.0), 0.0, 0.0, 0.0);
        SpaceFrame destination = SpaceFrame.oriented(
                new SpaceVector(40.0, 75.0, 8.0), 90.0, 0.0, 0.0);
        SpaceTransform transform = new SpaceTransform(source, destination);

        SpaceVector passengerPosition = source.fromLocalPoint(
                new SpaceVector(0.35, 1.2, 0.4));
        SpaceVector passengerLook = source.fromLocalVector(
                new SpaceVector(0.2, -0.1, 0.9)).normalized();
        SpaceVector passengerVelocity = source.fromLocalVector(
                new SpaceVector(-0.1, 0.25, 0.6));

        assertVector(destination.fromLocalPoint(new SpaceVector(0.35, 1.2, 0.4)),
                transform.mapPoint(passengerPosition));
        assertVector(destination.fromLocalVector(
                        new SpaceVector(0.2, -0.1, 0.9)).normalized(),
                transform.mapDirection(passengerLook));
        assertVector(destination.fromLocalVector(new SpaceVector(-0.1, 0.25, 0.6)),
                transform.mapVector(passengerVelocity));
    }

    @Test
    void inverseRestoresPointsDirectionsAndVelocity() {
        SpaceTransform transform = new SpaceTransform(
                SpaceFrame.oriented(
                        new SpaceVector(-4.0, 20.0, 7.0), 35.0, -10.0, 0.0),
                SpaceFrame.oriented(
                        new SpaceVector(80.0, 90.0, -30.0), -65.0, 20.0, 0.0));
        SpaceVector point = new SpaceVector(1.5, 23.0, 9.25);
        SpaceVector direction = new SpaceVector(0.3, -0.4, 0.8).normalized();
        SpaceVector velocity = new SpaceVector(-0.7, 1.1, 0.25);

        assertVector(point, transform.inverse().mapPoint(transform.mapPoint(point)));
        assertVector(direction,
                transform.inverse().mapDirection(transform.mapDirection(direction)));
        assertVector(velocity,
                transform.inverse().mapVector(transform.mapVector(velocity)));
    }

    private void assertVector(SpaceVector expected, SpaceVector actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-9);
        assertEquals(expected.y(), actual.y(), 1.0E-9);
        assertEquals(expected.z(), actual.z(), 1.0E-9);
    }
}
