package org.encinet.mik.shared.menu.runtime.space;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Eye-relative geometry only. The one spatial index serves both rays and complete surface volumes. */
final class MenuSpaceSnapshot implements MenuCollisionSpace {
    private static final int LEAF_SIZE = 8;
    private static final double BOUNDS_EPSILON = 1e-8;
    private final BoundingBox coverage;
    private final Node terrain;
    private final Node models;
    private final Vector offset;

    MenuSpaceSnapshot(BoundingBox coverage, List<BoundingBox> blocks) {
        this.coverage = coverage.clone();
        var obstacles = new ArrayList<Obstacle>(blocks.size());
        var eye = new Vector();
        for (var shape : blocks) obstacles.add(new Obstacle(MenuOcclusionGeometry.box(shape, eye), true));
        terrain = obstacles.isEmpty() ? null : Node.build(obstacles);
        models = null;
        offset = eye;
    }

    private MenuSpaceSnapshot(BoundingBox coverage, Node terrain, Node models, Vector offset) {
        this.coverage = coverage;
        this.terrain = terrain;
        this.models = models;
        this.offset = offset;
    }

    MenuSpaceSnapshot at(Vector offset, List<Box> entities) {
        if (entities.isEmpty() && offset.isZero()) return this;
        var obstacles = new ArrayList<Obstacle>(entities.size());
        for (var shape : entities) obstacles.add(new Obstacle(shape, false));
        return new MenuSpaceSnapshot(coverage, terrain, obstacles.isEmpty() ? null : Node.build(obstacles), offset);
    }

    boolean clear(BoundingBox region) {
        var sampled = region.clone().shift(offset);
        return coverage.contains(sampled) && (terrain == null || !terrain.overlaps(sampled))
                && (models == null || !models.overlaps(region));
    }

    @Override public double firstHit(Location eye, Vector direction, double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > FloatingMenuWorldSpace.MAX_TRACE_DISTANCE) return 0;
        var start = eye.toVector().add(offset);
        if (!covered(start.getX(), start.getY(), start.getZ()) || !covered(start.getX() + direction.getX() * distance,
                start.getY() + direction.getY() * distance, start.getZ() + direction.getZ() * distance)) return 0;
        double hit = terrain == null ? Double.POSITIVE_INFINITY : terrain.firstHit(start, direction, distance);
        return models == null ? hit : Math.min(hit, models.firstHit(eye.toVector(), direction, Math.min(hit, distance)));
    }

    private boolean covered(double right, double up, double forward) {
        return right >= coverage.getMinX() - BOUNDS_EPSILON && right <= coverage.getMaxX() + BOUNDS_EPSILON
                && up >= coverage.getMinY() - BOUNDS_EPSILON && up <= coverage.getMaxY() + BOUNDS_EPSILON
                && forward >= coverage.getMinZ() - BOUNDS_EPSILON && forward <= coverage.getMaxZ() + BOUNDS_EPSILON;
    }

    @Override public double limitSurface(Location eye, Vector center, Vector right, Vector up,
                                         double halfWidth, double halfHeight, double clearance) {
        var relative = center.clone().subtract(eye.toVector());
        var region = MenuOcclusionGeometry.surfaceBounds(relative, right, up, halfWidth, halfHeight, clearance);
        var sampled = region.clone().shift(offset);
        if (!coverage.contains(sampled)) return 0;
        List<Box> nearby = new ArrayList<>();
        if (terrain != null) terrain.collect(sampled, offset, nearby);
        if (models != null) models.collect(region, null, nearby);
        return MenuOcclusionGeometry.limit(relative, right, up, halfWidth, halfHeight, clearance, nearby);
    }

    private record Obstacle(Box shape, boolean axisAligned) {
        BoundingBox envelope() { return shape.envelope(); }

        double firstHit(Vector start, Vector direction, double distance) {
            double hit = boxHit(envelope(), start, direction, distance, axisAligned);
            return axisAligned || !Double.isFinite(hit) ? hit : MenuOcclusionGeometry.firstHit(start, direction, distance, List.of(shape));
        }
    }

    private record Node(BoundingBox bounds, List<Obstacle> leaf, Node left, Node right) {
        static Node build(List<Obstacle> obstacles) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            var bounds = obstacles.getFirst().envelope().clone();
            for (var obstacle : obstacles) bounds.union(obstacle.envelope());
            if (obstacles.size() <= LEAF_SIZE) return new Node(bounds, List.copyOf(obstacles), null, null);
            Comparator<Obstacle> order = bounds.getWidthX() >= Math.max(bounds.getHeight(), bounds.getWidthZ())
                    ? Comparator.comparingDouble(obstacle -> obstacle.envelope().getCenterX())
                    : bounds.getHeight() >= bounds.getWidthZ()
                    ? Comparator.comparingDouble(obstacle -> obstacle.envelope().getCenterY())
                    : Comparator.comparingDouble(obstacle -> obstacle.envelope().getCenterZ());
            obstacles.sort(order);
            int middle = obstacles.size() / 2;
            return new Node(bounds, List.of(), build(obstacles.subList(0, middle)), build(obstacles.subList(middle, obstacles.size())));
        }

        boolean overlaps(BoundingBox region) {
            if (!bounds.overlaps(region)) return false;
            if (left != null) return left.overlaps(region) || right.overlaps(region);
            for (var obstacle : leaf) if (obstacle.envelope().overlaps(region)) return true;
            return false;
        }

        void collect(BoundingBox region, Vector offset, List<Box> result) {
            if (!bounds.overlaps(region)) return;
            if (left == null) {
                for (var obstacle : leaf) if (obstacle.envelope().overlaps(region))
                    result.add(offset == null || offset.isZero() ? obstacle.shape() : MenuOcclusionGeometry.box(obstacle.envelope(), offset));
            } else {
                left.collect(region, offset, result);
                right.collect(region, offset, result);
            }
        }

        double firstHit(Vector start, Vector direction, double maximum) {
            if (!Double.isFinite(boxHit(bounds, start, direction, maximum, false))) return Double.POSITIVE_INFINITY;
            double nearest = Double.POSITIVE_INFINITY;
            if (left == null) {
                for (var obstacle : leaf) nearest = Math.min(nearest, obstacle.firstHit(start, direction, Math.min(nearest, maximum)));
                return nearest;
            }
            nearest = left.firstHit(start, direction, maximum);
            return Math.min(nearest, right.firstHit(start, direction, Math.min(nearest, maximum)));
        }
    }

    private static double boxHit(BoundingBox box, Vector start, Vector direction, double maximum, boolean halfOpen) {
        double near = 0, far = maximum;
        for (int axis = 0; axis < 3; axis++) {
            double at = axis == 0 ? start.getX() : axis == 1 ? start.getY() : start.getZ();
            double speed = axis == 0 ? direction.getX() : axis == 1 ? direction.getY() : direction.getZ();
            double minimum = axis == 0 ? box.getMinX() : axis == 1 ? box.getMinY() : box.getMinZ();
            double limit = axis == 0 ? box.getMaxX() : axis == 1 ? box.getMaxY() : box.getMaxZ();
            if (Math.abs(speed) < BOUNDS_EPSILON) {
                if (halfOpen ? at < minimum || at >= limit : at < minimum - BOUNDS_EPSILON || at > limit + BOUNDS_EPSILON)
                    return Double.POSITIVE_INFINITY;
            } else {
                double entering = (minimum - at) / speed, leaving = (limit - at) / speed;
                near = Math.max(near, Math.min(entering, leaving));
                far = Math.min(far, Math.max(entering, leaving));
                if (near > far + BOUNDS_EPSILON || far <= 0) return Double.POSITIVE_INFINITY;
            }
        }
        return near;
    }
}
