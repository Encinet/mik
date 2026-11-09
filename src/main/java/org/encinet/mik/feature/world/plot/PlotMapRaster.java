package org.encinet.mik.feature.world.plot;

import java.util.List;
import java.util.concurrent.CancellationException;

/** Rasterizes real cuboids at one protection layer; holes and stacked floors remain separate. */
final class PlotMapRaster {
    private PlotMapRaster() { }

    static boolean[] layer(PlotMapViewport viewport, int y, List<PlotSelectionRegions.Region> regions) {
        boolean[] pixels = new boolean[PlotMapViewport.COUNT];
        for (var region : regions) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            var bounds = region.bounds();
            if (y < bounds.minimumY() || y > bounds.maximumY()) continue;
            int left = coordinate(bounds.minimumX(), viewport.minimumX(), viewport.step());
            int right = coordinate(bounds.maximumX(), viewport.minimumX(), viewport.step());
            int top = coordinate(bounds.minimumZ(), viewport.minimumZ(), viewport.step());
            int bottom = coordinate(bounds.maximumZ(), viewport.minimumZ(), viewport.step());
            for (int row = Math.max(0, top); row <= Math.min(PlotMapViewport.SIDE - 1, bottom); row++)
                for (int column = Math.max(0, left); column <= Math.min(PlotMapViewport.SIDE - 1, right); column++)
                    pixels[row * PlotMapViewport.SIDE + column] = true;
        }
        return pixels;
    }

    private static int coordinate(int value, int origin, int step) {
        return (int) Math.floorDiv((long) value - origin, step);
    }
}
