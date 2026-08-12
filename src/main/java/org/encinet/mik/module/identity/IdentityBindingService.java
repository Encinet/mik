package org.encinet.mik.module.identity;

import org.encinet.mik.module.identity.IdentityBindingRepository.PendingIdentityLink;
import org.encinet.mik.module.identity.IdentityBindingRepository.RepositoryLinkResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class IdentityBindingService implements ExternalIdentityLinker, AutoCloseable {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final int CODE_LENGTH = 10;
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private static final char[] CODE_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int MAX_ATTEMPT_STATES = 4_096;

    private final IdentityBindingRepository repository;
    private final Clock clock;
    private final SecureRandom random;
    private final Map<ExternalIdentityKey, AttemptState> attempts = new HashMap<>();

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
        } catch (SQLException error) {
            throw storageFailure("Could not open identity database", error);
        }
    }

    synchronized CodeIssue issueCode(UUID playerId, String playerName, String platform) {
        String normalizedPlatform = ExternalIdentityKey.normalizePlatform(platform);
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(CODE_TTL);
        String normalizedCode = generateCode();
        PendingIdentityLink pending = new PendingIdentityLink(
                hashCode(normalizedCode), playerId, playerName, normalizedPlatform,
                createdAt, expiresAt);
        try {
            repository.replaceCode(pending);
        } catch (SQLException error) {
            throw storageFailure("Could not save an identity link code", error);
        }
        return new CodeIssue(formatCode(normalizedCode), expiresAt);
    }

    synchronized boolean cancelCode(UUID playerId, String platform) {
        try {
            return repository.cancelCode(playerId,
                    ExternalIdentityKey.normalizePlatform(platform));
        } catch (SQLException error) {
            throw storageFailure("Could not cancel an identity link code", error);
        }
    }

    @Override
    public synchronized IdentityLinkResult redeem(String rawCode, ExternalIdentity identity) {
        Instant now = clock.instant();
        Instant retryAt = retryAt(identity.key(), now);
        if (retryAt != null) {
            return new IdentityLinkResult(
                    IdentityLinkResult.Status.RATE_LIMITED, null, retryAt);
        }

        String normalizedCode = normalizeCode(rawCode);
        if (normalizedCode == null) {
            return failedAttempt(identity.key(), now);
        }

        final RepositoryLinkResult result;
        try {
            result = repository.redeem(hashCode(normalizedCode), identity, now);
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
    public synchronized Optional<IdentityBinding> find(ExternalIdentityKey key) {
        try {
            return repository.findByExternal(key);
        } catch (SQLException error) {
            throw storageFailure("Could not query an external identity", error);
        }
    }

    synchronized List<IdentityBinding> findByPlayer(UUID playerId) {
        try {
            return repository.findByPlayer(playerId);
        } catch (SQLException error) {
            throw storageFailure("Could not query player identity bindings", error);
        }
    }

    synchronized List<IdentityBinding> findByPlayerName(String playerName) {
        try {
            return repository.findByPlayerName(playerName);
        } catch (SQLException error) {
            throw storageFailure("Could not query identity bindings by player name", error);
        }
    }

    @Override
    public synchronized Optional<IdentityBinding> unlink(ExternalIdentityKey key) {
        try {
            Optional<IdentityBinding> removed = repository.unlinkExternal(key);
            attempts.remove(key);
            return removed;
        } catch (SQLException error) {
            throw storageFailure("Could not unlink an external identity", error);
        }
    }

    synchronized List<IdentityBinding> unlinkPlayerPlatform(
            UUID playerId,
            String platform
    ) {
        try {
            List<IdentityBinding> removed = repository.unlinkPlayerPlatform(
                    Objects.requireNonNull(playerId, "playerId"),
                    ExternalIdentityKey.normalizePlatform(platform));
            removed.forEach(binding -> attempts.remove(binding.externalKey()));
            return removed;
        } catch (SQLException error) {
            throw storageFailure("Could not unlink player identities for a platform", error);
        }
    }

    synchronized void updatePlayerName(UUID playerId, String playerName) {
        try {
            repository.updatePlayerName(playerId, playerName);
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

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    static String normalizeCode(String rawCode) {
        if (rawCode == null) {
            return null;
        }
        StringBuilder normalized = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < rawCode.length(); index++) {
            char character = Character.toUpperCase(rawCode.charAt(index));
            if (character == '-' || Character.isWhitespace(character)) {
                continue;
            }
            if (!isCodeCharacter(character) || normalized.length() >= CODE_LENGTH) {
                return null;
            }
            normalized.append(character);
        }
        return normalized.length() == CODE_LENGTH ? normalized.toString() : null;
    }

    private static boolean isCodeCharacter(char character) {
        for (char accepted : CODE_ALPHABET) {
            if (accepted == character) {
                return true;
            }
        }
        return false;
    }

    private static String formatCode(String normalizedCode) {
        return normalizedCode.substring(0, 5) + '-' + normalizedCode.substring(5);
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
        try {
            repository.close();
        } catch (SQLException error) {
            throw storageFailure("Could not close identity database", error);
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
