package org.encinet.mik.module;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Module for enforcing world boundaries on player movement and teleportation
 */
public class PlayerBoundaryModule implements Listener {

    private static final double SOFT_BOUNDARY_DISTANCE = 50.0; // Distance from boundary for knockback
    private static final double KNOCKBACK_STRENGTH = 1.5; // Velocity multiplier for knockback

    private final JavaPlugin plugin;

    public PlayerBoundaryModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enable boundary module
     */
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("PlayerBoundaryModule enabled (using world border settings)");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();

        Player player = event.getPlayer();
        World world = to.getWorld();
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();

        // Check if player is outside boundary
        if (isOutsideBoundary(to, border)) {
            handleBoundaryViolation(player, event.getFrom(), to, border, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();

        Player player = event.getPlayer();
        World world = to.getWorld();
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();

        // Check if teleport destination is outside boundary
        if (isOutsideBoundary(to, border)) {
            handleBoundaryViolation(player, event.getFrom(), to, border, event);
        }
    }

    /**
     * Check if location is outside the world border
     */
    private boolean isOutsideBoundary(Location location, WorldBorder border) {
        double x = location.getX();
        double z = location.getZ();

        Location center = border.getCenter();
        double size = border.getSize() / 2.0;

        double minX = center.getX() - size;
        double maxX = center.getX() + size;
        double minZ = center.getZ() - size;
        double maxZ = center.getZ() + size;

        return x < minX || x > maxX || z < minZ || z > maxZ;
    }

    /**
     * Calculate how far the player is outside the boundary
     * Returns 0 if inside boundary, positive value if outside
     */
    private double getDistanceOutsideBoundary(Location location, WorldBorder border) {
        double x = location.getX();
        double z = location.getZ();

        Location center = border.getCenter();
        double size = border.getSize() / 2.0;

        double minX = center.getX() - size;
        double maxX = center.getX() + size;
        double minZ = center.getZ() - size;
        double maxZ = center.getZ() + size;

        double distX = 0;
        double distZ = 0;

        // Calculate how far outside the boundary in X direction
        if (x < minX) {
            distX = minX - x;
        } else if (x > maxX) {
            distX = x - maxX;
        }

        // Calculate how far outside the boundary in Z direction
        if (z < minZ) {
            distZ = minZ - z;
        } else if (z > maxZ) {
            distZ = z - maxZ;
        }

        // Return the maximum distance outside (since we care about the furthest violation)
        return Math.max(distX, distZ);
    }

    /**
     * Handle player attempting to cross boundary
     */
    private void handleBoundaryViolation(Player player, Location from, Location to, WorldBorder border, PlayerMoveEvent event) {
        double distanceOutside = getDistanceOutsideBoundary(to, border);

        // If player is far outside boundary, teleport them back
        if (distanceOutside > SOFT_BOUNDARY_DISTANCE) {
            Location safeLocation = getNearestSafeLocation(to, border);
            event.setTo(safeLocation);

            player.sendMessage(Component.text("你已到达世界边界！", NamedTextColor.RED));
            plugin.getLogger().info("Teleported " + player.getName() + " back from boundary (distance outside: " + distanceOutside + ")");
        } else {
            // Apply knockback for soft boundary
            event.setTo(from);
            applyKnockback(player, from, to);

            player.sendMessage(Component.text("警告：你正在接近世界边界！", NamedTextColor.YELLOW));
        }
    }

    /**
     * Get nearest safe location within boundary
     */
    private Location getNearestSafeLocation(Location location, WorldBorder border) {
        World world = location.getWorld();

        Location center = border.getCenter();
        double size = border.getSize() / 2.0;

        double minX = center.getX() - size;
        double maxX = center.getX() + size;
        double minZ = center.getZ() - size;
        double maxZ = center.getZ() + size;

        double x = Math.max(minX, Math.min(maxX, location.getX()));
        double z = Math.max(minZ, Math.min(maxZ, location.getZ()));

        // Add small buffer to ensure player is inside boundary
        if (x == minX) x += 5;
        if (x == maxX) x -= 5;
        if (z == minZ) z += 5;
        if (z == maxZ) z -= 5;

        return new Location(world, x, location.getY(), z, location.getYaw(), location.getPitch());
    }

    /**
     * Apply knockback velocity to push player away from boundary
     */
    private void applyKnockback(Player player, Location from, Location to) {
        // Calculate direction from boundary violation point back to safe location
        Vector direction = from.toVector().subtract(to.toVector()).normalize();

        // Apply horizontal knockback only (preserve Y velocity)
        Vector knockback = direction.multiply(KNOCKBACK_STRENGTH);
        knockback.setY(0.2); // Small upward boost

        player.setVelocity(knockback);
    }
}
