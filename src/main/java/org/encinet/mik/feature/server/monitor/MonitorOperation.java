package org.encinet.mik.feature.server.monitor;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Main-thread snapshots of the watched player's actual interaction surface or entity. */
final class MonitorOperation {
    private static final int RETAIN_TICKS = 12;
    private static final double MAX_RANGE = 16;
    private static final double SURFACE_MARGIN = 0.04;
    private @Nullable Focus recent;
    private @Nullable Entity recentEntity;
    private int remaining;

    record Focus(Location point, BoundingBox bounds, BoundingBox subjectBounds, @Nullable UUID entityId) {
        Focus during(@Nullable Focus previous, Vector bodyShift, double fraction) {
            boolean sameEntity = entityId != null && previous != null && entityId.equals(previous.entityId());
            Vector pointShift = sameEntity ? previous.point().toVector().subtract(point.toVector()).multiply(1 - fraction) : new Vector();
            Vector objectShift = sameEntity ? previous.bounds().getCenter().subtract(bounds.getCenter()).multiply(1 - fraction) : new Vector();
            return new Focus(point.clone().add(pointShift), bounds.clone().shift(objectShift),
                    subjectBounds.clone().shift(bodyShift), entityId);
        }
    }

    void reset() {
        recent = null;
        recentEntity = null;
        remaining = 0;
    }

    void block(Player player, Block block, @Nullable Location point, @Nullable BlockFace face) {
        recent = blockFocus(player, block, point, face);
        recentEntity = null;
        remaining = RETAIN_TICKS;
    }

    void entity(Player player, Entity entity) {
        recent = entityFocus(player, entity, null);
        recentEntity = entity;
        remaining = RETAIN_TICKS;
    }

    @Nullable Focus sample(Player player) {
        Location eye = player.getEyeLocation();
        double blockRange = range(player, Attribute.BLOCK_INTERACTION_RANGE, player.getGameMode() == GameMode.CREATIVE ? 5 : 4.5);
        double entityRange = range(player, Attribute.ENTITY_INTERACTION_RANGE, player.getGameMode() == GameMode.CREATIVE ? 5 : 3);
        if (remaining > 0 && recent != null) {
            remaining--;
            if (recentEntity != null && recentEntity.isValid() && recentEntity.getWorld().equals(eye.getWorld())) {
                recent = entityFocus(player, recentEntity, null);
            } else if (recentEntity != null) {
                reset();
            }
            if (recent != null && recent.point().getWorld().equals(eye.getWorld())) {
                Vector delta = recent.point().toVector().subtract(eye.toVector());
                double reach = (recentEntity == null ? blockRange : entityRange) + 1;
                if (delta.lengthSquared() <= reach * reach && MonitorSight.loaded(eye, recent.point())
                        && (delta.lengthSquared() < 1.0e-6 || eye.getDirection().dot(delta.normalize()) >= 0.25)) {
                    return new Focus(recent.point(), recent.bounds(), subjectBounds(player), recent.entityId());
                }
            }
            reset();
        } else {
            reset();
        }

        Vector direction = eye.getDirection();
        double blockDistance = loadedReach(eye, direction, Math.max(blockRange, entityRange));
        RayTraceResult blockHit = blockDistance > 0 ? eye.getWorld().rayTraceBlocks(
                eye, direction, blockDistance, FluidCollisionMode.NEVER, false) : null;
        double entityDistance = Math.min(entityRange, blockHit == null ? entityRange
                : blockHit.getHitPosition().distance(eye.toVector()));
        entityDistance = loadedReach(eye, direction, entityDistance);
        RayTraceResult entityHit = entityDistance > 0 ? eye.getWorld().rayTraceEntities(
                eye, direction, entityDistance, 0.1, entity -> !entity.equals(player)
                        && entity.isValid() && !entity.isDead() && player.canSee(entity)) : null;
        if (entityHit != null && entityHit.getHitEntity() != null) {
            return entityFocus(player, entityHit.getHitEntity(), entityHit.getHitPosition().toLocation(eye.getWorld()));
        }
        if (blockHit != null && blockHit.getHitBlock() != null
                && blockHit.getHitPosition().distanceSquared(eye.toVector()) <= blockRange * blockRange) {
            return blockFocus(player, blockHit.getHitBlock(), blockHit.getHitPosition().toLocation(eye.getWorld()), blockHit.getHitBlockFace());
        }
        return null;
    }

    private static Focus blockFocus(Player player, Block block, @Nullable Location point, @Nullable BlockFace face) {
        BoundingBox bounds = block.getBoundingBox();
        if (bounds.getVolume() < 1.0e-6) {
            bounds = BoundingBox.of(block.getLocation(), block.getLocation().add(1, 1, 1));
        }
        if (point == null) {
            RayTraceResult hit = surface(bounds, player.getEyeLocation());
            point = (hit == null ? bounds.getCenter() : hit.getHitPosition()).toLocation(block.getWorld());
            if (hit != null) face = hit.getHitBlockFace();
        }
        Location surface = point.clone();
        if (face != null) surface.add(face.getDirection().multiply(SURFACE_MARGIN));
        return new Focus(surface, bounds, subjectBounds(player), null);
    }

    private static Focus entityFocus(Player player, Entity entity, @Nullable Location point) {
        BoundingBox bounds = entity.getBoundingBox();
        if (point == null) {
            RayTraceResult hit = surface(bounds, player.getEyeLocation());
            point = (hit == null ? bounds.getCenter() : hit.getHitPosition()).toLocation(entity.getWorld());
        }
        return new Focus(point, bounds, subjectBounds(player), entity.getUniqueId());
    }

    private static @Nullable RayTraceResult surface(BoundingBox bounds, Location eye) {
        Vector delta = bounds.getCenter().subtract(eye.toVector());
        double distance = delta.length();
        Vector direction = distance > 1.0e-6 ? delta.multiply(1 / distance) : eye.getDirection();
        return bounds.rayTrace(eye.toVector(), direction, distance + bounds.getMin().distance(bounds.getMax()) + 1);
    }

    private static BoundingBox subjectBounds(Player player) {
        return player.getBoundingBox();
    }

    private static double range(Player player, Attribute attribute, double fallback) {
        var value = player.getAttribute(attribute);
        return Math.clamp(value == null ? fallback : value.getValue(), 0, MAX_RANGE);
    }

    private static double loadedReach(Location eye, Vector direction, double reach) {
        // Stop before an unloaded boundary; sensing must never cause chunk loads.
        while (reach > 0 && !MonitorSight.loaded(eye, eye.clone().add(direction.clone().multiply(reach)))) reach -= 0.25;
        return Math.max(0, reach);
    }
}
