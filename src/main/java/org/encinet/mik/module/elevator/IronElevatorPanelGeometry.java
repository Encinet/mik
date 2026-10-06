package org.encinet.mik.module.elevator;

import org.bukkit.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

final class IronElevatorPanelGeometry {

    private static final long CACHE_NANOS = 2_000_000_000L;
    private static final int MAX_PLATFORMS = 64;

    record Plan(IronElevatorWall wall, IronElevatorPanelLayout layout) {
    }

    private record Cached(IronElevatorWall.Mount mount, long scannedAt, int levels, Plan plan) {
    }

    private final Map<IronElevatorPlatform, Cached> platforms = new LinkedHashMap<>(16, 0.75F, true);
    private final LongSupplier clock;

    IronElevatorPanelGeometry() {
        this(System::nanoTime);
    }

    IronElevatorPanelGeometry(LongSupplier clock) {
        this.clock = clock;
    }

    Plan plan(IronElevatorPlatform platform, int levels) {
        long now = clock.getAsLong();
        Cached previous = platforms.get(platform);
        IronElevatorWall.Mount mount;
        long scannedAt;
        if (previous == null || now - previous.scannedAt() >= CACHE_NANOS) {
            mount = IronElevatorWall.findMount(platform);
            scannedAt = now;
        } else {
            if (previous.levels() == levels) return previous.plan();
            mount = previous.mount();
            scannedAt = previous.scannedAt();
        }
        IronElevatorPanelLayout layout = mount == null ? null : mount.layout(levels);
        Plan plan = layout == null ? null : new Plan(mount.wall(), layout);
        platforms.put(platform, new Cached(mount, scannedAt, levels, plan));
        while (platforms.size() > MAX_PLATFORMS) platforms.remove(platforms.keySet().iterator().next());
        return plan;
    }

    void invalidate(Block changed) {
        UUID worldId = changed.getWorld().getUID();
        platforms.keySet().removeIf(platform -> platform.anchor().getWorld().getUID().equals(worldId)
                && changed.getY() >= platform.height() && changed.getY() <= platform.height() + 3
                && platform.blocks().stream().anyMatch(block -> Math.abs(block.getX() - changed.getX()) <= 7
                && Math.abs(block.getZ() - changed.getZ()) <= 7));
    }

    void invalidateChunk(UUID worldId, int chunkX, int chunkZ) {
        platforms.keySet().removeIf(platform -> platform.anchor().getWorld().getUID().equals(worldId)
                && platform.blocks().stream().anyMatch(block -> chunkX >= (block.getX() - 7 >> 4)
                && chunkX <= (block.getX() + 7 >> 4) && chunkZ >= (block.getZ() - 7 >> 4)
                && chunkZ <= (block.getZ() + 7 >> 4)));
    }

    void clear() {
        platforms.clear();
    }
}
