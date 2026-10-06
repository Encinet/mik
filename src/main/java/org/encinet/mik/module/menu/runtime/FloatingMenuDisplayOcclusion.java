package org.encinet.mik.module.menu.runtime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

final class FloatingMenuDisplayOcclusion {
    private static final int[][] FACES = {
            {0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1},
            {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}
    };
    private static final double EPSILON = 1.0E-8;

    private FloatingMenuDisplayOcclusion() { }

    static FloatingMenuAnchorResolver.Anchor fit(Player viewer, Location eye,
                                                 FloatingMenuAnchorResolver.Anchor anchor,
                                                 FloatingMenuAnchorResolver.SceneBounds bounds,
                                                 double motionPadding) {
        if (viewer == null || bounds.surfaces().isEmpty()) return anchor;
        Vector offset = anchor.origin().toVector().subtract(eye.toVector());
        double maximumDistance = offset.length();
        for (var surface : bounds.surfaces()) {
            var pose = surface.pose();
            double radius = Math.sqrt(pose.right() * pose.right() + pose.up() * pose.up()
                    + pose.forward() * pose.forward()) + surface.size().width() / 2
                    + surface.size().height() / 2 + Math.max(0.14, surface.depth() / 2) + motionPadding;
            maximumDistance = Math.max(maximumDistance, offset.length() + radius * anchor.spatialScale());
        }
        List<Box> boxes = new ArrayList<>();
        for (var entity : eye.getWorld().getEntitiesByClasses(BlockDisplay.class, ItemDisplay.class)) {
            Display display = (Display) entity;
            if (!display.isValid() || !viewer.canSee(display)) continue;
            if (display instanceof BlockDisplay block && air(block.getBlock().getMaterial())) continue;
            if (display instanceof ItemDisplay item && air(item.getItemStack().getType())) continue;
            Box box = box(eye, display, maximumDistance);
            if (box != null) boxes.add(box);
        }
        if (boxes.isEmpty()) return anchor;
        double factor = 1;
        for (var surface : bounds.surfaces()) {
            var pose = surface.pose();
            Vector center = offset.clone().add(anchor.right().clone().multiply(pose.right() * anchor.spatialScale()))
                    .add(new Vector(0, pose.up() * anchor.spatialScale(), 0))
                    .add(anchor.forward().clone().multiply(-pose.forward() * anchor.spatialScale()));
            double yaw = Math.toRadians(pose.yawDegrees());
            double pitch = Math.toRadians(pose.pitchDegrees());
            Vector right = anchor.right().clone().multiply(Math.cos(yaw))
                    .add(anchor.forward().clone().multiply(-Math.sin(yaw)));
            Vector up = new Vector(0, Math.cos(pitch), 0)
                    .add(anchor.right().clone().multiply(-Math.sin(yaw) * Math.sin(pitch)))
                    .add(anchor.forward().clone().multiply(-Math.cos(yaw) * Math.sin(pitch)));
            double depth = (Math.max(0.14, surface.depth() / 2) + motionPadding) * anchor.spatialScale();
            factor = Math.min(factor, limit(center, right, up,
                    surface.size().width() * anchor.spatialScale() / 2,
                    surface.size().height() * anchor.spatialScale() / 2, depth, boxes));
        }
        return contract(eye, anchor, factor);
    }

    private static boolean air(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    static FloatingMenuAnchorResolver.Anchor contract(Location eye, FloatingMenuAnchorResolver.Anchor anchor,
                                                      double factor) {
        factor = Math.clamp(factor, 0.001, 1);
        return new FloatingMenuAnchorResolver.Anchor(eye.clone().add(anchor.origin().toVector()
                .subtract(eye.toVector()).multiply(factor)), anchor.forward(), anchor.right(),
                anchor.spatialScale() * factor, anchor.distance() * factor, anchor.yawOffsetDegrees());
    }

    static double recoveryScale(double previous, double target) {
        if (previous <= 0 || target <= previous) return target;
        return Math.min(target, previous * 1.08);
    }

    static Box box(Location eye, Display display) {
        return box(eye, display, Double.POSITIVE_INFINITY);
    }

    static Box box(Location eye, Display display, double maximumDistance) {
        Location location = display.getLocation();
        Transformation transformation = display.getTransformation();
        Matrix4d local = new Matrix4d().translate(transformation.getTranslation())
                .rotate(transformation.getLeftRotation()).scale(new Vector3d(transformation.getScale()))
                .rotate(transformation.getRightRotation());
        if (!local.isFinite() || Math.abs(local.determinant()) < 1.0E-12) return null;
        Vector offset = location.toVector().subtract(eye.toVector());
        double low = display instanceof BlockDisplay ? 0 : -0.5;
        double high = display instanceof BlockDisplay ? 1 : 0.5;
        if (display.getBillboard() != Display.Billboard.FIXED) {
            double radius = 0;
            for (int corner = 0; corner < 8; corner++) {
                radius = Math.max(radius, local.transformPosition(new Vector3d(
                        (corner & 1) == 0 ? low : high, (corner & 2) == 0 ? low : high,
                        (corner & 4) == 0 ? low : high)).length());
            }
            return offset.length() > maximumDistance + radius * Math.sqrt(3)
                    ? null : Box.axisAligned(offset, radius);
        }
        Matrix4d transform = new Matrix4d().translate(offset.getX(), offset.getY(), offset.getZ())
                .rotateY(-Math.toRadians(location.getYaw())).rotateX(Math.toRadians(location.getPitch())).mul(local);
        if (!transform.isFinite()) return null;
        double centerCoordinate = (low + high) / 2;
        Vector3d center = transform.transformPosition(new Vector3d(centerCoordinate));
        double radius = Math.sqrt(transform.m00() * transform.m00() + transform.m01() * transform.m01()
                + transform.m02() * transform.m02() + transform.m10() * transform.m10()
                + transform.m11() * transform.m11() + transform.m12() * transform.m12()
                + transform.m20() * transform.m20() + transform.m21() * transform.m21()
                + transform.m22() * transform.m22()) * Math.sqrt(3) / 2;
        if (center.length() > maximumDistance + radius) return null;
        List<Vector> vertices = new ArrayList<>(8);
        for (int corner = 0; corner < 8; corner++) {
            Vector3d vertex = transform.transformPosition(new Vector3d((corner & 1) == 0 ? low : high,
                    (corner & 2) == 0 ? low : high, (corner & 4) == 0 ? low : high));
            vertices.add(new Vector(vertex.x, vertex.y, vertex.z));
        }
        Vector3d localEye = new Matrix4d(transform).invert().transformPosition(new Vector3d());
        boolean containsEye = localEye.x >= low && localEye.x <= high && localEye.y >= low
                && localEye.y <= high && localEye.z >= low && localEye.z <= high;
        return new Box(vertices, containsEye);
    }

    static double limit(Vector center, Vector right, Vector up, double halfWidth, double halfHeight,
                        double halfDepth, List<Box> boxes) {
        Vector normal = right.clone().crossProduct(up).normalize();
        if (normal.dot(center) < 0) normal.multiply(-1);
        double distance = normal.dot(center);
        if (distance <= halfDepth + EPSILON) return 1;
        double minRight = Double.POSITIVE_INFINITY;
        double maxRight = Double.NEGATIVE_INFINITY;
        double minUp = Double.POSITIVE_INFINITY;
        double maxUp = Double.NEGATIVE_INFINITY;
        List<Vector> envelope = new ArrayList<>(9);
        envelope.add(new Vector());
        for (int corner = 0; corner < 8; corner++) {
            Vector vertex = center.clone().add(right.clone().multiply((corner & 1) == 0 ? -halfWidth : halfWidth))
                    .add(up.clone().multiply((corner & 2) == 0 ? -halfHeight : halfHeight))
                    .add(normal.clone().multiply((corner & 4) == 0 ? -halfDepth : halfDepth));
            double toward = normal.dot(vertex);
            minRight = Math.min(minRight, right.dot(vertex) / toward);
            maxRight = Math.max(maxRight, right.dot(vertex) / toward);
            minUp = Math.min(minUp, up.dot(vertex) / toward);
            maxUp = Math.max(maxUp, up.dot(vertex) / toward);
            envelope.add(vertex);
        }
        List<Vector> planes = List.of(right.clone().subtract(normal.clone().multiply(minRight)),
                normal.clone().multiply(maxRight).subtract(right),
                up.clone().subtract(normal.clone().multiply(minUp)),
                normal.clone().multiply(maxUp).subtract(up), normal);
        BoundingBox region = bounds(envelope);
        double nearest = Double.POSITIVE_INFINITY;
        for (Box box : boxes) {
            if (!region.overlaps(box.envelope())) continue;
            if (box.containsEye()) return 0.001;
            for (int[] face : FACES) {
                List<Vector> polygon = new ArrayList<>(4);
                for (int index : face) polygon.add(box.vertices().get(index));
                for (Vector plane : planes) {
                    polygon = clip(polygon, plane);
                    if (polygon.isEmpty()) break;
                }
                for (Vector vertex : polygon) nearest = Math.min(nearest, normal.dot(vertex));
            }
        }
        double clearance = Math.min(0.04, nearest * 0.2);
        return Math.clamp((nearest - clearance) / (distance + halfDepth), 0.001, 1);
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

    private static BoundingBox bounds(List<Vector> vertices) {
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

    record Box(List<Vector> vertices, boolean containsEye, BoundingBox envelope) {
        Box(List<Vector> vertices, boolean containsEye) {
            this(List.copyOf(vertices), containsEye, bounds(vertices));
        }

        static Box axisAligned(Vector center, double radius) {
            List<Vector> vertices = new ArrayList<>(8);
            for (int corner = 0; corner < 8; corner++) {
                vertices.add(center.clone().add(new Vector((corner & 1) == 0 ? -radius : radius,
                        (corner & 2) == 0 ? -radius : radius, (corner & 4) == 0 ? -radius : radius)));
            }
            return new Box(vertices, Math.abs(center.getX()) <= radius
                    && Math.abs(center.getY()) <= radius && Math.abs(center.getZ()) <= radius);
        }
    }
}
