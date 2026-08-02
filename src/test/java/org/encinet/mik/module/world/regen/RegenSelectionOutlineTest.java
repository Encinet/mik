package org.encinet.mik.module.world.regen;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegenSelectionOutlineTest {

    @Test
    void cuboidOutlineUsesEdgesAndHonoursItsPointBudget() {
        TestSelection selection = new TestSelection(0, 0, 0, 99, 79, 59, true,
                (x, y, z) -> true);

        List<RegenPreviewPoint> outline = RegenSelectionOutline.sample(selection, 160);

        assertFalse(outline.isEmpty());
        assertTrue(outline.size() <= 160);
        for (RegenPreviewPoint point : outline) {
            int boundaryAxes = (point.x() == selection.minX() || point.x() == selection.maxX() ? 1 : 0)
                    + (point.y() == selection.minY() || point.y() == selection.maxY() ? 1 : 0)
                    + (point.z() == selection.minZ() || point.z() == selection.maxZ() ? 1 : 0);
            assertTrue(boundaryAxes >= 2, () -> "Point is not on a cuboid edge: " + point);
        }
    }

    @Test
    void arbitraryShapeSamplesItsActualSelectedSurface() {
        TestSelection cylinder = new TestSelection(-24, 0, -24, 24, 40, 24, false,
                (x, y, z) -> x * x + z * z <= 24 * 24);

        List<RegenPreviewPoint> outline = RegenSelectionOutline.sample(cylinder, 220);

        assertFalse(outline.isEmpty());
        assertTrue(outline.size() <= 220);
        assertTrue(outline.stream().allMatch(point -> cylinder.contains(point.x(), point.y(), point.z())));
        assertTrue(outline.stream().anyMatch(point -> Math.abs(point.x()) < 24
                && Math.abs(point.z()) < 24));
    }

    @Test
    void elongatedArbitrarySelectionsRemainBounded() {
        TestSelection line = new TestSelection(0, 10, 0, 1_000_000, 10, 0, false,
                (x, y, z) -> true);

        List<RegenPreviewPoint> outline = RegenSelectionOutline.sample(line, 128);

        assertFalse(outline.isEmpty());
        assertTrue(outline.size() <= 128);
    }

    @Test
    void deterministicSamplerDoesNotDependOnPlanningOrder() {
        List<RegenPreviewPoint> points = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            points.add(new RegenPreviewPoint(index, index % 64, -index));
        }
        RegenPreviewPointSampler forward = new RegenPreviewPointSampler(64);
        forward.addAll(points);
        Collections.reverse(points);
        RegenPreviewPointSampler reverse = new RegenPreviewPointSampler(64);
        reverse.addAll(points);

        assertEquals(forward.snapshot(), reverse.snapshot());
        assertEquals(64, forward.snapshot().size());
        assertThrows(IllegalArgumentException.class, () -> new RegenPreviewPointSampler(0));
    }

    private record TestSelection(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            boolean cuboid,
            Contains contains
    ) implements RegenSelection {

        @Override
        public World world() {
            return null;
        }

        @Override
        public long volume() {
            return (long) (maxX - minX + 1)
                    * (maxY - minY + 1)
                    * (maxZ - minZ + 1);
        }

        @Override
        public int worldMinHeight() {
            return -64;
        }

        @Override
        public int worldMaxHeight() {
            return 320;
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
                    && contains.test(x, y, z);
        }

        @Override
        public boolean fullyContainsBox(
                int minimumX,
                int minimumY,
                int minimumZ,
                int maximumX,
                int maximumY,
                int maximumZ
        ) {
            return cuboid
                    && minimumX >= minX && maximumX <= maxX
                    && minimumY >= minY && maximumY <= maxY
                    && minimumZ >= minZ && maximumZ <= maxZ;
        }
    }

    @FunctionalInterface
    private interface Contains {

        boolean test(int x, int y, int z);
    }
}
