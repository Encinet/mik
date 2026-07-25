package org.encinet.mik.module.music.jukebox;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Suppresses vanilla record-effect packets while MIK owns a jukebox's audio. */
public final class VanillaRecordSilencer implements AutoCloseable {

    private static final int RECORD_PLAY_EVENT = 1010;

    private final Set<JukeboxKey> activeJukeboxes = ConcurrentHashMap.newKeySet();
    private final Set<JukeboxKey> pendingJukeboxes = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> playerWorlds = new ConcurrentHashMap<>();
    private final PacketListener listener = new PacketListener();
    private final AtomicBoolean enabled = new AtomicBoolean();
    private EventManager eventManager;

    public void enable() {
        if (!enabled.compareAndSet(false, true)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerWorld(player);
        }
        eventManager = PacketEvents.getAPI().getEventManager();
        eventManager.registerListener(listener);
    }

    public void suppress(Block block) {
        pendingJukeboxes.add(JukeboxKey.of(block.getLocation()));
    }

    public void release(Block block) {
        pendingJukeboxes.remove(JukeboxKey.of(block.getLocation()));
    }

    void playbackStarted(Location location) {
        activeJukeboxes.add(JukeboxKey.of(location));
    }

    void playbackStopped(Location location) {
        activeJukeboxes.remove(JukeboxKey.of(location));
    }

    public void updatePlayerWorld(Player player) {
        playerWorlds.put(player.getUniqueId(), player.getWorld().getUID());
    }

    public void removePlayer(UUID playerId) {
        playerWorlds.remove(playerId);
    }

    public void removeWorld(World world) {
        if (world == null) {
            return;
        }
        UUID worldId = world.getUID();
        activeJukeboxes.removeIf(key -> worldId.equals(key.world()));
        pendingJukeboxes.removeIf(key -> worldId.equals(key.world()));
        playerWorlds.entrySet().removeIf(entry -> worldId.equals(entry.getValue()));
    }

    @Override
    public void close() {
        if (!enabled.compareAndSet(true, false)) {
            return;
        }
        if (eventManager != null) {
            eventManager.unregisterListener(listener);
            eventManager = null;
        }
        activeJukeboxes.clear();
        pendingJukeboxes.clear();
        playerWorlds.clear();
    }

    private record JukeboxKey(UUID world, int x, int y, int z) {
        private static JukeboxKey of(Location location) {
            return new JukeboxKey(location.getWorld().getUID(), location.getBlockX(),
                    location.getBlockY(), location.getBlockZ());
        }
    }

    private final class PacketListener extends PacketListenerAbstract {
        private PacketListener() {
            super(PacketListenerPriority.NORMAL);
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() != PacketType.Play.Server.EFFECT) {
                return;
            }
            WrapperPlayServerEffect packet = new WrapperPlayServerEffect(event);
            if (packet.getType() != RECORD_PLAY_EVENT) {
                return;
            }
            UUID world = playerWorlds.get(event.getUser().getUUID());
            if (world == null) {
                return;
            }
            Vector3i position = packet.getPosition();
            JukeboxKey key = new JukeboxKey(world, position.x, position.y, position.z);
            if (activeJukeboxes.contains(key) || pendingJukeboxes.contains(key)) {
                event.setCancelled(true);
            }
        }
    }
}
