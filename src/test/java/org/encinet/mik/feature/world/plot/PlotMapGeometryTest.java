package org.encinet.mik.feature.world.plot;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlotMapGeometryTest {
    @Test void nativeMapPixelsLocateSignedBlockCoordinates() {
        var viewport = PlotMapViewport.near(-1, -9);
        int index = viewport.index(-1, -9);
        assertEquals(-1, viewport.x(index % PlotMapViewport.SIDE));
        assertEquals(-9, viewport.z(index / PlotMapViewport.SIDE));
        assertEquals(1, viewport.step());
        assertEquals(-1, viewport.index(viewport.minimumX() - 1, -9));
        assertEquals(viewport, viewport.pan(1, 0).pan(-1, 0));
        assertEquals(viewport, viewport.zoom(-1).zoom(1));
    }

    @Test void fittingTheIntegerWorldRangeNeverOverflows() {
        var viewport = PlotMapViewport.fit(new PlotSelection.Bounds(Integer.MIN_VALUE, -64,
                Integer.MIN_VALUE, Integer.MAX_VALUE, 319, Integer.MAX_VALUE));
        assertEquals(PlotMapViewport.MAX_STEP, viewport.step());
        assertEquals(0, viewport.index(Integer.MIN_VALUE, Integer.MIN_VALUE));
        assertEquals(PlotMapViewport.COUNT - 1, viewport.index(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(viewport, viewport.pan(-1, 1));
        assertEquals(viewport, viewport.zoom(-1));
    }

    @Test void rasterKeepsCourtyardHolesAndSeparatesFloors() {
        var viewport = new PlotMapViewport(0, 0, 4);
        var regions = List.of(region(0, 64, 0, 11, 67, 3), region(0, 64, 8, 11, 67, 11),
                region(0, 64, 4, 3, 67, 7), region(8, 64, 4, 11, 67, 7),
                region(4, 72, 4, 7, 75, 7));
        boolean[] ground = PlotMapRaster.layer(viewport, 64, regions);
        assertTrue(ground[0]);
        assertFalse(ground[129]);
        assertTrue(ground[130]);
        boolean[] upper = PlotMapRaster.layer(viewport, 72, regions);
        assertFalse(upper[0]);
        assertTrue(upper[129]);
        assertFalse(upper[130]);
        assertEquals(0, count(PlotMapRaster.layer(viewport, 68, regions)));
    }

    @Test void coarseRasterShowsSmallRegionsWithoutFillingTheWholeBoundingBox() {
        var viewport = new PlotMapViewport(-64, -64, 16);
        boolean[] raster = PlotMapRaster.layer(viewport, 64, List.of(region(-4, 64, -4, -1, 67, -1),
                region(64, 64, 64, 67, 67, 67)));
        assertEquals(2, count(raster));
        assertTrue(raster[viewport.index(-1, -1)]);
        assertFalse(raster[viewport.index(32, 32)]);
    }

    @Test void horizontalWorldSectionRetainsHolesWithoutDrawingOtherFloors() {
        Set<PlotGeometry.Cell> ring = Set.of(new PlotGeometry.Cell(0, 16, 0), new PlotGeometry.Cell(1, 16, 0),
                new PlotGeometry.Cell(2, 16, 0), new PlotGeometry.Cell(0, 16, 1), new PlotGeometry.Cell(2, 16, 1),
                new PlotGeometry.Cell(0, 16, 2), new PlotGeometry.Cell(1, 16, 2), new PlotGeometry.Cell(2, 16, 2),
                new PlotGeometry.Cell(1, 18, 1));
        var ground = PlotBoundarySlices.index(ring, 16).nearby(new PlotPreviewGeometry.Point(6, 64, 6), 32, 64).edges();
        assertEquals(8, ground.size());
        assertTrue(ground.stream().allMatch(edge -> edge.first().vertical() == 64 && edge.second().vertical() == 64));
        assertTrue(ground.stream().anyMatch(edge -> edge.first().horizontal() == 4 && edge.second().horizontal() == 4));
        assertEquals(4, PlotBoundarySlices.index(ring, 18).nearby(new PlotPreviewGeometry.Point(6, 72, 6), 32, 64).edges().size());
    }

    private static PlotSelectionRegions.Region region(int x, int y, int z, int xx, int yy, int zz) {
        return new PlotSelectionRegions.Region(new PlotSelection.Bounds(x, y, z, xx, yy, zz));
    }
    private static int count(boolean[] values) { int count = 0; for (boolean value : values) if (value) count++; return count; }
}
