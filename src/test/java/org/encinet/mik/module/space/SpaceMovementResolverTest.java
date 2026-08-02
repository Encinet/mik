package org.encinet.mik.module.space;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceMovementResolverTest {

    @Test
    void slabCollisionClipsFallingButPreservesHorizontalMotion() {
        SpaceVector entry = new SpaceVector(0.0, 0.5, 0.0);
        SpaceVector desiredBelowSlab = new SpaceVector(0.0, -2.0, -0.4);

        SpaceMovementResolver.Result result = SpaceMovementResolver.resolve(
                entry,
                desiredBelowSlab,
                new SpaceVector(0.0, 0.0, 1.0),
                0.0,
                0.6,
                position -> position.y() < 0.5 && position.y() > -1.8).orElseThrow();

        assertEquals(0.5, result.position().y(), 1.0E-6);
        assertEquals(-0.4, result.position().z(), 1.0E-9);
        assertTrue(result.blockedY());
        assertEquals(new SpaceVector(0.2, 0.0, -0.4),
                result.clipVelocity(new SpaceVector(0.2, -0.8, -0.4)));
    }

    @Test
    void slightEntryOverlapIsLiftedToTheSlabSurface() {
        SpaceMovementResolver.Result result = SpaceMovementResolver.resolve(
                new SpaceVector(0.0, 0.49, 0.0),
                new SpaceVector(0.0, 0.45, -0.4),
                new SpaceVector(0.0, 0.0, 1.0),
                0.0,
                0.6,
                position -> position.y() < 0.5 && position.y() > -1.8).orElseThrow();

        assertEquals(0.5, result.position().y(), 1.0E-6);
        assertEquals(-0.4, result.position().z(), 1.0E-9);
        assertTrue(result.blockedY());
    }

    @Test
    void ceilingOverlapIsResolvedDownwardInsteadOfForcedUpward() {
        SpaceMovementResolver.Result result = SpaceMovementResolver.resolve(
                new SpaceVector(0.0, 1.01, 0.0),
                new SpaceVector(0.0, 1.05, 0.4),
                new SpaceVector(0.0, 0.0, 1.0),
                0.0,
                0.6,
                position -> position.y() > 1.0 && position.y() < 2.8).orElseThrow();

        assertEquals(1.0, result.position().y(), 1.0E-6);
        assertEquals(0.4, result.position().z(), 1.0E-9);
        assertTrue(result.blockedY());
    }

    @Test
    void unobstructedMovementRemainsExact() {
        SpaceVector entry = new SpaceVector(10.0, 65.0, 20.0);
        SpaceVector desired = new SpaceVector(10.25, 64.9, 19.6);

        SpaceMovementResolver.Result result = SpaceMovementResolver.resolve(
                entry, desired,
                new SpaceVector(0.0, 0.0, 1.0), 0.0,
                0.6, _ -> false).orElseThrow();

        assertEquals(desired, result.position());
        assertTrue(!result.blockedX() && !result.blockedY() && !result.blockedZ());
    }

    @Test
    void blockedEntryDoesNotChooseAnUnrelatedSafePosition() {
        Optional<SpaceMovementResolver.Result> result = SpaceMovementResolver.resolve(
                SpaceVector.ZERO,
                new SpaceVector(0.0, 0.0, 0.2),
                new SpaceVector(0.0, 0.0, 1.0),
                0.0,
                0.0,
                _ -> true);

        assertTrue(result.isEmpty());
    }

    @Test
    void downwardPortalClearsTheWholeUprightBodyAlongItsExitNormal() {
        double planeY = 72.5;
        double playerHeight = 1.8;
        SpaceMovementResolver.Result result = SpaceMovementResolver.resolve(
                new SpaceVector(0.0, planeY, 0.0),
                new SpaceVector(0.0, planeY - 0.2, 0.0),
                new SpaceVector(0.0, -1.0, 0.0),
                playerHeight + 0.02,
                0.6,
                position -> position.y() + playerHeight > planeY).orElseThrow();

        assertEquals(planeY - playerHeight - 0.2,
                result.position().y(), 1.0E-6);
        assertTrue(!result.blockedY());
        assertEquals(new SpaceVector(0.0, -1.4, 0.0),
                result.clipVelocity(new SpaceVector(0.0, -1.4, 0.0)));
    }

    @Test
    void downwardPortalRejectsARealObstacleBeyondBodyClearance() {
        Optional<SpaceMovementResolver.Result> result = SpaceMovementResolver.resolve(
                new SpaceVector(0.0, 72.5, 0.0),
                new SpaceVector(0.0, 72.3, 0.0),
                new SpaceVector(0.0, -1.0, 0.0),
                1.82,
                0.6,
                _ -> true);

        assertTrue(result.isEmpty());
    }
}
