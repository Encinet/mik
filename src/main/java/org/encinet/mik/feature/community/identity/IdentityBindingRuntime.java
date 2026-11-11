package org.encinet.mik.feature.community.identity;

import org.encinet.mik.shared.util.ShutdownSequence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Application runtime for identity storage and installed platform leases. */
final class IdentityBindingRuntime implements IdentityBindingManager {

    private final IdentityBindingService service;
    private final IdentityPlatformRegistry platforms;
    private final IdentityBindingNotifier notifier;
    private final IdentityMembershipCoordinator memberships;
    private volatile boolean available;

    IdentityBindingRuntime(
            IdentityBindingService service,
            IdentityPlatformRegistry platforms,
            IdentityBindingNotifier notifier,
            Executor writes
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.platforms = Objects.requireNonNull(platforms, "platforms");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        memberships = new IdentityMembershipCoordinator(service, writes, notifier::unlinked);
    }

    void open() {
        service.open();
        available = true;
    }

    void close() {
        available = false;
        var shutdown = new ShutdownSequence();
        shutdown.attempt("membership subscriptions", memberships::close);
        shutdown.attempt("identity platforms", platforms::clear);
        shutdown.attempt("identity storage", service::close);
        shutdown.finish("identity runtime");
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

    Optional<IdentityBinding> unlinkOwned(UUID playerId, UUID bindingId) {
        ensureAvailable();
        return service.unlinkOwned(playerId, bindingId);
    }

    Optional<IdentityBinding> unlinkById(UUID bindingId) {
        ensureAvailable();
        Optional<IdentityBinding> removed = service.unlinkById(bindingId);
        removed.ifPresent(notifier::unlinked);
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
    public IdentityPlatformRegistration registerPlatform(IdentityPlatform platform, IdentityMemberships provider) {
        ensureAvailable();
        IdentityPlatformRegistration registration = platforms.register(platform);
        final AutoCloseable subscription;
        try { subscription = memberships.watch(Objects.requireNonNull(provider, "memberships")); }
        catch (RuntimeException error) { registration.close(); throw error; }
        return new IdentityPlatformRegistration() {
            @Override public IdentityPlatform platform() { return registration.platform(); }
            @Override public boolean isActive() { return registration.isActive(); }
            @Override public void close() {
                registration.close();
                try { subscription.close(); }
                catch (Exception error) { throw new IllegalStateException("Could not release platform memberships", error); }
            }
        };
    }

    void reconcileMemberships() { memberships.retry(); }

    boolean canJoin(UUID playerId) {
        return available && service.findByPlayer(playerId).stream().anyMatch(binding ->
                memberships.state(binding.externalKey()) == IdentityMemberships.State.JOINED);
    }

    boolean hasBindings(UUID playerId) { return available && !service.findByPlayer(playerId).isEmpty(); }

    boolean codeIsPending(UUID playerId, IdentityLinkCode code) { return service.codeIsPending(playerId, code); }

    boolean mustDisconnect(UUID playerId) {
        List<IdentityBinding> bindings = service.findByPlayer(playerId);
        return bindings.isEmpty() || bindings.stream().allMatch(binding ->
                memberships.state(binding.externalKey()) == IdentityMemberships.State.ABSENT);
    }

    @Override
    public IdentityLinkResult redeem(String code, ExternalIdentity identity) {
        ensureAvailable();
        IdentityLinkResult result;
        synchronized (service) {
            if (memberships.state(identity.key()) != IdentityMemberships.State.JOINED) {
                throw new IdentityBindingException("Community membership is not verified",
                        new IllegalStateException("membership unavailable"));
            }
            result = service.redeem(code, identity);
        }
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
