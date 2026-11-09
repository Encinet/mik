package org.encinet.mik.feature.entertainment.music.ui;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.feature.entertainment.music.ui.MusicSoundEdits.Parameter;
import static org.junit.jupiter.api.Assertions.*;

class MusicSoundEditsTest {
    @Test void rapidChangesRetainLatestValuesAndSerializeWrites() {
        var h = new Harness();
        var result = h.edits.change(Parameter.VOLUME, 55);
        assertSame(result, h.edits.change(Parameter.VOLUME, 60));
        h.edits.change(Parameter.VOLUME, 65);
        h.edits.change(Parameter.RANGE, 68);
        assertEquals(65, h.edits.value(Parameter.VOLUME));
        assertEquals(68, h.edits.value(Parameter.RANGE));
        assertEquals(List.of(Map.of(Parameter.VOLUME, 55)), h.writes);
        h.ack(0);
        assertFalse(result.isDone(), "the task stays busy until the latest values are stored");
        assertEquals(Map.of(Parameter.VOLUME, 65, Parameter.RANGE, 68), h.writes.get(1));
        h.edits.change(Parameter.VOLUME, 70);
        h.ack(1);
        assertEquals(Map.of(Parameter.VOLUME, 70), h.writes.get(2));
        h.ack(2);
        result.join();
        assertEquals(70, h.actual.get(Parameter.VOLUME));
        assertEquals(68, h.actual.get(Parameter.RANGE));
        assertFalse(h.edits.active());
        h.actual.put(Parameter.VOLUME, 40);
        assertEquals(40, h.edits.value(Parameter.VOLUME), "idle controls read other controllers' changes");
    }

    @Test void backgroundCompletionOnlySchedulesTheNextWriteOnTheOwnerThread() {
        var h = new Harness();
        h.edits.change(Parameter.GAIN, 75);
        h.edits.change(Parameter.GAIN, 30);
        h.pending.getFirst().complete(null);
        assertEquals(1, h.writes.size());
        h.runMain();
        assertEquals(Map.of(Parameter.GAIN, 30), h.writes.get(1));
        h.ack(1);
        assertEquals(30, h.actual.get(Parameter.GAIN));
    }

    @Test void returningToTheInFlightValueAvoidsAnUnnecessaryWrite() {
        var h = new Harness();
        var result = h.edits.change(Parameter.VOLUME, 55);
        h.edits.change(Parameter.VOLUME, 60);
        h.edits.change(Parameter.VOLUME, 55);
        h.ack(0);
        result.join();
        assertEquals(1, h.writes.size());
    }

    @Test void rejectedChangesRevertToStoredValuesAndAllowExplicitRetry() {
        var h = new Harness();
        var result = h.edits.change(Parameter.RANGE, 200);
        h.edits.change(Parameter.RANGE, 256);
        var error = new IOException("Rejected sound setting");
        h.pending.getFirst().completeExceptionally(error);
        h.runMain();
        assertSame(error, assertThrows(CompletionException.class, result::join).getCause());
        assertEquals(64, h.edits.value(Parameter.RANGE));
        assertEquals(1, h.writes.size(), "failed permission or overlap checks are not retried automatically");
        var retry = h.edits.change(Parameter.RANGE, 68);
        h.ack(1);
        retry.join();
        assertEquals(68, h.edits.value(Parameter.RANGE));
    }

    @Test void closeDropsQueuedWorkAndLeavesTheDomainWriteAlreadyInFlightAlone() {
        var h = new Harness();
        var result = h.edits.change(Parameter.VOLUME, 55);
        h.edits.change(Parameter.VOLUME, 60);
        h.edits.close();
        assertTrue(result.isCancelled());
        assertFalse(h.pending.getFirst().isCancelled());
        h.ack(0);
        assertEquals(1, h.writes.size());
        assertTrue(h.edits.change(Parameter.VOLUME, 70).isCompletedExceptionally());
    }

    @Test void unchangedAndInvalidValuesNeverStartPersistence() {
        var h = new Harness();
        h.edits.change(Parameter.VOLUME, 50).join();
        assertThrows(IllegalArgumentException.class, () -> h.edits.change(Parameter.RANGE, 7));
        assertThrows(IllegalArgumentException.class, () -> h.edits.change(Parameter.VOLUME, 101));
        assertTrue(h.writes.isEmpty());
    }

    private static final class Harness {
        final Map<Parameter, Integer> actual = new EnumMap<>(Map.of(Parameter.VOLUME, 50, Parameter.RANGE, 64, Parameter.GAIN, 100));
        final List<Map<Parameter, Integer>> writes = new ArrayList<>();
        final List<CompletableFuture<Void>> pending = new ArrayList<>();
        final ArrayDeque<Runnable> main = new ArrayDeque<>();
        final MusicSoundEdits edits = new MusicSoundEdits(actual::get, values -> {
            writes.add(values);
            var future = new CompletableFuture<Void>();
            pending.add(future);
            return future.thenRun(() -> actual.putAll(values));
        }, main::add);
        void ack(int index) { pending.get(index).complete(null); runMain(); }
        void runMain() { while (!main.isEmpty()) main.remove().run(); }
    }
}
