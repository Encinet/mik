package org.encinet.mik.feature.world.plot;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

/** Computes a real horizontal cross-section off-thread, including courtyard and floor boundaries. */
final class PlotBoundarySlices {
    private final PlotPreviewCache worker;
    private final Map<Key, FutureTask<PlotEdgeIndex>> entries = new LinkedHashMap<>(16, .75F, true);
    PlotBoundarySlices(PlotPreviewCache worker) { this.worker = worker; }

    PlotEdgeIndex request(Set<PlotGeometry.Cell> cells, int y) {
        if (cells.isEmpty()) return null;
        Key key = new Key(cells, Math.floorDiv(y, PlotGeometry.CELL));
        FutureTask<PlotEdgeIndex> task = entries.get(key);
        if (task == null) {
            if (entries.size() == 16) {
                var iterator = entries.entrySet().iterator();
                worker.cancel(iterator.next().getValue());
                iterator.remove();
            }
            task = new FutureTask<>(() -> index(cells, key.layer()));
            entries.put(key, task);
            try { worker.execute(task); }
            catch (RejectedExecutionException rejected) { entries.remove(key); worker.cancel(task); return null; }
        }
        if (!task.isDone()) return null;
        try { return task.get(); }
        catch (Exception failure) {
            entries.remove(key);
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }

    void close() { entries.values().forEach(worker::cancel); entries.clear(); }

    static PlotEdgeIndex index(Set<PlotGeometry.Cell> cells, int layer) {
        Set<PlotGeometry.Cell> slice = new HashSet<>();
        for (var cell : cells) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (cell.y() == layer) slice.add(cell);
        }
        double bottom = (double) layer * PlotGeometry.CELL;
        return new PlotEdgeIndex(PlotPreviewGeometry.core(slice).stream()
                .filter(edge -> edge.first().vertical() == bottom && edge.second().vertical() == bottom).toList());
    }

    private record Key(Set<PlotGeometry.Cell> cells, int layer) {
        @Override public int hashCode() { return System.identityHashCode(cells) * 31 + layer; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && key.cells == cells && key.layer == layer;
        }
    }
}
