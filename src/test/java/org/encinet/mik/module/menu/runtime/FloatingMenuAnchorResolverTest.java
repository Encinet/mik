package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.bukkit.block.data.BlockData;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuScale;
import org.encinet.mik.module.menu.FloatingMenuSize;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuAnchorResolverTest {
    @Test
    void framedWallKeepsTheSameAnchorEnvelopeWhenBodyFragmentsMoveOrDisappear() {
        BlockData data = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[] {BlockData.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("clone")) return proxy;
                    throw new AssertionError(method.getName());
                });
        FloatingMenuAnchorResolver.SceneBounds resting = null;
        for (int frame = 0; frame < 20; frame++) {
            var builder = FloatingMenuDefinition.screen("wall").stableAnchor().framingDecorations("surface");
            builder.decoration(FloatingMenuDecoration.volume("surface",
                    FloatingMenuPose.at(new FloatingMenuPoint(0, 0, -0.04)), data, 8.6F, 6.7F, 0.025F));
            builder.navigation("browse", Component.text("Browse"));
            Map<String, FloatingMenuPose> poses = Map.of("browse", FloatingMenuPose.at(new FloatingMenuPoint(0, -2.4, 0)));
            Map<String, FloatingMenuSize> sizes = Map.of("browse", new FloatingMenuSize(2, 0.6));
            for (int row = 0; row < frame; row++)
                builder.decoration(FloatingMenuDecoration.text("row:" + row,
                        new FloatingMenuPoint(frame / 5.0 - 2, row / 10.0, 0.015), Component.text("Moving text")).tracking());
            var bounds = FloatingMenuAnchorResolver.measure(builder.build(), poses, sizes);
            if (resting == null) resting = bounds;
            else assertEquals(resting, bounds);
            assertEquals(2, bounds.surfaces().size());
        }
    }

    @Test
    void blockVolumesFitTheirRotatedDepthAndAggregateIntoOneCollisionVolume() {
        BlockData data = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[] {BlockData.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("clone")) return proxy;
                    throw new AssertionError(method.getName());
                });
        var builder = FloatingMenuDefinition.screen("miniature");
        builder.decoration(FloatingMenuDecoration.volume("first",
                FloatingMenuPose.oriented(new FloatingMenuPoint(0, 0, 0), 45, 18), data, 2, 1, 3));
        builder.decoration(FloatingMenuDecoration.volume("second",
                FloatingMenuPose.at(new FloatingMenuPoint(2, 0, 0)), data, 1, 1, 1));
        var bounds = FloatingMenuAnchorResolver.measure(builder.build(), Map.of(), Map.of());
        assertEquals(1, bounds.surfaces().size());
        var volume = bounds.surfaces().getFirst();
        assertTrue(volume.depth() > 3);
        assertTrue(volume.size().width() > 4);
        assertTrue(volume.size().height() > 1.5);
        assertTrue(bounds.probes().stream().anyMatch(point -> point.forward() < -1.5));
        assertTrue(bounds.probes().stream().anyMatch(point -> point.forward() > 1.5));
    }

    private static final FloatingMenuAnchorResolver.SceneBounds CENTER_ONLY =
            new FloatingMenuAnchorResolver.SceneBounds(List.of(FloatingMenuPoint.ORIGIN));

    @Test
    void surroundingSceneUsesTheViewerAsItsCenter() {
        Location eye = new Location(world(direction -> Double.POSITIVE_INFINITY),
                12, 65, -4);

        FloatingMenuAnchorResolver.Anchor anchor =
                FloatingMenuAnchorResolver.aroundViewer(
                        eye, new Vector(0, 0, 1), 1.2);

        assertEquals(eye, anchor.origin());
        assertEquals(0.0, anchor.distance(), 0.0001);
        assertEquals(1.2, anchor.spatialScale(), 0.0001);
        assertEquals(1.0, anchor.forward().getZ(), 0.0001);
    }

    @Test
    void ringShrinksForEssentialFrontContentButNotForHiddenRearCards() {
        World world = world(direction -> direction.getZ() > 0.0
                ? 1.3 : 0.5);
        Location eye = new Location(world, 0, 64, 0);
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.screen("ring")
                .aroundViewer();
        builder.information("reader", Component.text("Current"))
                .keepAccessible();
        builder.control("rear", Component.text("Another"))
                .primary((player, menu) -> { });
        Map<String, FloatingMenuPose> poses = Map.of(
                "reader", FloatingMenuPose.at(new FloatingMenuPoint(0, 0, -2)),
                "rear", FloatingMenuPose.at(new FloatingMenuPoint(0, 0, 2)));
        Map<String, FloatingMenuSize> sizes = Map.of(
                "reader", new FloatingMenuSize(1, 0.5),
                "rear", new FloatingMenuSize(1, 0.5));

        FloatingMenuAnchorResolver.Anchor fitted = FloatingMenuAnchorResolver.aroundViewer(
                eye, new Vector(0, 0, 1), 1.0, builder.build(), poses, sizes);

        assertTrue(fitted.spatialScale() < 0.65);
        assertTrue(fitted.spatialScale() > 0.4);
        assertTrue(2.0 * fitted.spatialScale() < 1.3);
    }

    @Test
    void closeWallDoesNotTrapTheReaderOrBackControlBehindBlocks() {
        World world = world(direction -> direction.getZ() > 0.0
                ? 0.18 / direction.getZ() : Double.POSITIVE_INFINITY);
        Location eye = new Location(world, 0, 64, 0);
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.screen("ring")
                .aroundViewer();
        builder.information("reader", Component.text("Current announcement"))
                .keepAccessible();
        builder.navigation("back", Component.text("Back"))
                .keepAccessible().primary((player, menu) -> { });

        FloatingMenuAnchorResolver.Anchor fitted = FloatingMenuAnchorResolver.aroundViewer(
                eye, new Vector(0, 0, 1), 1.0, builder.build(),
                Map.of("reader", FloatingMenuPose.at(new FloatingMenuPoint(0, 0, -2.05)),
                        "back", FloatingMenuPose.at(new FloatingMenuPoint(0, -1.4, -1.9))),
                Map.of("reader", new FloatingMenuSize(3.8, 2.2),
                        "back", new FloatingMenuSize(1.0, 0.5)));

        assertTrue(fitted.spatialScale() < 0.18 / 2.05);
        assertTrue(fitted.spatialScale() * 2.05 > 0.05);
        assertTrue(fitted.spatialScale() * 2.05 < 0.18);
        assertTrue(FloatingMenuWorldOcclusion.clearToSurface(eye,
                fitted.spatialScale() * 2.05));
    }

    @Test
    void angledControlFitsItsRotatedEdgesBeforeTheWall() {
        World world = world(direction -> direction.getZ() > 0.0
                ? 0.30 / direction.getZ() : Double.POSITIVE_INFINITY);
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.screen("ring")
                .aroundViewer();
        builder.navigation("back", Component.text("Back"))
                .keepAccessible().primary((player, menu) -> { });

        FloatingMenuAnchorResolver.Anchor fitted = FloatingMenuAnchorResolver.aroundViewer(
                new Location(world, 0, 64, 0), new Vector(0, 0, 1), 1.0,
                builder.build(),
                Map.of("back", FloatingMenuPose.oriented(
                        new FloatingMenuPoint(1.4, 0.0, -1.4), 45.0, 0.0)),
                Map.of("back", new FloatingMenuSize(2.0, 0.5)));

        double farEdgeDepth = 1.4 + Math.sin(Math.toRadians(45.0));
        assertTrue(fitted.spatialScale() < 0.14);
        assertTrue(farEdgeDepth * fitted.spatialScale() < 0.30);
    }

    @Test
    void keepsAnUnobstructedSceneCenteredAndWithinEntityReach() {
        Location eye = new Location(world(direction -> Double.POSITIVE_INFINITY), 0, 64, 0);

        FloatingMenuAnchorResolver.Anchor anchor = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), CENTER_ONLY);

        assertEquals(FloatingMenuAnchorResolver.PREFERRED_DISTANCE, anchor.distance(), 0.0001);
        assertEquals(0.0, anchor.yawOffsetDegrees(), 0.0001);
        assertEquals(2.8, anchor.origin().getZ(), 0.0001);
        assertEquals(FloatingMenuAnchorResolver.STANDARD_SCENE_SCALE,
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
    void onlyExceptionalScenesShrinkToTheMaximumReadingEnvelope() {
        Location eye = new Location(world(direction -> Double.POSITIVE_INFINITY), 0, 64, 0);
        FloatingMenuAnchorResolver.SceneBounds wide =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-8.0, -5.0, 0.0),
                        new FloatingMenuPoint(8.0, 5.0, 0.0)));

        FloatingMenuAnchorResolver.Anchor anchor = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), wide);

        assertTrue(anchor.spatialScale()
                < anchor.distance() / FloatingMenuAnchorResolver.REFERENCE_LAYOUT_DISTANCE);
        assertTrue(8.0 * anchor.spatialScale()
                <= anchor.distance()
                * FloatingMenuAnchorResolver.MAX_READING_FRAMING.horizontalTangent() + 0.0001);
        assertTrue(5.0 * anchor.spatialScale()
                <= anchor.distance()
                * FloatingMenuAnchorResolver.MAX_READING_FRAMING.verticalTangent() + 0.0001);
    }

    @Test
    void densePagesRespectVerticalReadingLimitsWithoutShrinkingOrdinaryPages() {
        Location eye = new Location(world(direction -> Double.POSITIVE_INFINITY), 0, 64, 0);
        FloatingMenuAnchorResolver.SceneBounds ordinary =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-1.2, -0.7, 0.0),
                        new FloatingMenuPoint(1.2, 0.7, 0.0)));
        FloatingMenuAnchorResolver.SceneBounds fuller =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-4.0, -2.5, 0.0),
                        new FloatingMenuPoint(4.0, 2.5, 0.0)));

        FloatingMenuAnchorResolver.Anchor smallPage = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), ordinary);
        FloatingMenuAnchorResolver.Anchor fullPage = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), fuller);

        assertEquals(FloatingMenuAnchorResolver.STANDARD_SCENE_SCALE,
                smallPage.spatialScale(), 0.0001);
        assertTrue(fullPage.spatialScale() < smallPage.spatialScale());
        assertTrue(Math.toDegrees(Math.atan2(2.5 * fullPage.spatialScale(), fullPage.distance())) <= 35.0001);
    }

    @Test
    void declaredFramingDoesNotResizeTheSamePage() {
        Location eye = new Location(world(direction -> Double.POSITIVE_INFINITY), 0, 64, 0);
        List<FloatingMenuPoint> footprint = List.of(
                new FloatingMenuPoint(-8.0, -5.0, 0.0),
                new FloatingMenuPoint(8.0, 5.0, 0.0));
        FloatingMenuAnchorResolver.SceneBounds comfortable =
                new FloatingMenuAnchorResolver.SceneBounds(footprint);
        FloatingMenuAnchorResolver.SceneBounds panoramic =
                new FloatingMenuAnchorResolver.SceneBounds(
                        footprint, FloatingMenuFraming.PANORAMIC);

        FloatingMenuAnchorResolver.Anchor normalAnchor = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), comfortable);
        FloatingMenuAnchorResolver.Anchor panoramicAnchor = FloatingMenuAnchorResolver.resolve(
                eye, new Vector(0, 0, 1), panoramic);

        assertEquals(normalAnchor.spatialScale(), panoramicAnchor.spatialScale(), 0.0001);
    }

    @Test
    void forwardDepthCountsTowardTheMaximumReadingAngle() {
        FloatingMenuAnchorResolver.SceneBounds forward =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(3.0, 0.0, 1.2)));

        double scale = forward.readableScale(FloatingMenuAnchorResolver.PREFERRED_DISTANCE);

        assertTrue(scale < FloatingMenuAnchorResolver.PREFERRED_DISTANCE
                * FloatingMenuAnchorResolver.MAX_READING_FRAMING.horizontalTangent() / 3.0);
        double angle = Math.toDegrees(Math.atan2(3.0 * scale,
                FloatingMenuAnchorResolver.PREFERRED_DISTANCE - 1.2 * scale));
        assertTrue(angle <= FloatingMenuAnchorResolver.MAX_READING_FRAMING
                .horizontalHalfAngleDegrees() + 0.0001);
    }

    @Test
    void worldInteractionConeFollowsTheVisibleScene() {
        FloatingMenuAnchorResolver.SceneBounds full =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-4.0, -2.5, 0.0),
                        new FloatingMenuPoint(4.0, 2.5, 0.0)));

        FloatingMenuFraming interaction = full.interactionFraming(
                FloatingMenuAnchorResolver.PREFERRED_DISTANCE,
                FloatingMenuAnchorResolver.STANDARD_SCENE_SCALE);

        assertTrue(interaction.horizontalHalfAngleDegrees()
                > FloatingMenuFraming.COMFORTABLE.horizontalHalfAngleDegrees());
        for (FloatingMenuPoint point : full.probes()) {
            double toward = FloatingMenuAnchorResolver.PREFERRED_DISTANCE
                    - point.forward() * FloatingMenuAnchorResolver.STANDARD_SCENE_SCALE;
            assertTrue(interaction.horizontalHalfAngleDegrees() >= Math.toDegrees(Math.atan2(
                    Math.abs(point.right()) * FloatingMenuAnchorResolver.STANDARD_SCENE_SCALE, toward)));
        }
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

        assertEquals(FloatingMenuAnchorResolver.STANDARD_SCENE_SCALE
                        * FloatingMenuScale.SMALL.factor(),
                small.spatialScale(), 0.0001);
        assertEquals(FloatingMenuAnchorResolver.STANDARD_SCENE_SCALE
                        * FloatingMenuScale.LARGE.factor(),
                large.spatialScale(), 0.0001);
    }

    @Test
    void smallPreferenceRemainsVisibleButLargePreferenceCannotExceedTheReadingLimit() {
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
        assertEquals(normal.spatialScale(), large.spatialScale(), 0.0001);
        assertTrue(small.spatialScale() < normal.spatialScale());
        assertTrue(Math.toDegrees(Math.atan2(3 * large.spatialScale(), large.distance())) <= 35.001);
    }

    @Test
    void widePagesDoNotExpandTheVerticalInteractionCone() {
        var bounds = new FloatingMenuAnchorResolver.SceneBounds(List.of(
                new FloatingMenuPoint(-6, -0.1, 0), new FloatingMenuPoint(6, 0.1, 0)));
        var framing = bounds.interactionFraming(2.8, 1);
        assertTrue(framing.horizontalHalfAngleDegrees() > 60);
        assertEquals(FloatingMenuFraming.COMFORTABLE.verticalHalfAngleDegrees(), framing.verticalHalfAngleDegrees());
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
                    case "isChunkLoaded" -> true;
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "getBlockAt" -> FloatingMenuTestWorld.air();
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
