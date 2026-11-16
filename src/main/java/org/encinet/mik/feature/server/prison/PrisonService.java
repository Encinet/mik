package org.encinet.mik.feature.server.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.ban.BanManager;
import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class PrisonService {
    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final BanManager bans;
    private final PrisonSettings settings;
    private final PrisonStore store;
    private final Set<UUID> teleporting = new HashSet<>();
    private Map<String, PrisonRecord> prisons = Map.of();
    private Map<UUID, PrisonerRecord> prisoners = Map.of();
    private volatile Set<UUID> jailedLoginIds = Set.of();

    PrisonService(JavaPlugin plugin, LanguageService languageService, BanManager bans,
                  PrisonSettings settings, PrisonStore store) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.bans = bans;
        this.settings = settings;
        this.store = store;
    }

    void load() throws IOException {
        PrisonData data = store.load();
        prisons = data.prisons();
        prisoners = data.prisoners();
        refreshAdmissionSnapshot();
    }

    void save() throws IOException {
        store.save(new PrisonData(prisons, prisoners));
    }

    List<PrisonRecord> prisons() {
        return prisons.values().stream().sorted(Comparator.comparing(PrisonRecord::name)).toList();
    }

    List<PrisonRecord> manageablePrisons(Player player) {
        return prisons().stream().filter(prison -> canManage(player, prison)).toList();
    }

    List<PrisonerRecord> prisoners() {
        return prisoners.values().stream().sorted(Comparator.comparing(PrisonerRecord::playerName,
                String.CASE_INSENSITIVE_ORDER)).toList();
    }

    List<PrisonerRecord> prisoners(String prisonName) {
        String key = PrisonRecord.normalize(prisonName);
        return prisoners().stream().filter(prisoner -> prisoner.prison().equals(key)).toList();
    }

    Optional<PrisonRecord> prison(String name) {
        return Optional.ofNullable(prisons.get(PrisonRecord.normalize(name)));
    }

    Optional<PrisonerRecord> prisoner(UUID playerId) {
        return Optional.ofNullable(prisoners.get(playerId));
    }

    boolean allowsBannedLogin(UUID playerId) {
        return jailedLoginIds.contains(playerId);
    }

    boolean canManage(Player player, PrisonRecord prison) {
        return RolePermissions.canModerate(player)
                || prison.owner() != null && prison.owner().equals(player.getUniqueId());
    }

    PrisonRecord create(Player owner, String name) {
        if (!settings.allowPlayerPrisons() && !RolePermissions.canModerate(owner))
            throw new IllegalStateException("Player prisons are disabled");
        String key = PrisonRecord.normalize(name);
        if (prisons.containsKey(key)) throw new IllegalArgumentException("A prison with that name already exists");
        PrisonRecord created = new PrisonRecord(key, RolePermissions.canModerate(owner) ? null : owner.getUniqueId(),
                null, null, null, null, Map.of());
        Map<String, PrisonRecord> updated = new HashMap<>(prisons);
        updated.put(key, created);
        commit(updated, prisoners);
        return created;
    }

    PrisonRecord setLocation(Player actor, String name, String field) {
        PrisonRecord prison = requireManageable(actor, name);
        PrisonLocation location = PrisonLocation.capture(actor.getLocation());
        PrisonRecord updatedPrison = switch (field) {
            case "spawn" -> prison.spawn(location);
            case "exit" -> prison.exit(location);
            case "corner1" -> prison.corner1(location);
            case "corner2" -> prison.corner2(location);
            default -> throw new IllegalArgumentException("Unknown prison location");
        };
        if (updatedPrison.corner1() != null && updatedPrison.corner2() != null
                && !updatedPrison.corner1().world().equals(updatedPrison.corner2().world()))
            throw new IllegalArgumentException("Region corners must be in the same world");
        if (updatedPrison.hasRegion() && updatedPrison.cells().values().stream()
                .map(PrisonLocation::resolve).anyMatch(cell -> !updatedPrison.contains(cell)))
            throw new IllegalArgumentException("All cells must remain inside the prison region");
        if (updatedPrison.hasRegion() && updatedPrison.spawn() != null
                && !updatedPrison.contains(updatedPrison.spawn().resolve()))
            throw new IllegalArgumentException("Fallback spawn must remain inside the prison region");
        return replacePrison(updatedPrison);
    }

    PrisonRecord addCell(Player actor, String prisonName, String cellName) {
        PrisonRecord prison = requireManageable(actor, prisonName);
        PrisonLocation location = PrisonLocation.capture(actor.getLocation());
        if (prison.hasRegion() && !prison.contains(actor.getLocation()))
            throw new IllegalArgumentException("Cell must be inside the prison region");
        if (prison.cells().values().stream().anyMatch(existing -> sameBlock(existing, location)))
            throw new IllegalArgumentException("A cell already uses that location");
        return replacePrison(prison.addCell(cellName, location));
    }

    PrisonRecord removeCell(Player actor, String prisonName, String cellName) {
        PrisonRecord prison = requireManageable(actor, prisonName);
        if (prisoners(prison.name()).stream().anyMatch(inmate -> inmate.cell().equals(PrisonRecord.normalize(cellName))))
            throw new IllegalStateException("Cell is occupied");
        return replacePrison(prison.removeCell(cellName));
    }

    void deletePrison(Player actor, String prisonName) {
        PrisonRecord prison = requireManageable(actor, prisonName);
        if (!prisoners(prison.name()).isEmpty()) throw new IllegalStateException("Prison has inmates");
        Map<String, PrisonRecord> updated = new HashMap<>(prisons);
        updated.remove(prison.name());
        commit(updated, prisoners);
    }

    PrisonerRecord jail(Player actor, Player target, String prisonName, long durationMillis) {
        if (!RolePermissions.canModerate(actor)) throw new IllegalStateException("Moderator permission required");
        return jail(actor, target.getUniqueId(), target.getName(), prisonName, durationMillis);
    }

    PrisonerRecord jail(Player actor, UUID targetId, String targetName, String prisonName, long durationMillis) {
        if (!RolePermissions.canModerate(actor)) throw new IllegalStateException("Moderator permission required");
        if (targetId.equals(actor.getUniqueId())) throw new IllegalArgumentException("Cannot jail yourself");
        Player target = Bukkit.getPlayer(targetId);
        return jail(targetId, targetName, prisonName, durationMillis, false,
                target == null ? null : PrisonLocation.capture(target.getLocation()));
    }

    private PrisonerRecord jail(UUID playerId, String playerName, String prisonName, long durationMillis,
                                boolean requireUnban, PrisonLocation returnLocation) {
        if (durationMillis < 0) throw new IllegalArgumentException("Sentence duration cannot be negative");
        if (prisoners.containsKey(playerId)) throw new IllegalStateException("Player is already imprisoned");
        PrisonRecord prison = prisons.get(PrisonRecord.normalize(prisonName));
        if (prison == null) throw new IllegalArgumentException("Prison does not exist");
        PrisonLocation cell = firstFreeCell(prison).orElseThrow(() ->
                new IllegalStateException("Prison has no available cells"));
        String cellName = prison.cells().entrySet().stream().filter(entry -> entry.getValue().equals(cell))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        long now = System.currentTimeMillis();
        long sentenceEnd = 0;
        if (durationMillis > 0) {
            try {
                sentenceEnd = Math.addExact(now, durationMillis);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Sentence duration is too long", exception);
            }
        }
        PrisonerRecord prisoner = new PrisonerRecord(playerId, playerName, prison.name(), cellName, sentenceEnd,
                requireUnban, returnLocation, now);
        Map<UUID, PrisonerRecord> updated = new HashMap<>(prisoners);
        updated.put(playerId, prisoner);
        commit(prisons, updated);
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) teleport(online, cell.resolve());
        return prisoner;
    }

    void release(Player actor, UUID playerId) {
        if (!RolePermissions.canModerate(actor)) throw new IllegalStateException("Moderator permission required");
        release(playerId);
    }

    void release(UUID playerId) {
        PrisonerRecord prisoner = prisoners.get(playerId);
        if (prisoner == null) throw new IllegalArgumentException("Player is not imprisoned");
        Map<UUID, PrisonerRecord> updated = new HashMap<>(prisoners);
        updated.remove(playerId);
        commit(prisons, updated);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            PrisonRecord prison = prisons.get(prisoner.prison());
            Location destination = prison == null || prison.exit() == null ? null : prison.exit().resolve();
            if (destination == null && prisoner.returnLocation() != null)
                destination = prisoner.returnLocation().resolve();
            if (destination != null) teleport(player, destination);
        }
    }

    void tick() {
        syncActiveBans();
        releaseExpired();
        refreshAdmissionSnapshot();
        for (PrisonerRecord prisoner : prisoners.values()) {
            Player player = Bukkit.getPlayer(prisoner.playerId());
            if (player != null) {
                player.sendActionBar(languageService.text(player, Message.PRISON_ACTION_BAR,
                        net.kyori.adventure.text.format.NamedTextColor.RED,
                        PrisonDuration.remaining(prisoner.sentenceEndMillis(), System.currentTimeMillis()),
                        prisoner.prison(), prisoner.cell()));
            }
        }
    }

    void handleJoin(Player player) {
        PrisonerRecord prisoner = prisoners.get(player.getUniqueId());
        if (prisoner == null) return;
        Location destination = location(prisoner).map(PrisonLocation::resolve).orElse(null);
        if (destination == null) {
            plugin.getLogger().severe("Prisoner " + player.getUniqueId() + " has no resolvable cell; login admission denied if banned.");
            return;
        }
        teleport(player, destination);
    }

    Optional<PrisonLocation> location(PrisonerRecord prisoner) {
        PrisonRecord prison = prisons.get(prisoner.prison());
        if (prison == null) return Optional.empty();
        PrisonLocation assigned = prison.cells().get(prisoner.cell());
        if (assigned != null && assigned.resolve() != null) return Optional.of(assigned);
        return Optional.ofNullable(prison.spawn());
    }

    boolean isTeleporting(UUID playerId) {
        return teleporting.contains(playerId);
    }

    boolean canMoveTo(Player player, Location destination) {
        PrisonerRecord prisoner = prisoners.get(player.getUniqueId());
        if (prisoner == null) return true;
        if (destination == null || destination.getWorld() == null) return false;
        PrisonRecord prison = prisons.get(prisoner.prison());
        if (prison == null) return false;
        if (prison.hasRegion()) return prison.contains(destination);
        Location cell = location(prisoner).map(PrisonLocation::resolve).orElse(null);
        return cell != null && cell.getWorld().equals(destination.getWorld())
                && cell.distanceSquared(destination) <= settings.prisonerRadius() * settings.prisonerRadius();
    }

    boolean protect(Location location) {
        if (!settings.protectRegion() || location == null || location.getWorld() == null) return false;
        return prisons.values().stream().anyMatch(prison -> prison.contains(location));
    }

    boolean mayBuild(Player player, Location location) {
        if (!protect(location)) return true;
        if (RolePermissions.canModerate(player)) return true;
        return prisons.values().stream().filter(prison -> prison.contains(location))
                .noneMatch(prison -> canManage(player, prison));
    }

    boolean allowedCommand(String command) {
        String label = command.strip().replaceFirst("^/", "").split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) label = label.substring(namespace + 1);
        return settings.allowedCommands().contains(label);
    }

    private void syncActiveBans() {
        if (!settings.autoJailBans()) return;
        for (BanRecord ban : bans.activeRecords()) {
            UUID id = ban.playerUuid();
            if (id == null || prisoners.containsKey(id)) continue;
            PrisonRecord target = prisons.values().stream()
                    .filter(prison -> (settings.allowPlayerPrisons() || prison.owner() == null)
                            && firstFreeCell(prison).isPresent())
                    .sorted(Comparator.comparing(PrisonRecord::name)).findFirst().orElse(null);
            if (target == null) continue;
            long duration = ban.expiresAt() == null ? settings.defaultBanSentenceMillis()
                    : Math.max(1, ban.expiresAt().toEpochMilli() - System.currentTimeMillis());
            try {
                jail(id, ban.playerName(), target.name(), duration, true, null);
            } catch (IllegalStateException | IllegalArgumentException exception) {
                plugin.getLogger().warning("Could not jail banned player " + ban.playerName() + ": " + exception.getMessage());
            }
        }
    }

    private void releaseExpired() {
        long now = System.currentTimeMillis();
        for (PrisonerRecord prisoner : List.copyOf(prisoners.values())) {
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
            for (PrisonerRecord prisoner : List.copyOf(prisoners.values())) {
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

    private PrisonRecord requireManageable(Player actor, String name) {
        PrisonRecord prison = prisons.get(PrisonRecord.normalize(name));
        if (prison == null) throw new IllegalArgumentException("Prison does not exist");
        if (!canManage(actor, prison)) throw new IllegalStateException("You do not manage that prison");
        return prison;
    }

    private PrisonRecord replacePrison(PrisonRecord updatedPrison) {
        Map<String, PrisonRecord> updated = new HashMap<>(prisons);
        updated.put(updatedPrison.name(), updatedPrison);
        commit(updated, prisoners);
        return updatedPrison;
    }

    private Optional<PrisonLocation> firstFreeCell(PrisonRecord prison) {
        Set<String> occupied = prisoners(prison.name()).stream().map(PrisonerRecord::cell)
                .collect(java.util.stream.Collectors.toSet());
        return prison.cells().entrySet().stream().filter(entry -> !occupied.contains(entry.getKey()))
                .map(Map.Entry::getValue).filter(location -> location.resolve() != null).findFirst();
    }

    private static boolean sameBlock(PrisonLocation first, PrisonLocation second) {
        return first.world().equals(second.world()) && Math.floor(first.x()) == Math.floor(second.x())
                && Math.floor(first.y()) == Math.floor(second.y()) && Math.floor(first.z()) == Math.floor(second.z());
    }

    private void refreshAdmissionSnapshot() {
        if (!settings.allowBannedPrisonerLogin()) {
            jailedLoginIds = Set.of();
            return;
        }
        Set<UUID> admitted = new HashSet<>();
        prisoners.values().forEach(prisoner -> location(prisoner).filter(value -> value.resolve() != null)
                .ifPresent(_ -> admitted.add(prisoner.playerId())));
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

    private void commit(Map<String, PrisonRecord> updatedPrisons, Map<UUID, PrisonerRecord> updatedPrisoners) {
        PrisonData updated = new PrisonData(updatedPrisons, updatedPrisoners);
        try {
            store.save(updated);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save prison data", exception);
        }
        prisons = updated.prisons();
        prisoners = updated.prisoners();
        refreshAdmissionSnapshot();
    }
}
