package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuFraming;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuWorldInteractionGuardTest {

    private final FloatingMenuWorldInteractionGuard guard =
            FloatingMenuWorldInteractionGuard.UNIFIED;

    @Test
    void protectsTheCompleteMenuConeAndReleasesOutsideIt() {
        assertTrue(guard.contains(new Vector(0, 0, 1), directionAt(0)));
        assertTrue(guard.contains(new Vector(0, 0, 1), directionAt(44.9)));
        assertFalse(guard.contains(new Vector(0, 0, 1), directionAt(45.1)));
        assertFalse(guard.contains(new Vector(0, 0, 1), new Vector(0, 0, -1)));
    }

    @Test
    void handlesDegenerateAndInvalidVectorsConservatively() {
        assertTrue(guard.contains(new Vector(0, 0, 1), new Vector()));
        assertFalse(guard.contains(new Vector(), new Vector(0, 0, 1)));
        assertFalse(guard.contains(new Vector(Double.NaN, 0, 1), new Vector(0, 0, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new FloatingMenuWorldInteractionGuard(0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new FloatingMenuWorldInteractionGuard(90.0));
    }

    @Test
    void panoramicFramingExtendsButDoesNotNarrowTheSharedSafetyCone() {
        FloatingMenuFraming panoramic = FloatingMenuFraming.PANORAMIC;

        assertTrue(guard.contains(directionAt(55.0), directionAt(0.0), panoramic));
        assertFalse(guard.contains(directionAt(59.0), directionAt(0.0), panoramic));
    }

    private static Vector directionAt(double degrees) {
        double radians = Math.toRadians(degrees);
        return new Vector(Math.sin(radians), 0.0, Math.cos(radians));
    }
}
