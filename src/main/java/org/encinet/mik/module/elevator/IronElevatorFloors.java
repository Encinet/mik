package org.encinet.mik.module.elevator;

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
    private static final long CACHE_NANOS = 2_000_000_000L;
    private final Map<Column, Integer> overrides = new HashMap<>();
    private final Map<Column, GroundReference> groundReferences = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<Column, Cached> scans = new LinkedHashMap<>(16, 0.75F, true);
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
    }

    void invalidate(Block changed) {
        UUID worldId = changed.getWorld().getUID();
        scans.entrySet().removeIf(entry -> entry.getKey().worldId().equals(worldId)
                && entry.getValue().shaft().columns().stream().anyMatch(column ->
                    Math.abs((long) column.blockX() - changed.getX()) <= 1
                            && Math.abs((long) column.blockZ() - changed.getZ()) <= 1));
    }

    void invalidateChunk(UUID worldId, int chunkX, int chunkZ) {
        scans.entrySet().removeIf(entry -> entry.getKey().worldId().equals(worldId)
                && entry.getValue().shaft().columns().stream().anyMatch(column ->
                    (column.blockX() >> 4) == chunkX && (column.blockZ() >> 4) == chunkZ));
    }

    void clear() {
        scans.clear();
        groundReferences.clear();
    }

    private IronElevatorShaft shaft(Block source) {
        World world = source.getWorld();
        if (!world.isChunkLoaded(source.getX() >> 4, source.getZ() >> 4)
                || source.getType() != Material.IRON_BLOCK) return new IronElevatorShaft(List.of(), java.util.Set.of());
        long now = clock.getAsLong();
        IronElevatorPlatform.Column requested = IronElevatorPlatform.Column.of(source);
        Column matching = scans.entrySet().stream().filter(entry -> entry.getKey().worldId().equals(world.getUID())
                        && entry.getValue().shaft().columns().contains(requested))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (matching != null) {
            Cached cached = scans.get(matching);
            if (now - cached.measuredAt() < CACHE_NANOS && cached.shaft().loaded(world)) return cached.shaft();
            scans.remove(matching);
        }
        IronElevatorShaft scanned = IronElevatorShaft.scan(source);
        if (!scanned.platforms().isEmpty()) {
            Block anchor = scanned.platforms().stream().flatMap(platform -> platform.blocks().stream())
                    .min(IronElevatorPlatform.ORDER).orElseThrow();
            scans.put(Column.of(anchor), new Cached(scanned, now, null));
            if (scans.size() > MAX_CACHED_SHAFTS) scans.remove(scans.keySet().iterator().next());
        }
        return scanned;
    }

    IronElevatorPlatform platform(Block source) {
        IronElevatorPlatform platform = shaft(source).at(source.getY());
        return platform != null && platform.contains(source) ? platform : null;
    }

    Location destination(Block source, int direction, Integer targetY, Location origin,
                         IronElevatorLanding.Size size, Predicate<BoundingBox> extraCollision) {
        IronElevatorShaft shaft = shaft(source);
        if (targetY != null) {
            IronElevatorPlatform target = targetY == source.getY() ? null : shaft.at(targetY);
            return target == null ? null : IronElevatorLanding.on(target, origin, size, extraCollision);
        }
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Direction must be -1 or 1");
        var candidates = shaft.platforms().stream().filter(platform -> direction * (platform.height() - source.getY()) > 0)
                .sorted(Comparator.comparingInt(platform -> direction * platform.height())).toList();
        for (IronElevatorPlatform target : candidates) {
            Location landing = IronElevatorLanding.on(target, origin, size, extraCollision);
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
        IronElevatorShaft shaft = shaft(source);
        List<IronElevatorPlatform> platforms = shaft.platforms().stream().filter(IronElevatorLanding::usable).toList();
        if (platforms.size() < 3) {
            return List.of();
        }
        Block anchor = shaft.platforms().stream().flatMap(platform -> platform.blocks().stream())
                .min(IronElevatorPlatform.ORDER).orElseThrow();
        Column column = Column.of(anchor);
        Integer override = shaft.columns().stream().sorted(Comparator.comparingInt(IronElevatorPlatform.Column::blockX)
                        .thenComparingInt(IronElevatorPlatform.Column::blockZ))
                .map(position -> overrides.get(new Column(column.worldId(), position.blockX(), position.blockZ())))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        Column referenceColumn = override == null ? referenceColumn(column, shaft) : null;
        GroundReference remembered = referenceColumn == null ? null : groundReferences.get(referenceColumn);
        Integer groundHeight = override != null ? override : remembered == null ? null : remembered.height();
        if (remembered != null && (!referenceColumn.equals(column) || !remembered.columns().equals(shaft.columns()))) {
            groundReferences.remove(referenceColumn);
            groundReferences.put(column, new GroundReference(remembered.height(), shaft.columns()));
        }
        if (groundHeight == null) {
            Cached cached = scans.get(column);
            IronElevatorGround.Estimate estimate = cached != null && cached.tentativeGround() != null
                    ? cached.tentativeGround() : IronElevatorGround.measure(anchor,
                        platforms.stream().map(IronElevatorPlatform::height).toList());
            groundHeight = estimate.height();
            if (estimate.stable()) groundReferences.put(column, new GroundReference(groundHeight, shaft.columns()));
            else if (cached != null) scans.put(column, new Cached(cached.shaft(), cached.measuredAt(), estimate));
        }
        if (groundReferences.size() > MAX_GROUND_REFERENCES)
            groundReferences.remove(groundReferences.keySet().iterator().next());
        int groundIndex = groundIndex(platforms.stream().map(IronElevatorPlatform::height).toList(), groundHeight,
                override == null ? 2 : 0);
        List<Floor> floors = new ArrayList<>();
        for (int index = 0; index < platforms.size(); index++) {
            floors.add(new Floor(platforms.get(index), floorNumber(index, groundIndex)));
        }
        return List.copyOf(floors);
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
