package org.encinet.mik.module.plot;

import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

final class PlotMiniatureMesher {
    static final int MAX_TILE_SIZE = 8;
    static final int MAX_ABSTRACT_TILE_SIZE = 32;
    private PlotMiniatureMesher() { }

    record Result(List<PlotMiniatureSampler.Voxel> terrain, boolean limited) { }

    static Result mesh(PlotMiniatureGeometry.Grid grid, BlockData[] blocks, int budget) {
        String[] states = new String[blocks.length];
        for (int index = 0; index < blocks.length; index++) {
            BlockData block = blocks[index];
            if (block != null && block.isOccluding()) states[index] = block.getAsString();
        }
        return mesh(grid, blocks, states, budget);
    }

    static Result mesh(PlotMiniatureGeometry.Grid grid, BlockData[] blocks, String[] states, int budget) {
        if (budget < 0) throw new IllegalArgumentException("Negative display budget");
        String[] surfaces = states.clone();
        for (int index = 0; index < blocks.length; index++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (states[index] != null && !exposed(grid, states, grid.column(index), grid.row(index), grid.depth(index)))
                surfaces[index] = null;
        }
        List<PlotMiniatureSampler.Voxel> terrain = tiles(grid, blocks, states, surfaces, MAX_TILE_SIZE);
        if (terrain.size() <= budget) return new Result(List.copyOf(terrain), false);
        if (budget == 0) return new Result(List.of(), true);
        for (int tileSize = MAX_TILE_SIZE * 2; tileSize <= MAX_ABSTRACT_TILE_SIZE; tileSize *= 2) {
            List<PlotMiniatureSampler.Voxel> coarser = tiles(grid, blocks, states, surfaces, tileSize);
            if (coarser.size() < terrain.size()) terrain = coarser;
            if (terrain.size() <= budget) return new Result(List.copyOf(terrain), false);
        }
        return new Result(PlotMiniatureAbstraction.select(grid, blocks, terrain, budget), true);
    }

    private static List<PlotMiniatureSampler.Voxel> tiles(PlotMiniatureGeometry.Grid grid, BlockData[] blocks,
                                                         String[] states, String[] surfaces, int tileSize) {
        boolean[] used = new boolean[blocks.length];
        List<PlotMiniatureSampler.Voxel> terrain = new ArrayList<>();
        for (int index = 0; index < blocks.length; index++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (blocks[index] == null || used[index]) continue;
            int column = grid.column(index);
            int row = grid.row(index);
            int depth = grid.depth(index);
            if (states[index] == null) {
                if (exposed(grid, states, column, row, depth))
                    terrain.add(new PlotMiniatureSampler.Voxel(index, grid.block(index), blocks[index]));
                continue;
            }
            if (surfaces[index] == null) continue;
            String state = states[index];
            int columns = 1;
            int depths = 1;
            int rows = 1;
            while ((long) (columns + 1) * grid.stepX() <= tileSize
                    && matches(grid, surfaces, used, state, column + columns, row, depth, 1, 1, 1)) columns++;
            while ((long) (depths + 1) * grid.stepZ() <= tileSize
                    && matches(grid, surfaces, used, state, column, row, depth + depths, columns, 1, 1)) depths++;
            while ((long) (rows + 1) * grid.stepY() <= tileSize
                    && matches(grid, surfaces, used, state, column, row + rows, depth, columns, 1, depths)) rows++;
            for (int vertical = row; vertical < row + rows; vertical++) {
                for (int forward = depth; forward < depth + depths; forward++) {
                    for (int horizontal = column; horizontal < column + columns; horizontal++) {
                        used[grid.index(horizontal, vertical, forward)] = true;
                    }
                }
            }
            terrain.add(new PlotMiniatureSampler.Voxel(index,
                    grid.box(column, row, depth, columns, rows, depths), blocks[index]));
        }
        return terrain;
    }

    private static boolean matches(PlotMiniatureGeometry.Grid grid, String[] states, boolean[] used,
                                   String state, int column, int row, int depth, int columns, int rows, int depths) {
        if (column + columns > grid.columns() || row + rows > grid.rows() || depth + depths > grid.depths())
            return false;
        for (int vertical = row; vertical < row + rows; vertical++) {
            for (int forward = depth; forward < depth + depths; forward++) {
                for (int horizontal = column; horizontal < column + columns; horizontal++) {
                    int index = grid.index(horizontal, vertical, forward);
                    if (used[index] || !state.equals(states[index])) return false;
                }
            }
        }
        return true;
    }

    private static boolean exposed(PlotMiniatureGeometry.Grid grid, String[] states, int column, int row, int depth) {
        return empty(grid, states, column - 1, row, depth) || empty(grid, states, column + 1, row, depth)
                || empty(grid, states, column, row - 1, depth) || empty(grid, states, column, row + 1, depth)
                || empty(grid, states, column, row, depth - 1) || empty(grid, states, column, row, depth + 1);
    }

    private static boolean empty(PlotMiniatureGeometry.Grid grid, String[] states, int column, int row, int depth) {
        return column < 0 || column >= grid.columns() || row < 0 || row >= grid.rows()
                || depth < 0 || depth >= grid.depths() || states[grid.index(column, row, depth)] == null;
    }
}
