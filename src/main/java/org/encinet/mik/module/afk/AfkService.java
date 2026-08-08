package org.encinet.mik.module.afk;

import java.util.Optional;
import java.util.UUID;

public interface AfkService extends AfkActivityService {

    boolean isAfk(UUID playerId);

    default boolean isActivityEligible(UUID playerId) {
        return !isAfk(playerId);
    }

    Optional<AfkState> getState(UUID playerId);

    void addListener(AfkStateListener listener);

    void removeListener(AfkStateListener listener);
}
