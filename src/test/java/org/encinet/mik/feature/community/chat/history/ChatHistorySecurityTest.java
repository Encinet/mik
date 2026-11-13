package org.encinet.mik.feature.community.chat.history;

import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ChatHistorySecurityTest {
    @TempDir Path temp;
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), other = UUID.randomUUID();
    private ChatHistoryStore.Entry entry(String id, ChatChannel channel) {
        return new ChatHistoryStore.Entry(id, new ChatAudience(channel, channel == ChatChannel.PRIVATE ? Set.of(alice, bob) : Set.of()), Optional.empty(), id);
    }
    private ChatHistoryStore.SyncQuery query(UUID viewer, ChatChannel channel, String since, String page, Set<UUID> hidden) {
        return new ChatHistoryStore.SyncQuery(viewer, false, channel, null, since, page, hidden);
    }
    private record Result(Set<String> messages, String checkpoint, String next, boolean reset) { }
    private Result sync(ChatHistoryStore store, ChatHistoryStore.SyncQuery query) throws Exception {
        return store.sync(query, result -> new Result(result.page().rows().stream().map(ChatHistoryStore.Row::payload)
                .collect(java.util.stream.Collectors.toSet()), result.checkpoint(), result.page().hasMore()
                ? result.continuation().apply(result.page().nextBefore()) : null, result.reset())).get(5, TimeUnit.SECONDS);
    }

    @Test void opaqueCheckpointsBindIdentityChannelVisibilityAndDatabaseAndPersistAcrossRestart() throws Exception {
        Path path = temp.resolve("history.db");
        String checkpoint;
        {
          var store = new ChatHistoryStore(path);
          try {
            store.append(entry("public", ChatChannel.PUBLIC)).get(5, TimeUnit.SECONDS);
            store.append(entry("private", ChatChannel.PRIVATE)).get(5, TimeUnit.SECONDS);
            Result first = sync(store, query(alice, ChatChannel.PUBLIC, null, null, Set.of()));
            assertEquals(Set.of("public"), first.messages());
            checkpoint = first.checkpoint();
            assertTrue(checkpoint.matches("[A-Za-z0-9_-]{91}"));
            Result same = sync(store, query(alice, ChatChannel.PUBLIC, checkpoint, null, Set.of()));
            assertTrue(same.messages().isEmpty()); assertFalse(same.reset());
            assertNotEquals(checkpoint, same.checkpoint(), "Randomized tokens cannot expose position equality");
            assertTrue(sync(store, query(other, ChatChannel.PUBLIC, checkpoint, null, Set.of())).reset());
            assertEquals(Set.of("private"), sync(store, query(alice, ChatChannel.PRIVATE, checkpoint, null, Set.of())).messages());
            assertTrue(sync(store, query(alice, ChatChannel.PUBLIC, checkpoint, null, Set.of(bob))).reset());
            String tampered = (checkpoint.charAt(0) == 'a' ? "b" : "a") + checkpoint.substring(1);
            assertTrue(sync(store, query(alice, ChatChannel.PUBLIC, tampered, null, Set.of())).reset());
          } finally { store.close(); store.stopped().get(5, TimeUnit.SECONDS); }
        }
        try (var reopened = new ChatHistoryStore(path)) {
            assertFalse(sync(reopened, query(alice, ChatChannel.PUBLIC, checkpoint, null, Set.of())).reset());
        }
        try (var replacement = new ChatHistoryStore(temp.resolve("replacement.db"))) {
            assertTrue(sync(replacement, query(alice, ChatChannel.PUBLIC, checkpoint, null, Set.of())).reset());
        }
    }

    @Test void continuationsFreezeTheOriginalHeadAndCatchUpWithTheCompletedCheckpoint() throws Exception {
        try (var store = new ChatHistoryStore(temp.resolve("pages.db"))) {
            for (int i = 0; i < 270; i++) store.append(entry("old-" + i, ChatChannel.PUBLIC)).get(5, TimeUnit.SECONDS);
            Result first = sync(store, query(alice, ChatChannel.PUBLIC, null, null, Set.of()));
            assertNotNull(first.next());
            store.append(entry("new", ChatChannel.PUBLIC)).get(5, TimeUnit.SECONDS);
            Set<String> all = new HashSet<>(first.messages());
            Result next = sync(store, query(alice, ChatChannel.PUBLIC, null, first.next(), Set.of()));
            assertNull(next.next()); all.addAll(next.messages()); assertEquals(270, all.size()); assertFalse(all.contains("new"));
            assertEquals(Set.of("new"), sync(store, query(alice, ChatChannel.PUBLIC, next.checkpoint(), null, Set.of())).messages());
        }
    }

    @Test void durableRequestDeduplicationAndOrderingSurviveRestartAndClockRollback() throws Exception {
        Path path = temp.resolve("requests.db");
        var request = new ChatHistoryStore.Request(alice, "request", "fingerprint");
        String firstOrder;
        {
          var store = new ChatHistoryStore(path, Clock.fixed(Instant.ofEpochMilli(2000), ZoneOffset.UTC));
          try {
            var first = store.append(() -> entry("first", ChatChannel.PUBLIC), request).get(5, TimeUnit.SECONDS);
            assertTrue(first.inserted()); firstOrder = first.order(); assertEquals(7, UUID.fromString(firstOrder).version());
          } finally { store.close(); store.stopped().get(5, TimeUnit.SECONDS); }
        }
        try (var store = new ChatHistoryStore(path, Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC))) {
            var retry = store.append(() -> entry("different-generated-id", ChatChannel.PUBLIC), request).get(5, TimeUnit.SECONDS);
            assertFalse(retry.inserted()); assertEquals("first", retry.id()); assertEquals(firstOrder, retry.order());
            var second = store.append(entry("second", ChatChannel.PUBLIC)).get(5, TimeUnit.SECONDS);
            assertTrue(firstOrder.compareTo(second.order()) < 0);
            var conflicting = new ChatHistoryStore.Request(alice, "request", "changed body");
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> store.append(() -> entry("conflict", ChatChannel.PUBLIC), conflicting).get(5, TimeUnit.SECONDS));
            assertEquals(Set.of("first", "second"), sync(store, query(alice, ChatChannel.PUBLIC, null, null, Set.of())).messages());
        }
    }

    @Test void stalledReadersCannotBlockDurableWritesAndCloseDrainsEveryAcceptedTask() throws Exception {
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(2);
        var store = new ChatHistoryStore(temp.resolve("lanes.db"));
        var query = new ChatHistoryStore.Query(alice, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE);
        java.util.function.Function<ChatHistoryStore.Page, Integer> slow = page -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Reader not released"); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            return page.rows().size();
        };
        CompletableFuture<Integer> first = store.read(query, slow), second = store.read(query, slow);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(store.append(entry("saved-during-read", ChatChannel.PUBLIC)).get(1, TimeUnit.SECONDS).inserted());
            store.close(); assertFalse(store.stopped().isDone());
        } finally { release.countDown(); }
        first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS); store.stopped().get(5, TimeUnit.SECONDS);
        assertTrue(store.read(query).isCompletedExceptionally());
        for (int i = 0; i < 20; i++) {
            var closing = new ChatHistoryStore(temp.resolve("close-" + i + ".db"));
            closing.maintain().get(5, TimeUnit.SECONDS);
            CompletableFuture.runAsync(closing::close).get(2, TimeUnit.SECONDS);
            closing.close(); closing.stopped().get(2, TimeUnit.SECONDS);
        }
    }
}
