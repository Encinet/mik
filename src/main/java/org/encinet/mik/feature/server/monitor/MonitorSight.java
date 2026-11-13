package org.encinet.mik.feature.server.monitor;

import org.bukkit.Location;
import org.bukkit.World;

/** Loaded query envelope shared by camera placement and interaction sensing. */
final class MonitorSight {
    static final double MARGIN = 0.35;

    private MonitorSight() { }

    static boolean loaded(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return false;
        World world = from.getWorld();
        int minX = (int) Math.floor((Math.min(from.getX(), to.getX()) - MARGIN) / 16);
        int maxX = (int) Math.floor((Math.max(from.getX(), to.getX()) + MARGIN) / 16);
        int minZ = (int) Math.floor((Math.min(from.getZ(), to.getZ()) - MARGIN) / 16);
        int maxZ = (int) Math.floor((Math.max(from.getZ(), to.getZ()) + MARGIN) / 16);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x, z)) return false;
            }
        }
        return true;
    }
}
