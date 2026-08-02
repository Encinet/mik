package org.encinet.mik.module.menu;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuSurfaceGeometryTest {

    @Test
    void intersectsTheVisibleCenterOfAFlatSurface() {
        FloatingMenuSurfaceGeometry.Hit hit = FloatingMenuSurfaceGeometry.intersect(
                        new Vector(0, 0, 0), new Vector(0, 0, 1),
                        new Vector(0, 0, 3), 0.0F, 0.0F,
                        1.0, 0.8, 0.0)
                .orElseThrow();

        assertEquals(3.0, hit.distance(), 0.0001);
        assertEquals(0.0, hit.normalizedCenterDistance(), 0.0001);
    }

    @Test
    void rejectsAProjectedPointOutsideTheSurface() {
        assertTrue(FloatingMenuSurfaceGeometry.intersect(
                new Vector(0.61, 0, 0), new Vector(0, 0, 1),
                new Vector(0, 0, 3), 0.0F, 0.0F,
                1.0, 0.8, 0.0).isEmpty());
    }

    @Test
    void followsYawAndPitchInsteadOfUsingAnAxisAlignedBox() {
        float yaw = 31.0F;
        float pitch = -12.0F;
        Vector center = new Vector(1.2, 2.0, -0.4);
        Vector normal = FloatingMenuSurfaceGeometry.facing(yaw, pitch);
        Vector up = FloatingMenuSurfaceGeometry.panelUp(yaw, pitch);
        Vector right = up.clone().crossProduct(normal).normalize();
        Vector projected = center.clone()
                .add(right.multiply(0.28))
                .add(up.multiply(-0.16));
        Vector origin = projected.clone().subtract(normal.clone().multiply(2.4));

        FloatingMenuSurfaceGeometry.Hit hit = FloatingMenuSurfaceGeometry.intersect(
                        origin, normal, center, yaw, pitch, 0.8, 0.6, 0.0)
                .orElseThrow();

        assertEquals(2.4, hit.distance(), 0.0001);
    }

    @Test
    void aScaledMarginRemainsProportionalToTheVisibleMenu() {
        double scale = 0.4;
        double width = 1.0 * scale;
        double margin = 0.08 * scale;
        double inside = width * 0.5 + margin - 0.001;
        double outside = width * 0.5 + margin + 0.001;

        assertTrue(FloatingMenuSurfaceGeometry.intersect(
                new Vector(inside, 0, 0), new Vector(0, 0, 1),
                new Vector(0, 0, 3), 0.0F, 0.0F,
                width, 0.4, margin).isPresent());
        assertTrue(FloatingMenuSurfaceGeometry.intersect(
                new Vector(outside, 0, 0), new Vector(0, 0, 1),
                new Vector(0, 0, 3), 0.0F, 0.0F,
                width, 0.4, margin).isEmpty());
    }
}
