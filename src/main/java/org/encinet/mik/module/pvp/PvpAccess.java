package org.encinet.mik.module.pvp;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** PVP state and overrides available to server integrations. */
public interface PvpAccess {
    boolean isEnabled(Player player);

    boolean preferenceEnabled(Player player);

    void setPreference(Player player, boolean enabled);

    void setOverride(Player player, String owner, String id,
                     boolean enabled, int priority, long durationMillis);

    boolean clearOverride(Player player, String owner, String id);

    void clearOverrides(Player player, String owner);

    void clearOverridesOwnedBy(String owner);

    Optional<PvpOverrideState> activeOverride(Player player);

    boolean hasOverride(Player player, String owner, String id);

    Set<String> overrideIds(Player player, String owner);

    boolean isCombatTagged(UUID playerId);

    long combatTagRemainingSeconds(UUID playerId);
}
