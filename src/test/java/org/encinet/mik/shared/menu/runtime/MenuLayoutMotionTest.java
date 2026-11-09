package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuLayoutMotionTest {
    @Test void safeEndpointsDoNotPermitMotionAcrossAnInterveningWall() {
        var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64, 0);
        var anchor = anchor(eye); var move = linear(-1.2, 1.2);
        fixture.block(0, 64, 2, new BoundingBox(.01, .01, .01, .08, .08, .08));
        var space = new FloatingMenuWorldSpace(eye, List.of());
        assertEndpointClear(eye, anchor, move.from(), move, space);
        assertEndpointClear(eye, anchor, move.to(), move, space);
        var layout = scene(); var before = layout.bounds();
        assertFalse(new MenuLayoutMotion().permits(layout, eye, anchor, 1, () -> List.of(move), _ -> space));
        assertSame(before, layout.bounds(), "Motion fallback must not change reading bounds or repaginate");
    }

    @Test void aLateReversalChecksTheSampledOldPathBeforeFollowingTheNewTarget() {
        var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64, 0);
        var anchor = anchor(eye); var move = linear(-1.2, 1.2);
        fixture.block(0, 64, 2, new BoundingBox(.01, .01, .01, .08, .08, .08));
        var transition = new MenuPoseTransition(); transition.snap(move.from(), 0);
        transition.advance(move.to(), 0, 6, FloatingMenuEasing.LINEAR, MenuPoseTransition.Path.LINEAR);
        var rendered = transition.sample(1); var sampled = transition.sample(3);
        var direct = new MenuLayoutMotion.Move(rendered, move.from(), move.size(), move.depth(), MenuPoseTransition.Path.LINEAR);
        var gate = new MenuLayoutMotion(); var layout = scene();
        assertTrue(gate.permits(layout, eye, anchor, 1, () -> List.of(direct), _ -> new FloatingMenuWorldSpace(eye, List.of())));
        var previous = new MenuLayoutMotion.Move(rendered, sampled, move.size(), move.depth(), transition.path());
        var next = new MenuLayoutMotion.Move(sampled, move.from(), move.size(), move.depth(), MenuPoseTransition.Path.LINEAR);
        assertFalse(gate.permits(scene(), eye, anchor, 1, () -> List.of(previous, next), _ -> new FloatingMenuWorldSpace(eye, List.of())));
    }

    @Test void unchangedFramesReuseValidationWhileNewWorldSnapshotsAndEyesRecheckRemainingMotion() {
        var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64, 0);
        var anchor = anchor(eye); var move = linear(-1.2, 1.2); var layout = scene();
        var gate = new MenuLayoutMotion(); var samples = new AtomicInteger(); var captures = new AtomicInteger();
        java.util.function.Supplier<List<MenuLayoutMotion.Move>> source = () -> { samples.incrementAndGet(); return List.of(move); };
        java.util.function.DoubleFunction<FloatingMenuWorldSpace> world = range -> {
            captures.incrementAndGet(); assertTrue(range > 2 && range < 4);
            return new FloatingMenuWorldSpace(eye, List.of());
        };
        assertTrue(gate.permits(layout, eye, anchor, 1, source, world));
        assertTrue(gate.permits(layout, eye.clone(), anchor, 1, source, world));
        assertEquals(1, samples.get()); assertEquals(1, captures.get());
        fixture.block(0, 64, 2, new BoundingBox(.01, .01, .01, .08, .08, .08));
        assertFalse(gate.permits(layout, eye, anchor, 2, source, world));
        assertEquals(2, samples.get()); assertEquals(2, captures.get());
        fixture.block(0, 64, 2);
        assertTrue(gate.permits(layout, eye.clone().add(.01, 0, 0), anchor, 2, source, world));
        assertEquals(3, captures.get());
        gate.clear();
        assertTrue(gate.permits(layout, eye, anchor, 2, source, world));
        assertEquals(4, captures.get());
    }

    @Test void rotationsScaleAndCurvedPathsStayInsideTheirContinuousEnvelopes() {
        var size = new FloatingMenuSize(.8, .35);
        var random = new java.util.Random(892134);
        for (var path : MenuPoseTransition.Path.values()) for (int example = 0; example < 30; example++) {
            double yaw = random.nextDouble(-360, 360), nextYaw = yaw + random.nextDouble(-340, 340);
            var from = new FloatingMenuPose(FloatingMenuRing.pose(yaw / 360, 1, 2, .1).point(), yaw,
                    random.nextDouble(-70, 70), random.nextDouble(-180, 180), random.nextDouble(.4, 1.8));
            var to = new FloatingMenuPose(FloatingMenuRing.pose(nextYaw / 360, 1, 2.1, .2).point(), nextYaw,
                    random.nextDouble(-70, 70), random.nextDouble(-180, 180), random.nextDouble(.4, 1.8));
            var move = new MenuLayoutMotion.Move(from, to, size, .6, path);
            var bounds = MenuLayoutMotion.envelope(List.of(move)).orElseThrow();
            assertTrue(bounds.surfaces().size() <= 24);
            for (int step = 0; step <= 100; step++) {
                double fraction = (double) step / 100;
                var pose = step == 0 ? from : step == 100 ? to
                        : path == MenuPoseTransition.Path.ORBIT ? FloatingMenuMotion.approachOrbit(from, to, fraction)
                        : path == MenuPoseTransition.Path.RING ? FloatingMenuPose.transformed(FloatingMenuMotion.approachRing(from.point(), to.point(), fraction),
                        FloatingMenuMotion.approachPose(from, to, fraction).rotation(), from.scale() * (1 - fraction) + to.scale() * fraction)
                        : FloatingMenuMotion.approachPose(from, to, fraction);
                for (int corner = 0; corner < 8; corner++) {
                    var offset = pose.rotation().apply(new UiVector3((corner & 1) == 0 ? -.4 : .4,
                            (corner & 2) == 0 ? -.175 : .175, (corner & 4) == 0 ? -.3 : .3).multiply(pose.scale()));
                    var point = new UiVector3(pose.right(), pose.up(), pose.forward()).add(offset);
                    assertTrue(bounds.surfaces().stream().anyMatch(surface -> contains(surface, point)), path + " " + example + " " + step);
                }
            }
        }
    }

    @Test void anEntityOrAnUnloadedChunkCanRejectMotionWithoutChangingTheEndpoint() {
        var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64, 0);
        var anchor = anchor(eye); var move = linear(-1.2, 1.2);
        var obstacle = MenuOcclusionGeometry.box(new BoundingBox(-.08, 63.92, 2.01, .08, 64.08, 2.08), eye.toVector());
        var space = new FloatingMenuWorldSpace(eye, List.of(obstacle));
        assertEndpointClear(eye, anchor, move.to(), move, space);
        assertFalse(new MenuLayoutMotion().permits(scene(), eye, anchor, 1, () -> List.of(move), _ -> space));
        fixture.loaded((x, z) -> x == 0 && z == 0);
        var edgeEye = new Location(fixture.world(), 15.9, 64, 1);
        assertFalse(new MenuLayoutMotion().permits(scene(), edgeEye, anchor(edgeEye), 1, () -> List.of(move),
                _ -> new FloatingMenuWorldSpace(edgeEye, List.of())));
    }

    @Test void ringAndOrbitMotionCheckTheArcBetweenSafeReadingSurfaces() {
        for (var path : new MenuPoseTransition.Path[]{MenuPoseTransition.Path.RING, MenuPoseTransition.Path.ORBIT}) {
            var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64, 0);
            double angle = path == MenuPoseTransition.Path.RING ? 5 : 45;
            var move = new MenuLayoutMotion.Move(FloatingMenuRing.pose(-angle / 360, 1, 3, 0),
                    FloatingMenuRing.pose(angle / 360, 1, 3, 0), new FloatingMenuSize(.2, .2), .04, path);
            var anchor = new FloatingMenuAnchorResolver.Anchor(eye, new Vector(0, 0, 1), new Vector(-1, 0, 0), 1, 0, 0);
            fixture.block(0, 64, 3, new BoundingBox(.01, .01, .01, .08, .08, .08));
            var space = new FloatingMenuWorldSpace(eye, List.of());
            assertEndpointClear(eye, anchor, move.from(), move, space);
            assertEndpointClear(eye, anchor, move.to(), move, space);
            assertFalse(new MenuLayoutMotion().permits(scene(), eye, anchor, 1, () -> List.of(move), _ -> space), path.toString());
        }
    }

    @Test void aWideMotionEnvelopeSharesTheBoundedTerrainSamplingPolicy() {
        var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64, 0);
        var anchor = new FloatingMenuAnchorResolver.Anchor(eye.clone().add(0, 0, 30), new Vector(0, 0, 1), new Vector(-1, 0, 0), 1, 30, 0);
        var move = new MenuLayoutMotion.Move(FloatingMenuPose.at(new FloatingMenuPoint(-20, -20, 0)),
                FloatingMenuPose.at(new FloatingMenuPoint(20, 20, 0)), new FloatingMenuSize(2, 2), .2, MenuPoseTransition.Path.LINEAR);
        assertFalse(new MenuLayoutMotion().permits(scene(), eye, anchor, 1, () -> List.of(move), _ -> new FloatingMenuWorldSpace(eye, List.of())));
        assertTrue(fixture.blockReads() <= 32768);
    }

    @Test void staticOversizedAndExcessiveMotionNeverSpendUnboundedWorldWork() {
        var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64, 0);
        var gate = new MenuLayoutMotion();
        assertTrue(gate.permits(scene(), eye, anchor(eye), 1, List::of, _ -> { fail("Static content must not capture the world"); return null; }));
        var oversized = new MenuLayoutMotion.Move(FloatingMenuPose.ORIGIN,
                FloatingMenuPose.at(new FloatingMenuPoint(128, 0, 0)), new FloatingMenuSize(.2, .2), .1, MenuPoseTransition.Path.LINEAR);
        assertFalse(gate.permits(scene(), eye, anchor(eye), 1, () -> List.of(oversized), _ -> { fail("Trace bound must be checked first"); return null; }));
        var moves = java.util.stream.IntStream.rangeClosed(0, MenuLayoutMotion.MAX_SURFACES).mapToObj(_ -> linear(-.5, .5)).toList();
        assertFalse(gate.permits(scene(), eye, anchor(eye), 1, () -> moves, _ -> { fail("Envelope budget must be checked first"); return null; }));
        var overflow = new MenuLayoutMotion.Move(new FloatingMenuPose(FloatingMenuPoint.ORIGIN, 0, 0, 0, Double.MAX_VALUE),
                FloatingMenuPose.at(new FloatingMenuPoint(1, 0, 0)), new FloatingMenuSize(10, 10), .1, MenuPoseTransition.Path.LINEAR);
        assertFalse(gate.permits(scene(), eye, anchor(eye), 1, () -> List.of(overflow), _ -> { fail("Nonfinite envelopes must be rejected before capture"); return null; }));
        var extremeCenter = new MenuLayoutMotion.Move(FloatingMenuPose.at(new FloatingMenuPoint(Double.MAX_VALUE, 0, 0)),
                FloatingMenuPose.at(new FloatingMenuPoint(Double.MAX_VALUE / 1.1, 0, 0)), new FloatingMenuSize(1e308, .2), .1, MenuPoseTransition.Path.LINEAR);
        assertFalse(gate.permits(scene(), eye, anchor(eye), 1, () -> List.of(extremeCenter), _ -> { fail("Overflowing corners must be rejected before capture"); return null; }));
        assertEquals(0, fixture.blockReads()); assertEquals(0, fixture.rayReads());
    }

    private static boolean contains(FloatingMenuAnchorResolver.Surface surface, UiVector3 point) {
        return Math.abs(point.right() - surface.pose().right()) <= surface.size().width() / 2 + 1e-8
                && Math.abs(point.up() - surface.pose().up()) <= surface.size().height() / 2 + 1e-8
                && Math.abs(point.forward() - surface.pose().forward()) <= surface.depth() / 2 + 1e-8;
    }

    private static MenuLayoutMotion.Move linear(double from, double to) {
        return new MenuLayoutMotion.Move(FloatingMenuPose.at(new FloatingMenuPoint(from, 0, 0)), FloatingMenuPose.at(new FloatingMenuPoint(to, 0, 0)),
                new FloatingMenuSize(.2, .2), .04, MenuPoseTransition.Path.LINEAR);
    }

    private static FloatingMenuAnchorResolver.Anchor anchor(Location eye) {
        return new FloatingMenuAnchorResolver.Anchor(eye.clone().add(0, 0, 2), new Vector(0, 0, 1), new Vector(-1, 0, 0), 1, 2, 0);
    }

    private static void assertEndpointClear(Location eye, FloatingMenuAnchorResolver.Anchor anchor, FloatingMenuPose pose,
                                            MenuLayoutMotion.Move move, FloatingMenuWorldSpace space) {
        var bounds = new FloatingMenuAnchorResolver.SceneBounds(List.of(pose.point()), FloatingMenuFraming.COMFORTABLE,
                List.of(new FloatingMenuAnchorResolver.Surface(pose, move.size(), move.depth())));
        assertEquals(1, FloatingMenuAnchorResolver.fitFactor(eye, anchor.origin(), anchor.forward(), anchor.right(), 1, bounds, space), 1e-9);
    }

    private static SceneGeometry scene() {
        return SceneGeometry.measure(MenuScene.screen("motion").content(Ui.text(Component.text("Content")).key("content").build()).build(),
                1, FloatingMenuFieldOfView.DEFAULT, 1);
    }
}
