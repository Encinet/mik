package org.encinet.mik.feature.player.security;

import java.util.UUID;

record TestRemovalAuthority(boolean permits) implements PasswordService.RemovalAuthority {
    @Override public boolean reserve(UUID account, UUID operation, long revision) { return permits; }
    @Override public void finish(UUID account, UUID operation, long revision, boolean present) { }
}
