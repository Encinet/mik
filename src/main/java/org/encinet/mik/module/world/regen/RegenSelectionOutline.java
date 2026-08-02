package org.encinet.mik.module.world.regen;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Produces a bounded particle outline without iterating every block in a large selection. */
final class RegenSelectionOutline {

    private static final int MAX_ARBITRARY_SHAPE_PROBES = 16_384;

    private RegenSelectionOutline() {
    }

    static List<RegenPreviewPoint> sample(RegenSelection selection, int maximumPoints) {
        if (maximumPoints <= 0) {
            return List.of();
        }
        if (selection.fullyContainsBox(
                selection.minX(), selection.minY(), selection.minZ(),
                selection.maxX(), selection.maxY(), selection.maxZ())) {
            return cuboid(selection, maximumPoints);
        }
        return arbitraryShape(selection, maximumPoints);
    }

    private static List<RegenPreviewPoint> cuboid(RegenSelection selection, int maximumPoints) {
        long xLength = (long) selection.maxX() - selection.minX() + 1L;
        long yLength = (long) selection.maxY() - selection.minY() + 1L;
        long zLength = (long) selection.maxZ() - selection.minZ() + 1L;
        long edgeLength = 4L * (xLength + yLength + zLength);
        int step = (int) Math.max(1L, divideCeil(edgeLength, maximumPoints));
        Set<RegenPreviewPoint> points = new LinkedHashSet<>();

        addXEdge(points, selection.minX(), selection.maxX(), selection.minY(), selection.minZ(), step);
        addXEdge(points, selection.minX(), selection.maxX(), selection.minY(), selection.maxZ(), step);
        addXEdge(points, selection.minX(), selection.maxX(), selection.maxY(), selection.minZ(), step);
        addXEdge(points, selection.minX(), selection.maxX(), selection.maxY(), selection.maxZ(), step);

        addYEdge(points, selection.minY(), selection.maxY(), selection.minX(), selection.minZ(), step);
        addYEdge(points, selection.minY(), selection.maxY(), selection.minX(), selection.maxZ(), step);
        addYEdge(points, selection.minY(), selection.maxY(), selection.maxX(), selection.minZ(), step);
        addYEdge(points, selection.minY(), selection.maxY(), selection.maxX(), selection.maxZ(), step);

        addZEdge(points, selection.minZ(), selection.maxZ(), selection.minX(), selection.minY(), step);
        addZEdge(points, selection.minZ(), selection.maxZ(), selection.minX(), selection.maxY(), step);
        addZEdge(points, selection.minZ(), selection.maxZ(), selection.maxX(), selection.minY(), step);
        addZEdge(points, selection.minZ(), selection.maxZ(), selection.maxX(), selection.maxY(), step);

        if (points.size() <= maximumPoints) {
            return List.copyOf(points);
        }
        RegenPreviewPointSampler sampler = new RegenPreviewPointSampler(maximumPoints);
        sampler.addAll(points);
        return sampler.snapshot();
    }

    private static List<RegenPreviewPoint> arbitraryShape(
            RegenSelection selection,
            int maximumPoints
    ) {
        double xLength = (double) selection.maxX() - selection.minX() + 1.0D;
        double yLength = (double) selection.maxY() - selection.minY() + 1.0D;
        double zLength = (double) selection.maxZ() - selection.minZ() + 1.0D;
        int step = Math.max(1, (int) Math.ceil(Math.cbrt(
                xLength * yLength * zLength / MAX_ARBITRARY_SHAPE_PROBES)));
        step = fitProbeBudget(selection, step);
        int[] xCoordinates = axis(selection.minX(), selection.maxX(), step);
        int[] yCoordinates = axis(selection.minY(), selection.maxY(), step);
        int[] zCoordinates = axis(selection.minZ(), selection.maxZ(), step);
        RegenPreviewPointSampler sampler = new RegenPreviewPointSampler(maximumPoints);

        for (int y : yCoordinates) {
            for (int z : zCoordinates) {
                for (int x : xCoordinates) {
                    if (!selection.contains(x, y, z)
                            || !touchesOutside(selection, x, y, z, step)) {
                        continue;
                    }
                    sampler.add(new RegenPreviewPoint(x, y, z));
                }
            }
        }
        return sampler.snapshot();
    }

    private static int fitProbeBudget(RegenSelection selection, int initialStep) {
        long step = initialStep;
        while (probeCount(selection, step) > MAX_ARBITRARY_SHAPE_PROBES) {
            double scale = Math.cbrt(
                    (double) probeCount(selection, step) / MAX_ARBITRARY_SHAPE_PROBES);
            long next = Math.max(step + 1L, (long) Math.ceil(step * scale));
            step = Math.min(Integer.MAX_VALUE, next);
        }
        return (int) step;
    }

    private static long probeCount(RegenSelection selection, long step) {
        long x = axisSize(selection.minX(), selection.maxX(), step);
        long y = axisSize(selection.minY(), selection.maxY(), step);
        long z = axisSize(selection.minZ(), selection.maxZ(), step);
        if (x > Long.MAX_VALUE / y) {
            return Long.MAX_VALUE;
        }
        long xy = x * y;
        return xy > Long.MAX_VALUE / z ? Long.MAX_VALUE : xy * z;
    }

    private static long axisSize(int minimum, int maximum, long step) {
        long length = (long) maximum - minimum;
        return length / step + 1L + (length % step == 0L ? 0L : 1L);
    }

    private static boolean touchesOutside(RegenSelection selection, int x, int y, int z, int step) {
        return outside(selection, (long) x - step, y, z)
                || outside(selection, (long) x + step, y, z)
                || outside(selection, x, (long) y - step, z)
                || outside(selection, x, (long) y + step, z)
                || outside(selection, x, y, (long) z - step)
                || outside(selection, x, y, (long) z + step);
    }

    private static boolean outside(RegenSelection selection, long x, long y, long z) {
        return x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
                || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE
                || !selection.contains((int) x, (int) y, (int) z);
    }

    private static int[] axis(int minimum, int maximum, int step) {
        long length = (long) maximum - minimum;
        int size = Math.toIntExact(axisSize(minimum, maximum, step));
        int[] coordinates = new int[size];
        int index = 0;
        for (long value = minimum; value <= maximum; value += step) {
            coordinates[index++] = (int) value;
        }
        if (coordinates[index - 1] != maximum) {
            coordinates[index] = maximum;
        }
        return coordinates;
    }

    private static void addXEdge(
            Set<RegenPreviewPoint> points,
            int minimum,
            int maximum,
            int y,
            int z,
            int step
    ) {
        addAxis(points, minimum, maximum, step, value -> new RegenPreviewPoint(value, y, z));
    }

    private static void addYEdge(
            Set<RegenPreviewPoint> points,
            int minimum,
            int maximum,
            int x,
            int z,
            int step
    ) {
        addAxis(points, minimum, maximum, step, value -> new RegenPreviewPoint(x, value, z));
    }

    private static void addZEdge(
            Set<RegenPreviewPoint> points,
            int minimum,
            int maximum,
            int x,
            int y,
            int step
    ) {
        addAxis(points, minimum, maximum, step, value -> new RegenPreviewPoint(x, y, value));
    }

    private static void addAxis(
            Set<RegenPreviewPoint> points,
            int minimum,
            int maximum,
            int step,
            java.util.function.IntFunction<RegenPreviewPoint> factory
    ) {
        for (long value = minimum; value <= maximum; value += step) {
            points.add(factory.apply((int) value));
        }
        points.add(factory.apply(maximum));
    }

    private static long divideCeil(long value, long divisor) {
        return value / divisor + (value % divisor == 0L ? 0L : 1L);
    }
}
