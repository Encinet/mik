package org.encinet.mik.shared.menu.runtime.space;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class MenuTrackedModels {
    private final HashMap<UUID, Set<Entity>> byViewer = new HashMap<>();

    public void seed(Collection<? extends Player> online) {
        online.stream().map(Player::getWorld).distinct().forEach(world -> {
            for (var entity : MenuEntityObstacles.models(world)) {
                for (var viewer : entity.getTrackedBy()) track(viewer, entity);
            }
        });
    }

    public void track(Player viewer, Entity entity) {
        if (entity instanceof Display || entity instanceof ArmorStand)
            byViewer.computeIfAbsent(viewer.getUniqueId(), _ -> new HashSet<>()).add(entity);
    }

    public void untrack(Player viewer, Entity entity) {
        if (!(entity instanceof Display || entity instanceof ArmorStand)) return;
        var id = viewer.getUniqueId();
        var tracked = byViewer.get(id);
        if (tracked != null && tracked.remove(entity) && tracked.isEmpty()) byViewer.remove(id);
    }

    public Collection<Entity> forViewer(Player viewer) {
        var id = viewer.getUniqueId();
        var tracked = byViewer.get(id);
        if (tracked == null) return Set.of();
        var world = viewer.getWorld();
        tracked.removeIf(entity -> !entity.isValid() || !world.equals(entity.getWorld()));
        if (tracked.isEmpty()) byViewer.remove(id);
        return tracked;
    }

    public void forget(UUID viewer) { byViewer.remove(viewer); }

    public void clear() { byViewer.clear(); }
}
