package org.encinet.mik.feature.player.afk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkEntrySafetyTest {

    @Test
    void aStationaryPlayerOrVehicleIsPhysicallyStable() {
        assertTrue(safety(false, false, 0.0F, 0.0D, 0.0D).isPhysicallyStable());
    }

    @Test
    void activeMovementIsNotPhysicallyStable() {
        assertFalse(safety(true, false, 0.0F, 0.0D, 0.0D).isPhysicallyStable());
        assertFalse(safety(false, true, 0.0F, 0.0D, 0.0D).isPhysicallyStable());
        assertFalse(safety(false, false, 1.0F, 0.0D, 0.0D).isPhysicallyStable());
        assertFalse(safety(false, false, 0.0F, 0.01D, 0.0D).isPhysicallyStable());
        assertFalse(safety(false, false, 0.0F, 0.0D, 0.01D).isPhysicallyStable());
    }

    @Test
    void invalidPhysicsValuesFailClosed() {
        assertFalse(safety(false, false, Float.NaN, 0.0D, 0.0D).isPhysicallyStable());
        assertFalse(safety(false, false, -1.0F, 0.0D, 0.0D).isPhysicallyStable());
        assertFalse(safety(false, false, 0.0F, Double.NaN, 0.0D).isPhysicallyStable());
        assertFalse(safety(false, false, 0.0F, 0.0D, -1.0D).isPhysicallyStable());
    }

    private static AfkEntrySafety safety(
            boolean gliding,
            boolean riptiding,
            float fallDistance,
            double playerVelocitySquared,
            double vehicleVelocitySquared
    ) {
        return new AfkEntrySafety(
                gliding, riptiding, fallDistance, playerVelocitySquared, vehicleVelocitySquared);
    }
}
