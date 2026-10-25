package org.encinet.mik.feature.player.pvp;

import java.util.UUID;

interface PvpStateResolver {

    boolean effectiveEnabled(UUID playerId);

    boolean effectiveEnabled(UUID playerId, boolean preference);

    boolean hasOverride(UUID playerId);
}
