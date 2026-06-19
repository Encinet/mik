package org.encinet.mik.module.afk;

import java.util.Optional;
import java.util.UUID;

public interface AfkService {

    boolean isAfk(UUID playerId);

    Optional<AfkState> getState(UUID playerId);

    void addListener(AfkStateListener listener);

    void removeListener(AfkStateListener listener);
}
