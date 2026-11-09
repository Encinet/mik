package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuFraming;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.bukkit.block.data.BlockData;
import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuScale;
import org.encinet.mik.shared.menu.FloatingMenuSize;

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

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry;

class FloatingMenuAnchorResolverTest {
    @Test
    void framedWallKeepsTheSameAnchorEnvelopeWhenBodyFragmentsMoveOrDisappear() {
        BlockData data = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[] {BlockData.class}, (proxy, method, _) -> {
                    if (method.getName().equals("clone")) return proxy;
                    throw new AssertionError(method.getName());
                });
        FloatingMenuAnchorResolver.SceneBounds resting = null;
        for (int frame = 0; frame < 20; frame++) {
            var builder = MenuScene.screen("wall").stableAnchor().framingDecorations("surface");
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
                new Class<?>[] {BlockData.class}, (proxy, method, _) -> {
                    if (method.getName().equals("clone")) return proxy;
                    throw new AssertionError(method.getName());
                });
        var builder = MenuScene.screen("miniature");
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

    @Test void modelDecorationsRetainTheirDepthInBothTargetAndMotionGeometry() {
        var pose = new FloatingMenuPose(new FloatingMenuPoint(.2, .3, 0), 35, 20, 17, 1.4);
        var decoration = FloatingMenuDecoration.item("model", pose, SceneGeometryTestSupport.item(), 2, FloatingMenuDecoration.Motion.NONE);
        var scene = MenuScene.screen("model").decoration(decoration).build();
        var bounds = FloatingMenuAnchorResolver.measure(scene, Map.of(), Map.of());
        var surface = FloatingMenuAnchorResolver.decorationSurface(decoration, pose, 1);
        assertEquals(2, surface.depth());
        assertEquals(surface, bounds.surfaces().getFirst());
        assertTrue(bounds.probes().stream().anyMatch(point -> point.forward() > 1));
        assertTrue(bounds.probes().stream().anyMatch(point -> point.forward() < -1));
        var move = new MenuLayoutMotion.Move(pose, pose.offset(.5, 0, 0), surface.size(), surface.depth(), MenuPoseTransition.Path.LINEAR);
        assertTrue(MenuLayoutMotion.envelope(List.of(move)).orElseThrow().surfaces().getFirst().depth() >= 2);
    }

    private static final FloatingMenuAnchorResolver.SceneBounds CENTER_ONLY =
            new FloatingMenuAnchorResolver.SceneBounds(List.of(FloatingMenuPoint.ORIGIN));

    @Test
    void surroundingSceneUsesTheViewerAsItsCenter() {
        Location eye = new Location(world(_ -> Double.POSITIVE_INFINITY),
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
        MenuScene.Builder builder = MenuScene.screen("ring")
                .aroundViewer();
        builder.information("reader", Component.text("Current"))
                .keepAccessible();
        builder.control("rear", Component.text("Another"))
                .primary((_, _) -> { });
        MenuScene definition = builder.build();
        Map<String, FloatingMenuPose> poses = Map.of(
                entry(definition, "reader").id(), FloatingMenuPose.at(new FloatingMenuPoint(0, 0, -2)),
                entry(definition, "rear").id(), FloatingMenuPose.at(new FloatingMenuPoint(0, 0, 2)));
        Map<String, FloatingMenuSize> sizes = Map.of(
                entry(definition, "reader").id(), new FloatingMenuSize(1, 0.5),
                entry(definition, "rear").id(), new FloatingMenuSize(1, 0.5));

        FloatingMenuAnchorResolver.Anchor fitted = FloatingMenuAnchorResolver.aroundViewer(
                eye, new Vector(0, 0, 1), 1.0, definition, poses, sizes, java.util.Optional.empty());

        assertTrue(fitted.spatialScale() < 0.65);
        assertTrue(fitted.spatialScale() > 0.4);
        assertTrue(2.0 * fitted.spatialScale() < 1.3);
    }

    @Test
    void closeWallDoesNotTrapTheReaderOrBackControlBehindBlocks() {
        World world = world(direction -> direction.getZ() > 0.0
                ? 0.18 / direction.getZ() : Double.POSITIVE_INFINITY);
        Location eye = new Location(world, 0, 64, 0);
        MenuScene.Builder builder = MenuScene.screen("ring")
                .aroundViewer();
        builder.information("reader", Component.text("Current announcement"))
                .keepAccessible();
        builder.navigation("back", Component.text("Back"))
                .keepAccessible().primary((_, _) -> { });
        MenuScene definition = builder.build();
        FloatingMenuAnchorResolver.Anchor fitted = FloatingMenuAnchorResolver.aroundViewer(
                eye, new Vector(0, 0, 1), 1.0, definition,
                Map.of(entry(definition, "reader").id(), FloatingMenuPose.at(new FloatingMenuPoint(0, 0, -2.05)),
                        entry(definition, "back").id(), FloatingMenuPose.at(new FloatingMenuPoint(0, -1.4, -1.9))),
                Map.of(entry(definition, "reader").id(), new FloatingMenuSize(3.8, 2.2),
                        entry(definition, "back").id(), new FloatingMenuSize(1.0, 0.5)), java.util.Optional.empty());

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
        MenuScene.Builder builder = MenuScene.screen("ring")
                .aroundViewer();
        builder.navigation("back", Component.text("Back"))
                .keepAccessible().primary((_, _) -> { });
        MenuScene definition = builder.build();
        FloatingMenuAnchorResolver.Anchor fitted = FloatingMenuAnchorResolver.aroundViewer(
                new Location(world, 0, 64, 0), new Vector(0, 0, 1), 1.0,
                definition,
                Map.of(entry(definition, "back").id(), FloatingMenuPose.oriented(
                        new FloatingMenuPoint(1.4, 0.0, -1.4), 45.0, 0.0)),
                Map.of(entry(definition, "back").id(), new FloatingMenuSize(2.0, 0.5)), java.util.Optional.empty());

        double farEdgeDepth = 1.4 + Math.sin(Math.toRadians(45.0));
        assertTrue(fitted.spatialScale() < 0.14);
        assertTrue(farEdgeDepth * fitted.spatialScale() < 0.30);
    }

    @Test
    void keepsAnUnobstructedSceneCenteredAndWithinEntityReach() {
        Location eye = new Location(world(_ -> Double.POSITIVE_INFINITY), 0, 64, 0);

        FloatingMenuAnchorResolver.Anchor anchor = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), CENTER_ONLY);

        assertEquals(MenuViewportLayout.DEFAULT_DISTANCE, anchor.distance(), 0.0001);
        assertEquals(0.0, anchor.yawOffsetDegrees(), 0.0001);
        assertEquals(2.8, anchor.origin().getZ(), 0.0001);
        assertEquals(MenuViewportLayout.DEFAULT_DISTANCE / MenuViewportLayout.referenceDistance(FloatingMenuFieldOfView.DEFAULT,1),
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

        FloatingMenuAnchorResolver.Anchor anchor = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), CENTER_ONLY);

        assertEquals(MenuViewportLayout.DEFAULT_DISTANCE, anchor.distance(), 0.0001);
        assertEquals(16.0, Math.abs(anchor.yawOffsetDegrees()), 0.0001);
        assertTrue(Math.abs(anchor.origin().getX()) > 0.5);
    }

    @Test
    void anUnavailableCenteredCandidateCannotOutrankASmallButValidSideCandidate() {
        World world = world(direction -> {
            double angle = Math.abs(Math.toDegrees(Math.atan2(direction.getX(), direction.getZ())));
            return angle < 8 ? 0 : .35;
        });
        Location eye = new Location(world,0,64,0);
        var anchor = MenuAnchorTestSupport.front(eye,new Vector(0,0,1),CENTER_ONLY);
        assertTrue(anchor.distance() > 0 && anchor.distance() < .35);
        assertEquals(16,Math.abs(anchor.yawOffsetDegrees()),1e-6);
        assertEquals(MenuViewportLayout.scale(1) / MenuViewportLayout.distance(FloatingMenuFieldOfView.DEFAULT),
                anchor.spatialScale() / anchor.distance(),1e-8);
    }

    @Test
    void aTightSpaceContractsTheMenuAndDistanceTogetherWithoutChangingItsApparentSize() {
        Location eye = new Location(world(_ -> 0.95), 0, 64, 0);

        var anchor = MenuAnchorTestSupport.front(eye,new Vector(0,0,1),CENTER_ONLY);
        assertTrue(anchor.distance() > 0 && anchor.distance() < .95);
        assertEquals(MenuViewportLayout.scale(1) / MenuViewportLayout.distance(FloatingMenuFieldOfView.DEFAULT),
                anchor.spatialScale() / anchor.distance(), 1e-8);
    }

    @Test
    void onlyExceptionalScenesShrinkToTheMaximumReadingEnvelope() {
        Location eye = new Location(world(_ -> Double.POSITIVE_INFINITY), 0, 64, 0);
        FloatingMenuAnchorResolver.SceneBounds wide =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-40.0, -25.0, 0.0),
                        new FloatingMenuPoint(40.0, 25.0, 0.0)));

        FloatingMenuAnchorResolver.Anchor anchor = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), wide);

        assertTrue(anchor.spatialScale()
                < anchor.distance() / MenuViewportLayout.referenceDistance(FloatingMenuFieldOfView.DEFAULT,1));
        assertTrue(40.0 * anchor.spatialScale()
                <= anchor.distance()
                * FloatingMenuAnchorResolver.MAX_READING_FRAMING.horizontalTangent() + 0.0001);
        assertTrue(25.0 * anchor.spatialScale()
                <= anchor.distance()
                * FloatingMenuAnchorResolver.MAX_READING_FRAMING.verticalTangent() + 0.0001);
    }

    @Test
    void densePagesRespectVerticalReadingLimitsWithoutShrinkingOrdinaryPages() {
        Location eye = new Location(world(_ -> Double.POSITIVE_INFINITY), 0, 64, 0);
        FloatingMenuAnchorResolver.SceneBounds ordinary =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-1.2, -0.7, 0.0),
                        new FloatingMenuPoint(1.2, 0.7, 0.0)));
        FloatingMenuAnchorResolver.SceneBounds fuller =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-20.0, -12.5, 0.0),
                        new FloatingMenuPoint(20.0, 12.5, 0.0)));

        FloatingMenuAnchorResolver.Anchor smallPage = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), ordinary);
        FloatingMenuAnchorResolver.Anchor fullPage = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), fuller);

        assertEquals(MenuViewportLayout.DEFAULT_DISTANCE / MenuViewportLayout.referenceDistance(FloatingMenuFieldOfView.DEFAULT,1),
                smallPage.spatialScale(), 0.0001);
        assertTrue(fullPage.spatialScale() < smallPage.spatialScale());
        assertTrue(Math.toDegrees(Math.atan2(12.5 * fullPage.spatialScale(), fullPage.distance())) <= 35.0001);
    }

    @Test
    void declaredFramingDoesNotResizeTheSamePage() {
        Location eye = new Location(world(_ -> Double.POSITIVE_INFINITY), 0, 64, 0);
        List<FloatingMenuPoint> footprint = List.of(
                new FloatingMenuPoint(-40.0, -25.0, 0.0),
                new FloatingMenuPoint(40.0, 25.0, 0.0));
        FloatingMenuAnchorResolver.SceneBounds comfortable =
                new FloatingMenuAnchorResolver.SceneBounds(footprint);
        FloatingMenuAnchorResolver.SceneBounds panoramic =
                new FloatingMenuAnchorResolver.SceneBounds(
                        footprint, FloatingMenuFraming.PANORAMIC);

        FloatingMenuAnchorResolver.Anchor normalAnchor = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), comfortable);
        FloatingMenuAnchorResolver.Anchor panoramicAnchor = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), panoramic);

        assertEquals(normalAnchor.spatialScale(), panoramicAnchor.spatialScale(), 0.0001);
    }

    @Test
    void forwardDepthCountsTowardTheMaximumReadingAngle() {
        FloatingMenuAnchorResolver.SceneBounds forward =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(3.0, 0.0, 1.2)));

        double scale = forward.readableScale(MenuViewportLayout.DEFAULT_DISTANCE);

        assertTrue(scale < MenuViewportLayout.DEFAULT_DISTANCE
                * FloatingMenuAnchorResolver.MAX_READING_FRAMING.horizontalTangent() / 3.0);
        double angle = Math.toDegrees(Math.atan2(3.0 * scale,
                MenuViewportLayout.DEFAULT_DISTANCE - 1.2 * scale));
        assertTrue(angle <= FloatingMenuAnchorResolver.MAX_READING_FRAMING
                .horizontalHalfAngleDegrees() + 0.0001);
    }

    @Test void retainedFramesFitDepthAndBothVerticalEdgesWithoutMovingTheirOrigin() {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 64, 0);
        var bounds = new FloatingMenuAnchorResolver.SceneBounds(List.of(
                new FloatingMenuPoint(-4, -3, .8), new FloatingMenuPoint(2, 5, 1.2)));
        for (int degrees : new int[]{30, 70, 110}) for (double vertical : new double[]{-.5, 0, .55}) {
            var fov = new org.encinet.mik.shared.menu.FloatingMenuFieldOfView(degrees);
            var reference = new FloatingMenuAnchorResolver.ReadingFrame(1.1, 2.8, vertical, 16);
            var fitted = reference.fittedTo(bounds, fov.readingFraming()).at(eye, new Vector(0, 0, 1));
            assertEquals(reference.at(eye, new Vector(0, 0, 1)).origin(), fitted.origin());
            assertTrue(fitted.spatialScale() > 0 && fitted.spatialScale() < reference.spatialScale());
            assertEquals(1.1, reference.spatialScale(), "Fitting does not mutate the retained baseline");
            SceneGeometryTestSupport.assertReadingCone(bounds, fitted, eye, fov, degrees + " / " + vertical);
        }
    }

    @Test
    void worldInteractionConeFollowsTheVisibleScene() {
        FloatingMenuAnchorResolver.SceneBounds full =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-20.0, -12.5, 0.0),
                        new FloatingMenuPoint(20.0, 12.5, 0.0)));

        FloatingMenuFraming interaction = full.interactionFraming(
                MenuViewportLayout.DEFAULT_DISTANCE,
                MenuViewportLayout.DEFAULT_DISTANCE / MenuViewportLayout.referenceDistance(FloatingMenuFieldOfView.DEFAULT,1));

        assertTrue(interaction.horizontalHalfAngleDegrees()
                > FloatingMenuFraming.COMFORTABLE.horizontalHalfAngleDegrees());
        for (FloatingMenuPoint point : full.probes()) {
            double toward = MenuViewportLayout.DEFAULT_DISTANCE
                    - point.forward() * MenuViewportLayout.DEFAULT_DISTANCE / MenuViewportLayout.referenceDistance(FloatingMenuFieldOfView.DEFAULT,1);
            assertTrue(interaction.horizontalHalfAngleDegrees() >= Math.toDegrees(Math.atan2(
                    Math.abs(point.right()) * MenuViewportLayout.DEFAULT_DISTANCE / MenuViewportLayout.referenceDistance(FloatingMenuFieldOfView.DEFAULT,1), toward)));
        }
    }

    @Test
    void playerInterfaceScaleChangesAnOrdinarySceneAsOneSharedFactor() {
        Location eye = new Location(world(_ -> Double.POSITIVE_INFINITY), 0, 64, 0);

        FloatingMenuAnchorResolver.Anchor small = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), CENTER_ONLY,
                FloatingMenuScale.SMALL.factor());
        FloatingMenuAnchorResolver.Anchor large = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), CENTER_ONLY,
                FloatingMenuScale.LARGE.factor());

        assertEquals(MenuViewportLayout.DEFAULT_DISTANCE / MenuViewportLayout.referenceDistance(FloatingMenuFieldOfView.DEFAULT,1)
                        * FloatingMenuScale.SMALL.factor(),
                small.spatialScale(), 0.0001);
        assertEquals(MenuViewportLayout.DEFAULT_DISTANCE / MenuViewportLayout.referenceDistance(FloatingMenuFieldOfView.DEFAULT,1)
                        * FloatingMenuScale.LARGE.factor(),
                large.spatialScale(), 0.0001);
    }

    @Test
    void smallPreferenceRemainsVisibleButLargePreferenceCannotExceedTheReadingLimit() {
        Location eye = new Location(world(_ -> Double.POSITIVE_INFINITY), 0, 64, 0);
        FloatingMenuAnchorResolver.SceneBounds wide =
                new FloatingMenuAnchorResolver.SceneBounds(List.of(
                        new FloatingMenuPoint(-25.0, -15.0, 0.0),
                        new FloatingMenuPoint(25.0, 15.0, 0.0)));

        FloatingMenuAnchorResolver.Anchor normal = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), wide,
                FloatingMenuScale.NORMAL.factor());
        FloatingMenuAnchorResolver.Anchor small = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), wide,
                FloatingMenuScale.SMALL.factor());
        FloatingMenuAnchorResolver.Anchor large = MenuAnchorTestSupport.front(
                eye, new Vector(0, 0, 1), wide,
                FloatingMenuScale.LARGE.factor());

        assertTrue(small.spatialScale() <= normal.spatialScale());
        assertTrue(large.spatialScale() <= normal.spatialScale() * FloatingMenuScale.LARGE.factor() + 1e-6);
        assertTrue(small.spatialScale() <= normal.spatialScale());
        assertTrue(Math.toDegrees(Math.atan2(15 * large.spatialScale(), large.distance())) <= 35.001);
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
