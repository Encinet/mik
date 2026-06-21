package org.encinet.mik.module.player;

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

public class GameModeSwitchModule implements Listener {

    private final JavaPlugin plugin;

    public GameModeSwitchModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        PacketEvents.getAPI().getEventManager().registerListener(
                new GameModeSwitchPacketListener()
        );

        plugin.getLogger().info("GameModeSwitchModule enabled");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player =  event.getPlayer();

        // Status 28 = allow F3+F4, Status 24 = disallow F3+F4
        int status = 28;
        PacketEvents.getAPI().getPlayerManager().sendPacket(
                player,
                new WrapperPlayServerEntityStatus(player.getEntityId(), status)
        );
    }

    private class GameModeSwitchPacketListener extends PacketListenerAbstract {

        public GameModeSwitchPacketListener() {
            super(PacketListenerPriority.HIGHEST);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
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
            if (event.getPacketType() != PacketType.Play.Server.ENTITY_STATUS) {
                return;
            }

            Player player = event.getPlayer();
            if (player == null) {
                return;
            }

            WrapperPlayServerEntityStatus entityStatusPacket = new WrapperPlayServerEntityStatus(event);

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
