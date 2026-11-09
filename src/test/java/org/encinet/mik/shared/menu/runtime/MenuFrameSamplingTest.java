package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.motion.MenuTransition;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.assertRotation;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.assertVector;
import static org.junit.jupiter.api.Assertions.*;

class MenuFrameSamplingTest {
    @Test void aCompanionUsesTheCurrentSourcePoseDuringMovementRotationScaleAndReversal() {
        var actual = new View(false); var referenceSource = new Node(false); var reference = new MenuPopoverAttachment();
        boolean observedMovement = false;
        for (int tick = 0; tick <= 20; tick++) {
            var target = target(tick < 6 || tick >= 13);
            var expectedSource = referenceSource.sample(target, tick, false, tick < 8, tick >= 8 && tick < 12);
            reference.update(actual.eye, "source", expectedSource, View.POPUP, FloatingMenuFieldOfView.DEFAULT,
                    List.of(expectedSource), tick / 4, (_, _) -> 1);
            var prior = actual.child.surface;
            assertTrue(actual.present(tick, target, tick < 8, tick >= 8 && tick < 12));
            assertEquals(expectedSource, actual.source.surface);
            assertVector(reference.placement().center(), actual.child.surface.center());
            assertRotation(reference.placement().rotation(), actual.child.surface.rotation());
            assertEquals(reference.readingScale(), actual.child.surface.scale(), 1e-8);
            assertEquals(actual.source.surface, actual.published.get(1));
            assertEquals(actual.child.surface, actual.published.getFirst());
            assertEquals(tick + 1, actual.source.samples, "Source sampling must run once per frame");
            assertEquals(tick + 1, actual.child.samples, "Companion sampling must run once per frame");
            if (prior != null && MenuSurfaceAttachment.vector(prior.center()).distanceSquared(MenuSurfaceAttachment.vector(actual.child.surface.center())) > 1e-7)
                observedMovement = true;
            assertHitsCurrentFrame(actual.eye, actual.source.surface);
            assertHitsCurrentFrame(actual.eye, actual.child.surface);
        }
        assertTrue(observedMovement, "The regression must exercise moving attachment geometry");
    }

    @Test void rejectedAttachmentPublishesNeitherHalfOfTheFrameAndAFreshSnapshotRecovers() {
        var view = new View(false);
        assertTrue(view.present(0, target(true), false, false));
        var oldSurface = view.child.surface;
        view.factor = 0;
        assertFalse(view.present(4, target(false), true, false));
        assertTrue(view.published.isEmpty());
        assertSame(oldSurface, view.child.surface, "A rejected frame must not sample its companion against absent geometry");
        assertEquals(2, view.source.samples); assertEquals(1, view.child.samples);
        assertTrue(view.attachment.blocked()); assertNull(view.attachment.placement());
        view.factor = 1;
        assertTrue(view.present(8, target(false), true, false));
        assertFalse(view.attachment.blocked());
        assertEquals(2, view.published.size());
        assertEquals(2, view.child.samples);
        assertHitsCurrentFrame(view.eye, view.child.surface);
    }

    @Test void disabledMotionSamplesTheLatestTargetAndKeepsTheSameInputAndAttachmentContracts() {
        var view = new View(true);
        for (int tick = 0; tick < 8; tick++) {
            var target = target(tick % 2 == 0);
            assertTrue(view.present(tick, target, true, true));
            assertEquals(target, view.source.pose);
            var at = new UiVector3(-target.right(), target.up(), 3 - target.forward());
            assertVector(at, view.source.surface.center());
            assertEquals(target.scale(), view.source.surface.scale(), 1e-8);
            assertHitsCurrentFrame(view.eye, view.source.surface);
            assertHitsCurrentFrame(view.eye, view.child.surface);
        }
    }

    @Test void aBodylessFrameCanPublishWithoutCreatingAnAttachmentOrAdvancingAHiddenNode() {
        var calls = new AtomicInteger();
        assertTrue(MenuFrameSampling.publish(List.<Node>of(), node -> node.companion,
                _ -> fail("No node should be sampled"), () -> true, _ -> fail("No node should be published")));
        var source = new Node(false);
        assertTrue(MenuFrameSampling.publish(List.of(source), node -> node.companion,
                node -> node.sample(target(true), 0, false, false, false), () -> true,
                node -> { assertNotNull(node.surface); calls.incrementAndGet(); }));
        assertEquals(1, calls.get());
    }

    @Test void aSampleFailureCannotPublishAPartiallyPreparedFrame() {
        var view = new View(false);
        var published = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> MenuFrameSampling.publish(view.nodes, node -> node.companion,
                _ -> { throw new IllegalStateException("source unavailable"); },
                () -> { fail("Attachment cannot consume failed source geometry"); return true; }, _ -> published.incrementAndGet()));
        assertEquals(0, published.get());
    }

    private static void assertHitsCurrentFrame(Location eye, MenuSurfaceAttachment.Surface surface) {
        Location pointing = eye.clone().setDirection(MenuSurfaceAttachment.vector(surface.center()).subtract(eye.toVector()));
        var hit = surface.intersect(pointing, 0).orElseThrow();
        // Bukkit stores the pointing direction as float yaw/pitch, so the round trip has native angular precision.
        assertEquals(eye.toVector().distance(MenuSurfaceAttachment.vector(surface.center())), hit.distance(), 1e-5);
        assertEquals(.5, hit.horizontalFraction(), 1e-5);
    }

    private static FloatingMenuPose target(boolean expanded) {
        return expanded ? new FloatingMenuPose(new FloatingMenuPoint(.45, .18, .05), 18, 8, 12, .9)
                : new FloatingMenuPose(new FloatingMenuPoint(-.4, -.12, -.08), -16, -7, -9, 1.15);
    }

    private static final class Node {
        final boolean companion;
        final MenuPoseTransition transition = new MenuPoseTransition();
        final MenuTransition hover = new MenuTransition(0), press = new MenuTransition(0);
        FloatingMenuPose pose = FloatingMenuPose.ORIGIN;
        MenuSurfaceAttachment.Surface surface;
        int samples;

        Node(boolean companion) { this.companion = companion; transition.snap(pose, 0); }

        MenuSurfaceAttachment.Surface sample(FloatingMenuPose target, long tick, boolean reduced, boolean hovered, boolean pressed) {
            samples++;
            pose = transition.advance(target, tick, reduced ? 0 : 6, FloatingMenuEasing.CUBIC_OUT, MenuPoseTransition.Path.LINEAR);
            double h = hover.step(hovered ? 1 : 0, tick, reduced ? 0 : 3, FloatingMenuEasing.CUBIC_OUT);
            double p = press.step(pressed ? 1 : 0, tick, reduced ? 0 : 3, FloatingMenuEasing.CUBIC_OUT);
            var center = new Vector(-pose.right(), pose.up(), 3 - pose.forward());
            surface = MenuNodeSurface.sample(pose, center, org.encinet.mik.shared.menu.ui.UiRotation.degrees(180,0,0), 1, new FloatingMenuSize(.8, .5), FloatingMenuAnimation.DEFAULT,
                    new MenuNodeSurface.Feedback(h, p, 1, reduced));
            return surface;
        }
    }

    private static final class View {
        static final FloatingMenuSize POPUP = new FloatingMenuSize(.7, .4);
        final Location eye = new Location(null, 0, 0, 0);
        final Node source = new Node(false), child = new Node(true);
        // Declaration order deliberately puts the child before its source.
        final List<Node> nodes = List.of(child, source);
        final List<MenuSurfaceAttachment.Surface> published = new ArrayList<>();
        final MenuPopoverAttachment attachment = new MenuPopoverAttachment();
        final boolean reduced;
        double factor = 1;

        View(boolean reduced) { this.reduced = reduced; }

        boolean present(long tick, FloatingMenuPose target, boolean hover, boolean press) {
            published.clear();
            return MenuFrameSampling.publish(nodes, node -> node.companion, node -> {
                if (!node.companion) node.sample(target, tick, reduced, hover, press);
                else {
                    node.samples++;
                    var placement = attachment.placement();
                    node.surface = new MenuSurfaceAttachment.Surface(placement.center(), placement.rotation(), POPUP, attachment.readingScale());
                }
            }, () -> {
                attachment.update(eye, "source", source.surface, POPUP, FloatingMenuFieldOfView.DEFAULT,
                        List.of(source.surface), tick / 4, (_, _) -> factor);
                return !attachment.blocked();
            }, node -> published.add(node.surface));
        }
    }
}
