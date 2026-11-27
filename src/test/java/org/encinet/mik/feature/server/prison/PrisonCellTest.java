package org.encinet.mik.feature.server.prison;

import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrisonCellTest {
    @Test
    void candidateSectorsUseActualRoomCellsAndRespectTheHeightLimit() {
        var region = Set.of(Cell.at(0, 64, 0), Cell.at(8, 64, 0), Cell.at(0, 72, 0));
        var height = new PrisonCell.Height(-64, 66);
        var sectors = PrisonCell.sectors(region, height);
        assertEquals(2, sectors.size());
        var plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Room",
                0, region, Map.of(), Map.of(), null);
        for (var sector : sectors) {
            assertTrue(plot.protects(sector.x(), sector.low(), sector.z()));
            assertTrue(plot.protects(sector.x(), sector.high() + 1, sector.z()));
            assertEquals(64, sector.high());
        }
        assertTrue(PrisonCell.sectors(region, new PrisonCell.Height(-64, 65)).isEmpty());
    }

    @Test
    void limitsTheSearchAndPrefersHigherSectors() {
        var geometry = new HashSet<Cell>();
        for (int y = 0; y < 100; y += 4) geometry.add(Cell.at(-4, y, -4));
        var sectors = PrisonCell.sectors(geometry, new PrisonCell.Height(-64, 320));
        assertEquals(8, sectors.size());
        assertEquals(96, sectors.getFirst().low());
        assertEquals(-4, sectors.getFirst().x());
        assertEquals(68, sectors.getLast().low());
    }
}
