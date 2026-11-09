package org.encinet.mik.feature.server.performance;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.player.movement.FlightSpeedControl;

import java.util.HashMap;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/** Main-thread sampling of client chunk delivery and upcoming, unloaded coverage. */
final class ChunkExplorationController implements Listener {
    private static final long WARMUP_NANOS = 10_000_000_000L;
    private final JavaPlugin plugin;
    private final FlightSpeedControl flights;
    private final DoubleSupplier networkPressure;
    private final DoubleSupplier mspt;
    private final Runnable onChanged;
    private final LongSupplier clock;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private BukkitTask task;

    ChunkExplorationController(JavaPlugin plugin, FlightSpeedControl flights, DoubleSupplier networkPressure,
                               DoubleSupplier mspt, Runnable onChanged, LongSupplier clock) {
        this.plugin = plugin;
        this.flights = flights;
        this.networkPressure = networkPressure;
        this.mspt = mspt;
        this.onChanged = onChanged;
        this.clock = clock;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 20L, 20L);
    }

    void disable() {
        HandlerList.unregisterAll(this);
        if (task != null) task.cancel();
        task = null;
        for (Player player : Bukkit.getOnlinePlayers()) flights.releaseExplorationSpeed(player);
        sessions.clear();
    }

    @EventHandler
    public void onChunkSent(PlayerChunkLoadEvent event) {
        // This event is observational only: all control happens in the later sampling task.
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.sentChunks < Integer.MAX_VALUE) session.sentChunks++;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // Read the final position on the next sampling tick, after the teleport has actually applied.
        forget(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        sessions.remove(player.getUniqueId());
        flights.releaseExplorationSpeed(player);
        // The parent already cleared overrides. Do not reconcile a player still in Bukkit's quit snapshot.
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        forget(event.getPlayer());
    }

    ChunkExplorationPolicy.Limit limit(UUID playerId) {
        Session session = sessions.get(playerId);
        return session == null ? ChunkExplorationPolicy.OPEN : session.policy.limit();
    }

    int limitedPlayers() {
        return (int) sessions.values().stream().filter(session -> session.policy.limit().throttled()).count();
    }

    String limitedSummary() {
        String summary = Bukkit.getOnlinePlayers().stream().filter(player -> limit(player.getUniqueId()).throttled())
                .sorted(Comparator.comparingDouble((Player player) -> sessions.get(player.getUniqueId()).policy.demand()).reversed())
                .limit(5).map(player -> {
                    var limit = limit(player.getUniqueId());
                    return String.format(Locale.ROOT, "%s (send %.0f%%, load %.0f%%, fly <=%.2fx)", player.getName(),
                            limit.sendRatio() * 100.0D, limit.loadRatio() * 100.0D, limit.flightCeiling() * 10.0f);
                }).collect(Collectors.joining(", "));
        return summary.isEmpty() ? "none" : summary;
    }

    boolean hasHeavyDemand() {
        return sessions.values().stream().anyMatch(session -> session.movingFast && session.policy.demand() >= 64.0D);
    }

    private void reset(Player player) {
        forget(player);
        sessions.put(player.getUniqueId(), new Session(player.getLocation(), clock.getAsLong()));
    }

    private void forget(Player player) {
        Session previous = sessions.remove(player.getUniqueId());
        flights.releaseExplorationSpeed(player);
        if (previous != null && previous.policy.limit().throttled()) onChanged.run();
    }

    void sample() {
        long now = clock.getAsLong();
        Map<Player, ChunkExplorationPolicy.Observation> observations = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            Session session = sessions.computeIfAbsent(player.getUniqueId(), _ -> new Session(location, now));
            double seconds = (now - session.lastNanos) / 1_000_000_000.0D;
            if (!location.getWorld().getUID().equals(session.previous.getWorld().getUID())) {
                reset(player);
                continue;
            }
            double speed = Math.hypot(location.getX() - session.previous.getX(),
                    location.getZ() - session.previous.getZ()) / Math.max(0.001D, seconds);
            int dx = (location.getBlockX() >> 4) - (session.previous.getBlockX() >> 4);
            int dz = (location.getBlockZ() >> 4) - (session.previous.getBlockZ() >> 4);
            double unloaded = 0.0D;
            if ((speed >= 24.0D || session.policy.limit().throttled())
                    && seconds > 0.0D && seconds <= 5.0D && now >= session.warmupUntil) {
                int radius = Math.clamp(Math.max(player.getSimulationDistance(),
                        Math.min(player.getViewDistance(),
                                PlayerViewDistancePolicy.clientChunkLimit(player.getClientViewDistance()))), 2, 32);
                unloaded = ChunkExplorationPolicy.newCoverage(radius, dx, dz)
                        * unloadedFrontier(location, radius, dx, dz) / seconds;
            }
            var observation = new ChunkExplorationPolicy.Observation(speed, session.sentChunks / Math.max(0.001D, seconds),
                    unloaded, player.isFlying(), player.getFlySpeed());
            session.seconds = seconds;
            session.previous = location;
            session.lastNanos = now;
            session.sentChunks = 0;
            session.movingFast = speed >= 24.0D;
            if (now >= session.warmupUntil) observations.put(player, observation);
        }
        int senders = 0;
        int loaders = 0;
        for (var entry : observations.entrySet()) {
            var observation = entry.getValue();
            if (observation.blocksPerSecond() >= 24.0D
                    || sessions.get(entry.getKey().getUniqueId()).policy.limit().throttled()) {
                if (observation.sentPerSecond() >= 8.0D) senders++;
                if (observation.unloadedPerSecond() >= 4.0D) loaders++;
            }
        }
        double pressure = networkPressure.getAsDouble();
        double serverMspt = mspt.getAsDouble();
        var resources = new ChunkExplorationPolicy.Resources(pressure, serverMspt, senders, loaders);
        boolean changed = false;
        for (var entry : observations.entrySet()) {
            Player player = entry.getKey();
            Session session = sessions.get(player.getUniqueId());
            ChunkExplorationPolicy.Limit previous = session.policy.limit();
            ChunkExplorationPolicy.Limit next = session.policy.update(entry.getValue(), session.seconds, resources);
            if (!next.equals(previous)) {
                changed = true;
            }
            if (next.flightCeiling() < 1.0f) flights.limitExplorationSpeed(player, next.flightCeiling());
            else flights.releaseExplorationSpeed(player);
        }
        if (changed) onChanged.run();
    }

    private static double unloadedFrontier(Location location, int radius, int dx, int dz) {
        World world = location.getWorld();
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        int probes = 0;
        int missing = 0;
        for (int step = 0; step < 4; step++) {
            int offset = -radius + 2 * radius * step / 3;
            if (dx != 0) {
                probes++;
                if (!world.isChunkLoaded(x + Integer.signum(dx) * (radius + 1), z + offset)) missing++;
            }
            if (dz != 0) {
                probes++;
                if (!world.isChunkLoaded(x + offset, z + Integer.signum(dz) * (radius + 1))) missing++;
            }
        }
        return probes == 0 ? 0.0D : (double) missing / probes;
    }

    private static final class Session {
        private final ChunkExplorationPolicy policy = new ChunkExplorationPolicy();
        private final long warmupUntil;
        private Location previous;
        private long lastNanos;
        private int sentChunks;
        private double seconds;
        private boolean movingFast;

        Session(Location location, long now) {
            previous = location;
            lastNanos = now;
            warmupUntil = now + WARMUP_NANOS;
        }
    }
}
