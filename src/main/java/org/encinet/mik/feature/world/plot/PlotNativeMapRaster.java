package org.encinet.mik.feature.world.plot;

import java.util.List;
import java.util.concurrent.CancellationException;

/** Minecraft map-palette pixels: terrain stays visible beneath thin protection contours. */
final class PlotNativeMapRaster {
    private final PlotNativeMapColors palette;
    private static final int SAVED = 0x36c9dc, ADDED = 0xc16cde, REMOVED = 0xef9c42, SELECTED = 0x7fdb59;

    PlotNativeMapRaster(PlotNativeMapColors palette) { this.palette = palette; }

    byte[] render(PlotEditorMap.Scene scene) {
        byte[] image = new byte[PlotMapViewport.COUNT];
        boolean[] saved = scene.pixels() == null ? new boolean[image.length] : scene.pixels().saved();
        boolean[] candidate = scene.pixels() == null ? new boolean[image.length] : scene.pixels().candidate();
        boolean[] selected = scene.regions().selected() == null ? new boolean[image.length]
                : PlotMapRaster.layer(scene.viewport(), scene.layer(), List.of(scene.regions().selected()));
        for (int index = 0; index < image.length; index++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            int x = index % PlotMapViewport.SIDE, z = index / PlotMapViewport.SIDE;
            int base = scene.terrain().colors().get(index);
            boolean inside = saved[index] || candidate[index];
            boolean selectedBorder = border(selected, x, z);
            if (base == 0 && !inside && !selectedBorder) continue; // Native unexplored-map parchment.
            if (base == 0) base = 0xd8c9a6;
            int overlay = candidate[index] ? saved[index] ? SAVED : ADDED : REMOVED;
            boolean edge = border(saved, x, z) || border(candidate, x, z);
            if (inside) base = edge ? overlay : tint(base, overlay);
            if (selectedBorder) base = SELECTED;
            image[index] = palette.encode(base);
        }
        return image;
    }

    private static boolean border(boolean[] mask, int x, int z) {
        int i = z * PlotMapViewport.SIDE + x;
        if (!mask[i]) return false;
        return x == 0 || z == 0 || x == PlotMapViewport.SIDE - 1 || z == PlotMapViewport.SIDE - 1
                || !mask[i - 1] || !mask[i + 1] || !mask[i - PlotMapViewport.SIDE] || !mask[i + PlotMapViewport.SIDE];
    }

    private static int tint(int base, int overlay) {
        int r = (((base >> 16) & 255) * 7 + ((overlay >> 16) & 255)) / 8;
        int g = (((base >> 8) & 255) * 7 + ((overlay >> 8) & 255)) / 8;
        int b = ((base & 255) * 7 + (overlay & 255)) / 8;
        return r << 16 | g << 8 | b;
    }

}
