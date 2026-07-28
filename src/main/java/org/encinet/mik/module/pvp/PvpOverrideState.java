package org.encinet.mik.module.pvp;

/** The currently winning temporary PVP override for a player. */
public record PvpOverrideState(
        String owner,
        String id,
        boolean enabled,
        int priority,
        long expiresAtMillis
) {

    public boolean expires() {
        return expiresAtMillis > 0L;
    }
}
