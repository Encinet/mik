package org.encinet.mik.feature.player.movement;

import org.bukkit.entity.Player;

/** Main-thread, temporary exploration ceilings. Speeds use Bukkit's [0.01, 1] scale. */
public interface FlightSpeedControl {
    void limitExplorationSpeed(Player player, float maximumSpeed);

    void releaseExplorationSpeed(Player player);
}
