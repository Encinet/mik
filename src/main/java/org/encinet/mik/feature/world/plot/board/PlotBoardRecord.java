package org.encinet.mik.feature.world.plot.board;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Optional;

/** A private reading snapshot: public fields and access facts, without exposing private author UUIDs. */
record PlotBoardRecord(JsonObject record, Optional<Ownership> ownership) {
    record Ownership(boolean feedbackAuthor, boolean disputeAuthor) { }

    PlotBoardRecord {
        record = Objects.requireNonNull(record).deepCopy();
        Objects.requireNonNull(ownership);
    }

    @Override public JsonObject record() { return record.deepCopy(); }
    String id() { return record.get("id").getAsString(); }
    static PlotBoardRecord unverified(JsonObject record) { return new PlotBoardRecord(record, Optional.empty()); }
    static PlotBoardRecord verified(JsonObject record, boolean feedbackAuthor, boolean disputeAuthor) {
        return new PlotBoardRecord(record, Optional.of(new Ownership(feedbackAuthor, disputeAuthor)));
    }
}
