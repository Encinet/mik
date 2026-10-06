package org.encinet.mik.module.vehicle;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class VehicleWorld implements VehiclePhysics.Environment {
    private record BlockPosition(int coordinateX, int coordinateY, int coordinateZ) { }
    private final World world;
    private final Map<BlockPosition, List<VehicleCollision.Box>> blocks = new HashMap<>();
    private final List<VehicleCollision.Box> otherVehicles;

    VehicleWorld(World world, List<VehicleCollision.Box> otherVehicles) {
        this.world = world;
        this.otherVehicles = otherVehicles;
    }

    @Override public double groundDistance(VehicleVector point, double maximum) {
        if (!loaded(point) || point.coordinateY() < world.getMinHeight() || point.coordinateY() >= world.getMaxHeight())
            return Double.POSITIVE_INFINITY;
        RayTraceResult result = world.rayTraceBlocks(location(point.add(new VehicleVector(0, 0.25, 0))),
                new Vector(0, -1, 0), maximum + 0.25, FluidCollisionMode.NEVER, true);
        return result == null ? Double.POSITIVE_INFINITY : point.coordinateY() - result.getHitPosition().getY();
    }

    @Override public double waterDepth(VehicleVector point) {
        if (!loaded(point)) return 0;
        int blockY = (int) Math.floor(point.coordinateY());
        double surface = point.coordinateY();
        for (int offset = 0; offset < 4; offset++) {
            int height = blockY + offset;
            if (height < world.getMinHeight() || height >= world.getMaxHeight()) break;
            Block block = world.getBlockAt((int) Math.floor(point.coordinateX()), height, (int) Math.floor(point.coordinateZ()));
            boolean wet = block.getType() == Material.WATER
                    || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
            if (!wet) break;
            double level = block.getBlockData() instanceof Levelled levelled && levelled.getLevel() < 8
                    ? (8 - levelled.getLevel()) / 9.0 : 1;
            surface = height + level;
        }
        return Math.max(0, surface - point.coordinateY());
    }

    @Override public VehicleVector wind() { return VehicleVector.ZERO; }

    boolean loaded(VehicleVector point) {
        return world.isChunkLoaded(((int) Math.floor(point.coordinateX())) >> 4, ((int) Math.floor(point.coordinateZ())) >> 4);
    }

    boolean clear(VehicleBody body) {
        for (VehicleDefinition.Collider collider : body.definition.colliders()) {
            VehicleCollision.Box box = VehicleCollision.Box.body(collider, body.origin(), body.orientation);
            for (VehicleCollision.Box obstacle : obstacles(box, VehicleVector.ZERO)) {
                VehicleCollision.Hit hit = VehicleCollision.sweep(box, VehicleVector.ZERO, obstacle);
                if (hit != null && hit.penetration() > 0.02) return false;
            }
        }
        return true;
    }

    void move(VehicleBody body, VehiclePhysics.Motion motion) {
        VehicleVector remaining = motion.displacement();
        Quaterniond oldOrientation = new Quaterniond(body.orientation);
        body.orientation.set(motion.orientation());
        for (int iteration = 0; iteration < 4; iteration++) {
            VehicleCollision.Hit first = null;
            for (VehicleDefinition.Collider collider : body.definition.colliders()) {
                VehicleCollision.Box box = VehicleCollision.Box.body(collider, body.origin(), body.orientation);
                VehicleCollision.Box old = VehicleCollision.Box.body(collider,
                        body.position.subtract(body.definition.centerOfMass().rotate(oldOrientation)), oldOrientation);
                VehicleVector minimum = minimum(box.center().subtract(box.extent()), old.center().subtract(old.extent()));
                VehicleVector maximum = maximum(box.center().add(box.extent()), old.center().add(old.extent()));
                VehicleCollision.Box broad = VehicleCollision.Box.axisAligned(minimum, maximum);
                for (VehicleCollision.Box obstacle : obstacles(broad, remaining)) {
                    VehicleCollision.Hit hit = VehicleCollision.sweep(box, remaining, obstacle);
                    VehicleCollision.Hit oldHit = VehicleCollision.sweep(old, remaining, obstacle);
                    if (oldHit != null && (hit == null || oldHit.fraction() < hit.fraction())) hit = oldHit;
                    if (hit != null && (first == null || hit.fraction() < first.fraction()
                            || hit.fraction() == first.fraction() && hit.penetration() > first.penetration())) first = hit;
                }
            }
            if (first == null) { body.position = body.position.add(remaining); return; }
            body.blocked = true;
            body.position = body.position.add(remaining.multiply(Math.max(0, first.fraction() - 0.001)))
                    .add(first.normal().multiply(Math.min(0.5, first.penetration()) + 0.001));
            double impact = body.velocity.dot(first.normal());
            if (impact < 0) {
                body.health = Math.max(0, body.health - Math.max(0, -impact - 4) * 0.7);
                body.velocity = body.velocity.subtract(first.normal().multiply(impact));
            }
            if (first.normal().coordinateY() > 0.5) body.grounded = true;
            else {
                body.angularVelocity = body.angularVelocity.multiply(0.75);
                body.orientation.set(oldOrientation);
            }
            remaining = remaining.multiply(1 - first.fraction());
            double inward = remaining.dot(first.normal());
            if (inward < 0) remaining = remaining.subtract(first.normal().multiply(inward));
            if (remaining.length() < 1.0E-6 && first.penetration() < 0.01) return;
        }
        body.velocity = VehicleVector.ZERO;
    }

    private List<VehicleCollision.Box> obstacles(VehicleCollision.Box box, VehicleVector travel) {
        VehicleVector extent = box.extent().add(new VehicleVector(0.1, 0.1, 0.1));
        VehicleVector start = box.center().subtract(extent);
        VehicleVector end = box.center().add(extent);
        int minimumX = (int) Math.floor(start.coordinateX() + Math.min(0, travel.coordinateX()));
        int maximumX = (int) Math.floor(end.coordinateX() + Math.max(0, travel.coordinateX()));
        int minimumY = (int) Math.floor(start.coordinateY() + Math.min(0, travel.coordinateY()));
        int maximumY = (int) Math.floor(end.coordinateY() + Math.max(0, travel.coordinateY()));
        int minimumZ = (int) Math.floor(start.coordinateZ() + Math.min(0, travel.coordinateZ()));
        int maximumZ = (int) Math.floor(end.coordinateZ() + Math.max(0, travel.coordinateZ()));
        long volume = (long) (maximumX - minimumX + 1) * (maximumY - minimumY + 1) * (maximumZ - minimumZ + 1);
        if (volume > 32768) throw new IllegalStateException("Vehicle collision query exceeds budget");
        List<VehicleCollision.Box> result = new ArrayList<>();
        result.addAll(otherVehicles);
        double halfBorder = world.getWorldBorder().getSize() / 2;
        Location borderCenter = world.getWorldBorder().getCenter();
        for (int blockX = minimumX; blockX <= maximumX; blockX++) {
            for (int blockZ = minimumZ; blockZ <= maximumZ; blockZ++) {
                boolean accessible = world.isChunkLoaded(blockX >> 4, blockZ >> 4)
                        && blockX >= borderCenter.getX() - halfBorder && blockX + 1 <= borderCenter.getX() + halfBorder
                        && blockZ >= borderCenter.getZ() - halfBorder && blockZ + 1 <= borderCenter.getZ() + halfBorder;
                for (int blockY = minimumY; blockY <= maximumY; blockY++) {
                    if (!accessible || blockY < world.getMinHeight() || blockY >= world.getMaxHeight()) {
                        result.add(VehicleCollision.Box.axisAligned(new VehicleVector(blockX, blockY, blockZ),
                                new VehicleVector(blockX + 1, blockY + 1, blockZ + 1)));
                    } else {
                        BlockPosition position = new BlockPosition(blockX, blockY, blockZ);
                        result.addAll(blocks.computeIfAbsent(position, key -> world.getBlockAt(key.coordinateX(), key.coordinateY(), key.coordinateZ())
                                .getCollisionShape().getBoundingBoxes().stream().map(bounds -> blockBox(bounds, key)).toList()));
                    }
                }
            }
        }
        return result;
    }

    private VehicleCollision.Box blockBox(BoundingBox box, BlockPosition position) {
        return VehicleCollision.Box.axisAligned(new VehicleVector(position.coordinateX() + box.getMinX(), position.coordinateY() + box.getMinY(),
                position.coordinateZ() + box.getMinZ()), new VehicleVector(position.coordinateX() + box.getMaxX(), position.coordinateY() + box.getMaxY(),
                position.coordinateZ() + box.getMaxZ()));
    }

    private Location location(VehicleVector point) { return new Location(world, point.coordinateX(), point.coordinateY(), point.coordinateZ()); }
    private static VehicleVector minimum(VehicleVector first, VehicleVector second) {
        return new VehicleVector(Math.min(first.coordinateX(), second.coordinateX()), Math.min(first.coordinateY(), second.coordinateY()), Math.min(first.coordinateZ(), second.coordinateZ()));
    }
    private static VehicleVector maximum(VehicleVector first, VehicleVector second) {
        return new VehicleVector(Math.max(first.coordinateX(), second.coordinateX()), Math.max(first.coordinateY(), second.coordinateY()), Math.max(first.coordinateZ(), second.coordinateZ()));
    }
}
