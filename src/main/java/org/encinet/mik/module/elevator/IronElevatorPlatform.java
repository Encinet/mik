package org.encinet.mik.module.elevator;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

record IronElevatorPlatform(List<Block> blocks) {

    static final int MAX_BLOCKS = 64;
    static final Comparator<Block> ORDER = Comparator.comparingInt(Block::getX).thenComparingInt(Block::getZ);

    record Column(int blockX, int blockZ) {
        static Column of(Block block) {
            return new Column(block.getX(), block.getZ());
        }
    }

    IronElevatorPlatform {
        if (blocks.isEmpty()) throw new IllegalArgumentException("A platform must contain iron blocks");
        blocks = blocks.stream().sorted(ORDER).toList();
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
        return blocks.stream().anyMatch(member -> member.getX() == blockX && member.getZ() == blockZ);
    }

    double centerX() {
        return blocks.stream().mapToDouble(block -> block.getX() + 0.5).average().orElseThrow();
    }

    double centerZ() {
        return blocks.stream().mapToDouble(block -> block.getZ() + 0.5).average().orElseThrow();
    }

    static IronElevatorPlatform resolve(Block source) {
        if (!source.getWorld().isChunkLoaded(source.getX() >> 4, source.getZ() >> 4)) return null;
        if (source.getType() != Material.IRON_BLOCK) return null;
        Map<Column, Block> found = new HashMap<>();
        ArrayDeque<Block> pending = new ArrayDeque<>();
        found.put(Column.of(source), source);
        pending.add(source);
        while (!pending.isEmpty()) {
            Block current = pending.removeFirst();
            for (Column neighbor : List.of(new Column(current.getX() - 1, current.getZ()),
                    new Column(current.getX() + 1, current.getZ()), new Column(current.getX(), current.getZ() - 1),
                    new Column(current.getX(), current.getZ() + 1))) {
                if (found.containsKey(neighbor)) continue;
                if (!source.getWorld().isChunkLoaded(neighbor.blockX() >> 4, neighbor.blockZ() >> 4)) return null;
                Block block = source.getWorld().getBlockAt(neighbor.blockX(), source.getY(), neighbor.blockZ());
                if (block.getType() != Material.IRON_BLOCK) continue;
                if (found.size() >= MAX_BLOCKS) return null;
                found.put(neighbor, block);
                pending.addLast(block);
            }
        }
        return new IronElevatorPlatform(List.copyOf(found.values()));
    }
}
