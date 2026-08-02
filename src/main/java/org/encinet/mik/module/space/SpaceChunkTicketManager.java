package org.encinet.mik.module.space;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Keeps every side of the active topology warm before a moving entity reaches it. */
final class SpaceChunkTicketManager implements AutoCloseable {

    private static final int MAX_TICKETS_PER_SURFACE = 64;
    private static final double ARRIVAL_MARGIN = 1.0;

    private final JavaPlugin plugin;
    private final Set<TicketKey> desired = new HashSet<>();
    private final Set<TicketKey> pending = new HashSet<>();
    private final Set<TicketKey> active = new HashSet<>();
    private boolean closed;

    SpaceChunkTicketManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void update(SpaceNetwork network) {
        requirePrimaryThread();
        if (closed) {
            return;
        }
        Set<TicketKey> next = desiredTickets(network);

        for (TicketKey key : Set.copyOf(active)) {
            if (!next.contains(key)) {
                removeTicket(key);
                active.remove(key);
            }
        }
        desired.clear();
        desired.addAll(next);
        pending.retainAll(next);

        for (TicketKey key : next) {
            if (active.contains(key) || !pending.add(key)) {
                continue;
            }
            World world = Bukkit.getWorld(key.worldId());
            if (world == null) {
                pending.remove(key);
                continue;
            }
            world.getChunkAtAsync(key.chunkX(), key.chunkZ(), true)
                    .whenComplete((_, error) -> runOnPrimaryThread(() -> {
                        pending.remove(key);
                        if (error != null) {
                            plugin.getLogger().warning("Could not preload spatial surface chunk "
                                    + world.getName() + " " + key.chunkX() + "," + key.chunkZ()
                                    + ": " + error.getMessage());
                            return;
                        }
                        if (closed || !desired.contains(key)) {
                            return;
                        }
                        if (world.addPluginChunkTicket(key.chunkX(), key.chunkZ(), plugin)) {
                            active.add(key);
                        }
                    }));
        }
    }

    @Override
    public void close() {
        requirePrimaryThread();
        if (closed) {
            return;
        }
        closed = true;
        desired.clear();
        pending.clear();
        for (TicketKey key : Set.copyOf(active)) {
            removeTicket(key);
        }
        active.clear();
    }

    private Set<TicketKey> desiredTickets(SpaceNetwork network) {
        Set<TicketKey> result = new HashSet<>();
        Set<SpaceSurface> surfaces = new HashSet<>();
        for (SpaceLink link : network.links()) {
            surfaces.add(link.first());
            surfaces.add(link.second());
        }
        for (SpaceSurface surface : surfaces) {
            World world = SpaceWorldResolver.resolve(surface.world());
            if (world != null) {
                addSurfaceTickets(result, world, surface);
            }
        }
        return result;
    }

    private void addSurfaceTickets(Set<TicketKey> tickets, World world, SpaceSurface surface) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{-surface.aperture().halfWidth(),
                surface.aperture().halfWidth()}) {
            for (double y : new double[]{-surface.aperture().halfHeight(),
                    surface.aperture().halfHeight()}) {
                for (double z : new double[]{-ARRIVAL_MARGIN, ARRIVAL_MARGIN}) {
                    SpaceVector point = surface.frame().fromLocalPoint(
                            new SpaceVector(x, y, z));
                    minX = Math.min(minX, point.x());
                    maxX = Math.max(maxX, point.x());
                    minZ = Math.min(minZ, point.z());
                    maxZ = Math.max(maxZ, point.z());
                }
            }
        }
        int minChunkX = blockToChunk(minX);
        int maxChunkX = blockToChunk(maxX);
        int minChunkZ = blockToChunk(minZ);
        int maxChunkZ = blockToChunk(maxZ);
        long count = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        if (count > MAX_TICKETS_PER_SURFACE) {
            SpaceVector center = surface.frame().origin();
            tickets.add(new TicketKey(
                    world.getUID(), blockToChunk(center.x()), blockToChunk(center.z())));
            plugin.getLogger().warning("Spatial surface '" + surface.id() + "' in "
                    + world.getName() + " overlaps " + count
                    + " chunks; only its centre chunk will be kept loaded");
            return;
        }
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                tickets.add(new TicketKey(world.getUID(), chunkX, chunkZ));
            }
        }
    }

    private void removeTicket(TicketKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world != null) {
            world.removePluginChunkTicket(key.chunkX(), key.chunkZ(), plugin);
        }
    }

    private void runOnPrimaryThread(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else if (!closed) {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Space chunk tickets must be changed on the server thread");
        }
    }

    private int blockToChunk(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), 16);
    }

    private record TicketKey(UUID worldId, int chunkX, int chunkZ) {
    }
}
