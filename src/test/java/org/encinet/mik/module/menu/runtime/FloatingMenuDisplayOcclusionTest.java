package org.encinet.mik.module.menu.runtime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuSize;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuDisplayOcclusionTest {
    private static final Vector RIGHT = new Vector(1, 0, 0);
    private static final Vector UP = new Vector(0, 1, 0);

    @Test
    void catchesAThinOccluderBetweenTheCenterAndCornerRays() {
        var blocker = box(new Vector(0.22, -0.1, 1), new Vector(0.26, 0.1, 1.1));
        double factor = limit(List.of(blocker));
        assertTrue(factor > 0.4 && factor < 0.5);
        assertEquals(1, FloatingMenuDisplayOcclusion.limit(new Vector(0, 0, 2 * factor), RIGHT, UP,
                factor, factor, 0.1 * factor, List.of(blocker)), 1.0E-7);
    }

    @Test
    void avoidsTheWholeButtonEvenIfItsCenterRayIsClear() {
        var blocker = box(new Vector(0.45, -0.1, 1), new Vector(0.49, 0.1, 1.1));
        assertTrue(limit(List.of(blocker)) < 0.5);
    }

    @Test
    void ignoresBehindBeyondAndOffscreenGeometryWithoutFalseDepthCompression() {
        assertEquals(1, limit(List.of(box(new Vector(-1, -1, -2), new Vector(1, 1, -1)))));
        assertEquals(1, limit(List.of(box(new Vector(-1, -1, 3), new Vector(1, 1, 4)))));
        assertEquals(1, limit(List.of(box(new Vector(0.9, -0.1, 1), new Vector(1, 0.1, 1.1)))));
    }

    @Test
    void closestOccluderWinsRegardlessOfIterationOrder() {
        var near = box(new Vector(-0.1, -0.1, 0.6), new Vector(0.1, 0.1, 0.7));
        var far = box(new Vector(-0.1, -0.1, 1.6), new Vector(0.1, 0.1, 1.7));
        assertEquals(limit(List.of(near)), limit(List.of(far, near)));
        assertEquals(limit(List.of(near)), limit(List.of(near, far)));
    }

    @Test
    void translatedDisplaysUseTheirRenderedGeometryAtFarCoordinates() {
        var eye = new Location(null, 20_000_000.125, 80.5, -20_000_000.375);
        var location = eye.clone().add(20, 0, 1);
        var transformation = new Transformation(new Vector3f(-20, -0.1f, 0), new Quaternionf(),
                new Vector3f(0.4f, 0.2f, 0.3f), new Quaternionf());
        var blocker = FloatingMenuDisplayOcclusion.box(eye,
                display(BlockDisplay.class, location, transformation, Display.Billboard.FIXED));
        assertNotNull(blocker);
        assertEquals(0, blocker.envelope().getMinX(), 1.0E-7);
        assertEquals(1, blocker.envelope().getMinZ(), 1.0E-7);
        assertTrue(limit(List.of(blocker)) < 0.5);
    }

    @Test
    void rotatedAndScaledDisplaysKeepBothQuaternionRotations() {
        var eye = new Location(null, 0, 0, 0);
        var rotated = FloatingMenuDisplayOcclusion.box(eye,
                display(BlockDisplay.class, eye.clone().add(0, 0, 1), new Transformation(new Vector3f(),
                        new Quaternionf().rotateZ(0.3f), new Vector3f(0.4f, 0.2f, 0.3f),
                        new Quaternionf().rotateY(0.4f)), Display.Billboard.FIXED));
        assertTrue(limit(List.of(rotated)) < 0.5);
    }

    @Test
    void includesEntityYawAndPitchBeforeTheLocalTransform() {
        var eye = new Location(null, 0, 0, 0);
        var location = new Location(null, 0, 0, 1, 90, 30);
        var blocker = FloatingMenuDisplayOcclusion.box(eye, display(BlockDisplay.class, location,
                new Transformation(new Vector3f(0, 0, -3), new Quaternionf(), new Vector3f(0.1f),
                        new Quaternionf()), Display.Billboard.FIXED));
        assertTrue(blocker.envelope().getMinX() > 2.4);
        assertTrue(blocker.envelope().getMinY() > 1.4);
        assertEquals(1, limit(List.of(blocker)));
    }

    @Test
    void centeredItemsAndCameraBillboardsHaveConservativeGeometry() {
        var eye = new Location(null, 0, 0, 0);
        var transformation = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.2f),
                new Quaternionf());
        var item = FloatingMenuDisplayOcclusion.box(eye, display(ItemDisplay.class, eye.clone().add(0, 0, 1),
                transformation, Display.Billboard.FIXED));
        assertEquals(-0.1, item.envelope().getMinX(), 1.0E-7);
        assertTrue(limit(List.of(item)) < 0.5);
        var billboard = FloatingMenuDisplayOcclusion.box(eye,
                display(ItemDisplay.class, eye.clone().add(0, 0, 1), transformation, Display.Billboard.CENTER));
        assertTrue(limit(List.of(billboard)) <= limit(List.of(item)));
    }

    @Test
    void singularNonfiniteAndEyeContainingGeometryNeverProducesNan() {
        var eye = new Location(null, 0, 0, 0);
        assertNull(FloatingMenuDisplayOcclusion.box(eye, display(BlockDisplay.class, eye,
                new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0), new Quaternionf()),
                Display.Billboard.FIXED)));
        assertNull(FloatingMenuDisplayOcclusion.box(eye, display(BlockDisplay.class, eye,
                new Transformation(new Vector3f(Float.NaN, 0, 0), new Quaternionf(), new Vector3f(1), new Quaternionf()),
                Display.Billboard.FIXED)));
        assertEquals(0.001, limit(List.of(box(new Vector(-1, -1, -1), new Vector(1, 1, 1)))));
    }

    @Test
    void obliquePanelsUseTheirOwnPlaneAndDepthRatherThanWorldZ() {
        double diagonal = Math.sqrt(0.5);
        var normal = new Vector(diagonal, 0, diagonal);
        var right = new Vector(diagonal, 0, -diagonal);
        var blocker = box(normal.clone().subtract(new Vector(0.05, 0.05, 0.05)),
                normal.clone().add(new Vector(0.05, 0.05, 0.05)));
        double factor = FloatingMenuDisplayOcclusion.limit(normal.clone().multiply(2), right, UP,
                1, 1, 0.1, List.of(blocker));
        assertTrue(factor > 0.35 && factor < 0.5);
    }

    @Test
    void contractionPreservesAngularSizeAndThePickingSurface() {
        var eye = new Location(null, 10, 20, 30);
        var anchor = new FloatingMenuAnchorResolver.Anchor(eye.clone().add(0, 0.2, 2),
                new Vector(0, 0, 1), RIGHT, 0.8, 2, 0);
        var fitted = FloatingMenuDisplayOcclusion.contract(eye, anchor, 0.4);
        assertEquals(0.32, fitted.spatialScale(), 1.0E-9);
        assertEquals(0.8, fitted.distance(), 1.0E-9);
        Vector before = anchor.origin().toVector().subtract(eye.toVector());
        Vector after = fitted.origin().toVector().subtract(eye.toVector());
        assertEquals(1, before.normalize().dot(after.clone().normalize()), 1.0E-9);
        var hit = FloatingMenuSurfaceGeometry.intersect(eye.toVector(), after, fitted.origin().toVector(),
                0, 0, fitted.spatialScale(), fitted.spatialScale(), 0);
        assertTrue(hit.isPresent());
        assertEquals(after.length(), hit.orElseThrow().distance(), 1.0E-9);
    }

    @Test
    void shrinksImmediatelyButRecoversGraduallyAndNeverExceedsTheSafeTarget() {
        assertEquals(0.2, FloatingMenuDisplayOcclusion.recoveryScale(1, 0.2));
        assertEquals(0.216, FloatingMenuDisplayOcclusion.recoveryScale(0.2, 1), 1.0E-9);
        assertEquals(0.21, FloatingMenuDisplayOcclusion.recoveryScale(0.2, 0.21));
        assertEquals(1, FloatingMenuDisplayOcclusion.recoveryScale(0, 1));
    }

    @Test
    void broadPhaseUsesTheTransformedPositionNotTheEntityOrigin() {
        var eye = new Location(null, 0, 0, 0);
        var transformation = new Transformation(new Vector3f(0, 0, -80), new Quaternionf(),
                new Vector3f(0.2f), new Quaternionf());
        assertNotNull(FloatingMenuDisplayOcclusion.box(eye,
                display(BlockDisplay.class, eye.clone().add(0, 0, 81), transformation, Display.Billboard.FIXED), 3));
        assertNull(FloatingMenuDisplayOcclusion.box(eye,
                display(BlockDisplay.class, eye.clone().add(0, 0, 100), transformation, Display.Billboard.FIXED), 3));
    }

    @Test
    void onlyVisibleValidDisplaysAffectTheCompleteSceneFit() {
        List<Display> entities = new ArrayList<>();
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getEntitiesByClasses" -> entities;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var eye = new Location(world, 0, 0, 0);
        var transformation = new Transformation(new Vector3f(-0.1f, -0.1f, 0), new Quaternionf(),
                new Vector3f(0.2f), new Quaternionf());
        Display hidden = display(BlockDisplay.class, eye.clone().add(0, 0, 0.1), transformation, Display.Billboard.FIXED);
        Display invalid = display(BlockDisplay.class, eye.clone().add(0, 0, 0.2), transformation, Display.Billboard.FIXED, false);
        Display visible = display(BlockDisplay.class, eye.clone().add(0, 0, 0.8), transformation, Display.Billboard.FIXED);
        entities.addAll(List.of(hidden, invalid, visible));
        Player viewer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "canSee" -> arguments[0] != hidden;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var anchor = new FloatingMenuAnchorResolver.Anchor(eye.clone().add(0, 0, 2),
                new Vector(0, 0, 1), RIGHT, 1, 2, 0);
        var bounds = new FloatingMenuAnchorResolver.SceneBounds(List.of(FloatingMenuPoint.ORIGIN),
                FloatingMenuFraming.COMFORTABLE, List.of(new FloatingMenuAnchorResolver.Surface(
                FloatingMenuPose.at(FloatingMenuPoint.ORIGIN), new FloatingMenuSize(2, 2))));
        var fitted = FloatingMenuDisplayOcclusion.fit(viewer, eye, anchor, bounds, 0);
        assertTrue(fitted.distance() > 0.6 && fitted.distance() < 0.8);
        entities.removeLast();
        assertEquals(2, FloatingMenuDisplayOcclusion.fit(viewer, eye, anchor, bounds, 0).distance());
        assertEquals(1, anchor.spatialScale());
    }

    @Test
    void repeatedFitsObserveMovingDisplayGeometryAndRecoverWhenItLeaves() {
        List<Display> entities = new ArrayList<>();
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getEntitiesByClasses" -> entities;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Player viewer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "canSee" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var eye = new Location(world, 0, 0, 0);
        var location = eye.clone().add(0, 0, 1);
        entities.add(display(BlockDisplay.class, location, new Transformation(new Vector3f(-0.1f, -0.1f, 0),
                new Quaternionf(), new Vector3f(0.2f), new Quaternionf()), Display.Billboard.FIXED));
        var anchor = new FloatingMenuAnchorResolver.Anchor(eye.clone().add(0, 0, 2),
                new Vector(0, 0, 1), RIGHT, 1, 2, 0);
        var bounds = new FloatingMenuAnchorResolver.SceneBounds(List.of(FloatingMenuPoint.ORIGIN),
                FloatingMenuFraming.COMFORTABLE, List.of(new FloatingMenuAnchorResolver.Surface(
                FloatingMenuPose.at(FloatingMenuPoint.ORIGIN), new FloatingMenuSize(2, 2))));
        double previous = FloatingMenuDisplayOcclusion.fit(viewer, eye, anchor, bounds, 0).distance();
        location.setZ(0.4);
        assertTrue(FloatingMenuDisplayOcclusion.fit(viewer, eye, anchor, bounds, 0).distance() < previous);
        location.setZ(20);
        assertEquals(2, FloatingMenuDisplayOcclusion.fit(viewer, eye, anchor, bounds, 0).distance());
    }

    private static double limit(List<FloatingMenuDisplayOcclusion.Box> boxes) {
        return FloatingMenuDisplayOcclusion.limit(new Vector(0, 0, 2), RIGHT, UP, 1, 1, 0.1, boxes);
    }

    private static FloatingMenuDisplayOcclusion.Box box(Vector minimum, Vector maximum) {
        List<Vector> vertices = new ArrayList<>(8);
        for (int corner = 0; corner < 8; corner++) {
            vertices.add(new Vector((corner & 1) == 0 ? minimum.getX() : maximum.getX(),
                    (corner & 2) == 0 ? minimum.getY() : maximum.getY(),
                    (corner & 4) == 0 ? minimum.getZ() : maximum.getZ()));
        }
        return new FloatingMenuDisplayOcclusion.Box(vertices, minimum.getX() <= 0 && maximum.getX() >= 0
                && minimum.getY() <= 0 && maximum.getY() >= 0 && minimum.getZ() <= 0 && maximum.getZ() >= 0);
    }

    private static Display display(Class<? extends Display> type, Location location,
                                   Transformation transformation, Display.Billboard billboard) {
        return display(type, location, transformation, billboard, true);
    }

    private static Display display(Class<? extends Display> type, Location location,
                                   Transformation transformation, Display.Billboard billboard, boolean valid) {
        BlockData block = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[]{BlockData.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMaterial" -> Material.STONE;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Display) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getLocation" -> location.clone();
                    case "getTransformation" -> transformation;
                    case "getBillboard" -> billboard;
                    case "getBlock" -> block;
                    case "isValid" -> valid;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
