package org.encinet.mik.feature.community.identity;

import org.encinet.mik.feature.community.identity.IdentityBindingRepository.PendingIdentityLink;
import org.encinet.mik.feature.community.identity.IdentityBindingRepository.RepositoryLinkResult;
import org.encinet.mik.feature.community.identity.IdentityBindingRepository.RepositoryLinkStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class IdentityBindingService implements ExternalIdentityLinker, AutoCloseable {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 32;
    private static final int MAX_ATTEMPT_STATES = 4_096;
    private final IdentityBindingRepository repository;
    private final Clock clock;
    private final SecureRandom random;
    private final Map<ExternalIdentityKey, AttemptState> attempts = new HashMap<>();
    private volatile BindingSnapshot snapshot = BindingSnapshot.empty();

    IdentityBindingService(IdentityBindingRepository repository) {
        this(repository, Clock.systemUTC(), new SecureRandom());
    }

    IdentityBindingService(
            IdentityBindingRepository repository,
            Clock clock,
            SecureRandom random
    ) {
        this.repository = repository;
        this.clock = clock;
        this.random = random;
    }

    void open() {
        try {
            repository.open();
            repository.deleteExpiredCodes(clock.instant());
            reloadBindings();
        } catch (SQLException error) {
            throw storageFailure("Could not open identity database", error);
        }
    }

    synchronized CodeIssue issueCode(UUID playerId, String playerName, String platform) {
        String normalizedPlatform = ExternalIdentityKey.normalizePlatform(platform);
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(CODE_TTL);
        try {
            for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
                String code = generateCode();
                PendingIdentityLink pending = new PendingIdentityLink(
                        hashCode(code), playerId, playerName, normalizedPlatform, createdAt, expiresAt);
                if (repository.tryReplaceCode(pending)) return new CodeIssue(code, expiresAt);
            }
        } catch (SQLException error) {
            throw storageFailure("Could not save an identity link code", error);
        }
        throw new IdentityBindingException("Could not allocate an unused identity link code", null);
    }

    synchronized boolean cancelCode(UUID playerId, String platform) {
        try {
            return repository.cancelCode(playerId,
                    ExternalIdentityKey.normalizePlatform(platform));
        } catch (SQLException error) {
            throw storageFailure("Could not cancel an identity link code", error);
        }
    }

    synchronized boolean codeIsPending(UUID playerId, IdentityLinkCode code) {
        try { return repository.codeIsPending(playerId, code.platform(), hashCode(code.code()), clock.instant()); }
        catch (SQLException error) { throw storageFailure("Could not check an identity link code", error); }
    }

    @Override
    public synchronized IdentityLinkResult redeem(String rawCode, ExternalIdentity identity) {
        Instant now = clock.instant();
        Instant retryAt = retryAt(identity.key(), now);
        if (retryAt != null) {
            return new IdentityLinkResult(
                    IdentityLinkResult.Status.RATE_LIMITED, null, retryAt);
        }

        String normalizedCode = IdentityLinkCode.normalizeCode(rawCode);
        if (normalizedCode == null) {
            return failedAttempt(identity.key(), now);
        }

        final RepositoryLinkResult result;
        try {
            result = repository.redeem(hashCode(normalizedCode), identity, now);
            if (result.status() == RepositoryLinkStatus.LINKED
                    || result.status() == RepositoryLinkStatus.ALREADY_LINKED) {
                reloadBindings();
            }
        } catch (SQLException error) {
            throw storageFailure("Could not redeem an identity link code", error);
        }

        return switch (result.status()) {
            case LINKED -> success(IdentityLinkResult.Status.LINKED, result.binding(), identity.key());
            case ALREADY_LINKED -> success(
                    IdentityLinkResult.Status.ALREADY_LINKED, result.binding(), identity.key());
            case NOT_FOUND, EXPIRED -> failedAttempt(identity.key(), now);
            case PLATFORM_MISMATCH -> new IdentityLinkResult(
                    IdentityLinkResult.Status.PLATFORM_MISMATCH, null, null);
            case EXTERNAL_IDENTITY_IN_USE -> new IdentityLinkResult(
                    IdentityLinkResult.Status.EXTERNAL_IDENTITY_IN_USE, null, null);
            case PLAYER_SCOPE_IN_USE -> new IdentityLinkResult(
                    IdentityLinkResult.Status.PLAYER_SCOPE_IN_USE, null, null);
        };
    }

    @Override
    public Optional<IdentityBinding> find(ExternalIdentityKey key) {
        return Optional.ofNullable(snapshot.byExternal().get(
                Objects.requireNonNull(key, "key")));
    }

    List<IdentityBinding> bindings() {
        return snapshot.bindings();
    }

    List<IdentityBinding> findByPlayer(UUID playerId) {
        return snapshot.byPlayer().getOrDefault(
                Objects.requireNonNull(playerId, "playerId"), List.of());
    }

    List<IdentityBinding> findByPlayerName(String playerName) {
        String key = Objects.requireNonNull(playerName, "playerName")
                .toLowerCase(java.util.Locale.ROOT);
        return snapshot.byName().getOrDefault(key, List.of());
    }

    @Override
    public synchronized Optional<IdentityBinding> unlink(ExternalIdentityKey key) {
        try {
            Optional<IdentityBinding> removed = repository.unlinkExternal(key);
            attempts.remove(key);
            if (removed.isPresent()) {
                reloadBindings();
            }
            return removed;
        } catch (SQLException error) {
            throw storageFailure("Could not unlink an external identity", error);
        }
    }

    synchronized Optional<IdentityBinding> unlinkOwned(UUID playerId, UUID bindingId) {
        try {
            return removed(repository.unlinkOwned(
                    Objects.requireNonNull(playerId, "playerId"),
                    Objects.requireNonNull(bindingId, "bindingId")));
        } catch (SQLException error) {
            throw storageFailure("Could not unlink the selected identity", error);
        }
    }

    synchronized Optional<IdentityBinding> unlinkById(UUID bindingId) {
        try {
            return removed(repository.unlinkById(Objects.requireNonNull(bindingId, "bindingId")));
        } catch (SQLException error) {
            throw storageFailure("Could not unlink the selected identity", error);
        }
    }

    private Optional<IdentityBinding> removed(Optional<IdentityBinding> removed) throws SQLException {
        if (removed.isPresent()) {
            attempts.remove(removed.get().externalKey());
            reloadBindings();
        }
        return removed;
    }

    synchronized void updatePlayerName(UUID playerId, String playerName) {
        try {
            repository.updatePlayerName(playerId, playerName);
            reloadBindings();
        } catch (SQLException error) {
            throw storageFailure("Could not update a bound player name", error);
        }
    }

    private IdentityLinkResult failedAttempt(ExternalIdentityKey key, Instant now) {
        AttemptState current = attempts.get(key);
        int failures = current == null || now.isAfter(current.windowStartedAt().plus(FAILURE_WINDOW))
                ? 1
                : current.failures() + 1;
        Instant windowStarted = current == null
                || now.isAfter(current.windowStartedAt().plus(FAILURE_WINDOW))
                ? now
                : current.windowStartedAt();
        Instant lockedUntil = failures >= MAX_FAILED_ATTEMPTS
                ? now.plus(LOCKOUT_DURATION)
                : null;
        attempts.put(key, new AttemptState(windowStarted, failures, lockedUntil, now));
        pruneAttempts(now);
        if (lockedUntil != null) {
            return new IdentityLinkResult(
                    IdentityLinkResult.Status.RATE_LIMITED, null, lockedUntil);
        }
        return new IdentityLinkResult(
                IdentityLinkResult.Status.INVALID_OR_EXPIRED_CODE, null, null);
    }

    private Instant retryAt(ExternalIdentityKey key, Instant now) {
        AttemptState state = attempts.get(key);
        if (state == null || state.lockedUntil() == null) {
            return null;
        }
        if (!now.isBefore(state.lockedUntil())) {
            attempts.remove(key);
            return null;
        }
        return state.lockedUntil();
    }

    private void pruneAttempts(Instant now) {
        if (attempts.size() <= MAX_ATTEMPT_STATES) {
            return;
        }
        Instant staleBefore = now.minus(LOCKOUT_DURATION).minus(FAILURE_WINDOW);
        attempts.entrySet().removeIf(entry -> entry.getValue().lastAttemptAt().isBefore(staleBefore));
    }

    private IdentityLinkResult success(
            IdentityLinkResult.Status status,
            IdentityBinding binding,
            ExternalIdentityKey key
    ) {
        attempts.remove(key);
        return new IdentityLinkResult(status, binding, null);
    }

    private void reloadBindings() throws SQLException {
        List<IdentityBinding> loaded = repository.findAll();
        Map<ExternalIdentityKey, IdentityBinding> external = new LinkedHashMap<>();
        Map<UUID, List<IdentityBinding>> byPlayer = new LinkedHashMap<>();
        Map<String, List<IdentityBinding>> byName = new LinkedHashMap<>();
        for (IdentityBinding binding : loaded) {
            external.put(binding.externalKey(), binding);
            byPlayer.computeIfAbsent(binding.playerId(), _ ->
                    new java.util.ArrayList<>()).add(binding);
            byName.computeIfAbsent(binding.playerName().toLowerCase(
                    java.util.Locale.ROOT), _ ->
                    new java.util.ArrayList<>()).add(binding);
        }
        byPlayer.replaceAll((_, values) -> List.copyOf(values));
        byName.replaceAll((_, values) -> List.copyOf(values));
        snapshot = new BindingSnapshot(List.copyOf(loaded), Map.copyOf(external),
                Map.copyOf(byPlayer), Map.copyOf(byName));
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(IdentityLinkCode.CODE_LENGTH);
        for (int index = 0; index < IdentityLinkCode.CODE_LENGTH; index++) {
            code.append((char) ('0' + random.nextInt(10)));
        }
        return code.toString();
    }

    private static String hashCode(String normalizedCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(normalizedCode.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static IdentityBindingException storageFailure(String message, SQLException error) {
        return new IdentityBindingException(message, error);
    }

    @Override
    public synchronized void close() {
        attempts.clear();
        snapshot = BindingSnapshot.empty();
        try {
            repository.close();
        } catch (SQLException error) {
            throw storageFailure("Could not close identity database", error);
        }
    }

    private record BindingSnapshot(
            List<IdentityBinding> bindings,
            Map<ExternalIdentityKey, IdentityBinding> byExternal,
            Map<UUID, List<IdentityBinding>> byPlayer,
            Map<String, List<IdentityBinding>> byName
    ) {
        static BindingSnapshot empty() {
            return new BindingSnapshot(List.of(), Map.of(), Map.of(), Map.of());
        }
    }

    record CodeIssue(String code, Instant expiresAt) {
    }

    private record AttemptState(
            Instant windowStartedAt,
            int failures,
            Instant lockedUntil,
            Instant lastAttemptAt
    ) {
    }

}
