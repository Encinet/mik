package org.encinet.mik.module.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceSurfaceTest {

    @Test
    void oppositeCornersDefineTheWholePlaneArea() {
        SpaceSurface surface = SpaceSurface.betweenCorners(
                "door", "world",
                new SpaceVector(-1.0, 64.0, 8.0),
                new SpaceVector(2.0, 67.0, 8.0),
                new SpaceVector(0.0, 0.0, -1.0),
                new SpaceVector(0.0, 1.0, 0.0));

        assertEquals(new SpaceVector(0.5, 65.5, 8.0), surface.frame().origin());
        assertEquals(3.0, surface.aperture().width(), 1.0E-9);
        assertEquals(3.0, surface.aperture().height(), 1.0E-9);
        assertEquals(4, surface.corners().size());
        assertTrue(surface.corners().contains(new SpaceVector(-1.0, 64.0, 8.0)));
        assertTrue(surface.corners().contains(new SpaceVector(2.0, 67.0, 8.0)));
    }

    @Test
    void cornersMustBeCoplanarWithTheThroughDirection() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SpaceSurface.betweenCorners(
                        "broken", "world",
                        new SpaceVector(0.0, 64.0, 0.0),
                        new SpaceVector(3.0, 67.0, 1.0),
                        new SpaceVector(0.0, 0.0, 1.0),
                        new SpaceVector(0.0, 1.0, 0.0)));

        assertTrue(error.getMessage().contains("same plane"));
    }
}
