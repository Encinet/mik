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
    private static final int MAX_SAMPLED_BLOCKS = 32768;
    private final World world;
    private final Map<Position, List<BoundingBox>> collisions = new HashMap<>();

    public MenuBlockCollisionSnapshot(World world) { this.world = Objects.requireNonNull(world); }

    public record Region(boolean complete, List<MenuOcclusionGeometry.Box> boxes) {
        public Region { boxes = List.copyOf(boxes); }
    }

    public Region capture(BoundingBox region, Vector origin) {
        // Fences and other collision shapes can extend outside the cell that owns them.
        BoundingBox sampled = region.clone().expand(1);
        double count = (Math.floor(sampled.getMaxX()) - Math.floor(sampled.getMinX()) + 1)
                * (Math.floor(sampled.getMaxY()) - Math.floor(sampled.getMinY()) + 1)
                * (Math.floor(sampled.getMaxZ()) - Math.floor(sampled.getMinZ()) + 1);
        if (!Double.isFinite(count) || count > MAX_SAMPLED_BLOCKS) return new Region(false, List.of());
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
                    List<BoundingBox> boxes = collisions.get(position);
                    if (boxes == null) {
                        if (collisions.size() >= MAX_SAMPLED_BLOCKS) return false;
                        boxes = world.getBlockAt(x, y, z).getCollisionShape().getBoundingBoxes().stream()
                                .map(box -> box.clone().shift(position.x(), position.y(), position.z())).toList();
                        collisions.put(position, boxes);
                    }
                    for (BoundingBox box : boxes) if (box.overlaps(region)) add(result, box, origin);
                }
            }
        }
        return true;
    }

    private static void add(List<MenuOcclusionGeometry.Box> result, BoundingBox box, Vector origin) {
        result.add(MenuOcclusionGeometry.box(box, origin));
    }

    private record Position(int x, int y, int z) { }
}
