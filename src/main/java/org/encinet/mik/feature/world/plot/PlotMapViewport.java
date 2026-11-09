package org.encinet.mik.feature.world.plot;

/** North-up 128×128 Minecraft map geometry. Long intermediates keep fitting and panning safe at negative/world limits. */
record PlotMapViewport(int minimumX, int minimumZ, int step) {
    static final int SIDE = 128;
    static final int COUNT = SIDE * SIDE;
    static final int MAX_STEP = 1 << 25;

    PlotMapViewport {
        if (step < 1 || step > MAX_STEP || Integer.bitCount(step) != 1
                || minimumX % PlotGeometry.CELL != 0 || minimumZ % PlotGeometry.CELL != 0
                || (long) minimumX + (long) step * SIDE > (long) Integer.MAX_VALUE + 1
                || (long) minimumZ + (long) step * SIDE > (long) Integer.MAX_VALUE + 1)
            throw new IllegalArgumentException("Invalid plot map viewport");
    }

    static PlotMapViewport near(int x, int z) { return centered(x, z, 1); }

    static PlotMapViewport fit(PlotSelection.Bounds bounds) {
        long width = (long) bounds.maximumX() - bounds.minimumX() + 1;
        long depth = (long) bounds.maximumZ() - bounds.minimumZ() + 1;
        int step = 1;
        // Leave one tile of padding unless the full integer range is needed.
        while (step < MAX_STEP && Math.max(width, depth) > (long) step * (SIDE - 8)) step *= 2;
        return centered(((long) bounds.minimumX() + bounds.maximumX() + 1) / 2,
                ((long) bounds.minimumZ() + bounds.maximumZ() + 1) / 2, step);
    }

    private static PlotMapViewport centered(long x, long z, int step) {
        return new PlotMapViewport(origin(x - (long) step * SIDE / 2, step),
                origin(z - (long) step * SIDE / 2, step), step);
    }

    private static int origin(long value, int step) {
        long aligned = Math.floorDiv(value, PlotGeometry.CELL) * PlotGeometry.CELL;
        return (int) Math.clamp(aligned, Integer.MIN_VALUE, (long) Integer.MAX_VALUE + 1 - (long) step * SIDE);
    }

    PlotMapViewport zoom(int direction) {
        int next = direction > 0 ? Math.max(1, step / 2) : (int) Math.min(MAX_STEP, (long) step * 2);
        return centered((long) minimumX + (long) step * SIDE / 2,
                (long) minimumZ + (long) step * SIDE / 2, next);
    }

    PlotMapViewport pan(int east, int south) {
        return new PlotMapViewport(origin(minimumX + (long) east * step * SIDE / 2, step),
                origin(minimumZ + (long) south * step * SIDE / 2, step), step);
    }

    int x(int column) { return (int) (minimumX + (long) column * step); }
    int z(int row) { return (int) (minimumZ + (long) row * step); }
    int index(int x, int z) {
        long column = Math.floorDiv((long) x - minimumX, step), row = Math.floorDiv((long) z - minimumZ, step);
        return column < 0 || row < 0 || column >= SIDE || row >= SIDE ? -1 : (int) (row * SIDE + column);
    }

    
}
