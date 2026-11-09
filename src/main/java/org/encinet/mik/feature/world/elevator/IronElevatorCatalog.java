package org.encinet.mik.feature.world.elevator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

final class IronElevatorCatalog {

    record Column(UUID worldId, int blockX, int blockZ) {
        static Column of(Block block) {
            return new Column(block.getWorld().getUID(), block.getX(), block.getZ());
        }
    }

    record Floor(IronElevatorShaft.Level level, int number) { }

    private static final int MAX_CACHED_SHAFTS = 64;
    private static final int MAX_GROUND_REFERENCES = 256;
    static final long CACHE_NANOS = 2_000_000_000L;
    private final Map<Column, Integer> overrides = new HashMap<>();
    private final Map<Column, GroundReference> groundReferences = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<Column, Cached> scans = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<Column, Column> scanColumns = new HashMap<>();
    private final Map<Column,Pending> pending = new HashMap<>();
    private final Map<IronElevatorShaft,Result> completed = new java.util.IdentityHashMap<>();
    private final LongSupplier clock;

    private record Cached(IronElevatorShaft shaft, long measuredAt, IronElevatorGround.Estimate tentativeGround) {
    }

    private record GroundReference(int height, Set<IronElevatorPlatform.Column> columns) {
        GroundReference {
            columns = Set.copyOf(columns);
        }
    }

    IronElevatorCatalog() {
        this(System::nanoTime);
    }

    IronElevatorCatalog(LongSupplier clock) {
        this.clock = clock;
    }

    void invalidate() {
        clearPending();
        completed.clear();
        scans.clear();
        scanColumns.clear();
    }

    void invalidate(Block changed) {
        invalidatePending(changed);
        UUID worldId = changed.getWorld().getUID();
        List<Column> affected = scans.entrySet().stream().filter(entry -> entry.getKey().worldId().equals(worldId)
                && entry.getValue().shaft().columns().stream().anyMatch(column ->
                    Math.abs((long) column.blockX() - changed.getX()) <= 1
                            && Math.abs((long) column.blockZ() - changed.getZ()) <= 1))
                .map(Map.Entry::getKey).toList();
        affected.forEach(this::removeScan);
    }

    void invalidateChunk(UUID worldId, int chunkX, int chunkZ) {
        invalidatePendingChunk(worldId, chunkX, chunkZ);
        List<Column> affected = scans.entrySet().stream().filter(entry -> entry.getKey().worldId().equals(worldId)
                && entry.getValue().shaft().columns().stream().anyMatch(column ->
                    IronElevatorShaft.nearChunk(column, chunkX, chunkZ)))
                .map(Map.Entry::getKey).toList();
        affected.forEach(this::removeScan);
    }

    void clear() {
        invalidate();
        groundReferences.clear();
    }

    private void removeScan(Column key) {
        Cached removed = scans.remove(key);
        if (removed == null) return;
        completed.remove(removed.shaft());
        for (var column : removed.shaft().columns())
            scanColumns.remove(new Column(key.worldId(), column.blockX(), column.blockZ()), key);
    }

    IronElevatorShaft cached(Block source) {
        World world = source.getWorld();
        if (!world.isChunkLoaded(source.getX() >> 4, source.getZ() >> 4)
                || source.getType() != Material.IRON_BLOCK) return null;
        long now = clock.getAsLong();
        Column matching = scanColumns.get(Column.of(source));
        if (matching != null) {
            Cached cached = scans.get(matching);
            if (now - cached.measuredAt() < CACHE_NANOS && cached.shaft().loaded(world)) return cached.shaft();
            removeScan(matching);
        }
        return null;
    }

    long measuredAt(Block source) {
        Column key = scanColumns.get(Column.of(source));
        Cached cached = key == null ? null : scans.get(key);
        return cached == null ? clock.getAsLong() : cached.measuredAt();
    }

    void publish(IronElevatorShaft scanned) {
        if (!scanned.levels().isEmpty()) {
            Column key = Column.of(scanned.anchor());
            // Geometry may have joined formerly separate shafts since a scan.
            for (var column : scanned.columns()) {
                Column previous = scanColumns.get(new Column(key.worldId(), column.blockX(), column.blockZ()));
                if (previous != null) removeScan(previous);
            }
            scans.put(key, new Cached(scanned, clock.getAsLong(), null));
            for (var column : scanned.columns())
                scanColumns.put(new Column(key.worldId(), column.blockX(), column.blockZ()), key);
            if (scans.size() > MAX_CACHED_SHAFTS) removeScan(scans.keySet().iterator().next());
        }
    }

    static Map<Column,Integer> readRules(Path file) {
        YamlConfiguration config = new YamlConfiguration();
        try { config.load(file.toFile()); }
        catch (IOException | InvalidConfigurationException error) {
            throw new IllegalStateException("Failed to load iron-elevators.yml", error);
        }
        Map<Column,Integer> rules = new HashMap<>();
        ConfigurationSection worlds = config.getConfigurationSection("ground-floors");
        if (worlds == null) {
            if (config.contains("ground-floors")) {
                throw new IllegalArgumentException("ground-floors must be a mapping");
            }
            return Map.of();
        }
        for (String worldId : worlds.getKeys(false)) {
            UUID world = UUID.fromString(worldId);
            ConfigurationSection columns = worlds.getConfigurationSection(worldId);
            if (columns == null) {
                throw new IllegalArgumentException("Ground-floor world must contain column coordinates");
            }
            for (String coordinates : columns.getKeys(false)) {
                String[] parts = coordinates.split(",", -1);
                Object value = columns.get(coordinates);
                if (parts.length != 2 || !(value instanceof Integer height)) {
                    throw new IllegalArgumentException("Ground-floor entry must be x,z: integer Y");
                }
                rules.put(new Column(world, Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim())), height);
            }
        }
        return Map.copyOf(rules);
    }

    void installRules(Map<Column,Integer> rules) {
        clear();
        overrides.clear();
        overrides.putAll(rules);
    }

    /** Floor usability and terrain probes yield independently of the geometry scan. */
    private final class Numbering {
        private final IronElevatorShaft shaft;
        private final List<IronElevatorShaft.Level> levels = new ArrayList<>();
        private int index;
        private IronElevatorLanding.Usability usability;
        private IronElevatorGround.Measurement measurement;
        private Column column;
        private Integer override;
        private List<Integer> heights;
        private List<Floor> result;

        Numbering(IronElevatorShaft shaft) { this.shaft = shaft; }
        List<Floor> result() { return result; }

        boolean step() {
            if (result != null) return true;
            if (index < shaft.levels().size()) {
                var level = shaft.levels().get(index);
                if (usability == null) usability = new IronElevatorLanding.Usability(level);
                if (usability.step()) {
                    if (usability.usable()) levels.add(level);
                    index++;
                    usability = null;
                }
                return false;
            }
            if (levels.size() < 2) {
                result = List.of();
                return true;
            }
            if (measurement != null) {
                if (!measurement.step()) return false;
                var estimate = measurement.result();
                if (estimate.stable()) {
                    groundReferences.put(column, new GroundReference(estimate.height(), shaft.columns()));
                    if (groundReferences.size() > MAX_GROUND_REFERENCES)
                        groundReferences.remove(groundReferences.keySet().iterator().next());
                } else {
                    Cached cached = scans.get(column);
                    if (cached != null && cached.shaft() == shaft)
                        scans.put(column, new Cached(shaft, cached.measuredAt(), estimate));
                }
                finish(estimate.height());
                return true;
            }
            column = Column.of(shaft.anchor());
            heights = levels.stream().map(IronElevatorShaft.Level::height).toList();
            override = shaft.orderedColumns().stream()
                    .map(position -> overrides.get(new Column(column.worldId(), position.blockX(), position.blockZ())))
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            Column referenceColumn = override == null ? referenceColumn(column, shaft) : null;
            GroundReference remembered = referenceColumn == null ? null : groundReferences.get(referenceColumn);
            if (remembered != null && (!referenceColumn.equals(column) || !remembered.columns().equals(shaft.columns()))) {
                groundReferences.remove(referenceColumn);
                groundReferences.put(column, new GroundReference(remembered.height(), shaft.columns()));
            }
            Integer groundHeight = override != null ? override : remembered == null ? null : remembered.height();
            if (groundHeight != null) finish(groundHeight);
            else {
                Cached cached = scans.get(column);
                if (cached != null && cached.shaft() == shaft && cached.tentativeGround() != null)
                    finish(cached.tentativeGround().height());
                else measurement = new IronElevatorGround.Measurement(shaft.anchor(), heights);
            }
            return result != null;
        }

        private void finish(int groundHeight) {
            int groundIndex = override == null ? IronElevatorGround.groundIndex(heights, groundHeight) : heights.indexOf(groundHeight);
            if (groundIndex < 0) throw new IllegalArgumentException("Configured elevator ground floor is not a usable level: " + groundHeight);
            List<Floor> numbered = new ArrayList<>();
            for (int floor = 0; floor < levels.size(); floor++)
                numbered.add(new Floor(levels.get(floor), floorNumber(floor, groundIndex)));
            result = List.copyOf(numbered);
        }
    }

    private Column referenceColumn(Column column, IronElevatorShaft shaft) {
        GroundReference direct = groundReferences.get(column);
        if (direct != null && direct.columns().stream().anyMatch(shaft.columns()::contains)) return column;
        return groundReferences.entrySet().stream().filter(entry -> entry.getKey().worldId().equals(column.worldId())
                        && entry.getValue().columns().stream().anyMatch(shaft.columns()::contains))
                .map(Map.Entry::getKey).min(Comparator.comparingInt(Column::blockX).thenComparingInt(Column::blockZ))
                .orElse(null);
    }

    static int floorNumber(int index, int groundIndex) {
        int offset = index - groundIndex;
        return offset < 0 ? offset : offset + 1;
    }

    record Result(IronElevatorShaft shaft, List<Floor> floors, long measuredAt) {
        Result { floors = List.copyOf(floors); }
    }

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
        Numbering numbering;
        final Set<Column> keys = new HashSet<>();
        final List<Subscription> subscribers = new ArrayList<>();
        IronElevatorWorkQueue.Task task;
        int indexedColumns;
        long geometryMeasuredAt;

        Pending(Block source, IronElevatorShaft cached) {
            world = source.getWorld().getUID();
            shaft = cached;
            geometryMeasuredAt = cached == null ? 0 : measuredAt(source);
            scan = cached == null ? new IronElevatorShaft.Scan(source) : null;
        }

        boolean step() {
            subscribers.removeIf(subscriber -> subscriber.cancelled);
            if (subscribers.isEmpty()) {
                remove(this);
                return true;
            }
            if (shaft != null) {
                if (numbering == null) numbering = new Numbering(shaft);
                if (!numbering.step()) return false;
                remove(this);
                Result result = new Result(shaft, numbering.result(), geometryMeasuredAt);
                if (scans.values().stream().anyMatch(cached -> cached.shaft() == shaft)) completed.put(shaft, result);
                RuntimeException failure = null;
                for (Subscription subscriber : List.copyOf(subscribers)) {
                    if (subscriber.cancelled) continue;
                    subscriber.completed = true;
                    try { subscriber.ready.accept(result); }
                    catch (RuntimeException error) {
                        if (failure == null) failure = error; else failure.addSuppressed(error);
                    }
                }
                if (failure != null) throw failure;
                return true;
            }
            boolean complete = scan.step();
            if (indexedColumns != scan.columns().size()) {
                indexedColumns = scan.columns().size();
                for (var column : scan.columns()) {
                    var key = new Column(world, column.blockX(), column.blockZ());
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
            publish(shaft);
            geometryMeasuredAt = clock.getAsLong();
            return false;
        }
    }

    Subscription prepare(Block source, IronElevatorPlatform platform, IronElevatorWorkQueue queue, Consumer<Result> ready) {
        Subscription subscription = new Subscription(ready);
        IronElevatorShaft cached = cached(source);
        Result complete = cached == null ? null : completed.get(cached);
        if (complete != null) {
            subscription.completed = true;
            ready.accept(complete);
            return subscription;
        }
        Pending work = pending.get(Column.of(source));
        if (work == null) {
            for (Block block : platform.blocks()) {
                work = pending.get(Column.of(block));
                if (work != null) break;
            }
        }
        if (work == null) {
            work = new Pending(source, cached);
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
                var key = new Column(source.getWorld().getUID(), column.blockX(), column.blockZ());
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

    void invalidateWorld(UUID world) {
        invalidate(world, _ -> true);
        scans.entrySet().stream().filter(entry -> entry.getKey().worldId().equals(world))
                .map(Map.Entry::getKey).toList().forEach(this::removeScan);
    }

    private void invalidatePending(Block changed) {
        invalidate(changed.getWorld().getUID(), work -> work.shaft == null
                ? work.scan.touches(changed.getX(), changed.getZ())
                : work.shaft.columns().stream().anyMatch(column -> Math.abs((long) column.blockX() - changed.getX()) <= 1
                && Math.abs((long) column.blockZ() - changed.getZ()) <= 1));
    }

    private void invalidatePendingChunk(UUID world, int x, int z) {
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

    private void clearPending() {
        for (Pending work : new HashSet<>(pending.values())) {
            work.task.cancel();
            work.subscribers.forEach(Subscription::cancel);
        }
        pending.clear();
    }
}
