package org.encinet.mik.module.identity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Application runtime for identity storage and installed platform leases. */
final class IdentityBindingRuntime implements IdentityBindingManager {

    private final IdentityBindingService service;
    private final IdentityPlatformRegistry platforms;
    private final IdentityBindingNotifier notifier;
    private volatile boolean available;

    IdentityBindingRuntime(
            IdentityBindingService service,
            IdentityPlatformRegistry platforms,
            IdentityBindingNotifier notifier
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.platforms = Objects.requireNonNull(platforms, "platforms");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    void open() {
        service.open();
        available = true;
    }

    void close() {
        available = false;
        platforms.clear();
        service.close();
    }

    boolean isAvailable() {
        return available;
    }

    int platformCount() {
        return platforms.size();
    }

    List<IdentityPlatform> platforms() {
        return platforms.platforms();
    }

    Optional<IdentityPlatform> platform(String id) {
        try {
            return platforms.find(id);
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    List<String> platformSuggestions(String remaining) {
        return platforms.suggestions(remaining);
    }

    Optional<IdentityLinkCode> issueCode(UUID playerId, String playerName, String platformId) {
        ensureAvailable();
        Optional<IdentityPlatform> installed = platform(platformId);
        if (installed.isEmpty()) {
            return Optional.empty();
        }
        IdentityBindingService.CodeIssue issue = service.issueCode(
                Objects.requireNonNull(playerId, "playerId"),
                IdentityBinding.requirePlayerName(playerName), installed.get().id());
        return Optional.of(new IdentityLinkCode(
                installed.get().id(), issue.code(), issue.expiresAt()));
    }

    boolean cancelCode(UUID playerId, String platformId) {
        ensureAvailable();
        return service.cancelCode(playerId, platformId);
    }

    List<IdentityBinding> unlinkPlayerPlatform(UUID playerId, String platformId) {
        ensureAvailable();
        List<IdentityBinding> removed = service.unlinkPlayerPlatform(playerId, platformId);
        removed.forEach(notifier::unlinked);
        return removed;
    }

    List<IdentityBinding> findByPlayerName(String playerName) {
        ensureAvailable();
        return service.findByPlayerName(playerName);
    }

    void updatePlayerName(UUID playerId, String playerName) {
        ensureAvailable();
        service.updatePlayerName(playerId, playerName);
    }

    @Override
    public IdentityPlatformRegistration registerPlatform(IdentityPlatform platform) {
        return platforms.register(platform);
    }

    @Override
    public IdentityLinkResult redeem(String code, ExternalIdentity identity) {
        ensureAvailable();
        IdentityLinkResult result = service.redeem(code, identity);
        if (result.status() == IdentityLinkResult.Status.LINKED && result.binding() != null) {
            notifier.linked(result.binding());
        }
        return result;
    }

    @Override
    public Optional<IdentityBinding> find(ExternalIdentityKey key) {
        ensureAvailable();
        return service.find(key);
    }

    @Override
    public List<IdentityBinding> bindings() {
        ensureAvailable();
        return service.bindings();
    }

    @Override
    public List<IdentityBinding> findByPlayer(UUID playerId) {
        ensureAvailable();
        return service.findByPlayer(playerId);
    }

    @Override
    public Optional<IdentityBinding> unlink(ExternalIdentityKey key) {
        ensureAvailable();
        Optional<IdentityBinding> removed = service.unlink(key);
        removed.ifPresent(notifier::unlinked);
        return removed;
    }

    private void ensureAvailable() {
        if (!available) {
            throw new IdentityBindingException(
                    "Identity binding service is unavailable",
                    new IllegalStateException("disabled"));
        }
    }
}
