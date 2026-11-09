package org.encinet.mik.feature.world.plot.board;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlotBoardRecordStateTest {
    @Test void aPendingRefreshKeepsNewReadingPositionsButCannotReplaceAnotherOpenOfTheSameRecord() {
        var source = PlotBoardRecord.unverified(record("first"));
        var initial = new PlotBoardRecordState(source, UiReadingPosition.START, 1, false);
        var current = new AtomicReference<>(initial);
        var response = new CompletableFuture<PlotBoardRecord>();
        response.thenAccept(fresh -> current.updateAndGet(latest -> PlotBoardRecordState.complete(latest, source, fresh, 2)));
        var position = new UiReadingPosition(UiKey.named("ruling"), 48, 13);
        current.set(PlotBoardRecordState.read(current.get(), initial, position));
        response.complete(PlotBoardRecord.verified(record("first"), true, true));
        assertEquals(position, current.get().position()); assertEquals(2, current.get().revision());
        assertTrue(current.get().source().ownership().orElseThrow().feedbackAuthor());
        var reopened = new PlotBoardRecordState(PlotBoardRecord.unverified(record("first")), position, 2, false);
        var retiredSource = new PlotBoardRecordState(PlotBoardRecord.unverified(record("first")), position, 2, false);
        assertEquals(retiredSource, reopened); assertNotSame(retiredSource.source(), reopened.source());
        assertSame(reopened, PlotBoardRecordState.complete(reopened, retiredSource.source(), PlotBoardRecord.verified(record("first"), false, false), 3));
        assertSame(reopened, PlotBoardRecordState.fail(reopened, retiredSource.source(), 3));
        assertSame(reopened, PlotBoardRecordState.read(reopened, retiredSource, UiReadingPosition.START));
    }

    @Test void outOfOrderTargetsErrorsAndMismatchedPayloadsNeverRetargetTheCurrentRecord() {
        var first = new PlotBoardRecordState(PlotBoardRecord.unverified(record("first")), UiReadingPosition.START, 1, false);
        var position = new UiReadingPosition(UiKey.named("description"), 123, 8);
        var second = new PlotBoardRecordState(PlotBoardRecord.unverified(record("second")), position, 2, false);
        assertSame(second, PlotBoardRecordState.complete(second, first.source(), PlotBoardRecord.verified(record("first"), true, true), 3));
        assertSame(second, PlotBoardRecordState.fail(second, first.source(), 3));
        var failed = PlotBoardRecordState.complete(second, second.source(), PlotBoardRecord.verified(record("first"), true, true), 3);
        assertTrue(failed.failed()); assertSame(second.source(), failed.source()); assertEquals(position, failed.position());
        var recovered = PlotBoardRecordState.complete(failed, second.source(), PlotBoardRecord.verified(record("second"), false, false), 4);
        assertFalse(recovered.failed()); assertEquals(position, recovered.position());
        assertFalse(recovered.source().ownership().orElseThrow().disputeAuthor());
        assertTrue(PlotBoardRecordState.complete(second, second.source(), null, 3).failed());
        assertTrue(PlotBoardRecordState.complete(second, second.source(), PlotBoardRecord.unverified(record("second")), 3).failed());
    }

    @Test void sourceAndReturnedJsonMutationsCannotModifyTheReadingSnapshot() {
        var input = record("first"); var source = PlotBoardRecord.unverified(input);
        input.addProperty("id", "changed"); source.record().addProperty("id", "changed-again");
        assertEquals("first", source.id()); assertTrue(source.ownership().isEmpty());
    }

    private static JsonObject record(String id) { var record = new JsonObject(); record.addProperty("id", id); return record; }
}
