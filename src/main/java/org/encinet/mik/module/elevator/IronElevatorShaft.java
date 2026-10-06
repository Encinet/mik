package org.encinet.mik.module.elevator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

record IronElevatorShaft(List<IronElevatorPlatform> platforms, Set<IronElevatorPlatform.Column> columns) {

    private static final int MAX_COLUMNS = 128;
    private static final int MAX_IRON_BLOCKS = 4096;

    static IronElevatorShaft scan(Block source) {
        IronElevatorPlatform initial = IronElevatorPlatform.resolve(source);
        if (initial == null) return new IronElevatorShaft(List.of(), Set.of());
        World world = source.getWorld();
        Map<Integer, Map<IronElevatorPlatform.Column, Block>> byHeight = new TreeMap<>();
        Set<IronElevatorPlatform.Column> columns = new HashSet<>();
        ArrayDeque<IronElevatorPlatform.Column> pending = new ArrayDeque<>();
        for (Block block : initial.blocks()) {
            IronElevatorPlatform.Column column = IronElevatorPlatform.Column.of(block);
            columns.add(column);
            pending.add(column);
        }
        int ironBlocks = 0;
        while (!pending.isEmpty()) {
            IronElevatorPlatform.Column column = pending.removeFirst();
            for (int height = world.getMinHeight(); height < world.getMaxHeight() - 1; height++) {
                Map<IronElevatorPlatform.Column, Block> members = byHeight.get(height);
                if (members != null && members.containsKey(column)) continue;
                Block block = world.getBlockAt(column.blockX(), height, column.blockZ());
                if (block.getType() != Material.IRON_BLOCK) continue;
                IronElevatorPlatform platform = IronElevatorPlatform.resolve(block);
                if (platform == null) continue;
                if (members == null) {
                    members = new HashMap<>();
                    byHeight.put(height, members);
                }
                for (Block member : platform.blocks()) {
                    IronElevatorPlatform.Column expanded = IronElevatorPlatform.Column.of(member);
                    if (members.putIfAbsent(expanded, member) == null && ++ironBlocks > MAX_IRON_BLOCKS)
                        return new IronElevatorShaft(List.of(), Set.of());
                    if (columns.add(expanded)) {
                        if (columns.size() > MAX_COLUMNS) return new IronElevatorShaft(List.of(), Set.of());
                        pending.addLast(expanded);
                    }
                }
            }
        }
        List<IronElevatorPlatform> platforms = byHeight.values().stream().filter(members -> !members.isEmpty())
                .map(members -> new IronElevatorPlatform(List.copyOf(members.values()))).toList();
        return new IronElevatorShaft(platforms, Set.copyOf(columns));
    }

    IronElevatorPlatform at(int height) {
        return platforms.stream().filter(platform -> platform.height() == height).findFirst().orElse(null);
    }

    boolean loaded(World world) {
        return columns.stream().allMatch(column -> world.isChunkLoaded(column.blockX() >> 4, column.blockZ() >> 4));
    }
}
