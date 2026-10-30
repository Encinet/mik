package org.encinet.mik.feature.player.security;

import org.bukkit.entity.Player;
import java.util.UUID;

/** Existing governance tests assume candidates have already completed credential setup. */
public final class EstablishedPasswordAccounts implements PasswordAccess {
    @Override public boolean hasPassword(UUID id) { return true; }
    @Override public <T, E extends Exception> T coordinate(Work<T, E> work) throws E { return work.run(); }
    @Override public void open(Player player, Runnable back) { throw new UnsupportedOperationException(); }
    @Override public void reset(Player actor, UUID target) { throw new UnsupportedOperationException(); }
    @Override public void manage(Player actor, UUID target, Runnable back) { throw new UnsupportedOperationException(); }
    @Override public void roleChanged(UUID id) { }
}
