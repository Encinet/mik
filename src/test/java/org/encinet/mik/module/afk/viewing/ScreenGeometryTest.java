package org.encinet.mik.module.afk.viewing;

import org.encinet.mik.module.afk.viewing.ScreenGeometry.Point;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ScreenGeometryTest {
    private final ScreenGeometry screen = new ScreenGeometry(new Point(0, 0, 0),
            new Point(1, 0, 0), new Point(0, 1, 0), new Point(0, 0, -1), 5, 1);

    @Test
    void measuresDistanceToTheRectangleInsteadOfItsCenter() {
        assertEquals(2, screen.distanceTo(new Point(5, 0, -2)), 1.0e-9);
        assertEquals(Math.sqrt(13), screen.distanceTo(new Point(8, 0, -2)), 1.0e-9);
        assertEquals(Math.sqrt(17), screen.distanceTo(new Point(0, 5, -1)), 1.0e-9);
    }

    @Test
    void requiresTheFrontSideOfTheActualSurface() {
        assertTrue(screen.isInFront(new Point(0, 0, -2)));
        assertFalse(screen.isInFront(new Point(0, 0, 2)));
        assertFalse(screen.isInFront(new Point(0, 0, 0)));
    }

    @Test
    void aimingAtTheScreenEdgeStillHasZeroViewAngle() {
        Point eye = new Point(0, 0, -5);
        assertEquals(0, screen.minimumViewAngleDegrees(eye, new Point(5, 1, 5)), 1.0e-9);
        assertEquals(0, screen.minimumViewAngleDegrees(eye, new Point(-5, -1, 5)), 1.0e-9);
    }

    @Test
    void findsAnEdgeInteriorInsteadOfOnlyTestingTheCorners() {
        Point view = new Point(Math.sin(Math.toRadians(60)), 0, Math.cos(Math.toRadians(60)));
        assertEquals(15, screen.minimumViewAngleDegrees(new Point(0, 0, -5), view), 1.0e-9);
    }

    @Test
    void lookingAwayCannotBeMistakenForAForwardRayHit() {
        assertTrue(screen.minimumViewAngleDegrees(new Point(0, 0, -5), new Point(0, 0, -1)) > 90);
    }

    @Test
    void analyticalAngleMatchesDenseRectangleSampling() {
        Random random = new Random(2037);
        for (int sample = 0; sample < 100; sample++) {
            Point eye = new Point(random.nextDouble(-10, 10), random.nextDouble(-4, 4), random.nextDouble(-15, -3));
            Point view = new Point(random.nextDouble(-1, 1), random.nextDouble(-1, 1), random.nextDouble(-1, 1)).unit();
            double actual = screen.minimumViewAngleDegrees(eye, view);
            double sampled = 180;
            for (int horizontal = 0; horizontal <= 80; horizontal++) {
                for (int vertical = 0; vertical <= 40; vertical++) {
                    Point offset = new Point(-5 + horizontal / 8.0, -1 + vertical / 20.0, 0).minus(eye).unit();
                    double angle = Math.toDegrees(Math.acos(Math.clamp(offset.dot(view), -1, 1)));
                    sampled = Math.min(sampled, angle);
                }
            }
            assertTrue(actual <= sampled + 1.0e-7, actual + " exceeds sampled " + sampled);
            assertTrue(sampled - actual < 1.5, () -> "Analytical angle deviates at sample " + actual);
        }
    }

    @Test
    void allSixFacesUseTheOutwardSurfaceNotTheBlockCenter() {
        for (ScreenGeometry.Face face : ScreenGeometry.Face.values()) {
            ScreenGeometry geometry = ScreenGeometry.fromBox(new Point(0, 0, 0), new Point(4, 3, 1), face);
            Point outside = geometry.center().plus(geometry.normal().scale(2));
            assertTrue(geometry.isInFront(outside));
            assertEquals(2, geometry.distanceTo(outside), 1.0e-9);
            assertEquals(0, geometry.minimumViewAngleDegrees(outside, geometry.normal().scale(-1)), 1.0e-9);
        }
        assertEquals(-0.02, ScreenGeometry.fromBox(new Point(0, 0, 0), new Point(4, 3, 1),
                ScreenGeometry.Face.NORTH).center().z(), 1.0e-9);
    }

    @Test
    void distantAndGrazingScreensHaveSmallerApparentSize() {
        double close = screen.angularSizeDegrees(new Point(0, 0, -5));
        assertTrue(screen.angularSizeDegrees(new Point(0, 0, -40)) < close);
        assertTrue(screen.angularSizeDegrees(new Point(40, 0, -0.1)) < close);
    }

    @Test
    void largeScreensCannotCreateUnlimitedViewingRange() {
        assertEquals(40, ViewingPolicy.DEFAULT.maximumDistance(screen));
        ScreenGeometry huge = new ScreenGeometry(screen.center(), screen.horizontal(), screen.vertical(), screen.normal(), 100, 50);
        assertEquals(48, ViewingPolicy.DEFAULT.maximumDistance(huge));
    }

    @Test
    void contextRejectsBacksideDistanceAndTinySideSlivers() {
        assertTrue(ViewingPolicy.DEFAULT.withinContext(screen, new Point(0, 0, -5)));
        assertFalse(ViewingPolicy.DEFAULT.withinContext(screen, new Point(0, 0, 5)));
        assertFalse(ViewingPolicy.DEFAULT.withinContext(screen, new Point(0, 0, -80)));
        assertFalse(ViewingPolicy.DEFAULT.withinContext(screen, new Point(30, 0, -0.1)));
    }

    @Test
    void malformedGeometryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Point(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> screen.minimumViewAngleDegrees(new Point(0, 0, -1), new Point(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new ScreenGeometry(screen.center(),
                screen.horizontal(), screen.horizontal(), screen.normal(), 5, 1));
        assertThrows(IllegalArgumentException.class, () -> new ScreenGeometry(screen.center(),
                screen.horizontal(), screen.vertical(), screen.normal(), -5, 1));
    }
}
