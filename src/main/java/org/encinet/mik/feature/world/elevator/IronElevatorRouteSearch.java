package org.encinet.mik.feature.world.elevator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Keeps geometry candidates independently of the travelling group's current landing checks. */
final class IronElevatorRouteSearch {
    private final World world;
    private Predicate<IronElevatorShaft.Level> canLand;
    private final List<IronElevatorPlatform.Column> supports;
    private final Direction up;
    private final Direction down;
    private final IronElevatorShaft cached;
    private boolean nextUp = true;

    IronElevatorRouteSearch(Block source, List<IronElevatorPlatform.Column> supports,
                            Predicate<IronElevatorShaft.Level> canLand, Integer targetY, IronElevatorShaft cached) {
        world = source.getWorld();
        this.canLand = canLand;
        this.cached = cached;
        this.supports = List.copyOf(supports);
        up = new Direction(1, targetY == null ? source.getY() + 1 : targetY, targetY != null,
                targetY != null && targetY == source.getY());
        down = new Direction(-1, source.getY() - 1, false, targetY != null);
    }

    IronElevatorShaft.Level up() { return up.result; }
    IronElevatorShaft.Level down() { return down.result; }
    boolean confirmed(int direction) { return ready(direction) && !(direction > 0 ? up : down).unloaded; }
    boolean ready(int direction) { return direction > 0 ? up.done : down.done; }

    void reevaluate(Predicate<IronElevatorShaft.Level> canLand) {
        this.canLand = canLand;
        up.reevaluate();
        down.reevaluate();
    }

    boolean resultsSafe() {
        return (up.result == null || canLand.test(up.result)) && (down.result == null || canLand.test(down.result));
    }

    boolean anyColumn(Predicate<IronElevatorPlatform.Column> predicate) {
        return supports.stream().anyMatch(predicate) || up.anyColumn(predicate) || down.anyColumn(predicate)
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
        final boolean disabled;
        final List<IronElevatorShaft.Level> candidates = new ArrayList<>();
        final Map<IronElevatorPlatform.Column, IronElevatorPlatform> blocks = new HashMap<>();
        IronElevatorPlatform.Search resolving;
        int height;
        int support;
        int candidate;
        boolean exhausted;
        boolean unloaded;
        boolean done;
        IronElevatorShaft.Level result;

        Direction(int direction, int height, boolean specific, boolean disabled) {
            this.direction = direction;
            this.height = height;
            this.specific = specific;
            this.disabled = disabled;
            exhausted = done = disabled;
        }

        void reevaluate() {
            if (unloaded) { unloaded = false; exhausted = false; }
            candidate = 0;
            result = null;
            done = disabled;
        }

        boolean anyColumn(Predicate<IronElevatorPlatform.Column> predicate) {
            return blocks.keySet().stream().anyMatch(predicate)
                    || resolving != null && resolving.columns().stream().anyMatch(predicate)
                    || candidates.stream().flatMap(platform -> platform.blocks().stream())
                            .map(IronElevatorPlatform.Column::of).anyMatch(predicate);
        }

        void advance() {
            blocks.clear();
            support = 0;
            resolving = null;
            if (specific) exhausted = true;
            else height += direction;
        }

        void step() {
            if (candidate < candidates.size()) {
                IronElevatorShaft.Level target = candidates.get(candidate++);
                if (canLand.test(target)) {
                    result = target;
                    done = true;
                }
                return;
            }
            if (exhausted || height < world.getMinHeight() || height >= world.getMaxHeight() - 1) {
                exhausted = done = true;
                return;
            }
            if (cached != null) {
                var levels = cached.levels();
                int index = cached.lowerBound(height);
                if (direction < 0 && (index == levels.size() || levels.get(index).height() != height)) index--;
                var target = specific ? cached.at(height)
                        : index < 0 || index >= levels.size() ? null : levels.get(index);
                if (target == null) exhausted = done = true;
                else {
                    candidates.add(target);
                    height = target.height();
                    advance();
                }
                return;
            }
            if (resolving != null) {
                if (!resolving.step()) return;
                var platform = resolving.result();
                boolean chunkUnloaded = resolving.unloaded();
                resolving = null;
                if (chunkUnloaded) {
                    unloaded = exhausted = done = true;
                    return;
                }
                if (platform == null) {
                    advance();
                    return;
                }
                for (Block block : platform.blocks()) blocks.put(IronElevatorPlatform.Column.of(block), platform);
                if (blocks.size() > IronElevatorShaft.MAX_COLUMNS) {
                    advance();
                    return;
                }
                support++;
                return;
            }
            if (support == supports.size()) {
                candidates.add(new IronElevatorShaft.Level(List.copyOf(blocks.values())));
                advance();
                return;
            }
            var column = supports.get(support);
            if (blocks.containsKey(column)) {
                support++;
                return;
            }
            if (!world.isChunkLoaded(column.blockX() >> 4, column.blockZ() >> 4)) {
                unloaded = true;
                exhausted = done = true;
                return;
            }
            Block block = world.getBlockAt(column.blockX(), height, column.blockZ());
            if (block.getType() != Material.IRON_BLOCK) advance();
            else resolving = new IronElevatorPlatform.Search(block);
        }
    }
}
