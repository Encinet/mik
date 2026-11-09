package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import org.bukkit.Location;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuPopoverAttachmentTest {
    @Test void worldContractionPreservesTheReadingAngleAndDoesNotCompoundOnRedraw() {
        var eye = new Location(null, 0, 0, 0);
        var source = new MenuSurfaceAttachment.Surface(new UiVector3(0, 0, 3), UiRotation.degrees(180, 0, 0), new FloatingMenuSize(1, 1), 1);
        var size = new FloatingMenuSize(.7, .4);
        var nominal = new MenuPopoverAttachment();
        nominal.update(eye, "source", source, size, FloatingMenuFieldOfView.DEFAULT, List.of(), 0, (_, _) -> 1);
        var actual = new MenuPopoverAttachment(); var checks = new java.util.concurrent.atomic.AtomicInteger();
        MenuPopoverAttachment.WorldConstraint half = (_, _) -> { checks.incrementAndGet(); return .5; };
        actual.update(eye, "source", source, size, FloatingMenuFieldOfView.DEFAULT, List.of(), 0, half);
        assertFalse(actual.blocked());
        assertVector(nominal.placement().center().multiply(.5), actual.placement().center());
        assertEquals(nominal.readingScale() * .5, actual.readingScale(), 1e-10);
        var before = actual.placement();
        for (int frame = 0; frame < 200; frame++) actual.update(eye, "source", source, size, FloatingMenuFieldOfView.DEFAULT, List.of(), 0, half);
        assertEquals(before, actual.placement()); assertEquals(1, checks.get());
        actual.update(eye, "source", source, size, FloatingMenuFieldOfView.DEFAULT, List.of(), 1, half);
        assertEquals(2, checks.get()); assertEquals(before, actual.placement());
    }

    @Test void blockedCompanionsPublishNoPlacementAndRecoverOnAFreshWorldSnapshot() {
        var eye = new Location(null, 0, 0, 0);
        var source = new MenuSurfaceAttachment.Surface(new UiVector3(0, 0, 3), UiRotation.degrees(180, 0, 0), new FloatingMenuSize(1, 1), 1);
        var size = new FloatingMenuSize(.7, .4);
        for (double unavailable : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 2}) {
            var attachment = new MenuPopoverAttachment();
            attachment.update(eye, "source", source, size, FloatingMenuFieldOfView.DEFAULT, List.of(), 0, (_, _) -> unavailable);
            assertTrue(attachment.blocked()); assertNull(attachment.placement()); assertTrue(attachment.readingScale() > 0);
            attachment.update(eye, "source", source, size, FloatingMenuFieldOfView.DEFAULT, List.of(), 1, (_, _) -> 1);
            assertFalse(attachment.blocked()); assertNotNull(attachment.placement());
        }
    }

    @Test void retargetingPreservesPositionAndReadingDoesNotFollowTheGazeOrCreepForward() {
        var eye = new Location(null, 0, 0, 0, 0, 0);
        var rotation = UiRotation.degrees(180,0,0);
        var a = new MenuSurfaceAttachment.Surface(new UiVector3(0,0,3), rotation, new FloatingMenuSize(2,1), .6);
        var b = new MenuSurfaceAttachment.Surface(new UiVector3(.3,0,3), rotation, a.size(), a.scale());
        var attachment = new MenuPopoverAttachment();
        var size = new FloatingMenuSize(.7,.45);
        attachment.update(eye,"a",a,size,FloatingMenuFieldOfView.DEFAULT,List.of(),0,(_,_) -> 1);
        var before = attachment.placement();
        eye.setYaw(30);
        attachment.update(eye,"b",b,size,FloatingMenuFieldOfView.DEFAULT,List.of(),0,(_,_) -> 1);
        assertVector(before.center(), attachment.placement().center());
        assertRotation(before.rotation(), attachment.placement().rotation());
        double scale = attachment.readingScale();
        for (int tick=0; tick<200; tick++) attachment.update(eye,"b",b,size,FloatingMenuFieldOfView.DEFAULT,List.of(),0,(_,_) -> 1);
        assertEquals(before.center(), attachment.placement().center());
        assertEquals(scale, attachment.readingScale(),1e-10);
        attachment.reset();
        assertNull(attachment.placement());
    }

    @Test void aDeferredOpenStillAppearsWhenThePlayerAlreadyLookedAwayFromTheSource() {
        var eye = new Location(null,0,0,0,90,0);
        var source = new MenuSurfaceAttachment.Surface(new UiVector3(0,0,3),UiRotation.degrees(180,0,0),new FloatingMenuSize(1,1),.5);
        var attachment = new MenuPopoverAttachment();
        attachment.update(eye,"source",source,new FloatingMenuSize(.6,.4),FloatingMenuFieldOfView.DEFAULT,List.of(),0,(_,_) -> 1);
        assertNotNull(attachment.placement());
    }

    @Test void aNarrowerFieldOfViewUsesNewBoundsWhilePreservingTheCapturedReadingDirection() {
        var eye = new Location(null,0,0,0,0,0);
        var source = new MenuSurfaceAttachment.Surface(new UiVector3(0,0,3),UiRotation.degrees(180,0,0),new FloatingMenuSize(2,1),1);
        var attachment = new MenuPopoverAttachment();
        attachment.update(eye,"source",source,new FloatingMenuSize(.8,.5),new FloatingMenuFieldOfView(110),List.of(),0,(_,_) -> 1);
        var rotation = attachment.placement().rotation();
        var size = new FloatingMenuSize(1.2,.9);
        attachment.update(eye,"source",source,size,new FloatingMenuFieldOfView(30),List.of(),0,(_,_) -> 1);
        var viewport = MenuSurfaceAttachment.pointer(eye,source,new FloatingMenuFieldOfView(30)).orElseThrow();
        var local = viewport.rotation().inverse().apply(attachment.placement().center().subtract(viewport.center()));
        assertTrue(Math.abs(local.up()) + size.height()*attachment.readingScale()/2 + viewport.gap() <= viewport.viewport().height()/2 + 1e-8);
        assertRotation(rotation,attachment.placement().rotation());
    }
}
