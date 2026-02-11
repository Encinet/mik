package org.encinet.mik.module;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChangeGameMode;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Module for F3+F4 game mode switching with permission control
 */
public class GameModeSwitchModule implements Listener {

    private final JavaPlugin plugin;

    public GameModeSwitchModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enable game mode switch module (called in onEnable)
     */
    public void enable() {
        // Register event listener for player join
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Register packet listener
        PacketEvents.getAPI().getEventManager().registerListener(
                new GameModeSwitchPacketListener()
        );

        plugin.getLogger().info("GameModeSwitchModule enabled");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Update player's F3+F4 permission status on join
        Player player =  event.getPlayer();

        // Status 28 = allow F3+F4, Status 24 = disallow F3+F4
        int status = 28;
        PacketEvents.getAPI().getPlayerManager().sendPacket(
                player,
                new WrapperPlayServerEntityStatus(player.getEntityId(), status)
        );
    }

    /**
     * Packet listener for game mode switching
     */
    private class GameModeSwitchPacketListener extends PacketListenerAbstract {

        public GameModeSwitchPacketListener() {
            super(PacketListenerPriority.HIGHEST);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            // Only handle game mode change packets
            if (event.getPacketType() != PacketType.Play.Client.CHANGE_GAME_MODE) {
                return;
            }

            Player player = event.getPlayer();

            WrapperPlayClientChangeGameMode gameModePacket = new WrapperPlayClientChangeGameMode(event);
            GameMode targetMode = gameModePacket.getGameMode();

            // Cancel packet and execute command on main thread
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                String command = "/gamemode " + targetMode.toString().toLowerCase();
                player.chat(command);
            });
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            // Only handle entity status packets
            if (event.getPacketType() != PacketType.Play.Server.ENTITY_STATUS) {
                return;
            }

            Player player = event.getPlayer();
            if (player == null) {
                return;
            }

            WrapperPlayServerEntityStatus entityStatusPacket = new WrapperPlayServerEntityStatus(event);

            // Only modify packets for the player themselves
            if (entityStatusPacket.getEntityId() != player.getEntityId()) {
                return;
            }

            // Ensure F3+F4 is enabled (status 24-28 range)
            if (entityStatusPacket.getStatus() <= 28 && entityStatusPacket.getStatus() >= 24) {
                entityStatusPacket.setStatus(28);
            }
        }
    }
}
