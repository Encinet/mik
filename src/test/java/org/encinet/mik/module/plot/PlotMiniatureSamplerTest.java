package org.encinet.mik.module.plot;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotMiniatureSamplerTest {
    private final Thread caller = Thread.currentThread();
    private final AtomicInteger reads = new AtomicInteger();
    private final UUID worldId = UUID.randomUUID();
    private final PlotMiniatureGeometry.Grid grid = new PlotMiniatureGeometry.Grid(
            new PlotSelection.Bounds(0, -16, 0, 15, -1, 15));
    private final BlockData stone = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
            new Class<?>[] {BlockData.class}, (proxy, method, arguments) -> {
                assertSame(caller, Thread.currentThread(), method.getName());
                return switch (method.getName()) {
                case "getMaterial" -> Material.STONE;
                case "getAsString" -> "minecraft:stone";
                case "isOccluding" -> true;
                case "clone" -> proxy;
                default -> throw new AssertionError(method.getName());
                };
            });

    @Test
    void neverReadsOrLoadsUnloadedChunksAndReportsUnknownSamples() {
        var sampler = new PlotMiniatureSampler(new PlotPreviewCache(Runnable::run));
        UUID player = UUID.randomUUID();
        World world = world(false);
        sampler.request(player, world, grid);
        complete(sampler, player, world);
        var snapshot = sampler.request(player, world, grid);
        assertTrue(snapshot.ready());
        assertEquals(grid.count(), snapshot.unknown());
        assertTrue(snapshot.terrain().isEmpty());
        assertEquals(0, reads.get());
        assertEquals(PlotMiniatureSampler.MAX_UNKNOWN, snapshot.unknownBoxes().size());
    }

    @Test
    void multipleViewersShareOneGlobalBudgetAndReceiveFairTurns() {
        var sampler = new PlotMiniatureSampler(new PlotPreviewCache(Runnable::run));
        World world = world(true);
        List<UUID> players = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            UUID player = UUID.randomUUID();
            players.add(player);
            sampler.request(player, world, grid);
        }
        for (int count = 0; count < 4; count++) {
            int before = reads.get();
            sampler.tick();
            assertEquals(PlotMiniatureSampler.READS_PER_TICK, reads.get() - before);
        }
        for (int count = 0; count < 160; count++) {
            for (UUID player : players) sampler.request(player, world, grid);
            int before = reads.get();
            sampler.tick();
            assertTrue(reads.get() - before <= PlotMiniatureSampler.READS_PER_TICK);
        }
        assertTrue(players.stream().allMatch(player -> sampler.request(player, world, grid).ready()));
    }

    @Test
    void snapshotsIncludeUndergroundGeometryAndKeepActualBlockStateInstances() {
        var sampler = new PlotMiniatureSampler(new PlotPreviewCache(Runnable::run));
        UUID player = UUID.randomUUID();
        World world = world(true);
        sampler.request(player, world, grid);
        complete(sampler, player, world);
        var snapshot = sampler.request(player, world, grid);
        assertEquals(0, snapshot.unknown());
        assertFalse(snapshot.terrain().isEmpty());
        assertEquals(0, snapshot.terrain().stream().mapToDouble(voxel -> voxel.box().worldX() - voxel.box().width() / 2).min().orElseThrow());
        assertEquals(16, snapshot.terrain().stream().mapToDouble(voxel -> voxel.box().worldX() + voxel.box().width() / 2).max().orElseThrow());
        assertEquals(-16, snapshot.terrain().stream().mapToDouble(voxel -> voxel.box().worldY() - voxel.box().height() / 2).min().orElseThrow());
        assertEquals(0, snapshot.terrain().stream().mapToDouble(voxel -> voxel.box().worldY() + voxel.box().height() / 2).max().orElseThrow());
        assertFalse(snapshot.limited());
        assertTrue(snapshot.terrain().stream().allMatch(voxel -> voxel.box().worldY() < 0));
        assertSame(stone, snapshot.terrain().getFirst().block());
        int before = reads.get();
        sampler.request(player, world, grid);
        sampler.tick();
        assertEquals(before, reads.get());
    }

    @Test
    void closedAndForgottenPreviewsStopSampling() {
        var sampler = new PlotMiniatureSampler(new PlotPreviewCache(Runnable::run));
        UUID player = UUID.randomUUID();
        World world = world(true);
        sampler.request(player, world, grid);
        for (int count = 0; count < 31; count++) sampler.tick();
        int before = reads.get();
        sampler.tick();
        assertEquals(before, reads.get());
        sampler.request(player, world, grid);
        sampler.forget(player);
        sampler.tick();
        assertEquals(before, reads.get());
    }

    @Test
    void oneBlockFloorAndRoofAreBothSampledAndStayContinuousAcrossALargeFootprint() {
        var sampler = new PlotMiniatureSampler(new PlotPreviewCache(Runnable::run));
        UUID player = UUID.randomUUID();
        var room = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 64, 0, 63, 95, 63));
        World world = world(true, blockY -> blockY == 64 || blockY == 95);
        sampler.request(player, world, room);
        complete(sampler, player, world, room);
        var snapshot = sampler.request(player, world, room);
        assertTrue(snapshot.ready());
        assertTrue(snapshot.limited());
        for (double height : new double[] {64.5, 95.5}) {
            assertEquals(4096, snapshot.terrain().stream().filter(voxel -> voxel.box().worldY() == height)
                    .mapToDouble(voxel -> voxel.box().width() * voxel.box().depth()).sum());
        }
        assertEquals(room.count(), reads.get());
    }

    @Test
    void nearbyPreviewSamplesThinWallsDoorsAndEveryHeightWithoutAliasing() {
        var sampler = new PlotMiniatureSampler(new PlotPreviewCache(Runnable::run));
        UUID player = UUID.randomUUID();
        var nearby = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(-12, 58, -12, 12, 73, 12));
        World world = world(true, (blockX, blockY, blockZ) -> blockX == -3 && blockY < 72
                && !(blockZ == 0 && (blockY == 64 || blockY == 65)));
        complete(sampler, player, world, nearby);
        var snapshot = sampler.request(player, world, nearby);
        assertTrue(nearby.exact());
        assertFalse(snapshot.limited());
        assertEquals(10000, reads.get());
        for (int blockY = 58; blockY <= 73; blockY++) {
            for (int blockZ = -12; blockZ <= 12; blockZ++) {
                int covering = 0;
                for (var voxel : snapshot.terrain()) {
                    var box = voxel.box();
                    if (Math.abs(-2.5 - box.worldX()) < box.width() / 2
                            && Math.abs(blockY + 0.5 - box.worldY()) < box.height() / 2
                            && Math.abs(blockZ + 0.5 - box.worldZ()) < box.depth() / 2) covering++;
                }
                assertEquals(blockY < 72 && !(blockZ == 0 && (blockY == 64 || blockY == 65)) ? 1 : 0, covering);
            }
        }
    }

    @Test
    void initialLoadingPublishesTopDownGeometryAndProgressBeforeTheFinalSnapshot() {
        var sampler = new PlotMiniatureSampler(new PlotPreviewCache(Runnable::run));
        UUID player = UUID.randomUUID();
        var nearby = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(-12, 58, -12, 12, 73, 12));
        World world = world(true, blockY -> blockY == 73);
        var initial = sampler.request(player, world, nearby);
        assertEquals(0, initial.progress());
        for (int tick = 0; tick < 5; tick++) {
            sampler.request(player, world, nearby);
            int before = reads.get();
            sampler.tick();
            assertEquals(PlotMiniatureSampler.READS_PER_TICK, reads.get() - before);
        }
        var partial = sampler.request(player, world, nearby);
        assertFalse(partial.ready());
        assertTrue(partial.progress() >= 25);
        assertFalse(partial.terrain().isEmpty());
        assertEquals(0, partial.unknown());
        assertTrue(partial.terrain().stream().allMatch(voxel -> voxel.box().worldY() == 73.5));
        complete(sampler, player, world, nearby);
        assertEquals(100, sampler.request(player, world, nearby).progress());
    }

    @Test
    void periodicRefreshKeepsTheLastCompleteGeometryVisibleUntilTheNewScanCompletes() {
        var sampler = new PlotMiniatureSampler(new PlotPreviewCache(Runnable::run));
        UUID player = UUID.randomUUID();
        World world = world(true);
        complete(sampler, player, world);
        var completed = sampler.request(player, world, grid);
        for (int tick = 0; tick < 150; tick++) {
            var visible = sampler.request(player, world, grid);
            assertTrue(visible.ready());
            assertFalse(visible.terrain().isEmpty());
            sampler.tick();
        }
        assertTrue(reads.get() > grid.count());
        assertTrue(sampler.request(player, world, grid).revision() > completed.revision());
    }

    @Test
    void asynchronousMeshUsesAnImmutableSnapshotAndNeverCallsBukkitFromTheWorker() throws Exception {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        try (var worker = new PlotPreviewCache(tasks::add)) {
            var sampler = new PlotMiniatureSampler(worker);
            UUID player = UUID.randomUUID();
            World world = world(true);
            for (int tick = 0; tick < 6; tick++) {
                sampler.request(player, world, grid);
                int before = reads.get();
                sampler.tick();
                assertTrue(reads.get() - before <= PlotMiniatureSampler.READS_PER_TICK);
            }
            assertEquals(1, tasks.size());
            assertTrue(sampler.request(player, world, grid).terrain().isEmpty());
            Thread background = new Thread(tasks.remove(), "terrain-mesh-test");
            background.start();
            background.join(5000);
            assertFalse(background.isAlive());
            assertEquals(0, sampler.request(player, world, grid).progress());
            sampler.tick();
            var partial = sampler.request(player, world, grid);
            assertEquals(25, partial.progress());
            assertFalse(partial.terrain().isEmpty());
            assertFalse(partial.ready());
            assertEquals(1, tasks.size());
            sampler.forget(player);
            tasks.remove().run();
            assertEquals(0, sampler.request(player, world, grid).progress());
            sampler.disable();
        }
    }

    @Test
    void narrowWallsAreNotLostBecauseTheLongAxisNeedsCoarseSampling() {
        var sampler = new PlotMiniatureSampler(new PlotPreviewCache(Runnable::run));
        UUID player = UUID.randomUUID();
        var narrow = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 0, 0, 4095, 7, 15));
        World world = world(true, (blockX, blockY, blockZ) -> blockZ == 0 || blockZ == 3);
        sampler.request(player, world, narrow);
        complete(sampler, player, world, narrow);
        var snapshot = sampler.request(player, world, narrow);
        assertTrue(snapshot.ready());
        assertTrue(snapshot.terrain().stream().anyMatch(voxel -> voxel.box().worldZ() == 0.5));
        assertTrue(snapshot.terrain().stream().anyMatch(voxel -> voxel.box().worldZ() == 3.5));
        assertEquals(narrow.count(), reads.get());
    }

    private void complete(PlotMiniatureSampler sampler, UUID player, World world) {
        complete(sampler, player, world, grid);
    }

    private void complete(PlotMiniatureSampler sampler, UUID player, World world, PlotMiniatureGeometry.Grid grid) {
        for (int count = 0; count < 70; count++) {
            sampler.request(player, world, grid);
            sampler.tick();
        }
    }

    private World world(boolean loaded) {
        return world(loaded, blockY -> true);
    }

    private World world(boolean loaded, IntPredicate occupied) {
        return world(loaded, (blockX, blockY, blockZ) -> occupied.test(blockY));
    }

    private World world(boolean loaded, Occupied occupied) {
        Block block = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
                (proxy, method, arguments) -> {
                    assertSame(caller, Thread.currentThread(), method.getName());
                    if (method.getName().equals("getBlockData")) return stone;
                    if (method.getName().equals("isEmpty")) return false;
                    throw new AssertionError(method.getName());
                });
        Block air = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
                (proxy, method, arguments) -> {
                    assertSame(caller, Thread.currentThread(), method.getName());
                    if (method.getName().equals("isEmpty")) return true;
                    throw new AssertionError(method.getName());
                });
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, arguments) -> {
                    assertSame(caller, Thread.currentThread(), method.getName());
                    return switch (method.getName()) {
                    case "getUID" -> worldId;
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "isChunkLoaded" -> loaded;
                    case "getBlockAt" -> {
                        assertTrue(loaded);
                        reads.incrementAndGet();
                        yield occupied.test((int) arguments[0], (int) arguments[1], (int) arguments[2]) ? block : air;
                    }
                    default -> throw new AssertionError(method.getName());
                    };
                });
    }

    private interface Occupied {
        boolean test(int blockX, int blockY, int blockZ);
    }
}
