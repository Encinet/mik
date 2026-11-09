package org.encinet.mik.feature.world.elevator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronElevatorPlatformTest {

    @Test
    void sharesOneFloorListAndChangesOnlyYFromEveryBlockOfThePlatform() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int height : List.of(20, 40, 80)) square(world, height);
        IronElevatorCatalog floors = new IronElevatorCatalog();
        var first = IronElevatorTestSupport.floors(floors, world.block(0, 40, 0));
        var second = IronElevatorTestSupport.floors(floors, world.block(1, 40, 1));
        assertEquals(first, second);
        assertEquals(3, first.size());
        for (int blockX = 0; blockX <= 1; blockX++) {
            for (int blockZ = 0; blockZ <= 1; blockZ++) {
                Location origin = new Location(world.world, blockX + 0.2, 41, blockZ + 0.8, 70, -25);
                Location landing = IronElevatorTestSupport.destination(floors, world.block(blockX, 40, blockZ), 1, null, origin, IronElevatorLanding.DEFAULT_SIZE, _ -> false);
                assertNotNull(landing);
                assertEquals(origin.getX(), landing.getX());
                assertEquals(origin.getZ(), landing.getZ());
                assertEquals(81, landing.getY());
                assertEquals(70, landing.getYaw());
                assertEquals(-25, landing.getPitch());
            }
        }
    }

    @Test
    void followsOverlappingPlatformsRatherThanOnlyThePlayersColumn() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        iron(world, 0, 20, 0);
        iron(world, 1, 20, 0);
        iron(world, 1, 40, 0);
        iron(world, 2, 40, 0);
        iron(world, 2, 80, 0);
        IronElevatorCatalog floors = new IronElevatorCatalog();
        assertEquals(List.of(20, 40, 80), IronElevatorTestSupport.floors(floors, world.block(0, 20, 0))
                .stream().map(floor -> floor.level().height()).toList());
        assertNull(IronElevatorTestSupport.destination(floors, world.block(2, 80, 0), 0, 20, new Location(world.world, 2.2, 81, 0.7), IronElevatorLanding.DEFAULT_SIZE, _ -> false));
        assertEquals(41, IronElevatorTestSupport.destination(floors, world.block(2, 80, 0), -1, null, new Location(world.world, 2.2, 81, 0.7), IronElevatorLanding.DEFAULT_SIZE, _ -> false).getY());
        assertNull(IronElevatorTestSupport.destination(floors, world.block(0, 20, 0), 1, null, world.origin(20), IronElevatorLanding.DEFAULT_SIZE, _ -> false));
        assertEquals(41, IronElevatorTestSupport.destination(floors, world.block(1, 20, 0), 1, null, new Location(world.world, 1.5, 21, 0.5), IronElevatorLanding.DEFAULT_SIZE, _ -> false).getY());
    }

    @Test
    void neverMovesHorizontallyToAvoidAHole() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int blockX = 0; blockX < 3; blockX++) {
            for (int blockZ = 0; blockZ < 3; blockZ++) {
                if (blockX == 1 && blockZ == 1) continue;
                iron(world, blockX, 40, blockZ);
            }
        }
        Location first = IronElevatorTestSupport.landing(world.block(0, 40, 0), world.origin(20),
                IronElevatorLanding.DEFAULT_SIZE, _ -> false);
        Location second = IronElevatorTestSupport.landing(world.block(2, 40, 2),
                new Location(world.world, 2.8, 21, 2.8), IronElevatorLanding.DEFAULT_SIZE, _ -> false);
        assertEquals(0.5, first.getX());
        assertEquals(0.5, first.getZ());
        assertEquals(2.8, second.getX());
        assertEquals(2.8, second.getZ());
        assertNull(IronElevatorTestSupport.landing(world.block(0, 40, 0), new Location(world.world, 1.5, 21, 1.5),
                IronElevatorLanding.DEFAULT_SIZE, _ -> false));
        assertNotEquals(Material.AIR, world.block(first.getBlockX(), 40, first.getBlockZ()).getType());
    }

    @Test
    void ignoresDiagonalAndUnconnectedIronAndRejectsOversizedPlatforms() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        iron(world, 0, 20, 0);
        iron(world, 1, 20, 1);
        assertEquals(1, IronElevatorTestSupport.platform(world.block(0, 20, 0)).blocks().size());
        assertEquals(1, IronElevatorTestSupport.shaft(world.block(0, 20, 0)).columns().size());
        for (int blockX = 0; blockX <= IronElevatorPlatform.MAX_BLOCKS; blockX++) iron(world, blockX, 40, 0);
        assertNull(IronElevatorTestSupport.platform(world.block(0, 40, 0)));
    }

    @Test
    void sharesWarmScansAcrossFloorsAndRefreshesAfterExpiryAndInvalidation() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int height : List.of(20, 40, 80)) square(world, height);
        AtomicLong clock = new AtomicLong();
        IronElevatorCatalog floors = new IronElevatorCatalog(clock::get);
        IronElevatorTestSupport.warm(floors, world.block(0, 20, 0));
        int coldReads = world.blockReads;
        for (int attempt = 0; attempt < 20; attempt++) IronElevatorTestSupport.warm(floors, world.block(1, 80, 1));
        assertEquals(coldReads, world.blockReads);
        clock.set(2_000_000_001L);
        IronElevatorTestSupport.warm(floors, world.block(1, 40, 1));
        assertTrue(world.blockReads > coldReads);
        int refreshedReads = world.blockReads;
        floors.invalidate();
        IronElevatorTestSupport.warm(floors, world.block(0, 20, 0));
        assertTrue(world.blockReads > refreshedReads);
    }

    @Test
    void rechecksObstaclesAndSupportEvenDuringAWarmGeometryCache() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.iron(40);
        IronElevatorCatalog floors = new IronElevatorCatalog();
        assertNotNull(IronElevatorTestSupport.destination(floors, world.block(0, 20, 0), 1, null, world.origin(20), IronElevatorLanding.DEFAULT_SIZE, _ -> false));
        world.put(0, 42, 0, Material.STONE, Set.of(BlockFace.UP));
        assertNull(IronElevatorTestSupport.destination(floors, world.block(0, 20, 0), 1, null, world.origin(20), IronElevatorLanding.DEFAULT_SIZE, _ -> false));
        world.put(0, 42, 0, Material.AIR, Set.of());
        world.put(0, 40, 0, Material.AIR, Set.of());
        assertNull(IronElevatorTestSupport.destination(floors, world.block(0, 20, 0), 1, null, world.origin(20), IronElevatorLanding.DEFAULT_SIZE, _ -> false));
    }

    @Test
    void doesNotReadUnloadedNeighborsOrReuseAnUnloadedShaft() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.iron(40);
        IronElevatorCatalog floors = new IronElevatorCatalog();
        IronElevatorTestSupport.warm(floors, world.block(0, 20, 0));
        world.unloadedChunks.add("0,0");
        assertNull(IronElevatorTestSupport.warm(floors, world.block(0, 20, 0)));
        world.unloadedChunks.clear();
        world.unloadedChunks.add("-1,0");
        floors.invalidate();
        assertNull(IronElevatorTestSupport.warm(floors, world.block(0, 20, 0)));
    }

    @Test
    void invalidatesOnlyTheAffectedShaftAndChunk() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int height : List.of(20, 40)) {
            world.iron(height);
            iron(world, 48, height, 0);
        }
        IronElevatorCatalog floors = new IronElevatorCatalog();
        IronElevatorTestSupport.warm(floors, world.block(0, 20, 0));
        IronElevatorTestSupport.warm(floors, world.block(48, 20, 0));
        iron(world, 1, 20, 0);
        floors.invalidate(world.block(1, 20, 0));
        int beforeRefresh = world.blockReads;
        IronElevatorTestSupport.warm(floors, world.block(48, 20, 0));
        assertEquals(beforeRefresh, world.blockReads);
        assertEquals(2, IronElevatorTestSupport.warm(floors, world.block(0, 20, 0)).blocks().size());
        assertTrue(world.blockReads > beforeRefresh);
        int refreshed = world.blockReads;
        floors.invalidateChunk(world.worldId, 0, 0);
        IronElevatorTestSupport.warm(floors, world.block(48, 20, 0));
        assertEquals(refreshed, world.blockReads);
        IronElevatorTestSupport.warm(floors, world.block(0, 20, 0));
        assertTrue(world.blockReads > refreshed);
    }

    @Test
    void evictsOldShaftsInsteadOfGrowingTheScanCacheIndefinitely() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        IronElevatorCatalog floors = new IronElevatorCatalog(() -> 0);
        for (int index = 0; index <= 64; index++) {
            iron(world, index * 16, 20, 0);
            IronElevatorTestSupport.warm(floors, world.block(index * 16, 20, 0));
        }
        int reads = world.blockReads;
        IronElevatorTestSupport.warm(floors, world.block(64 * 16, 20, 0));
        assertEquals(reads, world.blockReads);
        IronElevatorTestSupport.warm(floors, world.block(0, 20, 0));
        assertTrue(world.blockReads > reads);
    }

    @Test
    void warmShaftsPreserveTheSamePhysicalSourceAsColdDiscovery() {
        var world = new IronElevatorTestWorld();
        iron(world,0,20,0); iron(world,2,20,0);
        for (int x = 0; x < 3; x++) iron(world,x,40,0);
        var cold = IronElevatorTestSupport.platform(world.block(0,20,0));
        var shaft = IronElevatorTestSupport.shaft(world.block(0,20,0));
        assertEquals(2,shaft.at(20).platforms().size());
        assertEquals(cold,shaft.source(world.block(0,20,0)));
        assertEquals(1,shaft.source(world.block(0,20,0)).blocks().size());
        assertNotEquals(shaft.source(world.block(0,20,0)),shaft.source(world.block(2,20,0)));
    }

    private static void square(IronElevatorTestWorld world, int height) {
        for (int blockX = 0; blockX <= 1; blockX++) {
            for (int blockZ = 0; blockZ <= 1; blockZ++) iron(world, blockX, height, blockZ);
        }
    }

    private static void iron(IronElevatorTestWorld world, int blockX, int height, int blockZ) {
        world.put(blockX, height, blockZ, Material.IRON_BLOCK, Set.of(BlockFace.values()));
    }
}
