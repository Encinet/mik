package org.encinet.mik.module.menu;

import org.bukkit.entity.Player;

import java.util.Objects;

/** Selects the eye level used as the spatial reference for a floating menu. */
public enum FloatingMenuViewpoint {
    /** Follows the player's actual eye level when their pose changes. */
    POSE_AWARE,

    /** Always frames the menu from standing eye level, even while crouching. */
    STANDING;

    double eyeHeight(Player player) {
        Objects.requireNonNull(player, "player");
        return eyeHeight(player.getEyeHeight(), player.getEyeHeight(true));
    }

    double eyeHeight(double posedEyeHeight, double standingEyeHeight) {
        return this == POSE_AWARE ? posedEyeHeight : standingEyeHeight;
    }
}
