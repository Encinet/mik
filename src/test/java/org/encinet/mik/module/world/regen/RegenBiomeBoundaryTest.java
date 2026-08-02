package org.encinet.mik.module.world.regen;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegenBiomeBoundaryTest {

    @Test
    void alignsNegativeCoordinatesToMinecraftBiomeCells() {
        assertEquals(-8, RegenChunkPlanner.cellOrigin(-5));
        assertEquals(-4, RegenChunkPlanner.cellOrigin(-4));
        assertEquals(-4, RegenChunkPlanner.cellOrigin(-1));
        assertEquals(0, RegenChunkPlanner.cellOrigin(0));
        assertEquals(4, RegenChunkPlanner.cellOrigin(7));
    }

    @Test
    void acceptsOnlyCellsCompletelyInsideTheSelection() {
        RegenSelection complete = cuboid(0, 0, 0, 3, 3, 3, 0, 16);
        RegenChunkPlanner.CellCoverage accepted = RegenChunkPlanner.coverage(complete, 0, 0, 0);
        assertTrue(accepted.intersectsSelection());
        assertTrue(accepted.fullyInsideSelection());

        RegenSelection clipped = cuboid(1, 0, 0, 3, 3, 3, 0, 16);
        RegenChunkPlanner.CellCoverage rejected = RegenChunkPlanner.coverage(clipped, 0, 0, 0);
        assertTrue(rejected.intersectsSelection());
        assertFalse(rejected.fullyInsideSelection());
    }

    @Test
    void ignoresThePartOfABiomeCellOutsideWorldHeight() {
        RegenSelection bottom = cuboid(0, -2, 0, 3, -1, 3, -2, 16);
        RegenChunkPlanner.CellCoverage coverage = RegenChunkPlanner.coverage(bottom, 0, -4, 0);
        assertTrue(coverage.intersectsSelection());
        assertTrue(coverage.fullyInsideSelection());
    }

    @Test
    void aHoleInsideAnOtherwiseSelectedCellStillProtectsTheCell() {
        RegenSelection withHole = selection(0, 0, 0, 3, 3, 3, 0, 16,
                packed -> packed != pack(2, 2, 2));
        RegenChunkPlanner.CellCoverage coverage = RegenChunkPlanner.coverage(withHole, 0, 0, 0);
        assertTrue(coverage.intersectsSelection());
        assertFalse(coverage.fullyInsideSelection());
    }

    private static RegenSelection cuboid(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int worldMinHeight,
            int worldMaxHeight
    ) {
        return selection(minX, minY, minZ, maxX, maxY, maxZ, worldMinHeight, worldMaxHeight,
                ignored -> true);
    }

    private static RegenSelection selection(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int worldMinHeight,
            int worldMaxHeight,
            IntPredicate included
    ) {
        return new RegenSelection() {
            @Override
            public World world() {
                return null;
            }

            @Override
            public long volume() {
                return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            }

            @Override
            public int minX() {
                return minX;
            }

            @Override
            public int minY() {
                return minY;
            }

            @Override
            public int minZ() {
                return minZ;
            }

            @Override
            public int maxX() {
                return maxX;
            }

            @Override
            public int maxY() {
                return maxY;
            }

            @Override
            public int maxZ() {
                return maxZ;
            }

            @Override
            public int worldMinHeight() {
                return worldMinHeight;
            }

            @Override
            public int worldMaxHeight() {
                return worldMaxHeight;
            }

            @Override
            public List<RegenChunkPos> chunks() {
                return List.of();
            }

            @Override
            public boolean contains(int x, int y, int z) {
                return x >= minX && x <= maxX
                        && y >= minY && y <= maxY
                        && z >= minZ && z <= maxZ
                        && included.test(pack(x, y, z));
            }
        };
    }

    private static int pack(int x, int y, int z) {
        return ((x & 1023) << 20) | ((y & 1023) << 10) | (z & 1023);
    }
}
