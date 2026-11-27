package org.encinet.mik.feature.server.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.prison.PrisonCell.Height;
import org.encinet.mik.feature.server.prison.PrisonCell.Sector;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class PrisonLanding implements AutoCloseable {
    static final int TIMEOUT_SECONDS = 8;
    private static final int CACHE_SIZE = 64;
    private static final int[] OFFSETS = {2, 1, 3, 0};
    private static final double PLAYER_RADIUS = .3;

    private final JavaPlugin plugin;
    private final ThreadPoolExecutor geometry = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), Thread.ofPlatform().name("mik-prison-geometry").daemon(true).factory());
    // Accessed only by the geometry worker.
    private final LinkedHashMap<GeometryKey, List<Sector>> sectors = new LinkedHashMap<>(16, .75f, true);
    private final Set<CompletableFuture<Location>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    PrisonLanding(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    CompletableFuture<Location> prepare(Plot room, Height height) {
        var result = new CompletableFuture<Location>();
        pending.add(result);
        result.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((_, _) -> pending.remove(result));
        if (closed || height == null) {
            result.complete(null);
            return result;
        }
        try {
            geometry.execute(() -> {
                if (result.isDone()) return;
                try {
                    var key = new GeometryKey(room.cells(), height);
                    List<Sector> candidates = sectors.computeIfAbsent(key, _ -> PrisonCell.sectors(room.cells(), height));
                    if (sectors.size() > CACHE_SIZE) sectors.pollFirstEntry();
                    if (candidates.isEmpty()) result.complete(null);
                    else if (!result.isDone()) Bukkit.getScheduler().runTask(plugin, () -> {
                        World world = Bukkit.getWorld(room.world());
                        if (world == null) result.complete(null);
                        else search(world, candidates, 0, result);
                    });
                } catch (RuntimeException error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private static void search(World world, List<Sector> candidates, int index, CompletableFuture<Location> result) {
        if (result.isDone()) return;
        if (index == candidates.size()) {
            result.complete(null);
            return;
        }
        var sector = candidates.get(index);
        int chunkX = sector.x() >> 4, chunkZ = sector.z() >> 4;
        try {
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                Location point = findInSector(world, sector);
                if (point != null) result.complete(point);
                else search(world, candidates, index + 1, result);
            } else {
                world.getChunkAtAsync(chunkX, chunkZ, false).whenComplete((chunk, error) -> {
                    if (result.isDone()) return;
                    try {
                        Location point = error == null && chunk != null ? findInSector(world, sector) : null;
                        if (point != null) result.complete(point);
                        else search(world, candidates, index + 1, result);
                    } catch (RuntimeException failure) {
                        result.completeExceptionally(failure);
                    }
                });
            }
        } catch (RuntimeException error) {
            result.completeExceptionally(error);
        }
    }

    static Location near(Plot room, Location point) {
        if (!PrisonCell.inside(room, point)) return null;
        World world = point.getWorld();
        var sector = PrisonCell.sector(Cell.at(point.getBlockX(), point.getBlockY(), point.getBlockZ()),
                new Height(world.getMinHeight(), world.getMaxHeight()));
        return sector == null ? null : findInSector(world, sector);
    }

    static Location findInSector(World world, Sector sector) {
        if (!world.isChunkLoaded(sector.x() >> 4, sector.z() >> 4)) return null;
        for (int y = sector.low(); y <= sector.high(); y++) {
            for (int dx : OFFSETS) for (int dz : OFFSETS) {
                var point = new Location(world, sector.x() + dx + .5, y, sector.z() + dz + .5);
                if (standable(point)) return point;
            }
        }
        return null;
    }

    static boolean valid(Plot room, Location point) {
        return PrisonCell.inside(room, point) && room.protects(point.getBlockX(), point.getBlockY() + 1, point.getBlockZ())
                && standable(point);
    }

    private static boolean standable(Location point) {
        World world = point.getWorld();
        int x = point.getBlockX(), y = point.getBlockY(), z = point.getBlockZ();
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1 || !world.isChunkLoaded(x >> 4, z >> 4))
            return false;
        Block floor = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        if (dangerous(floor) || !floor.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL)
                || dangerous(feet) || !feet.isPassable() || dangerous(head) || !head.isPassable()) return false;
        for (double dx : new double[]{-PLAYER_RADIUS, PLAYER_RADIUS})
            for (double dz : new double[]{-PLAYER_RADIUS, PLAYER_RADIUS})
                if (!world.getWorldBorder().isInside(new Location(world, point.getX() + dx, y, point.getZ() + dz)))
                    return false;
        return true;
    }

    private static boolean dangerous(Block block) {
        return block.isLiquid() || switch (block.getType()) {
            case FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, POWDER_SNOW,
                    SWEET_BERRY_BUSH, WITHER_ROSE, POINTED_DRIPSTONE, NETHER_PORTAL, END_PORTAL, END_GATEWAY -> true;
            default -> false;
        };
    }

    @Override public void close() {
        closed = true;
        pending.forEach(result -> result.cancel(false));
        geometry.shutdownNow();
    }

    private record GeometryKey(Set<Cell> cells, Height height) {
        @Override public boolean equals(Object other) {
            return this == other || other instanceof GeometryKey key && cells == key.cells && height.equals(key.height);
        }

        @Override public int hashCode() { return 31 * System.identityHashCode(cells) + height.hashCode(); }
    }
}
