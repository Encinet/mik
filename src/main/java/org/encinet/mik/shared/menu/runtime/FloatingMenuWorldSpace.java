package org.encinet.mik.shared.menu.runtime;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry;
import org.encinet.mik.shared.menu.runtime.space.MenuBlockCollisionSnapshot;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class FloatingMenuWorldSpace {
    static final double MAX_TRACE_DISTANCE = 64;
    private final World world;
    private final Vector snapshotOrigin;
    private final List<Box> entities;
    private final MenuBlockCollisionSnapshot terrain;

    FloatingMenuWorldSpace(World world) {
        this.world = Objects.requireNonNull(world, "world");
        this.terrain = new MenuBlockCollisionSnapshot(world);
        this.snapshotOrigin = new Vector();
        this.entities = List.of();
    }

    FloatingMenuWorldSpace(Location eye, List<Box> entities) {
        this(eye, entities, new MenuBlockCollisionSnapshot(Objects.requireNonNull(eye.getWorld(), "world")));
    }

    private FloatingMenuWorldSpace(Location eye, List<Box> entities, MenuBlockCollisionSnapshot terrain) {
        this.world = Objects.requireNonNull(eye.getWorld(), "world");
        this.terrain = terrain;
        this.snapshotOrigin = eye.toVector();
        this.entities = List.copyOf(entities);
    }

    /** Parent and companion share the same loaded-block reads and placement budget, with their own eye-relative entities. */
    FloatingMenuWorldSpace forCompanion(Location eye, List<Box> entities) {
        if (!world.equals(eye.getWorld())) throw new IllegalArgumentException("A companion must share its parent's world");
        return new FloatingMenuWorldSpace(eye, entities, terrain);
    }

    double firstHit(Location start, Vector direction, double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > MAX_TRACE_DISTANCE) return 0;
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
        double blockDistance = hit == null ? (loadedDistance < distance ? loadedDistance : Double.POSITIVE_INFINITY)
                : hit.getHitPosition().distance(start.toVector());
        if (hit != null && !Double.isFinite(blockDistance)) return 0;
        double result = Math.min(blockDistance, MenuOcclusionGeometry.firstHit(
                start.toVector().subtract(snapshotOrigin), direction, loadedDistance, entities));
        return Double.isNaN(result) ? 0 : result;
    }

    /** The whole eye-to-panel volume must be clear, including obstacles between all point probes. */
    double limitSurface(Location eye, Vector center, Vector right, Vector up,
                        double halfWidth, double halfHeight, double clearance) {
        if (!entities.isEmpty() && snapshotOrigin.distanceSquared(eye.toVector()) > 1.0E-10)
            throw new IllegalArgumentException("Entity obstacle snapshot belongs to a different eye position");
        Vector normal = right.clone().crossProduct(up).normalize();
        List<Vector> vertices = new ArrayList<>(9);
        vertices.add(eye.toVector());
        for (int corner = 0; corner < 8; corner++) {
            vertices.add(center.clone().add(right.clone().multiply((corner & 1) == 0 ? -halfWidth : halfWidth))
                    .add(up.clone().multiply((corner & 2) == 0 ? -halfHeight : halfHeight))
                    .add(normal.clone().multiply((corner & 4) == 0 ? -clearance : clearance)));
        }
        var blocks = terrain.capture(MenuOcclusionGeometry.bounds(vertices), eye.toVector());
        if (!blocks.complete()) return 0;
        List<Box> boxes = new ArrayList<>(entities);
        boxes.addAll(blocks.boxes());
        return MenuOcclusionGeometry.limit(center.clone().subtract(eye.toVector()), right, up,
                halfWidth, halfHeight, clearance, boxes);
    }

}
