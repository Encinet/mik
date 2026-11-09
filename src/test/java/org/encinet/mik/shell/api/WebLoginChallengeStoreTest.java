package org.encinet.mik.shell.api;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class WebLoginChallengeStoreTest {
    @Test void revokedAuthenticationCannotBeReadBeforeCleanupRuns() {
        var valid = new AtomicBoolean(true); var store = new WebLoginChallengeStore();
        store.confirm("123456", UUID.randomUUID(), "Player", "moderator", valid::get);
        valid.set(false); assertNull(store.find("123456"));
    }

    @Test void changingCredentialsRevokesOnlyTheTargetsApprovals() {
        var store = new WebLoginChallengeStore(); UUID changed = UUID.randomUUID(), other = UUID.randomUUID();
        store.confirm("123456", changed, "Changed", "moderator", () -> true);
        store.confirm("234567", changed, "Changed", "moderator", () -> true);
        store.confirm("345678", other, "Other", "member", () -> true);
        store.invalidate(changed); assertNull(store.find("123456")); assertNull(store.find("234567"));
        assertEquals(other, store.find("345678").playerUuid());
    }

    @Test void readsAreRetryableAndReconfirmationCannotSwitchAccountsOrExtendExpiry() {
        var time = new AtomicLong(1000); var store = new WebLoginChallengeStore(time::get); UUID owner = UUID.randomUUID();
        assertTrue(store.confirm("123456", owner, "First", "member", () -> true));
        var approved = store.find("123456"); assertEquals(approved, store.find("123456"));
        assertFalse(store.confirm("123456", UUID.randomUUID(), "Attacker", "member", () -> true));
        time.addAndGet(TimeUnit.MINUTES.toMillis(4));
        assertTrue(store.confirm("123456", owner, "Renamed", "custodian", () -> true));
        assertEquals(approved, store.find("123456"));
        time.addAndGet(TimeUnit.MINUTES.toMillis(1)); assertNull(store.find("123456"));
    }

    @Test void outstandingApprovalsAreBoundedPerAccount() {
        var store = new WebLoginChallengeStore(); UUID owner = UUID.randomUUID();
        for (int index = 0; index < 4; index++) assertTrue(store.confirm("12345" + index, owner, "Player", "member", () -> true));
        assertFalse(store.confirm("123459", owner, "Player", "member", () -> true));
        assertTrue(store.confirm("123459", UUID.randomUUID(), "Other", "member", () -> true));
    }

    @Test void simultaneousApprovalsCannotReplaceOneAnother() throws Exception {
        var store = new WebLoginChallengeStore(); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return store.confirm("123456", UUID.randomUUID(), "First", "member", () -> true); });
            var second = executor.submit(() -> { start.await(); return store.confirm("123456", UUID.randomUUID(), "Second", "member", () -> true); });
            start.countDown(); assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertNotNull(store.find("123456"));
        }
    }

    @Test void expiredReadCannotDeleteAReplacementApproval() throws Exception {
        AtomicLong time = new AtomicLong(); AtomicBoolean pause = new AtomicBoolean();
        var read = new CountDownLatch(1); var replaced = new CountDownLatch(1);
        var store = new WebLoginChallengeStore(() -> {
            if (pause.compareAndSet(true, false)) {
                read.countDown();
                try { if (!replaced.await(5, TimeUnit.SECONDS)) throw new AssertionError("replacement missing"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
            }
            return time.get();
        });
        store.confirm("123456", UUID.randomUUID(), "Old", "member", () -> true);
        try (var executor = Executors.newSingleThreadExecutor()) {
            pause.set(true); var stale = executor.submit(() -> store.find("123456")); assertTrue(read.await(5, TimeUnit.SECONDS));
            time.set(TimeUnit.MINUTES.toMillis(5)); store.confirm("123456", UUID.randomUUID(), "New", "member", () -> true);
            replaced.countDown(); assertNull(stale.get(5, TimeUnit.SECONDS)); assertEquals("New", store.find("123456").playerName());
        }
    }
}
