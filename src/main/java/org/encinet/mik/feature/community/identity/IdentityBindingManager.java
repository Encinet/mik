package org.encinet.mik.feature.community.identity;

import java.util.List;
import java.util.UUID;

/**
 * Bukkit service used by the social host and other application services. READY adapters register
 * both a binding entry point and authoritative membership monitoring. Login challenges and
 * {@code /bind <platform>} share the registry; stopped adapters preserve durable bindings for revalidation.
 */
public interface IdentityBindingManager extends ExternalIdentityLinker {

    IdentityPlatformRegistration registerPlatform(IdentityPlatform platform, IdentityMemberships memberships);

    /** Immutable in-memory snapshot; this method never performs storage I/O. */
    default List<IdentityBinding> bindings() {
        return List.of();
    }

    List<IdentityBinding> findByPlayer(UUID playerId);
}
