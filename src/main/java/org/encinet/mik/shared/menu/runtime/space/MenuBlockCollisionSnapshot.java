package org.encinet.mik.shared.menu.runtime.space;

import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Main-thread, placement-scoped block sampling. Never loads chunks or retains world state between fits. */
public final class MenuBlockCollisionSnapshot {
    static final double SHAPE_PADDING = 1;
    private static final int MAX_SAMPLED_BLOCKS = 32768;
    private final World world;
    private final Map<Position, List<BoundingBox>> collisions = new HashMap<>();

    public MenuBlockCollisionSnapshot(World world) { this.world = Objects.requireNonNull(world); }

    public record Region(boolean complete, List<MenuOcclusionGeometry.Box> boxes) {
        public Region { boxes = List.copyOf(boxes); }
    }

    public Region capture(BoundingBox region, Vector origin) {
        // Fences and other collision shapes can extend outside the cell that owns them.
        BoundingBox sampled = region.clone().expand(SHAPE_PADDING);
        if (!withinBudget(sampled)) return new Region(false, List.of());
        List<MenuOcclusionGeometry.Box> result = new ArrayList<>();
        if (region.getMinY() < world.getMinHeight()) {
            add(result, new BoundingBox(region.getMinX(), region.getMinY(), region.getMinZ(),
                    region.getMaxX(), world.getMinHeight(), region.getMaxZ()), origin);
        }
        if (region.getMaxY() > world.getMaxHeight()) {
            add(result, new BoundingBox(region.getMinX(), world.getMaxHeight(), region.getMinZ(),
                    region.getMaxX(), region.getMaxY(), region.getMaxZ()), origin);
        }
        int minimumChunkX = (int) Math.floor(sampled.getMinX()) >> 4;
        int maximumChunkX = (int) Math.floor(sampled.getMaxX()) >> 4;
        int minimumChunkZ = (int) Math.floor(sampled.getMinZ()) >> 4;
        int maximumChunkZ = (int) Math.floor(sampled.getMaxZ()) >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    add(result, new BoundingBox(chunkX * 16.0, region.getMinY(), chunkZ * 16.0,
                            chunkX * 16.0 + 16, region.getMaxY(), chunkZ * 16.0 + 16), origin);
                    continue;
                }
                if (!captureChunk(result, sampled, region, origin, chunkX, chunkZ)) return new Region(false, List.of());
            }
        }
        return new Region(true, result);
    }

    private boolean captureChunk(List<MenuOcclusionGeometry.Box> result, BoundingBox sampled, BoundingBox region,
                                 Vector origin, int chunkX, int chunkZ) {
        int minimumY = Math.max(world.getMinHeight(), (int) Math.floor(sampled.getMinY()));
        int maximumY = Math.min(world.getMaxHeight() - 1, (int) Math.floor(sampled.getMaxY()));
        for (int x = Math.max(chunkX * 16, (int) Math.floor(sampled.getMinX()));
             x <= Math.min(chunkX * 16 + 15, (int) Math.floor(sampled.getMaxX())); x++) {
            for (int z = Math.max(chunkZ * 16, (int) Math.floor(sampled.getMinZ()));
                 z <= Math.min(chunkZ * 16 + 15, (int) Math.floor(sampled.getMaxZ())); z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    Position position = new Position(x, y, z);
                    var boxes = shapes(position);
                    if (boxes == null) return false;
                    for (BoundingBox box : boxes) if (box.overlaps(region)) add(result, box, origin);
                }
            }
        }
        return true;
    }

    private List<BoundingBox> shapes(Position position) {
        var boxes = collisions.get(position);
        if (boxes != null) return boxes;
        if (collisions.size() >= MAX_SAMPLED_BLOCKS) return null;
        boxes = world.getBlockAt(position.x(), position.y(), position.z()).getCollisionShape().getBoundingBoxes().stream()
                .map(box -> box.clone().shift(position.x(), position.y(), position.z())).toList();
        collisions.put(position, boxes);
        return boxes;
    }

    private static boolean withinBudget(BoundingBox sampled) {
        double count = (Math.floor(sampled.getMaxX()) - Math.floor(sampled.getMinX()) + 1)
                * (Math.floor(sampled.getMaxY()) - Math.floor(sampled.getMinY()) + 1)
                * (Math.floor(sampled.getMaxZ()) - Math.floor(sampled.getMinZ()) + 1);
        return count <= MAX_SAMPLED_BLOCKS;
    }

    /** Main-thread capture cursor. The result contains detached, eye-relative collision shapes. */
    public static final class Survey {
        private final World world;
        private final Vector origin;
        private final BoundingBox region;
        private final boolean possible;
        private final Cells full;
        private Cells active;
        private Cells sampledPrefix;
        private final Map<Chunk, Boolean> chunks = new HashMap<>();
        private final List<BoundingBox> shapes = new ArrayList<>();
        private int blockX, blockY, blockZ;
        private boolean stale;

        public Survey(World world, Vector origin, BoundingBox relative) {
            this(world, origin, relative, null);
        }

        Survey(World world, Vector origin, BoundingBox relative, BoundingBox prefix) {
            this.world = Objects.requireNonNull(world);
            this.origin = origin.clone();
            this.region = relative.clone().shift(origin);
            var sampled = region.clone().expand(SHAPE_PADDING);
            possible = withinBudget(sampled);
            int floor = world.getMinHeight(), ceiling = world.getMaxHeight();
            full = Cells.of(sampled, floor, ceiling);
            var first = prefix == null ? full : Cells.of(prefix.clone().shift(origin).expand(SHAPE_PADDING), floor, ceiling);
            resetCursor(first.count() * 4 <= full.count() * 3 ? first : full);
            if (region.getMinY() < floor) add(new BoundingBox(region.getMinX(), region.getMinY(), region.getMinZ(),
                    region.getMaxX(), floor, region.getMaxZ()));
            if (region.getMaxY() > ceiling) add(new BoundingBox(region.getMinX(), ceiling, region.getMinZ(),
                    region.getMaxX(), region.getMaxY(), region.getMaxZ()));
        }

        boolean partial() { return active != full; }

        void expand() {
            if (!partial() || !done() || stale) throw new IllegalStateException("Cannot expand an incomplete terrain survey");
            sampledPrefix = active;
            resetCursor(full);
        }

        private void resetCursor(Cells cells) {
            active = cells;
            blockX = cells.minX();
            blockY = cells.minY();
            blockZ = cells.minZ();
        }

        public int sample(MenuBlockCollisionSnapshot reads, int allowance, long deadline) {
            if (!world.equals(reads.world)) throw new IllegalArgumentException("A survey must sample its own world");
            int sampled = 0;
            var loadedNow = new HashMap<Chunk, Boolean>();
            while (!done() && sampled < allowance && System.nanoTime() - deadline < 0) {
                if (sampledPrefix != null && sampledPrefix.contains(blockX, blockY, blockZ)) {
                    blockY = sampledPrefix.maxY();
                    advance();
                    continue;
                }
                var chunk = new Chunk(blockX >> 4, blockZ >> 4);
                boolean loaded = loadedNow.computeIfAbsent(chunk, key -> world.isChunkLoaded(key.x(), key.z()));
                Boolean previous = chunks.putIfAbsent(chunk, loaded);
                if (previous != null && previous != loaded) { stale = true; break; }
                if (loaded) {
                    var boxes = reads.shapes(new Position(blockX, blockY, blockZ));
                    if (boxes == null) { stale = true; break; }
                    for (var box : boxes) if (box.overlaps(region)) add(box);
                } else if (previous == null) {
                    var wall = new BoundingBox(chunk.x() * 16.0, region.getMinY(), chunk.z() * 16.0,
                            chunk.x() * 16.0 + 16, region.getMaxY(), chunk.z() * 16.0 + 16);
                    if (wall.overlaps(region)) add(wall);
                }
                sampled++;
                advance();
            }
            return sampled;
        }

        public boolean possible() { return possible; }

        private void advance() {
            if (++blockY > active.maxY()) {
                blockY = active.minY();
                if (++blockZ > active.maxZ()) { blockZ = active.minZ(); blockX++; }
            }
        }

        public boolean done() { return !possible || stale || active.minY() > active.maxY() || blockX > active.maxX(); }

        public boolean current() {
            return !stale && chunks.entrySet().stream().allMatch(entry ->
                    world.isChunkLoaded(entry.getKey().x(), entry.getKey().z()) == entry.getValue());
        }

        public boolean covers(World changedWorld, int x, int y, int z) {
            return world.equals(changedWorld) && x >= Math.floor(region.getMinX() - SHAPE_PADDING) && x <= Math.floor(region.getMaxX() + SHAPE_PADDING)
                    && y >= Math.floor(region.getMinY() - SHAPE_PADDING) && y <= Math.floor(region.getMaxY() + SHAPE_PADDING)
                    && z >= Math.floor(region.getMinZ() - SHAPE_PADDING) && z <= Math.floor(region.getMaxZ() + SHAPE_PADDING);
        }

        public List<BoundingBox> finish() {
            if (!done() || !possible || stale) throw new IllegalStateException("The terrain survey is incomplete");
            return List.copyOf(shapes);
        }

        private void add(BoundingBox absolute) { shapes.add(absolute.clone().shift(-origin.getX(), -origin.getY(), -origin.getZ())); }

        private record Chunk(int x, int z) { }
        private record Cells(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            private static Cells of(BoundingBox sampled, int floor, int ceiling) {
                return new Cells((int) Math.floor(sampled.getMinX()), Math.max(floor, (int) Math.floor(sampled.getMinY())),
                        (int) Math.floor(sampled.getMinZ()), (int) Math.floor(sampled.getMaxX()),
                        Math.min(ceiling - 1, (int) Math.floor(sampled.getMaxY())), (int) Math.floor(sampled.getMaxZ()));
            }

            private double count() {
                return (maxX - (double) minX + 1) * Math.max(0, maxY - (double) minY + 1) * (maxZ - (double) minZ + 1);
            }

            private boolean contains(int x, int y, int z) {
                return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
            }
        }
    }

    private static void add(List<MenuOcclusionGeometry.Box> result, BoundingBox box, Vector origin) {
        result.add(MenuOcclusionGeometry.box(box, origin));
    }

    private record Position(int x, int y, int z) { }
}
