package org.encinet.mik.module.governance.membership;

import java.util.UUID;

/** Supplies current role membership without coupling use cases to LuckPerms. */
@FunctionalInterface
public interface CurrentMemberLookup {
    boolean isCurrentlyMember(UUID playerId, String playerName);
}
