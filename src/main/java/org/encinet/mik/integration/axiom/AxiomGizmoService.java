package org.encinet.mik.integration.axiom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Aggregates virtual entities that Axiom must not expose through editor gizmos.
 *
 * <p>Axiom's clientbound payload replaces the complete ignored UUID set, so every
 * producer contributes through an isolated scope and this service publishes their
 * union. The transport uses Bukkit's plugin messaging API and does not require
 * Axiom classes or an Axiom server mod.</p>
 */
public final class AxiomGizmoService implements Listener {
    public static final String CHANNEL = "axiom:ignore_display_entities";

    private final JavaPlugin plugin;
    private final Map<UUID, ViewerState> viewers = new HashMap<>();
    private final Set<UUID> reportedFailures = new HashSet<>();
    private int nextScopeId;
    private boolean enabled;

    public AxiomGizmoService(JavaPlugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
    }

    public void enable() {
        if (enabled) return;
        try {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
            Bukkit.getPluginManager().registerEvents(this, plugin);
            enabled = true;
        } catch (RuntimeException | LinkageError error) {
            try {
                HandlerList.unregisterAll(this);
            } catch (RuntimeException | LinkageError cleanupError) {
                error.addSuppressed(cleanupError);
            }
            try {
                Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
            } catch (RuntimeException | LinkageError cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    public void disable() {
        if (!enabled) return;
        for (UUID viewerId : java.util.List.copyOf(viewers.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) send(viewer, Set.of());
        }
        viewers.clear();
        reportedFailures.clear();
        try {
            HandlerList.unregisterAll(this);
        } finally {
            try {
                Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
            } finally {
                enabled = false;
            }
        }
    }

    /** Creates an independently managed contribution source. */
    public Scope scope(String name) {
        String checkedName = java.util.Objects.requireNonNull(name, "name").strip();
        if (checkedName.isEmpty()) throw new IllegalArgumentException("Axiom gizmo scope name is empty");
        return new Scope(++nextScopeId, checkedName);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        viewers.remove(viewerId);
        reportedFailures.remove(viewerId);
    }

    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        if (!CHANNEL.equals(event.getChannel())) return;
        ViewerState state = viewers.get(event.getPlayer().getUniqueId());
        if (state == null) return;
        state.published = null;
        publish(event.getPlayer(), state);
    }

    private void synchronize(Scope scope, Player viewer, UUID ownerId,
                             Collection<UUID> entityIds) {
        scope.requireOpen();
        java.util.Objects.requireNonNull(viewer, "viewer");
        OwnerKey key = new OwnerKey(scope.id, java.util.Objects.requireNonNull(ownerId, "ownerId"));
        Set<UUID> contribution = Set.copyOf(new LinkedHashSet<>(entityIds));
        if (contribution.isEmpty()) {
            remove(scope, viewer, ownerId);
            return;
        }
        ViewerState state = viewers.computeIfAbsent(viewer.getUniqueId(), ignored -> new ViewerState());
        state.contributions.put(key, contribution);
        publish(viewer, state);
    }

    private void remove(Scope scope, Player viewer, UUID ownerId) {
        scope.requireOpen();
        java.util.Objects.requireNonNull(viewer, "viewer");
        java.util.Objects.requireNonNull(ownerId, "ownerId");
        ViewerState state = viewers.get(viewer.getUniqueId());
        if (state == null
                || state.contributions.remove(new OwnerKey(scope.id, ownerId)) == null) return;
        publish(viewer, state);
        if (state.contributions.isEmpty()) viewers.remove(viewer.getUniqueId());
    }

    private void forget(Scope scope, UUID viewerId, UUID ownerId) {
        ViewerState state = viewers.get(viewerId);
        if (state == null) return;
        state.contributions.remove(new OwnerKey(scope.id, ownerId));
        if (state.contributions.isEmpty()) viewers.remove(viewerId);
    }

    private void forgetViewer(Scope scope, UUID viewerId) {
        ViewerState state = viewers.get(viewerId);
        if (state == null) return;
        state.contributions.keySet().removeIf(key -> key.scopeId == scope.id);
        if (state.contributions.isEmpty()) viewers.remove(viewerId);
    }

    private void close(Scope scope) {
        if (scope.closed) return;
        scope.closed = true;
        Iterator<Map.Entry<UUID, ViewerState>> iterator = viewers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ViewerState> entry = iterator.next();
            ViewerState state = entry.getValue();
            if (!state.contributions.keySet().removeIf(key -> key.scopeId == scope.id)) continue;
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null && viewer.isOnline()) publish(viewer, state);
            if (state.contributions.isEmpty()) iterator.remove();
        }
    }

    private void publish(Player viewer, ViewerState state) {
        Set<UUID> combined = merge(state.contributions.values());
        if (combined.equals(state.published)) return;
        if (send(viewer, combined)) state.published = combined;
    }

    private boolean send(Player player, Collection<UUID> entityIds) {
        if (!enabled || !player.isOnline()) return false;
        try {
            player.sendPluginMessage(plugin, CHANNEL, encode(entityIds));
            reportedFailures.remove(player.getUniqueId());
            return true;
        } catch (RuntimeException exception) {
            if (reportedFailures.add(player.getUniqueId())) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not synchronize Axiom gizmo exclusions for " + player.getName(), exception);
            }
            return false;
        }
    }

    static Set<UUID> merge(Collection<? extends Collection<UUID>> contributions) {
        Set<UUID> combined = new LinkedHashSet<>();
        for (Collection<UUID> contribution : contributions) combined.addAll(contribution);
        return Set.copyOf(combined);
    }

    static byte[] encode(Collection<UUID> entityIds) {
        int count = entityIds.size();
        ByteBuffer payload = ByteBuffer.allocate(varIntSize(count)
                + Math.multiplyExact(count, Long.BYTES * 2));
        writeVarInt(payload, count);
        for (UUID entityId : entityIds) {
            payload.putLong(entityId.getMostSignificantBits());
            payload.putLong(entityId.getLeastSignificantBits());
        }
        return payload.array();
    }

    private static int varIntSize(int value) {
        int bytes = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            bytes++;
        }
        return bytes;
    }

    private static void writeVarInt(ByteBuffer target, int value) {
        while ((value & ~0x7F) != 0) {
            target.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        target.put((byte) value);
    }

    private record OwnerKey(int scopeId, UUID ownerId) {
    }

    private static final class ViewerState {
        private final Map<OwnerKey, Set<UUID>> contributions = new HashMap<>();
        private Set<UUID> published = Set.of();
    }

    /** A producer-owned namespace within the combined per-viewer UUID set. */
    public final class Scope implements AutoCloseable {
        private final int id;
        private final String name;
        private boolean closed;

        private Scope(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public void synchronize(Player viewer, UUID ownerId, Collection<UUID> entityIds) {
            AxiomGizmoService.this.synchronize(this, viewer, ownerId, entityIds);
        }

        public void remove(Player viewer, UUID ownerId) {
            AxiomGizmoService.this.remove(this, viewer, ownerId);
        }

        public void forget(UUID viewerId, UUID ownerId) {
            requireOpen();
            AxiomGizmoService.this.forget(this, viewerId, ownerId);
        }

        public void forgetViewer(UUID viewerId) {
            requireOpen();
            AxiomGizmoService.this.forgetViewer(this, viewerId);
        }

        @Override
        public void close() {
            AxiomGizmoService.this.close(this);
        }

        private void requireOpen() {
            if (closed) throw new IllegalStateException("Axiom gizmo scope '" + name + "' is closed");
        }
    }

}
