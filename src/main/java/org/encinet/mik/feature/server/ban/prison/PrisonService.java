package org.encinet.mik.feature.server.ban.prison;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.ban.BanManager;
import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.server.ban.prison.PrisonCell.Height;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotPrisons;
import org.encinet.mik.feature.world.plot.PlotPrisons.Region;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class PrisonService implements AutoCloseable {
    private static final Comparator<PrisonAssignment> BY_NAME = Comparator
            .comparing(PrisonAssignment::playerName, String.CASE_INSENSITIVE_ORDER);

    private final LanguageService language;
    private final BanManager bans;
    private final PlotPrisons plots;
    private final PrisonLanding landing;
    // Only used by the server thread. A session caches derived results and never authorizes a ban.
    private final Map<UUID, Session> sessions = new HashMap<>();
    private volatile Map<UUID, Height> worlds = Map.of();

    PrisonService(JavaPlugin plugin, LanguageService language, BanManager bans, PlotPrisons plots) {
        this.language = language;
        this.bans = bans;
        this.plots = plots;
        landing = new PrisonLanding(plugin);
    }

    void loadWorlds() {
        Bukkit.getWorlds().forEach(this::worldLoaded);
    }

    void worldLoaded(World world) {
        var next = new HashMap<>(worlds);
        next.put(world.getUID(), new Height(world.getMinHeight(), world.getMaxHeight()));
        worlds = Map.copyOf(next);
    }

    void worldUnloaded(UUID worldId) {
        var next = new HashMap<>(worlds);
        next.remove(worldId);
        worlds = Map.copyOf(next);
        landing.worldUnloaded(worldId);
    }

    List<Region> prisons() { return plots.regions(); }

    Region prison(UUID plotId) {
        return prisons().stream().filter(region -> region.plot().id().equals(plotId)).findFirst().orElse(null);
    }

    void tickLandings() { landing.tick(); }

    void track(UUID id) { sessions.computeIfAbsent(id, _ -> new Session()); }

    List<UUID> trackedAccounts() { return sessions.isEmpty() ? List.of() : List.copyOf(sessions.keySet()); }

    /** Recheck against current bans when the staged plot edit is about to become visible. */
    boolean mayPublishPlots(List<Region> before, List<Region> after) {
        Map<UUID, Height> available = worlds;
        if (before.stream().noneMatch(region -> available.containsKey(region.plot().world()))
                || after.stream().anyMatch(region -> available.containsKey(region.plot().world()))) return true;
        return Bukkit.getOnlinePlayers().stream().noneMatch(player -> confined(player.getUniqueId()));
    }

    List<PrisonAssignment> prisoners() {
        List<Region> regions = prisons();
        Map<UUID, Height> available = worlds;
        return bans.activeRecords().stream().map(ban -> PrisonAssignment.select(ban, regions, available::containsKey))
                .filter(Objects::nonNull).sorted(BY_NAME).toList();
    }

    /** Configuration may read immutable ban and plot snapshots on an async thread. */
    PrisonAssignment assignment(UUID playerId) {
        BanRecord ban = activeBan(playerId);
        return ban == null ? null : PrisonAssignment.select(ban, prisons(), worlds::containsKey);
    }

    CompletableFuture<Location> prepare(PrisonAssignment inmate) {
        return landing.prepare(inmate.cell(), worlds.get(inmate.cell().world()));
    }

    Optional<PrisonAssignment> prisoner(UUID playerId) {
        BanRecord ban = activeBan(playerId);
        if (ban == null) return Optional.empty();
        Session session = sessions.get(playerId);
        return Optional.ofNullable(session == null
                ? PrisonAssignment.select(ban, prisons(), worlds::containsKey) : assignment(session, ban));
    }

    boolean confined(UUID playerId) { return activeBan(playerId) != null; }

    private BanRecord activeBan(UUID playerId) { return bans.active(playerId, null).orElse(null); }

    private PrisonAssignment assignment(Session session, BanRecord ban) {
        List<Region> regions = prisons();
        Map<UUID, Height> available = worlds;
        if (session.regions != regions || session.worlds != available) {
            PrisonAssignment next = PrisonAssignment.select(ban, regions, available::containsKey);
            if (next == null || !next.sameRoom(session.assignment)) session.clearLanding();
            session.assignment = next;
            session.regions = regions;
            session.worlds = available;
        } else if (session.assignment != null && session.assignment.ban() != ban) {
            var current = session.assignment;
            session.assignment = new PrisonAssignment(ban, current.plot(), current.cell());
        }
        return session.assignment;
    }

    void onJoin(Player player) {
        UUID playerId = player.getUniqueId();
        BanRecord ban = activeBan(playerId);
        if (ban == null) return;
        Session session = sessions.computeIfAbsent(playerId, _ -> new Session());
        PrisonAssignment inmate = assignment(session, ban);
        Location arrived = player.getLocation();
        if (inmate == null || !PrisonLanding.valid(inmate.cell(), arrived) || !PrisonCell.containsPlayer(inmate.cell(), arrived, player)) {
            unavailable(player);
            return;
        }
        session.entrance = arrived;
    }

    PrisonAssignment update(Player player, boolean refreshStatus) {
        UUID id = player.getUniqueId();
        BanRecord ban = activeBan(id);
        if (ban == null) {
            clear(id);
            return null;
        }
        Session session = sessions.computeIfAbsent(id, _ -> new Session());
        PrisonAssignment inmate = assignment(session, ban);
        if (inmate == null) {
            unavailable(player);
            return null;
        }
        if (!PrisonCell.containsPlayer(inmate.cell(), player.getLocation(), player)) {
            if (session.relocation == null) session.relocation = prepare(inmate);
            if (!session.relocation.isDone()) return null;
            Location target = completedLocation(session.relocation);
            session.relocation = null;
            if (!PrisonLanding.valid(inmate.cell(), target) || !PrisonCell.containsPlayer(inmate.cell(), target, player)
                    || !relocate(player, session, target)
                    || !PrisonCell.containsPlayer(inmate.cell(), player.getLocation(), player)) {
                unavailable(player);
                return null;
            }
            session.entrance = target;
        } else if (refreshStatus && session.entrance == null) {
            session.entrance = PrisonLanding.near(inmate.cell(), player.getLocation());
        }
        if (!refreshStatus) return inmate;
        String plotName = inmate.plot().name();
        String cellName = inmate.cell().name();
        player.sendActionBar(inmate.isPermanent()
                ? language.text(player, Message.PRISON_ACTION_BAR_PERMANENT, NamedTextColor.RED, plotName, cellName)
                : language.text(player, Message.PRISON_ACTION_BAR, NamedTextColor.RED,
                        PrisonDuration.remaining(inmate.ban().expiresAt().toEpochMilli(), System.currentTimeMillis()),
                        plotName, cellName));
        return inmate;
    }

    private static Location completedLocation(CompletableFuture<Location> future) {
        return future.isCompletedExceptionally() ? null : future.getNow(null);
    }

    Location panelAnchor(UUID id) {
        Session session = sessions.get(id);
        return session == null ? null : session.entrance;
    }

    Location respawn(Player player) {
        UUID id = player.getUniqueId();
        BanRecord ban = activeBan(id);
        if (ban == null) return null;
        Session session = sessions.computeIfAbsent(id, _ -> new Session());
        PrisonAssignment inmate = assignment(session, ban);
        if (inmate != null) {
            if (PrisonLanding.valid(inmate.cell(), session.entrance)) return session.entrance.clone();
            Location anchor = session.entrance == null ? player.getLocation() : session.entrance;
            Location alternative = PrisonLanding.near(inmate.cell(), anchor);
            if (alternative != null) {
                session.entrance = alternative;
                return alternative.clone();
            }
        }
        unavailable(player);
        return null;
    }

    void unavailable(Player player) {
        player.kick(language.text(player, Message.PRISON_ERROR_PLOT_UNAVAILABLE, NamedTextColor.RED),
                PlayerKickEvent.Cause.BANNED);
    }

    boolean canMoveTo(Player player, Location destination) {
        UUID id = player.getUniqueId();
        BanRecord ban = activeBan(id);
        if (ban == null) return true;
        PrisonAssignment inmate = assignment(sessions.computeIfAbsent(id, _ -> new Session()), ban);
        return inmate != null && PrisonCell.containsPlayer(inmate.cell(), destination, player);
    }

    private static boolean relocate(Player player, Session session, Location target) {
        session.teleportTarget = target;
        try {
            return player.teleport(target.clone());
        } finally {
            session.teleportTarget = null;
        }
    }

    boolean canTeleportTo(Player player, Location destination) {
        UUID id = player.getUniqueId();
        if (!confined(id)) return true;
        Session session = sessions.get(id);
        if (session == null || session.teleportTarget == null || destination == null
                || !session.teleportTarget.equals(destination)) return false;
        session.teleportTarget = null;
        return canMoveTo(player, destination);
    }

    boolean mayBuild(Player player, Location location) {
        if (confined(player.getUniqueId())) return false;
        Plot plot = plots.at(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return plot == null || plot.canManage(player.getUniqueId(), RolePermissions.canModerate(player));
    }

    void clear(UUID id) {
        Session session = sessions.remove(id);
        if (session != null) session.clearLanding();
    }

    @Override public void close() {
        landing.close();
        sessions.clear();
        worlds = Map.of();
    }

    private static final class Session {
        List<Region> regions;
        Map<UUID, Height> worlds;
        PrisonAssignment assignment;
        Location entrance;
        Location teleportTarget;
        CompletableFuture<Location> relocation;

        void clearLanding() {
            if (relocation != null) relocation.cancel(false);
            relocation = null;
            entrance = null;
        }
    }
}
