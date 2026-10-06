package org.encinet.mik.module.plot;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Exact four-block cells preserve composite plot boundaries and courtyard holes. */
public final class PlotGeometry {
    public static final int CELL = 4;

    private PlotGeometry() { }

    public record Cell(int x, int y, int z) {
        @Override
        public int hashCode() {
            int hash = Integer.rotateLeft(x * 0x9e3779b9, 7)
                    ^ Integer.rotateLeft(y * 0x85ebca6b, 17) ^ z * 0xc2b2ae35;
            hash ^= hash >>> 16;
            hash *= 0x85ebca6b;
            hash ^= hash >>> 13;
            hash *= 0xc2b2ae35;
            return hash ^ (hash >>> 16);
        }

        public static Cell at(int x, int y, int z) {
            return new Cell(Math.floorDiv(x, CELL), Math.floorDiv(y, CELL), Math.floorDiv(z, CELL));
        }

        public boolean protects(int blockX, int blockY, int blockZ) {
            return equals(at(blockX, blockY, blockZ));
        }

    }

    public static int horizontalArea(Set<Cell> cells) {
        Set<Cell> columns = new HashSet<>();
        for (Cell cell : cells) columns.add(new Cell(cell.x(), 0, cell.z()));
        return columns.size() * CELL * CELL;
    }

    static boolean connected(Set<Cell> cells) {
        if (cells.isEmpty()) return false;
        Set<Cell> reached = new HashSet<>();
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        Cell first = cells.iterator().next();
        reached.add(first);
        pending.add(first);
        while (!pending.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Cell current = pending.removeFirst();
            for (Cell neighbor : new Cell[] {
                    new Cell(current.x() - 1, current.y(), current.z()),
                    new Cell(current.x() + 1, current.y(), current.z()),
                    new Cell(current.x(), current.y() - 1, current.z()),
                    new Cell(current.x(), current.y() + 1, current.z()),
                    new Cell(current.x(), current.y(), current.z() - 1),
                    new Cell(current.x(), current.y(), current.z() + 1)}) {
                if (cells.contains(neighbor) && reached.add(neighbor)) pending.addLast(neighbor);
            }
        }
        return reached.size() == cells.size();
    }

}
