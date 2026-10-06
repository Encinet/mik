package org.encinet.mik.module.vehicle;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleCollisionTest {
    private VehicleCollision.Box box(double minimumX, double maximumX) {
        return VehicleCollision.Box.axisAligned(new VehicleVector(minimumX, 0, 0), new VehicleVector(maximumX, 1, 1));
    }
    @Test void sweptBoxCannotTunnelThroughThinWall() {
        VehicleCollision.Hit hit = VehicleCollision.sweep(box(0, 1), new VehicleVector(30, 0, 0), box(10, 10.1));
        assertNotNull(hit);
        assertEquals(0.3, hit.fraction(), 1.0E-9);
        assertEquals(new VehicleVector(-1, 0, 0), hit.normal());
    }
    @Test void allowsMovementAwayFromTouchingSurfaceAndAlongFloor() {
        assertNull(VehicleCollision.sweep(box(0, 1), new VehicleVector(-1, 0, 0), box(1, 2)));
        var floor = VehicleCollision.Box.axisAligned(new VehicleVector(-10, -1, -10), new VehicleVector(10, 0, 10));
        assertNull(VehicleCollision.sweep(box(0, 1), new VehicleVector(1, 0, 0), floor));
    }
    @Test void detectsOverlapAndUsesMinimumPenetrationAxis() {
        var hit = VehicleCollision.sweep(box(0, 1), VehicleVector.ZERO, box(0.9, 1.9));
        assertNotNull(hit);
        assertEquals(0.1, hit.penetration(), 1.0E-9);
        assertEquals(-1, hit.normal().coordinateX());
    }
    @Test void rotatedWingContactsEvenWhenRootDoesNot() {
        var collider = new VehicleDefinition.Collider(new VehicleVector(5, 1, 0), new VehicleVector(4, 0.1, 0.5));
        var wing = VehicleCollision.Box.body(collider, VehicleVector.ZERO, new Quaterniond().rotateY(Math.PI / 2));
        var wall = VehicleCollision.Box.axisAligned(new VehicleVector(-0.2, 0, -8), new VehicleVector(0.2, 2, -7));
        assertNotNull(VehicleCollision.sweep(wing, VehicleVector.ZERO, wall));
    }
    @Test void separatedRotatedBoxesAreNotFalsePositiveAabbs() {
        var slender = new VehicleDefinition.Collider(VehicleVector.ZERO, new VehicleVector(3, 0.1, 0.1));
        var first = VehicleCollision.Box.body(slender, VehicleVector.ZERO, new Quaterniond().rotateY(Math.PI / 4));
        var second = VehicleCollision.Box.body(slender, new VehicleVector(0, 1, 0), new Quaterniond().rotateY(-Math.PI / 4));
        assertNull(VehicleCollision.sweep(first, VehicleVector.ZERO, second));
    }
}
