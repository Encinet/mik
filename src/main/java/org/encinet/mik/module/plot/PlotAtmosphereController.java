package org.encinet.mik.module.plot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Applies plot atmosphere to each viewer without changing world time or weather. */
final class PlotAtmosphereController implements Listener {
    private final JavaPlugin plugin;
    private final PlotRegistry registry;
    private final Map<UUID, Applied> active = new HashMap<>();
    private final Map<UUID, Observation> observations = new HashMap<>();
    private BukkitTask reconcileTask;

    PlotAtmosphereController(JavaPlugin plugin, PlotRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reconcileTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, 20L);
    }

    void disable() {
        if (reconcileTask != null) {
            reconcileTask.cancel();
            reconcileTask = null;
        }
        HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Applied previous = active.get(player.getUniqueId());
            if (previous == null) continue;
            if (previous.setting().timeTicks() != null)
                restoreTime(player, previous.timeBefore());
            if (previous.setting().weather() != null)
                restoreWeather(player, previous.weatherBefore());
        }
        active.clear();
        observations.clear();
    }

    void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers())
            refresh(player, player.getLocation(), false);
    }

    private void refresh(Player player, Location location, boolean force) {
        UUID playerId = player.getUniqueId();
        UUID world = location.getWorld() == null ? null : location.getWorld().getUID();
        Observation previous = observations.get(playerId);
        long revision = registry.atmosphereRevision();
        if (!force && previous != null && previous.matches(world, location.getBlockX(),
                location.getBlockY(), location.getBlockZ(), revision)) return;
        Plot plot = world == null ? null : registry.at(
                world, location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
        PlotAtmosphere next = plot == null ? PlotAtmosphere.WORLD
                : registry.effectiveAtmosphere(plot.id());
        Applied applied = apply(player, active.get(playerId), next, force);
        if (applied == null) active.remove(playerId);
        else active.put(playerId, applied);
        observations.put(playerId, new Observation(world,
                Math.floorDiv(location.getBlockX(), PlotGeometry.CELL),
                Math.floorDiv(location.getBlockY(), PlotGeometry.CELL),
                Math.floorDiv(location.getBlockZ(), PlotGeometry.CELL), revision));
    }

    static Applied apply(Player player, Applied previous, PlotAtmosphere next, boolean force) {
        PlotAtmosphere old = previous == null ? PlotAtmosphere.WORLD : previous.setting();
        if (!force && old.equals(next)) return previous;
        TimeBefore timeBefore = previous == null ? null : previous.timeBefore();
        WeatherBefore weatherBefore = previous == null ? null : previous.weatherBefore();

        if (next.timeTicks() == null) {
            if (old.timeTicks() != null) {
                restoreTime(player, timeBefore);
                timeBefore = null;
            }
        } else {
            if (old.timeTicks() == null)
                timeBefore = new TimeBefore(player.getPlayerTimeOffset(),
                        player.isPlayerTimeRelative());
            if (force || !Objects.equals(old.timeTicks(), next.timeTicks()))
                player.setPlayerTime(next.timeTicks(), false);
        }
        if (next.weather() == null) {
            if (old.weather() != null) {
                restoreWeather(player, weatherBefore);
                weatherBefore = null;
            }
        } else {
            if (old.weather() == null)
                weatherBefore = new WeatherBefore(player.getPlayerWeather());
            if (force || old.weather() != next.weather())
                player.setPlayerWeather(next.weather() == PlotAtmosphere.Weather.CLEAR
                        ? WeatherType.CLEAR : WeatherType.DOWNFALL);
        }
        return next.followsWorld() ? null : new Applied(next, timeBefore, weatherBefore);
    }

    private static void restoreTime(Player player, TimeBefore before) {
        if (before == null || (before.relative() && before.offset() == 0))
            player.resetPlayerTime();
        else player.setPlayerTime(before.offset(), before.relative());
    }

    private static void restoreWeather(Player player, WeatherBefore before) {
        if (before == null || before.weather() == null) player.resetPlayerWeather();
        else player.setPlayerWeather(before.weather());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || sameBlock(event.getFrom(), to)) return;
        refresh(event.getPlayer(), to, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler
    public void changedWorld(PlayerChangedWorldEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
        observations.remove(event.getPlayer().getUniqueId());
    }

    private void refreshNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) refresh(player, player.getLocation(), true);
        });
    }

    private static boolean sameBlock(Location from, Location to) {
        return Objects.equals(from.getWorld(), to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }

    record Applied(PlotAtmosphere setting, TimeBefore timeBefore,
                   WeatherBefore weatherBefore) { }

    record Observation(UUID world, int cellX, int cellY, int cellZ, long revision) {
        boolean matches(UUID nextWorld, int nextX, int nextY, int nextZ, long nextRevision) {
            return Objects.equals(world, nextWorld) && cellX == Math.floorDiv(nextX, PlotGeometry.CELL)
                    && cellY == Math.floorDiv(nextY, PlotGeometry.CELL)
                    && cellZ == Math.floorDiv(nextZ, PlotGeometry.CELL)
                    && revision == nextRevision;
        }
    }

    record TimeBefore(long offset, boolean relative) { }

    record WeatherBefore(WeatherType weather) { }
}
