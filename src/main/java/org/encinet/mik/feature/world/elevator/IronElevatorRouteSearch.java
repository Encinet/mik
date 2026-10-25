package org.encinet.mik.feature.world.elevator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Searches only the travelling roots' support columns, independently of floor numbering. */
final class IronElevatorRouteSearch {
    private final World world;
    private final Predicate<IronElevatorPlatform> canLand;
    private final List<IronElevatorPlatform.Column> supports;
    private final Direction up;
    private final Direction down;
    private final IronElevatorShaft cached;
    private boolean nextUp = true;

    IronElevatorRouteSearch(Block source, IronElevatorTravelGroup group) {
        this(source, group, null);
    }

    IronElevatorRouteSearch(Block source, IronElevatorTravelGroup group, Integer targetY) {
        this(source, group, targetY, null);
    }

    IronElevatorRouteSearch(Block source, IronElevatorTravelGroup group, Integer targetY, IronElevatorShaft cached) {
        this(source, group.supportColumns(), target -> group.landing(target) != null, targetY, cached);
    }

    IronElevatorRouteSearch(Block source, List<IronElevatorPlatform.Column> supports,
                            Predicate<IronElevatorPlatform> canLand, Integer targetY, IronElevatorShaft cached) {
        world = source.getWorld();
        this.canLand = canLand;
        this.cached = cached;
        this.supports = List.copyOf(supports);
        up = new Direction(1, targetY == null ? source.getY() + 1 : targetY, targetY != null);
        down = new Direction(-1, source.getY() - 1, false);
        if (targetY != null) {
            down.done = true;
            if (targetY == source.getY()) up.done = true;
        }
    }

    IronElevatorPlatform up() { return up.result; }
    IronElevatorPlatform down() { return down.result; }

    boolean ready(int direction) { return direction > 0 ? up.done : down.done; }

    boolean resultsSafe() {
        return (up.result == null || canLand.test(up.result)) && (down.result == null || canLand.test(down.result));
    }

    boolean anyColumn(Predicate<IronElevatorPlatform.Column> predicate) {
        return supports.stream().anyMatch(predicate) || up.blocks.keySet().stream().anyMatch(predicate)
                || down.blocks.keySet().stream().anyMatch(predicate)
                || up.resolving != null && up.resolving.columns().stream().anyMatch(predicate)
                || down.resolving != null && down.resolving.columns().stream().anyMatch(predicate)
                || cached != null && cached.columns().stream().anyMatch(predicate);
    }

    boolean step() {
        Direction current = nextUp ? up : down;
        if (current.done) current = nextUp ? down : up;
        if (!current.done) current.step();
        nextUp = !nextUp;
        return up.done && down.done;
    }

    boolean step(int preferredDirection) {
        Direction preferred = preferredDirection > 0 ? up : down;
        if (!preferred.done) preferred.step();
        else return step();
        return up.done && down.done;
    }

    private final class Direction {
        final int direction;
        final boolean specific;
        final Map<IronElevatorPlatform.Column, Block> blocks = new HashMap<>();
        IronElevatorPlatform.Search resolving;
        int height;
        int support;
        boolean done;
        IronElevatorPlatform result;

        Direction(int direction, int height, boolean specific) {
            this.direction = direction;
            this.height = height;
            this.specific = specific;
        }

        void reject() {
            blocks.clear();
            support = 0;
            resolving = null;
            if (specific) done = true;
            else height += direction;
        }

        void step() {
            if (height < world.getMinHeight() || height >= world.getMaxHeight() - 1) {
                done = true;
                return;
            }
            if (cached != null) {
                var platforms = cached.platforms();
                int index = cached.lowerBound(height);
                if (direction < 0 && (index == platforms.size() || platforms.get(index).height() != height)) index--;
                var target = specific ? cached.at(height)
                        : index < 0 || index >= platforms.size() ? null : platforms.get(index);
                if (target == null) done = true;
                else if (canLand.test(target)) {
                    result = target;
                    done = true;
                } else {
                    height = target.height();
                    reject();
                }
                return;
            }
            if (resolving != null) {
                if (!resolving.step()) return;
                var platform = resolving.result();
                resolving = null;
                if (platform == null) {
                    reject();
                    return;
                }
                for (Block block : platform.blocks()) blocks.put(IronElevatorPlatform.Column.of(block), block);
                if (blocks.size() > IronElevatorShaft.MAX_COLUMNS) {
                    reject();
                    return;
                }
                support++;
                return;
            }
            if (support == supports.size()) {
                var platform = new IronElevatorPlatform(List.copyOf(blocks.values()));
                if (canLand.test(platform)) {
                    result = platform;
                    done = true;
                } else reject();
                return;
            }
            var column = supports.get(support);
            if (blocks.containsKey(column)) {
                support++;
                return;
            }
            if (!world.isChunkLoaded(column.blockX() >> 4, column.blockZ() >> 4)) {
                done = true;
                return;
            }
            Block block = world.getBlockAt(column.blockX(), height, column.blockZ());
            if (block.getType() != Material.IRON_BLOCK) reject();
            else resolving = new IronElevatorPlatform.Search(block);
        }
    }
}
