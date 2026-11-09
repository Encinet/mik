package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuTooltipOcclusionTest {
    private static final Location EYE = new Location(null, 0, 0, 0, 0, 0);
    private static final FloatingMenuSize SIZE = new FloatingMenuSize(.6, .3);

    @Test void aNearerSidePanelIsAvoidedBeforeChangingTheHintsDepth() {
        var source = source(1);
        var pointer = MenuSurfaceAttachment.pointer(EYE, source, FloatingMenuFieldOfView.DEFAULT).orElseThrow();
        var neighbour = new MenuSurfaceAttachment.Surface(new UiVector3(-.7, -.5, 2.4), UiRotation.degrees(180, 0, 0),
                new FloatingMenuSize(1, 1), 1);
        var original = MenuSurfaceAttachment.attach(pointer, source, SIZE).orElseThrow();
        assertTrue(blocked(EYE, original.place(source), SIZE, neighbour), "the old first quadrant is behind the neighbouring menu");
        var improved = MenuSurfaceAttachment.attach(pointer, source, SIZE, List.of(neighbour)).orElseThrow();
        assertEquals(1, improved.readingScale(), 1e-6);
        assertFalse(blocked(EYE, improved.place(source), SIZE, neighbour));
        assertPointerClear(improved, source, SIZE);
    }

    @Test void overlappingMenusUseAFrontReadingPlaneWithoutChangingApparentTextSize() {
        var source = source(1);
        var pointer = MenuSurfaceAttachment.pointer(EYE, source, FloatingMenuFieldOfView.DEFAULT).orElseThrow();
        var cover = cover(2);
        var original = MenuSurfaceAttachment.attach(pointer, source, SIZE).orElseThrow();
        var improved = MenuSurfaceAttachment.attach(pointer, source, SIZE, List.of(cover)).orElseThrow();
        var before = original.place(source); var after = improved.place(source);
        assertTrue(improved.readingScale() < 1);
        assertTrue(after.center().forward() < 2);
        assertEquals(before.center().right() / before.center().forward(), after.center().right() / after.center().forward(), 1e-6);
        assertEquals(before.center().up() / before.center().forward(), after.center().up() / after.center().forward(), 1e-6);
        assertEquals(SIZE.width() / before.center().forward(), SIZE.width() * improved.readingScale() / after.center().forward(), 1e-6);
        assertRotation(before.rotation(), after.rotation());
        assertFalse(blocked(EYE, after, scaled(SIZE, improved.readingScale()), cover));
        assertPointerClear(improved, source, SIZE);
    }

    @Test void tiltedPanelsAndModelVolumesCannotCoverAnyPartOfTheHint() {
        for (double scale : new double[]{.2, .6, 1}) for (double yaw : new double[]{-38, 0, 38}) {
            for (double pitch : new double[]{-28, 0, 28}) for (double roll : new double[]{-16, 0, 16}) {
                var source = source(scale);
                var size = scaled(SIZE, scale);
                var pointer = MenuSurfaceAttachment.pointer(EYE, source, FloatingMenuFieldOfView.DEFAULT).orElseThrow();
                var panel = new MenuSurfaceAttachment.Surface(new UiVector3(-.1, -.1, 2.6), UiRotation.degrees(180 + yaw, pitch, roll),
                        new FloatingMenuSize(3, 2), scale, .5);
                var anchor = MenuSurfaceAttachment.attach(pointer, source, size, List.of(panel)).orElseThrow();
                assertFalse(blocked(EYE, anchor.place(source), scaled(size, anchor.readingScale()), panel),
                        "scale=" + scale + " yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
                assertPointerClear(anchor, source, size);
            }
        }
    }

    @Test void newObstructionsAdvanceTheSameAnchorAndClearedObstructionsDoNotMakeItJumpBack() {
        var source = source(1);
        var pointer = MenuSurfaceAttachment.pointer(EYE, source, FloatingMenuFieldOfView.DEFAULT).orElseThrow();
        var anchor = MenuSurfaceAttachment.attach(pointer, source, SIZE).orElseThrow();
        var moved = anchor.inFront(UiVector3.ZERO, source, SIZE, List.of(cover(2)));
        assertNotEquals(anchor, moved);
        assertSame(moved, moved.inFront(UiVector3.ZERO, source, SIZE, List.of(cover(2))));
        assertSame(moved, moved.inFront(UiVector3.ZERO, source, SIZE, List.of()));
        var eye = EYE.clone(); eye.setYaw(20); eye.setPitch(-10);
        assertSame(moved, moved.inFront(MenuSurfaceAttachment.point(eye.toVector()), source, SIZE, List.of(cover(2))));
    }

    @Test void frontPlacementPreservesTextFramingAcrossFieldOfViewAndTypographyPreferences() {
        for (int fov : new int[]{30, 70, 110}) for (double scale : new double[]{.2, .6, 1}) {
            for (double typography : new double[]{.7, 1, 1.8}) {
                var source = source(scale);
                var pointer = MenuSurfaceAttachment.pointer(EYE, source, new FloatingMenuFieldOfView(fov)).orElseThrow();
                double textScale = scale * typography * .85;
                var text = MenuTooltipText.fit(UiTooltip.of(Component.text("Useful information 有用信息\n".repeat(80))), pointer.available(), textScale);
                assertNotNull(text);
                var bounds = new FloatingMenuSize(text.layout().size().width() * textScale, text.layout().size().height() * textScale);
                var anchor = MenuSurfaceAttachment.attach(pointer, source, bounds, List.of(cover(2))).orElseThrow();
                var placed = anchor.place(source);
                var projected = pointer.eye().add(placed.center().subtract(pointer.eye()).multiply(1 / anchor.readingScale()));
                var local = pointer.rotation().inverse().apply(projected.subtract(pointer.center()));
                assertTrue(Math.abs(local.right()) + bounds.width() / 2 <= pointer.viewport().width() / 2 + 1e-7);
                assertTrue(Math.abs(local.up()) + bounds.height() / 2 <= pointer.viewport().height() / 2 + 1e-7);
                assertFalse(blocked(EYE, placed, scaled(bounds, anchor.readingScale()), cover(2)));
                assertPointerClear(anchor, source, bounds);
            }
        }
    }

    @Test void offscreenAndBehindTheEyeGeometryCannotPullTheHintForward() {
        var source = source(1);
        var pointer = MenuSurfaceAttachment.pointer(EYE, source, FloatingMenuFieldOfView.DEFAULT).orElseThrow();
        var anchor = MenuSurfaceAttachment.attach(pointer, source, SIZE).orElseThrow();
        var offscreen = new MenuSurfaceAttachment.Surface(new UiVector3(20, 0, .5), UiRotation.degrees(180, 0, 0), new FloatingMenuSize(2, 2), 1);
        var unchanged = MenuSurfaceAttachment.attach(pointer, source, SIZE, List.of(offscreen, cover(-2), cover(5))).orElseThrow();
        assertEquals(anchor, unchanged);
    }

    @Test void theWholeSceneCanRotateTranslateAndScaleWithoutChangingTheAvoidanceDecision() {
        var source = source(1);
        var pointer = MenuSurfaceAttachment.pointer(EYE, source, FloatingMenuFieldOfView.DEFAULT).orElseThrow();
        var obstacle = new MenuSurfaceAttachment.Surface(new UiVector3(-.3, -.2, 2.3), UiRotation.degrees(202, -15, 12),
                new FloatingMenuSize(3, 2), 1, .4);
        var original = MenuSurfaceAttachment.attach(pointer, source, SIZE, List.of(obstacle)).orElseThrow();
        var rotation = UiRotation.degrees(35, -18, 12);
        var translation = new UiVector3(10, 64, -7);
        for (double scale : new double[]{.3, 1, 2}) {
            var movedSource = transformed(source, rotation, translation, scale);
            var movedPointer = new MenuSurfaceAttachment.Pointer(translation.add(rotation.apply(pointer.center().multiply(scale))),
                    rotation.compose(pointer.rotation()), new UiSize(pointer.viewport().width() * scale, pointer.viewport().height() * scale),
                    pointer.gap() * scale, translation);
            var moved = MenuSurfaceAttachment.attach(movedPointer, movedSource, scaled(SIZE, scale),
                    List.of(transformed(obstacle, rotation, translation, scale))).orElseThrow();
            assertVector(translation.add(rotation.apply(original.place(source).center().multiply(scale))), moved.place(movedSource).center());
            assertRotation(rotation.compose(original.place(source).rotation()), moved.place(movedSource).rotation());
            assertEquals(original.readingScale(), moved.readingScale(), 1e-6);
        }
    }

    @Test void geometryCrossingTheEyePlaneIsClippedBeforePerspectiveDivision() {
        var source = source(1);
        var pointer = MenuSurfaceAttachment.pointer(EYE, source, FloatingMenuFieldOfView.DEFAULT).orElseThrow();
        var crossing = new MenuSurfaceAttachment.Surface(new UiVector3(0, 0, .1), UiRotation.degrees(225, 15, 10),
                new FloatingMenuSize(3, 2), 1, .5);
        var anchor = assertDoesNotThrow(() -> MenuSurfaceAttachment.attach(pointer, source, SIZE, List.of(crossing)).orElseThrow());
        assertTrue(anchor.readingScale() > 0 && Double.isFinite(anchor.readingScale()));
        assertTrue(anchor.place(source).center().forward() > 0);
    }

    private static MenuSurfaceAttachment.Surface source(double scale) {
        return new MenuSurfaceAttachment.Surface(new UiVector3(0, 0, 3), UiRotation.degrees(180, 0, 0), new FloatingMenuSize(4, 2), scale);
    }

    private static MenuSurfaceAttachment.Surface cover(double depth) {
        return new MenuSurfaceAttachment.Surface(new UiVector3(0, 0, depth), UiRotation.degrees(180, 0, 0), new FloatingMenuSize(20, 20), 1);
    }

    private static FloatingMenuSize scaled(FloatingMenuSize size, double scale) { return new FloatingMenuSize(size.width() * scale, size.height() * scale); }

    private static MenuSurfaceAttachment.Surface transformed(MenuSurfaceAttachment.Surface surface, UiRotation rotation, UiVector3 translation, double scale) {
        return new MenuSurfaceAttachment.Surface(translation.add(rotation.apply(surface.center().multiply(scale))), rotation.compose(surface.rotation()),
                surface.size(), surface.scale() * scale, surface.depth());
    }

    private static void assertPointerClear(MenuSurfaceAttachment anchor, MenuSurfaceAttachment.Surface source, FloatingMenuSize size) {
        var view = anchor.place(source); var angles = view.rotation().angles();
        assertTrue(FloatingMenuSurfaceGeometry.intersect(EYE.toVector(), EYE.getDirection(), MenuSurfaceAttachment.vector(view.center()),
                (float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(), size.width() * anchor.readingScale(),
                size.height() * anchor.readingScale(), .01 * source.scale() * anchor.readingScale()).isEmpty());
    }

    /** Sample rays across the actual visible popup, independently of the placement algorithm's projected bounds. */
    private static boolean blocked(Location eye, MenuSurfaceAttachment.Placement view, FloatingMenuSize size, MenuSurfaceAttachment.Surface obstacle) {
        var faces = faces(obstacle);
        for (double x : new double[]{-.5, -.25, 0, .25, .5}) for (double y : new double[]{-.5, 0, .5}) {
            var point = view.center().add(view.rotation().apply(new UiVector3(size.width() * x, size.height() * y, 0)));
            var ray = MenuSurfaceAttachment.vector(point).subtract(eye.toVector());
            double distance = ray.length(); ray.normalize();
            for (var face : faces) {
                var angles = face.rotation().angles();
                var hit = FloatingMenuSurfaceGeometry.intersect(eye.toVector(), ray, MenuSurfaceAttachment.vector(face.center()),
                        (float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(), face.size().width() * face.scale(),
                        face.size().height() * face.scale(), 0);
                if (hit.isPresent() && hit.get().distance() < distance - 1e-7) return true;
            }
        }
        return false;
    }

    private static List<MenuSurfaceAttachment.Surface> faces(MenuSurfaceAttachment.Surface box) {
        if (box.depth() == 0) return List.of(box);
        return List.of(face(box, new UiVector3(0, 0, box.depth() / 2), UiRotation.IDENTITY, box.size()),
                face(box, new UiVector3(0, 0, -box.depth() / 2), UiRotation.IDENTITY, box.size()),
                face(box, new UiVector3(box.size().width() / 2, 0, 0), UiRotation.degrees(90, 0, 0), new FloatingMenuSize(box.depth(), box.size().height())),
                face(box, new UiVector3(-box.size().width() / 2, 0, 0), UiRotation.degrees(90, 0, 0), new FloatingMenuSize(box.depth(), box.size().height())),
                face(box, new UiVector3(0, box.size().height() / 2, 0), UiRotation.degrees(0, 90, 0), new FloatingMenuSize(box.size().width(), box.depth())),
                face(box, new UiVector3(0, -box.size().height() / 2, 0), UiRotation.degrees(0, 90, 0), new FloatingMenuSize(box.size().width(), box.depth())));
    }

    private static MenuSurfaceAttachment.Surface face(MenuSurfaceAttachment.Surface box, UiVector3 offset, UiRotation rotation, FloatingMenuSize size) {
        return new MenuSurfaceAttachment.Surface(box.center().add(box.rotation().apply(offset.multiply(box.scale()))),
                box.rotation().compose(rotation), size, box.scale());
    }
}
