package org.encinet.mik.feature.world.elevator;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class IronElevatorPlatform {

    static final int MAX_BLOCKS = 64;
    static final Comparator<Block> ORDER = Comparator.comparingInt(Block::getX).thenComparingInt(Block::getZ);

    record Column(int blockX, int blockZ) {
        static Column of(Block block) {
            return new Column(block.getX(), block.getZ());
        }
    }

    private final List<Block> blocks;
    private final long[] columns;
    private final double centerX;
    private final double centerZ;
    private final int hash;

    IronElevatorPlatform(List<Block> blocks) {
        if (blocks.isEmpty()) throw new IllegalArgumentException("A platform must contain iron blocks");
        this.blocks = blocks.stream().sorted(ORDER).toList();
        columns = new long[blocks.size()];
        double sumX = 0;
        double sumZ = 0;
        for (int index = 0; index < blocks.size(); index++) {
            Block block = blocks.get(index);
            columns[index] = column(block.getX(), block.getZ());
            sumX += block.getX() + 0.5;
            sumZ += block.getZ() + 0.5;
        }
        Arrays.sort(columns);
        centerX = sumX / blocks.size();
        centerZ = sumZ / blocks.size();
        hash = 31 * (31 * anchor().getWorld().hashCode() + height()) + Arrays.hashCode(columns);
    }

    List<Block> blocks() {
        return blocks;
    }

    Block anchor() {
        return blocks.getFirst();
    }

    int height() {
        return anchor().getY();
    }

    boolean contains(Block block) {
        return block != null && anchor().getWorld().equals(block.getWorld()) && height() == block.getY()
                && contains(block.getX(), block.getZ());
    }

    boolean contains(int blockX, int blockZ) {
        return Arrays.binarySearch(columns, column(blockX, blockZ)) >= 0;
    }

    double centerX() {
        return centerX;
    }

    double centerZ() {
        return centerZ;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof IronElevatorPlatform platform
                && hash == platform.hash && height() == platform.height()
                && anchor().getWorld().equals(platform.anchor().getWorld())
                && Arrays.equals(columns, platform.columns);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    static IronElevatorPlatform resolve(Block source) {
        Search search = new Search(source);
        while (!search.step()) { }
        return search.result();
    }

    /** Each step visits at most one neighbour, including on large platforms. */
    static final class Search {
        private static final int[] DX = {-1, 1, 0, 0};
        private static final int[] DZ = {0, 0, -1, 1};
        private final Block source;
        private final Map<Column, Block> found = new HashMap<>();
        private final Set<Column> visited = new HashSet<>();
        private final ArrayDeque<Block> pending = new ArrayDeque<>();
        private Block current;
        private int direction;
        private boolean initialized;
        private boolean done;
        private IronElevatorPlatform result;

        Search(Block source) { this.source = source; }

        IronElevatorPlatform result() { return result; }
        Set<Column> columns() { return visited; }

        boolean step() {
            if (done) return true;
            if (!initialized) {
                initialized = true;
                if (!source.getWorld().isChunkLoaded(source.getX() >> 4, source.getZ() >> 4)
                        || source.getType() != Material.IRON_BLOCK) return done = true;
                found.put(Column.of(source), source);
                visited.add(Column.of(source));
                pending.add(source);
                return false;
            }
            if (current == null) {
                if (pending.isEmpty()) {
                    result = new IronElevatorPlatform(List.copyOf(found.values()));
                    return done = true;
                }
                current = pending.removeFirst();
                direction = 0;
            }
            Column neighbor = new Column(current.getX() + DX[direction], current.getZ() + DZ[direction]);
            if (++direction == 4) current = null;
            if (!visited.add(neighbor)) return false;
            if (!source.getWorld().isChunkLoaded(neighbor.blockX() >> 4, neighbor.blockZ() >> 4)) return done = true;
            Block block = source.getWorld().getBlockAt(neighbor.blockX(), source.getY(), neighbor.blockZ());
            if (block.getType() == Material.IRON_BLOCK) {
                if (found.size() >= MAX_BLOCKS) return done = true;
                found.put(neighbor, block);
                pending.addLast(block);
            }
            return false;
        }
    }
}
