package org.encinet.mik.module.menu.runtime;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FloatingMenuAnchorMotionTest {
    @Test void followsVehicleTranslationWithoutChasingMouseAim() {
        Location previous = new Location(null, 1, 2, 3, 45, 10);
        Location current = new Location(null, 11, 5, -7, -120, -30);
        Location opened = new Location(null, 1, 3.2, 3, 45, 10);
        Location view = opened.clone();
        Location origin = new Location(null, 3, 3, 5, 45, 0);
        assertTrue(FloatingMenuAnchorMotion.translate(previous, current, opened, view, origin));
        assertEquals(11, opened.getX());
        assertEquals(6.2, opened.getY(), 1.0E-9);
        assertEquals(-7, opened.getZ());
        assertEquals(45, view.getYaw());
        assertEquals(10, view.getPitch());
        assertEquals(13, origin.getX());
        assertEquals(6, origin.getY());
        assertEquals(-5, origin.getZ());
        assertFalse(FloatingMenuAnchorMotion.translate(previous, current, opened, view, origin));
    }

    @Test void poseHeightDoesNotGetCountedTwiceOrReanchorOnHeadTurns() {
        Location previous = new Location(null, 0, 0, 0);
        Location current = new Location(null, 0, 0, 0, 90, 60);
        Location opened = new Location(null, 0, 1.2, 0);
        Location view = new Location(null, 0, 1.62, 0);
        Location origin = new Location(null, 0, 1.62, 3);
        assertFalse(FloatingMenuAnchorMotion.translate(previous, current, opened, view, origin));
        assertEquals(1.62, view.getY());
        assertEquals(0, view.getYaw());
        current.setY(2);
        assertTrue(FloatingMenuAnchorMotion.translate(previous, current, opened, view, origin));
        assertEquals(3.62, view.getY(), 1.0E-9);
    }
}
