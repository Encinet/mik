package org.encinet.mik.feature.player.security;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.Function;

/** Shared across login and management: account backoff, source rate and bounded hashing admission. */
final class PasswordAttempts implements AutoCloseable {
    private static final long BASE_DELAY = 30_000;
    private static final long MAX_DELAY = 48 * 3_600_000L;
    private static final long FAILURE_RETENTION = 24 * 3_600_000L;
    private static final long SOURCE_IDLE = 300_000;
    private static final int MAX_TRACKED = 4096;
    private static final int SOURCE_BURST = 20;
    private static final int GLOBAL_BURST = 120;

    private static final class Account {
        final PasswordCredential credential;
        int failures;
        long expires;
        long retryAt;
        Attempt active;
        Account(PasswordCredential credential) { this.credential = credential; }
    }

    private static final class Bucket {
        double tokens;
        long refilled;
        Bucket(int capacity, long now) { tokens = capacity; refilled = now; }
        void refill(int capacity, long interval, long now) {
            tokens = Math.min(capacity, tokens + Math.max(0, now - refilled) / (double) interval);
            refilled = now;
        }
    }

    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Map<String, Bucket> sources = new HashMap<>();
    private final LongSupplier clock;
    private final Function<UUID, PasswordCredential> credentials;
    private final Bucket global;
    private long lastSweep;
    private boolean closed;

    /** The clock is monotonic milliseconds. Credential identities come from the credential owner. */
    PasswordAttempts(LongSupplier clock, Function<UUID, PasswordCredential> credentials) {
        this.clock = clock;
        this.credentials = credentials;
        lastSweep = clock.getAsLong();
        global = new Bucket(GLOBAL_BURST, lastSweep);
    }

    /** Reserves before queueing. Cancellation does not release a hash that is still running. */
    <T> CompletableFuture<T> submit(Executor executor, UUID id, PasswordCredential credential, String source, Supplier<T> operation) {
        Attempt attempt = begin(id, credential, source);
        var result = new CompletableFuture<T>();
        try {
            executor.execute(() -> {
                T value = null;
                RuntimeException failure = null;
                try (attempt) {
                    if (result.isCancelled()) return;
                    try {
                        value = operation.get();
                        attempt.succeeded();
                    } catch (RuntimeException error) {
                        if (error instanceof PasswordException rejected && rejected.code() == PasswordException.Code.INVALID) {
                            long delay = attempt.failed();
                            if (delay > 0) error = cooldown(delay);
                        }
                        failure = error;
                    }
                }
                // Completion may immediately trigger another submission on another thread or in a callback.
                // Publish only after the finished computation has released its account reservation.
                if (failure == null) result.complete(value);
                else result.completeExceptionally(failure);
            });
        } catch (RejectedExecutionException error) {
            attempt.close();
            throw new PasswordException(PasswordException.Code.BUSY);
        }
        return result;
    }

    private synchronized Attempt begin(UUID id, PasswordCredential credential, String source) {
        if (closed) throw new PasswordException(PasswordException.Code.BUSY);
        if (credential == null || credentials.apply(id) != credential) throw new PasswordException(PasswordException.Code.CONFLICT);
        long now = clock.getAsLong();
        if (now - lastSweep >= 60_000) {
            accounts.entrySet().removeIf(entry -> entry.getValue().active == null && entry.getValue().expires <= now);
            sources.entrySet().removeIf(entry -> now - entry.getValue().refilled >= SOURCE_IDLE);
            lastSweep = now;
        }
        Account account = accounts.get(id);
        if (account != null && account.credential != credential) { accounts.remove(id); account = null; }
        if (account != null) {
            if (account.active != null) throw new PasswordException(PasswordException.Code.BUSY);
            if (account.expires <= now) { accounts.remove(id); account = null; }
            else if (account.retryAt > now) throw cooldown(account.retryAt - now);
        }
        if (account == null && accounts.size() >= MAX_TRACKED) throw new PasswordException(PasswordException.Code.BUSY);
        Bucket bucket = sources.get(source);
        if (bucket == null) {
            if (sources.size() >= MAX_TRACKED) throw new PasswordException(PasswordException.Code.BUSY);
            bucket = new Bucket(SOURCE_BURST, now);
        }
        bucket.refill(SOURCE_BURST, 3_000, now);
        global.refill(GLOBAL_BURST, 500, now);
        if (bucket.tokens < 1 || global.tokens < 1) throw new PasswordException(PasswordException.Code.BUSY);
        // Rejected account/source requests never consume global hashing capacity.
        bucket.tokens--;
        global.tokens--;
        sources.put(source, bucket);
        if (account == null) { account = new Account(credential); accounts.put(id, account); }
        Attempt attempt = new Attempt(id, account);
        account.active = attempt;
        return attempt;
    }

    private static PasswordException cooldown(long milliseconds) {
        return new PasswordException(PasswordException.Code.COOLDOWN, Math.max(1, (milliseconds + 999) / 1000));
    }

    private final class Attempt implements AutoCloseable {
        private final UUID id;
        private final Account account;
        private boolean finished;
        Attempt(UUID id, Account account) { this.id = id; this.account = account; }

        private boolean current() {
            return !closed && !finished && accounts.get(id) == account && account.active == this
                    && credentials.apply(id) == account.credential;
        }

        long failed() {
            synchronized (PasswordAttempts.this) {
                if (!current()) return 0;
                long now = clock.getAsLong();
                account.failures = Math.min(20, account.failures + 1);
                long delay = account.failures < 5 ? 0 : Math.min(MAX_DELAY, BASE_DELAY << Math.min(13, account.failures - 5));
                account.retryAt = now + delay;
                account.expires = account.retryAt + FAILURE_RETENTION;
                return delay;
            }
        }

        void succeeded() {
            synchronized (PasswordAttempts.this) {
                if (!current()) return;
                account.failures = 0;
                account.retryAt = 0;
                account.expires = 0;
            }
        }

        @Override public void close() {
            synchronized (PasswordAttempts.this) {
                if (finished) return;
                finished = true;
                if (accounts.get(id) != account || account.active != this) return;
                account.active = null;
                if (account.failures == 0) accounts.remove(id, account);
            }
        }
    }

    @Override public synchronized void close() { closed = true; accounts.clear(); sources.clear(); }
}
