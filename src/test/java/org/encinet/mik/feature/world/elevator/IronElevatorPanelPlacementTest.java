package org.encinet.mik.feature.world.elevator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.encinet.mik.feature.world.elevator.IronElevatorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class IronElevatorPanelPlacementTest {
    @Test void choosesStableExposedEdgesAndSharesCompletedWorkAcrossSourceBlocks() {
        var world = square();
        var resolver = new IronElevatorPanelPlacement();
        var source = platform(world.block(0,20,0));
        var neighbor = platform(world.block(1,20,1));
        var first = placement(resolver,source);
        int reads = world.blockReads;
        var second = placement(resolver,neighbor);
        assertNotNull(first);
        assertSame(first,second);
        assertEquals(reads,world.blockReads);
        assertEquals(.25,first.surface().scale());
        assertTrue(first.valid());
    }
    @Test void prefersWallsInEveryDirectionAndAllFourSearchRings() {
        for (var direction : List.of(BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST)) {
            for (int distance = 1; distance <= 4; distance++) {
                var world = new IronElevatorTestWorld(); world.iron(20); world.wall(20,direction,distance);
                var plan = placement(new IronElevatorPanelPlacement(),platform(world.block(0,20,0)));
                assertNotNull(plan);
                assertEquals(.5 + direction.getModX() * (distance - .5),plan.surface().center().getX(),1e-8);
                assertEquals(.5 + direction.getModZ() * (distance - .5),plan.surface().center().getZ(),1e-8);
                assertTrue(plan.valid());
            }
        }
    }
    @Test void prefersAnAdjacentWallAndKeepsSurfaceWithinTheIronFootprint() {
        var world = square(); world.wall(20,BlockFace.NORTH,4);
        for (int z = 0; z < 2; z++) for (int y = 21; y <= 22; y++) world.put(2,y,z,Material.STONE,Set.of(BlockFace.WEST));
        var plan = placement(new IronElevatorPanelPlacement(),platform(world.block(0,20,0)));
        assertNotNull(plan);
        assertEquals(UiRotation.degrees(90,0,0),plan.surface().rotation());
        assertTrue(plan.surface().size().width() <= 2);
        assertTrue(plan.surface().center().getY() + plan.surface().size().height() / 2 <= 23.6);
    }
    @Test void blockedRaysAndWrongBackingFacesFallBackToAnOpenEdge() {
        for (boolean wrongFace : List.of(false,true)) {
            var world = new IronElevatorTestWorld(); world.iron(20); world.wall(20,BlockFace.NORTH,3);
            if (wrongFace) world.put(0,22,-3,Material.STONE,Set.of(BlockFace.NORTH));
            else world.put(0,22,-1,Material.STONE,Set.of());
            var plan = placement(new IronElevatorPanelPlacement(),platform(world.block(0,20,0)));
            assertNotNull(plan);
            assertTrue(Math.abs(plan.surface().center().getZ() - .5) <= .5);
        }
    }
    @Test void keepsRectanglesInsideIndentationsAndWallHoles() {
        var world = new IronElevatorTestWorld();
        for (int x = 0; x < 5; x++) { world.put(x,20,0,Material.IRON_BLOCK,Set.of(BlockFace.UP)); world.put(x,20,1,Material.IRON_BLOCK,Set.of(BlockFace.UP)); }
        world.put(2,20,0,Material.AIR,Set.of());
        for (int x = 0; x < 5; x++) for (int y = 21; y <= 23; y++) world.put(x,y,-1,Material.STONE,Set.of(BlockFace.SOUTH));
        var plan = placement(new IronElevatorPanelPlacement(),platform(world.block(0,20,1)));
        assertNotNull(plan);
        assertTrue(plan.surface().size().width() < 2);
        assertTrue(plan.valid());
        world.put(1,22,-1,Material.AIR,Set.of());
        assertFalse(plan.valid());
    }
    @Test void mountValidationRejectsNewObstaclesAndExpandedEdges() {
        for (boolean expand : List.of(false,true)) {
            var world = new IronElevatorTestWorld(); world.iron(20);
            var plan = placement(new IronElevatorPanelPlacement(),platform(world.block(0,20,0)));
            assertTrue(plan.valid());
            if (expand) world.put(0,20,-1,Material.IRON_BLOCK,Set.of(BlockFace.UP));
            else world.put(0,22,-1,Material.STONE,Set.of());
            assertFalse(plan.valid());
        }
    }
    @Test void neverReadsUnloadedChunksAndCanUseAnotherLoadedEdge() {
        var world = new IronElevatorTestWorld(); world.iron(20);
        var source = platform(world.block(0,20,0));
        world.unloadedChunks.add("0,-1");
        var plan = placement(new IronElevatorPanelPlacement(),source);
        assertNotNull(plan);
        assertTrue(plan.valid());
        assertTrue(plan.surface().center().getZ() >= 0);
    }
    @Test void cacheExpiryDoesNotSlideAndNewWallsInvalidateFailedSearchRegions() {
        var world = new IronElevatorTestWorld(); world.iron(20);
        var clock = new AtomicLong(); var resolver = new IronElevatorPanelPlacement(clock::get);
        var source = platform(world.block(0,20,0)); var first = placement(resolver,source);
        clock.set(1_900_000_000L); assertSame(first,placement(resolver,source));
        clock.set(2_000_000_001L); assertNotSame(first,placement(resolver,source));
        world.wall(20,BlockFace.NORTH,3);
        resolver.invalidate(world.block(0,22,-3));
        assertEquals(-2,placement(resolver,source).surface().center().getZ(),1e-8);
    }
    @Test void cachesMissingSpaceAndRelevantChangesRetryIt() {
        var world = new IronElevatorTestWorld(); world.iron(20);
        for (var direction : List.of(BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST))
            world.put(direction.getModX(),22,direction.getModZ(),Material.STONE,Set.of());
        var resolver = new IronElevatorPanelPlacement(() -> 0); var source = platform(world.block(0,20,0));
        assertNull(placement(resolver,source)); int reads = world.blockReads;
        assertNull(placement(resolver,source)); assertEquals(reads,world.blockReads);
        world.put(0,22,-1,Material.AIR,Set.of()); resolver.invalidate(world.block(0,22,-1));
        assertNotNull(placement(resolver,source));
    }
    @Test void cancellationAndInvalidationNeverPublishPartialMounts() {
        var world = square(); var resolver = new IronElevatorPanelPlacement();
        var queue = new IronElevatorWorkQueue(() -> 0,error -> fail(error)); var source = platform(world.block(0,20,0));
        var cancelled = resolver.prepare(source,queue,_ -> fail("cancelled mount"));
        cancelled.cancel(); queue.tick(); assertTrue(queue.empty());
        resolver.prepare(source,queue,_ -> fail("invalidated mount"));
        resolver.invalidate(world.block(1,22,0)); queue.tick(); assertTrue(queue.empty());
    }
    @Test void chunkWorldAndLruBoundariesDoNotEvictUnrelatedMounts() {
        var clock = new AtomicLong(); var resolver = new IronElevatorPanelPlacement(clock::get);
        var world = new IronElevatorTestWorld(); world.iron(20);
        var source = platform(world.block(0,20,0)); var first = placement(resolver,source);
        var other = new IronElevatorTestWorld(); other.iron(20);
        resolver.invalidate(other.block(0,22,0)); resolver.invalidateChunk(world.worldId,50,50);
        assertSame(first,placement(resolver,source));
        resolver.invalidateChunk(world.worldId,0,0); assertNotSame(first,placement(resolver,source));
        var retained = placement(resolver,source);
        for (int index = 1; index <= 64; index++) {
            var extra = new IronElevatorTestWorld(); extra.iron(20);
            assertNotNull(placement(resolver,platform(extra.block(0,20,0))));
        }
        assertNotSame(retained,placement(resolver,source));
        resolver.clear(); assertNotSame(retained,placement(resolver,source));
    }
    private static IronElevatorTestWorld square() {
        var world = new IronElevatorTestWorld();
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) world.put(x,20,z,Material.IRON_BLOCK,Set.of(BlockFace.UP));
        return world;
    }
}
