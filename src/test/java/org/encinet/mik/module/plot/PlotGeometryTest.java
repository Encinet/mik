package org.encinet.mik.module.plot;

import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotGeometryTest {
    @Test
    void coordinatesDoNotRetainTheRecordsLinearHashCollisions() {
        assertNotEquals(new Cell(1, 0, 0).hashCode(), new Cell(0, 31, 0).hashCode());
        assertNotEquals(new Cell(0, 1, 0).hashCode(), new Cell(0, 0, 31).hashCode());
        assertEquals(new Cell(-19, 7, Integer.MAX_VALUE).hashCode(), new Cell(-19, 7, Integer.MAX_VALUE).hashCode());
    }

    @Test
    void fullHeightGridHasDistributedHashesAndImmutableCopiesStayFast() {
        Set<Cell> cells = new HashSet<>();
        Set<Integer> hashes = new HashSet<>();
        for (int coordinateX = -16; coordinateX < 16; coordinateX++)
            for (int coordinateY = -16; coordinateY < 80; coordinateY++)
                for (int coordinateZ = -16; coordinateZ < 16; coordinateZ++) {
                    Cell cell = new Cell(coordinateX, coordinateY, coordinateZ);
                    cells.add(cell);
                    hashes.add(cell.hashCode());
                }
        assertEquals(98_304, cells.size());
        assertTrue(hashes.size() > cells.size() * 0.99);
        assertTimeout(Duration.ofSeconds(3), () -> assertEquals(cells, Set.copyOf(cells)));
    }

    @Test
    void columnsCountOnceAcrossFloorsAndNegativeCoordinatesUseFloorDivision() {
        assertEquals(new Cell(-1, -1, -1), Cell.at(-1, -1, -1));
        assertEquals(16, PlotGeometry.horizontalArea(Set.of(new Cell(0, 0, 0), new Cell(0, 1, 0))));
    }

    @Test
    void cellsProtectOnlyTheirExactFourBlockBounds() {
        assertTrue(new Cell(0, 0, 0).protects(0, 0, 0));
        assertTrue(new Cell(0, 0, 0).protects(3, 3, 3));
        assertFalse(new Cell(0, 0, 0).protects(-1, 0, 0));
        assertFalse(new Cell(0, 0, 0).protects(4, 0, 0));
        assertTrue(new Cell(-1, -1, -1).protects(-4, -4, -4));
        assertTrue(new Cell(-1, -1, -1).protects(-1, -1, -1));
        assertFalse(new Cell(-1, -1, -1).protects(-5, -1, -1));
    }
}
