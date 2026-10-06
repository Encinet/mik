package org.encinet.mik.module.menu.runtime;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/** Rejects a menu surface when a solid block is closer along the viewer's ray. */
final class FloatingMenuWorldOcclusion {
    private static final double SURFACE_CLEARANCE = 0.04;

    private FloatingMenuWorldOcclusion() { }

    static boolean clearToSurface(Location eye, double surfaceDistance) {
        if (eye == null || !Double.isFinite(surfaceDistance) || surfaceDistance <= 0.0) {
            return false;
        }
        World world = eye.getWorld();
        if (world == null) return false;
        double rayDistance = surfaceDistance - SURFACE_CLEARANCE;
        if (rayDistance <= 0.0) return true;
        Vector direction = eye.getDirection();
        if (!Double.isFinite(direction.getX()) || !Double.isFinite(direction.getY())
                || !Double.isFinite(direction.getZ())
                || direction.lengthSquared() < 1.0E-8) {
            return false;
        }
        return new FloatingMenuWorldSpace(world).firstHit(eye, direction.normalize(), rayDistance) >= rayDistance;
    }
}
