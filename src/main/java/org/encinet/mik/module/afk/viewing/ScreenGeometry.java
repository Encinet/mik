package org.encinet.mik.module.afk.viewing;

import java.util.List;
import java.util.Objects;

/**
 * A physical screen's world-space rectangle, independent of Bukkit and playback APIs.
 *
 * <p>Distance is measured to the rectangle rather than its center. View alignment
 * minimizes the angle to any point on the rectangle, so watching an edge or a
 * subtitle does not require aiming at the center. This describes a viewing
 * opportunity, not proof that a human is present.</p>
 */
public record ScreenGeometry(
        Point center, Point horizontal, Point vertical, Point normal,
        double halfWidth, double halfHeight
) {
    public ScreenGeometry {
        Objects.requireNonNull(center, "center");
        requireUnit(horizontal);
        requireUnit(vertical);
        requireUnit(normal);
        if (Math.abs(horizontal.dot(vertical)) > 1.0e-6
                || Math.abs(horizontal.dot(normal)) > 1.0e-6
                || Math.abs(vertical.dot(normal)) > 1.0e-6
                || !Double.isFinite(halfWidth) || halfWidth <= 0
                || !Double.isFinite(halfHeight) || halfHeight <= 0) {
            throw new IllegalArgumentException("Screen requires an orthonormal basis and positive dimensions");
        }
    }

    public enum Face { NORTH, SOUTH, EAST, WEST, UP, DOWN }

    /**
     * Uses the outward face of an exclusive-max block box, not its volume center.
     * A small outward clearance avoids ray hits on the screen's own backing blocks.
     * Non-planar/conforming displays must be rejected by the source adapter.
     */
    public static ScreenGeometry fromBox(Point minimum, Point maximum, Face face) {
        Point extent = maximum.minus(minimum);
        if (extent.x <= 0 || extent.y <= 0 || extent.z <= 0) {
            throw new IllegalArgumentException("Empty screen box");
        }
        Point middle = minimum.plus(maximum).scale(0.5);
        Point normal = switch (face) {
            case NORTH -> new Point(0, 0, -1);
            case SOUTH -> new Point(0, 0, 1);
            case EAST -> new Point(1, 0, 0);
            case WEST -> new Point(-1, 0, 0);
            case UP -> new Point(0, 1, 0);
            case DOWN -> new Point(0, -1, 0);
        };
        Point horizontal = face == Face.EAST || face == Face.WEST
                ? new Point(0, 0, 1) : new Point(1, 0, 0);
        Point vertical = face == Face.UP || face == Face.DOWN
                ? new Point(0, 0, 1) : new Point(0, 1, 0);
        double depth = Math.abs(normal.dot(extent));
        return new ScreenGeometry(middle.plus(normal.scale(depth * 0.5 + 0.02)),
                horizontal, vertical, normal,
                Math.abs(horizontal.dot(extent)) * 0.5,
                Math.abs(vertical.dot(extent)) * 0.5);
    }

    public boolean isInFront(Point eye) {
        return eye.minus(center).dot(normal) > 0.01;
    }

    public double distanceTo(Point eye) {
        Point relative = eye.minus(center);
        Point nearest = point(
                Math.clamp(relative.dot(horizontal), -halfWidth, halfWidth),
                Math.clamp(relative.dot(vertical), -halfHeight, halfHeight));
        return eye.minus(nearest).length();
    }

    /**
     * Exact minimum angle for a planar rectangle. A forward ray hit gives zero;
     * otherwise the maximum direction cosine lies on the boundary. Each edge is
     * checked at both endpoints and its cosine's interior stationary point.
     * This avoids the overly generous center-plus-bounding-sphere approximation.
     */
    public double minimumViewAngleDegrees(Point eye, Point direction) {
        Point view = direction.unit();
        double denominator = view.dot(normal);
        if (Math.abs(denominator) > 1.0e-12) {
            double rayDistance = center.minus(eye).dot(normal) / denominator;
            if (rayDistance > 0) {
                Point hit = eye.plus(view.scale(rayDistance)).minus(center);
                if (Math.abs(hit.dot(horizontal)) <= halfWidth
                        && Math.abs(hit.dot(vertical)) <= halfHeight) {
                    return 0;
                }
            }
        }
        List<Point> corners = List.of(point(-halfWidth, -halfHeight), point(halfWidth, -halfHeight),
                point(halfWidth, halfHeight), point(-halfWidth, halfHeight));
        double bestCosine = -1;
        for (int index = 0; index < corners.size(); index++) {
            Point start = corners.get(index).minus(eye);
            Point edge = corners.get((index + 1) % corners.size()).minus(corners.get(index));
            bestCosine = Math.max(bestCosine, cosine(start, view));
            double projectedStart = view.dot(start);
            double projectedEdge = view.dot(edge);
            double mixed = start.dot(edge);
            double derivativeSlope = projectedEdge * mixed - projectedStart * edge.dot(edge);
            if (Math.abs(derivativeSlope) > 1.0e-12) {
                double fraction = (projectedStart * mixed - projectedEdge * start.dot(start)) / derivativeSlope;
                if (fraction > 0 && fraction < 1) {
                    bestCosine = Math.max(bestCosine, cosine(start.plus(edge.scale(fraction)), view));
                }
            }
        }
        return Math.toDegrees(Math.acos(Math.clamp(bestCosine, -1, 1)));
    }

    /** Rejects screens reduced to a tiny sliver by distance or an extreme side angle. */
    public double angularSizeDegrees(Point eye) {
        double widthAngle = angle(point(-halfWidth, 0).minus(eye), point(halfWidth, 0).minus(eye));
        double heightAngle = angle(point(0, -halfHeight).minus(eye), point(0, halfHeight).minus(eye));
        return Math.min(widthAngle, heightAngle);
    }

    /** Interior samples avoid ambiguous edge hits and tolerate a small central obstruction. */
    public List<Point> visibilityTargets() {
        return List.of(center,
                point(-halfWidth * 0.5, -halfHeight * 0.5),
                point(halfWidth * 0.5, -halfHeight * 0.5),
                point(halfWidth * 0.5, halfHeight * 0.5),
                point(-halfWidth * 0.5, halfHeight * 0.5));
    }

    private Point point(double horizontalOffset, double verticalOffset) {
        return center.plus(horizontal.scale(horizontalOffset)).plus(vertical.scale(verticalOffset));
    }

    private static double cosine(Point offset, Point view) {
        double length = offset.length();
        return length > 1.0e-12 ? offset.dot(view) / length : 1;
    }

    private static double angle(Point first, Point second) {
        return Math.toDegrees(Math.acos(Math.clamp(first.unit().dot(second.unit()), -1, 1)));
    }

    private static void requireUnit(Point axis) {
        Objects.requireNonNull(axis, "axis");
        if (Math.abs(axis.length() - 1) > 1.0e-6) {
            throw new IllegalArgumentException("Screen axes must be unit vectors");
        }
    }

    /** Finite immutable vectors keep malformed plugin coordinates out of the detector. */
    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Non-finite coordinate");
            }
        }

        public Point plus(Point other) { return new Point(x + other.x, y + other.y, z + other.z); }
        public Point minus(Point other) { return new Point(x - other.x, y - other.y, z - other.z); }
        public Point scale(double factor) { return new Point(x * factor, y * factor, z * factor); }
        public double dot(Point other) { return x * other.x + y * other.y + z * other.z; }
        public double length() { return Math.hypot(Math.hypot(x, y), z); }

        public Point unit() {
            double length = length();
            if (!Double.isFinite(length) || length <= 1.0e-12) throw new IllegalArgumentException("Invalid direction");
            return scale(1 / length);
        }
    }
}
