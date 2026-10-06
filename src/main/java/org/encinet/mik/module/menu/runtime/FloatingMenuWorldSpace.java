package org.encinet.mik.module.menu.runtime;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class FloatingMenuWorldSpace {
    private static final List<Vector> WORLD_AXES = List.of(
            new Vector(1, 0, 0), new Vector(0, 1, 0), new Vector(0, 0, 1));
    private final World world;
    private final Map<BlockPosition, List<BoundingBox>> collisions = new HashMap<>();

    FloatingMenuWorldSpace(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    double firstHit(Location start, Vector direction, double distance) {
        if (!world.isChunkLoaded(start.getBlockX() >> 4, start.getBlockZ() >> 4)) return 0;
        double loadedDistance = distance;
        Vector end = start.toVector().add(direction.clone().multiply(distance));
        int minimumChunkX = ((int) Math.floor(Math.min(start.getX(), end.getX()))) >> 4;
        int maximumChunkX = ((int) Math.floor(Math.max(start.getX(), end.getX()))) >> 4;
        int minimumChunkZ = ((int) Math.floor(Math.min(start.getZ(), end.getZ()))) >> 4;
        int maximumChunkZ = ((int) Math.floor(Math.max(start.getZ(), end.getZ()))) >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ)) continue;
                BoundingBox chunk = new BoundingBox(chunkX * 16.0, -1.0E9, chunkZ * 16.0,
                        chunkX * 16.0 + 16, 1.0E9, chunkZ * 16.0 + 16);
                RayTraceResult crossing = chunk.rayTrace(start.toVector(), direction, distance);
                if (crossing != null) loadedDistance = Math.min(loadedDistance,
                        Math.max(0, crossing.getHitPosition().distance(start.toVector()) - 0.01));
            }
        }
        if (loadedDistance <= 0) return 0;
        RayTraceResult hit = world.rayTraceBlocks(start, direction, loadedDistance,
                FluidCollisionMode.NEVER, false);
        return hit == null ? (loadedDistance < distance ? loadedDistance : Double.POSITIVE_INFINITY)
                : hit.getHitPosition().distance(start.toVector());
    }

    boolean clearSurface(Vector center, Vector right, Vector up,
                         double halfWidth, double halfHeight, double clearance) {
        Vector normal = right.clone().crossProduct(up).normalize();
        Vector extent = absolute(right).multiply(halfWidth)
                .add(absolute(up).multiply(halfHeight)).add(absolute(normal).multiply(clearance));
        BoundingBox envelope = new BoundingBox(
                center.getX() - extent.getX(), center.getY() - extent.getY(), center.getZ() - extent.getZ(),
                center.getX() + extent.getX(), center.getY() + extent.getY(), center.getZ() + extent.getZ());
        if (envelope.getMinY() < world.getMinHeight() || envelope.getMaxY() > world.getMaxHeight()) {
            return false;
        }
        List<Vector> axes = List.of(right, up, normal);
        double[] halfSizes = {halfWidth, halfHeight, clearance};
        for (int blockX = (int) Math.floor(envelope.getMinX()); blockX <= Math.floor(envelope.getMaxX()); blockX++) {
            for (int blockZ = (int) Math.floor(envelope.getMinZ()); blockZ <= Math.floor(envelope.getMaxZ()); blockZ++) {
                if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) return false;
                for (int blockY = (int) Math.floor(envelope.getMinY()); blockY <= Math.floor(envelope.getMaxY()); blockY++) {
                    BlockPosition position = new BlockPosition(blockX, blockY, blockZ);
                    List<BoundingBox> boxes = collisions.computeIfAbsent(position, key ->
                            world.getBlockAt(key.x(), key.y(), key.z()).getCollisionShape()
                                    .getBoundingBoxes().stream()
                                    .map(box -> box.clone().shift(key.x(), key.y(), key.z())).toList());
                    for (BoundingBox box : boxes) {
                        if (envelope.overlaps(box) && overlaps(center, axes, halfSizes, box)) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean overlaps(Vector center, List<Vector> axes, double[] halfSizes, BoundingBox box) {
        Vector delta = box.getCenter().subtract(center);
        Vector boxHalf = new Vector(box.getWidthX() / 2, box.getHeight() / 2, box.getWidthZ() / 2);
        for (Vector axis : WORLD_AXES) {
            if (separated(axis, delta, axes, halfSizes, boxHalf)) return false;
        }
        for (Vector axis : axes) {
            if (separated(axis, delta, axes, halfSizes, boxHalf)) return false;
            for (Vector worldAxis : WORLD_AXES) {
                Vector cross = axis.clone().crossProduct(worldAxis);
                if (cross.lengthSquared() > 1.0E-10
                        && separated(cross, delta, axes, halfSizes, boxHalf)) return false;
            }
        }
        return true;
    }

    private static boolean separated(Vector axis, Vector delta, List<Vector> axes,
                                     double[] halfSizes, Vector boxHalf) {
        double radius = absolute(axis).dot(boxHalf);
        for (int index = 0; index < axes.size(); index++) {
            radius += Math.abs(axis.dot(axes.get(index))) * halfSizes[index];
        }
        return Math.abs(axis.dot(delta)) >= radius - 1.0E-8;
    }

    private static Vector absolute(Vector vector) {
        return new Vector(Math.abs(vector.getX()), Math.abs(vector.getY()), Math.abs(vector.getZ()));
    }

    private record BlockPosition(int x, int y, int z) { }
}
