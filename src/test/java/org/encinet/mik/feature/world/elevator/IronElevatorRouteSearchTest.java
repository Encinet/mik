package org.encinet.mik.feature.world.elevator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class IronElevatorRouteSearchTest {
    @Test
    void directionalQueriesMatchCompleteTopologyForIrregularAndDisconnectedPlatforms() {
        var random = new Random(0x4d494b);
        for (int scenario = 0; scenario < 60; scenario++) {
            var world = new IronElevatorTestWorld();
            for (int x = 0; x < 2; x++)
                for (int z = 0; z < 2; z++) iron(world, x, 40, z);
            for (int y : List.of(10, 20, 60, 80, 100)) {
                for (int x = -2; x < 4; x++) {
                    for (int z = -2; z < 4; z++) {
                        if (random.nextBoolean()) iron(world, x, y, z);
                        if (random.nextInt(9) == 0)
                            world.put(x, y + 1, z, Material.STONE, Set.of(BlockFace.UP));
                    }
                }
            }
            var source = world.block(0, 40, 0);
            var origin = world.origin(40);
            Predicate<IronElevatorShaft.Level> fits = target -> IronElevatorLanding.on(target, origin,
                    IronElevatorLanding.DEFAULT_SIZE, _ -> false) != null;
            var shaft = IronElevatorTestSupport.shaft(source);
            var query = new IronElevatorRouteSearch(source, List.of(new IronElevatorPlatform.Column(0, 0)), fits, null, null);
            finish(query);
            for (double x : List.of(0.5, 0.25, 0.75)) {
                origin.setX(x);
                query.reevaluate(fits);
                finish(query);
                for (int direction : List.of(-1, 1)) {
                    Location expected = IronElevatorTestSupport.expectedDestination(shaft, 40, direction, null,
                            target -> fits.test(target) ? world.origin(target.height()) : null);
                    var actual = direction > 0 ? query.up() : query.down();
                    assertEquals(expected == null ? null : (int) expected.getY() - 1,
                            actual == null ? null : actual.height());
                }
            }
        }
    }

    @Test
    void combinesSeparateSupportPlatformsForMultipleTravellingRoots() {
        var world = new IronElevatorTestWorld();
        for (int x = 0; x < 3; x++) iron(world, x, 20, 0);
        iron(world, 0, 40, 0);
        iron(world, 2, 40, 0);
        var first = world.origin(20);
        var second = new Location(world.world, 2.5, 21, 0.5);
        var query = new IronElevatorRouteSearch(world.block(0, 20, 0),
                List.of(new IronElevatorPlatform.Column(0, 0), new IronElevatorPlatform.Column(2, 0)),
                target -> IronElevatorLanding.on(target, first, IronElevatorLanding.DEFAULT_SIZE, _ -> false) != null
                        && IronElevatorLanding.on(target, second, IronElevatorLanding.DEFAULT_SIZE, _ -> false) != null,
                null, null);
        finish(query);
        assertNotNull(query.up());
        assertEquals(2, query.up().blocks().size());
        assertNull(query.down());
    }

    @Test
    void requestedDirectionCanCompleteWhileTheOtherDirectionStillNeedsScanning() {
        var world = new IronElevatorTestWorld();
        world.iron(20);
        world.iron(24);
        var query = new IronElevatorRouteSearch(world.block(0, 20, 0),
                List.of(new IronElevatorPlatform.Column(0, 0)), _ -> true, null, null);
        int steps = 0;
        while (!query.ready(1)) {
            query.step(1);
            assertTrue(++steps < 30);
        }
        assertEquals(24, query.up().height());
        assertFalse(query.ready(-1));
    }

    @Test
    void warmQueriesSkipNonFloorHeightsAndSelectExactTargets() {
        var world = new IronElevatorTestWorld();
        world.iron(20);
        world.iron(100);
        world.iron(300);
        var source = world.block(0, 100, 0);
        var shaft = IronElevatorTestSupport.shaft(source);
        int reads = world.blockReads;
        var query = new IronElevatorRouteSearch(source, List.of(new IronElevatorPlatform.Column(0, 0)),
                _ -> true, null, shaft);
        finish(query);
        assertEquals(reads, world.blockReads);
        assertEquals(20, query.down().height());
        assertEquals(300, query.up().height());
        var exact = new IronElevatorRouteSearch(source, List.of(new IronElevatorPlatform.Column(0, 0)),
                _ -> true, 20, shaft);
        finish(exact);
        assertEquals(20, exact.up().height());
        assertNull(exact.down());
    }

    @Test
    void rechecksRejectedCandidatesWithoutReadingTheirGeometryAgain() {
        var world = new IronElevatorTestWorld();
        for (int height : List.of(20, 40, 80)) world.iron(height);
        var query = new IronElevatorRouteSearch(world.block(0, 40, 0),
                List.of(new IronElevatorPlatform.Column(0, 0)), target -> target.height() > 40, null, null);
        finish(query);
        assertNull(query.down());
        int reads = world.blockReads;

        query.reevaluate(_ -> true);
        finish(query);
        assertEquals(20, query.down().height());
        assertEquals(reads, world.blockReads, "changing landing rules must reuse discovered geometry");
    }

    @Test
    void resumesScanningBeyondAnUnsafePreviousResultAndCanReturnToANearerCandidate() {
        var world = new IronElevatorTestWorld();
        for (int height : List.of(20, 40, 80)) world.iron(height);
        var query = new IronElevatorRouteSearch(world.block(0, 20, 0),
                List.of(new IronElevatorPlatform.Column(0, 0)), _ -> true, null, null);
        finish(query);
        assertEquals(40, query.up().height());
        query.reevaluate(target -> target.height() == 80);
        finish(query);
        assertEquals(80, query.up().height());
        int reads = world.blockReads;
        query.reevaluate(_ -> true);
        finish(query);
        assertEquals(40, query.up().height());
        assertEquals(reads, world.blockReads);
    }

    private static void iron(IronElevatorTestWorld world, int x, int y, int z) {
        world.put(x, y, z, Material.IRON_BLOCK, Set.of(BlockFace.UP));
    }

    private static void finish(IronElevatorRouteSearch query) {
        int steps = 0;
        while (!query.step()) assertTrue(++steps < 10_000);
    }
}
