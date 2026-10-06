package org.encinet.mik.module.plot;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotMiniatureGeometryTest {
    @Test
    void evenWorldSizedRangesHaveABoundedSamplingGrid() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(
                Integer.MIN_VALUE, -64, Integer.MIN_VALUE, Integer.MAX_VALUE, 319, Integer.MAX_VALUE));
        assertTrue(grid.count() <= PlotMiniatureGeometry.MAX_SAMPLES);
        assertTrue(grid.columns() <= PlotMiniatureGeometry.GRID_SIDE);
        assertTrue(grid.rows() <= PlotMiniatureGeometry.HEIGHT_LAYERS);
        assertTrue(grid.depths() <= PlotMiniatureGeometry.GRID_SIDE);
        assertEquals(grid.stepX(), grid.stepZ());
        for (int index = 0; index < grid.count(); index++) {
            assertEquals(index, grid.index(grid.column(index), grid.row(index), grid.depth(index)));
            assertTrue(grid.box(index).width() > 0);
        }
    }

    @Test
    void negativeCoordinatesAndPartialLastSamplesStayInsideTheBounds() {
        var bounds = new PlotSelection.Bounds(-19, -9, -7, 17, 6, 11);
        var grid = new PlotMiniatureGeometry.Grid(bounds);
        for (int index = 0; index < grid.count(); index++) {
            var box = grid.box(index);
            assertTrue(box.worldX() - box.width() / 2 >= bounds.minimumX());
            assertTrue(box.worldX() + box.width() / 2 <= bounds.maximumX() + 1);
            assertTrue(box.worldY() - box.height() / 2 >= bounds.minimumY());
            assertTrue(box.worldZ() + box.depth() / 2 <= bounds.maximumZ() + 1);
        }
    }

    @Test
    void rotationIsAnIsometryAndAllEightCornersFitTheReservedModelSpace() {
        var bounds = new PlotSelection.Bounds(-8, 64, -8, 7, 111, 7);
        for (double rotation : new double[] {0, 45, 90, 180, 315}) {
            var projection = new PlotMiniatureGeometry.Projection(bounds, rotation);
            var first = projection.point(-8, 64, -8);
            var second = projection.point(-7, 64, -8);
            double distance = Math.sqrt(Math.pow(first.right() - second.right(), 2)
                    + Math.pow(first.up() - second.up(), 2) + Math.pow(first.forward() - second.forward(), 2));
            assertEquals(projection.scale(), distance, 1.0E-9);
            for (double horizontal : new double[] {-8, 8})
                for (double vertical : new double[] {64, 112})
                    for (double depth : new double[] {-8, 8}) {
                        var point = projection.point(horizontal, vertical, depth);
                        assertTrue(Math.abs(point.right()) <= 1.6);
                        assertTrue(point.up() >= PlotMiniatureGeometry.MODEL_BOTTOM - 1.0E-9);
                        assertTrue(point.up() <= PlotMiniatureGeometry.MODEL_BOTTOM + 1.8 + 1.0E-9);
                        assertTrue(Math.abs(point.forward() - 0.65) <= 1.55);
                    }
        }
    }

    @Test
    void clippedEdgesPreserveExactCoordinatesInsteadOfInventingClosingFaces() {
        var edge = new PlotPreviewGeometry.Edge(new PlotPreviewGeometry.Point(-8, 3, 2),
                new PlotPreviewGeometry.Point(8, 3, 2));
        var bounds = new PlotSelection.Bounds(-2, 0, 0, 3, 3, 3);
        var box = PlotMiniatureGeometry.clipped(edge, bounds);
        assertEquals(1, box.worldX());
        assertEquals(6, box.width());
        assertEquals(0, box.height());
        var outside = new PlotPreviewGeometry.Edge(new PlotPreviewGeometry.Point(-8, 8, 2),
                new PlotPreviewGeometry.Point(8, 8, 2));
        assertNull(PlotMiniatureGeometry.clipped(outside, bounds));
    }

    @Test
    void largeCoordinateCenterDoesNotOverflow() {
        var projection = new PlotMiniatureGeometry.Projection(new PlotSelection.Bounds(
                Integer.MAX_VALUE - 10, 64, 0, Integer.MAX_VALUE - 1, 73, 9), 0);
        assertEquals(0, projection.point(Integer.MAX_VALUE - 5.0, 69, 5).right(), 1.0E-9);
    }

    @Test
    void nearbyViewKeepsEveryHeightLayerAndUsesSquareHorizontalSamples() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(-12, 58, -12, 12, 73, 12));
        assertEquals(1, grid.stepY());
        assertEquals(16, grid.rows());
        assertEquals(1, grid.stepX());
        assertTrue(grid.exact());
        assertEquals(grid.stepX(), grid.stepZ());
        assertTrue(grid.count() <= PlotMiniatureGeometry.MAX_SAMPLES);
    }

    @Test
    void longNarrowPlotsSpendResolutionOnTheirLongAxisInsteadOfSixteenSamplesPerAxis() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 0, 0, 255, 3, 3));
        assertEquals(128, grid.columns());
        assertEquals(4, grid.rows());
        assertEquals(4, grid.depths());
        assertEquals(2, grid.stepX());
        assertEquals(1, grid.stepZ());
    }

    @Test
    void projectedAxesMatchTheNativeBlockDisplayRotationInsteadOfMirroringZ() {
        var bounds = new PlotSelection.Bounds(-8, 0, -8, 7, 15, 7);
        for (double tilt : new double[] {PlotMiniatureGeometry.TILT, 90}) {
            for (int rotation = 0; rotation < 360; rotation += 45) {
                var projection = new PlotMiniatureGeometry.Projection(bounds, rotation, tilt);
                var center = projection.point(0, 8, 0);
                Vector[] directions = {
                        new Location(null, 0, 0, 0, rotation - 90, 0).getDirection(),
                        new Location(null, 0, 0, 0, rotation, (float) tilt - 90).getDirection(),
                        new Location(null, 0, 0, 0, rotation, (float) tilt).getDirection()};
                for (int axis = 0; axis < directions.length; axis++) {
                    var next = projection.point(axis == 0 ? 1 : 0, axis == 1 ? 9 : 8, axis == 2 ? 1 : 0);
                    double scale = axis == 1 ? projection.heightScale() : projection.scale();
                    assertEquals(directions[axis].getX() * scale, next.right() - center.right(), 1.0E-9);
                    assertEquals(directions[axis].getY() * scale, next.up() - center.up(), 1.0E-9);
                    assertEquals(directions[axis].getZ() * scale, next.forward() - center.forward(), 1.0E-9);
                }
            }
        }
    }

    @Test
    void longThinWindowsKeepFullShortAxisResolutionWhenTheBudgetAllowsIt() {
        for (boolean alongX : new boolean[] {true, false}) {
            var bounds = new PlotSelection.Bounds(0, 0, 0, alongX ? 4095 : 15, 7, alongX ? 15 : 4095);
            var grid = new PlotMiniatureGeometry.Grid(bounds);
            assertEquals(alongX ? 32 : 1, grid.stepX());
            assertEquals(alongX ? 1 : 32, grid.stepZ());
            assertEquals(1, grid.stepY());
            assertEquals(16384, grid.count());
            assertTrue(grid.count() <= PlotMiniatureGeometry.MAX_SAMPLES);
        }
    }

    @Test
    void heightClippingPreservesHorizontalBoundsAndDoesNotInventTerrainOutsideTheWorld() {
        var bounds = new PlotSelection.Bounds(-5, -66, -7, 9, 321, 11);
        var grid = PlotMiniatureGeometry.Grid.withinHeight(bounds, -64, 320);
        assertEquals(new PlotSelection.Bounds(-5, -64, -7, 9, 319, 11), grid.bounds());
        assertEquals(1, grid.stepY());
        assertEquals(384, grid.rows());
        var outside = new PlotSelection.Bounds(-5, 340, -7, 9, 355, 11);
        assertEquals(outside, PlotMiniatureGeometry.Grid.withinHeight(outside, -64, 320).bounds());
    }

    @Test
    void independentAxisStepsRemainBoundedForDifferentAspectRatiosAndWorldHeights() {
        for (int width : new int[] {1, 4, 25, 128, 256, 4096, 1_000_000}) {
            for (int height : new int[] {1, 16, 384, 1024}) {
                for (int depth : new int[] {1, 4, 128, 1024}) {
                    var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(
                            -width, -height, -depth, -1, -1, -1));
                    assertTrue(grid.count() > 0 && grid.count() <= PlotMiniatureGeometry.MAX_SAMPLES);
                    assertTrue(grid.columns() <= PlotMiniatureGeometry.GRID_SIDE);
                    assertTrue(grid.depths() <= PlotMiniatureGeometry.GRID_SIDE);
                    assertTrue(grid.rows() <= PlotMiniatureGeometry.HEIGHT_LAYERS);
                    assertEquals(height <= 384 ? 1 : 3, grid.stepY());
                }
            }
        }
    }

    @Test
    void topViewIsNorthUpAndPreservesTheFootprintWithoutHeightDistortion() {
        var projection = new PlotMiniatureGeometry.Projection(new PlotSelection.Bounds(-32, -64, -8, 31, 319, 7), 0, 90);
        var center = projection.point(0, 0, 0);
        assertTrue(projection.point(0, 0, -1).up() > center.up());
        assertTrue(projection.point(1, 0, 0).right() > center.right());
        assertEquals(center.up(), projection.point(0, 300, 0).up(), 1.0E-9);
        assertEquals(center.right(), projection.point(0, 300, 0).right(), 1.0E-9);
        assertEquals(0.65, projection.point(0, 128, 0).forward(), 1.0E-9);
        assertTrue(projection.heightScale() < projection.scale());
        assertTrue(projection.scale() * 64 > 2.5);
        for (double height : new double[] {-64, 320}) {
            assertTrue(Math.abs(projection.point(0, height, 0).forward() - 0.65) <= 0.5 + 1.0E-9);
        }
    }

    @Test
    void fullWorldHeightRetainsEveryLayerAndNearbyArchitectureRetainsEveryBlock() {
        var tall = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(-8, -64, -8, 7, 319, 7));
        assertEquals(1, tall.stepY());
        assertEquals(384, tall.rows());
        assertTrue(tall.count() <= PlotMiniatureGeometry.MAX_SAMPLES);
        var nearby = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 64, 0, 31, 95, 31));
        assertTrue(nearby.exact());
        assertEquals(32768, nearby.count());
    }

    @Test
    void flatDeepAndTallModelsFitAfterTiltingAndRestAtTheSameBottomThroughRotation() {
        for (var bounds : new PlotSelection.Bounds[] {
                new PlotSelection.Bounds(-100, -4, -100, 99, -4, 99),
                new PlotSelection.Bounds(-1, -64, -1, 0, 319, 0),
                new PlotSelection.Bounds(0, 0, 0, 3, 3, 255),
                new PlotSelection.Bounds(0, 0, 0, 255, 3, 3)}) {
            double initialScale = new PlotMiniatureGeometry.Projection(bounds, 0).scale();
            for (int rotation = 0; rotation < 360; rotation += 15) {
                var projection = new PlotMiniatureGeometry.Projection(bounds, rotation);
                assertEquals(initialScale, projection.scale(), 1.0E-12);
                double bottom = Double.POSITIVE_INFINITY;
                for (double horizontal : new double[] {bounds.minimumX(), bounds.maximumX() + 1.0})
                    for (double vertical : new double[] {bounds.minimumY(), bounds.maximumY() + 1.0})
                        for (double depth : new double[] {bounds.minimumZ(), bounds.maximumZ() + 1.0}) {
                            var point = projection.point(horizontal, vertical, depth);
                            bottom = Math.min(bottom, point.up());
                            assertTrue(Math.abs(point.right()) <= 1.375 + 1.0E-9);
                            assertTrue(Math.abs(point.forward() - 0.65) <= 1.375 + 1.0E-9);
                            assertTrue(point.up() <= PlotMiniatureGeometry.MODEL_BOTTOM + 1.8 + 1.0E-9);
                        }
                assertEquals(PlotMiniatureGeometry.MODEL_BOTTOM, bottom, 1.0E-9);
            }
        }
    }

    @Test
    void specialBlockGeometryIsOneActualBlockEvenWhenTheSamplingCellIsCoarse() {
        var bounds = new PlotSelection.Bounds(-256, -16, -256, -1, -1, -1);
        var grid = new PlotMiniatureGeometry.Grid(bounds);
        var box = grid.block(0);
        var cell = grid.box(0);
        assertTrue(cell.width() > 1);
        assertEquals(1, box.width());
        assertEquals(1, box.height());
        assertEquals(1, box.depth());
        assertEquals(Math.floor(cell.worldX()) + 0.5, box.worldX());
        assertEquals(Math.floor(cell.worldY()) + 0.5, box.worldY());
        assertEquals(Math.floor(cell.worldZ()) + 0.5, box.worldZ());
    }
}
