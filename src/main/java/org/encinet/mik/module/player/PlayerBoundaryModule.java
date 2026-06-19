package org.encinet.mik.module.player;

import net.kyori.adventure.text.minimessage.MiniMessage;
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
    private static final double KNOCKBACK_STRENGTH = 1.5; // Velocity multiplier for knockback
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        Player player = event.getPlayer();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        World world = to.getWorld();
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();
        if (!isOutsideBoundary(to, border)) return;

        // 将位置夹到边界内（留 0.5 块缓冲），不重置到 from
        Location clamped = clampToBoundary(to, border, 0.5);
        event.setTo(clamped);

        applyReflectKnockback(player, to, border);
        player.sendActionBar(MINI_MESSAGE.deserialize("<yellow>你正在接近世界边界</yellow>"));
    }

    /**
     * 将位置夹到边界内 margin 块处
     */
    private Location clampToBoundary(Location loc, WorldBorder border, double margin) {
        Location center = border.getCenter();
        double half = border.getSize() / 2.0 - margin;

        double x = Math.max(center.getX() - half, Math.min(center.getX() + half, loc.getX()));
        double z = Math.max(center.getZ() - half, Math.min(center.getZ() + half, loc.getZ()));

        return new Location(loc.getWorld(), x, loc.getY(), z, loc.getYaw(), loc.getPitch());
    }

    /**
     * 反射速度 + 朝内推力，产生自然弹开感
     */
    private void applyReflectKnockback(Player player, Location to, WorldBorder border) {
        Location center = border.getCenter();
        double half = border.getSize() / 2.0;

        double dx = to.getX() - center.getX();
        double dz = to.getZ() - center.getZ();

        Vector current = player.getVelocity();
        double velX = current.getX();
        double velZ = current.getZ();

        if (Math.abs(dx) >= half) {
            // 朝内：速度分量取反（吸收）再加朝内推力，符号与 dx 相反
            velX = -Math.abs(current.getX()) * 0.8 - KNOCKBACK_STRENGTH * Math.signum(dx);
        }
        if (Math.abs(dz) >= half) {
            velZ = -Math.abs(current.getZ()) * 0.8 - KNOCKBACK_STRENGTH * Math.signum(dz);
        }

        velX = Math.clamp(velX, -3.0, 3.0);
        velZ = Math.clamp(velZ, -3.0, 3.0);

        player.setVelocity(new Vector(velX, Math.max(current.getY(), 0.15), velZ));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        Player player = event.getPlayer();
        World world = to.getWorld();
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();

        if (isOutsideBoundary(to, border)) {
            // For teleports: always redirect to safe location, never apply knockback
            Location safe = getNearestSafeLocation(to, border);
            event.setTo(safe);
            player.sendActionBar(MINI_MESSAGE.deserialize("<red>你已到达世界边界</red>"));
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

}
