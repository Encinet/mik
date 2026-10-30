package org.encinet.mik.feature.player.security;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PasswordAttemptsTest {
    private static final PasswordCredential CREDENTIAL = new PasswordCredential(null);
    private static final Executor DIRECT = Runnable::run;
    private static final long HOUR = 3_600_000;

    @Test void exponentialBackoffCapsAt48HoursAndNeverPermanentlyLocks() {
        var now = new AtomicLong(100_000);
        var attempts = new PasswordAttempts(now::get, ignored -> CREDENTIAL);
        UUID id = UUID.randomUUID();
        for (int count = 1; count <= 25; count++) {
            PasswordException failure = wrong(attempts, id, "network");
            long delay = count < 5 ? 0 : Math.min(48 * HOUR, 30_000L << Math.min(13, count - 5));
            assertEquals(delay == 0 ? PasswordException.Code.INVALID : PasswordException.Code.COOLDOWN, failure.code());
            assertEquals(delay / 1000, failure.retryAfterSeconds());
            if (delay > 0) {
                PasswordException cooling = rejected(PasswordException.Code.COOLDOWN,
                        () -> attempts.submit(DIRECT, id, CREDENTIAL, "another-network", () -> true));
                assertEquals(delay / 1000, cooling.retryAfterSeconds());
                now.addAndGet(delay - 1);
                assertEquals(1, rejected(PasswordException.Code.COOLDOWN,
                        () -> attempts.submit(DIRECT, id, CREDENTIAL, "network", () -> true)).retryAfterSeconds());
                now.incrementAndGet();
            }
        }
        assertTrue(attempts.submit(DIRECT, id, CREDENTIAL, "network", () -> true).join());
        assertEquals(PasswordException.Code.INVALID, wrong(attempts, id, "network").code());
    }

    @Test void longCooldownOutlives24HoursAndHistoryExpires24HoursAfterItEnds() {
        var now = new AtomicLong(100_000);
        var attempts = new PasswordAttempts(now::get, ignored -> CREDENTIAL);
        UUID id = UUID.randomUUID();
        for (int count = 1; count <= 17; count++) {
            PasswordException failure = wrong(attempts, id, "network");
            now.addAndGet(failure.retryAfterSeconds() * 1000);
        }
        assertEquals(172800, wrong(attempts, id, "network").retryAfterSeconds());
        now.addAndGet(24 * HOUR);
        assertEquals(86400, rejected(PasswordException.Code.COOLDOWN,
                () -> attempts.submit(DIRECT, id, CREDENTIAL, "network", () -> true)).retryAfterSeconds());
        now.addAndGet(48 * HOUR);
        assertEquals(PasswordException.Code.INVALID, wrong(attempts, id, "network").code());
    }

    @Test void coolingRequestsNeitherExtendCooldownNorSpendGlobalCapacity() {
        var attempts = new PasswordAttempts(() -> 100_000, ignored -> CREDENTIAL);
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 5; i++) wrong(attempts, id, "network");
        for (int i = 0; i < 500; i++) {
            assertEquals(30, rejected(PasswordException.Code.COOLDOWN,
                    () -> attempts.submit(DIRECT, id, CREDENTIAL, "different-network", () -> true)).retryAfterSeconds());
        }
        for (int i = 0; i < 115; i++) assertTrue(attempts.submit(DIRECT, UUID.randomUUID(), CREDENTIAL, "source-" + i, () -> true).join());
        rejected(PasswordException.Code.BUSY, () -> attempts.submit(DIRECT, UUID.randomUUID(), CREDENTIAL, "extra", () -> true));
    }

    @Test void sourceRateIsIndependentOfAccountFailureAndGlobalBudget() {
        var now = new AtomicLong(100_000);
        var attempts = new PasswordAttempts(now::get, ignored -> CREDENTIAL);
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 20; i++) assertTrue(attempts.submit(DIRECT, id, CREDENTIAL, "shared-network", () -> true).join());
        for (int i = 0; i < 500; i++) rejected(PasswordException.Code.BUSY,
                () -> attempts.submit(DIRECT, UUID.randomUUID(), CREDENTIAL, "shared-network", () -> true));
        for (int i = 0; i < 100; i++) assertTrue(attempts.submit(DIRECT, UUID.randomUUID(), CREDENTIAL, "source-" + i, () -> true).join());
        now.addAndGet(2999);
        rejected(PasswordException.Code.BUSY, () -> attempts.submit(DIRECT, id, CREDENTIAL, "shared-network", () -> true));
        now.incrementAndGet();
        assertTrue(attempts.submit(DIRECT, id, CREDENTIAL, "shared-network", () -> true).join());
    }

    @Test void globalRateRefillsOneAdmissionEachHalfSecond() {
        var now = new AtomicLong(100_000);
        var attempts = new PasswordAttempts(now::get, ignored -> CREDENTIAL);
        for (int i = 0; i < 120; i++) assertTrue(attempts.submit(DIRECT, UUID.randomUUID(), CREDENTIAL, "source-" + i, () -> true).join());
        now.addAndGet(499);
        rejected(PasswordException.Code.BUSY, () -> attempts.submit(DIRECT, UUID.randomUUID(), CREDENTIAL, "new", () -> true));
        now.incrementAndGet();
        assertTrue(attempts.submit(DIRECT, UUID.randomUUID(), CREDENTIAL, "new", () -> true).join());
        rejected(PasswordException.Code.BUSY, () -> attempts.submit(DIRECT, UUID.randomUUID(), CREDENTIAL, "new", () -> true));
    }

    @Test void reservationPrecedesQueueAndExecutorRejectionReleasesIt() {
        var attempts = new PasswordAttempts(() -> 100_000, ignored -> CREDENTIAL);
        UUID id = UUID.randomUUID();
        Executor unavailable = ignored -> { throw new RejectedExecutionException(); };
        rejected(PasswordException.Code.BUSY, () -> attempts.submit(unavailable, id, CREDENTIAL, "network", () -> true));
        var queue = new ArrayList<Runnable>();
        CompletableFuture<Boolean> pending = attempts.submit(queue::add, id, CREDENTIAL, "network", () -> true);
        for (int i = 0; i < 200; i++) rejected(PasswordException.Code.BUSY,
                () -> attempts.submit(DIRECT, id, CREDENTIAL, "network", () -> true));
        assertFalse(pending.isDone());
        queue.removeFirst().run();
        assertTrue(pending.join());
        assertTrue(attempts.submit(DIRECT, id, CREDENTIAL, "network", () -> true).join());
    }

    @Test void cancellingRunningHashRetainsReservationAndCountsWrongPassword() {
        var attempts = new PasswordAttempts(() -> 100_000, ignored -> CREDENTIAL);
        UUID id = UUID.randomUUID();
        var queue = new ArrayList<Runnable>();
        var pending = new AtomicReference<CompletableFuture<Boolean>>();
        pending.set(attempts.submit(queue::add, id, CREDENTIAL, "network", () -> {
            assertTrue(pending.get().cancel(false));
            rejected(PasswordException.Code.BUSY, () -> attempts.submit(DIRECT, id, CREDENTIAL, "network", () -> true));
            throw new PasswordException(PasswordException.Code.INVALID);
        }));
        queue.removeFirst().run();
        assertTrue(pending.get().isCancelled());
        for (int i = 0; i < 3; i++) assertEquals(PasswordException.Code.INVALID, wrong(attempts, id, "network").code());
        assertEquals(30, wrong(attempts, id, "network").retryAfterSeconds());
    }

    @Test void completionCallbacksCanImmediatelySubmitTheNextVerification() {
        for (boolean correct : new boolean[] {false, true}) {
            var attempts = new PasswordAttempts(() -> 100_000, ignored -> CREDENTIAL);
            UUID id = UUID.randomUUID();
            var queue = new ArrayList<Runnable>();
            var first = attempts.submit(queue::add, id, CREDENTIAL, "network", () -> {
                if (!correct) throw new PasswordException(PasswordException.Code.INVALID);
                return true;
            });
            var next = first.handle((value, error) -> attempts.submit(DIRECT, id, CREDENTIAL, "network", () -> true).join());
            queue.removeFirst().run();
            assertTrue(next.join());
        }
    }

    @Test void cancellingQueuedHashSkipsComputationAndDoesNotCountFailure() {
        var attempts = new PasswordAttempts(() -> 100_000, ignored -> CREDENTIAL);
        UUID id = UUID.randomUUID();
        var queue = new ArrayList<Runnable>();
        var computed = new AtomicBoolean();
        var pending = attempts.submit(queue::add, id, CREDENTIAL, "network", () -> {
            computed.set(true); throw new PasswordException(PasswordException.Code.INVALID);
        });
        pending.cancel(false);
        queue.removeFirst().run();
        assertFalse(computed.get());
        for (int i = 0; i < 4; i++) assertEquals(PasswordException.Code.INVALID, wrong(attempts, id, "network").code());
        assertEquals(30, wrong(attempts, id, "network").retryAfterSeconds());
    }

    @Test void resetInvalidatesOldAttemptWithoutReleasingNewCredentialReservation() {
        var credential = new AtomicReference<>(CREDENTIAL);
        var attempts = new PasswordAttempts(() -> 100_000, ignored -> credential.get());
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 4; i++) wrong(attempts, id, "network");
        var queue = new ArrayList<Runnable>();
        var stale = attempts.submit(queue::add, id, CREDENTIAL, "network", () -> { throw new PasswordException(PasswordException.Code.INVALID); });
        credential.set(new PasswordCredential(null));
        var current = attempts.submit(queue::add, id, credential.get(), "network", () -> true);
        queue.removeFirst().run();
        assertEquals(PasswordException.Code.INVALID, assertInstanceOf(PasswordException.class,
                assertThrows(CompletionException.class, stale::join).getCause()).code());
        rejected(PasswordException.Code.BUSY, () -> attempts.submit(DIRECT, id, credential.get(), "network", () -> true));
        queue.removeFirst().run();
        assertTrue(current.join());
        rejected(PasswordException.Code.CONFLICT, () -> attempts.submit(DIRECT, id, CREDENTIAL, "network", () -> true));
        assertTrue(attempts.submit(DIRECT, id, credential.get(), "network", () -> true).join());
    }

    @Test void credentialReplacementImmediatelyReleasesCooldown() {
        var credential = new AtomicReference<>(CREDENTIAL);
        var attempts = new PasswordAttempts(() -> 100_000, ignored -> credential.get());
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 5; i++) wrong(attempts, id, "network");
        credential.set(new PasswordCredential(null));
        assertTrue(attempts.submit(DIRECT, id, credential.get(), "network", () -> true).join());
    }

    @Test void closingPreventsNewReservationsAndDiscardsLateFailure() {
        var attempts = new PasswordAttempts(() -> 100_000, ignored -> CREDENTIAL);
        var queue = new ArrayList<Runnable>();
        var pending = attempts.submit(queue::add, UUID.randomUUID(), CREDENTIAL, "network",
                () -> { throw new PasswordException(PasswordException.Code.INVALID); });
        attempts.close();
        queue.removeFirst().run();
        assertEquals(PasswordException.Code.INVALID, assertInstanceOf(PasswordException.class,
                assertThrows(CompletionException.class, pending::join).getCause()).code());
        rejected(PasswordException.Code.BUSY, () -> attempts.submit(DIRECT, UUID.randomUUID(), CREDENTIAL, "network", () -> true));
    }

    private static PasswordException wrong(PasswordAttempts attempts, UUID id, String source) {
        CompletableFuture<Boolean> submitted = attempts.submit(DIRECT, id, CREDENTIAL, source,
                () -> { throw new PasswordException(PasswordException.Code.INVALID); });
        return assertInstanceOf(PasswordException.class, assertThrows(CompletionException.class, submitted::join).getCause());
    }

    private static PasswordException rejected(PasswordException.Code code, Runnable operation) {
        PasswordException failure = assertThrows(PasswordException.class, operation::run);
        assertEquals(code, failure.code());
        return failure;
    }
}
