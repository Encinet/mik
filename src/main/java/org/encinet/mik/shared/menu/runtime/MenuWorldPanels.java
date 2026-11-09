package org.encinet.mik.shared.menu.runtime;

import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.FloatingMenuCloseReason;
import org.encinet.mik.shared.menu.FloatingMenuHandle;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.WorldMenuScope;
import org.encinet.mik.shared.menu.WorldMenuSurface;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Owns world panel slots and their lifetime. The host reuses the ordinary menu rendering session. */
final class MenuWorldPanels<V extends MenuWorldPanels.View> implements AutoCloseable {
    private static final int MAX_PANELS = 8;

    interface View {
        UUID id();
        UUID viewerId();
        int liveEntities();
    }

    interface Host<V> {
        void requireThread(String operation);
        boolean enabled();
        V create(Player player, MenuScene scene, Mount mount);
        void update(V view, Player player, MenuScene scene, Mount mount);
        void open(V view, Player player);
        void close(V view, FloatingMenuCloseReason reason);
        FloatingMenuHandle handle(V view);
    }

    private record Slot(UUID scope, UiKey key) { }

    record Mount(WorldMenuSurface surface, Predicate<Player> available) {
        Mount { Objects.requireNonNull(surface); Objects.requireNonNull(available); }
    }

    private final Host<V> host;
    // This is the sole ownership index: viewer -> (feature scope, stable key) -> view.
    private final Map<UUID, Map<Slot, V>> viewers = new HashMap<>();
    private final Set<Scope> scopes = new HashSet<>();
    private boolean closed;

    MenuWorldPanels(Host<V> host) { this.host = Objects.requireNonNull(host); }

    WorldMenuScope scope() {
        requireOpen("world panel scope");
        var scope = new Scope();
        scopes.add(scope);
        return scope;
    }

    List<V> views(UUID viewer) {
        var slots = viewers.get(viewer);
        return slots == null ? List.of() : List.copyOf(slots.values());
    }

    List<V> all() {
        return viewers.values().stream().flatMap(slots -> slots.values().stream()).toList();
    }

    V find(UUID viewer, UUID id) {
        return views(viewer).stream().filter(view -> view.id().equals(id)).findFirst().orElse(null);
    }

    void remove(V view) {
        var slots = viewers.get(view.viewerId());
        if (slots == null) return;
        slots.values().removeIf(existing -> existing == view);
        if (slots.isEmpty()) viewers.remove(view.viewerId());
    }

    int reservedEntities(V owner) {
        return views(owner.viewerId()).stream().filter(view -> view != owner).mapToInt(View::liveEntities).sum();
    }

    int availableEntities(V owner) {
        return MenuEntityBudget.remaining(reservedEntities(owner));
    }

    private void requireBudget(UUID viewer, int entities) {
        int reserved = views(viewer).stream().mapToInt(View::liveEntities).sum();
        MenuEntityBudget.require(entities + reserved,"World panels");
    }

    void clear(UUID viewer, FloatingMenuCloseReason reason) {
        host.requireThread("world panel viewer clear");
        closeViews(views(viewer),reason);
    }

    void clear(FloatingMenuCloseReason reason) {
        host.requireThread("world panel clear all");
        closeViews(all(),reason);
    }

    @Override public void close() {
        host.requireThread("world panel service close");
        if (closed) return;
        closed = true;
        scopes.forEach(scope -> scope.closed = true);
        scopes.clear();
        closeViews(all(),FloatingMenuCloseReason.PLUGIN_DISABLE);
    }

    private void requireOpen(String operation) {
        host.requireThread(operation);
        if (closed || !host.enabled()) throw new IllegalStateException("World menu service is closed");
    }

    private void closeViews(List<V> views, FloatingMenuCloseReason reason) {
        var shutdown = new ShutdownSequence();
        for (var view : views) shutdown.attempt("world panel " + view.id(),() -> closeView(view,reason));
        shutdown.finish("world panels");
    }

    private void closeView(V view, FloatingMenuCloseReason reason) {
        try { host.close(view,reason); }
        finally { remove(view); }
    }

    private final class Scope implements WorldMenuScope {
        private final UUID id = UUID.randomUUID();
        private boolean closed;

        @Override public FloatingMenuHandle show(Player player, UiKey key, WorldMenuSurface surface,
                                                 MenuScene scene, Predicate<Player> available) {
            requireOpen("world panel show");
            if (closed) throw new IllegalStateException("World menu scope is closed");
            Objects.requireNonNull(player); Objects.requireNonNull(key); Objects.requireNonNull(scene);
            var mount = new Mount(surface,available);
            UUID viewer = player.getUniqueId();
            if (!player.isOnline() || !player.getWorld().equals(surface.center().getWorld()))
                throw new IllegalArgumentException("World menu viewer and surface must share a world");
            var slot = new Slot(id,key);
            var slots = viewers.get(viewer);
            var current = slots == null ? null : slots.get(slot);
            if (current != null) {
                host.update(current,player,scene,mount);
                return host.handle(current);
            }
            if (views(viewer).size() >= MAX_PANELS)
                throw new IllegalStateException("A viewer can own at most " + MAX_PANELS + " world panels");
            V view = host.create(player,scene,mount);
            requireBudget(viewer,view.liveEntities());
            viewers.computeIfAbsent(viewer,_ -> new LinkedHashMap<>()).put(slot,view);
            try { host.open(view,player); }
            catch (RuntimeException | LinkageError error) {
                try { closeView(view,FloatingMenuCloseReason.ERROR); }
                catch (RuntimeException | LinkageError cleanup) { error.addSuppressed(cleanup); }
                throw error;
            }
            return host.handle(view);
        }

        @Override public void clear(Player player, UiKey key) {
            host.requireThread("world panel slot clear");
            var slots = viewers.get(player.getUniqueId());
            var view = slots == null ? null : slots.get(new Slot(id,key));
            if (view != null) closeView(view,FloatingMenuCloseReason.USER);
        }

        @Override public void clear(UUID viewer) {
            host.requireThread("world panel scope viewer clear");
            closeViews(owned(viewer),FloatingMenuCloseReason.USER);
        }

        private List<V> owned(UUID viewer) {
            var slots = viewers.get(viewer);
            return slots == null ? List.of() : slots.entrySet().stream()
                    .filter(entry -> entry.getKey().scope().equals(id)).map(Map.Entry::getValue).toList();
        }

        @Override public void close() {
            host.requireThread("world panel scope close");
            if (closed) return;
            closed = true;
            scopes.remove(this);
            var owned = List.copyOf(viewers.keySet()).stream().flatMap(viewer -> owned(viewer).stream()).toList();
            closeViews(owned,FloatingMenuCloseReason.USER);
        }
    }
}
