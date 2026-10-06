package org.encinet.mik.module.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.module.menu.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

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
    void ordinaryPanelsHaveARealDistanceAndProjectionChangeAboveSeventy() {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 65, 0);
        var builder = FloatingMenuDefinition.screen("fov-panel");
        builder.information("center", Component.text("Center"));
        var bounds = FloatingMenuAnchorResolver.measure(builder.build(), Map.of("center", FloatingMenuPose.ORIGIN),
                Map.of("center", new FloatingMenuSize(1, 1)));
        var normal = FloatingMenuAnchorResolver.resolve(eye, new Vector(0, 0, 1), bounds, 1,
                FloatingMenuFieldOfView.DEFAULT);
        var highFov = new FloatingMenuFieldOfView(110);
        var high = FloatingMenuAnchorResolver.resolve(eye, new Vector(0, 0, 1), bounds, 1, highFov);
        assertTrue(high.distance() < normal.distance() * 0.55);
        assertEquals(normal.spatialScale(), high.spatialScale(), 1.0E-6);
        double reference = normal.spatialScale() / normal.distance() / Math.tan(Math.toRadians(35));
        assertEquals(reference, high.spatialScale() / high.distance()
                / Math.tan(Math.toRadians(55)), 1.0E-6);
        java.lang.ref.Reference.reachabilityFence(world);
    }

    @Test
    void highFovArcFittingDoesNotUndoTheCompensationByRescalingDistanceAndTextTogether() {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 65, 0);
        var builder = FloatingMenuDefinition.screen("fov-arc").frontArc();
        builder.information("center", Component.text("Center"));
        var definition = builder.build();
        var sizes = Map.of("center", new FloatingMenuSize(1, 1));
        var original = FloatingMenuPose.at(new FloatingMenuPoint(0, 0, -3));
        double reference = Double.NaN;
        double previousDistance = Double.POSITIVE_INFINITY;
        for (int degrees : new int[]{70, 80, 90, 100, 110}) {
            var fov = new FloatingMenuFieldOfView(degrees);
            var pose = FloatingMenuViewProjection.pose(definition.spatialFrame(), fov, original);
            var bounds = FloatingMenuAnchorResolver.measure(definition, Map.of("center", pose), sizes, 1, fov);
            var anchor = FloatingMenuAnchorResolver.frontArc(eye, new Vector(0, 0, 1), 1, bounds, fov);
            double distance = anchor.distance() - pose.forward() * anchor.spatialScale();
            double screenHeight = anchor.spatialScale() / distance / Math.tan(Math.toRadians(degrees / 2.0));
            if (Double.isNaN(reference)) reference = screenHeight;
            assertEquals(reference, screenHeight, 1.0E-6);
            assertTrue(distance < previousDistance);
            previousDistance = distance;
        }
        java.lang.ref.Reference.reachabilityFence(world);
    }

    @Test
    void menuSizePresetsGrowIconsAndTextOnScreenInsteadOfInvertingTheTextMultiplier() {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 65, 0);
        var builder = FloatingMenuDefinition.screen("size-arc").frontArc();
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
                var anchor = FloatingMenuAnchorResolver.frontArc(eye, new Vector(0, 0, 1), layout.factor(), bounds, fov);
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

    @Test
    void actualThreeColumnSettingsPageNeverShrinksWhenItsSizeControlsIncrease() throws Exception {
        var labels = Files.readAllLines(Path.of("src/main/resources/lang/en_us.ftl")).stream()
                .filter(line -> line.startsWith("interface-") || line.startsWith("back-to-main ="))
                .filter(line -> line.contains(" = "))
                .map(line -> line.split(" = ", 2))
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 65, 0);
        for (int degrees : new int[]{70, 90, 110}) {
            var fov = new FloatingMenuFieldOfView(degrees);
            double previous = 0;
            for (var layout : FloatingMenuScale.values()) {
                var preferences = new FloatingMenuPreferences(layout, FloatingMenuTextScale.NORMAL, fov);
                var definition = FloatingMenuSettingsMenu.definition(preferences, true,
                        message -> labels.getOrDefault(message.key(), message.key()), (player, selected) -> {},
                        (player, selected) -> {}, player -> {}, player -> {});
                var nodes = new ArrayList<FloatingMenuLayout.Node>();
                var sizes = new LinkedHashMap<String, FloatingMenuSize>();
                for (var entry : definition.entries().values()) {
                    var size = FloatingMenuNodeSizing.measure(entry, preferences.typographyFactor()).footprint();
                    nodes.add(new FloatingMenuLayout.Node(entry.id(), entry.style(), entry.role(), entry.region(), size));
                    sizes.put(entry.id(), size);
                }
                var poses = new LinkedHashMap<String, FloatingMenuPose>();
                for (int index = 0; index < nodes.size(); index++) {
                    var pose = definition.layout().pose(new FloatingMenuLayout.Context(index, nodes));
                    poses.put(nodes.get(index).elementId(), FloatingMenuViewProjection.pose(definition.spatialFrame(),
                            fov, layout.factor(), pose));
                }
                var bounds = FloatingMenuAnchorResolver.measure(definition, poses, sizes,
                        preferences.typographyFactor(), fov, layout.factor());
                var anchor = FloatingMenuAnchorResolver.frontArc(eye, new Vector(0, 0, 1), layout.factor(), bounds, fov);
                var pose = poses.get("preview");
                double distance = Math.hypot(pose.right() * anchor.spatialScale(),
                        anchor.distance() - pose.forward() * anchor.spatialScale());
                double apparentText = preferences.typographyFactor() * anchor.spatialScale() / distance
                        / Math.tan(Math.toRadians(degrees / 2.0));
                assertTrue(apparentText + 1.0E-9 >= previous,
                        "FOV " + degrees + " / menu " + layout.percent() + " / before " + previous + " / after " + apparentText);
                previous = apparentText;
            }
        }
        java.lang.ref.Reference.reachabilityFence(world);
    }
}
