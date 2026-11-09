package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.encinet.mik.shared.menu.FloatingMenuRing;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuMotionTest {
    @Test void aRetargetedPoseConvergesToTheLatestPositionRotationAndScaleWithoutQueuingTheOldTarget() {
        var current = FloatingMenuPose.transformed(FloatingMenuPoint.ORIGIN, UiRotation.degrees(0, 89, 0), 1);
        var obsolete = FloatingMenuPose.transformed(new FloatingMenuPoint(4, 1, 2), UiRotation.degrees(0, 91, 175), 2);
        for (int index = 0; index < 5; index++) current = FloatingMenuMotion.approachPose(current, obsolete, .25);
        var latest = FloatingMenuPose.transformed(new FloatingMenuPoint(-2, 3, 1), UiRotation.degrees(45, -30, -175), .8);
        for (int index = 0; index < 100; index++) current = FloatingMenuMotion.approachPose(current, latest, .25);
        assertEquals(latest, current);
    }

    @Test void equivalentRollRepresentationsDoNotKeepAnOrbitAliveForever() {
        var current = new FloatingMenuPose(new FloatingMenuPoint(0, 0, -4), 0, 0, 181, 1);
        var target = new FloatingMenuPose(current.point(), 0, 0, -179, 1);
        assertEquals(target, FloatingMenuMotion.approachOrbit(current, target, .3));
    }
    @Test void entryMotionIsBoundedAndReducedMotionKeepsTheFinalPlane() {
        assertEquals(-.12, FloatingMenuMotion.entryDepth(0, FloatingMenuAnimation.DEFAULT));
        assertEquals(-.06, FloatingMenuMotion.entryDepth(.5, FloatingMenuAnimation.DEFAULT), 1e-9);
        assertEquals(0, FloatingMenuMotion.entryDepth(1, FloatingMenuAnimation.DEFAULT));
        assertEquals(0, FloatingMenuMotion.entryDepth(5, FloatingMenuAnimation.DEFAULT));
        assertEquals(-.12, FloatingMenuMotion.entryDepth(-5, FloatingMenuAnimation.DEFAULT));
        for (double progress : new double[] {0, .2, .5, 1}) {
            assertEquals(0, FloatingMenuMotion.entryDepth(progress, FloatingMenuAnimation.DEFAULT.reduced()));
            assertEquals(0, FloatingMenuMotion.entryDepth(progress, FloatingMenuAnimation.PLANAR));
        }
    }
    @Test void fullyTransparentOrInsufficientlyRevealedContentCannotReceiveInput() {
        assertFalse(FloatingMenuMotion.inputReady(1, 0));
        assertFalse(FloatingMenuMotion.inputReady(0, 1));
        assertFalse(FloatingMenuMotion.inputReady(0.2, 0.5));
        assertTrue(FloatingMenuMotion.inputReady(0.4, 0.5));
        assertTrue(FloatingMenuMotion.inputReady(1, 1));
    }
    @Test
    void trackingDecorationsNeverRetractTowardTheSceneOrigin() {
        for (double progress : new double[] {0, 0.2, 0.5, 1, 1.12}) {
            assertEquals(1, FloatingMenuMotion.decorationPositionFactor(false,
                    FloatingMenuDecoration.Transition.TRACKING, progress));
            assertEquals(1, FloatingMenuMotion.decorationPositionFactor(true,
                    FloatingMenuDecoration.Transition.SMOOTH, progress));
            assertEquals(FloatingMenuMotion.progress(progress), FloatingMenuMotion.decorationPositionFactor(false,
                    FloatingMenuDecoration.Transition.SMOOTH, progress));
        }
    }

    @Test
    void openingAndClosingNeverPullAnOrbitThroughTheViewer() {
        for (double progress : new double[]{0, 0.1, 0.5, 1, 1.12}) {
            assertEquals(1, FloatingMenuMotion.positionFactor(true, progress));
            assertTrue(FloatingMenuMotion.positionFactor(false, progress) <= 1);
        }
    }

    @Test
    void clampsBackOutOvershootAndOpacity() {
        assertEquals(1, FloatingMenuMotion.progress(1.12));
        assertEquals(0, FloatingMenuMotion.progress(-0.1));
    }

    @Test
    void revealOpacitySupportsFullyHiddenContentAndUnsignedFullOpacity() {
        assertEquals(0, Byte.toUnsignedInt(FloatingMenuMotion.textOpacity(0)));
        assertEquals(1, Byte.toUnsignedInt(FloatingMenuMotion.textOpacity(0.005)));
        assertEquals(128, Byte.toUnsignedInt(FloatingMenuMotion.textOpacity(0.5)));
        assertEquals(255, Byte.toUnsignedInt(FloatingMenuMotion.textOpacity(1.12)));
    }

    @Test
    void largePageChangesReplaceSlotsWithoutSweepingAcrossTheScreen() {
        FloatingMenuPoint current = FloatingMenuRing.pose(0, 8, 2, 0).point();
        FloatingMenuPoint target = FloatingMenuRing.pose(4, 8, 2, 0).point();
        assertEquals(target, FloatingMenuMotion.approachRing(current, target, 0.3));
        assertEquals(new FloatingMenuPoint(0, 0, -3), FloatingMenuMotion.approachRing(
                current, new FloatingMenuPoint(0, 0, -3), 0.3));
    }

    @Test
    void smallArcChangesRetainRadiusAndTakeTheShortPathAcrossWraparound() {
        double angle = Math.toRadians(179);
        FloatingMenuPoint current = new FloatingMenuPoint(2 * Math.sin(angle), 0, -2 * Math.cos(angle));
        FloatingMenuPoint target = new FloatingMenuPoint(-current.right(), 0, current.forward());
        FloatingMenuPoint next = FloatingMenuMotion.approachRing(current, target, 0.5);
        assertEquals(2, Math.hypot(next.right(), next.forward()), 1.0E-6);
        assertEquals(0, next.right(), 1.0E-6);
        assertTrue(next.forward() > 1.99);
    }

    @Test
    void languageScrollMovesAlongTheOrbitInsteadOfSnappingAndRevealing() {
        var current = FloatingMenuRing.pose(0, 16, 4, 1);
        var target = FloatingMenuRing.pose(-1, 16, 4, 1);
        var halfway = FloatingMenuMotion.approachOrbit(current, target, 0.5);
        assertEquals(-11.25, halfway.yawDegrees(), 1.0E-6);
        assertEquals(4, Math.hypot(halfway.right(), halfway.forward()), 1.0E-6);
        assertEquals(1, halfway.up());
        assertTrue(halfway.right() < 0 && halfway.right() > target.right());
    }

    @Test
    void rapidScrollRetainsItsDirectionEvenBeyondHalfATurn() {
        var current = FloatingMenuRing.pose(0, 16, 4, 1);
        var target = FloatingMenuRing.pose(-10, 16, 4, 1);
        var halfway = FloatingMenuMotion.approachOrbit(current, target, 0.5);
        assertEquals(-112.5, halfway.yawDegrees(), 1.0E-6);
        var next = FloatingMenuMotion.approachOrbit(halfway, FloatingMenuRing.pose(-11, 16, 4, 1), 0.5);
        assertTrue(next.yawDegrees() < halfway.yawDegrees());
        assertEquals(4, Math.hypot(next.right(), next.forward()), 1.0E-6);
    }

    @Test
    void orbitSettlesExactlyAndDoesNotReverseAcrossFullTurns() {
        var current = FloatingMenuRing.pose(-15, 16, 4, 1);
        var target = FloatingMenuRing.pose(-16, 16, 4, 1);
        for (int tick = 0; tick < 40; tick++) current = FloatingMenuMotion.approachOrbit(current, target, 0.3);
        assertEquals(target, current);
        assertEquals(-360, current.yawDegrees());
    }
}
