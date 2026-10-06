package org.encinet.mik.module.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleControlsTest {
    @Test void explicitModeSelectionIsIdempotentAndPreservesParking() {
        var body = car();
        VehicleControls.apply(body, "mode manual");
        VehicleControls.apply(body, "mode manual");
        assertEquals(VehicleTransmission.Mode.MANUAL, body.transmission.mode);
        assertEquals(VehicleTransmission.Selector.P, body.transmission.selector);
        VehicleControls.apply(body, "mode automatic");
        assertEquals(VehicleTransmission.Mode.AUTOMATIC, body.transmission.mode);
        assertEquals(VehicleTransmission.Selector.P, body.transmission.selector);
    }

    @Test void switchingModeDoesNotCancelOrRedirectPendingShift() {
        var body = car();
        VehicleControls.apply(body, "gear D");
        double remaining = body.transmission.shiftRemaining;
        VehicleControls.apply(body, "mode manual");
        VehicleControls.apply(body, "mode automatic");
        assertEquals(VehicleTransmission.Selector.D, body.transmission.selector);
        assertEquals(1, body.transmission.targetGear);
        assertEquals(remaining, body.transmission.shiftRemaining);
        completeShift(body);
        assertEquals(1, body.transmission.gear);
    }

    @Test void directManualSelectionUsesRealShiftDelayAndStopsAtGearLimits() {
        var body = car();
        VehicleControls.apply(body, "mode manual");
        VehicleControls.apply(body, "gear 2");
        assertEquals(0, body.transmission.gear);
        assertEquals(2, body.transmission.targetGear);
        assertEquals(VehicleTransmission.Selector.D, body.transmission.selector);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, "gear 3"));
        assertEquals(2, body.transmission.targetGear);
        completeShift(body);
        assertEquals(2, body.transmission.gear);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, "gear 99"));
        assertEquals(2, body.transmission.gear);
        VehicleControls.apply(body, "gear up");
        assertEquals(3, body.transmission.targetGear);
    }

    @Test void automaticModeRejectsManualSelectionWithoutChangingDrive() {
        var body = car();
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, "gear 2"));
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, "gear up"));
        assertEquals(VehicleTransmission.Selector.P, body.transmission.selector);
        assertEquals(0, body.transmission.gear);
    }

    @Test void interfaceCannotBypassReverseParkingOrDownshiftProtection() {
        var body = car();
        VehicleControls.apply(body, "mode manual");
        body.transmission.selector = VehicleTransmission.Selector.D;
        body.transmission.gear = 3;
        body.velocity = new VehicleVector(0, 0, 28);
        for (String command : new String[]{"gear P", "gear R", "gear 1"})
            assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, command));
        assertEquals(3, body.transmission.gear);
        assertEquals(VehicleTransmission.Selector.D, body.transmission.selector);
        body.velocity = new VehicleVector(4, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, "gear P"));
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, "gear R"));
    }

    @Test void selectingTheActualGearDoesNotUseAnOldTarget() {
        var body = car();
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        body.transmission.gear = 2;
        body.transmission.targetGear = 0;
        VehicleControls.apply(body, "gear 2");
        assertEquals(2, body.transmission.targetGear);
        assertEquals(VehicleTransmission.Selector.D, body.transmission.selector);
    }

    @Test void numericAndWheelControlsCannotBypassDirectionChecksWhileSliding() {
        var body = car();
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        body.velocity = new VehicleVector(2, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, "gear -1"));
        assertFalse(VehicleControls.stepGear(body, -1));
        assertEquals(VehicleTransmission.Selector.P, body.transmission.selector);
        body.transmission.selector = VehicleTransmission.Selector.R;
        body.transmission.gear = -1;
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, "gear 1"));
        assertEquals(VehicleTransmission.Selector.R, body.transmission.selector);
    }

    @Test void engineCanStopAfterFuelRunsOutButCannotRestart() {
        var body = car();
        VehicleControls.apply(body, "engine");
        assertTrue(body.engineRunning);
        body.fuel = 0;
        assertEquals("ENGINE_OFF", VehicleControls.apply(body, "engine"));
        assertFalse(body.engineRunning);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, "engine"));
        body.fuel = 10;
        body.health = 0;
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, "engine"));
    }

    @Test void nonCarPanelsUseAssistanceAndBoundedThrottleRatherThanCarGears() {
        var boat = VehicleFixtures.body(VehicleDefinition.Kind.BOAT, VehicleVector.ZERO);
        VehicleControls.apply(boat, "mode manual");
        assertFalse(boat.assisted);
        VehicleControls.apply(boat, "mode assisted");
        assertTrue(boat.assisted);
        for (int index = 0; index < 40; index++) VehicleControls.apply(boat, "throttle down");
        assertEquals(-0.5, boat.throttle);
        for (int index = 0; index < 40; index++) VehicleControls.apply(boat, "throttle up");
        assertEquals(1, boat.throttle);
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(boat, "gear D"));
        assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(car(), "throttle up"));
    }

    @Test void invalidControlsCannotAlterModeOrEngineState() {
        var body = car();
        for (String command : new String[]{"mode invalid", "engine ignored", "gear", "mode manual ignored", "something else"})
            assertThrows(IllegalArgumentException.class, () -> VehicleControls.apply(body, command));
        assertEquals(VehicleTransmission.Mode.AUTOMATIC, body.transmission.mode);
        assertFalse(body.engineRunning);
        assertEquals("MANUAL", VehicleControls.apply(body, "Mode manual"));
    }

    private VehicleBody car() { return VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO); }
    private void completeShift(VehicleBody body) {
        for (int index = 0; index < 50; index++) body.transmission.driveForce(body.definition.engine(), 0, 0, true, 0.01);
    }
}
