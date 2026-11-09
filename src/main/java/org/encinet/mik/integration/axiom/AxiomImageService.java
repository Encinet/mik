package org.encinet.mik.integration.axiom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Main-thread transport for transient, per-viewer images. Does not write Axiom world annotations. */
public final class AxiomImageService {
    public static final String CHANNEL = "axiom:annotation_update";
    private final JavaPlugin plugin;
    private final Map<Owner, AxiomImageState> owners = new HashMap<>();
    private final Set<Owner> reportedFailures = new HashSet<>();
    private final Listener listener = new Events();
    private BukkitTask retryTask;
    private boolean enabled;

    public AxiomImageService(JavaPlugin plugin) { this.plugin = Objects.requireNonNull(plugin, "plugin"); }

    public void enable() {
        if (enabled) return;
        try {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            retryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::retryPending, 20, 20);
            enabled = true;
        } catch (RuntimeException | LinkageError error) {
            HandlerList.unregisterAll(listener);
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
            throw error;
        }
    }

    public boolean supported(Player viewer) { return enabled && viewer.getListeningPluginChannels().contains(CHANNEL); }

    public void synchronize(Player viewer, UUID ownerId, Map<UUID, AxiomImage> images) {
        requirePrimaryThread();
        if (!enabled) return;
        Owner owner = new Owner(viewer.getUniqueId(), Objects.requireNonNull(ownerId, "ownerId"));
        if (images.isEmpty() && !owners.containsKey(owner)) return;
        AxiomImageState state = owners.computeIfAbsent(owner, _ -> new AxiomImageState());
        state.replace(images);
        publish(owner, state, viewer);
    }

    public void remove(Player viewer, UUID ownerId) { synchronize(viewer, ownerId, Map.of()); }

    public void forget(UUID viewerId, UUID ownerId) {
        requirePrimaryThread();
        owners.remove(new Owner(viewerId, ownerId));
    }

    public void disable() {
        requirePrimaryThread();
        if (!enabled) return;
        if (retryTask != null) { retryTask.cancel(); retryTask = null; }
        try {
            for (var entry : List.copyOf(owners.entrySet())) {
                entry.getValue().replace(Map.of());
                Player viewer = Bukkit.getPlayer(entry.getKey().viewerId());
                if (viewer != null) publish(entry.getKey(), entry.getValue(), viewer);
            }
        } finally {
            owners.clear();
            reportedFailures.clear();
            HandlerList.unregisterAll(listener);
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
            enabled = false;
        }
    }

    private void retryPending() {
        for (var entry : List.copyOf(owners.entrySet())) {
            if (entry.getValue().empty()) { owners.remove(entry.getKey()); continue; }
            if (!entry.getValue().pending()) continue;
            Player viewer = Bukkit.getPlayer(entry.getKey().viewerId());
            if (viewer == null || !viewer.isOnline()) owners.remove(entry.getKey());
            else publish(entry.getKey(), entry.getValue(), viewer);
        }
    }

    private void publish(Owner owner, AxiomImageState state, Player viewer) {
        if (!viewer.isOnline() || !supported(viewer)) {
            if (state.empty()) owners.remove(owner);
            return;
        }
        try {
            state.synchronize(packet -> viewer.sendPluginMessage(plugin, CHANNEL, packet));
            reportedFailures.remove(owner);
            if (state.empty()) owners.remove(owner);
        } catch (RuntimeException exception) {
            // A partially sent batch can be replayed: create, move, rotate and delete are idempotent by UUID.
            if (reportedFailures.add(owner)) plugin.getLogger().log(Level.WARNING,
                    "Could not synchronize Axiom images for " + viewer.getName(), exception);
        }
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Axiom images must be accessed on the server thread");
    }

    private record Owner(UUID viewerId, UUID ownerId) { }

    private final class Events implements Listener {
        @EventHandler public void quit(PlayerQuitEvent event) {
            UUID viewer = event.getPlayer().getUniqueId();
            owners.keySet().removeIf(owner -> owner.viewerId().equals(viewer));
            reportedFailures.removeIf(owner -> owner.viewerId().equals(viewer));
        }

        @EventHandler public void registered(PlayerRegisterChannelEvent event) {
            if (!CHANNEL.equals(event.getChannel())) return;
            for (var entry : List.copyOf(owners.entrySet())) if (entry.getKey().viewerId().equals(event.getPlayer().getUniqueId())) {
                entry.getValue().resend();
                publish(entry.getKey(), entry.getValue(), event.getPlayer());
            }
        }

        @EventHandler public void unregistered(PlayerUnregisterChannelEvent event) {
            if (!CHANNEL.equals(event.getChannel())) return;
            owners.forEach((owner, state) -> { if (owner.viewerId().equals(event.getPlayer().getUniqueId())) state.resend(); });
        }
    }
}
