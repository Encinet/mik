package org.encinet.mik.module.vehicle;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehiclePhysicsTest {
    @Test void carAcceleratesThroughPowertrainAndBrakesWithoutReversing() {
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO);
        body.engineRunning = true;
        body.transmission.select(VehicleTransmission.Selector.D, 0, body.definition.engine());
        var environment = VehicleFixtures.environment(0, -100);
        VehicleFixtures.simulate(body, new VehicleInput(1, 0, false, true), environment, 600);
        assertTrue(body.velocity.coordinateZ() > 5, "speed=" + body.velocity);
        double fuel = body.fuel;
        VehicleFixtures.simulate(body, new VehicleInput(-1, 0, false, true), environment, 400);
        assertTrue(Math.abs(body.velocity.coordinateZ()) < 0.5, "speed=" + body.velocity);
        assertTrue(body.velocity.coordinateZ() > -0.1);
        assertTrue(body.fuel < fuel);
    }

    @Test void reverseRequiresExplicitGearAndStillUsesForwardThrottle() {
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO);
        body.engineRunning = true;
        body.transmission.select(VehicleTransmission.Selector.R, 0, body.definition.engine());
        VehicleFixtures.simulate(body, new VehicleInput(1, 0, false, true), VehicleFixtures.environment(0, -100), 300);
        assertTrue(body.velocity.coordinateZ() < -2, "speed=" + body.velocity);
        assertEquals(-1, body.transmission.gear);
    }

    @Test void airborneCarCannotGenerateGroundTractionOrSteering() {
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, new VehicleVector(0, 100, 0));
        body.engineRunning = true;
        body.transmission.gear = 1;
        body.transmission.selector = VehicleTransmission.Selector.D;
        VehicleFixtures.simulate(body, new VehicleInput(1, 1, false, true), VehicleFixtures.environment(0, -100), 100);
        assertEquals(0, body.velocity.coordinateX(), 1.0E-8);
        assertEquals(0, body.velocity.coordinateZ(), 1.0E-8);
        assertTrue(body.velocity.coordinateY() < -5);
        assertEquals(0, body.angularVelocity.length(), 1.0E-8);
    }

    @Test void boatFloatsAndUsesWaterThrustRatherThanGroundTires() {
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.BOAT, VehicleVector.ZERO);
        body.engineRunning = true;
        var environment = VehicleFixtures.environment(-100, 2);
        VehicleFixtures.simulate(body, new VehicleInput(1, 0, false, true), environment, 800);
        assertTrue(body.floating);
        assertFalse(body.grounded);
        assertTrue(body.origin().coordinateY() > 0.5 && body.origin().coordinateY() < 2.5, "origin=" + body.origin());
        assertTrue(body.velocity.coordinateZ() > 1);
        assertTrue(Math.abs(body.velocity.coordinateY()) < 0.1);
    }

    @Test void dryBoatDoesNotProducePropulsion() {
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.BOAT, VehicleVector.ZERO);
        body.engineRunning = true;
        VehicleFixtures.simulate(body, new VehicleInput(1, 1, false, true), VehicleFixtures.environment(0, -100), 200);
        assertEquals(0, body.velocity.coordinateZ(), 1.0E-8);
        assertFalse(body.floating);
    }

    @Test void planeCannotHoverAndStallsAtExcessiveAngleOfAttack() {
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.PLANE, new VehicleVector(0, 100, 0));
        var environment = VehicleFixtures.environment(0, -100);
        new VehiclePhysics().step(body, new VehicleInput(0, 0, false, true), environment, 0.01);
        assertTrue(body.velocity.coordinateY() < 0);
        body.velocity = new VehicleVector(0, -20, 40);
        new VehiclePhysics().step(body, new VehicleInput(0, 0, false, true), environment, 0.01);
        assertTrue(body.stalled);
        assertTrue(Double.isFinite(body.velocity.length()));
    }

    @Test void powerlessPlaneRetainsMomentumAndFuelStopsEngine() {
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.PLANE, new VehicleVector(0, 100, 0));
        body.velocity = new VehicleVector(0, 0, 30);
        body.engineRunning = true;
        body.fuel = 0;
        new VehiclePhysics().step(body, VehicleInput.EMPTY, VehicleFixtures.environment(0, -100), 0.01);
        assertFalse(body.engineRunning);
        assertTrue(body.velocity.coordinateZ() > 29);
    }

    @Test void centerOfMassRotationDoesNotMoveItsPhysicalPosition() {
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO);
        VehicleVector center = body.position;
        body.orientation.set(new Quaterniond().rotateZ(0.5));
        assertEquals(center, body.position);
        assertEquals(center.coordinateX(), body.point(body.definition.centerOfMass()).coordinateX(), 1.0E-9);
        assertEquals(center.coordinateY(), body.point(body.definition.centerOfMass()).coordinateY(), 1.0E-9);
    }
}
