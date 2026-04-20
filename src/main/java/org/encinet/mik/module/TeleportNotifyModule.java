package org.encinet.mik.module;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportNotifyModule implements Listener {

    private static final Set<String> TP_COMMANDS = Set.of("tp", "teleport", "minecraft:tp", "minecraft:teleport");

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<Player, String> pendingTeleports = new ConcurrentHashMap<>();

    public TeleportNotifyModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("TeleportNotifyModule enabled");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().substring(1).split("\\s+");
        if (args.length < 2) return;

        String cmd = args[0].toLowerCase();
        if (!TP_COMMANDS.contains(cmd)) return;

        int offset = cmd.contains(":") ? 2 : 1;
        if (args.length <= offset) return;

        Player sender = event.getPlayer();
        String targetArg = args[offset];
        if (targetArg.startsWith("@")) return;

        Player target = Bukkit.getPlayerExact(targetArg);
        if (target != null && !target.equals(sender)) {
            pendingTeleports.put(target, sender.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) return;

        Player targetPlayer = event.getPlayer();
        String senderName = pendingTeleports.remove(targetPlayer);

        if (senderName != null) {
            targetPlayer.sendActionBar(Component.text("你被 " + senderName + " 传送到了这里", NamedTextColor.AQUA));
        }
    }
}