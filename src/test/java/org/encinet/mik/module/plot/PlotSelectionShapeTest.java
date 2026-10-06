package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotSelectionShapeTest {
    private final UUID world = UUID.randomUUID();

    @Test
    void analyticMembershipAndOverlapMatchActualAlignedCellsForNegativeAndReversedCorners() {
        for (int offset = -9; offset <= 9; offset++) {
            PlotSelectionShape shape = PlotSelectionShape.of(new PlotPosition(world, offset + 9, 72, 5),
                    new PlotPosition(world, offset, 63, -5));
            Set<Cell> aligned = shape.alignedCells();
            assertEquals(BigInteger.valueOf(aligned.size()), shape.alignedCellCount());
            for (int coordinateX = -4; coordinateX <= 5; coordinateX++)
                for (int coordinateY = 14; coordinateY <= 20; coordinateY++)
                    for (int coordinateZ = -3; coordinateZ <= 3; coordinateZ++) {
                        Cell cell = new Cell(coordinateX, coordinateY, coordinateZ);
                        assertEquals(aligned.contains(cell), shape.contains(cell));
                        assertEquals(!Collections.disjoint(aligned, Set.of(cell)), shape.intersects(Set.of(cell)));
                    }
            Set<Cell> larger = new HashSet<>(aligned);
            larger.add(new Cell(100, 100, 100));
            assertTrue(shape.intersects(larger));
            assertFalse(shape.intersects(Set.of()));
        }
    }

    @Test
    void analyticOverlapKeepsCompositeCourtyardHolesAndDoesNotFillTheirEnvelope() {
        PlotSelectionShape shape = PlotSelectionShape.of(box(0, 0, 11, 11))
                .apply(box(4, 4, 7, 7), PlotSelectionShape.Operation.SUBTRACT);
        assertFalse(shape.contains(new Cell(1, 16, 1)));
        assertFalse(shape.intersects(Set.of(new Cell(1, 16, 1))));
        assertTrue(shape.intersects(Set.of(new Cell(0, 16, 0))));
        assertFalse(PlotSelectionShape.fromCells(world, Set.of()).intersects(shape.cells()));
    }

    @Test
    void overlappingAndAdjacentBoxesAutomaticallyCollapseToOneExactCuboid() {
        PlotSelectionShape merged = PlotSelectionShape.of(box(-4, 0, 3, 3))
                .apply(box(0, 0, 7, 3), PlotSelectionShape.Operation.ADD);
        assertFalse(merged.composite());
        assertEquals(3, merged.alignedCells().size());
        assertEquals(new PlotSelection.Bounds(-4, 64, 0, 7, 67, 3), merged.bounds());
        assertEquals(12, PlotPreviewGeometry.selection(merged).size());
        assertEquals(merged, merged.apply(box(0, 0, 3, 3), PlotSelectionShape.Operation.ADD));
    }

    @Test
    void normalizationNeverFillsMissingCellsForAnySmallShape() {
        for (int pattern = 1; pattern < 512; pattern++) {
            Set<Cell> cells = new HashSet<>();
            for (int index = 0; index < 9; index++) {
                if ((pattern & (1 << index)) != 0)
                    cells.add(new Cell(index % 3 - 1, 16, index / 3 - 1));
            }
            PlotSelectionShape normalized = PlotSelectionShape.fromCells(world, cells);
            assertEquals(cells, normalized.alignedCells());
            assertEquals(normalized, PlotSelectionShape.fromCells(world, normalized.alignedCells()));
        }
    }

    @Test
    void stackedCellsMergeInAllThreeAxesAndKeepTheirExactVolume() {
        Set<Cell> cells = new HashSet<>();
        for (int horizontal = -1; horizontal <= 1; horizontal++)
            for (int vertical = 16; vertical <= 18; vertical++)
                for (int depth = -2; depth <= 0; depth++)
                    cells.add(new Cell(horizontal, vertical, depth));
        PlotSelectionShape merged = PlotSelectionShape.fromCells(world, cells);
        assertFalse(merged.composite());
        assertEquals(cells, merged.alignedCells());
        assertEquals(new PlotSelection.Bounds(-4, 64, -8, 7, 75, 3), merged.bounds());
        assertEquals(12, PlotPreviewGeometry.selection(merged).size());
    }

    @Test
    void hugeSparseEnvelopesDoNotOverflowIntoFalseCuboids() {
        Set<Cell> cells = Set.of(Cell.at(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                Cell.at(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        PlotSelectionShape normalized = PlotSelectionShape.fromCells(world, cells);
        assertTrue(normalized.composite());
        assertEquals(cells, normalized.alignedCells());
    }

    @Test
    void subtractionKeepsCourtyardHolesAndAdditionDoesNotFillTheEnvelope() {
        PlotSelectionShape courtyard = PlotSelectionShape.of(box(0, 0, 11, 11))
                .apply(box(4, 4, 7, 7), PlotSelectionShape.Operation.SUBTRACT);
        assertEquals(8, courtyard.cells().size());
        assertFalse(courtyard.cells().contains(new Cell(1, 16, 1)));
        assertTrue(PlotGeometry.connected(courtyard.cells()));
        assertEquals(12, courtyard.bounds().width());
        PlotSelectionShape extended = courtyard.apply(box(12, 0, 15, 3), PlotSelectionShape.Operation.ADD);
        assertEquals(9, extended.cells().size());
        assertFalse(extended.cells().contains(new Cell(3, 16, 1)));
        assertFalse(extended.cells().contains(new Cell(1, 16, 1)));
        assertEquals(8, courtyard.cells().size());
        assertThrows(UnsupportedOperationException.class, () -> courtyard.cells().clear());
    }

    @Test
    void replacementAndSubtractionSnapWholeCellsIncludingNegativeCoordinates() {
        PlotSelectionShape original = PlotSelectionShape.of(box(-4, -4, 3, 3));
        PlotSelectionShape removed = original.apply(box(-1, -1, -1, -1), PlotSelectionShape.Operation.SUBTRACT);
        assertEquals(3, removed.cells().size());
        assertFalse(removed.cells().contains(new Cell(-1, 16, -1)));
        PlotSelectionShape replaced = removed.apply(box(-5, -5, -5, -5), PlotSelectionShape.Operation.REPLACE);
        assertEquals(Set.of(new Cell(-2, 16, -2)), replaced.alignedCells());
        assertEquals(-8, replaced.bounds().minimumX());
        assertEquals(-5, replaced.bounds().maximumX());
    }

    @Test
    void emptyResultRemainsEmptyAndCannotSupplyBounds() {
        PlotSelectionShape empty = PlotSelectionShape.of(box(0, 0, 3, 3))
                .apply(box(0, 0, 3, 3), PlotSelectionShape.Operation.SUBTRACT);
        assertTrue(empty.empty());
        assertThrows(PlotProblem.class, empty::bounds);
        assertEquals(1, empty.apply(box(8, 0, 8, 0), PlotSelectionShape.Operation.ADD).alignedCells().size());
    }

    @Test
    void alignedBoundsUseLongDimensionsWithoutExpandingTheSelection() {
        PlotSelection enormous = new PlotSelection(new PlotPosition(world, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                new PlotPosition(world, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(enormous.bounds(), PlotSelectionShape.of(enormous).alignedBounds());
        assertEquals(4_294_967_296L, PlotSelectionShape.of(enormous).alignedBounds().width());
        PlotSelection reversed = new PlotSelection(new PlotPosition(world, 5, 67, 9),
                new PlotPosition(world, -5, 65, -1));
        assertEquals(new PlotSelection.Bounds(-8, 64, -4, 7, 67, 11),
                PlotSelectionShape.of(reversed).alignedBounds());
    }

    @Test
    void cuboidAndCompositeSelectionsCanExceedTheFormerCellLimit() {
        PlotSelectionShape large = PlotSelectionShape.of(new PlotSelection(new PlotPosition(world, 0, 0, 0),
                new PlotPosition(world, 127, 127, 127)));
        Set<Cell> cells = large.alignedCells();
        assertEquals(32768, cells.size());
        assertTrue(cells.contains(new Cell(31, 31, 31)));
        PlotSelectionShape composite = new PlotSelectionShape(world, null, cells);
        assertEquals(cells, composite.cells());
        assertEquals(large.alignedBounds(), composite.alignedBounds());
    }

    @Test
    void enormousSubtractionBrushOnlyChecksTheExistingCells() {
        PlotSelectionShape original = PlotSelectionShape.of(box(-4, -4, 3, 3));
        PlotSelection brush = new PlotSelection(new PlotPosition(world, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                new PlotPosition(world, -1, Integer.MAX_VALUE, Integer.MAX_VALUE));
        PlotSelectionShape remaining = original.apply(brush, PlotSelectionShape.Operation.SUBTRACT);
        assertEquals(Set.of(new Cell(0, 16, -1), new Cell(0, 16, 0)), remaining.alignedCells());
        assertEquals(4, original.alignedCells().size());
    }

    @Test
    void rejectsCrossWorldBrushesAndPreservesPreciseSelectionCorners() {
        PlotSelection raw = box(1, 1, 2, 2);
        assertEquals(2, PlotSelectionShape.of(raw).bounds().width());
        assertEquals(Set.of(new Cell(0, 16, 0)), PlotSelectionShape.of(raw).alignedCells());
        UUID otherWorld = UUID.randomUUID();
        PlotSelection brush = new PlotSelection(new PlotPosition(otherWorld, 0, 64, 0),
                new PlotPosition(otherWorld, 3, 67, 3));
        assertEquals(Message.PLOT_ERROR_SELECTION, assertThrows(PlotProblem.class,
                () -> PlotSelectionShape.of(raw).apply(brush, PlotSelectionShape.Operation.ADD)).message());
    }

    private PlotSelection box(int minimumX, int minimumZ, int maximumX, int maximumZ) {
        return new PlotSelection(new PlotPosition(world, minimumX, 64, minimumZ),
                new PlotPosition(world, maximumX, 67, maximumZ));
    }
}
