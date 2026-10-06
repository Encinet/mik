package org.encinet.mik.module.plot;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotMiniatureMesherTest {
    private final BlockData stone = block(Material.STONE, "minecraft:stone");

    @Test
    void continuousGroundKeepsFullCoverageWithoutStretchingOneTextureAcrossTheWholePlot() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(-20, 64, -20, 19, 64, 19));
        BlockData[] blocks = new BlockData[grid.count()];
        Arrays.fill(blocks, stone);
        var result = PlotMiniatureMesher.mesh(grid, blocks, PlotMiniatureSampler.MAX_TERRAIN);
        assertFalse(result.limited());
        assertEquals(25, result.terrain().size());
        var terrain = result.terrain().getFirst();
        assertSame(stone, terrain.block());
        assertTrue(result.terrain().stream().allMatch(voxel -> voxel.box().width() <= PlotMiniatureMesher.MAX_TILE_SIZE
                && voxel.box().depth() <= PlotMiniatureMesher.MAX_TILE_SIZE));
        assertCoverage(grid, blocks, result);
    }

    @Test
    void hollowRoomKeepsItsInteriorAndEveryWallCellWithoutOverlappingMeshes() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 0, 0, 15, 15, 15));
        BlockData[] blocks = new BlockData[grid.count()];
        for (int index = 0; index < blocks.length; index++) {
            int column = grid.column(index);
            int row = grid.row(index);
            int depth = grid.depth(index);
            if (column == 0 || column == 15 || row == 0 || row == 15 || depth == 0 || depth == 15)
                blocks[index] = stone;
        }
        var result = PlotMiniatureMesher.mesh(grid, blocks, PlotMiniatureSampler.MAX_TERRAIN);
        assertFalse(result.limited());
        assertTrue(result.terrain().size() <= 32);
        assertCoverage(grid, blocks, result);
    }

    @Test
    void differentStatesOfTheSameMaterialAreNotMerged() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 0, 0, 7, 0, 0));
        BlockData first = block(Material.OAK_LOG, "minecraft:oak_log[axis=y]");
        BlockData second = block(Material.OAK_LOG, "minecraft:oak_log[axis=x]");
        BlockData[] blocks = new BlockData[grid.count()];
        Arrays.fill(blocks, 0, 4, first);
        Arrays.fill(blocks, 4, 8, second);
        var result = PlotMiniatureMesher.mesh(grid, blocks, PlotMiniatureSampler.MAX_TERRAIN);
        assertEquals(2, result.terrain().size());
        assertSame(first, result.terrain().getFirst().block());
        assertSame(second, result.terrain().getLast().block());
        assertCoverage(grid, blocks, result);
    }

    @Test
    void coarseStairsRailsAndFencesKeepTheirActualSizeAndBlockState() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(-256, 64, -256, -1, 79, -1));
        BlockData[] blocks = new BlockData[grid.count()];
        blocks[0] = block(Material.OAK_STAIRS, "minecraft:oak_stairs[facing=east,half=top]");
        blocks[1] = block(Material.RAIL, "minecraft:rail[shape=north_south]");
        blocks[2] = block(Material.OAK_FENCE, "minecraft:oak_fence[east=true]");
        var result = PlotMiniatureMesher.mesh(grid, blocks, PlotMiniatureSampler.MAX_TERRAIN);
        assertEquals(3, result.terrain().size());
        for (var voxel : result.terrain()) {
            assertEquals(grid.block(voxel.index()), voxel.box());
            assertSame(blocks[voxel.index()], voxel.block());
        }
    }

    @Test
    void irregularTerracesDoNotBridgeAirOrUnknownGaps() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(-4, -5, -4, 3, -1, 3));
        BlockData[] blocks = new BlockData[grid.count()];
        Random random = new Random(517);
        for (int index = 0; index < blocks.length; index++) {
            if (random.nextBoolean() && grid.column(index) % 2 == 0) blocks[index] = stone;
        }
        var result = PlotMiniatureMesher.mesh(grid, blocks, grid.count());
        assertFalse(result.limited());
        assertCoverage(grid, blocks, result);
    }

    @Test
    void displayBudgetKeepsLargeSurfacesAndReportsSimplificationDeterministically() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 0, 0, 31, 2, 31));
        BlockData[] blocks = new BlockData[grid.count()];
        for (int index = 0; index < blocks.length; index++) {
            if (grid.row(index) == 0 || grid.row(index) == 2 && (grid.column(index) + grid.depth(index)) % 2 == 0)
                blocks[index] = stone;
        }
        var result = PlotMiniatureMesher.mesh(grid, blocks, PlotMiniatureSampler.MAX_TERRAIN);
        assertTrue(result.limited());
        assertEquals(PlotMiniatureSampler.MAX_TERRAIN, result.terrain().size());
        assertEquals(1024, result.terrain().stream().filter(voxel -> voxel.box().worldY() == 0.5)
                .mapToDouble(voxel -> voxel.box().width() * voxel.box().depth()).sum());
        assertEquals(result, PlotMiniatureMesher.mesh(grid, blocks, PlotMiniatureSampler.MAX_TERRAIN));
    }

    @Test
    void buriedSolidBlocksDoNotUseTheBudgetBeforeWindowsAndSurfaceDetails() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 0, 0, 31, 31, 31));
        BlockData[] blocks = new BlockData[grid.count()];
        Arrays.fill(blocks, stone);
        var result = PlotMiniatureMesher.mesh(grid, blocks, PlotMiniatureSampler.MAX_TERRAIN);
        assertFalse(result.limited());
        for (var voxel : result.terrain()) {
            var box = voxel.box();
            assertTrue(box.worldX() - box.width() / 2 == 0 || box.worldX() + box.width() / 2 == 32
                    || box.worldY() - box.height() / 2 == 0 || box.worldY() + box.height() / 2 == 32
                    || box.worldZ() - box.depth() / 2 == 0 || box.worldZ() + box.depth() / 2 == 32);
        }
    }

    @Test
    void courtyardGroundUsesLargerTilesBeforeDiscardingAnySampledSurface() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 0, 0, 63, 0, 63));
        BlockData[] blocks = new BlockData[grid.count()];
        for (int index = 0; index < blocks.length; index++) {
            int column = grid.column(index);
            int depth = grid.depth(index);
            if (column < 24 || column >= 40 || depth < 24 || depth >= 40) blocks[index] = stone;
        }
        var result = PlotMiniatureMesher.mesh(grid, blocks, 32);
        assertTrue(result.terrain().size() <= 32);
        assertFalse(result.limited());
        assertCoverage(grid, blocks, result);
    }

    @Test
    void sparseCityDistrictsSurviveBesideADenseDecoratedPlaza() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 0, 0, 31, 0, 31));
        BlockData[] blocks = new BlockData[grid.count()];
        BlockData detail = block(Material.OAK_STAIRS, "minecraft:oak_stairs[facing=east]");
        for (int depth = 8; depth < 24; depth++) {
            for (int column = 8; column < 24; column++) blocks[grid.index(column, 0, depth)] = detail;
        }
        int[] districts = {grid.index(0, 0, 0), grid.index(31, 0, 0),
                grid.index(0, 0, 31), grid.index(31, 0, 31)};
        for (int index : districts) blocks[index] = detail;
        var result = PlotMiniatureMesher.mesh(grid, blocks, 12);
        assertTrue(result.limited());
        assertEquals(12, result.terrain().size());
        for (int index : districts) assertRetained(result, index);
        assertEquals(result, PlotMiniatureMesher.mesh(grid, blocks, 12));
    }

    @Test
    void towerSpireAndRaisedWalkwaySurviveADenseLowRiseBase() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 0, 0, 7, 31, 7));
        BlockData[] blocks = new BlockData[grid.count()];
        BlockData glass = block(Material.GLASS, "minecraft:glass");
        for (int row = 0; row < 8; row++) {
            for (int depth = 0; depth < 8; depth++) {
                for (int column = 0; column < 8; column++) blocks[grid.index(column, row, depth)] = glass;
            }
        }
        int spire = grid.index(7, 31, 7);
        int walkway = grid.index(7, 16, 0);
        blocks[spire] = glass;
        blocks[walkway] = glass;
        var result = PlotMiniatureMesher.mesh(grid, blocks, 16);
        assertEquals(16, result.terrain().size());
        assertRetained(result, spire);
        assertRetained(result, walkway);
        assertTrue(result.terrain().stream().anyMatch(voxel -> grid.row(voxel.index()) < 8));
        assertTrue(result.terrain().stream().allMatch(voxel -> voxel.box().width() == 1
                && voxel.box().height() == 1 && voxel.box().depth() == 1));
    }

    @Test
    void linearBridgesAndVerticalMastsKeepTheirTipsAndDistributedSegments() {
        BlockData rail = block(Material.RAIL, "minecraft:rail[shape=north_south]");
        for (int axis = 0; axis < 3; axis++) {
            var bounds = new PlotSelection.Bounds(0, 0, 0, axis == 0 ? 63 : 3,
                    axis == 1 ? 63 : 3, axis == 2 ? 63 : 3);
            var grid = new PlotMiniatureGeometry.Grid(bounds);
            BlockData[] blocks = new BlockData[grid.count()];
            for (int index = 0; index < blocks.length; index++) {
                int along = coordinate(grid, index, axis);
                if (along < 8 || grid.column(index) == (axis == 0 ? along : 2)
                        && grid.row(index) == (axis == 1 ? along : 2)
                        && grid.depth(index) == (axis == 2 ? along : 2)) blocks[index] = rail;
            }
            var result = PlotMiniatureMesher.mesh(grid, blocks, 16);
            assertEquals(16, result.terrain().size());
            assertRetained(result, grid.index(axis == 0 ? 63 : 2, axis == 1 ? 63 : 2, axis == 2 ? 63 : 2));
            int direction = axis;
            for (int section = 1; section < 4; section++) {
                int minimum = section * 16;
                assertTrue(result.terrain().stream().anyMatch(voxel -> coordinate(grid, voxel.index(), direction)
                        >= minimum && coordinate(grid, voxel.index(), direction) < minimum + 16));
            }
        }
    }

    @Test
    void zeroBudgetAndEmptyScenesRemainBounded() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 0, 0, 0, 0, 0));
        assertEquals(new PlotMiniatureMesher.Result(List.of(), false),
                PlotMiniatureMesher.mesh(grid, new BlockData[1], 0));
        var result = PlotMiniatureMesher.mesh(grid, new BlockData[] {stone}, 0);
        assertTrue(result.limited());
        assertTrue(result.terrain().isEmpty());
    }

    @Test
    void mixedHeightCityKeepsEveryDistrictRoofWithoutFillingItsStreets() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(0, 0, 0, 47, 11, 47));
        BlockData[] blocks = new BlockData[grid.count()];
        int[][] buildings = {{2, 2, 3}, {34, 2, 6}, {2, 34, 10}, {34, 34, 4}};
        for (int index = 0; index < blocks.length; index++) {
            int column = grid.column(index);
            int row = grid.row(index);
            int depth = grid.depth(index);
            if (row == 0) blocks[index] = stone;
            for (int[] building : buildings) {
                if (column >= building[0] && column < building[0] + 8
                        && depth >= building[1] && depth < building[1] + 8 && row <= building[2]
                        && (column == building[0] || column == building[0] + 7
                        || depth == building[1] || depth == building[1] + 7 || row == building[2]))
                    blocks[index] = stone;
            }
        }
        BlockData detail = block(Material.OAK_FENCE, "minecraft:oak_fence[east=true]");
        for (int depth = 16; depth < 32; depth++) {
            for (int column = 16; column < 32; column++) blocks[grid.index(column, 1, depth)] = detail;
        }
        var result = PlotMiniatureMesher.mesh(grid, blocks, 64);
        assertTrue(result.limited());
        assertEquals(64, result.terrain().size());
        for (int[] building : buildings) {
            assertTrue(result.terrain().stream().anyMatch(voxel -> {
                var box = voxel.box();
                return box.worldY() + box.height() / 2 == building[2] + 1
                        && box.worldX() >= building[0] && box.worldX() < building[0] + 8
                        && box.worldZ() >= building[1] && box.worldZ() < building[1] + 8;
            }), "Roof at " + Arrays.toString(building));
        }
        assertNoInventedSamples(grid, blocks, result);
    }

    @Test
    void irregularSteppedRoofsKeepSampledVoidsAndMaterialBoundariesWhenSimplified() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(-16, -3, -16, 15, 2, 15));
        BlockData[] blocks = new BlockData[grid.count()];
        BlockData wood = block(Material.OAK_LOG, "minecraft:oak_log[axis=y]");
        BlockData glass = block(Material.GLASS, "minecraft:glass");
        for (int index = 0; index < blocks.length; index++) {
            int column = grid.column(index);
            int depth = grid.depth(index);
            if (column == 8 || depth == 8 || column >= 12 && column < 20 && depth >= 12 && depth < 20) continue;
            if (grid.row(index) <= (column + depth) % 6)
                blocks[index] = (column + depth) % 3 == 0 ? glass : column % 2 == 0 ? stone : wood;
        }
        var result = PlotMiniatureMesher.mesh(grid, blocks, 32);
        assertTrue(result.limited());
        assertEquals(32, result.terrain().size());
        assertNoInventedSamples(grid, blocks, result);
        assertEquals(result, PlotMiniatureMesher.mesh(grid, blocks, 32));
    }

    @Test
    void allAbstractionStagesUseCachedStatesWithoutCallingBlockDataApis() {
        var grid = new PlotMiniatureGeometry.Grid(new PlotSelection.Bounds(-30000000, -64, 29999969,
                -29999969, -62, 30000000));
        BlockData forbidden = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[] {BlockData.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("Worker called " + method.getName());
                });
        BlockData[] blocks = new BlockData[grid.count()];
        String[] states = new String[grid.count()];
        for (int index = 0; index < blocks.length; index++) {
            if (grid.row(index) == 0 || grid.row(index) == 2) blocks[index] = forbidden;
            if (grid.row(index) == 0) states[index] = "minecraft:stone";
        }
        for (int budget : new int[] {1, 8, 32, 128, 512}) {
            var result = PlotMiniatureMesher.mesh(grid, blocks, states, budget);
            assertTrue(result.limited());
            assertEquals(budget, result.terrain().size());
            assertEquals(budget, result.terrain().stream().map(PlotMiniatureSampler.Voxel::index).distinct().count());
            assertEquals(result, PlotMiniatureMesher.mesh(grid, blocks, states, budget));
        }
    }

    private void assertNoInventedSamples(PlotMiniatureGeometry.Grid grid, BlockData[] blocks,
                                         PlotMiniatureMesher.Result result) {
        for (int index = 0; index < grid.count(); index++) {
            var point = grid.box(index);
            int covering = 0;
            for (var voxel : result.terrain()) {
                var box = voxel.box();
                if (Math.abs(point.worldX() - box.worldX()) < box.width() / 2
                        && Math.abs(point.worldY() - box.worldY()) < box.height() / 2
                        && Math.abs(point.worldZ() - box.worldZ()) < box.depth() / 2) {
                    covering++;
                    assertTrue(blocks[index] != null, "Filled empty sample " + index);
                    assertEquals(blocks[index].getAsString(), voxel.block().getAsString());
                }
            }
            assertTrue(covering <= 1, "Overlapping sample " + index);
        }
    }

    private int coordinate(PlotMiniatureGeometry.Grid grid, int index, int axis) {
        return switch (axis) {
            case 0 -> grid.column(index);
            case 1 -> grid.row(index);
            default -> grid.depth(index);
        };
    }

    private void assertRetained(PlotMiniatureMesher.Result result, int index) {
        assertTrue(result.terrain().stream().anyMatch(voxel -> voxel.index() == index), "Sample " + index);
    }

    private void assertCoverage(PlotMiniatureGeometry.Grid grid, BlockData[] blocks, PlotMiniatureMesher.Result result) {
        for (int index = 0; index < grid.count(); index++) {
            var point = grid.box(index);
            int covering = 0;
            for (var voxel : result.terrain()) {
                var box = voxel.box();
                if (Math.abs(point.worldX() - box.worldX()) < box.width() / 2
                        && Math.abs(point.worldY() - box.worldY()) < box.height() / 2
                        && Math.abs(point.worldZ() - box.worldZ()) < box.depth() / 2) {
                    covering++;
                    assertEquals(blocks[index].getAsString(), voxel.block().getAsString());
                }
            }
            assertEquals(blocks[index] == null ? 0 : 1, covering, "Sample " + index);
        }
    }

    private BlockData block(Material material, String state) {
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[] {BlockData.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMaterial" -> material;
                    case "isOccluding" -> material == Material.STONE || material == Material.OAK_LOG;
                    case "getAsString" -> state;
                    default -> throw new AssertionError(method.getName());
                });
    }
}
