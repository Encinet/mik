package org.encinet.mik.module.social.game;

import org.encinet.mik.module.identity.IdentityBinding;

import java.util.Optional;
import java.util.UUID;

/** Platform-neutral facade for the small set of game snapshots social features need. */
public interface SocialGameService {
    ServerSnapshot serverSnapshot(boolean includePlayerNames);

    SocialPlayerProfile playerProfile(IdentityBinding binding);

    Optional<SocialPlayerProfile> findPlayerProfile(String exactPlayerName);

    /** Full membership includes member, moderator, and custodian roles. */
    boolean isFullMember(UUID playerId);
}
