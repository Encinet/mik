package org.encinet.mik.module.identity;

import java.util.Optional;

/**
 * Platform-facing binding API. New chat or web adapters should depend on this interface rather
 * than on QQ or Bukkit-specific classes.
 */
public interface ExternalIdentityLinker {

    IdentityLinkResult redeem(String code, ExternalIdentity identity);

    Optional<IdentityBinding> find(ExternalIdentityKey key);

    Optional<IdentityBinding> unlink(ExternalIdentityKey key);
}
