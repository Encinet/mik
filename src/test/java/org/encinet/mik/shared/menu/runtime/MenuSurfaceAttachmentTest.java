package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuSurfaceAttachmentTest {
    @Test void aPopupUsesTheActualHitPointFacesTheViewerAndLeavesThePointerClear() {
        var eye = new Location(null, 10, 20, 30, 35, -20);
        var direction = eye.getDirection();
        UiVector3 hit = MenuSurfaceAttachment.point(eye.toVector().add(direction.clone().multiply(3.5)));
        for (double yaw : new double[] {-28, 0, 28}) for (double pitch : new double[] {-22, 0, 22}) {
            var rotation = UiRotation.degrees(eye.getYaw() + 180 + yaw, -eye.getPitch() + pitch, 0);
            for (double scale : new double[] {.2, .6, 1}) for (double typography : new double[] {.7, 1, 1.8}) {
                var source = new MenuSurfaceAttachment.Surface(hit.add(rotation.apply(new UiVector3(.4, .1, 0).multiply(scale))),
                        rotation, new FloatingMenuSize(4, 2), scale);
                for (int degrees : new int[] {30, 70, 110}) {
                    var pointer = MenuSurfaceAttachment.pointer(eye, source, new FloatingMenuFieldOfView(degrees)).orElseThrow();
                    double textScale = scale * typography * .85;
                    var text = MenuTooltipText.fit(UiTooltip.of(Component.text("Useful information 有用信息\n".repeat(80))),
                            pointer.available(), textScale);
                    assertNotNull(text, "ordinary preferences always have a readable popup");
                    var bounds = new FloatingMenuSize(text.layout().size().width() * textScale, text.layout().size().height() * textScale);
                    var anchor = MenuSurfaceAttachment.attach(pointer, source, bounds).orElseThrow();
                    var placed = anchor.place(source);
                    var visible = new FloatingMenuSize(bounds.width() * anchor.readingScale(), bounds.height() * anchor.readingScale());
                    assertRotation(pointer.rotation(), placed.rotation());
                    var angles = placed.rotation().angles();
                    assertTrue(FloatingMenuSurfaceGeometry.intersect(eye.toVector(), direction,
                            MenuSurfaceAttachment.vector(placed.center()), (float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(),
                            visible.width(), visible.height(), 0).isEmpty(), "the source remains clickable at the pointer");
                    var reading = MenuSurfaceAttachment.vector(placed.center()).subtract(eye.toVector());
                    assertTrue(FloatingMenuSurfaceGeometry.intersect(eye.toVector(), reading,
                            MenuSurfaceAttachment.vector(placed.center()), (float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(),
                            visible.width(), visible.height(), 0).isPresent(), "the popup owns an actual readable hit plane");
                    UiVector3 projected = pointer.eye().add(placed.center().subtract(pointer.eye()).multiply(1 / anchor.readingScale()));
                    UiVector3 local = pointer.rotation().inverse().apply(projected.subtract(pointer.center()));
                    assertTrue(Math.abs(local.right()) + bounds.width() / 2 <= pointer.viewport().width() / 2 + 1e-8);
                    assertTrue(Math.abs(local.up()) + bounds.height() / 2 <= pointer.viewport().height() / 2 + 1e-8);
                    assertTrue(MenuSurfaceAttachment.vector(pointer.center()).distance(eye.toVector()) < 3.5, "the overlay is in front of its source");
                }
            }
        }
    }

    @Test void readingDoesNotChaseHeadMovementAndParentTransformsStillApply() {
        var eye = new Location(null, 0, 0, 0, 0, 0);
        var source = new MenuSurfaceAttachment.Surface(new UiVector3(0, 0, 3), UiRotation.degrees(180, 0, 0), new FloatingMenuSize(2, 1), .5);
        var pointer = MenuSurfaceAttachment.pointer(eye, source, FloatingMenuFieldOfView.DEFAULT).orElseThrow();
        var anchor = MenuSurfaceAttachment.attach(pointer, source, new FloatingMenuSize(.8, .4)).orElseThrow();
        var before = anchor.place(source);
        eye.setYaw(25);
        eye.setPitch(-10);
        assertEquals(before, anchor.place(source), "looking toward the popup cannot move it away");
        var parent = UiRotation.degrees(30, 20, 0);
        var translation = new UiVector3(4, 5, 6);
        var moved = new MenuSurfaceAttachment.Surface(translation.add(parent.apply(source.center())),
                parent.compose(source.rotation()), source.size(), source.scale());
        assertVector(translation.add(parent.apply(before.center())), anchor.place(moved).center());
        assertRotation(parent.compose(before.rotation()), anchor.place(moved).rotation());
        var missed = new MenuSurfaceAttachment.Surface(new UiVector3(100, 100, 3), source.rotation(), source.size(), source.scale());
        assertTrue(MenuSurfaceAttachment.pointer(eye, missed, FloatingMenuFieldOfView.DEFAULT).isEmpty());
    }

    @Test void growingContentIsClampedInsideItsCapturedViewportWithoutChangingOrientationOrTextScale() {
        var source = new MenuSurfaceAttachment.Surface(new UiVector3(0,0,3), UiRotation.degrees(180,0,0), new FloatingMenuSize(2,1),1);
        var pointer = new MenuSurfaceAttachment.Pointer(new UiVector3(0,0,2.8),source.rotation(),new UiSize(2,1.4),.04,UiVector3.ZERO);
        var anchor = MenuSurfaceAttachment.relative(source,new UiVector3(.7,.4,2.8),source.rotation(),1);
        var grown = new FloatingMenuSize(1.5,1);
        var constrained = anchor.constrained(pointer,source,grown);
        var local = pointer.rotation().inverse().apply(constrained.place(source).center().subtract(pointer.center()));
        assertTrue(Math.abs(local.right()) + grown.width()/2 + pointer.gap() <= pointer.viewport().width()/2 + 1e-8);
        assertTrue(Math.abs(local.up()) + grown.height()/2 + pointer.gap() <= pointer.viewport().height()/2 + 1e-8);
        assertEquals(anchor.readingScale(),constrained.readingScale());
        assertRotation(anchor.place(source).rotation(),constrained.place(source).rotation());
        assertSame(constrained,constrained.constrained(pointer,source,grown));
    }
}
