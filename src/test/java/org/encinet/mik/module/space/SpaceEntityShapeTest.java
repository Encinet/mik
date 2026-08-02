package org.encinet.mik.module.space;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpaceEntityShapeTest {

    private static final SpaceEntityShape STANDING_PLAYER = SpaceEntityShape.from(
            new BoundingBox(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            SpaceVector.ZERO);

    @Test
    void downwardExitClearsThePlayersFullHeight() {
        assertEquals(1.82, STANDING_PLAYER.entryClearance(
                new SpaceVector(0.0, -1.0, 0.0)), 1.0E-9);
    }

    @Test
    void wallExitClearsHalfThePlayersWidth() {
        assertEquals(0.32, STANDING_PLAYER.entryClearance(
                new SpaceVector(1.0, 0.0, 0.0)), 1.0E-9);
    }

    @Test
    void upwardExitOnlyNeedsANumericalMarginAtThePlayersFeet() {
        assertEquals(0.02, STANDING_PLAYER.entryClearance(
                new SpaceVector(0.0, 1.0, 0.0)), 1.0E-9);
    }
}
