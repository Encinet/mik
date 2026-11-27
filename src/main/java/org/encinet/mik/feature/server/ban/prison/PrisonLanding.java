package org.encinet.mik.feature.server.ban.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.ban.prison.PrisonCell.Height;
import org.encinet.mik.feature.server.ban.prison.PrisonCell.Sector;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class PrisonLanding implements AutoCloseable {
    static final int TIMEOUT_SECONDS = 8;
    private static final int CACHE_SIZE = 64;
    private static final int SEARCH_LIMIT = 64;
    private static final int STEPS_PER_TICK = 128;
    private static final long TICK_BUDGET_NANOS = 1_000_000;
    private static final int[] OFFSETS = {2, 1, 3, 0};
    private static final double[] BORDER_OFFSETS = {-.3, .3};
    private static final int POSITIONS_PER_LEVEL = OFFSETS.length * OFFSETS.length;

    private final JavaPlugin plugin;
    private final ThreadPoolExecutor geometry = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(SEARCH_LIMIT), Thread.ofPlatform().name("mik-prison-geometry").daemon(true).factory());
    // Geometry is read by the worker; block search and its queue belong to the server thread.
    private final LinkedHashMap<GeometryKey, List<Sector>> sectors = new LinkedHashMap<>(16, .75f, true);
    private final ArrayDeque<Search> work = new ArrayDeque<>();
    private final Map<RoomKey, CompletableFuture<Location>> pending = new HashMap<>();
    private boolean closed;

    PrisonLanding(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    CompletableFuture<Location> prepare(Plot room, Height height) {
        if (height == null) return CompletableFuture.completedFuture(null);
        var key = new RoomKey(room.world(), new GeometryKey(room.cells(), height));
        CompletableFuture<Location> result;
        synchronized (pending) {
            result = pending.get(key);
            if (result != null && !result.isDone()) return copyResult(result);
            if (closed || pending.size() >= SEARCH_LIMIT) return CompletableFuture.completedFuture(null);
            result = new CompletableFuture<>();
            pending.put(key, result);
        }

        var shared = result;
        shared.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((_, _) -> {
            synchronized (pending) {
                pending.remove(key, shared);
            }
        });
        try {
            geometry.execute(() -> prepareGeometry(key, shared));
        } catch (RuntimeException error) {
            shared.completeExceptionally(error);
        }
        return copyResult(shared);
    }

    private static CompletableFuture<Location> copyResult(CompletableFuture<Location> shared) {
        // One disconnected player must not cancel a search other connections are waiting for.
        return shared.thenApply(point -> point == null ? null : point.clone());
    }

    private void prepareGeometry(RoomKey key, CompletableFuture<Location> result) {
        if (result.isDone()) return;
        try {
            List<Sector> candidates = sectors.computeIfAbsent(key.geometry(),
                    shape -> PrisonCell.sectors(shape.cells(), shape.height()));
            if (sectors.size() > CACHE_SIZE) sectors.pollFirstEntry();
            if (candidates.isEmpty()) result.complete(null);
            else if (!result.isDone()) Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.isDone()) return;
                World world = Bukkit.getWorld(key.world());
                if (world == null) result.complete(null);
                else work.addLast(new Search(world, candidates, result));
            });
        } catch (RuntimeException error) {
            result.completeExceptionally(error);
        }
    }

    void tick() {
        if (work.isEmpty()) return;
        long started = System.nanoTime();
        for (int i = 0; i < STEPS_PER_TICK && !work.isEmpty(); i++) {
            var search = work.removeFirst();
            try {
                if (search.step()) work.addLast(search);
            } catch (RuntimeException error) {
                search.result.completeExceptionally(error);
            }
            if (System.nanoTime() - started >= TICK_BUDGET_NANOS) break;
        }
    }

    void worldUnloaded(UUID id) {
        List<CompletableFuture<Location>> searches;
        synchronized (pending) {
            searches = pending.entrySet().stream().filter(entry -> entry.getKey().world().equals(id))
                    .map(Map.Entry::getValue).toList();
        }
        searches.forEach(result -> result.complete(null));
        work.removeIf(search -> search.result.isDone());
    }

    private final class Search {
        final World world;
        final List<Sector> candidates;
        final CompletableFuture<Location> result;
        int index;
        int position;
        boolean triedLoading;

        Search(World world, List<Sector> candidates, CompletableFuture<Location> result) {
            this.world = world;
            this.candidates = candidates;
            this.result = result;
        }

        /** Returns whether this job is ready for another step. Chunk loads requeue when ready. */
        boolean step() {
            if (result.isDone()) return false;
            if (index == candidates.size()) {
                result.complete(null);
                return false;
            }
            Sector sector = candidates.get(index);
            int x = sector.x() >> 4, z = sector.z() >> 4;
            if (!world.isChunkLoaded(x, z)) {
                if (triedLoading) {
                    nextSector();
                    return true;
                }
                triedLoading = true;
                // Paper completes the chunk future on the server thread.
                world.getChunkAtAsync(x, z, false).whenComplete((chunk, error) -> {
                    if (result.isDone()) return;
                    if (chunk == null || error != null) nextSector();
                    work.addLast(this);
                });
                return false;
            }
            Location point = candidate(world, sector, position++);
            if (standable(point)) {
                result.complete(point);
                return false;
            }
            if (position == positions(sector)) nextSector();
            return true;
        }

        private void nextSector() {
            index++;
            position = 0;
            triedLoading = false;
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
        for (int i = 0; i < positions(sector); i++) {
            Location point = candidate(world, sector, i);
            if (standable(point)) return point;
        }
        return null;
    }

    private static int positions(Sector sector) {
        return (sector.high() - sector.low() + 1) * POSITIONS_PER_LEVEL;
    }

    private static Location candidate(World world, Sector sector, int position) {
        int dx = OFFSETS[position / OFFSETS.length % OFFSETS.length];
        int dz = OFFSETS[position % OFFSETS.length];
        int y = sector.low() + position / POSITIONS_PER_LEVEL;
        return new Location(world, sector.x() + dx + .5, y, sector.z() + dz + .5);
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
        for (double dx : BORDER_OFFSETS)
            for (double dz : BORDER_OFFSETS)
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
        List<CompletableFuture<Location>> searches;
        synchronized (pending) {
            closed = true;
            searches = List.copyOf(pending.values());
            pending.clear();
        }
        searches.forEach(result -> result.cancel(false));
        work.clear();
        geometry.shutdownNow();
    }

    private record RoomKey(UUID world, GeometryKey geometry) { }

    private record GeometryKey(Set<Cell> cells, Height height) {
        @Override public boolean equals(Object other) {
            return this == other || other instanceof GeometryKey key && cells == key.cells && height.equals(key.height);
        }

        @Override public int hashCode() { return 31 * System.identityHashCode(cells) + height.hashCode(); }
    }
}
