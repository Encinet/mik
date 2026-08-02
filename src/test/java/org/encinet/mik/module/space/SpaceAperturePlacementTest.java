package org.encinet.mik.module.space;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceAperturePlacementTest {

    private static final SpaceFrame EAST_WALL = SpaceFrame.fromAxes(
            SpaceVector.ZERO,
            new SpaceVector(1.0, 0.0, 0.0),
            new SpaceVector(0.0, 1.0, 0.0));
    private static final SpaceEntityShape STANDING_PLAYER = SpaceEntityShape.from(
            new BoundingBox(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            SpaceVector.ZERO);

    @Test
    void highAnchorIsMovedDownUntilThePlayersHeadFitsTheWallPortal() {
        SpaceAperturePlacement.Result result = SpaceAperturePlacement.fit(
                EAST_WALL,
                new SpaceAperture(3.0, 3.0),
                EAST_WALL.fromLocalPoint(new SpaceVector(0.0, 1.5, 0.0)),
                EAST_WALL.fromLocalPoint(new SpaceVector(0.0, 1.5, 0.25)),
                STANDING_PLAYER).orElseThrow();

        SpaceVector localEntry = EAST_WALL.toLocalPoint(result.entry());
        SpaceVector localDesired = EAST_WALL.toLocalPoint(result.desired());
        assertEquals(-0.32, localEntry.y(), 1.0E-9);
        assertEquals(-0.32, localDesired.y(), 1.0E-9);
        assertEquals(0.25, localDesired.z(), 1.0E-9);
    }

    @Test
    void everyHorizontalPortalPositionIsClampedInsideTheUsableWallOpening() {
        for (double localWidth : new double[]{-1.5, -0.75, 0.0, 0.75, 1.5}) {
            for (double localHeight : new double[]{-1.5, -0.75, 0.0, 0.75, 1.5}) {
                SpaceAperturePlacement.Result result = SpaceAperturePlacement.fit(
                        EAST_WALL,
                        new SpaceAperture(3.0, 3.0),
                        EAST_WALL.fromLocalPoint(
                                new SpaceVector(localWidth, localHeight, 0.0)),
                        EAST_WALL.fromLocalPoint(
                                new SpaceVector(localWidth, localHeight, 0.2)),
                        STANDING_PLAYER).orElseThrow();
                SpaceVector fitted = EAST_WALL.toLocalPoint(result.entry());

                assertTrue(fitted.x() >= -1.18 - 1.0E-9);
                assertTrue(fitted.x() <= 1.18 + 1.0E-9);
                assertTrue(fitted.y() >= -1.48 - 1.0E-9);
                assertTrue(fitted.y() <= -0.32 + 1.0E-9);
            }
        }
    }

    @Test
    void entityLargerThanTheOpeningIsRejected() {
        SpaceEntityShape oversized = SpaceEntityShape.from(
                new BoundingBox(-2.0, 0.0, -0.3, 2.0, 4.0, 0.3),
                SpaceVector.ZERO);

        assertTrue(SpaceAperturePlacement.fit(
                EAST_WALL,
                new SpaceAperture(3.0, 3.0),
                SpaceVector.ZERO,
                new SpaceVector(0.2, 0.0, 0.0),
                oversized).isEmpty());
    }
}
