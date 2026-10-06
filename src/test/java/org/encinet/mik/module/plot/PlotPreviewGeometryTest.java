package org.encinet.mik.module.plot;

import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.encinet.mik.module.plot.PlotPreviewGeometry.Edge;
import org.encinet.mik.module.plot.PlotPreviewGeometry.Point;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotPreviewGeometryTest {

    @Test
    void enormousCuboidPreviewUsesOnlyItsTwelveAlignedEdges() {
        UUID world = UUID.randomUUID();
        PlotSelectionShape selection = PlotSelectionShape.of(new PlotPosition(world, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                new PlotPosition(world, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        List<Edge> edges = PlotPreviewGeometry.selection(selection);
        assertEquals(12, edges.size());
        assertTrue(edges.stream().allMatch(edge -> edge.length() == 4_294_967_296.0));
        assertEquals(PlotPreviewGeometry.box(selection.alignedBounds()), edges);
    }

    @Test
    void compositePreviewStillPreservesCourtyardHoles() {
        Set<Cell> cells = new HashSet<>();
        for (int horizontalX = 0; horizontalX < 3; horizontalX++)
            for (int horizontalZ = 0; horizontalZ < 3; horizontalZ++)
                if (horizontalX != 1 || horizontalZ != 1)
                    cells.add(new Cell(horizontalX, 16, horizontalZ));
        PlotSelectionShape shape = new PlotSelectionShape(UUID.randomUUID(), null, cells);
        assertEquals(PlotPreviewGeometry.core(cells), PlotPreviewGeometry.selection(shape));
        assertTrue(PlotPreviewGeometry.selection(shape).size() > 12);
    }

    @Test
    void reversedCornersUseInclusiveBlockBoundsWithoutHalfBlockOffset() {
        UUID world = UUID.randomUUID();
        PlotSelection selection = new PlotSelection(new PlotPosition(world, 3, 67, 3),
                new PlotPosition(world, 0, 64, 0));
        assertEquals(new PlotSelection.Bounds(0, 64, 0, 3, 67, 3), selection.bounds());
        List<Edge> edges = PlotPreviewGeometry.box(selection.bounds());
        assertEquals(12, edges.size());
        assertEquals(4, selection.bounds().width());
        assertEquals(4, selection.bounds().height());
        assertEquals(4, selection.bounds().depth());
        assertTrue(edges.stream().allMatch(edge -> edge.length() == 4));
        assertTrue(edges.stream().flatMap(edge -> java.util.stream.Stream.of(edge.first(), edge.second()))
                .allMatch(point -> (point.horizontal() == 0 || point.horizontal() == 4)
                        && (point.vertical() == 64 || point.vertical() == 68)
                        && (point.forward() == 0 || point.forward() == 4)));
    }

    @Test
    void oneBlockSelectionHasVolumeAndNegativeCellBoundsRemainExact() {
        UUID world = UUID.randomUUID();
        PlotPosition block = new PlotPosition(world, -1, -1, -1);
        assertTrue(PlotPreviewGeometry.box(new PlotSelection(block, block).bounds()).stream()
                .allMatch(edge -> edge.length() == 1));
        PlotSelection cell = PlotSelection.enclosing(world, Set.of(Cell.at(-1, -1, -1)));
        assertEquals(new PlotSelection.Bounds(-4, -4, -4, -1, -1, -1), cell.bounds());
    }

    @Test
    void fittingConstructionDoesNotIncludeProtectionMarginsOrReservations() {
        UUID world = UUID.randomUUID();
        PlotSelection fitted = PlotSelection.enclosing(world, Set.of(
                new Cell(-2, 16, 0), new Cell(1, 18, 2)));
        assertEquals(new PlotSelection.Bounds(-8, 64, 0, 7, 75, 11), fitted.bounds());
        assertEquals(16, fitted.bounds().width());
        assertThrows(IllegalArgumentException.class, () -> PlotSelection.enclosing(world, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new PlotSelection(
                new PlotPosition(world, 0, 0, 0), new PlotPosition(UUID.randomUUID(), 0, 0, 0)));
    }

    @Test
    void dimensionsDoNotOverflowAcrossIntegerCoordinateExtremes() {
        UUID world = UUID.randomUUID();
        PlotSelection selection = new PlotSelection(
                new PlotPosition(world, Integer.MIN_VALUE, 0, 0),
                new PlotPosition(world, Integer.MAX_VALUE, 0, 0));
        assertEquals(4_294_967_296L, selection.bounds().width());
        assertEquals(4_294_967_296.0, PlotPreviewGeometry.box(selection.bounds()).getFirst().length());
    }

    @Test
    void neighboringCellsHaveOneMergedOutlineInsteadOfInternalGridLines() {
        Set<Cell> cells = Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0), new Cell(1, 17, 0),
                new Cell(0, 17, 0));
        List<Edge> edges = PlotPreviewGeometry.core(cells);
        assertEquals(12, edges.size());
        assertEquals(Set.copyOf(PlotPreviewGeometry.box(
                new PlotSelection.Bounds(0, 64, 0, 7, 71, 3))), Set.copyOf(edges));
    }

    @Test
    void concaveConstructionKeepsItsIndentationInsteadOfShowingAnEnclosingRectangle() {
        List<Edge> edges = PlotPreviewGeometry.core(Set.of(new Cell(0, 16, 0),
                new Cell(1, 16, 0), new Cell(0, 16, 1)));
        assertEquals(18, edges.size());
        assertTrue(edges.contains(new Edge(new Point(4, 64, 4), new Point(4, 68, 4))));
        assertFalse(edges.contains(new Edge(new Point(8, 64, 8), new Point(8, 68, 8))));
    }

    @Test
    void solidAndHollowShapesDoNotLoseVerticalOrInnerBoundaryEdges() {
        Set<Cell> solid = new HashSet<>();
        for (int horizontal = 0; horizontal < 2; horizontal++) {
            for (int vertical = 16; vertical < 18; vertical++) {
                for (int forward = 0; forward < 2; forward++) solid.add(new Cell(horizontal, vertical, forward));
            }
        }
        assertEquals(Set.copyOf(PlotPreviewGeometry.box(new PlotSelection.Bounds(0, 64, 0, 7, 71, 7))),
                Set.copyOf(PlotPreviewGeometry.core(solid)));
        Set<Cell> hollow = new HashSet<>();
        for (int horizontal = 0; horizontal < 3; horizontal++) {
            for (int forward = 0; forward < 3; forward++) {
                if (horizontal != 1 || forward != 1) hollow.add(new Cell(horizontal, 16, forward));
            }
        }
        assertEquals(24, PlotPreviewGeometry.core(hollow).size());
        assertTrue(PlotPreviewGeometry.core(hollow).contains(new Edge(new Point(4, 64, 4), new Point(4, 68, 4))));
        assertTrue(PlotPreviewGeometry.core(Set.of()).isEmpty());
    }

    @Test
    void denseBoxSamplesEveryEdgeAndPreservesCornersWithinTheBudget() {
        List<Edge> edges = PlotPreviewGeometry.box(new PlotSelection.Bounds(0, 0, 0, 7, 7, 7));
        List<Point> points = PlotPreviewGeometry.sample(edges, new Point(4, 4, 4), 24, 160);
        assertTrue(points.size() > 100);
        assertTrue(points.size() <= 160);
        assertEquals(points.size(), new HashSet<>(points).size());
        for (Edge edge : edges) {
            assertTrue(points.contains(edge.first()));
            assertTrue(points.contains(edge.second()));
            assertTrue(points.stream().anyMatch(point -> !point.equals(edge.first())
                    && !point.equals(edge.second()) && onEdge(point, edge)), edge.toString());
        }
    }

    @Test
    void skippingInteriorCellsStillPreservesAllFacesOfAnEnclosedThreeDimensionalHole() {
        Set<Cell> cells = new HashSet<>();
        for (int horizontal = -2; horizontal <= 2; horizontal++)
            for (int vertical = -2; vertical <= 2; vertical++)
                for (int forward = -2; forward <= 2; forward++)
                    if (horizontal != 0 || vertical != 0 || forward != 0)
                        cells.add(new Cell(horizontal, vertical, forward));
        var edges = PlotPreviewGeometry.core(Set.copyOf(cells));
        Set<Edge> expected = new HashSet<>(PlotPreviewGeometry.box(new PlotSelection.Bounds(-8, -8, -8, 11, 11, 11)));
        expected.addAll(PlotPreviewGeometry.box(new PlotSelection.Bounds(0, 0, 0, 3, 3, 3)));
        assertEquals(expected, new HashSet<>(edges));
        assertEquals(24, edges.size());
    }

    @Test
    void longEdgesAreClippedNearTheViewerEvenWhenTheirCornersAreFarAway() {
        Edge edge = new Edge(new Point(-1_000_000, 10, 0), new Point(1_000_000, 10, 0));
        Point viewer = new Point(0, 0, 0);
        List<Point> points = PlotPreviewGeometry.sample(List.of(edge), viewer, 24, 80);
        assertTrue(points.size() > 20);
        assertTrue(points.stream().allMatch(point -> viewer.distanceSquared(point) <= 24 * 24 + 1.0E-6));
        assertTrue(points.stream().anyMatch(point -> Math.abs(point.horizontal()) < 1));
        assertTrue(PlotPreviewGeometry.sample(List.of(new Edge(new Point(-1_000_000, 25, 0),
                new Point(1_000_000, 25, 0))), viewer, 24, 80).isEmpty());
    }

    @Test
    void manyEdgesRemainBoundedAndZeroBudgetProducesNoParticles() {
        Set<Cell> cells = new HashSet<>();
        for (int horizontal = -8; horizontal < 8; horizontal++) {
            for (int forward = -8; forward < 8; forward++) {
                if ((horizontal + forward) % 2 == 0) cells.add(new Cell(horizontal, 0, forward));
            }
        }
        List<Edge> edges = PlotPreviewGeometry.core(cells);
        Point viewer = new Point(0, 2, 0);
        for (int budget : List.of(0, 1, 3, 16, 64, 256)) {
            List<Point> points = PlotPreviewGeometry.sample(edges, viewer, 24, budget);
            assertTrue(points.size() <= budget);
            assertTrue(points.stream().allMatch(point -> viewer.distanceSquared(point) <= 24 * 24 + 1.0E-6));
        }
        assertThrows(IllegalArgumentException.class, () -> PlotPreviewGeometry.sample(edges, viewer, 24, -1));
    }

    @Test
    void connectivityUsesFaceAdjacencyAndHandlesLargeComponents() {
        Set<Cell> cells = new HashSet<>();
        for (int position = 0; position < 2048; position++) cells.add(new Cell(position, 0, 0));
        assertTrue(PlotGeometry.connected(cells));
        cells.remove(new Cell(1000, 0, 0));
        assertFalse(PlotGeometry.connected(cells));
        assertFalse(PlotGeometry.connected(Set.of(new Cell(0, 0, 0), new Cell(1, 1, 0))));
        assertFalse(PlotGeometry.connected(Set.of()));
    }

    private static boolean onEdge(Point point, Edge edge) {
        return Math.abs(edge.first().distanceSquared(point) + edge.second().distanceSquared(point)
                + 2 * Math.sqrt(edge.first().distanceSquared(point) * edge.second().distanceSquared(point))
                - edge.length() * edge.length()) < 1.0E-6;
    }
}
