package org.encinet.mik.module.elevator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronElevatorWallTest {

    @Test
    void detectsFlatWallFacingElevatorInAllFourDirections() {
        for (BlockFace direction : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            IronElevatorTestWorld world = new IronElevatorTestWorld();
            world.iron(20);
            world.wall(20, direction);

            IronElevatorWall wall = IronElevatorWall.find(world.block(0, 20, 0));

            assertNotNull(wall, direction.toString());
            assertEquals(direction, wall.direction());
        }
    }

    @Test
    void rejectsMissingBlocksAndFacesPointingAwayFromElevator() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.wall(20, BlockFace.EAST);
        IronElevatorWall wall = new IronElevatorWall(world.block(0, 20, 0), BlockFace.EAST);
        world.put(1, 21, 0, Material.STONE, Set.of(BlockFace.EAST));
        assertFalse(wall.isFlat());
        world.put(1, 21, 0, Material.AIR, Set.of());
        assertFalse(wall.isFlat());
    }

    @Test
    void rejectsUnloadedWallChunksWithoutLoadingThem() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.wall(20, BlockFace.WEST);
        world.unloadedChunks.add("-1,0");

        assertNull(IronElevatorWall.find(world.block(0, 20, 0)));
    }

    @Test
    void projectsClicksOntoTheSameCoordinatesUsedForRendering() {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            IronElevatorTestWorld world = new IronElevatorTestWorld();
            IronElevatorWall wall = new IronElevatorWall(world.block(0, 20, 0), face);
            Location eye = world.origin(20).add(0, 1.62, 0);
            Location button = wall.position(1, -0.48);
            eye.setDirection(button.toVector().subtract(eye.toVector()));

            IronElevatorWall.Hit hit = wall.hit(eye);

            assertNotNull(hit, face.toString());
            assertEquals(1, hit.sideways(), 1.0E-6);
            assertEquals(-0.48, hit.above(), 1.0E-6);
        }
    }

    @Test
    void rejectsClicksFromBehindOtherWorldsOrTooFarAway() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        IronElevatorWall wall = new IronElevatorWall(world.block(0, 20, 0), BlockFace.EAST);
        Location behind = wall.position(0, 0).add(1, 0, 0);
        behind.setDirection(wall.position(0, 0).toVector().subtract(behind.toVector()));
        assertNull(wall.hit(behind));
        Location distant = wall.position(0, 0).add(-6, 0, 0);
        distant.setDirection(wall.position(0, 0).toVector().subtract(distant.toVector()));
        assertNull(wall.hit(distant));
        assertNull(wall.hit(new IronElevatorTestWorld().origin(20)));
    }

    @Test
    void alwaysPrefersAnAdjacentWallOverAFartherWall() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.wall(20, BlockFace.NORTH, 2);
        world.wall(20, BlockFace.WEST);
        var wall = IronElevatorWall.find(IronElevatorPlatform.resolve(world.block(0, 20, 0)));
        assertNotNull(wall);
        assertEquals(1, wall.offset());
        assertEquals(BlockFace.WEST, wall.direction());
    }

    @Test
    void searchesOnlyThreeAdditionalRingsAndKeepsThePanelOnTheActualWall() {
        for (int offset = 2; offset <= 5; offset++) {
            IronElevatorTestWorld world = new IronElevatorTestWorld();
            world.iron(20);
            world.wall(20, BlockFace.EAST, offset);
            var wall = IronElevatorWall.find(IronElevatorPlatform.resolve(world.block(0, 20, 0)));
            if (offset == 5) {
                assertNull(wall);
            } else {
                assertNotNull(wall);
                assertEquals(offset, wall.offset());
                assertEquals(offset - 0.02, wall.position(0, 0).getX(), 1.0E-6);
                Location eye = world.origin(20).add(0, 1.62, 0);
                eye.setDirection(wall.position(0, 0.3).toVector().subtract(eye.toVector()));
                assertNotNull(wall.hit(eye));
            }
        }
    }

    @Test
    void centersThePanelAlongTheAdjacentFaceInsteadOfChoosingAnOuterCorner() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int blockX = 0; blockX < 5; blockX++)
            world.put(blockX, 20, 0, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        for (int blockX = -1; blockX <= 5; blockX++) {
            for (int height = 21; height <= 22; height++)
                world.put(blockX, height, -1, Material.STONE, Set.of(BlockFace.SOUTH));
        }
        var first = IronElevatorWall.find(IronElevatorPlatform.resolve(world.block(0, 20, 0)));
        var second = IronElevatorWall.find(IronElevatorPlatform.resolve(world.block(4, 20, 0)));
        assertNotNull(first);
        assertEquals(first, second);
        assertEquals(2.5, first.position(0, 0).getX());
        assertEquals(BlockFace.NORTH, first.direction());
        assertEquals(1, first.offset());
    }

    @Test
    void acceptsTwoWideThreeHighWallsWithoutLiftingThePanelToTheWallCenter() {
        for (BlockFace direction : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            IronElevatorTestWorld world = new IronElevatorTestWorld();
            for (int sideways = 0; sideways <= 1; sideways++) {
                for (int above = 1; above <= 3; above++)
                    wallCell(world, direction, sideways, above);
            }
            var wall = wall(world, direction, 0, 1);
            var surface = wall.surface();
            assertEquals(new IronElevatorWall.Surface(0, 1, 3), surface);
            var layout = surface.layout(20);
            assertFalse(layout.portrait());
            assertEquals(0.5, layout.centerSideways());
            assertEquals(0.6, layout.centerAbove());
        }
    }

    @Test
    void excludesCabinSideWallsEvenWhenTheBackingWallIsWider() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int sideways = -3; sideways <= 3; sideways++) {
            for (int above = 1; above <= 2; above++) {
                wallCell(world, BlockFace.EAST, sideways, above);
                if (sideways < 0 || sideways > 1)
                    world.put(0, 20 + above, sideways, Material.STONE, Set.of(BlockFace.values()));
            }
        }
        var wall = wall(world, BlockFace.EAST, -3, 3);
        var surface = wall.surface();
        assertEquals(new IronElevatorWall.Surface(0, 1, 2), surface);
        assertFalse(surface.layout(20).portrait());
        assertEquals(0.5, surface.layout(20).centerSideways());
    }

    @Test
    void stopsAtTheCabinCeilingInsteadOfUsingTheWallAboveIt() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int sideways = 0; sideways <= 1; sideways++) {
            for (int above = 1; above <= 6; above++)
                wallCell(world, BlockFace.EAST, sideways, above);
            world.put(0, 23, sideways, Material.STONE, Set.of(BlockFace.values()));
        }
        var wall = wall(world, BlockFace.EAST, 0, 1);
        assertEquals(new IronElevatorWall.Surface(0, 1, 2), wall.surface());
    }

    @Test
    void usesACompleteRectangleRatherThanSpanningHolesInTheWall() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int sideways = -1; sideways <= 1; sideways++) {
            for (int above = 1; above <= 3; above++)
                wallCell(world, BlockFace.EAST, sideways, above);
        }
        world.put(1, 22, -1, Material.AIR, Set.of());
        var wall = wall(world, BlockFace.EAST, -1, 1);
        assertEquals(new IronElevatorWall.Surface(0, 1, 3), wall.surface());
    }

    @Test
    void limitsWallMeasurementsAndSkipsUnloadedEdgeChunks() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int sideways = -4; sideways <= 4; sideways++) {
            for (int above = 1; above <= 7; above++)
                wallCell(world, BlockFace.EAST, sideways, above);
        }
        var wall = wall(world, BlockFace.EAST, -3, 3);
        assertEquals(new IronElevatorWall.Surface(-3, 3, 3), wall.surface());
        world.unloadedChunks.add("0,-1");
        world.blockReads = 0;
        assertEquals(new IronElevatorWall.Surface(0, 3, 3), wall.surface());
        assertTrue(world.blockReads <= 42);
    }

    @Test
    void doesNotFollowAnUnboundedWallBeyondTheIronFootprint() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int sideways = -10; sideways <= 10; sideways++) {
            for (int above = 1; above <= 6; above++)
                wallCell(world, BlockFace.EAST, sideways, above);
        }
        var wall = wall(world, BlockFace.EAST, 0, 1);
        assertEquals(new IronElevatorWall.Surface(0, 1, 3), wall.surface());
        var layout = wall.surface().layout(60, wall.preferredSideways());
        assertEquals(0.5, layout.centerSideways());
        assertTrue(layout.centerSideways() - layout.width() / 2 >= -0.5);
        assertTrue(layout.centerSideways() + layout.width() / 2 <= 1.5);
    }

    @Test
    void doesNotSpanAnIndentationInAnIrregularPlatform() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.put(0, 20, 1, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        world.put(0, 20, 2, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        world.put(1, 20, 1, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        for (int sideways = 0; sideways <= 2; sideways++) {
            for (int above = 1; above <= 3; above++)
                wallCell(world, BlockFace.EAST, sideways, above);
        }
        var footprint = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        var wall = new IronElevatorWall(world.block(0, 20, 0), BlockFace.EAST, 1, footprint);
        assertEquals(new IronElevatorWall.Surface(0, 0, 3), wall.surface());
    }

    @Test
    void projectsTheTruePlatformCenterOntoTheWallInsteadOfUsingTheRectangleCenter() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int sideways = 0; sideways <= 2; sideways++) {
            world.put(0, 20, sideways, Material.IRON_BLOCK, Set.of(BlockFace.values()));
            for (int above = 1; above <= 3; above++)
                wallCell(world, BlockFace.EAST, sideways, above);
        }
        world.put(-1, 20, 0, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        world.put(-2, 20, 0, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        var footprint = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        var wall = new IronElevatorWall(world.block(0, 20, 0), BlockFace.EAST, 1, footprint);
        var layout = wall.surface().layout(7, wall.preferredSideways());
        assertEquals(0.6, layout.centerSideways(), 1.0E-9);
        assertEquals(footprint.centerZ(), wall.position(layout.centerSideways(), 0).getZ(), 1.0E-9);
    }

    @Test
    void rejectsFallbackWallsBehindAnObstacleAnywhereAlongTheSearchRay() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.wall(20, BlockFace.EAST, 4);
        world.put(1, 21, 0, Material.STONE, Set.of(BlockFace.values()));
        assertNull(IronElevatorWall.find(IronElevatorPlatform.resolve(world.block(0, 20, 0))));
    }

    @Test
    void keepsTwoByTwoCabinPanelsInsideTheFootprintAndCenteredInAllDirections() {
        for (BlockFace direction : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            IronElevatorTestWorld world = new IronElevatorTestWorld();
            for (int blockX = 0; blockX <= 1; blockX++) {
                for (int blockZ = 0; blockZ <= 1; blockZ++)
                    world.put(blockX, 20, blockZ, Material.IRON_BLOCK, Set.of(BlockFace.values()));
            }
            int wallCoordinate = direction.getModX() + direction.getModZ() > 0 ? 2 : -1;
            for (int sideways = -10; sideways <= 10; sideways++) {
                for (int above = 1; above <= 6; above++)
                    world.put(direction.getModX() == 0 ? sideways : wallCoordinate, 20 + above,
                            direction.getModZ() == 0 ? sideways : wallCoordinate,
                            Material.STONE, Set.of(direction.getOppositeFace()));
            }
            var first = IronElevatorWall.findMount(IronElevatorPlatform.resolve(world.block(0, 20, 0)));
            var second = IronElevatorWall.findMount(IronElevatorPlatform.resolve(world.block(1, 20, 1)));
            assertNotNull(first);
            assertEquals(first, second);
            assertEquals(direction, first.wall().direction());
            assertEquals(2, first.surface().width());
            var layout = first.layout(20);
            Location center = first.wall().position(layout.centerSideways(), layout.centerAbove());
            assertEquals(1, direction.getModX() == 0 ? center.getX() : center.getZ(), 1.0E-9);
            assertTrue(layout.width() < 2);
            assertTrue(center.getY() - layout.height() / 2 >= 22);
            assertTrue(center.getY() + layout.height() / 2 <= 23.6);
        }
    }

    @Test
    void limitsFallbackPanelsToTheSameProjectedIronEdge() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.put(0, 20, 1, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        for (int sideways = -10; sideways <= 10; sideways++) {
            for (int above = 1; above <= 3; above++)
                world.put(4, 20 + above, sideways, Material.STONE, Set.of(BlockFace.WEST));
        }
        var mount = IronElevatorWall.findMount(IronElevatorPlatform.resolve(world.block(0, 20, 0)));
        assertNotNull(mount);
        assertEquals(4, mount.wall().offset());
        assertEquals(2, mount.surface().width());
        var layout = mount.layout(7);
        assertEquals(1, mount.wall().position(layout.centerSideways(), 0).getZ(), 1.0E-9);
        assertTrue(layout.width() < 2);
    }

    private static IronElevatorWall wall(IronElevatorTestWorld world, BlockFace direction,
                                         int firstColumn, int lastColumn) {
        for (int sideways = firstColumn; sideways <= lastColumn; sideways++)
            world.put(-direction.getModZ() * sideways, 20, direction.getModX() * sideways,
                    Material.IRON_BLOCK, Set.of(BlockFace.values()));
        var footprint = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        return new IronElevatorWall(world.block(0, 20, 0), direction, 1, footprint);
    }

    private static void wallCell(IronElevatorTestWorld world, BlockFace direction, int sideways, int above) {
        world.put(direction.getModX() - direction.getModZ() * sideways, 20 + above,
                direction.getModZ() + direction.getModX() * sideways, Material.STONE,
                Set.of(direction.getOppositeFace()));
    }
}
