package org.encinet.mik.module.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightSpeedTest {

    @Test
    void mapsUserScaleToBukkitScale() {
        assertEquals(0.01f, FlightSpeed.toBukkit(0.1f), 0.0001f);
        assertEquals(0.1f, FlightSpeed.toBukkit(1.0f), 0.0001f);
        assertEquals(1.0f, FlightSpeed.toBukkit(10.0f), 0.0001f);
    }

    @Test
    void roundsUserInputToTwoDecimalPlaces() {
        float bukkitSpeed = FlightSpeed.toBukkit(2.345f);

        assertEquals(2.35f, FlightSpeed.fromBukkit(bukkitSpeed), 0.0001f);
        assertEquals("2.35", FlightSpeed.format(FlightSpeed.fromBukkit(bukkitSpeed)));
    }

    @Test
    void formatsWholeSpeedsWithoutDecimalNoise() {
        assertEquals("1", FlightSpeed.format(1.0f));
        assertEquals("0.5", FlightSpeed.format(0.5f));
        assertEquals("10", FlightSpeed.format(10.0f));
    }
}
