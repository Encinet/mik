package org.encinet.mik.module.plot;

import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;

final class PlotMiniatureAbstraction {
    private static final Comparator<Candidate> IMPORTANCE = Comparator.comparingDouble(Candidate::area)
            .thenComparingInt(Candidate::exposure)
            .thenComparingDouble(candidate -> candidate.voxel().box().worldY() + candidate.voxel().box().height() / 2)
            .thenComparing(Comparator.comparingInt((Candidate candidate) -> candidate.voxel().index()).reversed());
    private static final Comparator<Region> SPLIT_ORDER = Comparator.comparingDouble(Region::extent).reversed()
            .thenComparing(Comparator.comparingDouble(Region::spread).reversed())
            .thenComparingInt(Region::firstIndex);

    private PlotMiniatureAbstraction() { }

    private record Candidate(PlotMiniatureSampler.Voxel voxel, double area, int exposure) {
        double coordinate(int axis) {
            return switch (axis) {
                case 0 -> voxel.box().worldX();
                case 1 -> voxel.box().worldY();
                default -> voxel.box().worldZ();
            };
        }
    }

    private record Region(int start, int end, int axis, double midpoint, double extent, double spread,
                          int firstIndex, Candidate representative) { }

    static List<PlotMiniatureSampler.Voxel> select(PlotMiniatureGeometry.Grid grid, BlockData[] blocks,
                                                  List<PlotMiniatureSampler.Voxel> terrain, int budget) {
        Candidate[] candidates = new Candidate[terrain.size()];
        for (int index = 0; index < candidates.length; index++) {
            checkCancelled();
            var voxel = terrain.get(index);
            var box = voxel.box();
            double area = box.width() * box.height() + box.width() * box.depth() + box.height() * box.depth();
            candidates[index] = new Candidate(voxel, area, exposure(grid, blocks, voxel.index()));
        }
        PriorityQueue<Region> frontier = new PriorityQueue<>(SPLIT_ORDER);
        List<PlotMiniatureSampler.Voxel> retained = new ArrayList<>(budget);
        add(region(candidates, 0, candidates.length), frontier, retained);
        int regions = 1;
        while (regions < budget && !frontier.isEmpty()) {
            checkCancelled();
            Region parent = frontier.remove();
            int pivot = partition(candidates, parent);
            add(region(candidates, parent.start(), pivot), frontier, retained);
            add(region(candidates, pivot, parent.end()), frontier, retained);
            regions++;
        }
        for (Region leaf : frontier) retained.add(leaf.representative().voxel());
        retained.sort(Comparator.comparingInt(PlotMiniatureSampler.Voxel::index));
        return List.copyOf(retained);
    }

    private static void add(Region region, PriorityQueue<Region> frontier,
                            List<PlotMiniatureSampler.Voxel> retained) {
        if (region.end() - region.start() == 1) retained.add(region.representative().voxel());
        else frontier.add(region);
    }

    private static Region region(Candidate[] candidates, int start, int end) {
        double[] minimum = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] maximum = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        Candidate representative = candidates[start];
        int firstIndex = representative.voxel().index();
        for (int index = start; index < end; index++) {
            checkCancelled();
            Candidate candidate = candidates[index];
            if (IMPORTANCE.compare(candidate, representative) > 0) representative = candidate;
            firstIndex = Math.min(firstIndex, candidate.voxel().index());
            for (int axis = 0; axis < 3; axis++) {
                double coordinate = candidate.coordinate(axis);
                minimum[axis] = Math.min(minimum[axis], coordinate);
                maximum[axis] = Math.max(maximum[axis], coordinate);
            }
        }
        int longest = 0;
        double spread = 0;
        for (int axis = 0; axis < 3; axis++) {
            spread += maximum[axis] - minimum[axis];
            if (maximum[axis] - minimum[axis] > maximum[longest] - minimum[longest]) longest = axis;
        }
        double extent = maximum[longest] - minimum[longest];
        return new Region(start, end, longest, minimum[longest] + extent / 2, extent, spread,
                firstIndex, representative);
    }

    private static int partition(Candidate[] candidates, Region region) {
        int cursor = region.start();
        int last = region.end() - 1;
        while (cursor <= last) {
            checkCancelled();
            if (candidates[cursor].coordinate(region.axis()) < region.midpoint()) cursor++;
            else {
                Candidate swapped = candidates[cursor];
                candidates[cursor] = candidates[last];
                candidates[last--] = swapped;
            }
        }
        return cursor == region.start() || cursor == region.end() ? (region.start() + region.end()) / 2 : cursor;
    }

    private static int exposure(PlotMiniatureGeometry.Grid grid, BlockData[] blocks, int index) {
        int column = grid.column(index);
        int row = grid.row(index);
        int depth = grid.depth(index);
        return empty(grid, blocks, column - 1, row, depth) + empty(grid, blocks, column + 1, row, depth)
                + empty(grid, blocks, column, row - 1, depth) + empty(grid, blocks, column, row + 1, depth)
                + empty(grid, blocks, column, row, depth - 1) + empty(grid, blocks, column, row, depth + 1);
    }

    private static int empty(PlotMiniatureGeometry.Grid grid, BlockData[] blocks, int column, int row, int depth) {
        return column < 0 || column >= grid.columns() || row < 0 || row >= grid.rows()
                || depth < 0 || depth >= grid.depths() || blocks[grid.index(column, row, depth)] == null ? 1 : 0;
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
}
