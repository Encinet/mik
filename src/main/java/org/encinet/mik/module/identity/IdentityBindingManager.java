package org.encinet.mik.module.identity;

import java.util.List;
import java.util.UUID;

/**
 * Bukkit service used by the social host and other application services. The host owns platform
 * registration leases; a READY platform becomes available to the in-game
 * {@code /bind <platform>} flow, while durable bindings remain if a platform stops.
 */
public interface IdentityBindingManager extends ExternalIdentityLinker {

    IdentityPlatformRegistration registerPlatform(IdentityPlatform platform);

    /** Immutable in-memory snapshot; this method never performs storage I/O. */
    default List<IdentityBinding> bindings() {
        return List.of();
    }

    List<IdentityBinding> findByPlayer(UUID playerId);
}
