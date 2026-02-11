package org.encinet.mik.module;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;

import java.util.concurrent.TimeUnit;

/**
 * Module for automatic player promotion based on playtime and join date
 */
public class AutoPromoteModule implements Listener {

    private static final long JOIN_DAYS_MILLIS = TimeUnit.DAYS.toMillis(2);
    private static final int PLAYED_HOURS_TICKS = 20 * 60 * 60 * 8; // 8 hours in ticks
    private static final int FLAY_ON_CN = 1200000;

    private final JavaPlugin plugin;
    private LuckPerms luckPerms;

    public AutoPromoteModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enable auto-promote module (called in onEnable)
     */
    public void enable() {
        // Get LuckPerms API
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning("LuckPerms not found! AutoPromoteModule disabled.");
            return;
        }
        luckPerms = provider.getProvider();

        // Register event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("AutoPromoteModule enabled");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check conditions asynchronously to avoid blocking main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (shouldPromotePlayer(player)) {
                promotePlayer(player);
            }
        });
    }

    /**
     * Check if player meets promotion criteria
     */
    private boolean shouldPromotePlayer(Player player) {
        if (player.hasPermission("group." + Mik.GROUP_MEMBER)) {
            return false;
        }

        // Check if first join was more than 2 days ago
        long firstPlayed = player.getFirstPlayed();
        long currentTime = System.currentTimeMillis();
        long timeSinceFirstJoin = currentTime - firstPlayed;

        if (timeSinceFirstJoin < JOIN_DAYS_MILLIS) {
            return false;
        }

        // Check if playtime is more than 8 hours
        int playTimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        if (playTimeTicks < PLAYED_HOURS_TICKS) {
            return false;
        }

        // Check if playtime is more than 8 hours
        int playerFly = player.getStatistic(Statistic.FLY_ONE_CM);
        return playerFly < FLAY_ON_CN;
    }

    /**
     * Promote player to member group
     */
    private void promotePlayer(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return;
        }

        // Add member group
        Node memberNode = Node.builder("group." + Mik.GROUP_MEMBER).build();
        user.data().add(memberNode);

        // Set member as primary group
        user.setPrimaryGroup(Mik.GROUP_MEMBER);

        // Save changes
        luckPerms.getUserManager().saveUser(user);

        // Notify player on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            Component message = Component.text("恭喜！", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text(" 你已成为 ", NamedTextColor.GREEN))
                    .append(Component.text("正式成员", NamedTextColor.GOLD))
                    .append(Component.text(" , 现在你拥有更多的命令和功能访问权限，比如可以进行投影一键打印！", NamedTextColor.GREEN));
            player.sendMessage(message);
        });

        plugin.getLogger().info("Promoted player " + player.getName() + " to member group");
    }
}
