package org.encinet.mik.feature.community.social.platform.matrix.crypto;

import com.google.gson.JsonObject;
import org.encinet.mik.feature.community.social.api.SocialEventSink;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MatrixEncryptedInboxTest {
    @Test
    void missingKeysKeepRoomOrderWithoutBlockingOtherRoomsOrDuplicatingEvents() throws Exception {
        MatrixEncryption crypto = mock(MatrixEncryption.class);
        var missing = new CompletableFuture<JsonObject>();
        when(crypto.decrypt("one", "$first")).thenReturn(missing);
        when(crypto.decrypt("one", "$second")).thenReturn(CompletableFuture.completedFuture(event("$second")));
        when(crypto.decrypt("two", "$other")).thenReturn(CompletableFuture.completedFuture(event("$other")));
        var deliveries = new CopyOnWriteArrayList<String>();
        var other = new CountDownLatch(1);
        var all = new CountDownLatch(3);
        try (var inbox = inbox(crypto, message -> {
            deliveries.add(message.eventId());
            if (message.eventId().equals("$other")) other.countDown();
            all.countDown();
            return SocialEventSink.Acceptance.ACCEPTED;
        })) {
            assertTrue(inbox.offer("one", "$first"));
            assertTrue(inbox.offer("one", "$second"));
            assertTrue(inbox.offer("one", "$first"));
            assertTrue(inbox.offer("two", "$other"));
            assertTrue(other.await(3, TimeUnit.SECONDS));
            assertEquals(List.of("$other"), deliveries);
            missing.complete(event("$first"));
            assertTrue(all.await(3, TimeUnit.SECONDS));
            assertEquals(List.of("$other", "$first", "$second"), deliveries);
            assertTrue(inbox.offer("one", "$first"));
            verify(crypto, times(1)).decrypt("one", "$first");
        }
    }

    @Test
    void backpressureIsBoundedAndClosingCancelsThePendingKeyWait() throws Exception {
        MatrixEncryption crypto = mock(MatrixEncryption.class);
        var pending = new CompletableFuture<JsonObject>();
        var started = new CountDownLatch(1);
        when(crypto.decrypt(anyString(), anyString())).thenAnswer(_ -> {
            started.countDown();
            return pending;
        });
        var inbox = inbox(crypto, _ -> { fail("closed inbox delivered an event"); return SocialEventSink.Acceptance.IGNORED; });
        try {
            for (int index = 0; index < 512; index++) assertTrue(inbox.offer("one", "$" + index));
            assertFalse(inbox.offer("one", "$overflow"));
            assertTrue(started.await(3, TimeUnit.SECONDS));
        } finally {
            inbox.close();
        }
        awaitCancelled(pending);
    }

    @Test
    void leavingAndRejoiningCannotReleaseAnOldPendingEventIntoTheNewRoomQueue() throws Exception {
        MatrixEncryption crypto = mock(MatrixEncryption.class);
        var abandoned = new CompletableFuture<JsonObject>();
        var started = new CountDownLatch(1);
        when(crypto.decrypt("one", "$event")).thenAnswer(_ -> {
            started.countDown();
            return abandoned;
        }).thenReturn(CompletableFuture.completedFuture(event("$event")));
        var delivered = new CountDownLatch(1);
        try (var inbox = inbox(crypto, _ -> { delivered.countDown(); return SocialEventSink.Acceptance.ACCEPTED; })) {
            inbox.offer("one", "$event");
            assertTrue(started.await(3, TimeUnit.SECONDS));
            inbox.retainRooms(_ -> false);
            inbox.offer("one", "$event");
            assertTrue(delivered.await(3, TimeUnit.SECONDS));
            awaitCancelled(abandoned);
        }
    }

    private static MatrixEncryptedInbox inbox(MatrixEncryption crypto,
            java.util.function.Function<MatrixRoomMessage, SocialEventSink.Acceptance> delivery) {
        return new MatrixEncryptedInbox(crypto, (room, event) -> Optional.of(new MatrixRoomMessage(
                event.eventId(), room, new MatrixRoomMessage.MatrixMember(event.sender(), "Alice"),
                "text", "text", List.of(), Optional.empty(), Optional.empty())), delivery,
                () -> true, 10, Logger.getAnonymousLogger());
    }

    private static JsonObject event(String id) {
        var event = new JsonObject();
        event.addProperty("event_id", id);
        event.addProperty("type", "m.room.message");
        event.addProperty("sender", "@alice:example.org");
        event.add("content", new JsonObject());
        return event;
    }

    private static void awaitCancelled(CompletableFuture<?> future) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!future.isCancelled() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(future.isCancelled());
    }
}
