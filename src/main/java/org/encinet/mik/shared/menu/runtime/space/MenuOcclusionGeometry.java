package org.encinet.mik.shared.menu.runtime.space;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Pure eye-relative convex geometry. World and entity sampling belong to the runtime adapters. */
public final class MenuOcclusionGeometry {
    private static final int[][] FACES = {
            {0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1},
            {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}
    };
    private static final double EPSILON = 1.0E-8;


    private MenuOcclusionGeometry() { }

    public static double limit(Vector center, Vector right, Vector up, double halfWidth, double halfHeight,
                               double halfDepth, List<Box> boxes) {
        Vector normal = right.clone().crossProduct(up).normalize();
        Vector closest = center.clone().add(right.clone().multiply(Math.clamp(-center.dot(right), -halfWidth, halfWidth)))
                .add(up.clone().multiply(Math.clamp(-center.dot(up), -halfHeight, halfHeight)))
                .add(normal.clone().multiply(Math.clamp(-center.dot(normal), -halfDepth, halfDepth)));
        if (closest.lengthSquared() < EPSILON * EPSILON) return 0;
        Vector forward = closest.normalize();
        Vector coneRight = right.clone().subtract(forward.clone().multiply(right.dot(forward)));
        if (coneRight.lengthSquared() < EPSILON) coneRight = up.clone().subtract(forward.clone().multiply(up.dot(forward)));
        coneRight.normalize();
        Vector coneUp = forward.clone().crossProduct(coneRight).normalize();
        double minRight = Double.POSITIVE_INFINITY;
        double maxRight = Double.NEGATIVE_INFINITY;
        double minUp = Double.POSITIVE_INFINITY;
        double maxUp = Double.NEGATIVE_INFINITY;
        List<Vector> envelope = new ArrayList<>(9);
        envelope.add(new Vector());
        double farthest = 0;
        for (int corner = 0; corner < 8; corner++) {
            Vector vertex = center.clone().add(right.clone().multiply((corner & 1) == 0 ? -halfWidth : halfWidth))
                    .add(up.clone().multiply((corner & 2) == 0 ? -halfHeight : halfHeight))
                    .add(normal.clone().multiply((corner & 4) == 0 ? -halfDepth : halfDepth));
            double toward = forward.dot(vertex);
            farthest = Math.max(farthest, toward);
            minRight = Math.min(minRight, coneRight.dot(vertex) / toward);
            maxRight = Math.max(maxRight, coneRight.dot(vertex) / toward);
            minUp = Math.min(minUp, coneUp.dot(vertex) / toward);
            maxUp = Math.max(maxUp, coneUp.dot(vertex) / toward);
            envelope.add(vertex);
        }
        List<Vector> planes = List.of(coneRight.clone().subtract(forward.clone().multiply(minRight)),
                forward.clone().multiply(maxRight).subtract(coneRight),
                coneUp.clone().subtract(forward.clone().multiply(minUp)),
                forward.clone().multiply(maxUp).subtract(coneUp), forward);
        BoundingBox region = bounds(envelope);
        double nearest = Double.POSITIVE_INFINITY;
        for (Box box : boxes) {
            if (!region.overlaps(box.envelope())) continue;
            if (box.containsEye()) return 0;
            for (int[] face : FACES) {
                List<Vector> polygon = new ArrayList<>(4);
                for (int index : face) polygon.add(box.vertices().get(index));
                for (Vector plane : planes) {
                    polygon = clip(polygon, plane);
                    if (polygon.isEmpty()) break;
                }
                for (Vector vertex : polygon) nearest = Math.min(nearest, forward.dot(vertex));
            }
        }
        double clearance = Math.min(0.04, nearest * 0.2);
        return Math.clamp((nearest - clearance) / farthest, 0, 1);
    }

    private static List<Vector> clip(List<Vector> polygon, Vector plane) {
        if (polygon.isEmpty()) return polygon;
        List<Vector> result = new ArrayList<>();
        Vector previous = polygon.getLast();
        double previousDistance = previous.dot(plane);
        for (Vector current : polygon) {
            double currentDistance = current.dot(plane);
            if ((previousDistance >= -EPSILON) != (currentDistance >= -EPSILON)) {
                double fraction = previousDistance / (previousDistance - currentDistance);
                result.add(previous.clone().add(current.clone().subtract(previous).multiply(fraction)));
            }
            if (currentDistance >= -EPSILON) result.add(current);
            previous = current;
            previousDistance = currentDistance;
        }
        return result;
    }

    public static BoundingBox bounds(List<Vector> vertices) {
        if (vertices.isEmpty()) throw new IllegalArgumentException("A bounding volume needs vertices");
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vector vertex : vertices) {
            minX = Math.min(minX, vertex.getX());
            minY = Math.min(minY, vertex.getY());
            minZ = Math.min(minZ, vertex.getZ());
            maxX = Math.max(maxX, vertex.getX());
            maxY = Math.max(maxY, vertex.getY());
            maxZ = Math.max(maxZ, vertex.getZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public record Box(List<Vector> vertices, boolean containsEye, BoundingBox envelope) {
        public Box {
            vertices = List.copyOf(vertices);
            java.util.Objects.requireNonNull(envelope);
            if (vertices.size() != 8) throw new IllegalArgumentException("An obstacle box needs eight vertices");
        }
        public Box(List<Vector> vertices, boolean containsEye) {
            this(List.copyOf(vertices), containsEye, bounds(vertices));
        }

        public static Box axisAligned(Vector center, double radius) {
            List<Vector> vertices = new ArrayList<>(8);
            for (int corner = 0; corner < 8; corner++) {
                vertices.add(center.clone().add(new Vector((corner & 1) == 0 ? -radius : radius,
                        (corner & 2) == 0 ? -radius : radius, (corner & 4) == 0 ? -radius : radius)));
            }
            return new Box(vertices, Math.abs(center.getX()) <= radius
                    && Math.abs(center.getY()) <= radius && Math.abs(center.getZ()) <= radius);
        }
    }

    /** Uses the same convex obstruction volume for block shapes and entity models. */
    public static Box box(BoundingBox bounds, Vector origin) {
        List<Vector> vertices = new ArrayList<>(8);
        for (int corner = 0; corner < 8; corner++) {
            vertices.add(new Vector((corner & 1) == 0 ? bounds.getMinX() : bounds.getMaxX(),
                    (corner & 2) == 0 ? bounds.getMinY() : bounds.getMaxY(),
                    (corner & 4) == 0 ? bounds.getMinZ() : bounds.getMaxZ()).subtract(origin));
        }
        return new Box(vertices, bounds.contains(origin));
    }

    /** Slab intersection works for rotated and sheared display transformations as well as AABBs. */
    public static double firstHit(Vector start, Vector direction, double maximum, List<Box> boxes) {
        double result = Double.POSITIVE_INFINITY;
        for (Box box : boxes) {
            Vector base = box.vertices().getFirst();
            Vector x = box.vertices().get(1).clone().subtract(base);
            Vector y = box.vertices().get(2).clone().subtract(base);
            Vector z = box.vertices().get(4).clone().subtract(base);
            List<Vector> normals = List.of(y.clone().crossProduct(z).normalize(), z.clone().crossProduct(x).normalize(),
                    x.clone().crossProduct(y).normalize());
            double near = 0, far = maximum;
            for (Vector normal : normals) {
                double minimum = Double.POSITIVE_INFINITY, upper = Double.NEGATIVE_INFINITY;
                for (Vector vertex : box.vertices()) {
                    double projected = normal.dot(vertex);
                    minimum = Math.min(minimum, projected);
                    upper = Math.max(upper, projected);
                }
                double at = normal.dot(start), speed = normal.dot(direction);
                if (Math.abs(speed) < EPSILON) {
                    if (at < minimum - EPSILON || at > upper + EPSILON) { far = -1; break; }
                } else {
                    double a = (minimum - at) / speed, b = (upper - at) / speed;
                    near = Math.max(near, Math.min(a, b));
                    far = Math.min(far, Math.max(a, b));
                }
            }
            if (far >= near - EPSILON && far >= 0) result = Math.min(result, near);
        }
        return result;
    }
}
