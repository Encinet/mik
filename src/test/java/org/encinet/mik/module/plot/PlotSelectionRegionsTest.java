package org.encinet.mik.module.plot;

import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotSelectionRegionsTest {
    private final UUID world = UUID.randomUUID();

    @Test
    void allSmallShapesDecomposeWithoutOverlapsHolesOrInsertionOrderDependence() {
        for (int pattern = 1; pattern < 512; pattern++) {
            Set<Cell> cells = new LinkedHashSet<>();
            for (int index = 0; index < 9; index++) {
                if ((pattern & (1 << index)) != 0) cells.add(new Cell(index % 3 - 1, 16, index / 3 - 1));
            }
            var shape = new PlotSelectionShape(world, null, cells);
            var regions = PlotSelectionRegions.decompose(shape);
            Set<Cell> rebuilt = new HashSet<>();
            for (var region : regions) {
                for (Cell cell : PlotSelectionShape.of(region.selection(world)).alignedCells())
                    assertTrue(rebuilt.add(cell), "Rectangles must not overlap");
            }
            assertEquals(cells, rebuilt);
            List<Cell> reversed = new ArrayList<>(cells);
            Collections.reverse(reversed);
            assertEquals(regions, PlotSelectionRegions.decompose(
                    new PlotSelectionShape(world, null, new LinkedHashSet<>(reversed))));
        }
    }

    @Test
    void verticalVolumesMergeAndCourtyardRetainsFourSeparateRectangles() {
        Set<Cell> volume = new HashSet<>();
        Set<Cell> courtyard = new HashSet<>();
        for (int horizontal = 0; horizontal < 3; horizontal++) {
            for (int depth = 0; depth < 3; depth++) {
                if (horizontal != 1 || depth != 1) courtyard.add(new Cell(horizontal, 16, depth));
                for (int vertical = 16; vertical < 19; vertical++) volume.add(new Cell(horizontal, vertical, depth));
            }
        }
        assertEquals(1, PlotSelectionRegions.decompose(new PlotSelectionShape(world, null, volume)).size());
        assertEquals(4, PlotSelectionRegions.decompose(new PlotSelectionShape(world, null, courtyard)).size());
        assertFalse(courtyard.contains(new Cell(1, 16, 1)));
    }

    @Test
    void sparseAndHugeSelectionsNeverEnumerateTheirEmptyEnvelope() {
        Set<Cell> cells = Set.of(Cell.at(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                Cell.at(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(2, PlotSelectionRegions.decompose(new PlotSelectionShape(world, null, cells)).size());
        PlotSelectionShape enormous = PlotSelectionShape.of(new PlotPosition(world, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                new PlotPosition(world, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(List.of(new PlotSelectionRegions.Region(enormous.alignedBounds())), PlotSelectionRegions.decompose(enormous));
        assertTrue(PlotSelectionRegions.remove(enormous, PlotSelectionRegions.decompose(enormous).getFirst()).empty());
    }

    @Test
    void removingAndReplacingOneRectanglePreservesEveryOtherRectangle() {
        var shape = PlotSelectionShape.fromCells(world, Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0), new Cell(0, 16, 1)));
        var region = PlotSelectionRegions.decompose(shape).get(1);
        assertEquals(Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0)), PlotSelectionRegions.remove(shape, region).alignedCells());
        var replacement = new PlotSelection(new PlotPosition(world, 0, 64, 8), new PlotPosition(world, 3, 67, 11));
        assertEquals(Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0), new Cell(0, 16, 2)),
                PlotSelectionRegions.replace(shape, region, replacement).alignedCells());
        var overlap = new PlotSelection(new PlotPosition(world, 0, 64, 0), new PlotPosition(world, 7, 67, 3));
        assertEquals(2, PlotSelectionRegions.replace(shape, region, overlap).alignedCells().size());
        UUID foreign = UUID.randomUUID();
        assertThrows(PlotProblem.class, () -> PlotSelectionRegions.replace(shape, region,
                new PlotSelection(new PlotPosition(foreign, 0, 64, 0), new PlotPosition(foreign, 3, 67, 3))));
    }
}
