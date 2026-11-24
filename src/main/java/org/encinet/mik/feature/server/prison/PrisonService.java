package org.encinet.mik.feature.server.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.ban.BanManager;
import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PrisonPlotHost;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Prison rules over the ban list: a ban is the sentence, so the inmates are exactly the active
 * bans that land inside an enabled prison plot. Only exit points are persisted here.
 */
final class PrisonService {
    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final BanManager bans;
    private final PrisonSettings settings;
    private final PrisonStore store;
    private final Set<UUID> teleporting = new HashSet<>();
    private final Object lock = new Object();
    private Map<UUID, PrisonRecord> prisonRecords = Map.of();
    private volatile PrisonPlotHost host;
    private static final Comparator<PrisonerRecord> BY_NAME = Comparator
            .comparing(PrisonerRecord::playerName, String.CASE_INSENSITIVE_ORDER);

    PrisonService(JavaPlugin plugin, LanguageService languageService, BanManager bans,
                  PrisonSettings settings, PrisonStore store) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.bans = bans;
        this.settings = settings;
        this.store = store;
    }

    /** Lazy plot binding: the prison module starts before the plot registry is available. */
    void bind(PrisonPlotHost plotHost) {
        this.host = plotHost;
    }

    /** Refuses toggling off or deleting a prison plot while any banned player is locked inside it. */
    Predicate<Plot> inmateFreeGuard() {
        return plot -> prisoners().stream().noneMatch(inmate -> inmate.plot().id().equals(plot.id()));
    }

    void load() throws IOException {
        PrisonData data = store.load();
        synchronized (lock) {
            prisonRecords = data.prisons();
        }
    }

    void save() throws IOException {
        synchronized (lock) {
            store.save(new PrisonData(prisonRecords));
        }
    }

    /** Prison plots: the flag on the plot is the source of truth; the store only adds exit points. */
    List<PrisonRecord> prisons() {
        if (host == null) return List.of();
        return host.enabled().stream()
                .map(plot -> new PrisonRecord(plot.id(), recordFor(plot.id()).exit()))
                .toList();
    }

    List<PrisonRecord> manageablePrisons(Player player) {
        PrisonPlotHost host = this.host;
        if (host == null) return List.of();
        return host.enabled().stream()
                .filter(plot -> RolePermissions.canModerate(player) || host.canManage(plot, player.getUniqueId(),
                        RolePermissions.canModerate(player)))
                .map(plot -> new PrisonRecord(plot.id(), recordFor(plot.id()).exit()))
                .toList();
    }

    /** Every active ban that lands inside an enabled prison plot, by name. */
    List<PrisonerRecord> prisoners() {
        PrisonPlotHost host = this.host;
        if (host == null) return List.of();
        List<Plot> prisons = host.enabled();
        if (prisons.isEmpty()) return List.of();
        Instant now = Instant.now();
        List<PrisonerRecord> inmates = new ArrayList<>();
        for (BanRecord ban : bans.activeRecords()) {
            if (ban.playerUuid() == null) continue;
            Plot plot = prisonFor(host, prisons, ban.playerUuid());
            if (plot != null) inmates.add(new PrisonerRecord(ban, plot, now));
        }
        inmates.sort(BY_NAME);
        return List.copyOf(inmates);
    }

    List<PrisonerRecord> prisoners(UUID plotId) {
        return prisoners().stream().filter(inmate -> inmate.plot().id().equals(plotId)).toList();
    }

    /** The inmate holding this account, or empty when the account is not banned inside a prison. */
    Optional<PrisonerRecord> prisoner(UUID playerId) {
        PrisonPlotHost host = this.host;
        if (host == null) return Optional.empty();
        List<Plot> prisons = host.enabled();
        if (prisons.isEmpty()) return Optional.empty();
        return bans.activeRecords().stream()
                .filter(ban -> ban.playerUuid() != null && ban.playerUuid().equals(playerId))
                .findFirst()
                .map(ban -> {
                    Plot plot = prisonFor(host, prisons, playerId);
                    return plot == null ? null : new PrisonerRecord(ban, plot, Instant.now());
                });
    }

    boolean allowsBannedLogin(UUID playerId) {
        return prisoner(playerId).flatMap(this::location).isPresent();
    }

    PrisonRecord setExit(UUID plotId, PrisonLocation exit) {
        requireHost();
        requirePrison(plotId);
        Map<UUID, PrisonRecord> updated = new HashMap<>(prisonRecordsLocked());
        updated.put(plotId, new PrisonRecord(plotId, exit));
        commit(updated);
        return new PrisonRecord(plotId, exit);
    }

    /** Moves the inmate to their cell after login; silently keeps them in place without one. */
    void handleJoin(Player player) {
        PrisonerRecord prisoner = prisoner(player.getUniqueId()).orElse(null);
        if (prisoner == null) return;
        Location destination = location(prisoner).orElse(null);
        if (destination == null) {
            plugin.getLogger().warning("Banned player " + prisoner.playerName()
                    + " has no resolvable prison cell; the ban is enforced without a prison cell.");
            return;
        }
        teleport(player, destination);
    }

    /** Where a prisoner stands: the hash-assigned cell region's top band, or the plot's own band. */
    Optional<Location> location(PrisonerRecord prisoner) {
        PrisonPlotHost host = this.host;
        Plot plot = prisoner.plot();
        if (host == null || !host.isEnabled(plot.id())) return Optional.empty();
        return Optional.ofNullable(PrisonCell.standing(PrisonCell.cellOf(host, prisoner.playerId(), plot)));
    }

    boolean isTeleporting(UUID playerId) {
        return teleporting.contains(playerId);
    }

    boolean canMoveTo(Player player, Location destination) {
        PrisonerRecord prisoner = prisoner(player.getUniqueId()).orElse(null);
        if (prisoner == null) return true;
        if (destination == null || destination.getWorld() == null) return false;
        PrisonPlotHost host = this.host;
        if (host == null || !host.isEnabled(prisoner.plot().id())) return false;
        return PrisonCell.inside(PrisonCell.cellOf(host, prisoner.playerId(), prisoner.plot()), destination);
    }

    /** The prison plot (if any) protecting the block; null when no enabled prison plot covers it. */
    Plot protectedPlot(Location location) {
        PrisonPlotHost host = this.host;
        if (host == null || location == null || location.getWorld() == null) return null;
        Plot plot = host.plotAt(location.getWorld().getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return plot != null && host.isEnabled(plot.id()) ? plot : null;
    }

    boolean protect(Location location) {
        return settings.protectRegion() && protectedPlot(location) != null;
    }

    boolean mayBuild(Player player, Location location) {
        if (prisoner(player.getUniqueId()).isPresent()) return false;
        Plot plot = protectedPlot(location);
        if (plot == null) return true;
        if (RolePermissions.canModerate(player)) return true;
        PrisonPlotHost host = this.host;
        if (host == null) return true;
        return host.canManage(plot, player.getUniqueId(), false);
    }

    boolean allowedCommand(String command) {
        String label = command.strip().replaceFirst("^/", "").split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) label = label.substring(namespace + 1);
        return settings.allowedCommands().contains(label);
    }

    /** Refreshes the per-tick action bar of online inmates. */
    void tick() {
        List<PrisonerRecord> inmates = prisoners();
        if (inmates.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (PrisonerRecord inmate : inmates) {
            Player player = Bukkit.getPlayer(inmate.playerId());
            if (player == null) continue;
            PrisonPlotHost host = this.host;
            String prisonLabel = inmate.plot().name();
            List<Plot> cells = host == null ? List.of() : host.subplots(inmate.plot().id());
            String cellLabel = cells.isEmpty() ? prisonLabel
                    : cells.get(PrisonCell.index(inmate.playerId(), inmate.plot().id(), cells.size())).name();
            String remaining = inmate.isPermanent() ? null
                    : PrisonDuration.remaining(inmate.ban().expiresAt().toEpochMilli(), now);
            player.sendActionBar(remaining == null
                    ? languageService.text(player, Message.PRISON_ACTION_BAR_PERMANENT,
                        net.kyori.adventure.text.format.NamedTextColor.RED, prisonLabel, cellLabel)
                    : languageService.text(player, Message.PRISON_ACTION_BAR,
                        net.kyori.adventure.text.format.NamedTextColor.RED, remaining, prisonLabel, cellLabel));
        }
    }

    /** The exit point of a prison plot, if one is configured. */
    PrisonLocation exitFor(UUID plotId) {
        PrisonPlotHost host = this.host;
        if (host == null || !host.isEnabled(plotId)) return null;
        return recordFor(plotId).exit();
    }

    /** An enabled prison plot, or null when the host is unbound or the plot no longer runs the add-on. */
    Plot plotFor(UUID plotId) {
        PrisonPlotHost host = this.host;
        if (host == null || !host.isEnabled(plotId)) return null;
        return host.find(plotId);
    }

    /** The bound plot host; null until the bootstrap wiring runs. */
    public PrisonPlotHost host() { return host; }

    /** The first enabled prison plot whose cells contain the banned account, or null. */
    private static Plot prisonFor(PrisonPlotHost host, List<Plot> prisons, UUID playerId) {
        for (Plot plot : prisons) {
            if (PrisonCell.standing(PrisonCell.cellOf(host, playerId, plot)) != null) return plot;
        }
        return null;
    }

    private PrisonRecord recordFor(UUID plotId) {
        return prisonRecordsLocked().getOrDefault(plotId, new PrisonRecord(plotId, null));
    }

    private void requireHost() {
        if (host == null) throw new IllegalStateException("Prison plot host is not bound yet");
    }

    private void requirePrison(UUID plotId) {
        requireHost();
        if (!host.isEnabled(plotId)) throw new IllegalArgumentException("Prison plot does not exist: " + plotId);
    }

    private void teleport(Player player, Location destination) {
        if (destination == null) return;
        teleporting.add(player.getUniqueId());
        try {
            player.teleport(destination);
        } finally {
            teleporting.remove(player.getUniqueId());
        }
    }

    private Map<UUID, PrisonRecord> prisonRecordsLocked() {
        synchronized (lock) { return prisonRecords; }
    }

    private void commit(Map<UUID, PrisonRecord> updatedPrisons) {
        synchronized (lock) {
            prisonRecords = Map.copyOf(updatedPrisons);
            try {
                store.save(new PrisonData(prisonRecords));
            } catch (IOException exception) {
                throw new IllegalStateException("Could not save prison data", exception);
            }
        }
    }
}
