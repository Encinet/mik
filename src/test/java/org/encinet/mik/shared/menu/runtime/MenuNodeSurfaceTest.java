package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.assertRotation;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.assertVector;
import static org.junit.jupiter.api.Assertions.*;

class MenuNodeSurfaceTest {
    @Test void feedbackMovesOnlyAlongTheCurrentNormalAndInputUsesItsActualScaleAndRoll() {
        var target = new Vector(.3, 1.2, 3);
        var pose = new FloatingMenuPose(FloatingMenuPoint.ORIGIN, 23, 17, 38, .8);
        var size = new FloatingMenuSize(.8, .4);
        var surface = MenuNodeSurface.sample(pose, target, org.encinet.mik.shared.menu.ui.UiRotation.degrees(180,0,0), .7, size, FloatingMenuAnimation.DEFAULT,
                new MenuNodeSurface.Feedback(1, .5, .75, false));
        assertEquals(.56, surface.scale(), 1e-8);
        assertRotation(UiRotation.degrees(203, 17, 38), surface.rotation());
        var normal = FloatingMenuSurfaceGeometry.facing(203, 17);
        double distance = (-.12 * .25 + .045 - .06 * .5) * .56;
        assertVector(MenuSurfaceAttachment.point(target.clone().add(normal.multiply(distance))), surface.center());
        assertEquals(new Vector(.3, 1.2, 3), target, "Projection must not mutate a shared source point");
        var eye = new Location(null, 0, 0, 0).setDirection(MenuSurfaceAttachment.vector(surface.center()));
        assertEquals(.5, surface.intersect(eye, 0).orElseThrow().horizontalFraction(), 1e-5);
        var far = new Location(null, 0, 0, 0).setDirection(new Vector(10, 0, 2));
        assertTrue(surface.intersect(far, 0).isEmpty());
    }

    @Test void reducedMotionRemovesEntryAndFeedbackOffsetsWithoutChangingLogicalGeometry() {
        var pose = new FloatingMenuPose(FloatingMenuPoint.ORIGIN, -14, 8, 20, 1.3);
        var target = new Vector(.2, -.1, 3);
        var surface = MenuNodeSurface.sample(pose, target, org.encinet.mik.shared.menu.ui.UiRotation.degrees(180,0,0), .9, new FloatingMenuSize(1, .6), FloatingMenuAnimation.DEFAULT,
                new MenuNodeSurface.Feedback(1, 1, 0, true));
        assertVector(MenuSurfaceAttachment.point(target), surface.center());
        assertEquals(1.17, surface.scale(), 1e-8);
        assertRotation(UiRotation.degrees(166, 8, 20), surface.rotation());
    }

    @Test void planarFeedbackKeepsTheSameRenderingAndInputPlaneThroughEntryHoverAndPress() {
        var target = new Vector(.2,-.1,3);
        var pose = new FloatingMenuPose(FloatingMenuPoint.ORIGIN,23,17,38,.8);
        var size = new FloatingMenuSize(.8,.4);
        for (double reveal : new double[]{0,.5,1}) for (double hover : new double[]{0,.5,1})
            for (double press : new double[]{0,.5,1}) {
                var surface = MenuNodeSurface.sample(pose,target,UiRotation.degrees(180,0,0),.7,size,FloatingMenuAnimation.PLANAR,
                        new MenuNodeSurface.Feedback(hover,press,reveal,false));
                assertVector(MenuSurfaceAttachment.point(target),surface.center());
                assertRotation(UiRotation.degrees(203,17,38),surface.rotation());
                assertEquals(.56,surface.scale(),1e-8);
                var eye = new Location(null,0,0,0).setDirection(target);
                assertEquals(.5,surface.intersect(eye,0).orElseThrow().horizontalFraction(),1e-5);
            }
    }

    @Test void entryAndPointerDepthCanBeConfiguredIndependently() {
        var animation = new FloatingMenuAnimation(6,4,3,.3,.08,.02,0,0,
                FloatingMenuEasing.CUBIC_OUT,FloatingMenuEasing.CUBIC_OUT);
        var surface = MenuNodeSurface.sample(FloatingMenuPose.ORIGIN,new Vector(0,0,3),UiRotation.IDENTITY,.5,
                new FloatingMenuSize(1,.5),animation,new MenuNodeSurface.Feedback(1,1,.5,false));
        assertVector(new UiVector3(0,0,3 + (-.15 + .08 - .02) * .5),surface.center());
    }

    @Test void translatedSurfacesRetainTheSameImmutableReadingAndInputGeometry() {
        var initial = new MenuSurfaceAttachment.Surface(new UiVector3(1, 2, 3), UiRotation.degrees(160, 8, 22), new FloatingMenuSize(1, .5), .7, .2);
        var shifted = initial.translated(new UiVector3(.3, -.2, 1));
        assertVector(new UiVector3(1, 2, 3), initial.center());
        assertVector(new UiVector3(1.3, 1.8, 4), shifted.center());
        assertEquals(initial.rotation(), shifted.rotation()); assertEquals(initial.size(), shifted.size());
        assertEquals(initial.scale(), shifted.scale()); assertEquals(initial.depth(), shifted.depth());
    }

    @Test void aNewFootprintAtTheSamePosePublishesItsOwnInputBounds() {
        var pose = FloatingMenuPose.ORIGIN;
        var target = new Vector(0, 0, 3);
        var state = new MenuNodeSurface.Feedback(0, 0, 1, false);
        var narrow = MenuNodeSurface.sample(pose, target, org.encinet.mik.shared.menu.ui.UiRotation.degrees(180,0,0), 1, new FloatingMenuSize(.4, .3), FloatingMenuAnimation.DEFAULT, state);
        var wide = MenuNodeSurface.sample(pose, target, org.encinet.mik.shared.menu.ui.UiRotation.degrees(180,0,0), 1, new FloatingMenuSize(1.2, .6), FloatingMenuAnimation.DEFAULT, state);
        var eye = new Location(null, 0, 0, 0).setDirection(new Vector(.4, 0, 3));
        assertTrue(narrow.intersect(eye, 0).isEmpty());
        assertTrue(wide.intersect(eye, 0).isPresent());
        assertEquals(.4, narrow.size().width());
        assertEquals(1.2, wide.size().width());
    }
}
