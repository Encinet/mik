package org.encinet.mik.module.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.function.Consumer;

public class PlayerBoundaryModule implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final double EPSILON = 0.01;               // 防边界线抖动
    private static final double BOUNDARY_MARGIN = 0.5 + EPSILON; // 统一内部缓冲

    private final JavaPlugin plugin;
    private final LanguageService languageService;

    public PlayerBoundaryModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("PlayerBoundaryModule enabled");
    }

    /** 若坐标越界则修正到边界内，并通过 setter 设置新坐标，发送提示 */
    private void correctIfOutside(Location loc, Player player,
                                  Consumer<Location> setter) {
        World world = loc.getWorld();
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();
        if (!border.isInside(loc)) {
            setter.accept(clampInside(loc, border));
            player.sendActionBar(buildMessage(player, Message.BOUNDARY_REACHED_MM));
        }
    }

    /** 将坐标夹至边界内 BOUNDARY_MARGIN 处 */
    private Location clampInside(Location loc, WorldBorder border) {
        Location center = border.getCenter();
        double half = Math.max(0, border.getSize() / 2.0 - BOUNDARY_MARGIN);

        double x = Math.clamp(loc.getX(), center.getX() - half, center.getX() + half);
        double z = Math.clamp(loc.getZ(), center.getZ() - half, center.getZ() + half);

        return new Location(loc.getWorld(), x, loc.getY(), z, loc.getYaw(), loc.getPitch());
    }

    private Component buildMessage(Player player, Message msg) {
        return MINI_MESSAGE.deserialize(languageService.t(player, msg));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        Player player = event.getPlayer();
        World world = to.getWorld();
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();
        boolean fromOutside = !border.isInside(from);
        boolean toOutside = !border.isInside(to);

        if (!fromOutside && !toOutside) return;

        if (fromOutside) {
            // 已在外面 直接拉回
            event.setTo(clampInside(to, border));
        } else {
            // 试图出去 取消移动
            event.setCancelled(true);
        }
        player.sendActionBar(buildMessage(player, Message.BOUNDARY_NEAR_MM));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();

        correctIfOutside(to, event.getPlayer(), event::setTo);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location respawn = event.getRespawnLocation();

        correctIfOutside(respawn, event.getPlayer(),
                event::setRespawnLocation);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();

        correctIfOutside(loc, player,
                player::teleport);
    }
}