package org.encinet.mik.feature.server.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.ban.BanManager;
import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.world.plot.PrisonPlotHost;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

final class PrisonService {
    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final BanManager bans;
    private final PrisonSettings settings;
    private final PrisonStore store;
    private final Set<UUID> teleporting = new HashSet<>();
    private final Object lock = new Object();
    private Map<UUID, PrisonRecord> prisonRecords = Map.of();
    private Map<UUID, PrisonerRecord> prisoners = Map.of();
    private volatile PrisonPlotHost host;
    private volatile Set<UUID> jailedLoginIds = Set.of();

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
        refreshAdmissionSnapshot();
    }

    /** Refuses toggling off or deleting a prison plot while any inmate remains in it. */
    Predicate<Plot> inmateFreeGuard() {
        return plot -> prisonersLocked().stream().noneMatch(inmate -> inmate.plot().equals(plot.id()));
    }

    void load() throws IOException {
        PrisonData data = store.load();
        synchronized (lock) {
            prisonRecords = data.prisons();
            prisoners = data.prisoners();
        }
        refreshAdmissionSnapshot();
    }

    void save() throws IOException {
        synchronized (lock) {
            store.save(new PrisonData(prisonRecords, prisoners));
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

    List<PrisonerRecord> prisoners() {
        return prisonersLocked().stream().sorted(java.util.Comparator.comparing(PrisonerRecord::playerName,
                String.CASE_INSENSITIVE_ORDER)).toList();
    }

    List<PrisonerRecord> prisoners(UUID plotId) {
        return prisonersLocked().stream().filter(inmate -> inmate.plot().equals(plotId)).toList();
    }

    Optional<PrisonerRecord> prisoner(UUID playerId) {
        return Optional.ofNullable(prisonerLocked(playerId));
    }

    boolean allowsBannedLogin(UUID playerId) {
        return jailedLoginIds.contains(playerId);
    }

    PrisonRecord setExit(UUID plotId, PrisonLocation exit) {
        requireHost();
        requirePrison(plotId);
        Map<UUID, PrisonRecord> updated = new HashMap<>(prisonRecordsLocked());
        updated.put(plotId, new PrisonRecord(plotId, exit));
        commit(updated, null);
        return new PrisonRecord(plotId, exit);
    }

    PrisonerRecord jail(Player actor, Player target, UUID plotId, long durationMillis) {
        if (!RolePermissions.canModerate(actor)) throw new IllegalStateException("Moderator permission required");
        if (target.getUniqueId().equals(actor.getUniqueId()))
            throw new IllegalArgumentException("Cannot jail yourself");
        return jail(target.getUniqueId(), target.getName(), plotId, durationMillis, false,
                PrisonLocation.capture(target.getLocation()));
    }

    PrisonerRecord jail(Player actor, UUID targetId, String targetName, UUID plotId, long durationMillis) {
        if (!RolePermissions.canModerate(actor)) throw new IllegalStateException("Moderator permission required");
        if (targetId.equals(actor.getUniqueId())) throw new IllegalArgumentException("Cannot jail yourself");
        Player target = Bukkit.getPlayer(targetId);
        return jail(targetId, targetName, plotId, durationMillis, false,
                target == null ? null : PrisonLocation.capture(target.getLocation()));
    }

    private PrisonerRecord jail(UUID playerId, String playerName, UUID plotId, long durationMillis,
                                boolean requireUnban, PrisonLocation returnLocation) {
        requireHost();
        if (durationMillis < 0) throw new IllegalArgumentException("Sentence duration cannot be negative");
        if (prisonerLocked(playerId) != null) throw new IllegalStateException("Player is already imprisoned");
        requirePrison(plotId);
        long now = System.currentTimeMillis();
        long sentenceEnd = 0;
        if (durationMillis > 0) {
            try {
                sentenceEnd = Math.addExact(now, durationMillis);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Sentence duration is too long", exception);
            }
        }
        PrisonerRecord prisoner = new PrisonerRecord(playerId, playerName, plotId, sentenceEnd,
                requireUnban, returnLocation, now);
        commit(null, Map.of(playerId, prisoner));
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) teleport(online, location(prisoner).orElse(null));
        return prisoner;
    }

    void release(Player actor, UUID playerId) {
        if (!RolePermissions.canModerate(actor)) throw new IllegalStateException("Moderator permission required");
        release(playerId);
    }

    void release(UUID playerId) {
        PrisonerRecord prisoner = prisonerLocked(playerId);
        if (prisoner == null) throw new IllegalArgumentException("Player is not imprisoned");
        Map<UUID, PrisonerRecord> updated = new HashMap<>();
        for (PrisonerRecord inmate : prisonersLocked()) updated.put(inmate.playerId(), inmate);
        updated.remove(playerId);
        commit(null, updated);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            PrisonLocation exit = recordFor(prisoner.plot()).exit();
            Location destination = exit == null ? null : exit.resolve();
            if (destination == null && prisoner.returnLocation() != null)
                destination = prisoner.returnLocation().resolve();
            if (destination != null) teleport(player, destination);
        }
    }

    void tick() {
        syncActiveBans();
        releaseExpired();
        refreshAdmissionSnapshot();
        for (PrisonerRecord prisoner : prisonersLocked()) {
            Player player = Bukkit.getPlayer(prisoner.playerId());
            if (player != null) {
                Plot plot = host != null ? host.find(prisoner.plot()) : null;
                String prisonLabel = plot == null ? "unknown plot" : plot.name();
                List<Plot> cells = host == null ? List.of() : host.subplots(prisoner.plot());
                String cellLabel = cells.isEmpty() ? prisonLabel
                        : cells.get(PrisonCell.index(prisoner.playerId(), prisoner.plot(), cells.size())).name();
                player.sendActionBar(languageService.text(player, Message.PRISON_ACTION_BAR,
                        net.kyori.adventure.text.format.NamedTextColor.RED,
                        PrisonDuration.remaining(prisoner.sentenceEndMillis(), System.currentTimeMillis()),
                        prisonLabel, cellLabel));
            }
        }
    }

    void handleJoin(Player player) {
        PrisonerRecord prisoner = prisonerLocked(player.getUniqueId());
        if (prisoner == null) return;
        Location destination = location(prisoner).orElse(null);
        if (destination == null) {
            plugin.getLogger().severe("Prisoner " + player.getUniqueId()
                    + " has no resolvable cell; login admission denied if banned.");
            return;
        }
        teleport(player, destination);
    }

    /** Where a prisoner stands: the hash-assigned cell region's top band, or the plot's own band. */
    Optional<Location> location(PrisonerRecord prisoner) {
        PrisonPlotHost host = this.host;
        Plot plot = host == null ? null : host.find(prisoner.plot());
        if (plot == null || !host.isEnabled(plot.id())) return Optional.empty();
        return Optional.ofNullable(PrisonCell.standing(PrisonCell.cellOf(host, prisoner.playerId(), plot)));
    }

    boolean isTeleporting(UUID playerId) {
        return teleporting.contains(playerId);
    }

    boolean canMoveTo(Player player, Location destination) {
        PrisonerRecord prisoner = prisonerLocked(player.getUniqueId());
        if (prisoner == null) return true;
        if (destination == null || destination.getWorld() == null) return false;
        PrisonPlotHost host = this.host;
        if (host == null) return false;
        Plot plot = host.find(prisoner.plot());
        if (plot == null || !host.isEnabled(plot.id())) return false;
        return PrisonCell.inside(PrisonCell.cellOf(host, prisoner.playerId(), plot), destination);
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
        if (prisonerLocked(player.getUniqueId()) != null) return false;
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

    private void syncActiveBans() {
        if (!settings.autoJailBans()) return;
        PrisonPlotHost host = this.host;
        if (host == null) return;
        List<Plot> candidates = host.enabled();
        for (BanRecord ban : bans.activeRecords()) {
            UUID id = ban.playerUuid();
            if (id == null || prisonerLocked(id) != null) continue;
            Plot target = null;
            for (Plot plot : candidates) {
                if (PrisonCell.standing(PrisonCell.cellOf(host, id, plot)) != null) { target = plot; break; }
            }
            if (target == null) continue;
            long duration = ban.expiresAt() == null ? settings.defaultBanSentenceMillis()
                    : Math.max(1, ban.expiresAt().toEpochMilli() - System.currentTimeMillis());
            try {
                jail(id, ban.playerName(), target.id(), duration, true, null);
            } catch (IllegalStateException | IllegalArgumentException exception) {
                plugin.getLogger().warning("Could not jail banned player " + ban.playerName() + ": " + exception.getMessage());
            }
        }
    }

    private void releaseExpired() {
        long now = System.currentTimeMillis();
        for (PrisonerRecord prisoner : List.copyOf(prisonersLocked())) {
            if (!prisoner.hasSentence() || prisoner.sentenceEndMillis() > now
                    || !settings.releaseWhenSentenceEnds()) continue;
            boolean banned = bans.active(prisoner.playerId(), prisoner.playerName()).isPresent();
            if (!prisoner.requireUnban() || !banned) {
                try {
                    release(prisoner.playerId());
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Could not release prisoner " + prisoner.playerName() + ": " + exception.getMessage());
                }
            }
        }
        if (settings.releaseOnUnban()) {
            for (PrisonerRecord prisoner : List.copyOf(prisonersLocked())) {
                if (prisoner.requireUnban() && bans.active(prisoner.playerId(), prisoner.playerName()).isEmpty()) {
                    try {
                        release(prisoner.playerId());
                    } catch (IllegalArgumentException exception) {
                        plugin.getLogger().warning("Could not release unbanned prisoner " + prisoner.playerName()
                                + ": " + exception.getMessage());
                    }
                }
            }
        }
    }

    /** The exit point of a prison plot, if one is configured. */
    PrisonLocation exitFor(UUID plotId) {
        PrisonPlotHost host = this.host;
        if (host == null || !host.isEnabled(plotId)) return null;
        return recordFor(plotId).exit();
    }

    /** The plot of an inmate, when the host is bound and the plot still runs the add-on. */
    Plot plotFor(PrisonerRecord inmate) {
        return plotFor(inmate.plot());
    }

    /** An enabled prison plot, or null when the host is unbound or the plot no longer runs the add-on. */
    Plot plotFor(UUID plotId) {
        PrisonPlotHost host = this.host;
        if (host == null || !host.isEnabled(plotId)) return null;
        return host.find(plotId);
    }

    /** The bound plot host; null until the bootstrap wiring runs. */
    public PrisonPlotHost host() { return host; }

    /** The active ban reason for an inmate, or empty when no ban matches. */
    String banReason(PrisonerRecord prisoner) {
        return bans.active(prisoner.playerId(), prisoner.playerName())
                .map(BanRecord::reason).orElse("");
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

    private void refreshAdmissionSnapshot() {
        if (!settings.allowBannedPrisonerLogin() || host == null) {
            jailedLoginIds = Set.of();
            return;
        }
        Set<UUID> admitted = new HashSet<>();
        for (PrisonerRecord prisoner : prisonersLocked())
            if (location(prisoner).isPresent()) admitted.add(prisoner.playerId());
        jailedLoginIds = Set.copyOf(admitted);
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

    private List<PrisonerRecord> prisonersLocked() {
        synchronized (lock) { return List.copyOf(prisoners.values()); }
    }

    private PrisonerRecord prisonerLocked(UUID playerId) {
        synchronized (lock) { return prisoners.get(playerId); }
    }

    private void commit(Map<UUID, PrisonRecord> updatedPrisons, Map<UUID, PrisonerRecord> updatedPrisoners) {
        synchronized (lock) {
            if (updatedPrisons != null) prisonRecords = Map.copyOf(updatedPrisons);
            if (updatedPrisoners != null) prisoners = Map.copyOf(updatedPrisoners);
            Map<UUID, PrisonRecord> record = prisonRecords;
            Map<UUID, PrisonerRecord> inmate = prisoners;
            try {
                store.save(new PrisonData(record, inmate));
            } catch (IOException exception) {
                throw new IllegalStateException("Could not save prison data", exception);
            }
        }
        refreshAdmissionSnapshot();
    }
}
