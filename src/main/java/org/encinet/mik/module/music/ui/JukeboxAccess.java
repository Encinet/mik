package org.encinet.mik.module.music.ui;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Shared main-thread access rules for controlling a physical jukebox. */
public final class JukeboxAccess {

    public static final int CONTROL_DISTANCE_BLOCKS = 8;
    private static final double CONTROL_DISTANCE_SQUARED = CONTROL_DISTANCE_BLOCKS * CONTROL_DISTANCE_BLOCKS;

    private JukeboxAccess() {
    }

    public static boolean isAvailable(Location location) {
        return location != null
                && location.getWorld() != null
                && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                && location.getBlock().getType() == Material.JUKEBOX;
    }

    public static boolean canControl(Player player, Location location) {
        return player != null
                && isAvailable(location)
                && player.getWorld().equals(location.getWorld())
                && player.getLocation().distanceSquared(location) <= CONTROL_DISTANCE_SQUARED;
    }
}
