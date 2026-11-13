package org.encinet.mik.feature.server.monitor;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonitorFramingTest {
    @Test
    void ordinaryWalkingAndAlternatingSmallInputsDoNotPumpTheZoom() {
        MonitorFraming framing = new MonitorFraming();
        for (int tick = 0; tick < 100; tick++) {
            var frame = framing.update(new Vector(tick % 2 == 0 ? 0.25 : -0.25, 0, 0));
            assertEquals(4.5, frame.distance());
            assertEquals(0, frame.compensation().lengthSquared());
        }
    }

    @Test
    void fastThreeDimensionalMovementHasBoundedZoomAndCompensation() {
        MonitorFraming framing = new MonitorFraming();
        Vector movement = new Vector(3, 2, 4);
        MonitorFraming.Frame frame = null;
        for (int tick = 0; tick < 40; tick++) {
            frame = framing.update(movement);
            assertTrue(frame.distance() >= 4.5 && frame.distance() <= 8);
            assertTrue(frame.compensation().length() <= 1.0 + 1.0e-8);
        }
        assertNotNull(frame);
        assertTrue(frame.distance() > 7.9);
        assertEquals(1, frame.compensation().clone().normalize().dot(movement.clone().normalize()), 1.0e-8);
        assertEquals(new Vector(3, 2, 4), movement, "input movement must not be mutated");
    }

    @Test
    void stoppingDropsPredictionImmediatelyAndRetractsZoom() {
        MonitorFraming framing = new MonitorFraming();
        for (int tick = 0; tick < 30; tick++) framing.update(new Vector(3, 0, 0));
        var stopped = framing.update(new Vector());
        assertEquals(0, stopped.compensation().lengthSquared());
        assertTrue(stopped.retracting());
        for (int tick = 0; tick < 8; tick++) stopped = framing.update(new Vector());
        assertTrue(stopped.distance() < 4.55);
    }

    @Test
    void reversalsAndSharpTurnsRequireAConsistentNewDirectionBeforePredictingAgain() {
        for (Vector turn : new Vector[]{new Vector(-3, 0, 0), new Vector(0, 0, 3)}) {
            MonitorFraming framing = new MonitorFraming();
            for (int tick = 0; tick < 30; tick++) framing.update(new Vector(3, 0, 0));
            var turned = framing.update(turn);
            assertEquals(0, turned.compensation().lengthSquared(), "never keep projecting the old direction");
            assertTrue(turned.retracting());
            assertTrue(turned.distance() > 7, "a turn at unchanged speed should retain the wide view");
            assertEquals(0, framing.update(turn).compensation().lengthSquared());
            assertTrue(framing.update(turn).compensation().dot(turn) > 0);
        }
    }

    @Test
    void anUnexplainedLargeJumpClearsPredictionInsteadOfCreatingExtremeZoom() {
        MonitorFraming framing = new MonitorFraming();
        for (int tick = 0; tick < 30; tick++) framing.update(new Vector(3, 0, 0));
        var jumped = framing.update(new Vector(100, 80, 100));
        assertEquals(4.5, jumped.distance());
        assertEquals(0, jumped.compensation().lengthSquared());
        assertTrue(jumped.retracting());
    }
}
