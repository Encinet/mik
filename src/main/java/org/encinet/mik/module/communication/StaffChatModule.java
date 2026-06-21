package org.encinet.mik.module.communication;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Module for staff chat functionality
 */
public class StaffChatModule implements Listener {

    private static final String STAFF_PERMISSION = "mik.staff";
    private final JavaPlugin plugin;
    private final Set<UUID> staffChatPlayers = new HashSet<>();

    public StaffChatModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            // Register /staffchat command
            commands.register(Commands.literal("staffchat")
                    .requires(source -> source.getSender().hasPermission("mik.staff"))
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        Entity executor = ctx.getSource().getExecutor();
                        if (executor instanceof Player player) {
                            toggleStaffChat(player);
                            return Command.SINGLE_SUCCESS;
                        } else {
                            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                        }
                        return Command.SINGLE_SUCCESS;
                    }).build(), "切换员工聊天频道", List.of("staff"));
        });
    }

    /**
     * Toggle staff chat for a player
     */
    public void toggleStaffChat(Player player) {
        UUID uuid = player.getUniqueId();
        if (staffChatPlayers.contains(uuid)) {
            staffChatPlayers.remove(uuid);
            player.sendMessage(Component.text("已退出员工聊天频道")
                    .color(NamedTextColor.YELLOW));
        } else {
            staffChatPlayers.add(uuid);
            player.sendMessage(Component.text("已进入员工聊天频道")
                    .color(NamedTextColor.GREEN));
        }
    }

    /**
     * Check if player is in staff chat
     */
    public boolean isInStaffChat(Player player) {
        return staffChatPlayers.contains(player.getUniqueId());
    }

    /**
     * Handle chat events
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Check if player is in staff chat
        if (!isInStaffChat(player)) {
            return;
        }

        // Cancel the original event
        event.setCancelled(true);

        // Get the message
        Component message = event.message();

        // Create staff chat format
        Component staffMessage = Component.text()
                .append(Component.text("[STAFF] ")
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, true))
                .append(Component.text(player.getName())
                        .color(NamedTextColor.YELLOW))
                .append(Component.text(": ")
                        .color(NamedTextColor.WHITE))
                .append(message.color(NamedTextColor.WHITE))
                .build();

        // Send to all staff members and console
        plugin.getServer().getConsoleSender().sendMessage(staffMessage);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission(STAFF_PERMISSION)) {
                onlinePlayer.sendMessage(staffMessage);
            }
        }
    }

    /**
     * Clean up when player quits
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        staffChatPlayers.remove(event.getPlayer().getUniqueId());
    }
}
