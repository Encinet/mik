package org.encinet.mik.module.elevator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronElevatorPanelGeometryTest {

    @Test
    void sharesGeometryAndLayoutAcrossNeighboringIronBlocksWithoutReadingTheWorldAgain() {
        IronElevatorTestWorld world = world();
        world.put(0, 20, 1, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        var first = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        var second = IronElevatorPlatform.resolve(world.block(0, 20, 1));
        var geometry = new IronElevatorPanelGeometry(() -> 0);
        var plan = geometry.plan(first, 20);
        assertNotNull(plan);
        world.blockReads = 0;
        assertSame(plan, geometry.plan(second, 20));
        assertEquals(0, world.blockReads);
        assertEquals(plan.wall(), geometry.plan(second, 21).wall());
        assertEquals(0, world.blockReads);
    }

    @Test
    void expiresBothMissingAndPresentWallsWithoutExtendingTheCacheOnEveryAccess() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        var platform = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        AtomicLong now = new AtomicLong();
        var geometry = new IronElevatorPanelGeometry(now::get);
        assertNull(geometry.plan(platform, 7));
        world.wall(20, BlockFace.EAST);
        now.set(1_000_000_000L);
        assertNull(geometry.plan(platform, 8));
        now.set(2_000_000_000L);
        assertNotNull(geometry.plan(platform, 8));
        world.put(1, 22, 0, Material.AIR, Set.of());
        now.set(4_000_000_000L);
        assertNull(geometry.plan(platform, 8));
    }

    @Test
    void invalidatesNearbyWallsAndObstaclesButNotDistantBuilding() {
        IronElevatorTestWorld world = world();
        var platform = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        var geometry = new IronElevatorPanelGeometry(() -> 0);
        var plan = geometry.plan(platform, 20);
        assertNotNull(plan);
        geometry.invalidate(world.block(100, 21, 0));
        geometry.invalidate(world.block(1, 40, 0));
        world.blockReads = 0;
        assertSame(plan, geometry.plan(platform, 20));
        assertEquals(0, world.blockReads);
        world.put(1, 22, 0, Material.AIR, Set.of());
        geometry.invalidate(world.block(1, 22, 0));
        assertNull(geometry.plan(platform, 20));
        assertTrue(world.blockReads > 0);
    }

    @Test
    void rechecksNearerWallsWhenTheyAreAdded() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.wall(20, BlockFace.EAST, 3);
        var platform = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        var geometry = new IronElevatorPanelGeometry(() -> 0);
        assertEquals(3, geometry.plan(platform, 20).wall().offset());
        world.wall(20, BlockFace.WEST);
        geometry.invalidate(world.block(-1, 21, 0));
        assertEquals(1, geometry.plan(platform, 20).wall().offset());
    }

    @Test
    void invalidatesOnlyRelevantChunksAndClearsOnShutdown() {
        IronElevatorTestWorld world = world();
        var platform = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        var geometry = new IronElevatorPanelGeometry(() -> 0);
        var plan = geometry.plan(platform, 20);
        geometry.invalidateChunk(world.world.getUID(), 20, 20);
        world.blockReads = 0;
        assertSame(plan, geometry.plan(platform, 20));
        assertEquals(0, world.blockReads);
        geometry.invalidateChunk(world.world.getUID(), 0, 0);
        assertNotNull(geometry.plan(platform, 20));
        assertTrue(world.blockReads > 0);
        geometry.clear();
        world.blockReads = 0;
        assertNotNull(geometry.plan(platform, 20));
        assertTrue(world.blockReads > 0);
    }

    @Test
    void keepsOnlySixtyFourPlatformsAndEvictsTheLeastRecentlyUsedOne() {
        List<IronElevatorTestWorld> worlds = new ArrayList<>();
        List<IronElevatorPlatform> platforms = new ArrayList<>();
        var geometry = new IronElevatorPanelGeometry(() -> 0);
        for (int index = 0; index < 65; index++) {
            IronElevatorTestWorld world = world();
            worlds.add(world);
            platforms.add(IronElevatorPlatform.resolve(world.block(0, 20, 0)));
        }
        var first = geometry.plan(platforms.getFirst(), 20);
        for (int index = 1; index < 64; index++) assertNotNull(geometry.plan(platforms.get(index), 20));
        assertSame(first, geometry.plan(platforms.getFirst(), 20));
        assertNotNull(geometry.plan(platforms.get(64), 20));
        worlds.getFirst().blockReads = 0;
        assertSame(first, geometry.plan(platforms.getFirst(), 20));
        assertEquals(0, worlds.getFirst().blockReads);
        worlds.get(1).blockReads = 0;
        assertNotNull(geometry.plan(platforms.get(1), 20));
        assertTrue(worlds.get(1).blockReads > 0);
    }

    @Test
    void doesNotInvalidateAnotherWorldAtTheSameCoordinates() {
        IronElevatorTestWorld world = world();
        IronElevatorTestWorld other = world();
        var platform = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        var geometry = new IronElevatorPanelGeometry(() -> 0);
        var plan = geometry.plan(platform, 20);
        geometry.invalidate(other.block(1, 22, 0));
        geometry.invalidateChunk(other.worldId, 0, 0);
        world.blockReads = 0;
        assertSame(plan, geometry.plan(platform, 20));
        assertEquals(0, world.blockReads);
    }

    private static IronElevatorTestWorld world() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.wall(20, BlockFace.EAST);
        return world;
    }
}
