package org.encinet.mik.module;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Module for restricting commands with entity selectors and player names
 */
public class CommandRestrictionModule implements Listener {

    private static final Pattern SELECTOR_PATTERN = Pattern.compile("@[earp](?:\\[|\\s|$)");
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>(Arrays.asList("w", "tell", "msg", "tp", "teleport"));

    private final JavaPlugin plugin;

    public CommandRestrictionModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enable command restriction module (called in onEnable)
     */
    public void enable() {
        // Register event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("CommandRestrictionModule enabled");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage().trim();
        String commandLower = message.toLowerCase();

        // Block /kill @e for everyone (security measure)
        if (message.equals("/kill @e")) {
            event.setCancelled(true);
            Component errorMessage = Component.text("禁止使用 /kill @e 命令！", NamedTextColor.RED);
            player.sendMessage(errorMessage);
            plugin.getLogger().warning("Blocked /kill @e command from " + player.getName() + ": " + message);
            return;
        }

        // Check if player has helper permission - allow everything else if they do
        if (player.hasPermission("group." + Mik.GROUP_HELPER)) {
            return;
        }

        // Check if command contains entity selectors
        if (SELECTOR_PATTERN.matcher(commandLower).find()) {
            event.setCancelled(true);
            Component errorMessage = Component.text("你没有权限使用目标选择器 (@e, @a, @r, @p)！", NamedTextColor.RED);
            player.sendMessage(errorMessage);
            plugin.getLogger().info("Blocked selector command from " + player.getName() + ": " + message);
            return;
        }

        // Extract command name (without the leading /)
        String[] parts = message.substring(1).split("\\s+", 2);
        String commandName = parts[0].toLowerCase();

        // Allow /w, /tell, /msg commands
        if (ALLOWED_COMMANDS.contains(commandName)) {
            return;
        }

        // Check if command contains other player names
        if (containsPlayerName(message, player)) {
            event.setCancelled(true);
            Component errorMessage = Component.text("你没有权限在命令中提及其他玩家的名字！", NamedTextColor.RED);
            player.sendMessage(errorMessage);
            plugin.getLogger().info("Blocked player name command from " + player.getName() + ": " + message);
        }
    }

    /**
     * Check if command contains any online player's name (excluding the sender)
     */
    private boolean containsPlayerName(String command, Player sender) {
        String commandLower = command.toLowerCase();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            // Skip the command sender
            if (onlinePlayer.getUniqueId().equals(sender.getUniqueId())) {
                continue;
            }

            String playerName = onlinePlayer.getName().toLowerCase();
            // Check if player name appears as a separate word in the command
            if (commandLower.matches(".*\\b" + Pattern.quote(playerName) + "\\b.*")) {
                return true;
            }
        }

        return false;
    }
}
