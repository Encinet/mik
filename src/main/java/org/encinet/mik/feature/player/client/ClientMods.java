package org.encinet.mik.feature.player.client;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;

/** Main-thread snapshots of positive mod signals. Empty Optional means the connection cannot be observed. */
@FunctionalInterface
public interface ClientMods {
    Optional<Set<ClientMod>> detected(Player player);
}
