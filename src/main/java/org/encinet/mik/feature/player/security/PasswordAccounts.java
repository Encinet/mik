package org.encinet.mik.feature.player.security;
import java.util.UUID;

/** Cached credential presence and coordination with operations that require keeping a password. */
public interface PasswordAccounts {
    boolean hasPassword(UUID playerId);

    /** Runs off the main thread, atomically with credential removal. Never call back into Bukkit. */
    <T, E extends Exception> T coordinate(Work<T, E> work) throws E;

    @FunctionalInterface
    interface Work<T, E extends Exception> {
        T run() throws E;
    }
}
