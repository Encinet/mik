package org.encinet.mik.module.ban;

import java.io.File;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BanService implements BanManager, AutoCloseable {

    private final BanRepository repository;
    private final BanMirror mirror;
    private final Logger logger;
    private final Clock clock;
    private volatile Snapshot snapshot = Snapshot.empty();

    BanService(File databaseFile, BanMirror mirror, Logger logger) {
        this(new BanRepository(databaseFile), mirror, logger, Clock.systemUTC());
    }

    BanService(BanRepository repository, BanMirror mirror, Logger logger, Clock clock) {
        this.repository = repository;
        this.mirror = mirror;
        this.logger = logger;
        this.clock = clock;
    }

    public synchronized void open() throws BanServiceException {
        try {
            repository.open();
            reload();
        } catch (SQLException | RuntimeException e) {
            throw new BanServiceException("Failed to open ban database", e);
        }
    }

    public Optional<BanRecord> active(UUID playerUuid, String playerName) {
        BanRecord record = snapshot.findActive(playerUuid, normalizeName(playerName));
        return record != null && record.isActive(clock.instant()) ? Optional.of(record) : Optional.empty();
    }

    public List<BanRecord> activeRecords() {
        Instant now = clock.instant();
        return snapshot.records().stream().filter(record -> record.isActive(now)).toList();
    }

    public List<BanRecord> allRecords() {
        return snapshot.records();
    }

    synchronized List<PaperBanSnapshot> loadPaperState() throws BanServiceException {
        try {
            return repository.loadPaperState();
        } catch (SQLException e) {
            throw new BanServiceException("Failed to load Paper ban synchronization state", e);
        }
    }

    synchronized boolean hasPaperState() throws BanServiceException {
        try {
            return repository.hasPaperState();
        } catch (SQLException e) {
            throw new BanServiceException("Failed to read Paper ban synchronization metadata", e);
        }
    }

    synchronized void savePaperState(List<PaperBanSnapshot> paperState) throws BanServiceException {
        try {
            repository.replacePaperState(paperState);
        } catch (SQLException e) {
            throw new BanServiceException("Failed to save Paper ban synchronization state", e);
        }
    }

    public List<BanRecord> history(UUID playerUuid, String playerName) {
        String normalizedName = normalizeName(playerName);
        return snapshot.records().stream()
                .filter(record -> matches(record, playerUuid, normalizedName))
                .toList();
    }

    public synchronized BanRecord ban(
            UUID playerUuid,
            String playerName,
            String reason,
            String source,
            Instant expiresAt,
            BanRecord.Origin origin
    ) throws BanServiceException {
        requireWriteThread();
        Instant now = clock.instant();
        String normalizedName = requirePlayerName(playerName);
        if (active(playerUuid, playerName).isPresent()) {
            throw new BanServiceException(playerName + " is already banned");
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new BanServiceException("Ban expiration must be in the future");
        }

        String validReason = requireText(reason, "reason");
        String validSource = requireText(source, "source");
        if (origin == null) {
            throw new BanServiceException("origin must not be null");
        }
        BanDraft draft = new BanDraft(playerUuid, playerName.trim(), normalizedName,
                validReason, validSource, now, expiresAt, origin);
        try {
            BanRecord record = repository.insert(draft);
            reload();
            mirrorUpsert(record);
            log("CREATE", record, source, "expires=" + displayInstant(expiresAt)
                    + " reason=\"" + logText(reason) + "\"");
            return record;
        } catch (SQLException | RuntimeException e) {
            throw new BanServiceException("Failed to ban " + playerName, e);
        }
    }

    synchronized BanRecord ban(
            UUID playerUuid,
            String playerName,
            BanSeverity severity,
            String reason,
            String operator
    )
            throws BanServiceException {
        if (severity == null) {
            throw new BanServiceException("severity must not be null");
        }
        String validReason = requireText(reason, "reason");
        return ban(playerUuid, playerName, severity.storedReason(validReason), operator,
                severity.expiresAt(clock.instant()), BanRecord.Origin.MIK);
    }

    public synchronized BanRecord editReason(UUID playerUuid, String playerName, String reason, String operator)
            throws BanServiceException {
        requireWriteThread();
        BanRecord current = requireActive(playerUuid, playerName);
        String newReason = requireText(reason, "reason");
        BanSeverity severity = BanSeverity.fromStoredReason(current.reason()).orElse(null);
        String storedReason = severity == null ? newReason : severity.storedReason(newReason);
        String validOperator = requireText(operator, "operator");
        try {
            BanRecord updated = repository.updateReason(current.id(), storedReason, clock.instant());
            reload();
            mirrorUpsert(updated);
            log("EDIT_REASON", updated, validOperator, "old=\"" + logText(current.reason())
                    + "\" new=\"" + logText(newReason) + "\"");
            return updated;
        } catch (SQLException | RuntimeException e) {
            throw new BanServiceException("Failed to update ban reason for " + current.playerName(), e);
        }
    }

    public synchronized BanRecord editExpiration(
            UUID playerUuid,
            String playerName,
            Instant expiresAt,
            String operator
    ) throws BanServiceException {
        requireWriteThread();
        BanRecord current = requireActive(playerUuid, playerName);
        String validOperator = requireText(operator, "operator");
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            throw new BanServiceException("Ban expiration must be in the future");
        }
        try {
            BanRecord updated = repository.updateExpiration(current.id(), expiresAt, clock.instant());
            reload();
            mirrorUpsert(updated);
            log("EDIT_EXPIRY", updated, validOperator, "old=" + displayInstant(current.expiresAt())
                    + " new=" + displayInstant(expiresAt));
            return updated;
        } catch (SQLException | RuntimeException e) {
            throw new BanServiceException("Failed to update ban expiration for " + current.playerName(), e);
        }
    }

    synchronized BanRecord editSeverity(
            UUID playerUuid,
            String playerName,
            BanSeverity severity,
            String operator
    ) throws BanServiceException {
        requireWriteThread();
        BanRecord current = requireActive(playerUuid, playerName);
        if (severity == null) {
            throw new BanServiceException("severity must not be null");
        }
        String validOperator = requireText(operator, "operator");
        Instant now = clock.instant();
        Instant expiresAt = severity.expiresAt(now);
        try {
            String reason = BanSeverity.userReason(current.reason());
            String storedReason = reason == null || reason.isBlank()
                    ? severity.storedReason()
                    : severity.storedReason(reason);
            BanRecord updated = repository.updateSeverity(current.id(), storedReason, expiresAt, now);
            reload();
            mirrorUpsert(updated);
            log("EDIT_SEVERITY", updated, validOperator,
                    "oldReason=\"" + logText(current.reason()) + "\""
                            + " oldExpires=" + displayInstant(current.expiresAt())
                            + " newSeverity=" + severity.id()
                            + " newExpires=" + displayInstant(expiresAt));
            return updated;
        } catch (SQLException | RuntimeException e) {
            throw new BanServiceException("Failed to update ban severity for " + current.playerName(), e);
        }
    }

    public synchronized BanRecord pardon(UUID playerUuid, String playerName, String operator, String reason)
            throws BanServiceException {
        requireWriteThread();
        BanRecord current = requireActive(playerUuid, playerName);
        String validOperator = requireText(operator, "operator");
        String validReason = requireText(reason, "pardon reason");
        try {
            BanRecord revoked = repository.revoke(current.id(), clock.instant());
            reload();
            mirrorPardon(current);
            log("PARDON", revoked, validOperator, "reason=\"" + logText(validReason) + "\"");
            return revoked;
        } catch (SQLException | RuntimeException e) {
            throw new BanServiceException("Failed to pardon " + current.playerName(), e);
        }
    }

    synchronized BanRecord importPaperBan(PaperBanSnapshot paper, boolean existingPaperEntryChanged)
            throws BanServiceException {
        requireWriteThread();
        Optional<BanRecord> existing = active(paper.playerUuid(), paper.playerName());
        if (existing.isPresent()) {
            return applyPaperUpdate(existing.get(), paper, existingPaperEntryChanged);
        }
        BanDraft draft = paperDraft(paper, null);
        try {
            BanRecord imported = repository.insert(draft);
            reload();
            log("IMPORT_PAPER", imported, paper.source(), "expires=" + displayInstant(paper.expiresAt())
                    + " reason=\"" + logText(paper.reason()) + "\"");
            return imported;
        } catch (SQLException | RuntimeException e) {
            throw new BanServiceException("Failed to import Paper ban for " + paper.playerName(), e);
        }
    }

    synchronized BanRecord applyPaperUpdate(
            BanRecord current,
            PaperBanSnapshot paper,
            boolean existingPaperEntryChanged
    )
            throws BanServiceException {
        requireWriteThread();
        boolean enrichIdentity = requiresIdentityEnrichment(current, paper);
        if (existingPaperEntryChanged && !enrichIdentity && isReplacement(current, paper)) {
            try {
                BanRecord replacement = repository.replace(
                        current.id(), paperDraft(paper, current.playerUuid()), paper.createdAt());
                reload();
                log("REPLACE_FROM_PAPER", replacement, paper.source(), "previousId=" + current.id());
                return replacement;
            } catch (SQLException | RuntimeException e) {
                throw new BanServiceException("Failed to replace Paper ban for " + paper.playerName(), e);
            }
        }
        if (paper.sameContent(current) && !enrichIdentity) {
            return current;
        }
        try {
            BanRecord updated = repository.updateFromPaper(
                    current.id(), paperDraft(paper, current.playerUuid()), clock.instant());
            reload();
            log("UPDATE_FROM_PAPER", updated, paper.source(), "oldReason=\"" + logText(current.reason())
                    + "\" oldExpires=" + displayInstant(current.expiresAt()));
            return updated;
        } catch (SQLException | RuntimeException e) {
            throw new BanServiceException("Failed to apply Paper ban update for " + paper.playerName(), e);
        }
    }

    synchronized void applyPaperPardon(BanRecord current) throws BanServiceException {
        requireWriteThread();
        if (!current.isActive(clock.instant())) {
            return;
        }
        try {
            BanRecord revoked = repository.revoke(current.id(), clock.instant());
            reload();
            log("PARDON_FROM_PAPER", revoked, "external", "source=Paper profile ban list");
        } catch (SQLException | RuntimeException e) {
            throw new BanServiceException("Failed to import Paper pardon for " + current.playerName(), e);
        }
    }

    void restorePaperBan(BanRecord record) {
        try {
            mirror.upsert(record);
            log("RESTORE_PAPER_MIRROR", record, "Mik", "source=SQLite");
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[BanManager] Failed to restore Paper mirror for " + record.playerName(), e);
        }
    }

    private void mirrorUpsert(BanRecord record) {
        try {
            mirror.upsert(record);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[BanManager] Ban record " + record.id()
                    + " was saved but its Paper mirror will be retried", e);
        }
    }

    private void mirrorPardon(BanRecord record) {
        try {
            mirror.pardon(record);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[BanManager] Ban record " + record.id()
                    + " was pardoned but its Paper mirror cleanup will be retried", e);
        }
    }

    private void requireWriteThread() throws BanServiceException {
        try {
            mirror.requireWriteThread();
        } catch (RuntimeException e) {
            throw new BanServiceException("Ban mutations must run on the server thread", e);
        }
    }

    private BanRecord requireActive(UUID playerUuid, String playerName) throws BanServiceException {
        return active(playerUuid, playerName)
                .orElseThrow(() -> new BanServiceException(playerName + " is not actively banned"));
    }

    private BanDraft paperDraft(PaperBanSnapshot paper, UUID fallbackUuid) {
        UUID playerUuid = paper.playerUuid() == null ? fallbackUuid : paper.playerUuid();
        return new BanDraft(playerUuid, paper.playerName(), paper.normalizedName(),
                paper.reason(), paper.source(), paper.createdAt(), paper.expiresAt(), BanRecord.Origin.PAPER_IMPORT);
    }

    private boolean isReplacement(BanRecord current, PaperBanSnapshot paper) {
        return paper.createdAt().isAfter(current.createdAt());
    }

    private boolean requiresIdentityEnrichment(BanRecord current, PaperBanSnapshot paper) {
        return current.playerUuid() == null && paper.playerUuid() != null;
    }

    private synchronized void reload() throws SQLException {
        snapshot = Snapshot.from(repository.loadAll(), clock.instant());
    }

    private void log(String action, BanRecord record, String operator, String details) {
        logger.info("[BanManager] action=" + action
                + " id=" + record.id()
                + " operator=" + logText(operator)
                + " player=" + logText(record.playerName())
                + " uuid=" + (record.playerUuid() == null ? "unknown" : record.playerUuid())
                + " origin=" + record.origin()
                + " " + details);
    }

    private static String requirePlayerName(String playerName) throws BanServiceException {
        String normalized = normalizeName(playerName);
        if (normalized.isEmpty()) {
            throw new BanServiceException("playerName must not be blank");
        }
        return normalized;
    }

    private static String requireText(String value, String field) throws BanServiceException {
        if (value == null || value.isBlank()) {
            throw new BanServiceException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeName(String playerName) {
        return playerName == null ? "" : playerName.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(BanRecord record, UUID playerUuid, String normalizedName) {
        if (playerUuid != null) {
            if (record.playerUuid() != null) {
                return playerUuid.equals(record.playerUuid());
            }
            return !normalizedName.isEmpty() && normalizedName.equals(record.normalizedName());
        }
        return !normalizedName.isEmpty() && normalizedName.equals(record.normalizedName());
    }

    private static String displayInstant(Instant instant) {
        return instant == null ? "forever" : instant.toString();
    }

    private static String logText(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }

    @Override
    public synchronized void close() throws BanServiceException {
        try {
            repository.close();
            snapshot = Snapshot.empty();
        } catch (SQLException e) {
            throw new BanServiceException("Failed to close ban database", e);
        }
    }

    private record Snapshot(
            List<BanRecord> records,
            Map<UUID, BanRecord> activeByUuid,
            Map<String, BanRecord> activeByName
    ) {

        static Snapshot empty() {
            return new Snapshot(List.of(), Map.of(), Map.of());
        }

        static Snapshot from(List<BanRecord> loaded, Instant now) {
            List<BanRecord> records = new ArrayList<>(loaded);
            records.sort(Comparator.comparing(BanRecord::createdAt).reversed()
                    .thenComparing(Comparator.comparingLong(BanRecord::id).reversed()));
            Map<UUID, BanRecord> byUuid = new HashMap<>();
            Map<String, BanRecord> byName = new HashMap<>();
            for (BanRecord record : records) {
                if (!record.isActive(now)) {
                    continue;
                }
                if (record.playerUuid() != null) {
                    byUuid.putIfAbsent(record.playerUuid(), record);
                }
                byName.putIfAbsent(record.normalizedName(), record);
            }
            return new Snapshot(List.copyOf(records), Map.copyOf(byUuid), Map.copyOf(byName));
        }

        BanRecord findActive(UUID playerUuid, String normalizedName) {
            if (playerUuid != null) {
                BanRecord byUuid = activeByUuid.get(playerUuid);
                if (byUuid != null) {
                    return byUuid;
                }
                BanRecord byName = activeByName.get(normalizedName);
                return byName != null && byName.playerUuid() == null ? byName : null;
            }
            return activeByName.get(normalizedName);
        }
    }
}
