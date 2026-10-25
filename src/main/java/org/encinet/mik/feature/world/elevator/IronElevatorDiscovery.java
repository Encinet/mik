package org.encinet.mik.feature.world.elevator;

import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Shared, cancellable discovery; only finished snapshots are published to the floor cache. */
final class IronElevatorDiscovery {
    record Result(IronElevatorShaft shaft, List<IronElevatorFloors.Floor> floors) { }

    static final class Subscription {
        private final Consumer<Result> ready;
        private boolean cancelled;
        private boolean completed;

        Subscription(Consumer<Result> ready) { this.ready = ready; }
        void cancel() { cancelled = true; }
        boolean active() { return !cancelled && !completed; }
    }

    private final class Pending {
        final UUID world;
        final IronElevatorShaft.Scan scan;
        IronElevatorShaft shaft;
        IronElevatorFloors.Catalog catalog;
        final Set<IronElevatorFloors.Column> keys = new HashSet<>();
        final List<Subscription> subscribers = new ArrayList<>();
        IronElevatorWorkQueue.Task task;
        int indexedColumns;

        Pending(Block source, IronElevatorPlatform platform, IronElevatorShaft cached) {
            world = source.getWorld().getUID();
            shaft = cached;
            scan = cached == null ? new IronElevatorShaft.Scan(source) : null;
        }

        boolean step() {
            subscribers.removeIf(subscriber -> subscriber.cancelled);
            if (subscribers.isEmpty()) {
                remove(this);
                return true;
            }
            if (shaft != null) {
                if (catalog == null) catalog = floors.catalog(shaft);
                if (!catalog.step()) return false;
                remove(this);
                Result result = new Result(shaft, catalog.result());
                for (Subscription subscriber : List.copyOf(subscribers)) {
                    if (subscriber.cancelled) continue;
                    subscriber.completed = true;
                    subscriber.ready.accept(result);
                }
                return true;
            }
            boolean complete = scan.step();
            if (indexedColumns != scan.columns().size()) {
                indexedColumns = scan.columns().size();
                for (var column : scan.columns()) {
                    var key = new IronElevatorFloors.Column(world, column.blockX(), column.blockZ());
                    Pending other = pending.get(key);
                    if (other != null && other != this) {
                        // A newly discovered overlap joins two in-flight requests.
                        other.subscribers.addAll(subscribers);
                        remove(this);
                        return true;
                    }
                    pending.put(key, this);
                    keys.add(key);
                }
            }
            if (!complete) return false;
            shaft = scan.result();
            floors.publish(shaft);
            return false;
        }
    }

    private final IronElevatorFloors floors;
    private final IronElevatorWorkQueue queue;
    private final Map<IronElevatorFloors.Column, Pending> pending = new HashMap<>();

    IronElevatorDiscovery(IronElevatorFloors floors, IronElevatorWorkQueue queue) {
        this.floors = floors;
        this.queue = queue;
    }

    Subscription prepare(Block source, IronElevatorPlatform platform, Consumer<Result> ready) {
        Subscription subscription = new Subscription(ready);
        IronElevatorShaft cached = floors.cached(source);
        Pending work = pending.get(IronElevatorFloors.Column.of(source));
        if (work == null) {
            for (Block block : platform.blocks()) {
                work = pending.get(IronElevatorFloors.Column.of(block));
                if (work != null) break;
            }
        }
        if (work == null) {
            work = new Pending(source, platform, cached);
            Pending scheduled = work;
            work.task = queue.submit(IronElevatorWorkQueue.CATALOG, () -> {
                try {
                    return scheduled.step();
                } catch (RuntimeException error) {
                    remove(scheduled);
                    scheduled.subscribers.forEach(Subscription::cancel);
                    throw error;
                }
            });
            if (work.task == null) {
                subscription.cancel();
                return subscription;
            }
            for (var column : cached == null ? platform.blocks().stream().map(IronElevatorPlatform.Column::of).toList()
                    : cached.columns()) {
                var key = new IronElevatorFloors.Column(source.getWorld().getUID(), column.blockX(), column.blockZ());
                pending.put(key, work);
                work.keys.add(key);
            }
        }
        work.subscribers.add(subscription);
        return subscription;
    }

    private void remove(Pending work) {
        for (var key : work.keys) pending.remove(key, work);
    }

    void invalidate(UUID world) {
        invalidate(world, work -> true);
    }

    void invalidate(Block changed) {
        invalidate(changed.getWorld().getUID(), work -> work.shaft == null
                ? work.scan.touches(changed.getX(), changed.getZ())
                : work.shaft.columns().stream().anyMatch(column -> Math.abs((long) column.blockX() - changed.getX()) <= 1
                && Math.abs((long) column.blockZ() - changed.getZ()) <= 1));
    }

    void invalidateChunk(UUID world, int x, int z) {
        invalidate(world, work -> work.shaft == null ? work.scan.touchesChunk(x, z)
                : work.shaft.columns().stream().anyMatch(column -> IronElevatorShaft.nearChunk(column, x, z)));
    }

    private void invalidate(UUID world, Predicate<Pending> affected) {
        for (Pending work : new HashSet<>(pending.values())) {
            if (!work.world.equals(world) || !affected.test(work)) continue;
            work.task.cancel();
            work.subscribers.forEach(Subscription::cancel);
            remove(work);
        }
    }

    void clear() {
        for (Pending work : new HashSet<>(pending.values())) {
            work.task.cancel();
            work.subscribers.forEach(Subscription::cancel);
        }
        pending.clear();
    }
}
