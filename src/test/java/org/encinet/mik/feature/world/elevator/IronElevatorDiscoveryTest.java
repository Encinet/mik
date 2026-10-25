package org.encinet.mik.feature.world.elevator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IronElevatorDiscoveryTest {
    @Test
    void sharesColdWorkAcrossPlayersFloorsAndPlatformColumns() {
        var world = world();
        var floors = new IronElevatorFloors(() -> 0);
        var queue = new IronElevatorWorkQueue(() -> 0, error -> fail(error));
        var discovery = new IronElevatorDiscovery(floors, queue);
        var first = world.block(0, 20, 0);
        var second = world.block(1, 80, 1);
        var results = new ArrayList<IronElevatorDiscovery.Result>();
        var firstPlatform = IronElevatorPlatform.resolve(first);
        var secondPlatform = IronElevatorPlatform.resolve(second);
        int reads = world.blockReads;
        var one = discovery.prepare(first, firstPlatform, results::add);
        var two = discovery.prepare(second, secondPlatform, results::add);
        assertEquals(reads, world.blockReads, "subscribing must not scan synchronously");
        assertTrue(results.isEmpty());
        drain(queue);
        assertEquals(2, results.size());
        assertSame(results.getFirst(), results.get(1));
        assertEquals(List.of(20, 40, 80), results.getFirst().floors().stream()
                .map(floor -> floor.platform().height()).toList());
        assertFalse(one.active());
        assertFalse(two.active());
        assertNotNull(floors.cached(second));

        int sharedReads = world.blockReads - reads;
        var singleWorld = world();
        var singleFloors = new IronElevatorFloors(() -> 0);
        var singleQueue = new IronElevatorWorkQueue(() -> 0, error -> fail(error));
        var single = new IronElevatorDiscovery(singleFloors, singleQueue);
        var source = singleWorld.block(0, 20, 0);
        var sourcePlatform = IronElevatorPlatform.resolve(source);
        int singleReads = singleWorld.blockReads;
        single.prepare(source, sourcePlatform, ignored -> { });
        drain(singleQueue);
        assertEquals(singleWorld.blockReads - singleReads, sharedReads);
    }

    @Test
    void cancellationAndRelevantInvalidationDiscardPartialSnapshots() {
        var world = world();
        var floors = new IronElevatorFloors(() -> 0);
        var queue = new IronElevatorWorkQueue(() -> 0, error -> fail(error));
        var discovery = new IronElevatorDiscovery(floors, queue);
        var source = world.block(0, 20, 0);
        var platform = IronElevatorPlatform.resolve(source);
        var subscription = discovery.prepare(source, platform, ignored -> fail("cancelled result"));
        queue.tick();
        subscription.cancel();
        int reads = world.blockReads;
        drain(queue);
        assertEquals(reads, world.blockReads);
        assertNull(floors.cached(source));

        discovery.prepare(source, platform, ignored -> fail("invalidated result"));
        queue.tick();
        discovery.invalidate(world.block(1, 80, 1));
        drain(queue);
        assertNull(floors.cached(source));
    }

    @Test
    void unrelatedEditsAndUnloadsDoNotRestartTheScan() {
        var world = world();
        var queue = new IronElevatorWorkQueue(() -> 0, error -> fail(error));
        var discovery = new IronElevatorDiscovery(new IronElevatorFloors(() -> 0), queue);
        var source = world.block(0, 20, 0);
        var results = new ArrayList<IronElevatorDiscovery.Result>();
        discovery.prepare(source, IronElevatorPlatform.resolve(source), results::add);
        queue.tick();
        discovery.invalidate(world.block(1000, 80, 1000));
        discovery.invalidateChunk(world.worldId, 100, 100);
        drain(queue);
        assertEquals(1, results.size());
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
        assertEquals(List.of(20, 40, 80), scan.result().platforms().stream().map(IronElevatorPlatform::height).toList());

        var unloaded = new IronElevatorShaft.Scan(world.block(0, 20, 0));
        for (int step = 0; step < 20; step++) unloaded.step();
        world.unloadedChunks.add("0,0");
        while (!unloaded.step()) { }
        assertTrue(unloaded.result().platforms().isEmpty());
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
