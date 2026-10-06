package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotGeometry.Cell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;

final class PlotSelectionRegions {
    private static final Comparator<Cell> ORDER = Comparator.comparingInt(Cell::y)
            .thenComparingInt(Cell::z).thenComparingInt(Cell::x);

    private PlotSelectionRegions() { }

    record Region(PlotSelection.Bounds bounds) {
        PlotSelection selection(UUID world) {
            return new PlotSelection(new PlotPosition(world, bounds.minimumX(), bounds.minimumY(), bounds.minimumZ()),
                    new PlotPosition(world, bounds.maximumX(), bounds.maximumY(), bounds.maximumZ()));
        }
    }

    static List<Region> decompose(PlotSelectionShape shape) {
        if (shape == null || shape.empty()) return List.of();
        if (!shape.composite()) return List.of(new Region(shape.alignedBounds()));
        Set<Cell> remaining = new HashSet<>(shape.cells());
        List<Region> regions = new ArrayList<>();
        for (Cell seed : shape.cells().stream().sorted(ORDER).toList()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (!remaining.contains(seed)) continue;
            int maximumX = seed.x();
            int maximumZ = seed.z();
            int maximumY = seed.y();
            while (maximumX < Integer.MAX_VALUE && remaining.contains(new Cell(maximumX + 1, seed.y(), seed.z())))
                maximumX++;
            while (maximumZ < Integer.MAX_VALUE
                    && row(remaining, seed.x(), maximumX, seed.y(), maximumZ + 1)) maximumZ++;
            while (maximumY < Integer.MAX_VALUE
                    && plane(remaining, seed.x(), maximumX, maximumY + 1, seed.z(), maximumZ)) maximumY++;
            for (int vertical = seed.y();; vertical++) {
                for (int depth = seed.z();; depth++) {
                    for (int horizontal = seed.x();; horizontal++) {
                        remaining.remove(new Cell(horizontal, vertical, depth));
                        if (horizontal == maximumX) break;
                    }
                    if (depth == maximumZ) break;
                }
                if (vertical == maximumY) break;
            }
            int size = PlotGeometry.CELL;
            regions.add(new Region(new PlotSelection.Bounds(seed.x() * size, seed.y() * size, seed.z() * size,
                    maximumX * size + size - 1, maximumY * size + size - 1, maximumZ * size + size - 1)));
        }
        return List.copyOf(regions);
    }

    private static boolean row(Set<Cell> cells, int minimumX, int maximumX, int vertical, int depth) {
        for (int horizontal = minimumX;; horizontal++) {
            if (!cells.contains(new Cell(horizontal, vertical, depth))) return false;
            if (horizontal == maximumX) return true;
        }
    }

    private static boolean plane(Set<Cell> cells, int minimumX, int maximumX, int vertical,
                                 int minimumZ, int maximumZ) {
        for (int depth = minimumZ;; depth++) {
            if (!row(cells, minimumX, maximumX, vertical, depth)) return false;
            if (depth == maximumZ) return true;
        }
    }

    static PlotSelectionShape remove(PlotSelectionShape shape, Region region) {
        if (!shape.composite() && shape.alignedBounds().equals(region.bounds()))
            return new PlotSelectionShape(shape.world(), null, Set.of());
        return shape.apply(region.selection(shape.world()), PlotSelectionShape.Operation.SUBTRACT);
    }

    static PlotSelectionShape replace(PlotSelectionShape shape, Region region, PlotSelection replacement) {
        if (!shape.world().equals(replacement.first().world()))
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        PlotSelectionShape remaining = remove(shape, region);
        if (remaining.empty()) return PlotSelectionShape.of(replacement);
        return remaining.apply(replacement, PlotSelectionShape.Operation.ADD);
    }
}
