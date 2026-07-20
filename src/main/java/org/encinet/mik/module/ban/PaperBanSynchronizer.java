package org.encinet.mik.module.ban;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.ban.BanListType;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

final class PaperBanSynchronizer implements BanMirror {

    private static final long RECONCILE_INTERVAL_TICKS = 20L;

    private final JavaPlugin plugin;
    private BanService service;
    private Map<String, PaperBanSnapshot> previousPaper = Map.of();
    private boolean paperStateInitialized;
    private BukkitTask reconcileTask;
    private boolean reconciling;

    PaperBanSynchronizer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void bind(BanService service) {
        if (this.service != null) {
            throw new IllegalStateException("PaperBanSynchronizer is already bound");
        }
        this.service = service;
    }

    void start() throws BanServiceException {
        requireMainThread();
        ensureBound();
        paperStateInitialized = service.hasPaperState();
        previousPaper = index(service.loadPaperState());
        reconcile();
        reconcileTask = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcileSafely,
                RECONCILE_INTERVAL_TICKS, RECONCILE_INTERVAL_TICKS);
    }

    void stop() {
        if (reconcileTask != null) {
            reconcileTask.cancel();
            reconcileTask = null;
        }
    }

    void reconcileNow() throws BanServiceException {
        requireMainThread();
        reconcile();
    }

    @Override
    public void requireWriteThread() {
        requireMainThread();
    }

    @Override
    public void upsert(BanRecord record) {
        requireMainThread();
        BanEntry<PlayerProfile> existing = findPaperEntry(record);
        if (existing == null) {
            PlayerProfile profile = profile(record.playerUuid(), record.playerName());
            profileBanList().addBan(profile, record.reason(), record.expiresAt(), record.source());
        } else {
            existing.setReason(record.reason());
            existing.setSource(record.source());
            existing.setExpiration(record.expiresAt() == null ? null : java.util.Date.from(record.expiresAt()));
            existing.save();
        }
        persistCurrentPaperState();
    }

    @Override
    public void pardon(BanRecord record) {
        requireMainThread();
        BanEntry<PlayerProfile> existing = findPaperEntry(record);
        if (existing == null) {
            profileBanList().pardon(profile(record.playerUuid(), record.playerName()));
        } else {
            existing.remove();
        }
        persistCurrentPaperState();
    }

    private void reconcileSafely() {
        try {
            reconcile();
        } catch (BanServiceException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "[BanManager] Paper ban reconciliation failed", e);
        }
    }

    private void reconcile() throws BanServiceException {
        if (reconciling) {
            return;
        }
        reconciling = true;
        try {
            Map<String, PaperBanSnapshot> currentPaper = index(readPaperBans());
            boolean firstSynchronization = !paperStateInitialized;

            for (PaperBanSnapshot paper : currentPaper.values()) {
                PaperBanSnapshot previous = find(previousPaper, paper);
                if (firstSynchronization || previous == null || !paper.samePaperEntry(previous)) {
                    boolean wasActive = service.active(paper.playerUuid(), paper.playerName()).isPresent();
                    BanRecord imported = service.importPaperBan(
                            paper, !firstSynchronization && previous != null);
                    if (!wasActive && imported.isActive(Instant.now())) {
                        enforceOnlineBan(imported);
                    }
                }
            }

            if (!firstSynchronization) {
                for (PaperBanSnapshot previous : previousPaper.values()) {
                    if (find(currentPaper, previous) != null) {
                        continue;
                    }
                    BanRecord active = service.active(previous.playerUuid(), previous.playerName()).orElse(null);
                    if (active != null) {
                        service.applyPaperPardon(active);
                    }
                }
            }

            currentPaper = index(readPaperBans());
            for (PaperBanSnapshot paper : currentPaper.values()) {
                PaperBanSnapshot previous = find(previousPaper, paper);
                if (paper.samePaperEntry(previous)
                        && service.active(paper.playerUuid(), paper.playerName()).isEmpty()) {
                    profileBanList().pardon(profile(paper.playerUuid(), paper.playerName()));
                }
            }

            currentPaper = index(readPaperBans());
            for (BanRecord active : service.activeRecords()) {
                PaperBanSnapshot paper = find(currentPaper, active);
                if (paper == null || !paper.sameContent(active)) {
                    service.restorePaperBan(active);
                }
            }
            persistCurrentPaperState();
        } finally {
            reconciling = false;
        }
    }

    private void persistCurrentPaperState() {
        try {
            List<PaperBanSnapshot> current = readPaperBans();
            Map<String, PaperBanSnapshot> indexed = index(current);
            if (paperStateInitialized && sameState(previousPaper, indexed)) {
                return;
            }
            service.savePaperState(current);
            previousPaper = indexed;
            paperStateInitialized = true;
        } catch (BanServiceException e) {
            throw new ReconciliationRuntimeException(e);
        }
    }

    private List<PaperBanSnapshot> readPaperBans() {
        List<PaperBanSnapshot> bans = new ArrayList<>();
        for (BanEntry<PlayerProfile> entry : profileBanList().<BanEntry<PlayerProfile>>getEntries()) {
            PlayerProfile profile = entry.getBanTarget();
            String name = profile.getName();
            UUID uuid = profile.getId();
            if (name == null || name.isBlank()) {
                if (uuid == null) {
                    plugin.getLogger().warning("[BanManager] Ignored a Paper ban with no UUID or player name");
                    continue;
                }
                name = uuid.toString();
            }
            String reason = entry.getReason();
            String source = entry.getSource();
            bans.add(new PaperBanSnapshot(
                    uuid,
                    name,
                    reason == null ? "" : reason,
                    source == null ? "" : source,
                    entry.getCreated().toInstant(),
                    entry.getExpiration() == null ? null : entry.getExpiration().toInstant()));
        }
        bans.sort(Comparator.comparing(PaperBanSnapshot::createdAt).reversed());
        return List.copyOf(bans);
    }

    private Map<String, PaperBanSnapshot> index(List<PaperBanSnapshot> snapshots) {
        Map<String, PaperBanSnapshot> indexed = new HashMap<>();
        for (PaperBanSnapshot snapshot : snapshots) {
            indexed.put(snapshot.identityKey(), snapshot);
        }
        return Map.copyOf(indexed);
    }

    private PaperBanSnapshot find(Map<String, PaperBanSnapshot> indexed, PaperBanSnapshot target) {
        PaperBanSnapshot exact = indexed.get(target.identityKey());
        if (exact != null) {
            return exact;
        }
        return indexed.values().stream().filter(target::sameIdentity).findFirst().orElse(null);
    }

    private PaperBanSnapshot find(Map<String, PaperBanSnapshot> indexed, BanRecord target) {
        String exactKey = identityKey(target);
        PaperBanSnapshot exact = indexed.get(exactKey);
        if (exact != null) {
            return exact;
        }
        return indexed.values().stream()
                .filter(snapshot -> target.playerUuid() != null && snapshot.playerUuid() != null
                        ? target.playerUuid().equals(snapshot.playerUuid())
                        : target.normalizedName().equals(snapshot.normalizedName()))
                .findFirst()
                .orElse(null);
    }

    private boolean sameState(
            Map<String, PaperBanSnapshot> left,
            Map<String, PaperBanSnapshot> right
    ) {
        if (left.size() != right.size()) {
            return false;
        }
        for (PaperBanSnapshot snapshot : left.values()) {
            PaperBanSnapshot other = find(right, snapshot);
            if (!snapshot.samePaperEntry(other)) {
                return false;
            }
        }
        return true;
    }

    private void enforceOnlineBan(BanRecord record) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = record.playerUuid() == null
                    ? Bukkit.getPlayerExact(record.playerName())
                    : Bukkit.getPlayer(record.playerUuid());
            if (player == null || service.active(player.getUniqueId(), player.getName()).isEmpty()) {
                return;
            }
            String userReason = BanSeverity.userReason(record.reason());
            String reason = userReason == null || userReason.isBlank() ? "Banned" : userReason;
            player.kick(net.kyori.adventure.text.Component.text(reason), PlayerKickEvent.Cause.BANNED);
        });
    }

    private String identityKey(BanRecord record) {
        return record.playerUuid() != null
                ? "uuid:" + record.playerUuid()
                : "name:" + record.normalizedName();
    }

    private PlayerProfile profile(UUID uuid, String name) {
        String profileName = name != null && name.matches("[A-Za-z0-9_]{1,16}") ? name : null;
        return Bukkit.createProfileExact(uuid, profileName);
    }

    private BanEntry<PlayerProfile> findPaperEntry(BanRecord record) {
        for (BanEntry<PlayerProfile> entry : profileBanList().<BanEntry<PlayerProfile>>getEntries()) {
            PlayerProfile target = entry.getBanTarget();
            if (record.playerUuid() != null && target.getId() != null) {
                if (record.playerUuid().equals(target.getId())) {
                    return entry;
                }
                continue;
            }
            if (target.getName() != null && record.playerName().equalsIgnoreCase(target.getName())) {
                return entry;
            }
        }
        return null;
    }

    private BanList<PlayerProfile> profileBanList() {
        return Bukkit.getBanList(BanListType.PROFILE);
    }

    private void ensureBound() {
        if (service == null) {
            throw new IllegalStateException("PaperBanSynchronizer has not been bound to BanService");
        }
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Paper profile ban list must be accessed on the server thread");
        }
    }

    private static final class ReconciliationRuntimeException extends RuntimeException {
        private ReconciliationRuntimeException(BanServiceException cause) {
            super(cause);
        }
    }
}
