package org.encinet.mik.feature.world.elevator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IronElevatorCatalogDiscoveryTest {
    @Test
    void sharesColdWorkAcrossPlayersFloorsAndPlatformColumns() {
        var world = world();
        var catalog = new IronElevatorCatalog(() -> 0);
        var queue = new IronElevatorWorkQueue(() -> 0, error -> fail(error));
        var first = world.block(0, 20, 0);
        var second = world.block(1, 80, 1);
        var results = new ArrayList<IronElevatorCatalog.Result>();
        var firstPlatform = IronElevatorTestSupport.platform(first);
        var secondPlatform = IronElevatorTestSupport.platform(second);
        int reads = world.blockReads;
        var one = catalog.prepare(first, firstPlatform, queue, results::add);
        var two = catalog.prepare(second, secondPlatform, queue, results::add);
        assertEquals(reads, world.blockReads, "subscribing must not scan synchronously");
        assertTrue(results.isEmpty());
        drain(queue);
        assertEquals(2, results.size());
        assertSame(results.getFirst(), results.get(1));
        assertEquals(List.of(20, 40, 80), results.getFirst().floors().stream()
                .map(floor -> floor.level().height()).toList());
        assertFalse(one.active());
        assertFalse(two.active());
        assertNotNull(catalog.cached(second));

        int sharedReads = world.blockReads - reads;
        var singleWorld = world();
        var single = new IronElevatorCatalog(() -> 0);
        var singleQueue = new IronElevatorWorkQueue(() -> 0, error -> fail(error));
        var source = singleWorld.block(0, 20, 0);
        var sourcePlatform = IronElevatorTestSupport.platform(source);
        int singleReads = singleWorld.blockReads;
        single.prepare(source, sourcePlatform, singleQueue, _ -> { });
        drain(singleQueue);
        assertEquals(singleWorld.blockReads - singleReads, sharedReads);
    }

    @Test
    void cancellationAndRelevantInvalidationDiscardPartialSnapshots() {
        var world = world();
        var catalog = new IronElevatorCatalog(() -> 0);
        var queue = new IronElevatorWorkQueue(() -> 0, error -> fail(error));
        var source = world.block(0, 20, 0);
        var platform = IronElevatorTestSupport.platform(source);
        var subscription = catalog.prepare(source, platform, queue, _ -> fail("cancelled result"));
        queue.tick();
        subscription.cancel();
        int reads = world.blockReads;
        drain(queue);
        assertEquals(reads, world.blockReads);
        assertNull(catalog.cached(source));

        catalog.prepare(source, platform, queue, _ -> fail("invalidated result"));
        queue.tick();
        catalog.invalidate(world.block(1, 80, 1));
        drain(queue);
        assertNull(catalog.cached(source));
    }

    @Test
    void unrelatedEditsAndUnloadsDoNotRestartTheScan() {
        var world = world();
        var queue = new IronElevatorWorkQueue(() -> 0, error -> fail(error));
        var catalog = new IronElevatorCatalog(() -> 0);
        var source = world.block(0, 20, 0);
        var results = new ArrayList<IronElevatorCatalog.Result>();
        catalog.prepare(source, IronElevatorTestSupport.platform(source), queue, results::add);
        queue.tick();
        catalog.invalidate(world.block(1000, 80, 1000));
        catalog.invalidateChunk(world.worldId, 100, 100);
        drain(queue);
        assertEquals(1, results.size());
    }

    @Test
    void unloadingAProbedNeighbourChunkInvalidatesACompletedCatalog() {
        var world = world();
        var catalog = new IronElevatorCatalog(() -> 0);
        var source = world.block(0,20,0);
        assertEquals(3,IronElevatorTestSupport.floors(catalog,source).size());
        assertNotNull(catalog.cached(source));

        world.unloadedChunks.add("-1,0");
        catalog.invalidateChunk(world.worldId,-1,0);
        assertNull(catalog.cached(source));
    }

    @Test
    void resumableScansReadAtMostOneBlockPerStepAndRejectUnloadedChunks() {
        var world = world();
        var scan = new IronElevatorShaft.Scan(world.block(0, 20, 0));
        int steps = 0;
        boolean complete;
        do {
            int reads = world.blockReads;
            complete = scan.step();
            assertTrue(world.blockReads - reads <= 1);
            assertTrue(++steps < 10_000);
        } while (!complete);
        assertEquals(List.of(20, 40, 80), scan.result().levels().stream().map(IronElevatorShaft.Level::height).toList());

        var unloaded = new IronElevatorShaft.Scan(world.block(0, 20, 0));
        for (int step = 0; step < 20; step++) unloaded.step();
        world.unloadedChunks.add("0,0");
        while (!unloaded.step()) { }
        assertTrue(unloaded.result().levels().isEmpty());
    }

    private static IronElevatorTestWorld world() {
        var world = new IronElevatorTestWorld();
        for (int y : List.of(20, 40, 80))
            for (int x = 0; x < 2; x++)
                for (int z = 0; z < 2; z++)
                    world.put(x, y, z, Material.IRON_BLOCK, Set.of(BlockFace.UP));
        return world;
    }

    private static void drain(IronElevatorWorkQueue queue) {
        int ticks = 0;
        while (!queue.empty()) {
            queue.tick();
            assertTrue(++ticks < 200);
        }
    }
}
