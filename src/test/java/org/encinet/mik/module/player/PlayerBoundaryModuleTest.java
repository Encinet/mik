package org.encinet.mik.module.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerBoundaryModuleTest {

    @Test
    void coordinateInsideBoundaryIsUnchanged() {
        assertEquals(12.5D, PlayerBoundaryModule.clampCoordinate(12.5D, 10.0D, 5.0D));
    }

    @Test
    void coordinateIsClampedOnBothSidesOfOffsetBoundary() {
        assertEquals(15.0D, PlayerBoundaryModule.clampCoordinate(20.0D, 10.0D, 5.0D));
        assertEquals(5.0D, PlayerBoundaryModule.clampCoordinate(-20.0D, 10.0D, 5.0D));
    }

    @Test
    void collapsedBoundaryClampsToCenter() {
        assertEquals(10.0D, PlayerBoundaryModule.clampCoordinate(20.0D, 10.0D, 0.0D));
    }
}
