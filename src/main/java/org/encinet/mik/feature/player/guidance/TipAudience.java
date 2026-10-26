package org.encinet.mik.feature.player.guidance;

import org.bukkit.entity.Player;
import org.encinet.mik.shared.role.RolePermissions;

/** Shared exposure bonus; callers read the player's current permission on the main thread. */
enum TipAudience {
    MEMBER(1),
    NEWCOMER(2);

    private final int multiplier;

    TipAudience(int multiplier) {
        this.multiplier = multiplier;
    }

    static TipAudience forPlayer(Player player) {
        return player.hasPermission(RolePermissions.MEMBER) ? MEMBER : NEWCOMER;
    }

    double chance(double probability) {
        return Math.min(1.0, probability * multiplier);
    }

    long ambientDelay(long intervalMillis) {
        return intervalMillis / multiplier;
    }
}
