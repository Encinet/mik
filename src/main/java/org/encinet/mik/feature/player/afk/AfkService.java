package org.encinet.mik.feature.player.afk;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public interface AfkService extends AfkActivityService {

    boolean isAfk(UUID playerId);

    /** AFK with no observed input for the idle timeout; manual labels alone do not qualify. */
    boolean isIdleAfk(UUID playerId);

    /** Passive observation suppresses automatic AFK and rewards without refreshing activity clocks. */
    ActivityLease beginPassiveObservation(Player player);

    default boolean isActivityEligible(UUID playerId) {
        return !isAfk(playerId);
    }

    Optional<AfkState> getState(UUID playerId);

    void addListener(AfkStateListener listener);

    void removeListener(AfkStateListener listener);
}
