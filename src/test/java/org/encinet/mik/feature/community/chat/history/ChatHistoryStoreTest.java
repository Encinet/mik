package org.encinet.mik.feature.community.chat.history;

import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ChatHistoryStoreTest {
    @TempDir Path temp;
    private static final UUID ALICE = UUID.randomUUID(), BOB = UUID.randomUUID(), OTHER = UUID.randomUUID();

    @Test void compressionScopesFollowChannelsPrivateParticipantsAndRestrictedRows() throws Exception {
        try (var store = new ChatHistoryStore(temp.resolve("scopes.db"))) {
            for (String id : java.util.List.of("public-1", "public-2"))
                store.append(entry(id, ChatChannel.PUBLIC, Set.of(), Optional.empty())).join();
            for (String id : java.util.List.of("restricted-1", "restricted-2"))
                store.append(entry(id, ChatChannel.PUBLIC, Set.of(), Optional.of(Set.of(ALICE)))).join();
            var publicRows = store.read(query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)).join().rows();
            assertNotEquals(publicRows.get(0).compressionScope(), publicRows.get(1).compressionScope());
            assertNotEquals(publicRows.get(1).compressionScope(), publicRows.get(2).compressionScope());
            assertEquals(publicRows.get(2).compressionScope(), publicRows.get(3).compressionScope());
            for (String id : java.util.List.of("bob-1", "bob-2"))
                store.append(entry(id, ChatChannel.PRIVATE, Set.of(ALICE, BOB), Optional.empty())).join();
            store.append(entry("other", ChatChannel.PRIVATE, Set.of(ALICE, OTHER), Optional.empty())).join();
            var privateRows = store.read(query(ALICE, false, ChatChannel.PRIVATE, null, Long.MAX_VALUE)).join().rows();
            assertNotEquals(privateRows.get(0).compressionScope(), privateRows.get(1).compressionScope());
            assertEquals(privateRows.get(1).compressionScope(), privateRows.get(2).compressionScope());
            assertNotEquals(publicRows.get(2).compressionScope(), privateRows.get(2).compressionScope());
        }
    }

    @Test void snapshotPreparationAndProjectionRunOnTheSameWorkerAndFailuresDoNotBlockLaterWork() throws Exception {
        Thread caller = Thread.currentThread();
        var preparedBy = new AtomicReference<Thread>();
        var projectedBy = new AtomicReference<Thread>();
        try (var store = new ChatHistoryStore(temp.resolve("worker.db"))) {
            var write = store.append(() -> {
                preparedBy.set(Thread.currentThread());
                return entry("background", ChatChannel.PUBLIC, Set.of(), Optional.empty());
            });
            var result = store.read(query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE), page -> {
                projectedBy.set(Thread.currentThread());
                return page.rows().stream().map(ChatHistoryStore.Row::payload).toList();
            });
            assertEquals(java.util.List.of("background"), result.get(5, TimeUnit.SECONDS));
            write.get(5, TimeUnit.SECONDS);
            assertNotSame(caller, preparedBy.get());
            assertNotSame(preparedBy.get(), projectedBy.get());
            assertThrows(java.util.concurrent.ExecutionException.class, () -> store.append(() -> {
                throw new IllegalArgumentException("Invalid snapshot");
            }).get(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.ExecutionException.class, () -> store.read(
                    query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE), _ -> {
                        throw new IllegalArgumentException("Invalid presentation");
                    }).get(5, TimeUnit.SECONDS));
            store.append(entry("after-failure", ChatChannel.PUBLIC, Set.of(), Optional.empty())).get(5, TimeUnit.SECONDS);
            assertEquals(Set.of("background", "after-failure"), payloads(store, query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)));
        }
    }

    @Test void aSaturatedOrClosedQueueNeverRunsSnapshotPreparationOnTheCaller() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var rejectedRan = new AtomicBoolean();
        var store = new ChatHistoryStore(temp.resolve("bounded.db"));
        try {
            store.append(() -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Worker was not released");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return entry("accepted", ChatChannel.PUBLIC, Set.of(), Optional.empty());
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 256; i++) store.maintain();
            var rejected = store.append(() -> {
                rejectedRan.set(true);
                return entry("rejected", ChatChannel.PUBLIC, Set.of(), Optional.empty());
            });
            assertInstanceOf(RejectedExecutionException.class, rejected.handle((_, failure) -> failure).get(5, TimeUnit.SECONDS));
            assertFalse(rejectedRan.get());
        } finally {
            release.countDown();
            store.close();
        }
        store.stopped().get(5, TimeUnit.SECONDS);
        var closed = store.append(() -> {
            rejectedRan.set(true);
            return entry("closed", ChatChannel.PUBLIC, Set.of(), Optional.empty());
        });
        assertTrue(closed.isCompletedExceptionally());
        assertFalse(rejectedRan.get());
    }

    @Test void persistsAcrossReopeningDeduplicatesAndDrainsBeforeClosing() throws Exception {
        Path path = temp.resolve("history.db");
        var first = new ChatHistoryStore(path);
        first.append(entry("once", ChatChannel.PUBLIC, Set.of(), Optional.empty()));
        first.append(entry("once", ChatChannel.PUBLIC, Set.of(), Optional.empty()));
        first.close();
        first.stopped().get(5, TimeUnit.SECONDS);
        assertTrue(first.append(entry("late", ChatChannel.PUBLIC, Set.of(), Optional.empty())).isCompletedExceptionally());
        try (var reopened = new ChatHistoryStore(path)) {
            var page = reopened.read(query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)).get(5, TimeUnit.SECONDS);
            assertEquals(1, page.rows().size());
            assertEquals("once", page.rows().getFirst().payload());
        }
    }

    @Test void paginatesEqualTimestampMessagesWithoutGapsWhileNewMessagesArrive() throws Exception {
        try (var store = new ChatHistoryStore(temp.resolve("history.db"), new MutableClock())) {
            for (int i = 0; i < ChatHistoryStore.PAGE_SIZE + 21; i++) store.append(entry("message-" + i, ChatChannel.PUBLIC, Set.of(), Optional.empty())).join();
            long before = Long.MAX_VALUE;
            Set<String> found = new HashSet<>();
            var page = store.read(query(ALICE, false, ChatChannel.PUBLIC, null, before)).join();
            assertTrue(page.hasMore());
            assertEquals(ChatHistoryStore.PAGE_SIZE, page.rows().size());
            page.rows().forEach(row -> assertTrue(found.add(row.payload())));
            store.append(entry("new-arrival", ChatChannel.PUBLIC, Set.of(), Optional.empty())).join();
            while (page.hasMore()) {
                before = page.nextBefore();
                page = store.read(query(ALICE, false, ChatChannel.PUBLIC, null, before)).join();
                page.rows().forEach(row -> assertTrue(found.add(row.payload())));
            }
            assertEquals(ChatHistoryStore.PAGE_SIZE + 21, found.size());
            assertFalse(found.contains("new-arrival"));
        }
    }

    @Test void enforcesPrivateMembershipPeerSelectionStaffAndAdditionalVisibilityInSql() throws Exception {
        try (var store = new ChatHistoryStore(temp.resolve("history.db"))) {
            store.append(entry("ab", ChatChannel.PRIVATE, Set.of(ALICE, BOB), Optional.empty())).join();
            store.append(entry("bo", ChatChannel.PRIVATE, Set.of(BOB, OTHER), Optional.empty())).join();
            store.append(entry("staff", ChatChannel.STAFF, Set.of(), Optional.empty())).join();
            store.append(entry("restricted", ChatChannel.PUBLIC, Set.of(), Optional.of(Set.of(ALICE)))).join();
            store.append(entry("nobody", ChatChannel.PUBLIC, Set.of(), Optional.of(Set.of()))).join();
            assertEquals(Set.of("ab"), payloads(store, query(ALICE, false, ChatChannel.PRIVATE, null, Long.MAX_VALUE)));
            assertEquals(Set.of("ab"), payloads(store, query(BOB, false, ChatChannel.PRIVATE, ALICE, Long.MAX_VALUE)));
            assertTrue(payloads(store, query(OTHER, true, ChatChannel.PRIVATE, ALICE, Long.MAX_VALUE)).isEmpty());
            assertEquals(Set.of("restricted"), payloads(store, query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)));
            assertTrue(payloads(store, query(BOB, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> query(ALICE, false, ChatChannel.STAFF, null, Long.MAX_VALUE));
            assertEquals(Set.of("staff"), payloads(store, query(ALICE, true, ChatChannel.STAFF, null, Long.MAX_VALUE)));
        }
    }

    @Test void expiresAtSevenDaysAndCascadesVisibilityRows() throws Exception {
        MutableClock clock = new MutableClock();
        Path path = temp.resolve("history.db");
        var store = new ChatHistoryStore(path, clock);
        store.append(entry("old", ChatChannel.PUBLIC, Set.of(), Optional.of(Set.of(ALICE)))).join();
        assertEquals(clock.now + Duration.ofDays(7).toMillis(), store.read(
                query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)).join().rows().getFirst().expiresAt());
        clock.now += Duration.ofDays(7).toMillis() - 1;
        assertEquals(Set.of("old"), payloads(store, query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)));
        clock.now++;
        assertTrue(payloads(store, query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)).isEmpty());
        clock.now += Duration.ofHours(1).toMillis();
        store.maintain().join(); store.close(); store.stopped().get(5, TimeUnit.SECONDS);
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + path); var statement = db.createStatement();
             var count = statement.executeQuery("SELECT count(*) FROM chat_viewers")) {
            assertTrue(count.next()); assertEquals(0, count.getInt(1));
        }
    }

    @Test void pageByteBudgetStillAdvancesAndReturnsEveryLargeMessage() {
        try (var store = new ChatHistoryStore(temp.resolve("history.db"))) {
            String payload = "字".repeat(25_000);
            for (int i = 0; i < 3; i++) store.append(new ChatHistoryStore.Entry("large-" + i,
                    new ChatAudience(ChatChannel.PUBLIC, Set.of()), Optional.empty(), payload)).join();
            var first = store.read(query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)).join();
            assertEquals(1, first.rows().size()); assertTrue(first.hasMore());
            var second = store.read(query(ALICE, false, ChatChannel.PUBLIC, null, first.nextBefore())).join();
            assertEquals(1, second.rows().size()); assertTrue(second.hasMore());
            var third = store.read(query(ALICE, false, ChatChannel.PUBLIC, null, second.nextBefore())).join();
            assertEquals(1, third.rows().size()); assertFalse(third.hasMore());
        }
    }

    @Test void incrementalSyncUsesAStableDatabaseIdentityAndDoesNotSkipArrivalsDuringBackfill() throws Exception {
        Path path = temp.resolve("sync.db");
        String identity;
        long checkpoint;
        var store = new ChatHistoryStore(path);
        for (int i = 0; i < ChatHistoryStore.PAGE_SIZE + 21; i++) store.append(entry("initial-" + i, ChatChannel.PUBLIC, Set.of(), Optional.empty())).join();
        var first = store.read(query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)).join();
        identity = first.databaseId(); checkpoint = first.head();
        store.append(entry("during-backfill", ChatChannel.PUBLIC, Set.of(), Optional.empty())).join();
        var older = store.read(new ChatHistoryStore.Query(ALICE, false, ChatChannel.PUBLIC, null, first.nextBefore(), 0)).join();
        assertEquals(21, older.rows().size()); assertFalse(older.hasMore());
        var delta = store.read(new ChatHistoryStore.Query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE, checkpoint)).join();
        assertEquals(Set.of("during-backfill"), delta.rows().stream().map(ChatHistoryStore.Row::payload).collect(java.util.stream.Collectors.toSet()));
        store.close(); store.stopped().get(5, TimeUnit.SECONDS);
        try (var reopened = new ChatHistoryStore(path)) {
            assertEquals(identity, reopened.read(query(ALICE, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)).join().databaseId());
        }
    }

    private static Set<String> payloads(ChatHistoryStore store, ChatHistoryStore.Query query) {
        return store.read(query).join().rows().stream().map(ChatHistoryStore.Row::payload)
                .collect(java.util.stream.Collectors.toSet());
    }
    private static ChatHistoryStore.Query query(UUID viewer, boolean staff, ChatChannel channel, UUID target, long before) {
        return new ChatHistoryStore.Query(viewer, staff, channel, target, before);
    }
    private static ChatHistoryStore.Entry entry(String id, ChatChannel channel, Set<UUID> participants, Optional<Set<UUID>> viewers) {
        return new ChatHistoryStore.Entry(id, new ChatAudience(channel, participants), viewers, id);
    }
    private static final class MutableClock extends Clock {
        long now = Instant.parse("2026-11-12T00:00:00Z").toEpochMilli();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
    }
}
