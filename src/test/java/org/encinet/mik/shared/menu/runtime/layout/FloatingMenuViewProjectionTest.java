package org.encinet.mik.shared.menu.runtime.layout;

import org.encinet.mik.shared.menu.runtime.FloatingMenuTestWorld;
import org.encinet.mik.shared.menu.runtime.MenuAnchorTestSupport;
import org.encinet.mik.shared.menu.runtime.space.FloatingMenuAnchorResolver;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FloatingMenuViewProjectionTest {
    @Test
    void highFovBringsFrontArcAndRingPanelsCloserWithoutShrinkingTheirTextOrSpacing() {
        var original = FloatingMenuPose.oriented(new FloatingMenuPoint(1, 0.4, -3), 18, 0);
        for (var frame : new FloatingMenuSpatialFrame[]{FloatingMenuSpatialFrame.FRONT_ARC,
                FloatingMenuSpatialFrame.AROUND_VIEWER}) {
            assertSame(original, FloatingMenuViewProjection.pose(frame, FloatingMenuFieldOfView.DEFAULT, original));
            double previous = Double.POSITIVE_INFINITY;
            for (int degrees : new int[]{70, 80, 90, 100, 110}) {
                var projected = FloatingMenuViewProjection.pose(frame, new FloatingMenuFieldOfView(degrees), original);
                assertEquals(original.right(), projected.right());
                assertEquals(original.up(), projected.up());
                assertTrue(-projected.forward() < previous);
                previous = -projected.forward();
            }
            var high = FloatingMenuViewProjection.pose(frame, new FloatingMenuFieldOfView(110), original);
            assertTrue(-high.forward() < 1.5);
        }
    }

    @Test
    void frontalTextHasTheSameNormalizedPerspectiveHeightAtEverySupportedFov() {
        double reference = 1 / (3 * Math.tan(Math.toRadians(35)));
        for (var fov : FloatingMenuFieldOfView.PRESETS) {
            var projected = FloatingMenuViewProjection.pose(FloatingMenuSpatialFrame.FRONT_ARC, fov,
                    FloatingMenuPose.at(new FloatingMenuPoint(0, 0, -3)));
            double screenHeight = 1 / (-projected.forward() * Math.tan(Math.toRadians(fov.degrees() / 2.0)));
            assertEquals(reference, screenHeight, 1.0E-9);
        }
    }

    @Test
    void curvedPanelNormalsStillFaceTheViewerAfterDepthCompensation() {
        for (int degrees : new int[]{-170, -80, -45, 0, 45, 80, 170}) {
            double angle = Math.toRadians(degrees);
            var original = FloatingMenuPose.oriented(new FloatingMenuPoint(3 * Math.sin(angle), 0,
                    -3 * Math.cos(angle)), degrees, 0);
            for (var fov : FloatingMenuFieldOfView.PRESETS) {
                var projected = FloatingMenuViewProjection.pose(FloatingMenuSpatialFrame.AROUND_VIEWER, fov, original);
                assertEquals(Math.toDegrees(Math.atan2(projected.right(), -projected.forward())),
                        projected.yawDegrees(), 1.0E-9);
            }
        }
    }

    @Test
    void ordinaryPanelsKeepTheirPhysicalDistanceWhileFovChangesTheirProjection() {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 65, 0);
        var builder = MenuScene.screen("fov-panel");
        builder.information("center", Component.text("Center"));
        var bounds = FloatingMenuAnchorResolver.measure(builder.build(), Map.of("center", FloatingMenuPose.ORIGIN),
                Map.of("center", new FloatingMenuSize(1, 1)));
        var normal = MenuAnchorTestSupport.front(eye, new Vector(0, 0, 1), bounds, 1,
                FloatingMenuFieldOfView.DEFAULT);
        var highFov = new FloatingMenuFieldOfView(110);
        var high = MenuAnchorTestSupport.front(eye, new Vector(0, 0, 1), bounds, 1, highFov);
        assertEquals(normal.distance(),high.distance(),1.0E-6);
        assertEquals(normal.spatialScale(), high.spatialScale(), 1.0E-6);
        double reference = normal.spatialScale() / normal.distance() / Math.tan(Math.toRadians(35));
        assertTrue(reference > high.spatialScale() / high.distance()
                / Math.tan(Math.toRadians(55)));
        java.lang.ref.Reference.reachabilityFence(world);
    }

    @Test
    void frontArcFieldOfViewDoesNotInflateThePhysicalTypography() {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 65, 0);
        var builder = MenuScene.screen("fov-arc").frontArc();
        builder.information("center", Component.text("Center"));
        var definition = builder.build();
        var sizes = Map.of("center", new FloatingMenuSize(1, 1));
        var original = FloatingMenuPose.at(new FloatingMenuPoint(0, 0, -3));
        double previousScreenHeight = Double.POSITIVE_INFINITY;
        double previousDistance = Double.POSITIVE_INFINITY;
        for (int degrees : new int[]{70, 80, 90, 100, 110}) {
            var fov = new FloatingMenuFieldOfView(degrees);
            var pose = FloatingMenuViewProjection.pose(definition.spatialFrame(), fov, original);
            var bounds = FloatingMenuAnchorResolver.measure(definition, Map.of("center", pose), sizes, 1, fov);
            var anchor = MenuAnchorTestSupport.arc(eye, new Vector(0, 0, 1), 1, bounds, fov);
            double distance = anchor.distance() - pose.forward() * anchor.spatialScale();
            double screenHeight = anchor.spatialScale() / distance / Math.tan(Math.toRadians(degrees / 2.0));
            assertEquals(.25,anchor.spatialScale(),1e-6);
            assertTrue(screenHeight < previousScreenHeight);
            previousScreenHeight = screenHeight;
            assertTrue(distance >= 2.8);
            assertTrue(distance < previousDistance);
            previousDistance = distance;
        }
        java.lang.ref.Reference.reachabilityFence(world);
    }

    @Test
    void menuSizePresetsGrowIconsAndTextOnScreenInsteadOfInvertingTheTextMultiplier() {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 65, 0);
        var builder = MenuScene.screen("size-arc").frontArc();
        builder.information("center", Component.text("Center"));
        var definition = builder.build();
        var original = FloatingMenuPose.at(new FloatingMenuPoint(0, 0, -3));
        for (var fov : FloatingMenuFieldOfView.PRESETS) {
            double previousIcon = 0;
            double previousText = 0;
            double referenceText = Double.NaN;
            for (var layout : FloatingMenuScale.values()) {
                var preferences = new FloatingMenuPreferences(layout, FloatingMenuTextScale.NORMAL, fov);
                var pose = FloatingMenuViewProjection.pose(definition.spatialFrame(), fov, layout.factor(), original);
                var sizes = Map.of("center", new FloatingMenuSize(preferences.typographyFactor(), preferences.typographyFactor()));
                var bounds = FloatingMenuAnchorResolver.measure(definition, Map.of("center", pose), sizes,
                        preferences.typographyFactor(), fov, layout.factor());
                var anchor = MenuAnchorTestSupport.arc(eye, new Vector(0, 0, 1), layout.factor(), bounds, fov);
                double distance = anchor.distance() - pose.forward() * anchor.spatialScale();
                double projection = anchor.spatialScale() / distance / Math.tan(Math.toRadians(fov.degrees() / 2.0));
                double icon = 0.32 * projection;
                double text = preferences.typographyFactor() * projection;
                assertTrue(icon > previousIcon, "FOV " + fov.degrees() + " / menu " + layout.percent());
                assertTrue(text > previousText);
                if (Double.isNaN(referenceText)) referenceText = text / layout.factor();
                assertEquals(referenceText, text / layout.factor(), 1.0E-6);
                previousIcon = icon;
                previousText = text;
            }
        }
        java.lang.ref.Reference.reachabilityFence(world);
    }

}
