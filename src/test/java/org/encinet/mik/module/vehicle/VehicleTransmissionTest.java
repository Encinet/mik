package org.encinet.mik.module.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleTransmissionTest {
    private final VehicleDefinition.Engine profile = VehicleFixtures.definition(VehicleDefinition.Kind.CAR).engine();

    @Test void blocksUnsafeDirectionChangesAndParkingWithoutChangingSelector() {
        VehicleTransmission transmission = new VehicleTransmission();
        transmission.selector = VehicleTransmission.Selector.D;
        transmission.gear = 2;
        assertFalse(transmission.select(VehicleTransmission.Selector.R, 12, profile));
        assertEquals(VehicleTransmission.Selector.D, transmission.selector);
        assertFalse(transmission.select(VehicleTransmission.Selector.P, 12, profile));
        assertEquals(2, transmission.gear);
    }

    @Test void disconnectsDriveDuringShiftAndCommitsOnlyAfterDelay() {
        VehicleTransmission transmission = new VehicleTransmission();
        assertTrue(transmission.select(VehicleTransmission.Selector.D, 0, profile));
        assertEquals(0, transmission.gear);
        assertEquals(0, transmission.driveForce(profile, 0, 1, true, 0.1));
        assertEquals(0, transmission.gear);
        assertTrue(transmission.label().contains("→"));
        for (int index = 0; index < 30; index++) transmission.driveForce(profile, 0, 1, true, 0.01);
        assertEquals(1, transmission.gear);
        assertFalse(transmission.label().contains("→"));
    }

    @Test void manualNeverAutomaticallyUpshiftsAndRejectsOverrev() {
        VehicleTransmission transmission = new VehicleTransmission();
        transmission.mode = VehicleTransmission.Mode.MANUAL;
        transmission.selector = VehicleTransmission.Selector.D;
        transmission.gear = 3;
        transmission.gearAge = 3;
        for (int index = 0; index < 100; index++) transmission.driveForce(profile, 28, 1, true, 0.01);
        assertEquals(3, transmission.gear);
        assertFalse(transmission.request(1, 28, profile));
        assertEquals("OVERREV", transmission.warning);
    }

    @Test void automaticChangesGearsWithHysteresisAndModeSwitchRetainsGear() {
        VehicleTransmission transmission = new VehicleTransmission();
        transmission.selector = VehicleTransmission.Selector.D;
        transmission.gear = 1;
        transmission.gearAge = 2;
        transmission.driveForce(profile, 20, 1, true, 0.01);
        assertEquals(2, transmission.targetGear);
        assertTrue(transmission.shiftRemaining > 0);
        transmission.toggle();
        assertEquals(VehicleTransmission.Mode.MANUAL, transmission.mode);
        assertEquals(1, transmission.gear);
        transmission.toggle();
        assertEquals(VehicleTransmission.Selector.D, transmission.selector);
    }

    @Test void neutralCanRevWithoutWheelForce() {
        VehicleTransmission transmission = new VehicleTransmission();
        transmission.selector = VehicleTransmission.Selector.N;
        for (int index = 0; index < 100; index++) assertEquals(0, transmission.driveForce(profile, 0, 1, true, 0.01));
        assertTrue(transmission.rpm > profile.idleRpm() * 2);
    }
}
