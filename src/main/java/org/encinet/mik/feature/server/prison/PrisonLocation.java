package org.encinet.mik.feature.server.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

record PrisonLocation(UUID world, double x, double y, double z, float yaw, float pitch) {
    PrisonLocation {
        if (world == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch))
            throw new IllegalArgumentException("Invalid prison location");
    }

    static PrisonLocation capture(Location location) {
        if (location == null || location.getWorld() == null) throw new IllegalArgumentException("World required");
        return new PrisonLocation(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    Location resolve() {
        World resolved = Bukkit.getWorld(world);
        return resolved == null ? null : new Location(resolved, x, y, z, yaw, pitch);
    }
}
