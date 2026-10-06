package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuSize;
import org.encinet.mik.module.menu.FloatingMenuFieldOfView;
import org.encinet.mik.module.menu.FloatingMenuPreferences;
import org.encinet.mik.module.menu.FloatingMenuScale;
import org.encinet.mik.module.menu.FloatingMenuTextScale;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuFrontArcTest {
    @Test
    void fitsEveryPanelBetweenFloorCeilingAndSideWallsInATwoBlockRoom() {
        FloatingMenuTestWorld scene = room();
        Location eye = new Location(scene.world(), 0.5, 65.62, 0.5);
        var bounds = bounds(Map.of(
                "center", pose(0, 0, -3, 0),
                "left", pose(-2.6, -0.7, -1.5, -60),
                "right", pose(2.6, 0.7, -1.5, 60)), new FloatingMenuSize(2, 2));
        var fitted = FloatingMenuAnchorResolver.frontArc(eye, new Vector(0, 0, 1), 1.1, bounds);
        assertTrue(fitted.spatialScale() > 0.1);
        assertClear(scene, eye, fitted, bounds);
    }

    @Test
    void limitsTheVerticalReadingAngleForVeryTallPages() {
        FloatingMenuTestWorld scene = new FloatingMenuTestWorld();
        Location eye = new Location(scene.world(), 0, 65, 0);
        var bounds = bounds(Map.of("tall", pose(0, 0, -3, 0)), new FloatingMenuSize(2, 12));
        var fitted = FloatingMenuAnchorResolver.frontArc(eye, new Vector(0, 0, 1), 1, bounds);
        for (FloatingMenuPoint point : bounds.probes()) {
            Vector target = position(fitted, point).subtract(eye.toVector());
            if (Math.hypot(target.getX(), target.getZ()) < 1.0E-6) continue;
            double angle = Math.toDegrees(Math.atan2(Math.abs(target.getY()), Math.hypot(target.getX(), target.getZ())));
            assertTrue(angle <= 35.001, "Vertical angle: " + angle);
        }
    }

    @Test
    void frontArcsFitEverySupportedFovWithoutLeavingItsVerticalEnvelope() {
        var scene = new FloatingMenuTestWorld();
        var eye = new Location(scene.world(), 0, 65, 0);
        var bounds = bounds(Map.of("tall", pose(0, 0, -3, 0)), new FloatingMenuSize(2, 12));
        for (int degrees = 30; degrees <= 110; degrees++) {
            var fieldOfView = new FloatingMenuFieldOfView(degrees);
            var fitted = FloatingMenuAnchorResolver.frontArc(eye, new Vector(0, 0, 1), 1, bounds, fieldOfView);
            assertVerticalEnvelope(eye, fitted, bounds, fieldOfView);
        }
        java.lang.ref.Reference.reachabilityFence(scene);
    }

    @Test
    void ordinaryPanelsUseFovRatherThanTheFixedThirtyFiveDegreeLimit() {
        var scene = new FloatingMenuTestWorld();
        var eye = new Location(scene.world(), 0, 65, 0);
        var bounds = bounds(Map.of("tall", pose(0, 0, 0, 0)), new FloatingMenuSize(2, 12));
        for (var fieldOfView : FloatingMenuFieldOfView.PRESETS) {
            var fitted = FloatingMenuAnchorResolver.resolve(eye, new Vector(0, 0, 1), bounds, 1, fieldOfView);
            assertVerticalEnvelope(eye, fitted, bounds, fieldOfView);
        }
        java.lang.ref.Reference.reachabilityFence(scene);
    }

    @Test
    void narrowFovStillRespectsRealFloorCeilingAndWalls() {
        var scene = room();
        var eye = new Location(scene.world(), 0.5, 65.62, 0.5);
        var bounds = bounds(Map.of("center", pose(0, 0, -3, 0),
                "left", pose(-2.6, -0.7, -1.5, -60), "right", pose(2.6, 0.7, -1.5, 60)), new FloatingMenuSize(2, 2));
        for (var fieldOfView : FloatingMenuFieldOfView.PRESETS) {
            var fitted = FloatingMenuAnchorResolver.frontArc(eye, new Vector(0, 0, 1), 1.1, bounds, fieldOfView);
            assertClear(scene, eye, fitted, bounds);
            assertVerticalEnvelope(eye, fitted, bounds, fieldOfView);
        }
    }

    @Test
    void allSizeAndFovCombinationsKeepTheirMeasuredFootprintsInsideTheReadingEnvelope() {
        var scene = new FloatingMenuTestWorld();
        var eye = new Location(scene.world(), 0, 65, 0);
        for (var fieldOfView : FloatingMenuFieldOfView.PRESETS) {
            for (var layout : FloatingMenuScale.values()) {
                for (var text : FloatingMenuTextScale.values()) {
                    var preferences = new FloatingMenuPreferences(layout, text, fieldOfView);
                    var size = new FloatingMenuSize(4 * preferences.typographyFactor(), 5 * preferences.typographyFactor());
                    var projected = FloatingMenuViewProjection.pose(org.encinet.mik.module.menu.FloatingMenuSpatialFrame.FRONT_ARC,
                            fieldOfView, layout.factor(), pose(0, 0, -3, 0));
                    var bounds = bounds(Map.of("center", projected), size);
                    var fitted = FloatingMenuAnchorResolver.frontArc(eye, new Vector(0, 0, 1), layout.factor(), bounds, fieldOfView);
                    assertTrue(fitted.spatialScale() > 0);
                    assertTrue(fitted.spatialScale() <= layout.factor() + 0.0001);
                    assertVerticalEnvelope(eye, fitted, bounds, fieldOfView);
                }
            }
        }
        java.lang.ref.Reference.reachabilityFence(scene);
    }

    private static void assertVerticalEnvelope(Location eye, FloatingMenuAnchorResolver.Anchor fitted,
                                               FloatingMenuAnchorResolver.SceneBounds bounds,
                                               FloatingMenuFieldOfView fieldOfView) {
        for (var point : bounds.probes()) {
            var target = position(fitted, point).subtract(eye.toVector());
            double horizontal = Math.hypot(target.getX(), target.getZ());
            if (horizontal < 1.0E-6) continue;
            double angle = Math.toDegrees(Math.atan2(Math.abs(target.getY()), horizontal));
            assertTrue(angle <= fieldOfView.readingFraming().verticalHalfAngleDegrees() + 0.001,
                    "FOV " + fieldOfView.degrees() + ", vertical angle " + angle);
        }
    }

    @Test
    void largeTypographyCannotPushTheActualEdgesThroughTheCeiling() {
        FloatingMenuTestWorld scene = room();
        Location eye = new Location(scene.world(), 0.5, 65.62, 0.5);
        var bounds = bounds(Map.of("center", pose(0, 0, -3, 0)), new FloatingMenuSize(4, 5));
        var fitted = FloatingMenuAnchorResolver.frontArc(eye, new Vector(0, 0, 1), 1.1, bounds);
        assertClear(scene, eye, fitted, bounds);
    }

    @Test
    void fitsAnOrdinarySceneAfterApplyingThePlayersSizePreference() {
        FloatingMenuTestWorld scene = room();
        Location eye = new Location(scene.world(), 0.5, 65.62, 0.5);
        var bounds = bounds(Map.of("center", pose(0, 0, 0, 0)), new FloatingMenuSize(2, 4));
        var fitted = FloatingMenuAnchorResolver.resolve(eye, new Vector(0, 0, 1), bounds, 1.1);
        assertClear(scene, eye, fitted, bounds);
    }

    @Test
    void titlesHaveAFullFootprintAndStayInFrontOfTheViewer() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.screen("title-test").frontArc().title(Component.text("Title"));
        builder.information("center", Component.text("Center"));
        var bounds = FloatingMenuAnchorResolver.measure(builder.build(),
                Map.of("center", pose(0, 0, -3, 0)), Map.of("center", new FloatingMenuSize(2, 2)));
        assertTrue(bounds.surfaces().size() == 2);
        assertTrue(bounds.surfaces().stream().allMatch(surface -> surface.pose().forward() < 0));
    }

    private static FloatingMenuTestWorld room() {
        FloatingMenuTestWorld scene = new FloatingMenuTestWorld();
        for (int blockX = -1; blockX <= 2; blockX++) {
            for (int blockZ = -1; blockZ <= 3; blockZ++) {
                scene.block(blockX, 63, blockZ, new BoundingBox(0, 0, 0, 1, 1, 1));
                scene.block(blockX, 66, blockZ, new BoundingBox(0, 0, 0, 1, 1, 1));
                for (int blockY = 64; blockY <= 65; blockY++) {
                    if (blockX == -1 || blockX == 2 || blockZ == 3 || blockZ == -1) {
                        scene.block(blockX, blockY, blockZ, new BoundingBox(0, 0, 0, 1, 1, 1));
                    }
                }
            }
        }
        return scene;
    }

    private static FloatingMenuPose pose(double right, double up, double forward, double yaw) {
        return FloatingMenuPose.oriented(new FloatingMenuPoint(right, up, forward), yaw, 0);
    }

    private static FloatingMenuAnchorResolver.SceneBounds bounds(Map<String, FloatingMenuPose> poses, FloatingMenuSize size) {
        var builder = FloatingMenuDefinition.screen("arc-test").frontArc();
        poses.keySet().forEach(id -> builder.information(id, Component.text(id)));
        var sizes = poses.keySet().stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> size));
        return FloatingMenuAnchorResolver.measure(builder.build(), poses, sizes);
    }

    private static Vector position(FloatingMenuAnchorResolver.Anchor anchor, FloatingMenuPoint point) {
        return anchor.origin().toVector().add(anchor.right().multiply(point.right() * anchor.spatialScale()))
                .add(new Vector(0, point.up() * anchor.spatialScale(), 0))
                .add(anchor.forward().multiply(-point.forward() * anchor.spatialScale()));
    }

    private static void assertClear(FloatingMenuTestWorld scene, Location eye,
                                    FloatingMenuAnchorResolver.Anchor anchor,
                                    FloatingMenuAnchorResolver.SceneBounds bounds) {
        FloatingMenuWorldSpace space = new FloatingMenuWorldSpace(scene.world());
        for (var surface : bounds.surfaces()) {
            double yaw = Math.toRadians(surface.pose().yawDegrees());
            Vector right = anchor.right().multiply(Math.cos(yaw)).add(anchor.forward().multiply(-Math.sin(yaw)));
            assertTrue(space.clearSurface(position(anchor, surface.pose().point()), right, new Vector(0, 1, 0),
                    surface.size().width() * anchor.spatialScale() / 2,
                    surface.size().height() * anchor.spatialScale() / 2, 0.001), surface.toString());
        }
        for (FloatingMenuPoint point : bounds.probes()) {
            Vector ray = position(anchor, point).subtract(eye.toVector());
            double distance = ray.length();
            if (distance < 1.0E-6) continue;
            assertTrue(space.firstHit(eye, ray.multiply(1 / distance), distance) >= distance - 1.0E-5);
        }
    }
}
