package org.encinet.mik.module.governance.membership;

import java.util.UUID;

/** Supplies current ban state without coupling governance to the ban module. */
@FunctionalInterface
public interface ActiveBanLookup {
    boolean isActivelyBanned(UUID playerId, String playerName);
}
