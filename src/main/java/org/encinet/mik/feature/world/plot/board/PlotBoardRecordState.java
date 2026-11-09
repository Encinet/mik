package org.encinet.mik.feature.world.plot.board;

import java.util.Objects;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;

/** Paging preserves the loaded source; only results for that exact source may replace it. */
record PlotBoardRecordState(PlotBoardRecord source, UiReadingPosition position, long revision, boolean failed) {
    PlotBoardRecordState { Objects.requireNonNull(source); Objects.requireNonNull(position); }

    static PlotBoardRecordState read(PlotBoardRecordState current, PlotBoardRecordState expected, UiReadingPosition next) {
        return current == expected ? new PlotBoardRecordState(current.source, next, current.revision, current.failed) : current;
    }

    static PlotBoardRecordState complete(PlotBoardRecordState current, PlotBoardRecord expected, PlotBoardRecord fresh, long revision) {
        if (current.source != expected) return current;
        if (fresh == null || !fresh.id().equals(expected.id()) || fresh.ownership().isEmpty()) return fail(current, expected, revision);
        return new PlotBoardRecordState(fresh, current.position, revision, false);
    }

    static PlotBoardRecordState fail(PlotBoardRecordState current, PlotBoardRecord expected, long revision) {
        return current.source == expected ? new PlotBoardRecordState(current.source, current.position, revision, true) : current;
    }
}
