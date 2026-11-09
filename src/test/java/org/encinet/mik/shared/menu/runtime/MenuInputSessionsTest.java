package org.encinet.mik.shared.menu.runtime;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MenuInputSessionsTest {
    @Test void nativeDialogCleanupRunsOnceForCompletionCancellationAndOwnerLoss() {
        for (var finish : MenuInputSessions.Finish.values()) {
            var owner = new AtomicBoolean(true);
            var ended = new AtomicInteger();
            var finished = new ArrayList<MenuInputSessions.Finish>();
            var inputs = new MenuInputSessions((_, reason) -> finished.add(reason));
            UUID id = UUID.randomUUID();
            var lease = inputs.begin(id, owner::get, ended::incrementAndGet);
            assertTrue(inputs.blocking(id));
            assertFalse(inputs.offerText(id, "Ordinary chat is not captured by a dialog"));
            switch (finish) {
                case COMPLETED -> assertTrue(lease.complete());
                case CANCELLED -> lease.close();
                case INVALIDATED -> { owner.set(false); inputs.tick(); }
            }
            assertFalse(lease.complete());
            lease.close();
            inputs.clear();
            assertEquals(1, ended.get());
            assertEquals(java.util.List.of(finish), finished);
            assertFalse(inputs.blocking(id));
        }
    }

    @Test void aSubmissionClaimsOnlyTheFirstMessageAndCompletesOnTheServerThread() throws Exception {
        Thread main = Thread.currentThread();
        var received = new ArrayList<String>();
        var ended = new AtomicInteger();
        var inputs = new MenuInputSessions((_, _) -> fail("Not a confirmation"));
        UUID id = UUID.randomUUID();
        var lease = inputs.beginChatOnce(id, () -> {
            assertSame(main, Thread.currentThread()); return true;
        }, raw -> { assertSame(main, Thread.currentThread()); received.add(raw); }, () -> {
            assertSame(main, Thread.currentThread()); ended.incrementAndGet();
        });
        inputs.tick();
        assertTrue(lease.active());
        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                assertTrue(inputs.offerText(id, "first query"));
                assertFalse(inputs.offerText(id, "ordinary chat before the tick"));
            }).get();
        }
        assertTrue(received.isEmpty());
        inputs.tick();
        assertEquals(java.util.List.of("first query"), received);
        assertFalse(lease.active());
        lease.close();
        inputs.tick();
        assertEquals(1, ended.get());
        assertFalse(inputs.offerText(id, "ordinary chat"));
    }

    @Test void cancelledOrAbandonedSubmissionsDiscardTheirQueuedTextAndFailuresReleaseCapture() {
        for (boolean cancelled : new boolean[] {false, true}) {
            var owner = new AtomicBoolean(true);
            var inputs = new MenuInputSessions((_, _) -> { });
            UUID id = UUID.randomUUID();
            var lease = inputs.beginChatOnce(id, owner::get, _ -> fail("A stale submission was delivered"), () -> { });
            assertTrue(inputs.offerText(id, "private abandoned query"));
            if (cancelled) lease.close();
            else owner.set(false);
            inputs.tick();
            assertFalse(inputs.offerText(id, "ordinary chat"));
        }
        var inputs = new MenuInputSessions((_, _) -> { });
        UUID id = UUID.randomUUID();
        var ended = new AtomicInteger();
        inputs.beginChatOnce(id, () -> true, _ -> { throw new IllegalStateException("delivery failed"); }, ended::incrementAndGet);
        inputs.offerText(id, "query");
        assertThrows(IllegalStateException.class, inputs::tick);
        assertEquals(1, ended.get());
        assertFalse(inputs.offerText(id, "ordinary chat"));
    }

    @Test void completingASubmissionCannotReleaseAnInputOpenedByItsCallback() {
        var inputs = new MenuInputSessions((_, _) -> { });
        UUID id = UUID.randomUUID();
        var next = new java.util.concurrent.atomic.AtomicReference<org.encinet.mik.shared.menu.MenuInputLease>();
        var received = new ArrayList<String>();
        inputs.beginChatOnce(id, () -> true,
                _ -> next.set(inputs.beginChat(id, () -> true, received::add, () -> { })), () -> { });
        inputs.offerText(id, "open next input");
        inputs.tick();
        assertTrue(next.get().active());
        assertTrue(inputs.offerText(id, "new input value"));
        inputs.tick();
        assertEquals(java.util.List.of("new input value"), received);
    }

    @Test void chatKeepsControlsAvailableAndCoalescesTextOnTheServerTick() throws Exception {
        Thread owner = Thread.currentThread();
        var values = new ArrayList<String>();
        var inputs = new MenuInputSessions((_, _) -> fail("Chat must not close native dialogs"));
        UUID id = UUID.randomUUID();
        AtomicInteger ended = new AtomicInteger();
        var lease = inputs.beginChat(id, () -> {
            assertSame(owner, Thread.currentThread());
            return true;
        }, raw -> {
            assertSame(owner, Thread.currentThread());
            values.add(raw);
        }, ended::incrementAndGet);
        assertTrue(lease.active());
        assertFalse(inputs.blocking(id));
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                for (int index = 0; index < 100; index++) assertTrue(inputs.offerText(id, "<red>" + index));
            }).get();
        }
        assertTrue(values.isEmpty());
        inputs.contentChanged(id);
        inputs.tick();
        assertEquals(java.util.List.of("<red>99"), values);
        assertTrue(inputs.offerText(id, "second"));
        inputs.tick();
        assertEquals(java.util.List.of("<red>99", "second"), values);
        assertTrue(lease.complete());
        lease.close();
        assertFalse(inputs.offerText(id, "normal chat"));
        assertEquals(1, ended.get());
    }

    @Test void oldPendingChatCannotReachANewFieldOrSurviveOwnerLossOrShutdown() {
        AtomicBoolean owner = new AtomicBoolean(true);
        var values = new ArrayList<String>();
        AtomicInteger ended = new AtomicInteger();
        var inputs = new MenuInputSessions((_, _) -> { });
        UUID id = UUID.randomUUID();
        inputs.beginChat(id, owner::get, values::add, ended::incrementAndGet);
        assertTrue(inputs.offerText(id, "old field"));
        inputs.beginChat(id, owner::get, raw -> values.add("new:" + raw), ended::incrementAndGet);
        inputs.tick();
        assertTrue(values.isEmpty());
        assertTrue(inputs.offerText(id, "new field"));
        owner.set(false);
        inputs.tick();
        assertTrue(values.isEmpty());
        inputs.beginChat(id, () -> true, values::add, ended::incrementAndGet);
        assertTrue(inputs.offerText(id, "before shutdown"));
        inputs.clear();
        inputs.tick();
        assertFalse(inputs.offerText(id, "after shutdown"));
        inputs.beginChat(id, () -> true, values::add, ended::incrementAndGet);
        inputs.clear();
        assertFalse(inputs.offerText(id, "after disable"));
        assertEquals(4, ended.get());
    }

    @Test void replacementAndLateOrDuplicateSubmissionCannotReachAnotherView() {
        var finished = new ArrayList<MenuInputSessions.Finish>();
        var inputs = new MenuInputSessions((_, reason) -> finished.add(reason));
        UUID id = UUID.randomUUID();
        var first = inputs.begin(id, () -> true);
        var second = inputs.begin(id, () -> true);
        assertFalse(first.complete());
        first.close();
        assertTrue(second.active());
        assertTrue(second.complete());
        assertFalse(second.complete());
        assertFalse(second.active());
        assertEquals(java.util.List.of(MenuInputSessions.Finish.INVALIDATED, MenuInputSessions.Finish.COMPLETED), finished);
    }

    @Test void changingContentStillInvalidatesNativeResponses() {
        var finished = new ArrayList<MenuInputSessions.Finish>();
        var inputs = new MenuInputSessions((_, reason) -> finished.add(reason));
        UUID id = UUID.randomUUID();
        var nativeInput = inputs.begin(id, () -> true);
        assertTrue(inputs.blocking(id));
        inputs.contentChanged(id);
        assertFalse(nativeInput.complete());
        assertFalse(inputs.blocking(id));
        assertEquals(java.util.List.of(MenuInputSessions.Finish.INVALIDATED), finished);
    }

    @Test void nativeInputEndsOnlyOnCompletionOwnerChangesCancellationAndShutdown() {
        AtomicBoolean owner = new AtomicBoolean(true);
        var finished = new ArrayList<MenuInputSessions.Finish>();
        var inputs = new MenuInputSessions((_, reason) -> finished.add(reason));
        UUID id = UUID.randomUUID();
        var stale = inputs.begin(id, owner::get);
        owner.set(false);
        assertFalse(stale.complete());
        var editing = inputs.begin(id, () -> true);
        inputs.tick();
        assertTrue(editing.active());
        assertTrue(editing.complete());
        inputs.begin(id, () -> true).close();
        inputs.begin(id, () -> true);
        inputs.clear();
        inputs.clear();
        assertEquals(java.util.List.of(MenuInputSessions.Finish.INVALIDATED, MenuInputSessions.Finish.COMPLETED,
                MenuInputSessions.Finish.CANCELLED, MenuInputSessions.Finish.INVALIDATED), finished);
    }
}
