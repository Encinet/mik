package org.encinet.mik.module.menu;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuAnchorResolverTest {

    private static final FloatingMenuAnchorResolver.SceneBounds CENTER_ONLY =
            new FloatingMenuAnchorResolver.SceneBounds(List.of(FloatingMenuPoint.ORIGIN));

    @Test
    void keepsAnUnobstructedSceneCenteredAndWithinEntityReach() {
        Location eye = new Location(world(direction -> Double.POSITIVE_INFINITY), 0, 64, 0);

        FloatingMenuAnchorResolver.Anchor anchor = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), CENTER_ONLY);

        assertEquals(FloatingMenuAnchorResolver.PREFERRED_DISTANCE, anchor.distance(), 0.0001);
        assertEquals(0.0, anchor.yawOffsetDegrees(), 0.0001);
        assertEquals(2.8, anchor.origin().getZ(), 0.0001);
        assertEquals(FloatingMenuAnchorResolver.HIGH_FOV_BASE_SCALE,
                anchor.spatialScale(), 0.0001);
    }

    @Test
    void movesSidewaysWhenTheForwardSceneVolumeIsBlocked() {
        World world = world(direction -> {
            double horizontalAngle = Math.abs(Math.toDegrees(
                    Math.atan2(direction.getX(), direction.getZ())));
            return horizontalAngle < 8.0 ? 1.45 : Double.POSITIVE_INFINITY;
        });
        Location eye = new Location(world, 0, 64, 0);

        FloatingMenuAnchorResolver.Anchor anchor = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), CENTER_ONLY);

        assertEquals(FloatingMenuAnchorResolver.PREFERRED_DISTANCE, anchor.distance(), 0.0001);
        assertEquals(16.0, Math.abs(anchor.yawOffsetDegrees()), 0.0001);
        assertTrue(Math.abs(anchor.origin().getX()) > 0.5);
    }

    @Test
    void tightFallbackStaysBeforeTheWallAndDoesNotGrowIntoThePlayersFace() {
        Location eye = new Location(world(direction -> 0.95), 0, 64, 0);

        FloatingMenuAnchorResolver.Anchor anchor = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), CENTER_ONLY);

        assertTrue(anchor.distance() < FloatingMenuAnchorResolver.MIN_COMFORTABLE_DISTANCE);
        assertTrue(anchor.distance() < 0.95);
        assertEquals(anchor.distance() / FloatingMenuAnchorResolver.REFERENCE_LAYOUT_DISTANCE,
                anchor.spatialScale(), 0.0001);
        assertEquals(0.0, anchor.yawOffsetDegrees(), 0.0001);
    }

    @Test
    void largeScenesShrinkToAComfortableAngularEnvelope() {
        Location eye = new Location(world(direction -> Double.POSITIVE_INFINITY), 0, 64, 0);
        FloatingMenuAnchorResolver.SceneBounds wide =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-5.0, -3.0, 0.0),
                        new FloatingMenuPoint(5.0, 3.0, 0.0)));

        FloatingMenuAnchorResolver.Anchor anchor = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), wide);

        assertTrue(anchor.spatialScale()
                < anchor.distance() / FloatingMenuAnchorResolver.REFERENCE_LAYOUT_DISTANCE);
        assertTrue(5.0 * anchor.spatialScale()
                <= anchor.distance() * Math.tan(Math.toRadians(42.0)) + 0.0001);
        assertTrue(3.0 * anchor.spatialScale()
                <= anchor.distance() * Math.tan(Math.toRadians(32.0)) + 0.0001);
    }

    @Test
    void highFovBaselineScalesLayoutAndHitGeometryFromOneSharedFactor() {
        Location eye = new Location(world(direction -> Double.POSITIVE_INFINITY), 0, 64, 0);
        FloatingMenuAnchorResolver.SceneBounds ordinary =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-1.2, -0.7, 0.0),
                        new FloatingMenuPoint(1.2, 0.7, 0.0)));

        FloatingMenuAnchorResolver.Anchor anchor = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), ordinary);

        assertEquals(1.28, anchor.spatialScale(), 0.0001);
        assertTrue(1.2 * anchor.spatialScale()
                < anchor.distance() * Math.tan(Math.toRadians(42.0)));
    }

    @Test
    void panoramicFramingLetsWideScenesOccupyMoreOfTheView() {
        Location eye = new Location(world(direction -> Double.POSITIVE_INFINITY), 0, 64, 0);
        List<FloatingMenuPoint> footprint = List.of(
                new FloatingMenuPoint(-4.0, -2.0, 0.0),
                new FloatingMenuPoint(4.0, 2.0, 0.0));
        FloatingMenuAnchorResolver.SceneBounds comfortable =
                new FloatingMenuAnchorResolver.SceneBounds(footprint);
        FloatingMenuAnchorResolver.SceneBounds panoramic =
                new FloatingMenuAnchorResolver.SceneBounds(
                        footprint, FloatingMenuFraming.PANORAMIC);

        FloatingMenuAnchorResolver.Anchor normalAnchor = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), comfortable);
        FloatingMenuAnchorResolver.Anchor panoramicAnchor = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), panoramic);

        assertTrue(panoramicAnchor.spatialScale() > normalAnchor.spatialScale());
        assertTrue(4.0 * panoramicAnchor.spatialScale()
                <= panoramicAnchor.distance()
                * FloatingMenuFraming.PANORAMIC.horizontalTangent() + 0.0001);
    }

    @Test
    void playerInterfaceScaleChangesAnOrdinarySceneAsOneSharedFactor() {
        Location eye = new Location(world(direction -> Double.POSITIVE_INFINITY), 0, 64, 0);

        FloatingMenuAnchorResolver.Anchor small = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), CENTER_ONLY,
                FloatingMenuScale.SMALL.factor());
        FloatingMenuAnchorResolver.Anchor large = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), CENTER_ONLY,
                FloatingMenuScale.LARGE.factor());

        assertEquals(FloatingMenuAnchorResolver.HIGH_FOV_BASE_SCALE
                        * FloatingMenuScale.SMALL.factor(),
                small.spatialScale(), 0.0001);
        assertEquals(FloatingMenuAnchorResolver.HIGH_FOV_BASE_SCALE
                        * FloatingMenuScale.LARGE.factor(),
                large.spatialScale(), 0.0001);
    }

    @Test
    void preferenceRemainsVisibleEvenWhenAutomaticFramingLimitsTheScene() {
        Location eye = new Location(world(direction -> Double.POSITIVE_INFINITY), 0, 64, 0);
        FloatingMenuAnchorResolver.SceneBounds wide =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-5.0, -3.0, 0.0),
                        new FloatingMenuPoint(5.0, 3.0, 0.0)));

        FloatingMenuAnchorResolver.Anchor normal = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), wide,
                FloatingMenuScale.NORMAL.factor());
        FloatingMenuAnchorResolver.Anchor small = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), wide,
                FloatingMenuScale.SMALL.factor());
        FloatingMenuAnchorResolver.Anchor large = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), wide,
                FloatingMenuScale.LARGE.factor());

        assertEquals(normal.spatialScale() * FloatingMenuScale.SMALL.factor(),
                small.spatialScale(), 0.0001);
        assertEquals(normal.spatialScale() * FloatingMenuScale.LARGE.factor(),
                large.spatialScale(), 0.0001);
        assertTrue(small.spatialScale() < normal.spatialScale());
        assertTrue(large.spatialScale() > normal.spatialScale());
    }

    private static World world(ToDoubleFunction<Vector> firstHitDistance) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "rayTraceBlocks" -> {
                        Location start = (Location) args[0];
                        Vector direction = ((Vector) args[1]).clone().normalize();
                        double maximumDistance = (double) args[2];
                        double hitDistance = firstHitDistance.applyAsDouble(direction);
                        yield !Double.isFinite(hitDistance) || hitDistance > maximumDistance
                                ? null : new RayTraceResult(start.toVector()
                                .add(direction.multiply(hitDistance)));
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "getName" -> "anchor-test";
                    case "toString" -> "AnchorTestWorld";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new AssertionError(type);
    }
}
