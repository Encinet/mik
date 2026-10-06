package org.encinet.mik.module.afk.viewing;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.encinet.mik.integration.dreamdisplays.DreamDisplaysScreenSource;
import org.encinet.mik.module.afk.viewing.ScreenGeometry.Point;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Main-thread orchestration for physical-screen viewing only, with fixed policy.
 *
 * <p>Snapshots are read once per automatic-AFK check and grouped by world. Cheap
 * front/distance/angular-size tests precede view alignment and block rays. The
 * current screen is preferred to avoid switching between adjacent screens. Only
 * two of five unobstructed interior rays are required, allowing a small pillar or
 * another player's body without granting an exemption through an opaque wall.</p>
 *
 * <p>Suppression is a time-bounded query rather than an indefinitely open activity
 * lease. Plugin failures discard all sessions immediately; freshness also expires
 * if updates stop. This controller never records activity or awards rewards.</p>
 */
public final class AfkViewingController implements Listener {
    private static final String PLUGIN_NAME = "DreamDisplays";
    private static final double MAXIMUM_RAY_DISTANCE = 96;
    private static final int REQUIRED_VISIBLE_TARGETS = 2;

    private final JavaPlugin plugin;
    private final ViewingPolicy policy = ViewingPolicy.DEFAULT;
    private final Map<UUID, ViewingSession> sessions = new HashMap<>();
    private Plugin attemptedPlugin;
    private PhysicalScreenSource source;
    private boolean reportedReadFailure;

    public AfkViewingController(JavaPlugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        bindSource();
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        resetSource();
    }

    /** Called once per second by the AFK scheduler, before it considers automatic entry. */
    public void update(long now) {
        bindSource();
        if (source == null) {
            sessions.clear();
            return;
        }
        try {
            Map<UUID, List<PhysicalScreen>> byWorld = source.snapshot(Instant.now()).stream()
                    .collect(Collectors.groupingBy(PhysicalScreen::worldId));
            HashSet<UUID> online = new HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                online.add(playerId);
                if (!source.supportsViewer(playerId) || player.isDead()) {
                    sessions.remove(playerId);
                    continue;
                }
                ViewingSession session = sessions.computeIfAbsent(playerId, ignored -> new ViewingSession(policy));
                Location eyeLocation = player.getEyeLocation();
                Point eye = point(eyeLocation.toVector());
                Point view = point(eyeLocation.getDirection());
                List<PhysicalScreen> screens = byWorld.getOrDefault(player.getWorld().getUID(), List.of());
                PhysicalScreen current = screens.stream()
                        .filter(screen -> screen.id().equals(session.screenId()))
                        .filter(screen -> withinContext(screen, eye))
                        .findFirst().orElse(null);
                if (current != null) {
                    double maximumAngle = session.isConfirmed() ? policy.retainAngleDegrees() : policy.enterAngleDegrees();
                    session.update(observation(current, looksAt(player.getWorld(), current.geometry(),
                            eye, view, maximumAngle)), now);
                } else {
                    PhysicalScreen candidate = screens.stream()
                            .filter(screen -> screen.playback() == PhysicalScreen.Playback.PLAYING)
                            .filter(screen -> withinContext(screen, eye))
                            .sorted(Comparator.comparingDouble(screen -> screen.geometry().distanceTo(eye)))
                            .filter(screen -> looksAt(player.getWorld(), screen.geometry(), eye, view, policy.enterAngleDegrees()))
                            .findFirst().orElse(null);
                    session.update(candidate == null ? null : observation(candidate, true), now);
                }
                if (session.screenId() == null && !session.suppressesAutomaticAfk(now)) sessions.remove(playerId);
            }
            sessions.keySet().retainAll(online);
            reportedReadFailure = false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            sessions.clear();
            if (!reportedReadFailure) {
                plugin.getLogger().log(Level.WARNING,
                        "DreamDisplays viewing data unavailable; ordinary AFK detection remains active", failure);
                reportedReadFailure = true;
            }
        }
    }

    public boolean suppressesAutomaticAfk(UUID playerId, long now) {
        ViewingSession session = sessions.get(playerId);
        return session != null && session.suppressesAutomaticAfk(now);
    }

    /** Exit grace alone cannot clear an existing automatic AFK state. */
    public boolean isConfirmedViewing(UUID playerId, long now) {
        ViewingSession session = sessions.get(playerId);
        return session != null && session.isConfirmed() && session.suppressesAutomaticAfk(now);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisabled(PluginDisableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase(PLUGIN_NAME)) resetSource();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnabled(PluginEnableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase(PLUGIN_NAME)) resetSource();
    }

    private void bindSource() {
        Plugin candidate = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (candidate == null || !candidate.isEnabled()) {
            resetSource();
            return;
        }
        if (candidate == attemptedPlugin) return;
        resetSource();
        attemptedPlugin = candidate;
        try {
            source = new DreamDisplaysScreenSource(candidate.getClass().getClassLoader());
            plugin.getLogger().info("DreamDisplays physical-screen AFK exemption enabled (server-clock playback only)");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            plugin.getLogger().log(Level.WARNING,
                    "DreamDisplays server interface unsupported; physical-screen AFK exemption disabled", failure);
        }
    }

    private void resetSource() {
        sessions.clear();
        source = null;
        attemptedPlugin = null;
        reportedReadFailure = false;
    }

    private boolean withinContext(PhysicalScreen screen, Point eye) {
        ScreenGeometry geometry = screen.geometry();
        return (screen.playback() == PhysicalScreen.Playback.PLAYING || screen.playback() == PhysicalScreen.Playback.PAUSED)
                && policy.withinContext(geometry, eye);
    }

    private static ViewingSession.Observation observation(PhysicalScreen screen, boolean looking) {
        return new ViewingSession.Observation(screen.id(), screen.contentKey(), screen.playback(), looking);
    }

    private static boolean looksAt(World world, ScreenGeometry geometry, Point eye, Point view, double maximumAngle) {
        if (geometry.minimumViewAngleDegrees(eye, view) > maximumAngle) return false;
        int visible = 0;
        for (Point target : geometry.visibilityTargets()) {
            if (hasLineOfSight(world, eye, target) && ++visible >= REQUIRED_VISIBLE_TARGETS) return true;
        }
        return false;
    }

    /**
     * Rays ignore entities and fluids, but not opaque doors/walls. Ordinary glass
     * is traversed explicitly, with a finite traversal budget. The enclosing chunk
     * rectangle is checked first: this conservative guard never loads chunks just
     * to grant a viewing exemption, even for an unusually large screen.
     */
    static boolean hasLineOfSight(World world, Point eye, Point target) {
        Point delta = target.minus(eye);
        double length = delta.length();
        if (length <= 0.01 || length > MAXIMUM_RAY_DISTANCE) return false;
        int minimumChunkX = (int) Math.floor(Math.min(eye.x(), target.x()) / 16);
        int maximumChunkX = (int) Math.floor(Math.max(eye.x(), target.x()) / 16);
        int minimumChunkZ = (int) Math.floor(Math.min(eye.z(), target.z()) / 16);
        int maximumChunkZ = (int) Math.floor(Math.max(eye.z(), target.z()) / 16);
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) return false;
            }
        }
        Vector direction = new Vector(delta.x(), delta.y(), delta.z()).multiply(1 / length);
        Location start = new Location(world, eye.x(), eye.y(), eye.z());
        double remaining = length - 0.005;
        for (int traversal = 0; traversal < 16; traversal++) {
            if (remaining <= 0) return true;
            RayTraceResult hit = world.rayTraceBlocks(start, direction, remaining, FluidCollisionMode.NEVER, true);
            if (hit == null) return true;
            Block block = hit.getHitBlock();
            if (block == null || !isTransparentGlass(block.getType())) return false;
            Vector hitPosition = hit.getHitPosition();
            BoundingBox bounds = block.getBoundingBox();
            double exit = Math.min(axisExit(hitPosition.getX(), direction.getX(), bounds.getMinX(), bounds.getMaxX()),
                    Math.min(axisExit(hitPosition.getY(), direction.getY(), bounds.getMinY(), bounds.getMaxY()),
                            axisExit(hitPosition.getZ(), direction.getZ(), bounds.getMinZ(), bounds.getMaxZ())));
            if (!Double.isFinite(exit) || exit < 0) return false;
            double advance = start.toVector().distance(hitPosition) + exit + 0.001;
            remaining -= advance;
            start.add(direction.clone().multiply(advance));
        }
        return false;
    }

    private static double axisExit(double coordinate, double direction, double minimum, double maximum) {
        if (Math.abs(direction) < 1.0e-12) return Double.POSITIVE_INFINITY;
        return ((direction > 0 ? maximum : minimum) - coordinate) / direction;
    }

    private static boolean isTransparentGlass(Material material) {
        return material == Material.GLASS || material == Material.GLASS_PANE || material == Material.TINTED_GLASS
                || material.name().endsWith("_STAINED_GLASS") || material.name().endsWith("_STAINED_GLASS_PANE");
    }

    private static Point point(Vector vector) {
        return new Point(vector.getX(), vector.getY(), vector.getZ());
    }
}
