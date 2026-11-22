package org.encinet.mik.feature.player.security;

import java.util.UUID;

/** Cached credential presence for local game features. */
@FunctionalInterface
public interface PasswordAccounts {
    boolean hasPassword(UUID playerId);
}
