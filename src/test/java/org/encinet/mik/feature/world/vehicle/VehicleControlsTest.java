package org.encinet.mik.feature.world.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleControlsTest {
    @Test void explicitModeSelectionIsIdempotentAndPreservesParking() {
        var body = car();
        VehicleControls.apply(body, new VehicleControls.Mode(false));
        VehicleControls.apply(body, new VehicleControls.Mode(false));
        assertEquals(VehicleTransmission.Mode.MANUAL, body.transmission.mode);
        assertEquals(VehicleTransmission.Selector.P, body.transmission.selector);
        VehicleControls.apply(body, new VehicleControls.Mode(true));
        assertEquals(VehicleTransmission.Mode.AUTOMATIC, body.transmission.mode);
        assertEquals(VehicleTransmission.Selector.P, body.transmission.selector);
    }

    @Test void switchingModeDoesNotCancelOrRedirectPendingShift() {
        var body = car();
        VehicleControls.apply(body, new VehicleControls.Select(VehicleTransmission.Selector.D));
        double remaining = body.transmission.shiftRemaining;
        VehicleControls.apply(body, new VehicleControls.Mode(false));
        VehicleControls.apply(body, new VehicleControls.Mode(true));
        assertEquals(VehicleTransmission.Selector.D, body.transmission.selector);
        assertEquals(1, body.transmission.targetGear);
        assertEquals(remaining, body.transmission.shiftRemaining);
        completeShift(body);
        assertEquals(1, body.transmission.gear);
    }

    @Test void directManualSelectionUsesRealShiftDelayAndStopsAtGearLimits() {
        var body = car();
        VehicleControls.apply(body, new VehicleControls.Mode(false));
        VehicleControls.apply(body, new VehicleControls.Gear(2));
        assertEquals(0, body.transmission.gear);
        assertEquals(2, body.transmission.targetGear);
        assertEquals(VehicleTransmission.Selector.D, body.transmission.selector);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, new VehicleControls.Gear(3)));
        assertEquals(2, body.transmission.targetGear);
        completeShift(body);
        assertEquals(2, body.transmission.gear);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, new VehicleControls.Gear(99)));
        assertEquals(2, body.transmission.gear);
        VehicleControls.apply(body, new VehicleControls.StepGear(1));
        assertEquals(3, body.transmission.targetGear);
    }

    @Test void automaticModeRejectsManualSelectionWithoutChangingDrive() {
        var body = car();
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, new VehicleControls.Gear(2)));
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, new VehicleControls.StepGear(1)));
        assertEquals(VehicleTransmission.Selector.P, body.transmission.selector);
        assertEquals(0, body.transmission.gear);
    }

    @Test void interfaceCannotBypassReverseParkingOrDownshiftProtection() {
        var body = car();
        VehicleControls.apply(body, new VehicleControls.Mode(false));
        body.transmission.selector = VehicleTransmission.Selector.D;
        body.transmission.gear = 3;
        body.velocity = new VehicleVector(0, 0, 28);
        for (VehicleControls.Action command : new VehicleControls.Action[]{new VehicleControls.Select(VehicleTransmission.Selector.P), new VehicleControls.Select(VehicleTransmission.Selector.R), new VehicleControls.Gear(1)})
            assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, command));
        assertEquals(3, body.transmission.gear);
        assertEquals(VehicleTransmission.Selector.D, body.transmission.selector);
        body.velocity = new VehicleVector(4, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, new VehicleControls.Select(VehicleTransmission.Selector.P)));
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, new VehicleControls.Select(VehicleTransmission.Selector.R)));
    }

    @Test void selectingTheActualGearDoesNotUseAnOldTarget() {
        var body = car();
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        body.transmission.gear = 2;
        body.transmission.targetGear = 0;
        VehicleControls.apply(body, new VehicleControls.Gear(2));
        assertEquals(2, body.transmission.targetGear);
        assertEquals(VehicleTransmission.Selector.D, body.transmission.selector);
    }

    @Test void numericAndWheelControlsCannotBypassDirectionChecksWhileSliding() {
        var body = car();
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        body.velocity = new VehicleVector(2, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, new VehicleControls.Gear(-1)));
        assertFalse(VehicleControls.stepGear(body, -1));
        assertEquals(VehicleTransmission.Selector.P, body.transmission.selector);
        body.transmission.selector = VehicleTransmission.Selector.R;
        body.transmission.gear = -1;
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, new VehicleControls.Gear(1)));
        assertEquals(VehicleTransmission.Selector.R, body.transmission.selector);
    }

    @Test void engineCanStopAfterFuelRunsOutButCannotRestart() {
        var body = car();
        VehicleControls.apply(body, VehicleControls.Toggle.ENGINE);
        assertTrue(body.engineRunning);
        body.fuel = 0;
        assertEquals("ENGINE_OFF", VehicleControls.apply(body, VehicleControls.Toggle.ENGINE));
        assertFalse(body.engineRunning);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, VehicleControls.Toggle.ENGINE));
        body.fuel = 10;
        body.health = 0;
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, VehicleControls.Toggle.ENGINE));
    }

    @Test void nonCarPanelsUseAssistanceAndBoundedThrottleRatherThanCarGears() {
        var boat = VehicleFixtures.body(VehicleDefinition.Kind.BOAT, VehicleVector.ZERO);
        VehicleControls.apply(boat, new VehicleControls.Mode(false));
        assertFalse(boat.assisted);
        VehicleControls.apply(boat, new VehicleControls.Mode(true));
        assertTrue(boat.assisted);
        for (int index = 0; index < 40; index++) VehicleControls.apply(boat, new VehicleControls.Throttle(-1));
        assertEquals(-0.5, boat.throttle);
        for (int index = 0; index < 40; index++) VehicleControls.apply(boat, new VehicleControls.Throttle(1));
        assertEquals(1, boat.throttle);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(boat, new VehicleControls.Select(VehicleTransmission.Selector.D)));
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(car(), new VehicleControls.Throttle(1)));
    }

    private VehicleBody car() { return VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO); }
    private void completeShift(VehicleBody body) {
        for (int index = 0; index < 50; index++) body.transmission.driveForce(body.definition.engine(), 0, 0, true, 0.01);
    }
}
