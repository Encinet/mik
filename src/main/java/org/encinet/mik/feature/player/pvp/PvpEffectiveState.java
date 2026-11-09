package org.encinet.mik.feature.player.pvp;

import java.util.UUID;

/** Resolves preferences and overrides without publishing a partially constructed module. */
record PvpEffectiveState(PvpSettingsStore settings, PvpOverrideRegistry overrides) implements PvpStateResolver {
    @Override public boolean effectiveEnabled(UUID playerId) {
        return effectiveEnabled(playerId, settings.get(playerId).enabled());
    }

    @Override public boolean effectiveEnabled(UUID playerId, boolean preference) {
        return overrides.effectiveEnabled(playerId, preference);
    }

    @Override public boolean hasOverride(UUID playerId) {
        return overrides.hasOverride(playerId);
    }
}
