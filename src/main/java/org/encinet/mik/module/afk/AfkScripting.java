package org.encinet.mik.module.afk;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/** AFK state and actions exposed to server scripts. */
public interface AfkScripting {
    boolean isAfk(UUID playerId);

    Optional<AfkState> getState(UUID playerId);

    void setAfkFromSkript(Player player, String customMessage, boolean broadcast);

    boolean clearAfkFromSkript(Player player, boolean broadcast);
}
