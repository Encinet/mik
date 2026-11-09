package org.encinet.mik.feature.world.plot;

import java.util.Collections;
import java.util.List;

final class PlotMapTestSupport {
    private PlotMapTestSupport() { }
    static PlotNativeMapRaster nativeRaster() { return new PlotNativeMapRaster(new PlotNativeMapColors(nativeColors())); }

    /** Snapshot generated from MapColor.getColorFromPackedId in the configured Paper 26.3 server. */
    static int[] nativeColors() {
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                java.util.Objects.requireNonNull(PlotMapTestSupport.class.getResourceAsStream("/plot/native-map-colors.txt")),
                java.nio.charset.StandardCharsets.UTF_8))) {
            return reader.lines().mapToInt(line -> Integer.parseUnsignedInt(line, 16)).toArray();
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }

    static PlotEditorMap.Scene scene(int mode) {
        var region = new PlotSelectionRegions.Region(new PlotSelection.Bounds(0, 64, 0, 7, 67, 7));
        return new PlotEditorMap.Scene(new PlotEditorMap.Stamp(1, 1, true, 0, 0),
                PlotMapViewport.near(0, 0), 64, true,
                new PlotEditorMap.Regions(List.of(region), region, 1, mode != 0, mode == 2, 0),
                new PlotSelectionState.Points(null, null),
                new PlotMapTerrain.Snapshot(1, Collections.nCopies(PlotMapViewport.COUNT, 0x85bb65), PlotMapViewport.COUNT, 0),
                new PlotEditorMap.Pixels(new boolean[PlotMapViewport.COUNT], new boolean[PlotMapViewport.COUNT]), false);
    }
}
