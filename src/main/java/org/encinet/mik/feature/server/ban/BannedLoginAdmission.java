package org.encinet.mik.feature.server.ban;

import io.papermc.paper.connection.PlayerConfigurationConnection;

import java.util.UUID;

/** Optional destination for an exact account ban. Other admission checks still apply. */
public interface BannedLoginAdmission {
    BannedLoginAdmission NONE = new BannedLoginAdmission() {
        @Override public boolean canConfigure(UUID account) { return false; }
        @Override public boolean canEnter(UUID account, PlayerConfigurationConnection connection) { return false; }
    };

    boolean canConfigure(UUID account);

    /** Invoked on the server thread after the configuration phase has prepared the spawn. */
    boolean canEnter(UUID account, PlayerConfigurationConnection connection);
}
