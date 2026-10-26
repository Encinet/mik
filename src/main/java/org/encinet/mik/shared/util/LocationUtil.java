package org.encinet.mik.shared.util;

import org.bukkit.Location;

import java.util.Objects;

public final class LocationUtil {
    private LocationUtil() {}

    public static boolean sameBlock(Location first, Location second) {
        return first != null && second != null
                && Objects.equals(first.getWorld(), second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }
}
