package org.encinet.mik.feature.player.security;
import org.encinet.mik.shared.role.PlayerRole;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Owns credentials and logical authentication together. Hashing happens outside the commit lock. */
public final class PasswordService implements PasswordAccounts, AutoCloseable {
    @FunctionalInterface
    public interface Retention {
        boolean required(UUID id) throws Exception;
    }

    /** A connection's authority; only this service can issue it. Closing never waits for storage. */
    static final class Session implements AutoCloseable {
        private final PasswordService owner;
        private final UUID account;
        private volatile boolean active = true;
        private Session(PasswordService owner, UUID account) { this.owner = owner; this.account = account; }
        @Override public void close() {
            active = false;
            owner.accounts.computeIfPresent(account, (id, state) ->
                    state.credential() == PasswordCredential.NONE && state.session() == this ? null : state);
        }
    }

    /** Credential and authority are published as one state, so a commit cannot retain stale authentication. */
    private record Account(PasswordCredential credential, Session session) { }

    record Change<T>(UUID account, PasswordCredential credential, T value) {
        @Override public String toString() { return "Change[account=" + account + "]"; }
    }

    private final Object coordination = new Object();
    private final PasswordStore store;
    private final PasswordHasher hasher = new PasswordHasher();
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
    private volatile boolean ready;
    private volatile Runnable storageFailure = () -> { };
    private volatile Retention retention = id -> { throw new IllegalStateException("Governance not connected"); };

    public PasswordService(Path path) { store = new PasswordStore(path); }

    void open() {
        synchronized (coordination) {
            store.open().forEach((id, credential) -> accounts.put(id, new Account(credential, null)));
            ready = true;
        }
    }

    public void retention(Retention retention) { this.retention = java.util.Objects.requireNonNull(retention); }
    boolean available() { return ready; }
    void onStorageFailure(Runnable observer) { storageFailure = java.util.Objects.requireNonNull(observer); }

    @Override public boolean hasPassword(UUID id) { return credential(id).present(); }

    PasswordCredential credential(UUID id) {
        PasswordCredential value = currentCredential(id);
        if (value == null) throw new PasswordException(PasswordException.Code.STORAGE);
        return value;
    }

    /** Non-throwing guard: unavailable credentials cannot authorize work. */
    PasswordCredential currentCredential(UUID id) {
        if (!ready) return null;
        Account account = accounts.get(id);
        return ready ? account == null ? PasswordCredential.NONE : account.credential() : null;
    }

    @Override public <T, E extends Exception> T coordinate(Work<T, E> work) throws E {
        synchronized (coordination) { ensureReady(); return work.run(); }
    }

    boolean retained(UUID id) {
        try { return retention.required(id); }
        catch (Exception error) { throw new PasswordException(PasswordException.Code.STORAGE, error); }
    }

    PasswordCredential verify(UUID id, String raw) {
        PasswordCredential expected = credential(id);
        String password;
        try { password = PasswordPolicy.normalize(raw); }
        catch (PasswordException error) { throw new PasswordException(PasswordException.Code.INVALID); }
        if (!expected.present() || !hasher.verify(password, expected.hash())) throw new PasswordException(PasswordException.Code.INVALID);
        return coordinate(() -> { checkCurrent(id, expected); return expected; });
    }

    /** Claim a verified credential (or an optional account without one) before granting world entry. */
    Session login(UUID id, PasswordCredential expected) {
        return coordinate(() -> {
            checkCurrent(id, expected);
            Account current = accounts.get(id);
            if (current != null && current.session() != null && current.session().active) {
                throw new PasswordException(PasswordException.Code.BUSY);
            }
            Session session = new Session(this, id);
            accounts.put(id, new Account(expected, session));
            return session;
        });
    }

    boolean authenticated(Session session) {
        if (!ready || session == null || !session.active) return false;
        Account account = accounts.get(session.account);
        return ready && account != null && account.session() == session && session.active;
    }

    Change<Void> set(UUID id, String name, PasswordCredential expected, String password, String confirm,
                     Supplier<PlayerRole> role, Session continuation) {
        String hash = hasher.hash(matching(password, confirm, name));
        return coordinate(() -> {
            checkCurrent(id, expected);
            if (hasPassword(id)) throw new PasswordException(PasswordException.Code.CONFLICT);
            if (PasswordPolicy.mandatory(role.get())) throw new PasswordException(PasswordException.Code.REQUIRED_ROLE);
            return save(id, hash, id.toString(), "SET", null, continuation);
        });
    }

    Change<Void> change(UUID id, String name, PasswordCredential expected, String current, String password, String confirm,
                        Session continuation) {
        if (verify(id, current) != expected) throw new PasswordException(PasswordException.Code.CONFLICT);
        String hash = hasher.hash(matching(password, confirm, name));
        return coordinate(() -> { checkCurrent(id, expected); return save(id, hash, id.toString(), "CHANGE", null, continuation); });
    }

    Change<Void> remove(UUID id, PasswordCredential expected, String current, Supplier<PlayerRole> role, Session continuation) {
        if (verify(id, current) != expected) throw new PasswordException(PasswordException.Code.CONFLICT);
        return coordinate(() -> {
            checkCurrent(id, expected);
            if (PasswordPolicy.mandatory(role.get())) throw new PasswordException(PasswordException.Code.KEEP_ROLE);
            if (retained(id)) throw new PasswordException(PasswordException.Code.REQUIRED_APPLICATION);
            return save(id, null, id.toString(), "REMOVE", null, continuation);
        });
    }

    Change<String> reset(UUID target, PasswordCredential expected, UUID actor, PasswordCredential actorCredential,
                         Supplier<PlayerRole> actorRole, Supplier<PlayerRole> targetRole) {
        String password = hasher.randomPassword();
        String hash = hasher.hash(password);
        return coordinate(() -> {
            checkCurrent(target, expected);
            if (actor != null) {
                checkCurrent(actor, actorCredential);
                if (!hasPassword(actor) || !PasswordPolicy.canReset(actorRole.get(), targetRole.get(), hasPassword(target), actor.equals(target))) {
                    throw new PasswordException(PasswordException.Code.DENIED);
                }
            }
            return save(target, hash, actor == null ? "console" : actor.toString(), "RESET", password, null);
        });
    }

    private static String matching(String password, String confirm, String name) {
        String normalized = PasswordPolicy.validate(password, name);
        if (!normalized.equals(PasswordPolicy.normalize(confirm))) throw new PasswordException(PasswordException.Code.MISMATCH);
        return normalized;
    }

    private void checkCurrent(UUID id, PasswordCredential expected) {
        if (credential(id) != expected) throw new PasswordException(PasswordException.Code.CONFLICT);
    }

    private <T> Change<T> save(UUID id, String hash, String actor, String operation, T value, Session continuation) {
        if (continuation != null && (!id.equals(continuation.account) || !authenticated(continuation))) {
            throw new PasswordException(PasswordException.Code.SESSION_EXPIRED);
        }
        try {
            PasswordCredential committed = store.replace(id, hash, actor, operation);
            // Atomic publication changes the password and revokes all authority except the exact initiating session.
            accounts.put(id, new Account(committed, continuation));
            return new Change<>(id, committed, value);
        } catch (PasswordException error) {
            if (error.code() == PasswordException.Code.STORAGE) {
                ready = false;
                try { storageFailure.run(); }
                catch (RuntimeException notification) { error.addSuppressed(notification); }
            }
            throw error;
        }
    }

    private void ensureReady() { if (!ready) throw new PasswordException(PasswordException.Code.STORAGE); }

    @Override public void close() {
        synchronized (coordination) { ready = false; store.close(); accounts.clear(); }
    }
}
