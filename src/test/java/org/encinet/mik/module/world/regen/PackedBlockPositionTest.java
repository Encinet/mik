package org.encinet.mik.module.world.regen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackedBlockPositionTest {

    @Test
    void roundTripsCoordinatesAcrossNegativeWorldHeights() {
        int packed = PackedBlockPosition.pack(15, -37, 9, -64);

        assertEquals(15, PackedBlockPosition.localX(packed));
        assertEquals(9, PackedBlockPosition.localZ(packed));
        assertEquals(-37, PackedBlockPosition.y(packed, -64));
    }

    @Test
    void rejectsCoordinatesOutsideAChunkSnapshot() {
        assertThrows(IllegalArgumentException.class,
                () -> PackedBlockPosition.pack(16, 0, 0, -64));
        assertThrows(IllegalArgumentException.class,
                () -> PackedBlockPosition.pack(0, 0, -1, -64));
        assertThrows(IllegalArgumentException.class,
                () -> PackedBlockPosition.pack(0, -65, 0, -64));
    }
}
