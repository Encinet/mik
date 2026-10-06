package org.encinet.mik.module.plot;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

record PlotSelection(PlotPosition first, PlotPosition second) {
    PlotSelection {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.world().equals(second.world()))
            throw new IllegalArgumentException("Selection points must share a world");
    }

    Bounds bounds() {
        return new Bounds(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()),
                Math.min(first.z(), second.z()), Math.max(first.x(), second.x()),
                Math.max(first.y(), second.y()), Math.max(first.z(), second.z()));
    }

    static PlotSelection enclosing(UUID world, Set<PlotGeometry.Cell> cells) {
        if (cells.isEmpty()) throw new IllegalArgumentException("Selection requires construction cells");
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (PlotGeometry.Cell cell : cells) {
            minimumX = Math.min(minimumX, cell.x());
            minimumY = Math.min(minimumY, cell.y());
            minimumZ = Math.min(minimumZ, cell.z());
            maximumX = Math.max(maximumX, cell.x());
            maximumY = Math.max(maximumY, cell.y());
            maximumZ = Math.max(maximumZ, cell.z());
        }
        int size = PlotGeometry.CELL;
        return new PlotSelection(new PlotPosition(world, minimumX * size, minimumY * size, minimumZ * size),
                new PlotPosition(world, maximumX * size + size - 1,
                        maximumY * size + size - 1, maximumZ * size + size - 1));
    }

    record Bounds(int minimumX, int minimumY, int minimumZ,
                  int maximumX, int maximumY, int maximumZ) {
        long width() { return (long) maximumX - minimumX + 1; }
        long height() { return (long) maximumY - minimumY + 1; }
        long depth() { return (long) maximumZ - minimumZ + 1; }
    }
}
