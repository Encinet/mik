package org.encinet.mik.feature.server.prison;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.encinet.mik.feature.server.ban.BanManager;
import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotPrisons;
import org.encinet.mik.feature.world.plot.PlotPrisons.Region;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class PrisonService {
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "msg", "tell", "r", "reply", "help", "rules", "minecraft:help", "bukkit:help", "mik:msg");
    private static final Comparator<PrisonAssignment> BY_NAME = Comparator
            .comparing(PrisonAssignment::playerName, String.CASE_INSENSITIVE_ORDER);

    private final LanguageService language;
    private final BanManager bans;
    private final PlotPrisons plots;
    private final Set<UUID> teleporting = new HashSet<>();
    private volatile Map<UUID, WorldHeight> worlds = Map.of();

    PrisonService(LanguageService language, BanManager bans, PlotPrisons plots) {
        this.language = language;
        this.bans = bans;
        this.plots = plots;
    }

    void loadWorlds() {
        Bukkit.getWorlds().forEach(this::worldLoaded);
    }

    void worldLoaded(World world) {
        var next = new HashMap<>(worlds);
        next.put(world.getUID(), new WorldHeight(world.getMinHeight(), world.getMaxHeight()));
        worlds = Map.copyOf(next);
    }

    void worldUnloaded(UUID worldId) {
        var next = new HashMap<>(worlds);
        next.remove(worldId);
        worlds = Map.copyOf(next);
    }

    List<Region> prisons() {
        return plots.regions();
    }

    Region prison(UUID plotId) {
        return prisons().stream().filter(region -> region.plot().id().equals(plotId)).findFirst().orElse(null);
    }

    boolean mayRemovePlot(UUID plotId) {
        return bans.activeRecords().stream().noneMatch(ban -> {
            PrisonAssignment inmate = assign(ban);
            return inmate != null && inmate.plot().id().equals(plotId);
        });
    }

    boolean allowsBannedLogin(UUID playerId) {
        return prisoner(playerId).isPresent();
    }

    List<PrisonAssignment> prisoners() {
        return bans.activeRecords().stream().map(this::assign).filter(java.util.Objects::nonNull)
                .sorted(BY_NAME).toList();
    }

    Optional<PrisonAssignment> prisoner(UUID playerId) {
        return bans.active(playerId, null).map(this::assign);
    }

    /** Uses only immutable catalog and world-height snapshots, including during async login. */
    private PrisonAssignment assign(BanRecord ban) {
        Map<UUID, WorldHeight> heights = worlds;
        return PrisonAssignment.select(ban, prisons(), cell -> {
            WorldHeight height = heights.get(cell.world());
            return height == null ? null : PrisonCell.entrance(cell, height.min(), height.max());
        });
    }

    void onJoin(Player player) {
        bans.active(player.getUniqueId(), player.getName())
                .ifPresent(ban -> confine(player, assign(ban), true));
    }

    PrisonAssignment update(Player player) {
        BanRecord ban = bans.active(player.getUniqueId(), player.getName()).orElse(null);
        if (ban == null) return null;
        PrisonAssignment inmate = assign(ban);
        if (!confine(player, inmate, false)) return null;
        String plotName = inmate.plot().name();
        String cellName = inmate.cell().name();
        player.sendActionBar(inmate.isPermanent()
                ? language.text(player, Message.PRISON_ACTION_BAR_PERMANENT, NamedTextColor.RED, plotName, cellName)
                : language.text(player, Message.PRISON_ACTION_BAR, NamedTextColor.RED,
                        PrisonDuration.remaining(inmate.ban().expiresAt().toEpochMilli(), System.currentTimeMillis()),
                        plotName, cellName));
        return inmate;
    }

    private boolean confine(Player player, PrisonAssignment inmate, boolean joined) {
        if (inmate == null || !player.getUniqueId().equals(inmate.playerId()) || ((joined || !PrisonCell.inside(inmate.cell(), player.getLocation()))
                && !teleport(player, entrance(inmate)))) {
            player.kick(language.text(player, Message.PRISON_ERROR_PLOT_UNAVAILABLE, NamedTextColor.RED),
                    PlayerKickEvent.Cause.BANNED);
            return false;
        }
        return true;
    }

    Location entrance(PrisonAssignment inmate) {
        var point = inmate.entrance();
        World world = Bukkit.getWorld(point.world());
        if (world == null) return null;
        Location entrance = new Location(world, point.x(), point.y(), point.z());
        if (!entrance.getBlock().isPassable() || !entrance.getBlock().getRelative(0, 1, 0).isPassable()) return null;
        return entrance;
    }

    private boolean teleport(Player player, Location destination) {
        if (destination == null) return false;
        teleporting.add(player.getUniqueId());
        try {
            return player.teleport(destination);
        } finally {
            teleporting.remove(player.getUniqueId());
        }
    }

    boolean canMoveTo(Player player, Location destination) {
        return teleporting.contains(player.getUniqueId()) || prisoner(player.getUniqueId())
                .map(inmate -> PrisonCell.inside(inmate.cell(), destination)).orElse(true);
    }

    boolean mayBuild(Player player, Location location) {
        if (prisoner(player.getUniqueId()).isPresent()) return false;
        Plot plot = plots.at(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return plot == null || plot.canManage(player.getUniqueId(), RolePermissions.canModerate(player));
    }

    static boolean allowedCommand(String command) {
        String label = command.strip().replaceFirst("^/", "").split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return ALLOWED_COMMANDS.contains(label);
    }

    private record WorldHeight(int min, int max) { }
}
