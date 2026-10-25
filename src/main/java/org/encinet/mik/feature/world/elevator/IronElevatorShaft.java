package org.encinet.mik.feature.world.elevator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class IronElevatorShaft {

    static final int MAX_COLUMNS = 128;
    private static final int MAX_IRON_BLOCKS = 4096;
    static final IronElevatorShaft EMPTY = new IronElevatorShaft(List.of(), Set.of());

    private final List<IronElevatorPlatform> platforms;
    private final Set<IronElevatorPlatform.Column> columns;
    private final List<IronElevatorPlatform.Column> orderedColumns;
    private final long[] chunks;
    private final Block anchor;

    IronElevatorShaft(List<IronElevatorPlatform> platforms, Set<IronElevatorPlatform.Column> columns) {
        this.platforms = platforms.stream().sorted(Comparator.comparingInt(IronElevatorPlatform::height)).toList();
        this.columns = Set.copyOf(columns);
        orderedColumns = columns.stream().sorted(Comparator.comparingInt(IronElevatorPlatform.Column::blockX)
                .thenComparingInt(IronElevatorPlatform.Column::blockZ)).toList();
        chunks = columns.stream().mapToLong(column -> ((long) (column.blockX() >> 4) << 32)
                | ((column.blockZ() >> 4) & 0xffffffffL)).distinct().toArray();
        anchor = this.platforms.stream().map(IronElevatorPlatform::anchor)
                .min(IronElevatorPlatform.ORDER).orElse(null);
    }

    List<IronElevatorPlatform> platforms() { return platforms; }
    Set<IronElevatorPlatform.Column> columns() { return columns; }
    List<IronElevatorPlatform.Column> orderedColumns() { return orderedColumns; }
    Block anchor() { return anchor; }

    static IronElevatorShaft scan(Block source) {
        Scan scan = new Scan(source);
        while (!scan.step()) { }
        return scan.result();
    }

    /** Resumable topology discovery. A step performs at most one world block read. */
    static final class Scan {
        private final World world;
        private final Map<Integer, Map<IronElevatorPlatform.Column, Block>> byHeight = new HashMap<>();
        private final Set<IronElevatorPlatform.Column> columns = new HashSet<>();
        private final ArrayDeque<IronElevatorPlatform.Column> pending = new ArrayDeque<>();
        private final int minimumHeight;
        private final int maximumHeight;
        private IronElevatorPlatform.Search resolving;
        private boolean initial;
        private IronElevatorPlatform.Column column;
        private int height;
        private int ironBlocks;
        private IronElevatorShaft result;

        Scan(Block source) {
            world = source.getWorld();
            minimumHeight = world.getMinHeight();
            maximumHeight = world.getMaxHeight() - 1;
            initial = true;
            resolving = new IronElevatorPlatform.Search(source);
        }

        Set<IronElevatorPlatform.Column> columns() { return columns; }
        boolean touches(int x, int z) {
            return columns.stream().anyMatch(column -> Math.abs((long) column.blockX() - x) <= 1
                    && Math.abs((long) column.blockZ() - z) <= 1)
                    || resolving != null && resolving.columns().stream().anyMatch(column ->
                    Math.abs((long) column.blockX() - x) <= 1 && Math.abs((long) column.blockZ() - z) <= 1);
        }

        boolean touchesChunk(int x, int z) {
            return columns.stream().anyMatch(column -> nearChunk(column, x, z))
                    || resolving != null && resolving.columns().stream().anyMatch(column -> nearChunk(column, x, z));
        }
        IronElevatorShaft result() { return result; }

        private void add(IronElevatorPlatform platform) {
            Map<IronElevatorPlatform.Column, Block> members = byHeight.computeIfAbsent(
                    platform.height(), ignored -> new HashMap<>());
            for (Block block : platform.blocks()) {
                var expanded = IronElevatorPlatform.Column.of(block);
                if (members.putIfAbsent(expanded, block) == null && ++ironBlocks > MAX_IRON_BLOCKS) {
                    result = EMPTY;
                    return;
                }
                if (columns.add(expanded)) {
                    if (columns.size() > MAX_COLUMNS) {
                        result = EMPTY;
                        return;
                    }
                    pending.addLast(expanded);
                }
            }
        }

        boolean step() {
            if (result != null) return true;
            if (resolving != null) {
                if (!resolving.step()) return false;
                IronElevatorPlatform platform = resolving.result();
                resolving = null;
                if (platform != null) add(platform);
                else if (initial) result = EMPTY;
                initial = false;
                return result != null;
            }
            if (column == null || height >= maximumHeight) {
                if (pending.isEmpty()) {
                    List<IronElevatorPlatform> platforms = byHeight.values().stream()
                            .map(members -> new IronElevatorPlatform(List.copyOf(members.values()))).toList();
                    result = new IronElevatorShaft(platforms, columns);
                    if (!result.loaded(world)) result = EMPTY;
                    return true;
                }
                column = pending.removeFirst();
                height = minimumHeight;
            }
            if (!world.isChunkLoaded(column.blockX() >> 4, column.blockZ() >> 4)) {
                result = EMPTY;
                return true;
            }
            int currentHeight = height++;
            Map<IronElevatorPlatform.Column, Block> members = byHeight.get(currentHeight);
            if (members != null && members.containsKey(column)) return false;
            Block block = world.getBlockAt(column.blockX(), currentHeight, column.blockZ());
            if (block.getType() == Material.IRON_BLOCK) resolving = new IronElevatorPlatform.Search(block);
            return false;
        }
    }

    IronElevatorPlatform at(int height) {
        int index = lowerBound(height);
        return index < platforms.size() && platforms.get(index).height() == height ? platforms.get(index) : null;
    }

    int lowerBound(int height) {
        int low = 0;
        int high = platforms.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (platforms.get(middle).height() < height) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    boolean loaded(World world) {
        for (long chunk : chunks)
            if (!world.isChunkLoaded((int) (chunk >> 32), (int) chunk)) return false;
        return true;
    }

    static boolean nearChunk(IronElevatorPlatform.Column column, int x, int z) {
        return x >= (column.blockX() - 1 >> 4) && x <= (column.blockX() + 1 >> 4)
                && z >= (column.blockZ() - 1 >> 4) && z <= (column.blockZ() + 1 >> 4);
    }
}
