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
    private static final IronElevatorShaft EMPTY = new IronElevatorShaft(List.of(), Set.of());

    private final List<Level> levels;
    private final Set<IronElevatorPlatform.Column> columns;
    private final List<IronElevatorPlatform.Column> orderedColumns;
    private final long[] chunks;
    private final Block anchor;

    /** A floor may contain disconnected physical platforms joined elsewhere in the shaft. */
    record Level(List<IronElevatorPlatform> platforms) {
        Level {
            platforms = platforms.stream().distinct()
                    .sorted(Comparator.comparing(IronElevatorPlatform::anchor, IronElevatorPlatform.ORDER)).toList();
            if (platforms.isEmpty()) throw new IllegalArgumentException("A level needs a physical platform");
            var first = platforms.getFirst();
            if (platforms.stream().anyMatch(platform -> platform.height() != first.height()
                    || !platform.anchor().getWorld().equals(first.anchor().getWorld())))
                throw new IllegalArgumentException("Level platforms must share a world and height");
        }
        static Level of(IronElevatorPlatform platform) { return new Level(List.of(platform)); }
        int height() { return platforms.getFirst().height(); }
        Block anchor() { return platforms.getFirst().anchor(); }
        List<Block> blocks() { return platforms.stream().flatMap(platform -> platform.blocks().stream()).toList(); }
        boolean contains(Block block) { return platformAt(block) != null; }
        IronElevatorPlatform platformAt(Block block) {
            return platforms.stream().filter(platform -> platform.contains(block)).findFirst().orElse(null);
        }
    }

    IronElevatorShaft(List<Level> levels, Set<IronElevatorPlatform.Column> columns) {
        this.levels = levels.stream().sorted(Comparator.comparingInt(Level::height)).toList();
        this.columns = Set.copyOf(columns);
        orderedColumns = columns.stream().sorted(Comparator.comparingInt(IronElevatorPlatform.Column::blockX)
                .thenComparingInt(IronElevatorPlatform.Column::blockZ)).toList();
        chunks = columns.stream().mapToLong(column -> ((long) (column.blockX() >> 4) << 32)
                | ((column.blockZ() >> 4) & 0xffffffffL)).distinct().toArray();
        anchor = this.levels.stream().map(Level::anchor).min(IronElevatorPlatform.ORDER).orElse(null);
    }

    List<Level> levels() { return levels; }
    Set<IronElevatorPlatform.Column> columns() { return columns; }
    List<IronElevatorPlatform.Column> orderedColumns() { return orderedColumns; }
    Block anchor() { return anchor; }
    IronElevatorPlatform source(Block standing) {
        Level level = at(standing.getY());
        return level == null ? null : level.platformAt(standing);
    }

    /** Resumable topology discovery. A step performs at most one world block read. */
    static final class Scan {
        private final World world;
        private final Map<Integer, Map<IronElevatorPlatform.Column, IronElevatorPlatform>> byHeight = new HashMap<>();
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
        private void fail() { result = EMPTY; }

        private void add(IronElevatorPlatform platform) {
            Map<IronElevatorPlatform.Column, IronElevatorPlatform> members = byHeight.computeIfAbsent(
                    platform.height(), _ -> new HashMap<>());
            for (Block block : platform.blocks()) {
                var expanded = IronElevatorPlatform.Column.of(block);
                if (members.putIfAbsent(expanded, platform) == null && ++ironBlocks > MAX_IRON_BLOCKS) {
                    fail();
                    return;
                }
                if (columns.add(expanded)) {
                    if (columns.size() > MAX_COLUMNS) {
                        fail();
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
                boolean unloaded = resolving.unloaded();
                resolving = null;
                if (platform != null) add(platform);
                else if (initial || unloaded) fail();
                initial = false;
                return result != null;
            }
            if (column == null || height >= maximumHeight) {
                if (pending.isEmpty()) {
                    List<Level> levels = byHeight.values().stream()
                            .map(members -> new Level(List.copyOf(members.values()))).toList();
                    result = new IronElevatorShaft(levels, columns);
                    if (!result.loaded(world)) fail();
                    return true;
                }
                column = pending.removeFirst();
                height = minimumHeight;
            }
            if (!world.isChunkLoaded(column.blockX() >> 4, column.blockZ() >> 4)) {
                fail();
                return true;
            }
            int currentHeight = height++;
            Map<IronElevatorPlatform.Column, IronElevatorPlatform> members = byHeight.get(currentHeight);
            if (members != null && members.containsKey(column)) return false;
            Block block = world.getBlockAt(column.blockX(), currentHeight, column.blockZ());
            if (block.getType() == Material.IRON_BLOCK) resolving = new IronElevatorPlatform.Search(block);
            return false;
        }
    }

    Level at(int height) {
        int index = lowerBound(height);
        return index < levels.size() && levels.get(index).height() == height ? levels.get(index) : null;
    }

    int lowerBound(int height) {
        int low = 0;
        int high = levels.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (levels.get(middle).height() < height) low = middle + 1;
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
