package org.encinet.mik.feature.world.elevator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

final class IronElevatorFloors {

    record Column(UUID worldId, int blockX, int blockZ) {
        static Column of(Block block) {
            return new Column(block.getWorld().getUID(), block.getX(), block.getZ());
        }
    }

    record Floor(IronElevatorPlatform platform, int number) {
        Block block() {
            return platform.anchor();
        }
    }

    private static final int MAX_CACHED_SHAFTS = 64;
    private static final int MAX_GROUND_REFERENCES = 256;
    static final long CACHE_NANOS = 2_000_000_000L;
    private final Map<Column, Integer> overrides = new HashMap<>();
    private final Map<Column, GroundReference> groundReferences = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<Column, Cached> scans = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<Column, Column> scanColumns = new HashMap<>();
    private final LongSupplier clock;

    private record Cached(IronElevatorShaft shaft, long measuredAt, IronElevatorGround.Estimate tentativeGround) {
    }

    private record GroundReference(int height, Set<IronElevatorPlatform.Column> columns) {
        GroundReference {
            columns = Set.copyOf(columns);
        }
    }

    IronElevatorFloors() {
        this(System::nanoTime);
    }

    IronElevatorFloors(LongSupplier clock) {
        this.clock = clock;
    }

    void invalidate() {
        scans.clear();
        scanColumns.clear();
    }

    void invalidate(Block changed) {
        UUID worldId = changed.getWorld().getUID();
        List<Column> affected = scans.entrySet().stream().filter(entry -> entry.getKey().worldId().equals(worldId)
                && entry.getValue().shaft().columns().stream().anyMatch(column ->
                    Math.abs((long) column.blockX() - changed.getX()) <= 1
                            && Math.abs((long) column.blockZ() - changed.getZ()) <= 1))
                .map(Map.Entry::getKey).toList();
        affected.forEach(this::removeScan);
    }

    void invalidateChunk(UUID worldId, int chunkX, int chunkZ) {
        List<Column> affected = scans.entrySet().stream().filter(entry -> entry.getKey().worldId().equals(worldId)
                && entry.getValue().shaft().columns().stream().anyMatch(column ->
                    (column.blockX() >> 4) == chunkX && (column.blockZ() >> 4) == chunkZ))
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
        for (var column : removed.shaft().columns())
            scanColumns.remove(new Column(key.worldId(), column.blockX(), column.blockZ()), key);
    }

    IronElevatorShaft cached(Block source) {
        World world = source.getWorld();
        if (!world.isChunkLoaded(source.getX() >> 4, source.getZ() >> 4)
                || source.getType() != Material.IRON_BLOCK) return IronElevatorShaft.EMPTY;
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

    private IronElevatorShaft shaft(Block source) {
        IronElevatorShaft cached = cached(source);
        if (cached != null) return cached;
        IronElevatorShaft scanned = IronElevatorShaft.scan(source);
        publish(scanned);
        return scanned;
    }

    void publish(IronElevatorShaft scanned) {
        if (!scanned.platforms().isEmpty()) {
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

    IronElevatorPlatform platform(Block source) {
        IronElevatorPlatform platform = shaft(source).at(source.getY());
        return platform != null && platform.contains(source) ? platform : null;
    }

    Location destination(Block source, int direction, Integer targetY, Location origin,
                         IronElevatorLanding.Size size, Predicate<BoundingBox> extraCollision) {
        return destination(source, direction, targetY, origin, size, extraCollision, target -> true);
    }

    Location destination(Block source, int direction, Integer targetY, Location origin,
                         IronElevatorLanding.Size size, Predicate<BoundingBox> extraCollision,
                         Predicate<IronElevatorPlatform> groupFits) {
        return destination(source, direction, targetY, target -> groupFits.test(target)
                ? IronElevatorLanding.on(target, origin, size, extraCollision) : null);
    }

    Location destination(Block source, int direction, Integer targetY,
                         java.util.function.Function<IronElevatorPlatform, Location> landingFor) {
        return destination(shaft(source), source.getY(), direction, targetY, landingFor);
    }

    static Location destination(IronElevatorShaft shaft, int sourceY, int direction, Integer targetY,
                                java.util.function.Function<IronElevatorPlatform, Location> landingFor) {
        if (targetY != null) {
            IronElevatorPlatform target = targetY == sourceY ? null : shaft.at(targetY);
            return target == null ? null : landingFor.apply(target);
        }
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Direction must be -1 or 1");
        List<IronElevatorPlatform> platforms = shaft.platforms();
        int index = shaft.lowerBound(sourceY);
        if (direction < 0) index--;
        else if (index < platforms.size() && platforms.get(index).height() == sourceY) index++;
        for (; index >= 0 && index < platforms.size(); index += direction) {
            Location landing = landingFor.apply(platforms.get(index));
            if (landing != null) return landing;
        }
        return null;
    }

    void load(Path file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file.toFile());
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalStateException("Failed to load iron-elevators.yml", error);
        }
        overrides.clear();
        clear();
        ConfigurationSection worlds = config.getConfigurationSection("ground-floors");
        if (worlds == null) {
            if (config.contains("ground-floors")) {
                throw new IllegalArgumentException("ground-floors must be a mapping");
            }
            return;
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
                overrides.put(new Column(world, Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim())), height);
            }
        }
    }

    List<Floor> scan(Block source, Location origin) {
        Catalog catalog = catalog(shaft(source));
        while (!catalog.step()) { }
        return catalog.result();
    }

    Catalog catalog(IronElevatorShaft shaft) { return new Catalog(shaft); }

    /** Floor usability and terrain probes yield independently of the geometry scan. */
    final class Catalog {
        private final IronElevatorShaft shaft;
        private final List<IronElevatorPlatform> platforms = new ArrayList<>();
        private int index;
        private IronElevatorLanding.Usability usability;
        private IronElevatorGround.Measurement measurement;
        private Column column;
        private Integer override;
        private List<Integer> heights;
        private List<Floor> result;

        Catalog(IronElevatorShaft shaft) { this.shaft = shaft; }
        List<Floor> result() { return result; }

        boolean step() {
            if (result != null) return true;
            if (index < shaft.platforms().size()) {
                var platform = shaft.platforms().get(index);
                if (usability == null) usability = new IronElevatorLanding.Usability(platform);
                if (usability.step()) {
                    if (usability.usable()) platforms.add(platform);
                    index++;
                    usability = null;
                }
                return false;
            }
            if (platforms.size() < 3) {
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
            heights = platforms.stream().map(IronElevatorPlatform::height).toList();
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
            int groundIndex = groundIndex(heights, groundHeight, override == null ? 2 : 0);
            List<Floor> numbered = new ArrayList<>();
            for (int floor = 0; floor < platforms.size(); floor++)
                numbered.add(new Floor(platforms.get(floor), floorNumber(floor, groundIndex)));
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

    static int groundIndex(List<Integer> heights, int reference) {
        return groundIndex(heights, reference, 2);
    }

    private static int groundIndex(List<Integer> heights, int reference, int tolerance) {
        if (heights.isEmpty()) {
            throw new IllegalArgumentException("At least one floor is required");
        }
        for (int index = 0; index < heights.size(); index++) {
            if (heights.get(index) >= (long) reference - tolerance) {
                return index;
            }
        }
        return heights.size() - 1;
    }

    static int floorNumber(int index, int groundIndex) {
        int offset = index - groundIndex;
        return offset < 0 ? offset : offset + 1;
    }

    static int terrainReference(List<Integer> samples, int fallback) {
        if (samples.isEmpty()) {
            return fallback;
        }
        return IronElevatorGround.reference(java.util.stream.IntStream.range(0, samples.size())
                .mapToObj(index -> new IronElevatorGround.Sample(samples.get(index), index % 2 == 0 ? 8 : 16)).toList(), fallback);
    }
}
